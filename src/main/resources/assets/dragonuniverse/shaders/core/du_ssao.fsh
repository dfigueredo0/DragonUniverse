#version 330

// Dragon Universe — SSAO (screen-space ambient occlusion), view-space hemisphere method.
//
// Reconstructs the view-space position from depth and orients a cosine-ish hemisphere of samples around
// the geometry normal (from the Sodium MRT G-buffer). Each sample is reprojected to screen, the real
// depth there is read back, and the sample counts as occluded when actual geometry sits in front of it
// (with a range check to suppress haloing across large depth gaps). Textureless: the per-pixel rotation
// is interleaved-gradient noise (same family as the god-ray jitter) and the kernel is a golden-angle
// (Vogel) spiral — so no kernel UBO array and no noise texture.
//
// Coverage gating: AO is only computed where the normal buffer has coverage (terrain in Phase 1).
// Occlusion *sampling* still reads the full depth buffer, so entities/objects correctly cast contact AO
// onto terrain even though they don't yet receive AO themselves.
//
// Output is the AO factor in rgb (1 = unoccluded/lit, 0 = fully occluded); the composite pass reads .r.

uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;   // world-space geometry normals, a = coverage
uniform sampler2D HiZSampler;      // Hi-Z min-depth pyramid (packed linear depth); mipmap sampler

layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;   // view -> world (rotation)
    mat4 uProj;      // view -> clip (forward; for reprojecting samples)
};

layout(std140) uniform DUSsao {
    vec4 uParams;    // x=radius, y=bias, z=intensity, w=power
    vec4 uParams2;   // x=sampleCount, y=enabled, z/w pad
    vec4 uParams3;   // x=hiZEnabled, y=hiZLodBias, z/w pad
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float GOLDEN_ANGLE = 2.39996323;

// Hi-Z linear-depth normalization range — MUST match du_hiz_init's FAR.
const float HIZ_FAR = 1024.0;
// Kernel screen radius (px) below which we start reading coarser Hi-Z mips (so distant samples read the
// min-neighbourhood instead of point-aliasing). Above it, mip 0 (full detail). Tunable via hiZLodBias.
const float HIZ_RES_PX = 8.0;

float unpackDepth(vec4 c) {
    return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}

// Slope-scaled depth bias: the depth-compare bias grows as the surface tilts away from the view
// direction (grazing), so a flat plane stops occluding itself at shallow angles — the cause of the
// low-frequency dark gradient on open ground at the horizon. At face-on angles the term is ~0, so real
// crevice/contact AO is preserved. Expressed as a fraction of the (view-space) radius so it scales
// with the kernel rather than being an absolute world distance.
const float SLOPE_BIAS = 0.1;   // grazing self-occlusion guard, as a fraction of radius at full grazing

vec3 viewPosFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = uInvProj * ndc;
    return v.xyz / v.w;   // perspective divide -> linear view-space position
}

