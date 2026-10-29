STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    float r = length(texCoord0);
    if (r >= 1.0) {
        discard;
    }
    float kind = vertexColor.a;
    float theta = atan(texCoord0.y, texCoord0.x);
    vec3 warm = vec3(1.0, 0.86, 0.62);
    vec3 cool = vec3(0.62, 0.74, 1.0);
    vec3 color;
    if (kind > 0.6) {
        float core = exp(-r * r * 9.0);
        color = warm * core * 0.9;
    } else {
        float twist = kind > 0.3 ? 3.5 : 5.0;
        float arms = 0.5 + 0.5 * cos(2.0 * theta - log(r + 0.04) * twist);
        arms = pow(arms, 2.5);
        float clumps = starlitNoise2(texCoord0 * 9.0 + vec2(kind * 13.0));
        float disk = exp(-r * 3.4) * (0.2 + 1.1 * arms * (0.6 + 0.6 * clumps));
        float core = exp(-r * r * 60.0) * 1.3 + exp(-r * r * 12.0) * 0.35;
        color = cool * disk * 0.55 + warm * core;
    }
    float edge = smoothstep(1.0, 0.75, r);
    fragColor = vec4(color * vertexColor.rgb * edge * ColorModulator.x, 0.0);
}
