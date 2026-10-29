package dev.starlit.client;

import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Flock;
import dev.starlit.core.Rng;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BirdSky {
    public static final int MAX_FLOCKS = 2;
    public static final float FLOCKS_PER_MINUTE = 1.2F;

    private static final List<Flock> FLOCKS = new ArrayList<>();
    private static final Rng RNG = new Rng(System.nanoTime() ^ 0x5DEECE66DL);
    private static int spawned;

    private BirdSky() {
    }

    public static List<Flock> flocks() {
        return FLOCKS;
    }

    public static int spawned() {
        return spawned;
    }

    public static void clear() {
        FLOCKS.clear();
    }

    public static void tick(StarlitConfig config, double now, Vec3 eye, float rain) {
        FLOCKS.removeIf(flock -> flock.done(now) || flock.born > now + 1.0);
        if (!config.birds()) {
            FLOCKS.clear();
            return;
        }
        if (SkyState.sunHeight < 0.12F || rain > 0.2F || FLOCKS.size() >= MAX_FLOCKS) {
            return;
        }
        if (RNG.nextFloat() < FLOCKS_PER_MINUTE / 1200.0F) {
            add(Flock.random(RNG, now, eye.x, eye.y, eye.z));
        }
    }

    public static void spawnToward(double now, Vec3 eye, float[] look) {
        add(Flock.toward(RNG, now, eye.x, eye.y, eye.z, look));
    }

    private static void add(Flock flock) {
        if (FLOCKS.size() >= MAX_FLOCKS) {
            FLOCKS.remove(0);
        }
        FLOCKS.add(flock);
        spawned++;
    }
}
