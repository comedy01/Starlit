STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    float d = length(texCoord0);
    if (d >= 1.0) {
        discard;
    }
    float fall = 1.0 - d;
    float glow = fall * fall * fall * 0.7 + exp(-d * d * 30.0) * 0.3;
    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb * glow * ColorModulator.a, 0.0);
}
