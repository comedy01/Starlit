STARLIT_FRAGMENT_HEADER
STARLIT_NOISE

IN(0) vec3 direction;

OUT(0) vec4 fragColor;

vec3 dogDirection(vec3 center, float alt, float side) {
    float ca = cos(alt);
    float sa = sin(alt);
    float cosShift = (cos(radians(22.0)) - sa * sa) / max(ca * ca, 1.0e-4);
    float shift = acos(clamp(cosShift, -1.0, 1.0));
    float azimuth = atan(center.z, center.x) + side * shift;
    return vec3(cos(azimuth) * ca, sa, sin(azimuth) * ca);
}

void main() {
    vec3 d = normalize(direction);
    vec3 center = normalize(ColorModulator.xyz);
    float strength = ColorModulator.w;
    float dogs = TextureMat[0].x;
    float pillar = TextureMat[0].y;
    float time = TextureMat[0].w;

    float r = degrees(acos(clamp(dot(d, center), -1.0, 1.0)));
    if (r > 48.0 || d.y < -0.02) {
        fragColor = vec4(0.0);
        return;
    }
    float x = (r - 22.0) / 1.4;
    float ring = x < 0.0 ? exp(-x * x * 5.0) : exp(-x * 0.9) * 0.9 + 0.1 * exp(-x * x);
    ring *= smoothstep(18.0, 21.5, r);
    vec3 tint = mix(vec3(1.0, 0.5, 0.32), vec3(0.88, 0.93, 1.0), smoothstep(-0.4, 1.6, x));
    float patches = 0.55 + 0.45 * starlitNoise3(d * 3.2 + vec3(time * 0.01));
    vec3 color = tint * ring * patches * 0.11;

    float alt = asin(clamp(center.y, -1.0, 1.0));
    float dogTime = (1.0 - smoothstep(radians(45.0), radians(60.0), alt)) * dogs;
    for (int i = 0; i < 2 && dogTime > 0.001; i++) {
        float side = i == 0 ? -1.0 : 1.0;
        vec3 p = dogDirection(center, alt, side);
        vec3 v = d - p;
        float vy = v.y;
        vec3 toSun = normalize(center - p * dot(center, p));
        float along = dot(v, -toSun);
        float spread = length(v - vec3(0.0, vy, 0.0) - (-toSun) * along);
        float spot = exp(-vy * vy / 0.0011 - along * along / 0.0016 - spread * spread / 0.0011);
        float tail = exp(-vy * vy / 0.00035) * exp(-max(along, 0.0) * 9.0) * smoothstep(-0.01, 0.02, along) * 0.35;
        vec3 dogTint = mix(vec3(1.0, 0.45, 0.25), vec3(1.0, 0.97, 0.92), smoothstep(-0.03, 0.03, along));
        color += dogTint * (spot * 0.55 + tail * 0.4) * dogTime;
    }

    vec2 flatDir = d.xz / max(length(d.xz), 1.0e-4);
    vec2 centerFlat = center.xz / max(length(center.xz), 1.0e-4);
    float sideways = 1.0 - dot(flatDir, centerFlat);
    float rise = d.y - center.y;
    float column = exp(-sideways / 0.00012) * exp(-abs(rise) * 5.0) * smoothstep(-0.08, 0.0, rise);
    float low = 1.0 - smoothstep(0.05, 0.2, abs(center.y));
    color += vec3(1.0, 0.72, 0.5) * column * 0.3 * pillar * low;

    color *= strength * smoothstep(-0.02, 0.04, d.y);
    color += vec3(starlitDither(gl_FragCoord.xy));
    fragColor = vec4(max(color, vec3(0.0)), 0.0);
}
