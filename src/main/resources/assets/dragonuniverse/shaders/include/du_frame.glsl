// assets/dragonuniverse/shaders/include/du_frame.glsl
// Per-frame uniform block. MUST mirror DUFrameData.STD140_SIZE layout exactly.
// Included by effect shaders via:  #moj_import <dragonuniverse:du_frame.glsl>

layout(std140) uniform DUFrame {
    float uTime;          // game time + partialTick (seconds-ish; ticks actually)
    float uPartialTick;
    vec2  _duPad0;
    vec3  uSunDir;        // normalized
    float uFogDensity;
    vec3  uSkyTint;
    float uBloomIntensity;
};
