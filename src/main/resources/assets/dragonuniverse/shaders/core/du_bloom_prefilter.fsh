#version 330

// Dragon Universe — Task 2 bloom: bright-extract from the float HDR overlay. Now meaningful because
// the overlay holds additive VFX values that exceed 1.0. Soft-knee threshold (Unity-style) so the
// transition into bloom is smooth rather than a hard cutoff. Output stays HDR (RGBA16F target).

uniform sampler2D InSampler;   // HDR overlay (VFX)

// Matches DULookData: x=exposure, y=operator, z=bloomIntensity, w=bloomThreshold.
layout(std140) uniform DULook {
    vec4 uTonemap;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));

    float threshold = uTonemap.w;
    const float knee = 0.5;

    // Soft knee: quadratic ramp across [threshold-knee, threshold+knee], then hard above.
    float soft = clamp(luma - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float contribution = max(soft, luma - threshold) / max(luma, 1e-4);

    fragColor = vec4(c * contribution, 1.0);
}
