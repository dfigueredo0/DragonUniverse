#version 330

// Dragon Universe — SSAO composite (stop 2).
//
// Multiplies the (blurred) ambient-occlusion factor into the scene colour. Drawn under a MULTIPLY
// blend (result = src * dst), so this shader only outputs the AO factor as rgb and the blend does
// scene *= ao. Runs BEFORE the additive passes (bloom/tonemap/godray/rim) so light those passes add
// isn't darkened by the occlusion. Uncovered pixels (sky/entities/water) carry AO = 1 in the buffer,
// so multiplying leaves them untouched.

uniform sampler2D InSampler;   // blurred AO (grayscale; read .r)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float ao = texture(InSampler, texCoord).r;
    fragColor = vec4(vec3(ao), 1.0);
}
