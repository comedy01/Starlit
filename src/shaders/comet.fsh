STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    float raw = texCoord0.x * 1.08 - 0.06;
    float u = clamp(raw, 0.0, 1.0);
    float v = texCoord0.y;
    float coma = exp(-((raw - 0.04) * (raw - 0.04) * 400.0 + v * v * 22.0));
    float nucleus = exp(-((raw - 0.04) * (raw - 0.04) * 6000.0 + v * v * 400.0));
    float bent = v + 0.5 * u * u;
    float dustWidth = 0.07 + 0.62 * u;
    float dustGrain = 0.75 + 0.25 * starlitNoise2(vec2(u * 9.0, bent * 6.0));
    float dust = exp(-bent * bent / (dustWidth * dustWidth)) * pow(1.0 - u, 1.3) * smoothstep(0.0, 0.08, u) * dustGrain;
    float ionWidth = 0.025 + 0.08 * u;
    float streaks = 0.7 + 0.3 * starlitNoise2(vec2(u * 6.0 - ColorModulator.y * 0.05, v * 40.0));
    float ion = exp(-v * v / (ionWidth * ionWidth)) * pow(1.0 - u, 1.1) * smoothstep(0.0, 0.06, u) * streaks;
    vec3 color = vec3(0.7, 1.0, 0.82) * coma * 0.9
            + vec3(1.0) * nucleus * 1.4
            + vec3(1.0, 0.9, 0.72) * dust * 0.9
            + vec3(0.45, 0.68, 1.0) * ion * 0.38;
    fragColor = vec4(color * vertexColor.rgb * ColorModulator.x, 0.0);
}
