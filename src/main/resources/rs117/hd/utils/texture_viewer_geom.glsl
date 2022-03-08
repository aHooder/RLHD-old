#version 330

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;

in V_OUT {
    vec4 pos;
    vec2 uv;
} vOut[];

out G_OUT {
    vec2 uv;
    vec3 tsEyePos;
    vec3 tsFragPos;
} gOut;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    vec4 origo = vec4(vec3(0), 1);
    mat4 MVP = projection * view * model;

    // We need the fragment to eye vector in tangent space
    // Abbreviations:
    // vs = view space
    // ws = world space
    // ms = model space
    // ts = tangent space

    // We have the eye pos in view space,
    // and the vertex pos in model space:
    vec4 vsEyePos = origo;
//  vec4 msVertPos = vOut[i].pos;
    // By transforming both of these into tangent space
    // and outputting these to the fragment shader,
    // we get per-fragment coordinates to calculate the
    // fragment-to-view vector in tangent space
    // that we can use for parallax mapping.

    // Convert the view space eye pos to tangent space
    // In this case, model space is always the same as
    // tangent space at twice the size, translated by -1,
    // to go from [0, 1] to [-1, 1].
    mat4 tangentSpaceToModelSpace = transpose(mat4(
        2, 0, 0, -1,
        0, 2, 0, -1,
        0, 0, 2, -1,
        0, 0, 0, 1
    ));
    mat4 modelSpaceToTangentSpace = inverse(tangentSpaceToModelSpace);

    mat4 viewSpaceToTangentSpace = inverse(view * model * tangentSpaceToModelSpace);
    gOut.tsEyePos = (viewSpaceToTangentSpace * vsEyePos).xyz;

    for (int i = 0; i < 3; i++) {
        gl_Position = MVP * vOut[i].pos;
        gOut.uv = vOut[i].uv;

        // Convert the model space vertex pos to tangent space
        gOut.tsFragPos = (modelSpaceToTangentSpace * vOut[i].pos).xyz;

        EmitVertex();
    }

    EndPrimitive();
}
