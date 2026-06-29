#version 330

// Dragon Universe — Task 2 orbital planet (ray-marched, fullscreen).
//
// One pass per planet. Reconstructs a world-space view ray from the shared inverse-VP UBO (exactly
// like the skybox — no hand-built camera basis), intersects a single camera-relative sphere, maps an
// equirectangular surface texture onto the hit normal, applies a day/night terminator from the
// per-scene light direction (NOT a hardcoded sun angle), then integrates a Rayleigh atmosphere shell
// and an optional flat ring. Translucent blend + far->near draw order composite multiple planets.

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;  // inverse(projection * rotation-only modelview)
    vec4 uSunDir;       // xyz scene sun direction (unused here; planet light is per-planet below)
    vec4 uMisc;         // x = time
};

layout(std140) uniform DUPlanet {
    vec4 uCenterRadius; // xyz = planet center (camera-relative), w = radius
    vec4 uLight;        // xyz = light direction (toward star), w = hasTexture
    vec4 uAtmo0;        // x = size, y = falloff, z = density, w = brightnessFactor
    vec4 uAtmo1;        // xyz = Rayleigh scatter coefficients, w = densityFactor
    vec4 uRing0;        // x = hasRing, y = innerRadius, z = outerRadius
    vec4 uRingNormal;   // xyz = ring plane normal
};

uniform sampler2D Sampler0; // equirectangular surface map
uniform sampler2D Sampler1; // 1D radial ring strip

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const int ATMO_STEPS = 12;

// World-space view ray (camera at origin), same reconstruction the skybox uses.
vec3 rayDir() {
    vec4 clip = vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec4 world = uInvViewProj * clip;
    return normalize(world.xyz / world.w);
}

