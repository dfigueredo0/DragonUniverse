#version 330

// Dragon Universe — Hi-Z pyramid, min downsample. Reads the previous level (bound as a single-level view)
// and writes the MIN (closest) of each 2x2 block into this level. Clamps source coords for odd sizes so a
// shrinking dimension doesn't read out of bounds.

uniform sampler2D InSampler;   // previous (finer) Hi-Z level, packed depth

in vec2 texCoord;
out vec4 fragColor;

float unpackDepth(vec4 c) {
    return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}

vec4 packDepth(float v) {
    v = clamp(v, 0.0, 1.0);
    vec4 enc = fract(v * vec4(1.0, 255.0, 65025.0, 16581375.0));
    enc -= enc.yzww * vec4(1.0 / 255.0, 1.0 / 255.0, 1.0 / 255.0, 0.0);
    return enc;
}

void main() {
    ivec2 sz = textureSize(InSampler, 0);
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    float a = unpackDepth(texelFetch(InSampler, min(base + ivec2(0, 0), sz - 1), 0));
    float b = unpackDepth(texelFetch(InSampler, min(base + ivec2(1, 0), sz - 1), 0));
    float c = unpackDepth(texelFetch(InSampler, min(base + ivec2(0, 1), sz - 1), 0));
    float d = unpackDepth(texelFetch(InSampler, min(base + ivec2(1, 1), sz - 1), 0));
    fragColor = packDepth(min(min(a, b), min(c, d)));
}
