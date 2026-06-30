#version 330

// Dragon Universe — shadow-map debug viz (Stage A). The raw linear depth as grayscale is too low-contrast
// to judge short casters: a few-block caster's depth gap is a tiny fraction of the (tight) depth range, so
// it's near-invisible against the ground even when it IS in the map. So we ALSO edge-detect:
//
//  * Base: dim grayscale linear depth (near=dark .. far=bright) for orientation.
//  * Overlay (orange): a Laplacian (|4c - l - r - u - d|) of the depth. The Laplacian ignores the smooth
//    linear depth ramp of flat ground (an angled sun gradient) but SPIKES at depth discontinuities — i.e.
//    caster silhouette edges. So any geometry actually rasterized into the map pops as an orange outline,
//    no matter how small its absolute depth difference. If a short caster shows an outline here, it IS in
//    the map (the receiver is missing it); if it shows nothing, the producer isn't writing it.

uniform sampler2D DepthSampler;   // the shadow depth map (depth in .r)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 ts = 1.0 / vec2(textureSize(DepthSampler, 0));
    float c  = texture(DepthSampler, texCoord).r;
    float l  = texture(DepthSampler, texCoord - vec2(ts.x, 0.0)).r;
    float r  = texture(DepthSampler, texCoord + vec2(ts.x, 0.0)).r;
    float u  = texture(DepthSampler, texCoord - vec2(0.0, ts.y)).r;
    float dn = texture(DepthSampler, texCoord + vec2(0.0, ts.y)).r;

    // Laplacian: ~0 on flat/linear depth, large at discontinuities (caster edges). Range-independent.
    float lap = abs(4.0 * c - l - r - u - dn);
    float edge = clamp(lap * 600.0, 0.0, 1.0);   // gain: ~0.001 light-depth jump -> full edge

    vec3 base = vec3(c * 0.6);                    // dim depth for context
    fragColor = vec4(base + vec3(edge, edge * 0.35, 0.0), 1.0);  // orange silhouette outlines
}
