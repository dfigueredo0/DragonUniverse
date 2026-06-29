#version 330

// Dragon Universe — SSAO bilateral blur (stop 2).
//
// Depth-aware 2D blur that cleans the interleaved-gradient noise in the raw AO buffer without bleeding
// across depth discontinuities. Each neighbour's contribution is a spatial Gaussian weight times a
// depth-similarity weight: the depth buffer is reconstructed to linear view-space Z, and neighbours
// sitting on a different surface (large |dz|) are rejected — so silhouettes and crease edges stay crisp
// instead of smearing into a halo. The depth tolerance scales with distance so the edge test behaves
// consistently near and far.

uniform sampler2D InSampler;     // raw AO (grayscale; read .r)
uniform sampler2D DepthSampler;  // scene depth (NEAREST)

layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;   // view -> world (rotation)
    mat4 uProj;      // view -> clip
};

in vec2 texCoord;
out vec4 fragColor;

const int RADIUS = 2;             // 5x5 kernel
const float SPATIAL_SIGMA = 2.0;  // in pixels

// Linear view-space Z from a depth sample (negative going into the screen).
float viewZFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = uInvProj * ndc;
    return v.z / v.w;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float centerZ = viewZFromDepth(texCoord, texture(DepthSampler, texCoord).r);

    // Depth tolerance proportional to distance (a fixed fraction), with a floor for near surfaces.
    float sigmaZ = max(0.5, 0.05 * abs(centerZ));

    float sum = 0.0;
    float wsum = 0.0;
    for (int y = -RADIUS; y <= RADIUS; y++) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            vec2 uv = texCoord + vec2(float(x), float(y)) * texel;
            float ao = texture(InSampler, uv).r;
            float z = viewZFromDepth(uv, texture(DepthSampler, uv).r);

            float r2 = float(x * x + y * y);
            float ws = exp(-r2 / (2.0 * SPATIAL_SIGMA * SPATIAL_SIGMA));   // spatial Gaussian
            float dz = z - centerZ;
            float wz = exp(-(dz * dz) / (2.0 * sigmaZ * sigmaZ));          // depth similarity
            float w = ws * wz;

            sum += ao * w;
            wsum += w;
        }
    }
    fragColor = vec4(vec3(sum / max(wsum, 1.0e-4)), 1.0);
}
