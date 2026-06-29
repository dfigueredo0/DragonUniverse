#version 330

// Dragon Universe — Hi-Z debug viz. Unpacks a chosen pyramid level (bound as a single-level view) to a
// grayscale linear-depth image for the Target Inspector (the raw packed RGBA8 is unreadable). Near = dark,
// far = white; higher levels look blockier (the min-downsampled neighbourhood).

uniform sampler2D InSampler;   // one Hi-Z level, packed depth

in vec2 texCoord;
out vec4 fragColor;

float unpackDepth(vec4 c) {
    return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}

void main() {
    float lin = unpackDepth(texture(InSampler, texCoord));
    fragColor = vec4(vec3(lin), 1.0);
}
