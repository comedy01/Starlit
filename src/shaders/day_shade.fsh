STARLIT_FRAGMENT_HEADER

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

void main() {
    vec3 d = normalize(direction);
    vec3 sun = normalize(ColorModulator.xyz);
    float strength = clamp(ColorModulator.w, 0.0, 1.0);
    float atmosphere = TextureMat[0].x;
    float sunsets = TextureMat[0].y;

    float h = d.y;
    float day = smoothstep(-0.05, 0.25, sun.y);
    float zenith = smoothstep(0.02, 0.9, h);
    vec3 factor = mix(vec3(1.0), vec3(0.72, 0.8, 0.95), zenith * atmosphere * day);

    vec2 flatDir = d.xz / max(length(d.xz), 1.0e-4);
    float sunFlatLength = length(sun.xz);
    vec2 sunFlat = sun.xz / max(sunFlatLength, 1.0e-4);
    float az = dot(flatDir, sunFlat) * smoothstep(0.0, 0.3, sunFlatLength);
    float anti = pow(max(-az, 0.0), 1.2);
    float band = smoothstep(-0.03, 0.04, h) * (1.0 - smoothstep(0.04, 0.15, h));
    float dusk = smoothstep(-0.22, -0.04, sun.y) * (1.0 - smoothstep(-0.01, 0.12, sun.y));
    factor *= mix(vec3(1.0), vec3(0.8, 0.81, 0.92), band * anti * dusk * sunsets);

    fragColor = vec4(mix(vec3(1.0), factor, strength), 1.0);
}
