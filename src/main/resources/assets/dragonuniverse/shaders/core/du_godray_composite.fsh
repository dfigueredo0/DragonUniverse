#version 330

// Dragon Universe — Task 4 god rays, step 3: tint the grayscale ray intensity by the sun colour and
// gain, and add it onto the scene (the pipeline uses additive blending). Where there are no rays the
// contribution is 0, so the base scene is untouched.

uniform sampler2D InSampler;   // blurred ray intensity (step 2)

layout(std140) uniform DUGodray {
    vec4 uSun;       // xy = sun screen uv, z = visibility gate (0..1), w = intensity
    vec4 uParams;
    vec4 uSunColor;  // rgb, w = godrayEnabled
    vec4 uRim;
    vec4 uRim2;
    vec4 uSunDir;    // xyz = world-space sun direction (unused here; kept for std140 layout parity)
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float gate = uSunColor.w * uSun.z;        // god rays enabled * sun visibility/edge fade
    float rays = texture(InSampler, texCoord).r;
    fragColor = vec4(uSunColor.rgb * rays * uSun.w * gate, 1.0);
}
