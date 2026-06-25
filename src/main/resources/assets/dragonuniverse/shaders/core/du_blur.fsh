#version 330

// Dragon Universe — Phase 3b separable Gaussian blur.
// Direction is chosen by the HORIZONTAL shader define (set on the BLUR_H pipeline); the BLUR_V
// pipeline omits it. Texel size is derived from the bound texture, so it works at any resolution.

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef HORIZONTAL
    vec2 dir = vec2(texel.x, 0.0);
#else
    vec2 dir = vec2(0.0, texel.y);
#endif

    float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 result = texture(InSampler, texCoord).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        result += texture(InSampler, texCoord + dir * float(i)).rgb * w[i];
        result += texture(InSampler, texCoord - dir * float(i)).rgb * w[i];
    }
    fragColor = vec4(result, 1.0);
}
