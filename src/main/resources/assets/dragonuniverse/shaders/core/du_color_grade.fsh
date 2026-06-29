#version 330

// Dragon Universe — Task 5 color grading (final pass). Operates on the composited, tone-mapped LDR frame
// (every other look-stack pass has already run), overwriting it with the graded result. Neutral params
// are an exact identity. Order: temperature/tint -> lift/gamma/gain -> contrast -> saturation, which is
// the conventional creative-grade order (white-balance first, then tonal shaping, then contrast/sat).

uniform sampler2D InSampler;   // composited LDR frame (pre-grade copy)

layout(std140) uniform DUGrade {
    vec4 uP0;       // x=contrast, y=saturation, z=temperature, w=tint
    vec4 uLift;     // rgb additive (shadows)
    vec4 uGamma;    // rgb per-channel gamma (midtones)
    vec4 uGain;     // rgb multiplicative (highlights)
    vec4 uSky;      // x=skyStrength, y=skyEnabled, z=gradeEnabled (static grade)
    vec4 uSkyWarm;  // rgb tint toward the sun
    vec4 uSkyCool;  // rgb tint away from the sun
};

// Prefix of the DUView buffer (only the two inverse matrices are needed to rebuild the view ray).
layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;   // view -> world (rotation)
};

// Prefix of the DUGodray buffer up to uSunDir (world-space sun direction; the only member we read).
layout(std140) uniform DUGodray {
    vec4 uGodSun;
    vec4 uGodParams;
    vec4 uGodSunColor;
    vec4 uGodRim;
    vec4 uGodRim2;
    vec4 uSunDir;    // xyz = world-space sun direction (view-independent)
};

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;

    // Static creative grade (independent toggle from the sky-tint below).
    if (uSky.z > 0.5) {
        // White balance. temp > 0 warms (R up, B down); tint > 0 toward magenta (G down), < 0 green.
        float temp = uP0.z;
        float tint = uP0.w;
        c *= vec3(1.0 + 0.2 * temp, 1.0 - 0.2 * tint, 1.0 - 0.2 * temp);

        // Lift / gamma / gain (ASC-CDL-style): gain scales, lift offsets shadows, gamma reshapes mids.
        c = uGain.rgb * c + uLift.rgb;
        c = pow(max(c, 0.0), 1.0 / max(uGamma.rgb, vec3(1.0e-3)));

        // Contrast around mid-grey, then saturation around luma.
        c = (c - 0.5) * uP0.x + 0.5;
        float l = dot(c, LUMA);
        c = mix(vec3(l), c, uP0.y);
    }

    // Directional sky-tint (sun-driven, view-independent of the sun's *screen* position). Rebuild this
    // pixel's world view ray and tint toward the warm colour when looking toward the world sun, the cool
    // colour when looking away, neutral side-on. A cheap full-frame atmospheric wash; strongest on the
    // sky but applied everywhere. Depth-independent: only the ray DIRECTION through the pixel matters.
    if (uSky.y > 0.5) {
        vec4 vp = uInvProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
        vec3 worldDir = normalize(mat3(uInvView) * normalize(vp.xyz / vp.w));  // camera -> pixel (world)
        float a = dot(worldDir, normalize(uSunDir.xyz));                       // -1 away .. +1 toward sun
        vec3 tintCol = a >= 0.0 ? mix(vec3(1.0), uSkyWarm.rgb, a)
                                : mix(vec3(1.0), uSkyCool.rgb, -a);
        c = mix(c, c * tintCol, uSky.x);
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
