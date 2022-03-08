#version 330

layout(location = 0) in vec3 vPos;
layout(location = 1) in vec2 vUv;

out V_OUT {
    vec4 pos;
    vec2 uv;
} vOut;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main()
{
    vOut.pos = vec4(vPos, 1);
    vOut.uv = vUv;
}
