#version 330

// Dragon Universe — Task 4 god rays, step 2: radial (light-scattering) blur. The classic Mitchell /
// GPU-Gems-3 crepuscular-ray march: from each pixel, step toward the sun's screen position sampling
// the occlusion mask, accumulating with exponential decay. Output is half-res grayscale ray intensity.

uniform sampler2D InSampler;   // occlusion mask (step 1)

layout(std140) uniform DUGodray {
    vec4 uSun;       // xy = sun screen uv, z = visibility gate (0..1), w = intensity
    vec4 uParams;    // x = density, y = decay, z = weight, w = sampleCount
    vec4 uSunColor;  // rgb, w = godrayEnabled
    vec4 uRim;       // rgb, w = rimStrength
    vec4 uRim2;      // x = rimPower, y = rimEnabled
    vec4 uSunDir;    // xyz = world-space sun direction (unused here; kept for std140 layout parity)
};

in vec2 texCoord;
out vec4 fragColor;

const int MAX_SAMPLES = 128;   // hard ceiling; actual count comes from uParams.w

// Interleaved gradient noise (Jimenez) — a cheap per-pixel dither in [0,1). Deterministic from the
// pixel coord, so it doesn't shimmer frame-to-frame, and it has no array indexing (driver-safe).
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    if (uSunColor.w < 0.5 || uSun.z <= 0.0) {  // god rays off, or sun off-screen/behind (gate == 0)
        fragColor = vec4(0.0);
        return;
    }

    int samples = int(uParams.w);
    float density = uParams.x;
    float decay = uParams.y;
    float weight = uParams.z;

    // Per-step march vector from this pixel toward the sun, scaled by density.
    vec2 delta = (texCoord - uSun.xy) * (density / float(max(samples, 1)));

    // Stagger each pixel's march start by up to one step. Without this, the evenly-spaced samples hit
    // the hard edges of Minecraft's blocky clouds at regular intervals across neighbouring pixels,
    // producing periodic light/dark stripes; the per-pixel jitter scatters those hits into fine noise.
    float jitter = ign(gl_FragCoord.xy);
    vec2 uv = texCoord - delta * jitter;
    float illum = 1.0;
    float accum = 0.0;

    for (int i = 0; i < MAX_SAMPLES; i++) {
        if (i >= samples) break;
        uv -= delta;
        accum += texture(InSampler, uv).r * illum * weight;
        illum *= decay;
    }

    fragColor = vec4(vec3(accum), 1.0);
}
