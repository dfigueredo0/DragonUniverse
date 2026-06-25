#version 330

// Dragon Universe — Phase 3b depth fog pass.
// Samples scene color + the main depth buffer and blends toward the sky-tint fog color by
// distance, scaled by fog density. No-op when fogDensity == 0.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform DUFrame {
    vec4 uTimeInfo;   // x=time, y=partialTick
    vec4 uSunFog;     // xyz=sunDir, w=fogDensity
    vec4 uSkyBloom;   // xyz=skyTint, w=bloomIntensity
    vec4 uProjInfo;   // x=near, y=far
};

in vec2 texCoord;
out vec4 fragColor;

// Reconstruct view-space distance from a zero-to-one [0,1] depth buffer (MC 26.1 uses
// glClipControl ZERO_TO_ONE, and the depth buffer is cleared to 1.0 = far).
float linearizeDepth(float d, float near, float far) {
    return near * far / (far - d * (far - near));
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float fog = uSunFog.w;
    if (fog <= 0.0) {
        fragColor = scene;
        return;
    }

    float near = uProjInfo.x;
    float far = uProjInfo.y;

    float depth = texture(DepthSampler, texCoord).r;
    float dist = linearizeDepth(depth, near, far);

    // Normalize by far so the gradient (and the sky/horizon boundary) is consistent regardless
    // of render distance. Sky/cleared pixels (depth == 1.0) map to dist == far -> nd == 1.0,
    // matching the terrain just in front of them, so there's no hard seam at the horizon.
    float nd = clamp(dist / far, 0.0, 1.0);
    float f = clamp(1.0 - exp(-fog * nd * 4.0), 0.0, 1.0);

    vec3 fogColor = uSkyBloom.rgb;
    fragColor = vec4(mix(scene.rgb, fogColor, f), scene.a);
}
