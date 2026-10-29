package dev.starlit.core;

import java.util.List;

public final class DeepSky {
    public static final float SPIRAL = 0.0F;
    public static final float LOOSE_SPIRAL = 0.45F;
    public static final float ELLIPTICAL = 1.0F;

    public record Object(float[] direction, float size, float rotation, float tilt, float kind, float red, float green,
                         float blue) {
    }

    public static final List<Object> GALAXIES = List.of(
            new Object(Vec.normalize(Vec.of(-0.15F, -0.80F, -0.58F)), 7.5F, 0.6F, 0.36F, SPIRAL, 1.0F, 0.95F, 0.9F),
            new Object(Vec.normalize(Vec.of(0.72F, -0.62F, 0.30F)), 3.6F, 1.9F, 0.85F, LOOSE_SPIRAL, 0.9F, 0.95F, 1.0F),
            new Object(Vec.normalize(Vec.of(-0.62F, -0.68F, 0.38F)), 3.0F, 0.2F, 0.18F, SPIRAL, 0.85F, 0.9F, 1.0F),
            new Object(Vec.normalize(Vec.of(0.28F, -0.42F, -0.86F)), 2.4F, 1.1F, 0.7F, ELLIPTICAL, 1.0F, 0.9F, 0.78F),
            new Object(Vec.normalize(Vec.of(-0.88F, -0.20F, -0.42F)), 2.8F, 2.6F, 0.55F, LOOSE_SPIRAL, 0.95F, 0.92F, 1.0F));

    private DeepSky() {
    }

    public static float[][] corners(Object object, float distance) {
        float[] center = Vec.scale(object.direction(), distance);
        float[] east = Vec.anyPerpendicular(object.direction());
        float[] north = Vec.cross(object.direction(), east);
        float c = (float) Math.cos(object.rotation());
        float s = (float) Math.sin(object.rotation());
        float[] u = Vec.add(Vec.scale(east, c), Vec.scale(north, s));
        float[] v = Vec.add(Vec.scale(east, -s), Vec.scale(north, c));
        float[] du = Vec.scale(u, object.size());
        float[] dv = Vec.scale(v, object.size() * object.tilt());
        return new float[][]{
                Vec.sub(Vec.sub(center, du), dv),
                Vec.add(Vec.sub(center, dv), du),
                Vec.add(Vec.add(center, du), dv),
                Vec.sub(Vec.add(center, dv), du)};
    }
}
