#version 330

// Dragon Universe — Hi-Z pyramid, level 0 (linearize). Reconstructs view-space depth from the scene depth
// buffer (same uInvProj as SSAO, so the values match), normalizes to [0,1] by a far constant, and
// float-packs it into RGBA8 so the min-downsample keeps ~32-bit precision. Smaller value = closer to the
// camera, so a MIN downsample yields the nearest occluder (the sky, at the far plane, packs to ~1).

uniform sampler2D DepthSampler;

layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;   // view -> world (rotation)
    mat4 uProj;      // view -> clip
};

in vec2 texCoord;
out vec4 fragColor;

// Normalization range for linear depth (blocks). Covers a generous render distance; beyond it clamps to 1.
const float FAR = 1024.0;

vec4 packDepth(float v) {
    v = clamp(v, 0.0, 1.0);
    vec4 enc = fract(v * vec4(1.0, 255.0, 65025.0, 16581375.0));
    enc -= enc.yzww * vec4(1.0 / 255.0, 1.0 / 255.0, 1.0 / 255.0, 0.0);
    return enc;
}

void main() {
    float d = texture(DepthSampler, texCoord).r;
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 v = uInvProj * ndc;
    float viewZ = v.z / v.w;                       // negative going into the screen
    float lin = clamp(-viewZ / FAR, 0.0, 1.0);     // 0 near .. 1 far
    fragColor = packDepth(lin);
}
