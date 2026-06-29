#version 330

// Dragon Universe — Phase 5D texture-backed sky layer (equirectangular).
// Samples an authored panorama by the reconstructed world-space view ray. Cheaper per pixel than
// multi-octave fbm, and gives full art control. Output is premultiplied so the same shader serves
// both the additive and the premultiplied-translucent pipeline.

uniform sampler2D Sampler0;

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;  // inverse(projection * rotation-only modelview)
    vec4 uSunDir;
    vec4 uMisc;         // x=time
};

layout(std140) uniform DUSkyLayer {
    vec4 uColor;    // rgb tint
    vec4 uParams;   // x=opacity, y=rotation
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;

vec3 rayDir() {
    vec4 clip = vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec4 world = uInvViewProj * clip;
    return normalize(world.xyz / world.w);
}

vec3 rotY(vec3 d, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
}

void main() {
    vec3 d = rotY(rayDir(), uParams.y);
    float u = atan(d.z, d.x) / (2.0 * PI) + 0.5;
    float v = 0.5 - asin(clamp(d.y, -1.0, 1.0)) / PI; // up -> top of image
    vec4 tex = texture(Sampler0, vec2(u, v));

    float opacity = uParams.x;
    fragColor = vec4(tex.rgb * uColor.rgb * opacity, tex.a * opacity);
}
