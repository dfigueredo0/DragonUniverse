#version 330

// Dragon Universe — Stage 1.5 underwater scene fog. When the camera eye is submerged, tint the whole
// scene toward an underwater colour by view-space distance (exponential), so submerged terrain fades
// into the water with depth. Screen-space; reads a copy of the composited scene + the depth buffer.
// This is the volume tint — distinct from the water *surface* material (the Sodium terrain inject),
// which shades only the boundary. Runs only while the eye is in water.

uniform sampler2D InSampler;     // a copy of the composited scene colour
uniform sampler2D DepthSampler;  // scene depth (NEAREST)

layout(std140) uniform DUView {
    mat4 uInvProj;   // clip -> view
    mat4 uInvView;
    mat4 uProj;
};

layout(std140) uniform DUWaterFog {
    vec4 uFog;       // rgb = underwater colour, a = density (per block)
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;
    float d = texture(DepthSampler, texCoord).r;

    float dist;
    if (d >= 1.0) {
        dist = 1.0e6;   // far plane / sky gap: fully fogged (underwater there's no clear sky)
    } else {
        vec4 ndc = vec4(texCoord * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
        vec4 v = uInvProj * ndc;
        dist = length(v.xyz / v.w);   // view-space distance from the camera
    }

    float f = 1.0 - exp(-uFog.a * dist);
    fragColor = vec4(mix(scene, uFog.rgb, f), 1.0);
}
