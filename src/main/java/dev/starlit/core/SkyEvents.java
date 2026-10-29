package dev.starlit.core;

public final class SkyEvents {
    public static final int COMET_CYCLE = 6;
    public static final long DAY = 24000L;

    private SkyEvents() {
    }

    public static long night(long clockTime) {
        return Math.floorDiv(clockTime, DAY);
    }

    public static float timeOfNight(long clockTime, float partial) {
        double t = Math.floorMod(clockTime, DAY) + partial;
        return (float) ((t - 12000.0) / 12000.0);
    }

    public static boolean meteorShower(long night, int chance) {
        return chance > 0 && Rng.unit(night * 31L + 7L) < chance / 100.0 * 0.5;
    }

    public static float[] radiant(long night) {
        Rng rng = new Rng(night * 977L + 13L);
        float[] dir = rng.unitVector();
        if (dir[1] > -0.35F) {
            dir[1] = -0.35F - Math.abs(dir[1]) * 0.5F;
        }
        return Vec.normalize(dir);
    }

    public static float showerStrength(float timeOfNight) {
        if (timeOfNight < 0.0F || timeOfNight > 1.0F) {
            return 0.0F;
        }
        float x = (timeOfNight - 0.62F) / 0.28F;
        return (float) Math.exp(-x * x);
    }

    public static boolean geomagneticStorm(long night) {
        return Rng.unit(night * 13L + 11L) < 0.08;
    }

    public static boolean haloDay(long day) {
        return Rng.unit(day * 19L + 5L) < 0.2;
    }

    public static float auroraActivity(long night) {
        return geomagneticStorm(night) ? 1.0F : 0.45F + 0.55F * (float) Rng.unit(night * 7L + 5L);
    }

    public static long cometCycle(long night) {
        return Math.floorDiv(night, COMET_CYCLE);
    }

    public static boolean comet(long night, int chance) {
        return chance > 0 && Rng.unit(cometCycle(night) * 17L + 3L) < chance / 100.0 * 0.6;
    }

    public static float cometStrength(long night) {
        long into = Math.floorMod(night, COMET_CYCLE);
        return (float) Math.sin(Math.PI * (into + 0.5) / COMET_CYCLE);
    }

    public static float[] cometDirection(long night) {
        long cycle = cometCycle(night);
        Rng rng = new Rng(cycle * 389L + 71L);
        float[] dir = rng.unitVector();
        dir[1] = -0.45F - Math.abs(dir[1]) * 0.45F;
        float[] drift = Vec.anyPerpendicular(Vec.normalize(dir));
        float step = (Math.floorMod(night, COMET_CYCLE) - COMET_CYCLE / 2.0F) * 0.035F;
        return Vec.normalize(Vec.axpy(Vec.normalize(dir), step, drift));
    }

    public static float planetAngle(int planet, long night) {
        switch (planet) {
            case 0:
                return (float) Math.toRadians(180.0 + 38.0 * Math.sin(night * 0.11));
            case 1:
                return (float) Math.toRadians(150.0 + Math.floorMod(night * 4L, 360L) * 0.15);
            default:
                return (float) Math.toRadians(205.0 - Math.floorMod(night, 720L) * 0.08);
        }
    }

    public static float[] planetDirection(int planet, long night) {
        float angle = planetAngle(planet, night);
        float lean = planet == 0 ? 0.04F : planet == 1 ? -0.03F : 0.02F;
        return Vec.normalize(Vec.of(lean, (float) Math.cos(angle), (float) Math.sin(angle)));
    }
}
