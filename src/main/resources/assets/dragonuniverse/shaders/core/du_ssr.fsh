#version 330

// Dragon Universe — SSR (screen-space reflections), Step B: refined raymarch + robustness + sky fallback.
//
// Water only (gated by the water G-buffer mask, alpha > 0). For each water pixel: reconstruct the surface
// view position from the G-buffer depth, decode the view-space surface normal, reflect the camera->surface
// ray about it, then march that reflection ray in view space. A coarse linear march finds the first place
// the ray crosses behind the visible geometry; a binary search then refines the crossing for a sharp
// intersection, and a thickness test rejects crossings that pass through thin geometry into the void.
//
// Robustness over Step A: sky/empty-depth texels are skipped (never a false hit); the crossing is detected
// by SIGN change (front->behind) so a large stride can't step over a surface; reflections fade to the sky
// fallback near the screen borders instead of hard-cutting; and rays that find no hit (left the screen /
// ran out of distance) sample a horizon->zenith sky gradient with a reflected sun disc — so open water
// toward the sky reads as sky, not black. Still RAW reflected colour (no fresnel blend with the surface —
// that is Step C). Clean-room: standard SSR raymarch + binary-search refine, written from the concept.

uniform sampler2D SceneColorSampler;  // copy of the composited scene colour (LDR)
uniform sampler2D SceneDepthSampler;  // opaque scene depth (no water), NEAREST
uniform sampler2D WaterGbufSampler;   // rg = oct view normal, b = fresnel, a = water surface depth (mask)
uniform sampler2D HistorySampler;     // previous frame's SSR output (rgb), a = that frame's water mask

layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;   // view -> world (rotation; for the world-space sky fallback)
    mat4 uProj;      // view -> clip (forward; for reprojecting march steps)
};

layout(std140) uniform DUSsr {
    vec4 uP0;     // x = maxSteps, y = maxDistance, z = thickness, w = stride
    vec4 uP1;     // x = refineSteps, y = edgeFade, z = fallbackEnabled, w = fresnelStrength
    vec4 uSky0;   // rgb = zenith colour, w = sun-glow strength
    vec4 uSky1;   // rgb = horizon colour, w = sun-glow sharpness
    vec4 uP2;     // x = roughness (tap-blur), y = temporalEnabled, z = effective history feedback
};

// Roughness tap-blur: max screen-space radius (UV) reached at roughness = 1, over a few spiral taps.
const float ROUGH_MAX_RADIUS = 0.04;
const int ROUGH_TAPS = 5;
const float TWO_PI = 6.28318530718;

// Prefix of the DUGodray buffer up to uSunDir — reuse the look stack's sun colour + world sun direction
// for the reflected sun disc (same members the color grade reads).
layout(std140) uniform DUGodray {
    vec4 uGodSun;
    vec4 uGodParams;
    vec4 uGodSunColor;   // rgb = sun colour
    vec4 uGodRim;
    vec4 uGodRim2;
    vec4 uSunDir;        // xyz = world-space sun direction
};

in vec2 texCoord;
out vec4 fragColor;

vec3 viewPosFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = uInvProj * ndc;
    return v.xyz / v.w;   // perspective divide -> linear view-space position
}

// Octahedral decode — matches du_octEncode in the water inject.
vec3 octDecode(vec2 e) {
    vec3 n = vec3(e.x, e.y, 1.0 - abs(e.x) - abs(e.y));
    float t = max(-n.z, 0.0);
    n.x += n.x >= 0.0 ? -t : t;
    n.y += n.y >= 0.0 ? -t : t;
    return normalize(n);
}

// Project a view-space position to screen UV. w<=0 (behind camera) flagged via the returned .z = -1.
vec3 toScreen(vec3 viewPos) {
    vec4 clip = uProj * vec4(viewPos, 1.0);
    if (clip.w <= 0.0) {
        return vec3(0.0, 0.0, -1.0);
    }
    return vec3(clip.xy / clip.w * 0.5 + 0.5, 1.0);
}

