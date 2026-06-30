#version 330

// Dragon Universe — Stage C: PCF-soft shadow apply. Screen-space deferred: reconstruct each terrain
// fragment's camera-relative world position from the main depth buffer, project it into the sun's-eye
// light space, then MULTI-TAP percentage-closer-filter the shadow map and MULTIPLY-darken lit,
// sun-facing surfaces by the shadowed fraction. Soft edges (Vogel-spiral PCF, per-pixel rotated by
// interleaved-gradient noise — the same textureless dither as SSAO, purely SPATIAL so it's identical
// every frame -> temporally stable with no TAA to converge a per-frame rotation). Terrain only for now
// (entities/clouds are skipped via the coverage tag). Lit/identity pixels output 1.0 (multiply no-op).

uniform sampler2D DepthSampler;    // main scene depth (.r)
uniform sampler2D NormalSampler;   // geometry-normals MRT: rgb = world-space normal (terrain), a = coverage
uniform sampler2D ShadowSampler;   // sun's-eye depth map (.r), orthographic (linear depth)
uniform sampler2D ColoredDepthSampler; // Stage D: translucent-occluder sun's-eye depth (.r)
uniform sampler2D ColoredTintSampler;  // Stage D: translucent-occluder tint (rgb) + opacity (a)

layout(std140) uniform DUShadow {  // DUShadowData
    mat4 uLightVP;                  // camera-relative light view-projection (the map was drawn with this)
    mat4 uInvProjBob;               // bob-inclusive inverse projection (matches the depth buffer)
    mat4 uInvViewBob;               // bob-inclusive inverse modelview (-> camera-relative world)
    vec4 uBias;                     // x=depthBias, y=normalOffset, z=slopeBias, w=strength
    vec4 uSun;                      // xyz = world sun dir, w = horizon fade
    vec4 uTint;                     // rgb = shadow tint, w = texelWorld (2*coverage/resolution)
    vec4 uPcf;                      // x=filter radius (texels), y=tap count, z=penumbra scale, w=pad
    vec4 uColored;                  // Stage D: x=strength, y=filter taps, z=filter radius (texels), w=enabled
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float GOLDEN_ANGLE = 2.39996323;     // Vogel-spiral increment (same kernel as SSAO)

vec3 viewPosFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = uInvProjBob * ndc;
    return v.xyz / v.w;            // linear view-space position
}

