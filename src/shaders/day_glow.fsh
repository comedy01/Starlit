STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

void main() {
    vec3 d = normalize(direction);
    vec3 sun = normalize(ColorModulator.xyz);
    float strength = ColorModulator.w;
    float atmosphere = TextureMat[0].x;
    float sunsets = TextureMat[0].y;
    float time = TextureMat[0].w;

    float h = d.y;
    float above = smoothstep(-0.01, 0.06, h);
    float day = smoothstep(-0.12, 0.2, sun.y);
    float twilight = smoothstep(-0.3, -0.02, sun.y) * (1.0 - smoothstep(0.04, 0.42, sun.y));
    float warm = 1.0 - smoothstep(0.02, 0.5, sun.y);

    vec2 flatDir = d.xz / max(length(d.xz), 1.0e-4);
    float sunFlatLength = length(sun.xz);
    vec2 sunFlat = sun.xz / max(sunFlatLength, 1.0e-4);
    float az = dot(flatDir, sunFlat) * smoothstep(0.0, 0.3, sunFlatLength);

    float c = dot(d, sun);
    vec3 sunTint = mix(vec3(1.0, 0.96, 0.86), vec3(1.0, 0.56, 0.24), warm);
    float aureole = exp((c - 1.0) * 260.0) * 0.5 + exp((c - 1.0) * 30.0) * 0.2 + exp((c - 1.0) * 4.5) * 0.07;
    vec3 color = sunTint * aureole * atmosphere * max(day, twilight * 0.8) * smoothstep(-0.1, 0.03, h);

    float haze = exp(-max(h, 0.0) * 8.0);
    color += vec3(0.86, 0.92, 1.0) * haze * (0.05 + 0.05 * max(az, 0.0)) * atmosphere * day * above;

    float lowBand = exp(-max(h - 0.03, 0.0) * 7.0);
    float sunSide = pow(az * 0.5 + 0.5, 3.0);
    vec3 fire = mix(vec3(1.0, 0.36, 0.1), vec3(1.0, 0.64, 0.32), smoothstep(0.02, 0.14, h));
    float upperBand = exp(-pow((h - 0.3) / 0.2, 2.0));
    vec3 dusk = fire * lowBand * 0.3 + vec3(0.62, 0.34, 0.72) * upperBand * 0.08;
    color += dusk * sunSide * twilight * sunsets * above;

    float anti = pow(max(-az, 0.0), 1.3);
    float belt = exp(-pow((h - 0.13) / 0.07, 2.0));
    float afterglow = smoothstep(-0.25, -0.03, sun.y) * (1.0 - smoothstep(0.02, 0.2, sun.y));
    color += vec3(0.95, 0.55, 0.64) * belt * anti * 0.13 * afterglow * sunsets * above;

    float rayTime = smoothstep(-0.2, -0.02, sun.y) * (1.0 - smoothstep(0.03, 0.25, sun.y)) * sunsets;
    if (rayTime > 0.001 && c > 0.0) {
        vec3 side = normalize(cross(sun, vec3(0.0, 1.0, 0.0)) + vec3(0.0, 0.0, 1.0e-4));
        vec3 up = cross(side, sun);
        vec3 u = d - sun * c;
        float theta = atan(dot(u, up), dot(u, side));
        float n = starlitNoise3(vec3(cos(theta) * 7.0, sin(theta) * 7.0, time * 0.015));
        float rays = smoothstep(0.42, 0.78, n) * exp((c - 1.0) * 2.2);
        color += vec3(1.0, 0.7, 0.42) * rays * 0.11 * rayTime * above;
    }

    color *= strength;
    color += vec3(starlitDither(gl_FragCoord.xy));
    fragColor = vec4(max(color, vec3(0.0)), 0.0);
}