// Sky fallback: horizon->zenith gradient by the reflection ray's world-space elevation, plus a reflected
// sun disc tinted by the look stack's sun colour. Reused for misses and for the screen-edge fade.
vec3 skyFallback(vec3 viewR) {
    vec3 wR = normalize(mat3(uInvView) * viewR);
    float t = clamp(wR.y, 0.0, 1.0);
    vec3 sky = mix(uSky1.rgb, uSky0.rgb, t);
    float s = max(dot(wR, normalize(uSunDir.xyz)), 0.0);
    sky += uGodSunColor.rgb * (pow(s, max(uSky1.w, 1.0)) * uSky0.w);
    return sky;
}

// Interleaved gradient noise -> per-pixel rotation, decorrelates the roughness taps without a texture.
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// Sample the reflected scene colour at the hit UV. roughness > 0 cone-blurs it with a few jittered spiral
// taps so the wave-perturbed normals read as a slightly broken-up reflection rather than a hard mirror.
vec3 sampleReflection(vec2 uv, float rough) {
    if (rough <= 0.0) {
        return texture(SceneColorSampler, uv).rgb;
    }
    float radius = rough * ROUGH_MAX_RADIUS;
    float rot = ign(gl_FragCoord.xy) * TWO_PI;
    vec3 sum = texture(SceneColorSampler, uv).rgb;
    float wsum = 1.0;
    for (int k = 0; k < ROUGH_TAPS; k++) {
        float a = rot + float(k) * (TWO_PI / float(ROUGH_TAPS));
        float r = radius * (0.4 + 0.6 * float(k + 1) / float(ROUGH_TAPS));
        vec2 o = vec2(cos(a), sin(a)) * r;
        vec2 suv = clamp(uv + o, 0.0, 1.0);
        sum += texture(SceneColorSampler, suv).rgb;
        wsum += 1.0;
    }
    return sum / wsum;
}

// 1 in the screen interior, smoothly 0 at the borders (over uP1.y). Reflections fade to sky near edges.
float edgeFactor(vec2 uv) {
    float fw = max(uP1.y, 1.0e-4);
    vec2 e = smoothstep(0.0, fw, uv) * smoothstep(0.0, fw, 1.0 - uv);
    return e.x * e.y;
}

