#version 330

// Dragon Universe — Phase 3c depth visualization.
// The raw DEPTH32 buffer is hyperbolic (almost everything sits near 1.0), so sampling it
// directly reads as near-uniform red. This pass linearizes it to a readable grayscale gradient
// (near = black, far/sky = white) for the Target Inspector.

uniform sampler2D DepthSampler;

layout(std140) uniform DUFrame {
    vec4 uTimeInfo;
    vec4 uSunFog;
    vec4 uSkyBloom;
    vec4 uProjInfo;   // x=near, y=far
};

in vec2 texCoord;
out vec4 fragColor;

float linearizeDepth(float d, float near, float far) {
    return near * far / (far - d * (far - near));
}

void main() {
    float near = uProjInfo.x;
    float far = uProjInfo.y;
    float d = texture(DepthSampler, texCoord).r;
    float nd = clamp(linearizeDepth(d, near, far) / far, 0.0, 1.0);
    fragColor = vec4(vec3(nd), 1.0);
}
