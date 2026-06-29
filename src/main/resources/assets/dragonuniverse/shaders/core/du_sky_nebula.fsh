#version 330

// Dragon Universe — Phase 5D nebula sky layer (additive). fbm value-noise over the view ray.

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;  // inverse(projection * rotation-only modelview)
    vec4 uSunDir;
    vec4 uMisc;         // x=time
};

layout(std140) uniform DUSkyLayer {
    vec4 uColor;
    vec4 uParams;   // x=opacity, y=rotation, z=scale, w=seed
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

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    // 3 octaves: enough for soft nebula clouds at a fraction of the per-pixel cost of 5.
    float amp = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 3; i++) {
        sum += amp * vnoise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec3 d = rotY(rayDir(), uParams.y + uParams.w);
    float n = fbm(d * uParams.z + vec3(uMisc.x * 0.001));
    n = smoothstep(0.45, 0.95, n);
    vec3 col = uColor.rgb * n * uParams.x;
    fragColor = vec4(col, 1.0);
}
