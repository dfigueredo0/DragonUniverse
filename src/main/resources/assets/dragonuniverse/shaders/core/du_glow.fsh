// assets/dragonuniverse/shaders/core/du_glow.fsh
#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

// Linear, hue-preserving gain into the HDR range. The old LDR path used pow(rgb,0.75)*1.7 to make
// glow "read solid", but the per-channel pow distorts hue and the fixed gain pre-saturates colors
// before they even accumulate — which the HDR look stack then can't recover (dense additive geometry
// like the beam tube pinned near white). With the float HDR overlay + tone mapping doing the
// highlight rolloff, glow just needs a straight gain: bright cores exceed 1.0 and the tonemap rolls
// them off while preserving color. ~2.0 keeps mid-tones close to the old look. Tune brightness per
// VFX via alpha; soft edges are dimmer now (no midtone lift) — that's correct for additive glow.
const float GLOW_INTENSITY = 2.0;

void main() {
    vec4 tex = texture(Sampler0, texCoord);

    vec3 rgb = vertexColor.rgb * tex.rgb;
    float a  = vertexColor.a * tex.a;

    if (a < 0.01) discard;

    rgb *= GLOW_INTENSITY;
    fragColor = vec4(rgb, a);
}
