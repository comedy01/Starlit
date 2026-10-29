package dev.starlit.core;

public record Star(float x, float y, float z, float size, float red, float green, float blue, float phase) {
    public float[] direction() {
        return new float[]{x, y, z};
    }
}