void main() {
    vec4 g = texture(WaterGbufSampler, texCoord);
    vec3 scene = texture(SceneColorSampler, texCoord).rgb;

    // Non-water pixels (mask 0) pass straight through. Alpha = 0 so it's the water mask in the history
    // buffer (copied from this output): next frame a pixel only blends history if it was water here.
    if (g.a <= 0.0) {
        fragColor = vec4(scene, 0.0);
        return;
    }

    vec3 N = octDecode(g.rg);                          // view-space surface normal
    vec3 P = viewPosFromDepth(texCoord, g.a);          // water surface view-space position
    vec3 V = normalize(P);                             // camera -> surface (camera at origin in view space)
    vec3 R = reflect(V, N);                            // reflection direction

    int maxSteps = int(uP0.x);
    float maxDist = uP0.y;
    float thickness = uP0.z;
    float stride = max(uP0.w, 1.0e-3);
    int refineSteps = int(uP1.x);

    // Coarse linear march: find the first step that crosses BEHIND the visible geometry (sign change of
    // diff = sceneZ - rayZ, both negative going into the screen). Tracking the previous (in-front) sample
    // lets the binary search bracket the surface even when a large stride overshoots it.
    vec3 prevPos = P;
    vec3 pos = P;
    float marched = 0.0;
    bool crossed = false;
    for (int i = 0; i < maxSteps; i++) {
        prevPos = pos;
        pos += R * stride;
        marched += stride;
        if (marched > maxDist) {
            break;                                     // out of range -> fallback
        }
        if (pos.z >= 0.0) {
            break;                                     // stepped behind the camera
        }
        vec3 sp = toScreen(pos);
        if (sp.z < 0.0 || sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0) {
            break;                                     // left the screen -> fallback
        }
        float sd = texture(SceneDepthSampler, sp.xy).r;
        if (sd >= 1.0) {
            continue;                                  // sky / no geometry here: not an occluder
        }
        float diff = viewPosFromDepth(sp.xy, sd).z - pos.z;
        if (diff > 0.0) {
            crossed = true;                            // ray is now behind geometry; refine below
            break;
        }
    }

    vec3 result;
    bool hit = false;
    vec2 hitUv = vec2(0.0);
    if (crossed) {
        // Binary search between prevPos (in front) and pos (behind) for the surface crossing.
        vec3 a = prevPos;
        vec3 b = pos;
        for (int j = 0; j < refineSteps; j++) {
            vec3 mid = (a + b) * 0.5;
            vec3 sp = toScreen(mid);
            float sd = texture(SceneDepthSampler, sp.xy).r;
            float mdiff = viewPosFromDepth(sp.xy, sd).z - mid.z;
            if (mdiff > 0.0) {
                b = mid;                               // crossing is before mid
            } else {
                a = mid;                               // crossing is after mid
            }
        }
        vec3 sp = toScreen(b);
        float sd = texture(SceneDepthSampler, sp.xy).r;
        float bdiff = viewPosFromDepth(sp.xy, sd).z - b.z;
        // Accept only a thin crossing (true surface contact); a deep crossing means the ray passed through
        // thin geometry into whatever is far behind it -> reject and fall back to sky.
        if (sd < 1.0 && bdiff >= 0.0 && bdiff < thickness) {
            hit = true;
            hitUv = sp.xy;
        }
    }

    // Resolve the reflected colour + how much of it to apply (reflMask), handling both fallback modes:
    //  * fallback ON  — always reflect something; near screen borders a hit dissolves into reflected sky.
    //  * fallback OFF — only on-screen hits reflect; near borders the reflection AMOUNT fades out (back to
    //                   the bare water surface), and a miss adds nothing. Never tint the water toward black.
    bool fallbackOn = uP1.z > 0.5;
    vec3 sky = fallbackOn ? skyFallback(R) : vec3(0.0);
    float reflMask;
    if (hit) {
        float edge = edgeFactor(hitUv);
        vec3 refl = sampleReflection(hitUv, uP2.x);
        if (fallbackOn) {
            result = mix(sky, refl, edge);   // dissolve into reflected sky near the borders
            reflMask = 1.0;
        } else {
            result = refl;                   // no sky: keep the reflection colour, fade its amount instead
            reflMask = edge;
        }
    } else {
        result = sky;
        reflMask = fallbackOn ? 1.0 : 0.0;   // no hit + no fallback -> nothing to reflect
    }

    // Step C — the wet look: fresnel-weighted blend of the reflection into the already-shaded water
    // surface. The water surface colour is `scene` (sampled at this pixel above); grazing angles (high
    // stored fresnel) reflect strongly, top-down (low fresnel) keeps the water tint. fresnelStrength
    // (uP1.w) scales it; reflMask gates the blend off where there's genuinely nothing to reflect.
    float fres = clamp(g.b * uP1.w, 0.0, 1.0) * reflMask;
    vec3 cur = mix(scene, result, fres);

    // Step D — temporal accumulation. SSR shimmers under the animated wave normals (worst with a still
    // camera). Blend with last frame's result at this pixel by the effective feedback (uP2.z = maxFeedback
    // * camera-stillness, computed CPU-side), so a still camera averages the boil out while motion (low
    // feedback) avoids ghosting. Gated by the history's stored water mask (uP2.z is already 0 under motion).
    vec3 outc = cur;
    if (uP2.y > 0.5) {
        vec4 hist = texture(HistorySampler, texCoord);
        outc = mix(cur, hist.rgb, uP2.z * hist.a);
    }

    // Alpha = water mask (1 here) so the history copy carries it for next frame's disocclusion gate.
    fragColor = vec4(outc, 1.0);
}
