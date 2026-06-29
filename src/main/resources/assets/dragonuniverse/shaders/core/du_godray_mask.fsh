#version 330

// Dragon Universe — Task 4 god rays, step 1: occlusion mask. The sky (far plane) is the light source;
// any geometry occludes it. The radial blur (step 2) then smears this sky light toward the sun,
// producing crepuscular rays streaming around terrain/objects. Output is half-res grayscale.

uniform sampler2D DepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float d = texture(DepthSampler, texCoord).r;
    float light = (d >= 1.0) ? 1.0 : 0.0;   // 1 = unoccluded sky, 0 = geometry
    fragColor = vec4(vec3(light), 1.0);
}
