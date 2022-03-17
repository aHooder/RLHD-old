#version 330

#include color_utils.glsl
#include utils/gl4_polyfill.glsl

#include <RENDER_PASS>
#include <TEXTURE_MODIFIER>

in G_OUT {
    vec2 uv;
    vec3 tsEyePos;
    vec3 tsFragPos;
    vec3 tsSunDir;
} gOut;

out vec4 fragColor;

uniform int renderPass;
uniform int textureModifiers;
uniform float texDepthMin;
uniform float texDepthMax;
uniform float texDepthScale;
uniform float texAspectRatio;

uniform sampler2D texColor;
uniform sampler2D texDepth;
uniform usampler2D texUDepth;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

bool hasModifiers(int modifiers, int requiredModifiers) {
    return (modifiers & requiredModifiers) == requiredModifiers;
}

bool hasTexMod(int modifiers) {
    return hasModifiers(textureModifiers, modifiers);
}

vec2 applyUvModifiers(vec2 uv) {
    if (hasTexMod(TEXTURE_MODIFIER_FLIP_X))
        uv.x = 1 - uv.x;
    if (hasTexMod(TEXTURE_MODIFIER_FLIP_Y))
        uv.y = 1 - uv.y;
    return uv;
}

vec4 sampleColor(vec2 uv) {
    uv = applyUvModifiers(uv);

    vec4 samp = texture(texColor, uv);

    if (hasTexMod(TEXTURE_MODIFIER_DISABLE_ALPHA))
        samp.a = 1;

    if (hasTexMod(TEXTURE_MODIFIER_RGB_TO_GREYSCALE))
        samp.rgb = vec3((samp.r + samp.g + samp.b) / 3.f);
    else if (hasTexMod(TEXTURE_MODIFIER_GREYSCALE))
        samp.rgb = vec3(samp.r);
    else if (hasTexMod(TEXTURE_MODIFIER_RGB_TO_BGR))
        samp.rgb = samp.bgr;

    return samp;
}

vec4 sampleDepthInterpolate(vec2 uv) {
    ivec2 texSize = textureSize(texUDepth, 0);
    vec2 texelSize = 1.f / texSize;

    ivec2 texelCoords = ivec2(uv * texSize);
    vec2 texelOffset = mod(uv, texelSize);

    vec4 samp = vec4(0);
    int kernel = 3;
    for (int x = -kernel; x <= kernel; x++) {
        for (int y = -kernel; y <= kernel; y++) {
            float d = 1 - distance(texelOffset, vec2(x, y));
            samp += texelFetch(texUDepth, texelCoords + ivec2(x, y), 0);
        }
    }

    float diam = kernel * 2 + 1;
    samp /= diam * diam;

    return samp;
}

float sampleDepthDirect(vec2 uv) {
    uv = applyUvModifiers(uv);

    vec4 samp;
    if (hasTexMod(TEXTURE_MODIFIER_UINT_DEPTH)) {
        if (hasTexMod(TEXTURE_MODIFIER_INTERPOLATE_DEPTH)) {
            samp = sampleDepthInterpolate(uv);
        } else {
            samp = texture(texUDepth, uv);
        }
        samp = (samp - texDepthMin) / (texDepthMax - texDepthMin);
    } else {
        samp = (texture(texDepth, uv) - texDepthMin) / (texDepthMax - texDepthMin);
    }

    float depth;
    if (hasTexMod(TEXTURE_MODIFIER_ALPHA_DEPTH)) {
        depth = samp.a;
    } else {
        depth = samp.r;
    }

    if (hasTexMod(TEXTURE_MODIFIER_FLIP_Z))
        return 1 - depth;
    return depth;
}

float sampleDepth(inout vec2 uv, inout float shadowing) {
    if (hasTexMod(TEXTURE_MODIFIER_PARALLAX)) {
        vec3 fragToEye = normalize(gOut.tsEyePos - gOut.tsFragPos);
        vec2 xyPerZ = fragToEye.xy / fragToEye.z;

        const int numLayers = 100;
        float heightScale = texDepthScale;

        if (numLayers > 1) {
            const float depthIncrement = 1.f / (numLayers - 1);
            vec2 deltaUv = xyPerZ * heightScale / (numLayers - 1);
            float layerDepth = 0;
            float texelDepth = sampleDepthDirect(uv);
            float prevTexelDepth = texelDepth;
            while (texelDepth >= layerDepth) { // TODO: compare performance to for loop with & without break
                layerDepth += depthIncrement;
                uv -= deltaUv;
                prevTexelDepth = texelDepth;
                texelDepth = sampleDepthDirect(uv);
            }

            float afterDiff = texelDepth - layerDepth;
            float beforeDiff = prevTexelDepth - (layerDepth - depthIncrement);
//            uv += deltaUv * clamp(afterDiff / (afterDiff - beforeDiff), 0, 1);

        }

        if (uv.x < 0 || uv.y < 0 || uv.x > 1 || uv.y > 1)
            discard;
    }

    return sampleDepthDirect(uv);
}