// Ray-sphere (rd normalized -> a = 1). Returns vec2(tNear, tFar); .x < 0 with .y < 0 means miss.
vec2 raySphere(vec3 ro, vec3 rd, vec3 center, float r) {
    vec3 oc = ro - center;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - r * r;
    float h = b * b - c;
    if (h < 0.0) return vec2(-1.0, -1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// Equirectangular lat-long mapping from a surface normal (matches the authored 2:1 textures).
vec2 equirectUV(vec3 n) {
    float u = 0.5 + atan(n.z, n.x) / (2.0 * PI);
    float v = 0.5 - asin(clamp(n.y, -1.0, 1.0)) / PI;
    return vec2(u, v);
}

// Rayleigh phase (normalized-ish), brighter forward/back along the light.
float rayleighPhase(float cosT) {
    return 0.75 * (1.0 + cosT * cosT);
}

void main() {
    vec3 ro = vec3(0.0);
    vec3 rd = rayDir();

    vec3 center = uCenterRadius.xyz;
    float radius = uCenterRadius.w;
    vec3 L = normalize(uLight.xyz);

    float atmoSize = max(uAtmo0.x, 0.0);
    float atmoRadius = radius * (1.0 + atmoSize);

    // NOTE: no early "miss the shell -> discard" here. The ring is a separate flat annulus whose
    // outer radius is far larger than the atmosphere shell, so gating on the sphere/shell hit would
    // clip the ring to the planet's silhouette (it would "smear" around the sphere). Instead every
    // layer is evaluated independently below and we discard only when nothing contributes.

    vec2 hSurf = raySphere(ro, rd, center, radius);
    bool surfaceHit = hSurf.x > 0.0;
    float tSurf = hSurf.x;

    // ---- Planet body + atmosphere layer (straight-alpha) ----
    vec3 planetColor = vec3(0.0);
    float planetAlpha = 0.0;
    {
        // Surface shading.
        vec3 surfColor = vec3(0.0);
        if (surfaceHit) {
            vec3 hit = ro + rd * tSurf;
            vec3 n = normalize(hit - center);
            vec3 albedo = (uLight.w > 0.5) ? texture(Sampler0, equirectUV(n)).rgb : vec3(0.5);

            // Soft day/night terminator from the per-scene light direction.
            float ndl = dot(n, L);
            float day = smoothstep(-0.15, 0.15, ndl);
            surfColor = albedo * (0.06 + 0.94 * day);   // keep a faint night-side ambient
        }

        // Atmosphere shell (cheap single-scatter approximation), only where its sphere is crossed.
        vec3 atmoColor = vec3(0.0);
        float atmoCover = 0.0;
        if (atmoSize > 0.0) {
            vec2 hOuter = raySphere(ro, rd, center, atmoRadius);
            if (hOuter.y > 0.0) {
                float tNear = max(hOuter.x, 0.0);
                float tFar = surfaceHit ? tSurf : hOuter.y;
                float seg = tFar - tNear;
                if (seg > 0.0) {
                    float shell = max(atmoRadius - radius, 1.0e-4);
                    float falloff = max(uAtmo0.y, 0.0001);
                    float density = max(uAtmo0.z, 0.0) * max(uAtmo1.w, 0.0);
                    float brightness = max(uAtmo0.w, 0.0);

                    float optical = 0.0;
                    float stepLen = seg / float(ATMO_STEPS);
                    for (int i = 0; i < ATMO_STEPS; i++) {
                        float t = tNear + (float(i) + 0.5) * stepLen;
                        vec3 sp = ro + rd * t;
                        float altitude = clamp((length(sp - center) - radius) / shell, 0.0, 1.0);
                        float localDensity = exp(-altitude * falloff);
                        vec3 sn = normalize(sp - center);
                        float lit = 0.25 + 0.75 * clamp(dot(sn, L), 0.0, 1.0);
                        optical += localDensity * lit * stepLen;
                    }
                    optical *= density / shell;

                    float phase = rayleighPhase(dot(rd, L));
                    atmoColor = uAtmo1.xyz * optical * brightness * phase;
                    atmoCover = clamp(optical * brightness, 0.0, 1.0);
                }
            }
        }

        if (surfaceHit) {
            // Atmosphere in front of / around the lit disc reads as additive glow; disc stays opaque.
            planetColor = surfColor + atmoColor;
            planetAlpha = 1.0;
        } else if (atmoCover > 0.0) {
            planetColor = atmoColor;
            planetAlpha = atmoCover;
        }
    }

    // ---- Ring layer: an independent flat annulus in the planet's equatorial plane ----
    // Ray-vs-plane intersection (NOT derived from the sphere hit). The plane passes through the
    // planet center with normal uRingNormal (carries the optional RingDef tilt).
    vec3 ringColor = vec3(0.0);
    float ringAlpha = 0.0;
    float tRing = -1.0;
    bool ringHit = false;
    if (uRing0.x > 0.5) {
        vec3 N = normalize(uRingNormal.xyz);
        float denom = dot(rd, N);
        if (abs(denom) > 1.0e-6) {
            tRing = dot(center, N) / denom;   // ro = 0
            if (tRing > 0.0) {
                vec3 rp = rd * tRing;
                float dist = length(rp - center);          // in-plane radial distance
                float inner = uRing0.y;
                float outer = max(uRing0.z, inner + 1.0e-4);
                if (dist >= inner && dist <= outer) {
                    float u = (dist - inner) / (outer - inner);
                    vec4 ringTex = texture(Sampler1, vec2(u, 0.5));
                    float ringLit = 0.15 + 0.85 * clamp(dot(N, L) * 0.5 + 0.5, 0.0, 1.0);
                    ringColor = ringTex.rgb * ringLit;
                    ringAlpha = ringTex.a;
                    ringHit = ringAlpha > 0.0;
                }
            }
        }
    }

    // Depth ordering: the planet body occludes the ring's far arc. Where the sphere is actually hit
    // and the ring crossing is behind that hit (tRing > tSurf), the body hides the ring. Everywhere
    // else the ring is in front of (or beside) the body, so it composites over it. The near arc
    // (tRing < tSurf) therefore crosses in front; the far arc behind the disc is culled here.
    if (surfaceHit && tRing > tSurf) {
        ringHit = false;
        ringAlpha = 0.0;
    }

    if (planetAlpha <= 0.0 && !ringHit) {
        discard;
    }

    // Composite ring "over" planet in premultiplied space, then output straight-alpha so the
    // pipeline's (SRC_ALPHA, 1-SRC_ALPHA) blend lays the result over the already-drawn sky.
    vec3 premul = ringColor * ringAlpha + planetColor * planetAlpha * (1.0 - ringAlpha);
    float outA = ringAlpha + planetAlpha * (1.0 - ringAlpha);
    if (outA <= 0.0) {
        discard;
    }
    fragColor = vec4(premul / outA, outA);
}
