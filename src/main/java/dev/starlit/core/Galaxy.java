package dev.starlit.core;

public final class Galaxy {
    public static final float[] NORMAL = Vec.normalize(Vec.of(0.35F, 0.30F, 0.887F));
    public static final float[] E1 = Vec.normalize(Vec.cross(Vec.of(0.0F, 1.0F, 0.0F), NORMAL));
    public static final float[] E2 = Vec.cross(NORMAL, E1);
    public static final float CORE_ANGLE = 5.34F;
    public static final float[] CORE = Vec.normalize(Vec.add(
            Vec.scale(E1, (float) Math.cos(CORE_ANGLE)),
            Vec.scale(E2, (float) Math.sin(CORE_ANGLE))));

    private Galaxy() {
    }

    public static float[] inPlane(float longitude, float latitude) {
        float c = (float) Math.cos(latitude);
        float[] around = Vec.add(
                Vec.scale(E1, (float) Math.cos(longitude) * c),
                Vec.scale(E2, (float) Math.sin(longitude) * c));
        return Vec.normalize(Vec.axpy(around, (float) Math.sin(latitude), NORMAL));
    }

    public static float bulge(float[] direction) {
        return (float) Math.exp((Vec.dot(direction, CORE) - 1.0F) * 5.0F);
    }
}
