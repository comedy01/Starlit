package dev.starlit.core;

public final class Flock {
    private static final float REACH = 260.0F;

    public final double born;
    public final double life;
    public final double anchorX;
    public final double anchorY;
    public final double anchorZ;
    public final int size;
    private final float[] start;
    private final float[] velocity;
    private final float[] forward;
    private final float[] right;
    private final float spacing;
    private final float[] rates;
    private final float[] phases;

    private Flock(double born, double anchorX, double anchorY, double anchorZ, float[] start, float[] velocity,
                  int size, float spacing, Rng rng) {
        this.born = born;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.start = start;
        this.velocity = velocity;
        this.size = size;
        this.spacing = spacing;
        float speed = (float) Math.sqrt(Vec.dot(velocity, velocity));
        this.forward = Vec.scale(velocity, 1.0F / speed);
        this.right = Vec.of(-forward[2], 0.0F, forward[0]);
        this.life = 2.0 * REACH / speed;
        this.rates = new float[size];
        this.phases = new float[size];
        for (int i = 0; i < size; i++) {
            rates[i] = rng.range(13.0F, 18.0F);
            phases[i] = rng.range(0.0F, 6.2832F);
        }
    }

    public static Flock random(Rng rng, double now, double anchorX, double anchorY, double anchorZ) {
        float heading = rng.range(0.0F, 6.2832F);
        float speed = rng.range(7.0F, 11.0F);
        float[] dir = Vec.of((float) Math.cos(heading), 0.0F, (float) Math.sin(heading));
        float[] side = Vec.of(-dir[2], 0.0F, dir[0]);
        float miss = rng.range(-120.0F, 120.0F);
        float height = rng.range(35.0F, 85.0F);
        float[] start = Vec.add(Vec.add(Vec.scale(dir, -REACH), Vec.scale(side, miss)), Vec.of(0.0F, height, 0.0F));
        int size = 5 + (int) (rng.nextFloat() * 8.0F);
        return new Flock(now, anchorX, anchorY, anchorZ, start, Vec.scale(dir, speed), size, rng.range(2.2F, 3.2F), rng);
    }

    public static Flock toward(Rng rng, double now, double anchorX, double anchorY, double anchorZ, float[] look) {
        float[] flatLook = Vec.normalize(Vec.of(look[0], 0.0F, look[2]));
        float[] dir = Vec.of(-flatLook[2], 0.0F, flatLook[0]);
        float speed = 9.0F;
        float distance = 90.0F;
        float lift = Math.max(0.2F, Math.min(0.9F, look[1]));
        float[] middle = Vec.add(Vec.scale(flatLook, distance), Vec.of(0.0F, distance * lift / (float) Math.sqrt(1.0F - lift * lift), 0.0F));
        float[] start = Vec.add(middle, Vec.scale(dir, -REACH));
        return new Flock(now - REACH / speed, anchorX, anchorY, anchorZ, start, Vec.scale(dir, speed), 9, 2.6F, rng);
    }

    public boolean done(double now) {
        return now - born > life;
    }

    public float fade(double now) {
        double t = (now - born) / life;
        if (t <= 0.0 || t >= 1.0) {
            return 0.0F;
        }
        return (float) Math.min(1.0, Math.min(t, 1.0 - t) * 8.0);
    }

    public float[] bird(int i, double now) {
        float t = (float) (now - born);
        float[] center = Vec.axpy(start, t, velocity);
        int rank = (i + 1) / 2;
        float side = i % 2 == 1 ? -1.0F : 1.0F;
        float[] offset = Vec.add(Vec.scale(forward, -rank * spacing), Vec.scale(right, side * rank * spacing * 0.8F));
        float bob = (float) Math.sin(t * 0.7F + phases[i]) * 0.4F;
        return Vec.add(Vec.add(center, offset), Vec.of(0.0F, bob, 0.0F));
    }

    public float flap(int i, double now) {
        double t = now - born;
        double glide = Math.sin(t * 0.45 + phases[i] * 0.5);
        if (glide > 0.55) {
            return 0.25F;
        }
        return (float) Math.sin(t * rates[i] + phases[i]);
    }
}
