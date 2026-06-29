#version 330

// Dragon Universe — Phase 5D atmosphere sky layer (alpha-blended).
// A horizon→zenith gradient tinted by the layer color, with a sun glow keyed off uSunDir.
// Coverage fades below the horizon so terrain/void still shows.

layout(std140) uniform DUSkyView {
    mat4 uInvViewProj;  // inverse(projection * rotation-only modelview)
    vec4 uSunDir;       // xyz sun direction
    vec4 uMisc;         // x=time
};

layout(std140) uniform DUSkyLayer {
    vec4 uColor;
    vec4 uParams;   // x=opacity
};

in vec2 texCoord;
out vec4 fragColor;

vec3 rayDir() {
    vec4 clip = vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec4 world = uInvViewProj * clip;
    return normalize(world.xyz / world.w);
}

void main() {
    vec3 d = rayDir();
    float elev = clamp(d.y, -1.0, 1.0);
    float up = max(elev, 0.0);

    vec3 horizonCol = uColor.rgb * 1.25;
    vec3 zenithCol = uColor.rgb * 0.45;
    vec3 sky = mix(horizonCol, zenithCol, pow(up, 0.5));

    float sd = max(dot(d, normalize(uSunDir.xyz)), 0.0);
    vec3 glow = vec3(1.0, 0.85, 0.6) * pow(sd, 64.0) * 1.5;
    glow += uColor.rgb * pow(sd, 4.0) * 0.3;

    vec3 col = sky + glow;
    // Full-sphere coverage: terrain draws over our sky via depth anyway, and fading below the
    // horizon would expose the vanilla sky in the void (the "top-half only" bug). Below the
    // horizon the gradient holds the horizon color (max(elev,0)), which is correct for space.
    fragColor = vec4(col, uParams.x);
}
