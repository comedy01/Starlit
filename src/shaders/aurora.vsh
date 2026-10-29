STARLIT_VERTEX_HEADER

IN(0) vec3 Position;

OUT(0) vec3 ribbon;

STARLIT_FAR

void main() {
    float u = Position.x;
    float v = Position.y;
    float id = Position.z;
    float time = ColorModulator.y;
    float northBias = ColorModulator.z;

    float a = fract(sin(id * 12.9898 + 1.3) * 437.5453);
    float b = fract(sin(id * 78.233 + 4.1) * 239.1371);
    float c = fract(sin(id * 39.425 + 2.7) * 911.7713);

    float spread = mix(2.6, 1.15, northBias) + 0.5 * a;
    float center = (b - 0.5) * mix(2.4, 0.9, northBias);
    float phi = center + (u - 0.5) * spread;
    phi += 0.05 * sin(u * 11.0 + time * 0.11 + id * 3.0);

    float near = mix(0.42, 1.0, northBias);
    float radius = (62.0 + 26.0 * c) * near;
    radius += 9.0 * sin(phi * 3.1 + time * 0.045 + id * 1.9);
    radius += 4.5 * sin(phi * 7.7 - time * 0.07 + id * 2.3);
    radius += 2.0 * sin(phi * 17.0 + time * 0.13 + id);

    float base = (5.0 + 9.0 * b) * mix(2.2, 1.0, northBias);
    float height = (26.0 + 26.0 * a) * mix(1.6, 1.0, northBias);
    float y = base + v * height + 3.0 * sin(phi * 4.0 + time * 0.06 + id) * (1.0 - v);

    vec3 world = vec3(sin(phi) * radius, y, -cos(phi) * radius);
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);
    gl_Position = starlitFar(gl_Position);
    ribbon = vec3(u, v, id);
}
