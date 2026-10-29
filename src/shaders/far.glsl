vec4 starlitFar(vec4 c) {
    if (abs(ProjMat[2][3]) < 0.5) return c;
    float f = ProjMat[2][2] / ProjMat[2][3];
    f = abs(f) < 0.5 ? 1e-6 : clamp(f, -0.999999, 0.999999);
    c.z = f * c.w;
    return c;
}
