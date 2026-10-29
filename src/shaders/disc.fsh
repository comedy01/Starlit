STARLIT_FRAGMENT_HEADER

IN(0) vec2 texCoord0;
IN(1) vec4 vertexColor;

OUT(0) vec4 fragColor;

STARLIT_NOISE

void main() {
    float r = length(texCoord0);
    if (r >= 1.0) {
        discard;
    }
    float moon = TextureMat[0].x;
    float phase = TextureMat[0].y;
    float aa = fwidth(r) * 1.5;
    float core = 1.0 - smoothstep(0.32 - aa, 0.32 + aa, r);
    float glow = pow(1.0 - r, 3.0);
    vec3 rgb = ColorModulator.rgb;
    float alpha = ColorModulator.a;

    if (moon < 0.5) {
        vec3 c = mix(rgb * glow, vec3(1.0, 0.98, 0.9), core);
        fragColor = vec4(c, max(core, glow * 0.8) * alpha);
        return;
    }

    vec2 p = texCoord0 / 0.32;
    float q = dot(p, p);
    if (q >= 1.0) {
        fragColor = vec4(rgb, glow * 0.25 * alpha * min(abs(phase - 0.5) * 2.0, 1.0));
        if (fragColor.a <= 0.002) {
            discard;
        }
        return;
    }
    vec3 n = vec3(p, sqrt(1.0 - q));
    float a = (phase - 0.5) * 6.2831853;
    vec3 light = vec3(sin(a), 0.0, -cos(a));
    float lit = smoothstep(-0.06, 0.06, dot(n, light));
    float maria = smoothstep(0.48, 0.62, starlitFbm3(n * 2.2 + vec3(4.1, 1.7, 8.3), 4));
    float grain = starlitFbm3(n * 9.0, 3);
    float mottle = (1.0 - 0.28 * maria) * (0.92 + 0.16 * grain) * (0.82 + 0.18 * n.z);
    vec3 c = mix(vec3(0.05, 0.06, 0.09), rgb * mottle, lit);
    fragColor = vec4(c, core * alpha);
}
