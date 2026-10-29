STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

const vec3 NEBULA_DIRS[5] = vec3[](
    vec3(0.62, -0.55, 0.56),
    vec3(-0.48, -0.71, -0.52),
    vec3(0.15, -0.35, 0.92),
    vec3(-0.81, -0.22, 0.54),
    vec3(0.33, -0.82, -0.47)
);

const vec3 NEBULA_COLORS[5] = vec3[](
    vec3(1.0, 0.28, 0.45),
    vec3(0.25, 0.75, 0.95),
    vec3(0.62, 0.38, 1.0),
    vec3(1.0, 0.5, 0.28),
    vec3(0.35, 0.55, 1.0)
);

void main() {
    vec3 d = normalize(direction);
    float brightness = ColorModulator.x;
    float milkyWay = ColorModulator.y;
    float nebulae = ColorModulator.z;

    vec3 n = normalize(vec3(0.35, 0.30, 0.887));
    vec3 e1 = normalize(cross(vec3(0.0, 1.0, 0.0), n));
    vec3 e2 = cross(n, e1);
    vec3 core = normalize(e1 * cos(5.34) + e2 * sin(5.34));

    float b = dot(d, n);
    float bulge = exp((dot(d, core) - 1.0) * 5.0);
    float width = 0.09 + 0.11 * bulge;
    float band = exp(-b * b / (width * width));

    vec3 color = vec3(0.0);
    if (milkyWay > 0.0 && band > 0.002) {
        float large = starlitFbm3(d * 3.2, 4);
        float fine = starlitFbm3(d * 11.0 + vec3(3.1), 3);
        float clouds = band * (0.45 + 1.1 * large) * (0.65 + 0.7 * fine);
        float laneWidth = 0.028 + 0.035 * bulge;
        float laneOffset = b - 0.012 + 0.02 * (starlitNoise3(d * 4.0) - 0.5);
        float lane = exp(-laneOffset * laneOffset / (laneWidth * laneWidth));
        float dust = smoothstep(0.3, 0.72, starlitFbm3(d * 7.0 + vec3(7.7), 4)) * lane;
        float glow = clouds * (1.0 - 0.88 * dust) * (0.3 + 1.4 * bulge);
        vec3 tint = mix(vec3(0.62, 0.7, 1.0), vec3(1.0, 0.86, 0.66), clamp(bulge * 0.9 + (large - 0.5) * 0.3, 0.0, 1.0));
        color += tint * glow * 0.55 * milkyWay;
    }

    if (nebulae > 0.0) {
        for (int i = 0; i < 5; i++) {
            float c = dot(d, normalize(NEBULA_DIRS[i]));
            if (c > 0.93) {
                float falloff = smoothstep(0.93, 1.0, c);
                vec3 p = d * 14.0 + vec3(float(i) * 4.3);
                float warp = starlitFbm3(p * 0.6, 2);
                float shape = smoothstep(0.38, 0.85, starlitFbm3(p + vec3(warp * 2.5), 5));
                float wisps = starlitFbm3(p * 2.2 + vec3(warp), 2);
                color += NEBULA_COLORS[i] * shape * falloff * falloff * (0.6 + 0.6 * wisps) * 0.38 * nebulae;
            }
        }
    }

    color *= brightness;
    color += vec3(starlitDither(gl_FragCoord.xy));
    fragColor = vec4(max(color, vec3(0.0)), 0.0);
}
