#version 330

// Dragon Universe — Phase 5D starfield sky layer (additive).
// Reconstructs a world-space view ray per pixel from the camera basis, hashes direction-space
// cells into sparse round twinkling stars.

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;  // inverse(projection * rotation-only modelview)
    vec4 uSunDir;       // xyz sun direction
    vec4 uMisc;         // x=time
};

layout(std140) uniform DUSkyLayer {
    vec4 uColor;    // rgb
    vec4 uParams;   // x=opacity, y=rotation, z=scale(density), w=seed
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

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 d = rotY(rayDir(), uParams.y + uMisc.x * 0.0004 + uParams.w);

    vec3 gp = d * uParams.z;
    vec3 cell = floor(gp);
    vec3 f = fract(gp) - 0.5;

    float present = step(0.99, hash13(cell));
    vec3 jitter = (vec3(hash13(cell + 1.3), hash13(cell + 2.7), hash13(cell + 4.1)) - 0.5) * 0.7;
    float star = present * smoothstep(0.09, 0.0, length(f - jitter));
    float twinkle = 0.6 + 0.4 * sin(uMisc.x * 0.06 + hash13(cell) * 200.0);

    vec3 col = uColor.rgb * star * twinkle * uParams.x;
    fragColor = vec4(col, 1.0);
}
