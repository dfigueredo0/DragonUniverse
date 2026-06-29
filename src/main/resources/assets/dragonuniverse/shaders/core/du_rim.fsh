#version 330

// Dragon Universe — rim light (Task 4) + silver-lining (Phase 2 clouds).
//
// Two related edge glows, both additive, driven by the geometry-normals buffer:
//   * RIM (terrain/entities): a sun-facing Fresnel edge, pow(1-dot(N,V),power) * max(dot(N,sunDir),0).
//     World-space & sun-driven, so lit edges glow regardless of camera direction. Front-lit.
//   * SILVER-LINING (clouds): the classic back-lit cloud edge. The visible front faces of a back-lit
//     cloud point AWAY from the sun, so the rim's dot(N,sunDir) gate would kill it — instead silver uses
//     a forward-scatter term: bright on grazing cloud edges when the camera looks TOWARD the sun
//     (dot(view ray, sunDir)), independent of edge-normal sun-facing.
//
// Coverage alpha also tags the normal's space/class: terrain ~1.0 (world), entity ~0.667 (view), cloud
// ~0.333 (world). SSAO excludes clouds via its a<0.5 gate; here we route clouds to silver-lining.

uniform sampler2D NormalSampler;  // geometry normals, encoded N*0.5+0.5; a = coverage/class tag
uniform sampler2D DepthSampler;

layout(std140) uniform DUView {   // inverse camera matrices (Task 3 / DUViewData)
    mat4 uInvProj;
    mat4 uInvView;
};

layout(std140) uniform DUGodray {
    vec4 uSun;
    vec4 uParams;
    vec4 uSunColor;
    vec4 uRim;       // rgb, w = rimStrength
    vec4 uRim2;      // x = rimPower, y = rimEnabled
    vec4 uSunDir;    // xyz = world-space sun direction (view-independent)
    vec4 uSilver;    // rgb, w = silverStrength
    vec4 uSilver2;   // x = silverPower, y = scatterPower, z = silverEnabled
};

in vec2 texCoord;
out vec4 fragColor;

// Class thresholds on the coverage tag (see DUEntityNormals): ~0.333 cloud, ~0.667 entity, ~1.0 terrain.
const float COVER_MIN  = 0.16;   // below this = no geometry (sky/water/uncovered)
const float CLOUD_MAX  = 0.5;    // a < this (and >= COVER_MIN) = cloud
const float ENTITY_MAX = 0.83;   // CLOUD_MAX < a < this = entity (view-space); a >= this = terrain (world)

vec3 viewPosFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = uInvProj * ndc;
    return view.xyz / view.w;
}

void main() {
    bool rimOn = uRim2.y >= 0.5 && uRim.w > 0.0;
    bool silverOn = uSilver2.z >= 0.5 && uSilver.w > 0.0;
    if (!rimOn && !silverOn) {
        fragColor = vec4(0.0);
        return;
    }

    vec4 nSample = texture(NormalSampler, texCoord);
    if (nSample.a < COVER_MIN) {        // sky / water / uncovered -> no edge
        fragColor = vec4(0.0);
        return;
    }
    bool isCloud = nSample.a < CLOUD_MAX;                          // ~0.333
    bool isEntity = nSample.a >= CLOUD_MAX && nSample.a < ENTITY_MAX; // ~0.667 (view-space)

    // Gate per class against the relevant toggle.
    if (isCloud ? !silverOn : !rimOn) {
        fragColor = vec4(0.0);
        return;
    }

    // Decode the normal to WORLD space. Entities are stored view-space (bring up via view->world);
    // terrain & clouds are already world-space.
    vec3 nRaw = normalize(nSample.rgb * 2.0 - 1.0);
    vec3 N = isEntity ? normalize(mat3(uInvView) * nRaw) : nRaw;

    // View direction (surface -> camera) in world space. Camera sits at the view-space origin.
    float d = texture(DepthSampler, texCoord).r;
    vec3 viewPos = viewPosFromDepth(texCoord, d);
    vec3 vWorld = normalize(mat3(uInvView) * normalize(-viewPos));

    vec3 sunDir = normalize(uSunDir.xyz);   // surface -> sun

    if (isCloud) {
        // Silver-lining: grazing cloud-silhouette edge * forward-scatter toward the sun.
        float edge = pow(1.0 - max(dot(N, vWorld), 0.0), uSilver2.x);   // silhouette (Fresnel-like)
        // -vWorld is camera->surface; aligned with surface->sun when the cloud sits between camera and
        // sun (back-lit) -> the glow peaks looking toward the sun, and self-gates to 0 when the sun is
        // behind the camera. No edge-normal sun-facing requirement (that's the point of back-lighting).
        float scatter = pow(max(dot(-vWorld, sunDir), 0.0), uSilver2.y);
        float silver = edge * scatter;
        fragColor = vec4(uSilver.rgb * (uSilver.w * silver), 1.0);
    } else {
        // Front-lit rim (terrain/entities).
        float fresnel = pow(1.0 - max(dot(N, vWorld), 0.0), uRim2.x);
        float sunFacing = max(dot(N, sunDir), 0.0);
        float rim = fresnel * sunFacing;
        fragColor = vec4(uRim.rgb * (uRim.w * rim), 1.0);
    }
}
