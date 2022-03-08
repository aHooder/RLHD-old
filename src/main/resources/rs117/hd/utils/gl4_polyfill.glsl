float smoothstep(int from, int to, int mixValue) {
    int range = to - from;
    if (range <= 0)
    return 0;
    return (clamp(mixValue, from, to) - from) / float(range);
}

float smoothstep(float from, float to, float mixValue) {
    float range = to - from;
    if (range <= 0)
    return 0;
    return (clamp(mixValue, from, to) - from) / range;
}

float rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float noise(vec2 p){
    vec2 ip = floor(p);
    vec2 u = fract(p);
    u = u*u*(3.0-2.0*u);

    float res = mix(
    mix(rand(ip),rand(ip+vec2(1.0,0.0)),u.x),
    mix(rand(ip+vec2(0.0,1.0)),rand(ip+vec2(1.0,1.0)),u.x),u.y);
    return res*res;
}
