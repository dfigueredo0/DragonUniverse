#version 330

// Dragon Universe — Phase 5D cubemap sky layer.
// Samples a 6-face cubemap directly by the reconstructed world-space view ray (like vanilla's
// panorama). Premultiplied output so the same shader serves additive and translucent pipelines.

uniform samplerCube Sampler0;

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;
    vec4 uSunDir;
    vec4 uMisc;
};

layout(std140) uniform DUSkyLayer {
    vec4 uColor;    // rgb tint
    vec4 uParams;   // x=opacity, y=rotation
};

in vec2 texCoord;
out vec4 fragColor;

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
    vec4 tex = texture(Sampler0, d);
    float opacity = uParams.x;
    fragColor = vec4(tex.rgb * uColor.rgb * opacity, tex.a * opacity);
}
