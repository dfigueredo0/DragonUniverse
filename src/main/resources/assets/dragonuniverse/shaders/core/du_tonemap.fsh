#version 330

// Dragon Universe — Task 1 HDR foundation: tone map the float HDR scene buffer down to the LDR
// main target. This is the foundation of the look stack — every later stage (bloom, god rays,
// grading) composites in HDR *before* this pass, so values are physically meaningful here.

uniform sampler2D InSampler;     // RGBA16F HDR overlay (VFX)
uniform sampler2D BloomSampler;  // RGBA16F blurred bloom (Task 2)

// Matches DULookData: x = exposure, y = operator (0 = Reinhard, 1 = ACES),
// z = bloom intensity, w = bloom threshold (used by the prefilter, not here).
layout(std140) uniform DULook {
    vec4 uTonemap;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 reinhard(vec3 c) {
    return c / (c + vec3(1.0));
}

// Narkowicz 2015 ACES filmic approximation — cheap, no matrices, good highlight rolloff.
vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 hdr = texture(InSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;

    vec3 c = (hdr + bloom * uTonemap.z) * max(uTonemap.x, 0.0);   // + bloom, then exposure

    vec3 ldr = (uTonemap.y > 0.5) ? aces(c) : reinhard(c);
    fragColor = vec4(ldr, 1.0);
}
