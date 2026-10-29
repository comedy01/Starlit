STARLIT_VERTEX_HEADER

IN(0) vec3 Position;
IN(1) vec2 UV0;
IN(2) vec4 Color;

OUT(0) vec2 texCoord0;
OUT(1) vec4 vertexColor;

STARLIT_FAR

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = starlitFar(gl_Position);
    texCoord0 = UV0;
    vertexColor = Color;
}
