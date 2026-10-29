STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

vec3 spectrum(float t) {
    float red = smoothstep(0.55, 0.85, t) + 0.35 * exp(-pow((t - 0.05) / 0.07, 2.0));
    float green = exp(-pow((t - 0.55) / 0.17, 2.0)) * 0.95;
    float blue = exp(-pow((t - 0.22) / 0.15, 2.0));
    vec3 c = vec3(red, green, blue);
    c.rg += vec2(0.55, 0.45) * exp(-pow((t - 0.7) / 0.07, 2.0));
    return c;
}

float bowMask(float t) {
    return smoothstep(0.0, 0.12, t) * (1.0 - smoothstep(0.88, 1.0, t));
}

void main() {
    vec3 d = normalize(direction);
    vec3 center = normalize(ColorModulator.xyz);
    float strength = clamp(ColorModulator.w, 0.0, 1.5);
    float time = TextureMat[0].w;

    float r = degrees(acos(clamp(dot(d, center), -1.0, 1.0)));
    if (r < 6.0 || r > 59.0 || d.y < -0.12) {
        fragColor = vec4(0.0);
        return;
    }
    float t1 = (r - 39.6) / 3.4;
    float t2 = (54.4 - r) / 4.2;
    float m1 = bowMask(t1);
    float m2 = bowMask(t2);
    float dark = smoothstep(42.6, 44.5, r) * (1.0 - smoothstep(48.5, 50.2, r));
    float inside = smoothstep(40.0, 34.0, r) * smoothstep(6.0, 30.0, r);

    float n = starlitNoise3(d * 2.4 + vec3(0.0, time * 0.02, 0.0));
    float patches = 0.55 + 0.45 * smoothstep(0.2, 0.75, n);
    float fade = smoothstep(-0.12, 0.0, d.y) * patches * strength;

    vec3 color = spectrum(t1) * m1 * 0.55 + spectrum(t2) * m2 * 0.18 + vec3(0.92, 0.94, 1.0) * inside * 0.04;
    float alpha = m1 * 0.42 + m2 * 0.14 + dark * 0.07;
    color *= fade;
    alpha *= fade;
    fragColor = vec4(max(color, vec3(0.0)), clamp(alpha, 0.0, 0.8));
}