vec4 generateTerrainColor(float depth, vec2 uv) {
    float height = 1 - depth;

    // Colors
    vec3 deepBlue = vec3(.09, .165, .384);
    vec3 lightBlue = vec3(.345, .439, .675);
    vec3 sandYellow = vec3(.871, .808, .71);
    vec3 lightGrassGreen = vec3(.329, .42, .322);
    vec3 grassGreen = vec3(.212, .376, .235);
    vec3 mountainGrey = vec3(.292, .31, .265);
    vec3 mountainBlack = vec3(.192, .141, .1365);
    vec3 mountainWhite = vec3(.902, .906, .91);

    // Parameters
    float seaFrom = .01f;
    float seaTo = .011f;
    float shoreFrom = seaTo + .0001f;
    float shoreTo = shoreFrom + .05f;
    float grassFrom = shoreTo + .05f;
    float grassTo = shoreFrom + .2f;
    float mountainFrom = grassTo + .1f;
    float mountainTo = .75f;
    float snowFrom = .9f;

    vec3 c = deepBlue;
    c = mix(c, lightBlue, smoothstep(seaFrom, seaTo, height));
    c = mix(c, sandYellow, smoothstep(seaTo, shoreFrom, height));
    c = mix(c, sandYellow, smoothstep(shoreFrom, shoreTo, height));
    c = mix(c, lightGrassGreen, smoothstep(shoreTo, grassFrom, height));
    c = mix(c, grassGreen, smoothstep(grassFrom, grassTo, height));
    c = mix(c, mountainBlack, smoothstep(grassTo, mountainFrom, height));
    c = mix(c, mountainGrey, smoothstep(mountainFrom, mountainTo, height));
    c = mix(c, mountainWhite, smoothstep(mountainTo, snowFrom, height));

    c *= 1.f + .3f * vec3(noise(uv * 1000000));

    return vec4(c, 1);
}

void main() {
    switch (renderPass) {
        case RENDER_PASS_SOLID_AUTO:
            const float eps = .0001f;
//            vec3 average = (
//                sampleColor(vec2(eps, eps)).rgb,
//                sampleColor(vec2(eps, 1 - eps)).rgb,
//                sampleColor(vec2(1 - eps, 1 - eps)).rgb,
//                sampleColor(vec2(1 - eps, eps)).rgb
//            ) / 4.f;
            vec3 average = sampleColor(vec2(.01f)).rgb;
            fragColor = vec4(average, 1);
            break;
        case RENDER_PASS_SOLID_BLACK:
            fragColor = vec4(0, 0, 0, 1);
            break;
        case RENDER_PASS_SOLID_OSRS_SEA:
            fragColor = vec4(.341, .424, .616, 1);
            break;
        case RENDER_PASS_PATTERN_CHECKER: {
            vec3 colorA = vec3(.4f);
            vec3 colorB = vec3(.6f);
            int tileSize = 8;
            vec2 loc = ceil(mod(gl_FragCoord.xy / tileSize, vec2(2)) - 1);
            vec3 tileColor = mix(colorA, colorB, mod(loc.x + loc.y, 2));
            fragColor = vec4(tileColor, 1);
            break;
        }
        case RENDER_PASS_PATTERN_MISSING: {
            vec3 colorA = vec3(0);
            vec3 colorB = vec3(1, 0, .863);
            vec2 tileSize = vec2(1 / 15.f) / vec2(texAspectRatio, 1);
            vec2 loc = ceil(mod(gOut.uv.xy / tileSize, vec2(2)) - 1);
            vec3 tileColor = mix(colorA, colorB, mod(loc.x + loc.y, 2));
            fragColor = vec4(tileColor, 1);
            break;
        }
        case RENDER_PASS_TEXTURE: {
            vec2 uv = gOut.uv;
            bool hasColor = hasTexMod(TEXTURE_MODIFIER_HAS_COLOR);
            bool hasDepth = hasTexMod(TEXTURE_MODIFIER_HAS_DEPTH);
            if (!hasDepth) {
                fragColor = sampleColor(uv);
            } else {
                float shadowing = 0;
                float h = sampleDepth(uv, shadowing);

                if (hasTexMod(TEXTURE_MODIFIER_RAINBOW)) {
                    vec3 color = hsvToRgb(vec3(1 - .66f * h, 1, 1));
                    fragColor = vec4(mix(vec3(0), color, ceil(1 - h)), 1);
                } else if (hasTexMod(TEXTURE_MODIFIER_TERRAIN)) {
                    fragColor = generateTerrainColor(h, uv);
                } else if (hasColor) {
                    const float eps = .001f;
                    uv = clamp(uv, vec2(eps), vec2(1 - eps));
                    fragColor = sampleColor(uv);
                } else {
                    fragColor = vec4(vec3(h), 1);
                }

                fragColor.rgb *= 1 - shadowing;
            }
            break;
        }
    }
}
