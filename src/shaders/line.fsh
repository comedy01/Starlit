STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    float across = exp(-texCoord0.y * texCoord0.y * 5.0);
    float ends = smoothstep(0.0, 0.12, texCoord0.x) * smoothstep(1.0, 0.88, texCoord0.x);
    fragColor = vec4(vertexColor.rgb * across * ends * ColorModulator.x, 0.0);
}
