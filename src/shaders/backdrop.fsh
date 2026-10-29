STARLIT_FRAGMENT_HEADER

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

void main() {
    vec3 d = normalize(direction);
    vec3 sun = normalize(ColorModulator.xyz);
    float clear = clamp(ColorModulator.w, 0.0, 1.0);
    float sunsets = TextureMat[0].y;

    float h = max(d.y, 0.0);
    float day = smoothstep(-0.18, 0.22, sun.y);
    vec3 zenithDay = vec3(0.32, 0.52, 0.92);
    vec3 horizonDay = vec3(0.72, 0.83, 0.98);
    vec3 zenithNight = vec3(0.004, 0.006, 0.018);
    vec3 horizonNight = vec3(0.016, 0.022, 0.045);
    float up = pow(h, 0.55);
    vec3 color = mix(mix(horizonNight, zenithNight, up), mix(horizonDay, zenithDay, up), day);

    vec2 flatDir = d.xz / max(length(d.xz), 1.0e-4);
    float sunFlatLength = length(sun.xz);
    vec2 sunFlat = sun.xz / max(sunFlatLength, 1.0e-4);
    float az = dot(flatDir, sunFlat) * smoothstep(0.0, 0.3, sunFlatLength);
    float dusk = smoothstep(-0.25, -0.05, sun.y) * (1.0 - smoothstep(0.05, 0.3, sun.y));
    float low = 1.0 - smoothstep(-0.05, 0.45, d.y);
    float toward = pow(max(az, 0.0) * 0.5 + 0.5, 3.0);
    vec3 duskColor = mix(vec3(0.95, 0.42, 0.16), vec3(1.0, 0.68, 0.36), smoothstep(-0.02, 0.2, d.y));
    color = mix(color, duskColor, dusk * low * toward * sunsets * 0.9);

    float grey = dot(color, vec3(0.3, 0.59, 0.11));
    color = mix(vec3(grey) * 0.55, color, clear);
    fragColor = vec4(color, 1.0);
}
