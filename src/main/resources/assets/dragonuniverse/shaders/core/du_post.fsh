#version 330

// Dragon Universe — Phase 3 composite (final) pass.
// Combines the (fogged) scene with the blurred bloom buffer and applies sky-tint + a time pulse.
// This is the pass that consumes the per-frame DUFrame std140 UBO uploaded by DUFrameData.

uniform sampler2D InSampler;     // scene color (already fogged by the fog pass)
uniform sampler2D BloomSampler;  // blurred bright-extract buffer

// Layout MUST match DUFrameData's CPU writes (STD140_SIZE = 48 = three vec4s):
//   uTimeInfo : x=time, y=partialTick
//   uSunFog   : xyz=sunDir, w=fogDensity
//   uSkyBloom : xyz=skyTint, w=bloomIntensity
layout(std140) uniform DUFrame {
    vec4 uTimeInfo;
    vec4 uSunFog;
    vec4 uSkyBloom;
    vec4 uProjInfo;   // x=near, y=far (unused here; keeps the block size matching the 64-byte UBO)
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);

    vec3 tint = uSkyBloom.rgb;
    float time = uTimeInfo.x;
    float bloomI = uSkyBloom.w;

    // Sky-tint multiply + gentle brightness pulse driven by bloom intensity + time.
    float pulse = 1.0 + bloomI * 0.05 * sin(time * 0.1);
    vec3 col = scene.rgb * tint * pulse;

    // Additive bloom, scaled by intensity.
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    col += bloom * bloomI;

    fragColor = vec4(col, scene.a);
}
