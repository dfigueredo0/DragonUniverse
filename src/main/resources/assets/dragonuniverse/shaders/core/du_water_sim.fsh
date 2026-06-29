#version 330

// Dragon Universe — Stage 3 ripple simulation. A persistent height field advanced by a damped
// height-field wave equation, ping-ponged each frame. The field is camera-anchored but stays pinned to
// WORLD positions via temporal reprojection: each texel reads the previous field at a UV shifted by the
// camera's movement (cameraDelta / extent), so a ripple holds its world spot as the camera moves; texels
// that fall outside the previous field (newly exposed at the trailing edge) reset to flat.
//
// Clean-room: this is the textbook height-field water sim (Laplacian propagation + velocity damping),
// written from the math, plus standard temporal reprojection. Nothing copied.
//
// Channels: r = height, g = velocity.

uniform sampler2D PrevField;   // previous frame's field (r=height, g=velocity)

const int DU_MAX_INJ = 32;
layout(std140) uniform DUWaterSim {
    vec4 p0;            // x=reprojX, y=reprojZ, z=damping, w=propagation (C = (c*dt/dx)^2, stable < 0.5)
    vec4 meta;          // x=texel (1/N), y=heightDecay, z=injCount, w=pad
    vec4 inj[DU_MAX_INJ]; // per injection: xy=UV centre, z=radiusUV, w=strength
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Reproject: where did this texel's world point sit in the previous (differently-centred) field?
    vec2 puv = texCoord + vec2(p0.x, p0.y);
    if (puv.x < 0.0 || puv.x > 1.0 || puv.y < 0.0 || puv.y > 1.0) {
        fragColor = vec4(0.0);   // newly exposed — no prior data, start flat
        return;
    }

    float texel = meta.x;
    float h = texture(PrevField, puv).r;
    float v = texture(PrevField, puv).g;

    // Neighbours share the same world-per-texel scale in both frames, so a current ±1 texel step maps to
    // a ±texel step in the previous field too — the reprojected Laplacian is exact.
    float hL = texture(PrevField, puv + vec2(-texel, 0.0)).r;
    float hR = texture(PrevField, puv + vec2( texel, 0.0)).r;
    float hU = texture(PrevField, puv + vec2(0.0, -texel)).r;
    float hD = texture(PrevField, puv + vec2(0.0,  texel)).r;
    float lap = (hL + hR + hU + hD) - 4.0 * h;

    // Damped wave integration (velocity form).
    v = (v + p0.w * lap) * p0.z;
    h = (h + v) * meta.y;

    // Add this frame's disturbances (Gaussian splats; world-anchored centres supplied CPU-side): the
    // debug source plus Stage 4's real sources — entity wakes and player charge-ripples.
    int n = int(meta.z);
    for (int i = 0; i < n && i < DU_MAX_INJ; i++) {
        vec4 s = inj[i];
        float d = distance(texCoord, s.xy) / max(s.z, 1.0e-5);
        h += s.w * exp(-d * d);
    }

    fragColor = vec4(h, v, 0.0, 1.0);
}
