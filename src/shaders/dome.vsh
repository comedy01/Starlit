STARLIT_VERTEX_HEADER

IN(0) vec3 Position;

OUT(0) vec3 direction;

STARLIT_FAR

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = starlitFar(gl_Position);
    direction = Position;
}
