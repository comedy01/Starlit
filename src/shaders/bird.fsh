STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    vec2 p = texCoord0;
    float flap = ColorModulator.x;
    float alpha = ColorModulator.y;

    float x = abs(p.x);
    float tip = 0.5 * flap;
    float wingY = tip * pow(x, 1.3) + 0.2 * sin(3.14159 * min(x, 1.0)) * (0.6 - 0.4 * flap) - 0.05;
    float thickness = 0.13 * (1.0 - 0.75 * x);
    float aa = fwidth(p.y) * 1.2;
    float wing = (1.0 - smoothstep(thickness - aa, thickness + aa, abs(p.y - wingY))) * (1.0 - smoothstep(0.88, 0.98, x));
    vec2 b = vec2(p.x / 0.16, (p.y + 0.04) / 0.1);
    float body = 1.0 - smoothstep(1.0 - aa * 8.0, 1.0 + aa * 8.0, length(b));

    float shape = max(wing, body);
    if (shape <= 0.002) {
        discard;
    }
    fragColor = vec4(vec3(0.07, 0.075, 0.09), shape * alpha);
}
