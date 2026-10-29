STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

void main() {
    vec3 d = normalize(direction);
    float time = ColorModulator.y;
    float brightness = ColorModulator.x;

    float angle = time * 0.004;
    float c = cos(angle);
    float s = sin(angle);
    vec3 q = vec3(c * d.x - s * d.z, d.y, s * d.x + c * d.z);

    float broad = starlitFbm3(q * 2.2 + vec3(0.0, time * 0.006, 0.0), 4);
    float detail = starlitFbm3(q * 6.5 + vec3(broad * 2.2), 3);
    float clouds = smoothstep(0.32, 0.88, detail) * (0.45 + 0.75 * broad);

    vec3 n = normalize(vec3(0.2, 0.9, 0.38));
    float b = dot(q, n);
    float band = exp(-b * b / 0.02) * (0.5 + 0.7 * starlitFbm3(q * 4.0 + vec3(9.1), 3));

    vec3 violet = vec3(0.52, 0.22, 0.9);
    vec3 magenta = vec3(0.92, 0.3, 0.72);
    vec3 teal = vec3(0.22, 0.75, 0.82);
    vec3 tint = mix(violet, magenta, smoothstep(0.35, 0.7, broad));
    tint = mix(tint, teal, smoothstep(0.55, 0.85, detail) * 0.6);

    vec3 color = tint * clouds * 0.32 + vec3(0.75, 0.6, 1.0) * band * 0.14;
    color *= brightness;
    color += vec3(starlitDither(gl_FragCoord.xy));
    fragColor = vec4(max(color, vec3(0.0)), 0.0);
}
