// assets/dragonuniverse/shaders/core/du_glow.vsh
#version 330

#moj_import <minecraft:dynamictransforms.glsl>  // provides ModelViewMat, ColorModulator, ModelOffset, TextureMat
#moj_import <minecraft:projection.glsl>          // provides ProjMat

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
