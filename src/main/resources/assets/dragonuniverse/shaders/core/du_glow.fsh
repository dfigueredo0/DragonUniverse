// assets/dragonuniverse/shaders/core/du_glow.fsh
#version 330

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord);

    vec3 rgb = vertexColor.rgb * tex.rgb;
    float a  = vertexColor.a * tex.a;

    if (a < 0.01) discard;
    fragColor = vec4(rgb, a);
}
