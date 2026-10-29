#ifndef STARLIT_NOISE_GLSL
#define STARLIT_NOISE_GLSL

uvec3 starlitPcg3(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return v;
}

float starlitHash3(vec3 p) {
    uvec3 h = starlitPcg3(uvec3(ivec3(floor(p + 0.5))));
    return float(h.x >> 8u) * (1.0 / 16777216.0);
}

float starlitNoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(starlitHash3(i), starlitHash3(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(starlitHash3(i + vec3(0.0, 1.0, 0.0)), starlitHash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(starlitHash3(i + vec3(0.0, 0.0, 1.0)), starlitHash3(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(starlitHash3(i + vec3(0.0, 1.0, 1.0)), starlitHash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

float starlitFbm3(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * starlitNoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.4);
        amp *= 0.5;
    }
    return sum;
}

float starlitHash2(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p + 0.5)));
    uint h = (q.x * 0x8da6b343u) ^ (q.y * 0xd8163841u);
    h ^= h >> 15u;
    h *= 0x2c1b3c6du;
    h ^= h >> 12u;
    h *= 0x297a2d39u;
    h ^= h >> 15u;
    return float(h >> 8u) * (1.0 / 16777216.0);
}

float starlitNoise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(starlitHash2(i), starlitHash2(i + vec2(1.0, 0.0)), f.x),
               mix(starlitHash2(i + vec2(0.0, 1.0)), starlitHash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

float starlitFbm2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * starlitNoise2(p);
        p = p * 2.07 + vec2(5.1, 1.3);
        amp *= 0.5;
    }
    return sum;
}

float starlitDither(vec2 fragCoord) {
    return (starlitHash2(fragCoord) - 0.5) / 255.0;
}

#endif
