STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

void main() {
    vec2 uv = texCoord0;
    float d2 = dot(uv, uv);
    float lum = max(vertexColor.r, max(vertexColor.g, vertexColor.b));
    float spike = 0.0;
    if (lum > 0.72 && ColorModulator.w > 0.0) {
        float reach = 2.4;
        float strength = (lum - 0.72) * 2.2 * ColorModulator.w;
        spike = (exp(-abs(uv.y) * 26.0) * max(0.0, 1.0 - abs(uv.x) / reach)
                + exp(-abs(uv.x) * 26.0) * max(0.0, 1.0 - abs(uv.y) / reach)) * strength;
    }
    float disc = d2 < 1.0 ? (exp(-d2 * 9.0) + exp(-d2 * 3.0) * 0.3) * (1.0 - d2) : 0.0;
    float light = disc + spike;
    if (light <= 0.002) {
        discard;
    }
    float phase = vertexColor.a;
    float twinkle = 1.0;
    if (phase < 0.99) {
        float time = ColorModulator.z;
        float wave = sin(time * (2.3 + phase * 3.1) + phase * 61.0) * 0.6 + sin(time * (5.7 + phase * 2.3) + phase * 17.0) * 0.4;
        twinkle = 1.0 - ColorModulator.y * (0.5 + 0.5 * wave);
    }
    fragColor = vec4(vertexColor.rgb * light * twinkle * ColorModulator.x, 0.0);
}
