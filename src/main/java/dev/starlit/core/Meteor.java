package dev.starlit.core;

public final class Meteor {
    public final float[] start;
    public final float[] travel;
    public final float speed;
    public final float length;
    public final float width;
    public final float duration;
    public final float brightness;
    public final float[] color;
    public final boolean fireball;
    public final double born;

    public Meteor(float[] start, float[] travel, float speed, float length, float width, float duration,
                  float brightness, float[] color, boolean fireball, double born) {
        this.start = start;
        this.travel = travel;
        this.speed = speed;
        this.length = length;
        this.width = width;
        this.duration = duration;
        this.brightness = brightness;
        this.color = color;
        this.fireball = fireball;
        this.born = born;
    }

    public float age(double now) {
        return (float) (now - born);
    }

    public boolean done(double now) {
        return age(now) > duration;
    }

    public float[] head(double now) {
        float travelled = speed * Math.max(0.0F, Math.min(age(now), duration));
        return Vec.normalize(Vec.axpy(start, travelled, travel));
    }

    public float tail(double now) {
        float travelled = speed * Math.max(0.0F, Math.min(age(now), duration));
        return Math.min(length, travelled);
    }

    public float fade(double now) {
        float t = age(now) / duration;
        if (t < 0.0F || t > 1.0F) {
            return 0.0F;
        }
        float in = Math.min(1.0F, t / 0.12F);
        float out = t > 0.7F ? Math.max(0.0F, (1.0F - t) / 0.3F) : 1.0F;
        float flare = fireball ? 1.0F + 0.8F * (float) Math.exp(-((t - 0.75F) * (t - 0.75F)) / 0.004F) : 1.0F;
        return in * out * flare * brightness;
    }

    private static final float[][] SPORADIC_COLORS = {
            {0.75F, 0.85F, 1.0F},
            {1.0F, 0.95F, 0.8F},
            {0.7F, 1.0F, 0.85F},
            {1.0F, 0.8F, 0.6F}
    };

    private static final float[][] FIREBALL_COLORS = {
            {0.4F, 1.0F, 0.55F},
            {1.0F, 0.55F, 0.25F},
            {0.6F, 0.7F, 1.0F},
            {1.0F, 0.4F, 0.8F}
    };

    public static Meteor sporadic(Rng rng, double now, boolean fireballs) {
        float[] start = rng.unitVector();
        start[1] = 0.25F + Math.abs(start[1]) * 0.7F;
        start = Vec.normalize(start);
        float[] tangent = Vec.anyPerpendicular(start);
        float spin = rng.range(0.0F, (float) (Math.PI * 2.0));
        float[] other = Vec.cross(start, tangent);
        float[] travel = Vec.add(Vec.scale(tangent, (float) Math.cos(spin)), Vec.scale(other, (float) Math.sin(spin)));
        float[] down = Vec.tangentAway(start, Vec.of(0.0F, 1.0F, 0.0F));
        travel = Vec.normalize(Vec.axpy(travel, 0.9F, down));
        boolean fireball = fireballs && rng.nextFloat() < 0.05F;
        return build(rng, now, start, travel, fireball, false);
    }

    public static Meteor fromRadiant(Rng rng, double now, float[] radiant, boolean fireballs) {
        float[] start = null;
        for (int tries = 0; tries < 12; tries++) {
            float[] offset = rng.unitVector();
            float[] candidate = Vec.normalize(Vec.axpy(radiant, rng.range(0.25F, 1.6F), offset));
            if (candidate[1] > 0.18F && Vec.dot(candidate, radiant) > 0.2F) {
                start = candidate;
                break;
            }
        }
        if (start == null) {
            return null;
        }
        float[] travel = Vec.tangentAway(start, radiant);
        boolean fireball = fireballs && rng.nextFloat() < 0.03F;
        return build(rng, now, start, travel, fireball, true);
    }

    private static Meteor build(Rng rng, double now, float[] start, float[] travel, boolean fireball, boolean shower) {
        float speed = rng.range(0.28F, 0.55F) * (fireball ? 0.55F : 1.0F);
        float duration = rng.range(0.45F, 1.1F) * (fireball ? 2.2F : 1.0F);
        float length = rng.range(0.1F, 0.22F) * (fireball ? 1.7F : 1.0F);
        float width = fireball ? rng.range(1.3F, 1.8F) : rng.range(0.45F, 0.7F);
        float brightness = fireball ? 2.2F : rng.range(0.7F, 1.3F);
        float[] color = fireball
                ? FIREBALL_COLORS[(int) (rng.nextFloat() * FIREBALL_COLORS.length) % FIREBALL_COLORS.length]
                : shower ? new float[]{0.85F, 0.9F, 1.0F}
                : SPORADIC_COLORS[(int) (rng.nextFloat() * SPORADIC_COLORS.length) % SPORADIC_COLORS.length];
        return new Meteor(start, travel, speed, length, width, duration, brightness, color, fireball, now);
    }
}
