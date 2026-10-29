STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 ribbon;

OUT(0) vec4 fragColor;

vec3 palette(float h) {
    vec3 low = TextureMat[0].rgb;
    vec3 mid = TextureMat[1].rgb;
    vec3 top = TextureMat[2].rgb;
    return h < 0.45 ? mix(low, mid, h / 0.45) : mix(mid, top, (h - 0.45) / 0.55);
}

void main() {
    float u = ribbon.x;
    float v = ribbon.y;
    float id = ribbon.z;
    float time = ColorModulator.y;
    float intensity = ColorModulator.x;

    float rays = 0.35 + 0.65 * starlitNoise2(vec2(u * 150.0 + time * 0.35, id * 7.0));
    rays *= 0.65 + 0.35 * starlitNoise2(vec2(u * 38.0 - time * 0.22, id * 3.0 + 50.0));
    float patches = smoothstep(0.15, 0.7, starlitFbm2(vec2(u * 5.0 + time * 0.025, id * 11.0), 2));
    float ends = smoothstep(0.0, 0.1, u) * smoothstep(1.0, 0.9, u);
    float rise = smoothstep(0.0, 0.05, v);
    float fade = exp(-v * 2.4) * (1.0 - smoothstep(0.85, 1.0, v));
    float glow = rays * patches * ends * rise * fade;
    float pulse = 0.82 + 0.18 * sin(time * 0.4 + id * 2.0) * sin(time * 0.17 + id);

    vec3 color = palette(v) * glow * pulse * intensity * 0.55;
    color += vec3(starlitDither(gl_FragCoord.xy));
    fragColor = vec4(max(color, vec3(0.0)), 0.0);
}
