#version 330

layout(location = 0) in vec3 vPos;
layout(location = 1) in vec2 vUv;

out vec2 uv;

uniform mat4 projection;

void main()
{
    gl_Position = projection * vec4(vPos, 1);
    uv = vUv;
}