// Interleaved gradient noise -> per-pixel rotation angle (decorrelates the kernel without a texture).
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 nSample = texture(NormalSampler, texCoord);
    if (nSample.a < 0.5) {           // no geometry normal here (sky/clouds/water/uncovered) -> fully lit
        fragColor = vec4(1.0);
        return;
    }

    float d = texture(DepthSampler, texCoord).r;
    vec3 P = viewPosFromDepth(texCoord, d);                  // view-space position

    // Decode the stored normal into VIEW space (what this shader works in). Coverage alpha doubles as a
    // space tag: terrain (Phase 1) stores world-space at a~1.0; vanilla entities (Phase 2) store
    // view-space at a~0.667. World-space needs transpose(view->world)=world->view; view-space is already
    // there. (transpose(mat3(uInvView)) is the world->view rotation.)
    vec3 nRaw = normalize(nSample.rgb * 2.0 - 1.0);
    vec3 N = (nSample.a < 0.83) ? nRaw : (transpose(mat3(uInvView)) * nRaw);
    N = normalize(N);

    // Geometry normals are already correct/outward-facing (real G-buffer, not depth-reconstructed), so
    // do NOT force +Z. The old `N.z < 0 -> flip` test wrongly inverted a grazing flat plane's normal
    // (≈(0,1,~0) in view space, N.z near 0) into the ground, dropping the whole sample hemisphere inside
    // solid terrain -> the low-frequency dark gradient on open ground at the horizon. Instead only flip
    // genuine back-faces (which can appear from normal interpolation at silhouette edges) using the
    // visible-surface invariant dot(N, V) >= 0.
    vec3 V = normalize(-P);          // view direction (surface -> camera) in view space
    // Margin on the back-face flip: on a grazing flat plane dot(N,V) approaches 0 from the positive
    // side, and interpolated/quantized MRT normals can dip it just below 0 on genuinely visible pixels,
    // flipping N into the surface and re-introducing a milder version of the horizon smear. We trust the
    // G-buffer normals, so only flip clear back-faces (well past zero), not borderline grazing ones.
    if (dot(N, V) < -0.1) N = -N;

    // Tangent basis around N (built once; reused for every sample).
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 T = normalize(cross(up, N));
    vec3 B = cross(N, T);

    float radius = uParams.x;
    float bias = uParams.y;
    int samples = int(uParams2.x);
    float rot = ign(gl_FragCoord.xy) * 2.0 * PI;

    // NOTE: radius is a constant *world/view-space* distance — deliberately NOT capped in screen space.
    // A previous screen-space radius cap (projScale / screenRadius / radiusScale) made the sample reach
    // vary with -P.z across the framebuffer, which painted a screen-locked radial gradient (bright
    // center, dark edges) into the AO buffer — do NOT reintroduce a radius cap. The constant world radius
    // shrinks to a sub-pixel screen footprint at distance; that's handled per-sample by the resolution
    // weight in the loop below (a sample whose reprojection lands within ~1 texel of this pixel carries no
    // neighbourhood info and is dropped), so distant AO fades cleanly to unoccluded instead of the
    // spurious self-occlusion darkening that used to make creases lighten as the camera approached.

    // Texel size of the depth buffer, for the per-sample screen-resolution weight (see the loop).
    vec2 texSize = vec2(textureSize(DepthSampler, 0));

    // Slope-scaled bias: base crevice bias + a grazing term that grows as the normal tilts away from
    // the view direction. NdotV = 1 face-on (term ~0, crevices preserved); NdotV -> 0 at grazing
    // (term -> SLOPE_BIAS * radius, so the flat plane no longer self-occludes). Real contacts survive:
    // a genuine occluder sits far closer than the plane sample, well past this bias.
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float effBias = bias + radius * SLOPE_BIAS * (1.0 - NdotV);

    // Hi-Z distance persistence (optional). Pick a pyramid LOD from the kernel's on-screen radius: above
    // HIZ_RES_PX the kernel is well-resolved -> mip 0 (full detail); below it, read coarser mips so each
    // sample sees the MIN (closest) depth over a neighbourhood instead of point-aliasing -> distant
    // contact shadows persist instead of fading. A footprint-scaled bias keeps the conservative min read
    // from self-occluding flats (the min sits slightly in front of the centre across a tilted patch).
    bool hiZ = uParams3.x > 0.5;
    float kRadPx = radius * uProj[1][1] / max(1.0e-4, -P.z) * (texSize.y * 0.5);
    float maxLod = floor(log2(max(texSize.x, texSize.y)));
    float hiZLod = clamp(log2(HIZ_RES_PX / max(1.0e-3, kRadPx)) + uParams3.y, 0.0, maxLod);
    if (hiZ) {
        float worldPerPx = (-P.z) / max(1.0e-4, uProj[1][1] * texSize.y * 0.5);
        effBias += exp2(hiZLod) * worldPerPx * 0.5;
    }

    float occlusion = 0.0;
    for (int i = 0; i < samples; i++) {
        float fi = float(i) + 0.5;
        float r = sqrt(fi / float(samples));                // disk radius 0..1 (even area coverage)
        float theta = fi * GOLDEN_ANGLE + rot;              // golden-angle spiral + per-pixel rotation
        // Hemisphere direction in tangent space: bias toward the normal (z = sqrt(1 - r^2)).
        vec3 dir = vec3(cos(theta) * r, sin(theta) * r, sqrt(max(0.0, 1.0 - r * r)));
        vec3 sampleDir = T * dir.x + B * dir.y + N * dir.z;
        vec3 samplePos = P + sampleDir * radius;

        // Reproject the sample to screen space.
        vec4 clip = uProj * vec4(samplePos, 1.0);
        if (clip.w <= 0.0) {
            continue;
        }
        vec2 suv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) {
            continue;
        }

        // Occluder view-space Z along this screen ray, two ways:
        //  * Hi-Z path: read the footprint-matched min-depth mip, so a sub-pixel sample still sees the
        //    nearest occluder in its neighbourhood -> distant contact shadows PERSIST. Full sample weight.
        //  * Default path: full-res depth + the screen-resolution weight. When the kernel is sub-pixel
        //    (far), suv collapses onto this pixel, the depth read returns this pixel's own depth, and
        //    near-tangent samples would self-occlude the coincident surface (the spurious, view-dependent
        //    darkening). Weighting by how far the reprojection lands (< ~1 texel = no info -> drop) makes
        //    distant AO FADE cleanly instead. Per-sample, so it can't paint the global radius-cap gradient.
        float occZ;
        float sampleW;
        if (hiZ) {
            occZ = -unpackDepth(textureLod(HiZSampler, suv, hiZLod)) * HIZ_FAR;
            sampleW = 1.0;
        } else {
            float resWeight = smoothstep(0.5, 1.5, length((suv - texCoord) * texSize));
            if (resWeight <= 0.0) {
                continue;
            }
            occZ = viewPosFromDepth(suv, texture(DepthSampler, suv).r).z;
            sampleW = resWeight;
        }

        // Occluded when real geometry is closer to the camera (less negative view Z) than the sample.
        // Z is negative going into the screen, so to *tighten* the test (require the occluder to be
        // meaningfully in front and avoid self-occlusion) the bias must move the threshold away from the
        // camera, i.e. SUBTRACT effBias. Adding it pushed the threshold toward the camera and loosened
        // the test — raising bias then increased dark haloing instead of reducing self-occlusion.
        //
        // Range/falloff: weight occlusion by how close the occluder sits to the shaded pixel in view
        // depth — full at |Δz|~0 (true contact), smoothly to zero by one kernel radius. The old
        // smoothstep(0,1, radius/|Δz|) kept FULL weight all the way out to |Δz|=radius (only halving at
        // 2*radius), so a thin tall occluder like an entity's base cast a wide drop-shadow ellipse onto
        // the surrounding floor. This distance-weighted falloff tightens that to the contact line without
        // shrinking the radius — which would reintroduce the screen-locked radial gradient the radius cap
        // caused. Terrain creases keep their AO: an adjacent wall sits at small |Δz|, weight stays ~1.
        float rangeCheck = smoothstep(radius, 0.0, abs(P.z - occZ));
        if (occZ >= samplePos.z - effBias) {
            occlusion += rangeCheck * sampleW;
        }
    }

    float ao = 1.0 - (occlusion / float(samples)) * uParams.z;   // intensity
    ao = pow(clamp(ao, 0.0, 1.0), uParams.w);                    // contrast
    fragColor = vec4(vec3(ao), 1.0);
}
