package dev.starlit.client;

public final class SkyState {
    public static float starAngle;
    public static float darkness;
    public static double time;
    public static float auroraLevel;
    public static float auroraNorthBias = 0.85F;
    public static long night = Long.MIN_VALUE;
    public static float timeOfNight;
    public static boolean seenThisFrame;
    public static float sunHeight;
    public static float[] sun = {0.0F, 1.0F, 0.0F};
    public static float haloLevel;
    public static float rainbowLevel;

    private SkyState() {
    }
}
