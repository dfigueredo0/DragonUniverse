#version 330

// Dragon Universe — Phase 3b bloom bright-extract pass.
// Keeps only the portion of each pixel above a luma threshold; feeds the blur passes.

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

const float THRESHOLD = 0.75;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float k = max(luma - THRESHOLD, 0.0) / max(luma, 1e-4);
    fragColor = vec4(c * k, 1.0);
}