// Interleaved gradient noise -> per-pixel rotation angle (decorrelates the PCF kernel without a texture).
// SPATIAL ONLY (no frame term): identical every frame, so the dither never boils — the static spatial
// pattern is hidden by the multi-tap spiral + the per-pixel rotation, the same trade SSAO makes.
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 nSample = texture(NormalSampler, texCoord);
    float coverage = nSample.a;
    float d = texture(DepthSampler, texCoord).r;

    // Terrain only (world-space normals, coverage ~1.0). Sky/entities/clouds -> identity (no darkening).
    if (coverage < 0.9 || d >= 1.0) {
        fragColor = vec4(1.0);
        return;
    }

    vec3 N = normalize(nSample.rgb * 2.0 - 1.0);   // world-space
    vec3 L = normalize(uSun.xyz);
    float NdotL = dot(N, L);

    // Only sun-facing surfaces, faded out near/below the horizon (back-faces get no direct sun anyway).
    float gate = smoothstep(0.0, 0.25, NdotL) * uSun.w;
    if (gate <= 0.0) {
        fragColor = vec4(1.0);
        return;
    }

    // Camera-relative world position (bob-inclusive) — the exact space the light matrix was built in.
    vec4 wr = uInvViewBob * vec4(viewPosFromDepth(texCoord, d), 1.0);
    vec3 worldRel = wr.xyz / wr.w;

    // Normal-offset bias (the ONLY range-independent bias) then project into light space. Push the receiver
    // off its surface in WORLD space, sized to ONE shadow texel (uTint.w = 2*coverage/resolution, world
    // blocks per texel) times the grazing slope. This clears self-shadow acne on steep faces with the
    // minimum push, so it never peter-pans short casters and auto-tracks the texel as render distance
    // rescales coverage. uBias.y = base texels, uBias.z = extra texels per unit grazing slope.
    float graze = clamp(tan(acos(clamp(NdotL, 0.0, 1.0))), 0.0, 4.0);
    worldRel += N * uTint.w * (uBias.y + uBias.z * graze);
    vec4 lc = uLightVP * vec4(worldRel, 1.0);
    vec3 ndc = lc.xyz / lc.w;
    vec2 suv = ndc.xy * 0.5 + 0.5;
    float fragDepth = ndc.z * 0.5 + 0.5;

    // Outside the map (or behind the light) -> lit.
    if (any(lessThan(suv, vec2(0.0))) || any(greaterThan(suv, vec2(1.0))) || fragDepth > 1.0) {
        fragColor = vec4(1.0);
        return;
    }

    // Depth-compare bias is fully baked into the world-space normal offset above; compare nearly raw.
    // uBias.x stays as a tiny optional normalized-depth nudge for stubborn acne — keep it ~0.
    float refDepth = fragDepth - uBias.x;

    // ---- PCF ---------------------------------------------------------------------------------------
    vec2 texel = 1.0 / vec2(textureSize(ShadowSampler, 0));
    int taps = int(max(1.0, uPcf.y));
    float radius = uPcf.x;                              // filter half-extent, in shadow-map texels
    float rot = ign(gl_FragCoord.xy) * 2.0 * PI;        // per-pixel kernel rotation (spatial, frame-stable)

    // Optional distance-scaled penumbra (PCSS-lite): a cheap blocker search widens the filter where the
    // occluder sits far in front of the receiver (contact stays crisp, far casts soften). Off when
    // uPcf.z <= 0 -> constant-radius PCF, the single loop below at zero extra cost. The ortho map's depth
    // is LINEAR, so the receiver–blocker gap (light-clip units) scales the radius directly; penumbra is a
    // raw texels-per-unit-gap dial tuned by eye in the panel (not physically calibrated — ~100–300 reads
    // well at the default coverage/res), clamped so a deep gap can't blow the tap spread up unboundedly.
    if (uPcf.z > 0.0) {
        float blockerSum = 0.0;
        float blockerCount = 0.0;
        int bsTaps = max(4, taps / 2);
        float bsRadius = radius * 2.0;
        for (int i = 0; i < bsTaps; i++) {
            float fi = float(i) + 0.5;
            float r = sqrt(fi / float(bsTaps));
            float theta = fi * GOLDEN_ANGLE + rot;
            vec2 off = vec2(cos(theta), sin(theta)) * (r * bsRadius) * texel;
            float occ = texture(ShadowSampler, suv + off).r;
            if (occ < refDepth) { blockerSum += occ; blockerCount += 1.0; }
        }
        if (blockerCount < 0.5) { fragColor = vec4(1.0); return; }   // nothing in front -> fully lit
        float avgBlocker = blockerSum / blockerCount;
        radius = min(radius + (refDepth - avgBlocker) * uPcf.z, 48.0);
    }

    float shadowed = 0.0;
    for (int i = 0; i < taps; i++) {
        float fi = float(i) + 0.5;
        float r = sqrt(fi / float(taps));               // even disk-area coverage (matches SSAO)
        float theta = fi * GOLDEN_ANGLE + rot;          // golden-angle spiral + per-pixel rotation
        vec2 off = vec2(cos(theta), sin(theta)) * (r * radius) * texel;
        float occ = texture(ShadowSampler, suv + off).r;
        shadowed += (refDepth <= occ) ? 0.0 : 1.0;      // 1 per occluded tap
    }
    float shadowFrac = shadowed / float(taps);          // 0 lit .. 1 fully shadowed (the soft penumbra)
    // Fade the shadow out as the receiver nears the map's edge, so the shadow-distance boundary is a soft
    // ramp instead of a hard line (the cutoff would otherwise read as a visible ring at coverage radius).
    float edge = min(min(suv.x, 1.0 - suv.x), min(suv.y, 1.0 - suv.y));
    float distFade = smoothstep(0.0, 0.06, edge);
    float shadow = shadowFrac * gate * uBias.w * distFade;

    // Start from the opaque shadow multiply (lit -> white, shadowed -> dark uTint).
    vec3 mult = mix(vec3(1.0), uTint.rgb, shadow);

    // ---- Stage D: colored shadows ------------------------------------------------------------------
    // Where the OPAQUE map says lit but a translucent occluder is between the fragment and the sun, tint the
    // sunlight by the occluder's stored color instead of darkening. Same Vogel spiral + same per-pixel `rot`
    // as the opaque PCF (so the colored edge's penumbra matches), but its own (cheaper) tap count/radius —
    // the soft edge comes from the coverage filter; the tint COLOR is a single center tap (it doesn't alias
    // like the coverage edge does). Only applied in the sun-lit fraction (1 - shadowFrac) so it never
    // double-tints under the opaque shadow.
    if (uColored.w > 0.5) {
        int ctaps = int(max(1.0, uColored.y));
        float crad = uColored.z;
        float coloredFrac = 0.0;
        for (int i = 0; i < ctaps; i++) {
            float fi = float(i) + 0.5;
            float r = sqrt(fi / float(ctaps));
            float theta = fi * GOLDEN_ANGLE + rot;
            vec2 off = vec2(cos(theta), sin(theta)) * (r * crad) * texel;
            float cocc = texture(ColoredDepthSampler, suv + off).r;
            coloredFrac += (refDepth <= cocc) ? 0.0 : 1.0;   // translucent occluder closer than the receiver
        }
        coloredFrac /= float(ctaps);
        vec4 ctint = texture(ColoredTintSampler, suv);       // rgb = occluder tint, a = opacity
        float camt = coloredFrac * ctint.a * uColored.x * gate * distFade * (1.0 - shadowFrac);
        mult *= mix(vec3(1.0), ctint.rgb, camt);
    }

    fragColor = vec4(mult, 1.0);
}
