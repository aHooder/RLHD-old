#version 330

#include color_utils.glsl

#define BACKGROUND_PATTERN_PASS 0
#define MISSING_TEXTURE_PASS 1
#define COLOR_TEXTURE_PASS 2
#define GREYSCALE_TEXTURE_PASS 3
#define DEPTH_TEXTURE_PASS 4

in vec2 uv;

out vec4 fragColor;

uniform int renderPass;
uniform sampler2D tex;

void main()
{
    switch (renderPass)
    {
        case BACKGROUND_PATTERN_PASS: {
            vec3 colorA = vec3(.4f);
            vec3 colorB = vec3(.6f);
            int tileSize = 8;
            vec2 loc = ceil(mod(gl_FragCoord.xy / tileSize, vec2(2)) - 1);
            vec3 tileColor = mix(colorA, colorB, mod(loc.x + loc.y, 2));
            fragColor = vec4(tileColor, 1);
            break;
        }
        case MISSING_TEXTURE_PASS: {
            vec3 colorA = vec3(0);
            vec3 colorB = vec3(1, 0, .863);
            float tileSize = 1 / 15.f;
            vec2 loc = ceil(mod(uv.xy / tileSize, vec2(2)) - 1);
            vec3 tileColor = mix(colorA, colorB, mod(loc.x + loc.y, 2));
            fragColor = vec4(tileColor, 1);
            break;
        }
        case COLOR_TEXTURE_PASS:
            fragColor = texture(tex, uv);
            break;
        case GREYSCALE_TEXTURE_PASS:
        case DEPTH_TEXTURE_PASS:
            float depth = texture(tex, uv).r;

            float from = .4;
            float to = .6;
            depth = (depth - from) / (to - from);

            if (renderPass == GREYSCALE_TEXTURE_PASS) {
                fragColor = vec4(vec3(depth), 1);
            } else {
                vec3 color = hsvToRgb(vec3(1 - .66f * depth, 1, 1));
                fragColor = vec4(mix(vec3(0), color, ceil(1 - depth)), 1);
            }
            break;
    }
}
