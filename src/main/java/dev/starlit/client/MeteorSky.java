package dev.starlit.client;

import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Meteor;
import dev.starlit.core.Rng;
import dev.starlit.core.SkyEvents;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class MeteorSky {
    public static final int MAX_METEORS = 48;
    public static final float SPORADIC_PER_MINUTE = 8.0F;
    public static final float SHOWER_PER_MINUTE = 70.0F;

    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final Rng RNG = new Rng(System.nanoTime());
    private static int spawned;
    private static double forcedShowerUntil = -1.0;

    private MeteorSky() {
    }

    public static List<Meteor> meteors() {
        return METEORS;
    }

    public static int spawned() {
        return spawned;
    }

    public static void forceShower(double now, double seconds) {
        forcedShowerUntil = now + seconds;
    }

    public static void clear() {
        METEORS.clear();
        forcedShowerUntil = -1.0;
    }

    public static boolean showerActive(StarlitConfig config, double now) {
        return now < forcedShowerUntil
                || SkyEvents.meteorShower(SkyState.night, config.meteorShowers()) && SkyEvents.showerStrength(SkyState.timeOfNight) > 0.05F;
    }

    public static void tick(StarlitConfig config, double now) {
        METEORS.removeIf(meteor -> meteor.done(now) || meteor.born > now + 1.0);
        float visible = SkyState.darkness;
        if (visible < 0.15F) {
            return;
        }
        float perMinute = config.shootingStars() / 100.0F * SPORADIC_PER_MINUTE * visible;
        if (perMinute > 0.0F && RNG.nextFloat() < perMinute / 1200.0F) {
            add(Meteor.sporadic(RNG, now, config.fireballs()));
        }
        float shower = 0.0F;
        if (now < forcedShowerUntil) {
            shower = 1.0F;
        } else if (SkyEvents.meteorShower(SkyState.night, config.meteorShowers())) {
            shower = SkyEvents.showerStrength(SkyState.timeOfNight);
        }
        if (shower > 0.0F && RNG.nextFloat() < SHOWER_PER_MINUTE * shower * visible / 1200.0F) {
            float[] radiant = toWorld(SkyEvents.radiant(SkyState.night), SkyState.starAngle);
            if (now < forcedShowerUntil && radiant[1] < 0.3F) {
                radiant = new float[]{0.28F, 0.92F, -0.27F};
            }
            if (radiant[1] > -0.1F) {
                add(Meteor.fromRadiant(RNG, now, radiant, config.fireballs()));
            }
        }
    }

    public static void spawnNow(double now, boolean fireball) {
        Meteor meteor = Meteor.sporadic(RNG, now, false);
        if (fireball) {
            Meteor fire;
            do {
                fire = Meteor.sporadic(RNG, now, true);
            } while (!fire.fireball);
            meteor = fire;
        }
        add(meteor);
    }

    public static void spawnToward(double now, float[] direction) {
        float[] start = dev.starlit.core.Vec.normalize(direction);
        float[] travel = dev.starlit.core.Vec.anyPerpendicular(start);
        add(new Meteor(start, travel, 0.05F, 0.12F, 0.6F, 2.5F, 1.0F, new float[]{0.9F, 0.95F, 1.0F}, false, now));
    }

    private static void add(Meteor meteor) {
        if (meteor != null && METEORS.size() < MAX_METEORS) {
            METEORS.add(meteor);
            spawned++;
        }
    }

    public static float[] toWorld(float[] starDirection, float starAngle) {
        Matrix3f rotation = new Matrix3f().rotateY((float) Math.toRadians(-90.0)).rotateX(starAngle);
        Vector3f v = rotation.transform(new Vector3f(starDirection[0], starDirection[1], starDirection[2]));
        return new float[]{v.x, v.y, v.z};
    }
}
