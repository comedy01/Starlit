STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    float raw = texCoord0.x * 1.05 - 0.04;
    float u = clamp(raw, 0.0, 1.0);
    float v = texCoord0.y;
    float width = mix(0.55, 0.12, u);
    float across = exp(-(v * v) / (width * width) * 3.0);
    float tail = pow(1.0 - u, 2.2);
    float head = exp(-raw * raw * 900.0) * exp(-v * v * 6.0);
    vec3 tint = ColorModulator.rgb;
    vec3 color = mix(vec3(1.0), tint, clamp(u * 3.0, 0.0, 1.0)) * tail * across + vec3(1.0, 0.98, 0.92) * head * 1.6;
    fragColor = vec4(color * ColorModulator.a * vertexColor.rgb, 0.0);
}
