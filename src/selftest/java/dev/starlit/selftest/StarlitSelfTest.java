package dev.starlit.selftest;

import dev.starlit.client.Compat;
import dev.starlit.client.MeteorSky;
import dev.starlit.client.SkyState;
import dev.starlit.client.StarlitClient;
import dev.starlit.client.StarlitRuntime;
import dev.starlit.client.gui.StarlitSettingsScreen;
import dev.starlit.client.render.SkyPainter;
import dev.starlit.client.render.SkyPainter.Layer;
import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Constellations;
import dev.starlit.core.DeepSky;
import dev.starlit.core.Galaxy;
import dev.starlit.core.SkyEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class StarlitSelfTest {
    private static final String WORLD = "starlit-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private final Map<String, Double> brightness = new HashMap<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;
    private int fov;
    private CloudStatus clouds;
    private long frameStart;
    private int frameCount;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                Opts.chatVisibility(mc).set(ChatVisiblity.HIDDEN);
                fov = Opts.fov(mc).get();
                clouds = Opts.cloudStatus(mc).get();
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                boolean gallery = System.getProperty("starlit.only", "").equals("gallery");
                Worlds.create(mc, WORLD, !gallery, gallery ? Long.getLong("starlit.seed", 20261008L) : 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        for (Layer layer : Layer.values()) {
            log("draws " + layer + ": " + SkyPainter.draws(layer) + (SkyPainter.broken(layer) ? " (BROKEN)" : ""));
        }
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        StarlitConfig config = StarlitClient.config();
        config.resetToDefaults();
        StarlitClient.saveConfig();
        Opts.fov(mc).set(fov);
        Opts.cloudStatus(mc).set(clouds);
        mc.stop();
    }

    private void plan(Minecraft mc) {
        StarlitConfig config = StarlitClient.config();

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            config.setShootingStars(0);
            config.setMeteorShowers(0);
            config.setComets(0);
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule advance_weather false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "gamerule doDaylightCycle false");
            run(mc, "gamerule doWeatherCycle false");
            run(mc, "gamerule doMobSpawning false");
            run(mc, "weather clear 1000000");
            run(mc, "time set 18000");
            run(mc, "gamemode spectator @p");
            run(mc, "tp @p 0.5 -40 0.5 180 -55");
            Screens.hideHud(mc);
        });
        String only = System.getProperty("starlit.only", "");
        if (only.equals("settings")) {
            then(5, config::resetToDefaults);
            planSettings(mc, config);
            return;
        }
        if (only.equals("perf")) {
            planPerformance(mc, config);
            return;
        }
        if (only.equals("day")) {
            then(30, () -> Opts.cloudStatus(mc).set(CloudStatus.OFF));
            planDay(mc, config);
            return;
        }
        if (only.equals("gallery")) {
            planGallery(mc, config);
            return;
        }
        if (only.equals("iris")) {
            planIris(mc, Boolean.parseBoolean(System.getProperty("starlit.shaders", "true")));
            return;
        }
        if (only.equals("dh")) {
            planDh(mc, config);
            return;
        }
        if (only.equals("dim")) {
            planDimension(mc, config);
            return;
        }
        if (only.equals("look")) {
            then(30, () -> Opts.cloudStatus(mc).set(CloudStatus.OFF));
            planLook(mc, config);
            return;
        }
        then(60, () -> {
            check(SkyState.darkness > 0.9F, "midnight sky was not dark: " + SkyState.darkness);
            check(!SkyPainter.broken(Layer.STARS), "the star layer failed");
            check(SkyPainter.draws(Layer.STARS) > 0, "no stars were drawn at midnight");
            check(SkyPainter.draws(Layer.MILKY_WAY) > 0, "the Milky Way was not drawn");
            check(SkyPainter.replacesVanillaStars(), "vanilla stars were not replaced");
            config.setEnabled(false);
        });
        then(20, () -> {
            check(!SkyPainter.replacesVanillaStars(), "vanilla stars stayed hidden with Starlit off");
            shoot(mc, "vanilla");
        });
        then(5, () -> config.setEnabled(true));
        then(20, () -> {
            shoot(mc, "starlit-clouds");
            Opts.cloudStatus(mc).set(CloudStatus.OFF);
            config.setEnabled(false);
        });
        then(20, () -> {
            shoot(mc, "vanilla-no-clouds");
            config.setEnabled(true);
        });
        then(20, () -> shoot(mc, "starlit"));
        then(20, () -> {
            check(average("starlit") > average("vanilla-no-clouds") + 2.0,
                    "Starlit's sky was not brighter than vanilla: " + average("starlit") + " vs " + average("vanilla-no-clouds"));
            face(mc, Galaxy.CORE);
        });
        then(20, () -> shoot(mc, "milky-way-core"));
        then(20, () -> {
            check(average("milky-way-core") > average("vanilla-no-clouds") + 6.0,
                    "the Milky Way core was not bright: " + average("milky-way-core"));
            config.setMilkyWay(0);
            config.setNebulae(false);
        });
        then(20, () -> {
            shoot(mc, "milky-way-off");
            config.setMilkyWay(100);
            config.setNebulae(true);
        });
        then(20, () -> check(average("milky-way-off") < average("milky-way-core") - 3.0,
                "turning the Milky Way off did not darken the sky: " + average("milky-way-off")));

        planDay(mc, config);
        planDeepSky(mc, config);
        planConstellations(mc, config);
        planComet(mc, config);
        planMeteors(mc, config);
        planAurora(mc, config);
        planStarCount(mc, config);
        planFades(mc, config);
        planWishes(mc, config);
        planEnd(mc, config);
        planSettings(mc, config);
    }

    private void planWishes(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            config.setShootingStars(0);
            config.setMeteorShowers(0);
            MeteorSky.clear();
            StarlitRuntime.resetWishCooldown();
            wishesBefore = StarlitRuntime.wishes();
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-40.0F);
        });
        then(5, () -> {
            net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
            MeteorSky.spawnToward(mc.level.getGameTime() / 20.0, new float[]{(float) look.x, (float) look.y, (float) look.z});
        });
        within(60, () -> StarlitRuntime.wishes() > wishesBefore, () -> {
            check(StarlitRuntime.wishes() == wishesBefore + 1, "looking at a shooting star did not make a wish");
            config.setWishes(false);
            StarlitRuntime.resetWishCooldown();
            MeteorSky.clear();
            net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
            MeteorSky.spawnToward(mc.level.getGameTime() / 20.0, new float[]{(float) look.x, (float) look.y, (float) look.z});
        });
        then(30, () -> {
            check(StarlitRuntime.wishes() == wishesBefore + 1, "a wish was made with Wishes off");
            config.setWishes(true);
            MeteorSky.clear();
        });
    }

    private int wishesBefore;

    private void planDay(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            run(mc, "time set 6000");
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(-35.0F);
            config.setEnabled(false);
        });
        then(30, () -> shoot(mc, "day-vanilla"));
        then(5, () -> {
            config.setEnabled(true);
            dayDraws = SkyPainter.draws(Layer.DAY_SKY);
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.DAY_SKY) > dayDraws, "the day sky was not drawn at noon");
            check(!SkyPainter.broken(Layer.DAY_SKY), "the day sky layer failed");
            shoot(mc, "day-atmosphere");
            mc.player.setXRot(-75.0F);
        });
        then(20, () -> {
            shoot(mc, "day-up");
            config.setAtmosphere(false);
            config.setSunsets(false);
            dayDraws = SkyPainter.draws(Layer.DAY_SKY);
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.DAY_SKY) == dayDraws, "the day sky was drawn with Atmosphere and Sunsets off");
            config.setAtmosphere(true);
            config.setSunsets(true);
            run(mc, "time set 12150");
        });
        then(30, () -> faceWorld(mc, flatToward(SkyState.sun, 0.12F)));
        then(20, () -> {
            shoot(mc, "sunset");
            config.setEnabled(false);
        });
        then(20, () -> {
            shoot(mc, "sunset-vanilla");
            config.setEnabled(true);
            run(mc, "time set 12650");
        });
        then(30, () -> faceWorld(mc, flatToward(SkyState.sun, 0.06F)));
        then(20, () -> {
            shoot(mc, "dusk-glow");
            faceWorld(mc, flatToward(new float[]{-SkyState.sun[0], 0.0F, -SkyState.sun[2]}, 0.18F));
        });
        then(20, () -> {
            shoot(mc, "dusk-opposite");
            run(mc, "time set 9800");
            StarlitRuntime.forceHalo(true);
            SkyState.haloLevel = 1.0F;
        });
        then(30, () -> {
            faceWorld(mc, SkyState.sun);
            Opts.fov(mc).set(90);
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.HALO) > 0, "the halo was not drawn");
            check(!SkyPainter.broken(Layer.HALO), "the halo layer failed");
            shoot(mc, "halo");
            run(mc, "time set 11500");
        });
        then(30, () -> faceWorld(mc, flatToward(SkyState.sun, 0.12F)));
        then(20, () -> {
            shoot(mc, "sun-dogs");
            StarlitRuntime.forceHalo(false);
            SkyState.haloLevel = 0.0F;
            run(mc, "time set 9000");
            StarlitRuntime.forceRainbow(mc.level.getGameTime() / 20.0);
            SkyState.rainbowLevel = 1.0F;
        });
        then(30, () -> {
            float[] center = SkyPainter.rainbowCenter(SkyState.sun);
            faceWorld(mc, flatToward(center, 0.3F));
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.RAINBOW) > 0, "the rainbow was not drawn");
            check(!SkyPainter.broken(Layer.RAINBOW), "the rainbow layer failed");
            shoot(mc, "rainbow");
            run(mc, "time set 11800");
        });
        then(30, () -> {
            float[] center = SkyPainter.rainbowCenter(SkyState.sun);
            faceWorld(mc, flatToward(center, 0.35F));
        });
        then(20, () -> {
            shoot(mc, "rainbow-evening");
            StarlitRuntime.endRainbow();
            SkyState.rainbowLevel = 0.0F;
            run(mc, "time set 6000");
            run(mc, "weather rain 1000000");
        });
        within(600, () -> mc.level.getRainLevel(1.0F) > 0.95F, () -> run(mc, "weather clear 1000000"));
        within(600, () -> StarlitRuntime.rainbowPending(mc.level.getGameTime() / 20.0), () -> {
            check(StarlitRuntime.rainbowPending(mc.level.getGameTime() / 20.0), "no rainbow after the rain stopped");
            StarlitRuntime.endRainbow();
            SkyState.rainbowLevel = 0.0F;
            Opts.fov(mc).set(fov);
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(-30.0F);
        });
        then(20, () -> {
            net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
            dev.starlit.client.BirdSky.spawnToward(mc.level.getGameTime() / 20.0, mc.player.getEyePosition(),
                    new float[]{(float) look.x, (float) look.y, (float) look.z});
        });
        then(10, () -> {
            check(SkyPainter.draws(Layer.BIRDS) > 0, "no birds were drawn");
            check(!SkyPainter.broken(Layer.BIRDS), "the bird layer failed");
            shoot(mc, "birds");
            Opts.fov(mc).set(30);
        });
        then(5, () -> shoot(mc, "birds-close"));
        then(40, () -> {
            check(difference("day-vanilla", "day-atmosphere") > 1.5, "the atmosphere did not change the noon sky");
            check(average("sunset") > average("sunset-vanilla") + 1.0, "the sunset was not brighter than vanilla");
            check(pixels("rainbow", rgb -> red(rgb) > blue(rgb) + 25 && red(rgb) > green(rgb)) > 300,
                    "no red rainbow band was visible");
            check(pixels("birds-close", rgb -> red(rgb) + green(rgb) + blue(rgb) < 200) > 40, "no birds were visible");
            check(pixels("halo", rgb -> red(rgb) > 245 && green(rgb) > 245 && blue(rgb) > 245) > 200,
                    "the sun glow was missing in the halo shot");
            dev.starlit.client.BirdSky.clear();
            Opts.fov(mc).set(fov);
            run(mc, "time set 18000");
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-55.0F);
        });
        then(40, () -> log("day checks done"));
    }

    private int dayDraws;

    private static float[] flatToward(float[] direction, float lift) {
        float[] flat = dev.starlit.core.Vec.normalize(new float[]{direction[0], 0.0F, direction[2] + 1.0e-4F});
        float c = (float) Math.cos(lift);
        return new float[]{flat[0] * c, (float) Math.sin(lift), flat[2] * c};
    }

    private static void faceWorld(Minecraft mc, float[] world) {
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, world[1]))));
        float yaw = (float) Math.toDegrees(Math.atan2(-world[0], world[2]));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        log(String.format(Locale.ROOT, "facing yaw %.1f pitch %.1f", yaw, pitch));
    }

    private void planEnd(Minecraft mc, StarlitConfig config) {
        then(5, () -> run(mc, "execute in minecraft:the_end run tp @p 3000.5 120 3000.5 0 -40"));
        within(600, () -> mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.END
                && Screens.current(mc) == null, () -> {
            check(mc.level.dimension() == net.minecraft.world.level.Level.END, "did not reach the End");
            run(mc, "execute in minecraft:the_end run kill @e[type=minecraft:ender_dragon]");
            mc.player.setYRot(0.0F);
            mc.player.setXRot(-50.0F);
        });
        then(200, () -> {
            endDraws = SkyPainter.draws(Layer.END_SKY);
            check(endDraws > 0, "the End sky was not drawn");
            check(!SkyPainter.broken(Layer.END_SKY), "the End sky layer failed");
            shoot(mc, "end-sky");
            config.setEndSky(false);
        });
        then(20, () -> {
            endDraws = SkyPainter.draws(Layer.END_SKY);
            shoot(mc, "end-vanilla");
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.END_SKY) == endDraws, "the End sky was drawn with End Sky off");
            check(average("end-sky") > average("end-vanilla") + 2.0, "the End sky was not brighter than vanilla: "
                    + average("end-sky") + " vs " + average("end-vanilla"));
            config.setEndSky(true);
            run(mc, "execute in minecraft:overworld run tp @p 0.5 -40 0.5 180 -55");
        });
        within(600, () -> mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && Screens.current(mc) == null, () -> {
        });
        then(40, () -> log("end checks done"));
    }

    private int endDraws;

    private int irisDraws;

    private volatile int[] snowSpot;
    private volatile int[] meadowSpot;
    private volatile int[] endSpot;
    private volatile boolean spotsReady;

    private static final String[] SNOW = {"snowy_slopes", "grove", "jagged_peaks", "frozen_peaks", "snowy_taiga", "snowy_plains"};
    private static final String[] MEADOW = {"meadow", "cherry_grove", "sunflower_plains", "plains", "savanna"};

    private static int rank(String biome, String[] wanted) {
        for (int i = 0; i < wanted.length; i++) {
            if (biome.contains("minecraft:" + wanted[i] + "]") || biome.endsWith("minecraft:" + wanted[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int[] surface(net.minecraft.server.level.ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        return new int[]{x, y, z};
    }

    private static int[] lookout(net.minecraft.server.level.ServerLevel level, int x, int z) {
        int[] best = surface(level, x, z);
        for (int dx = -96; dx <= 96; dx += 24) {
            for (int dz = -96; dz <= 96; dz += 24) {
                int[] spot = surface(level, x + dx, z + dz);
                if (spot[1] > best[1]) {
                    best = spot;
                }
            }
        }
        return best;
    }

    private void findSpots(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.overworld();
            int bestSnow = 99;
            int bestMeadow = 99;
            int[] snow = null;
            int[] meadow = null;
            for (int r = 256; r <= 3200 && (bestSnow > 0 || bestMeadow > 0); r += 64) {
                for (int i = -r; i < r; i += 64) {
                    int[][] points = {{i, -r}, {r, i}, {-i, r}, {-r, -i}};
                    for (int[] p : points) {
                        String high = String.valueOf(level.getBiome(new net.minecraft.core.BlockPos(p[0], 140, p[1])).unwrapKey());
                        String low = String.valueOf(level.getBiome(new net.minecraft.core.BlockPos(p[0], 70, p[1])).unwrapKey());
                        int s = rank(high, SNOW);
                        if (s >= 0 && s < bestSnow) {
                            bestSnow = s;
                            snow = p;
                        }
                        int m = rank(low, MEADOW);
                        if (m >= 0 && m < bestMeadow) {
                            bestMeadow = m;
                            meadow = p;
                        }
                    }
                }
            }
            if (snow != null) {
                snowSpot = lookout(level, snow[0], snow[1]);
            }
            if (meadow != null) {
                meadowSpot = lookout(level, meadow[0], meadow[1]);
            }
            net.minecraft.server.level.ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
            outer:
            for (int r = 1200; r <= 3000; r += 150) {
                for (int a = 0; a < 16; a++) {
                    int x = (int) (Math.cos(a * Math.PI / 8) * r);
                    int z = (int) (Math.sin(a * Math.PI / 8) * r);
                    int[] spot = surface(end, x, z);
                    if (spot[1] > 40) {
                        endSpot = spot;
                        break outer;
                    }
                }
            }
            log("gallery spots: snow " + java.util.Arrays.toString(snowSpot) + " (" + bestSnow + "), meadow "
                    + java.util.Arrays.toString(meadowSpot) + " (" + bestMeadow + "), end " + java.util.Arrays.toString(endSpot));
            spotsReady = true;
        });
    }

    private void goTo(Minecraft mc, java.util.function.Supplier<int[]> where, int lift) {
        then(5, () -> {
            int[] spot = where.get();
            run(mc, String.format(Locale.ROOT, "tp @p %d.5 %d %d.5", spot[0], spot[1] + lift, spot[2]));
        });
        then(400, () -> log("at " + java.util.Arrays.toString(where.get())));
    }

    private void planGallery(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            Opts.cloudStatus(mc).set(CloudStatus.OFF);
            Opts.renderDistance(mc).set(16);
            Opts.fov(mc).set(80);
            findSpots(mc);
        });
        within(2000, () -> spotsReady, () -> check(snowSpot != null && meadowSpot != null, "no gallery spots found"));
        then(1, () -> {
        });
        planGallerySnow(mc, config);
        planGalleryMeadow(mc, config);
        then(5, () -> {
            check(endSpot != null, "no End island found");
            run(mc, "execute in minecraft:the_end run kill @e[type=minecraft:ender_dragon]");
        });
        then(5, () -> run(mc, String.format(Locale.ROOT, "execute in minecraft:the_end run tp @p %d.5 %d %d.5 0 -20",
                endSpot[0], endSpot[1] + 6, endSpot[2])));
        within(600, () -> mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.END
                && Screens.current(mc) == null, () -> {
        });
        then(400, () -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(-20.0F);
        });
        then(20, () -> shoot(mc, "gallery-end"));
        then(5, () -> mc.player.setXRot(-45.0F));
        then(20, () -> shoot(mc, "gallery-end-up"));
        then(5, () -> {
            Opts.fov(mc).set(fov);
            Opts.renderDistance(mc).set(12);
        });
    }

    private void planGallerySnow(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            run(mc, "time set 18000");
            config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
            SkyState.auroraLevel = 1.0F;
        });
        goTo(mc, () -> snowSpot, 6);
        within(600, () -> SkyState.auroraLevel > 0.95F, () -> {
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-12.0F);
        });
        then(20, () -> shoot(mc, "gallery-aurora"));
        then(5, () -> mc.player.setYRot(150.0F));
        then(20, () -> shoot(mc, "gallery-aurora-2"));
        then(5, () -> {
            config.setAuroraColors(StarlitConfig.AuroraColors.VIVID);
            mc.player.setYRot(210.0F);
            mc.player.setXRot(-18.0F);
        });
        then(20, () -> shoot(mc, "gallery-aurora-vivid"));
        then(5, () -> {
            config.setAuroraColors(StarlitConfig.AuroraColors.CLASSIC);
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
            SkyState.auroraLevel = 0.0F;
        });
        then(40, () -> {
            float[] core = MeteorSky.toWorld(Galaxy.CORE, SkyState.starAngle);
            faceWorld(mc, flatToward(core, 0.30F));
        });
        then(20, () -> shoot(mc, "gallery-milky-way"));
        then(5, () -> {
            float[] core = MeteorSky.toWorld(Galaxy.CORE, SkyState.starAngle);
            faceWorld(mc, flatToward(new float[]{-core[0], 0.0F, -core[2]}, 0.25F));
        });
        then(20, () -> shoot(mc, "gallery-stars"));
        then(5, () -> {
            run(mc, "time set 11500");
            StarlitRuntime.forceHalo(true);
            SkyState.haloLevel = 1.0F;
        });
        then(40, () -> faceWorld(mc, flatToward(SkyState.sun, 0.10F)));
        then(20, () -> shoot(mc, "gallery-sun-dogs"));
        then(5, () -> run(mc, "time set 9800"));
        then(40, () -> faceWorld(mc, flatToward(SkyState.sun, 0.30F)));
        then(20, () -> shoot(mc, "gallery-halo"));
        then(5, () -> {
            StarlitRuntime.forceHalo(false);
            SkyState.haloLevel = 0.0F;
        });
    }

    private void planGalleryMeadow(Minecraft mc, StarlitConfig config) {
        then(5, () -> run(mc, "time set 12150"));
        goTo(mc, () -> meadowSpot, 6);
        then(5, () -> faceWorld(mc, flatToward(SkyState.sun, 0.08F)));
        then(20, () -> shoot(mc, "gallery-sunset"));
        then(5, () -> run(mc, "time set 12650"));
        then(40, () -> faceWorld(mc, flatToward(SkyState.sun, 0.05F)));
        then(20, () -> shoot(mc, "gallery-dusk"));
        then(5, () -> faceWorld(mc, flatToward(new float[]{-SkyState.sun[0], 0.0F, -SkyState.sun[2]}, 0.12F)));
        then(20, () -> shoot(mc, "gallery-afterglow"));
        then(5, () -> {
            run(mc, "time set 9000");
            StarlitRuntime.forceRainbow(mc.level.getGameTime() / 20.0);
            SkyState.rainbowLevel = 1.0F;
        });
        then(40, () -> faceWorld(mc, flatToward(SkyPainter.rainbowCenter(SkyState.sun), 0.22F)));
        then(20, () -> shoot(mc, "gallery-rainbow"));
        then(5, () -> {
            StarlitRuntime.endRainbow();
            SkyState.rainbowLevel = 0.0F;
            run(mc, "time set 6000");
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(-15.0F);
        });
        then(40, () -> {
            net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
            dev.starlit.client.BirdSky.spawnToward(mc.level.getGameTime() / 20.0, mc.player.getEyePosition(),
                    new float[]{(float) look.x, (float) look.y, (float) look.z});
        });
        then(15, () -> shoot(mc, "gallery-birds"));
        then(5, () -> {
            dev.starlit.client.BirdSky.clear();
            run(mc, "time set 14500");
        });
        then(40, () -> {
            float[] head = SkyEvents.cometDirection(3L);
            SkyPainter.forceComet(head);
            float[] world = MeteorSky.toWorld(head, SkyState.starAngle);
            faceWorld(mc, flatToward(world, Math.max(0.15F, Math.min(0.5F, (float) Math.asin(world[1])))));
        });
        then(20, () -> shoot(mc, "gallery-comet"));
        then(5, () -> {
            SkyPainter.forceComet(null);
            run(mc, "time set 18000");
            mc.player.setXRot(-40.0F);
        });
        then(40, () -> {
            double now = mc.level.getGameTime() / 20.0;
            MeteorSky.forceShower(now, 600.0);
            for (int i = 0; i < 10; i++) {
                MeteorSky.spawnNow(now, i < 2);
            }
        });
        then(10, () -> shoot(mc, "gallery-meteors"));
        then(5, MeteorSky::clear);
    }

    private void planIris(Minecraft mc, boolean shaders) {
        then(100, () -> {
            log("shader pack in use: " + Compat.shaderPackInUse());
            check(Compat.shaderPackInUse() == shaders, "shader pack in use should be " + shaders);
            irisDraws = SkyPainter.draws(Layer.STARS);
        });
        then(100, () -> {
            int drawn = SkyPainter.draws(Layer.STARS) - irisDraws;
            log("star draws in 100 ticks: " + drawn);
            if (shaders) {
                check(drawn > 0, "Starlit drew no stars over a shader pack");
                check(SkyPainter.replacesVanillaStars(), "vanilla stars were not replaced under a shader pack");
                if (StarlitClient.config().shaderMode() == StarlitConfig.ShaderMode.REPLACE) {
                    log("backdrop draws: " + SkyPainter.draws(Layer.BACKDROP) + ", sun/moon draws: " + SkyPainter.draws(Layer.SUN_MOON));
                    check(!SkyPainter.broken(Layer.BACKDROP), "the backdrop layer failed");
                    check(!SkyPainter.broken(Layer.SUN_MOON), "the sun/moon layer failed");
                    check(SkyPainter.draws(Layer.BACKDROP) > 0, "Starlit did not replace the shader pack's sky");
                }
            } else {
                check(drawn > 0, "Starlit drew no stars with Iris installed and shaders off");
            }
            shoot(mc, shaders ? "iris-shaders" : "iris-no-shaders");
            if (shaders) {
                run(mc, "time set 12150");
            }
        });
        if (shaders) {
            then(30, () -> faceWorld(mc, flatToward(SkyState.sun, 0.12F)));
            then(20, () -> {
                shoot(mc, "iris-shaders-sunset");
                run(mc, "time set 6000");
                mc.player.setYRot(-90.0F);
                mc.player.setXRot(-35.0F);
            });
            then(30, () -> shoot(mc, "iris-shaders-day"));
        }
        then(20, () -> {
            if (shaders) {
                check(pixels("iris-shaders", rgb -> red(rgb) + green(rgb) + blue(rgb) > 450) > 2000,
                        "Starlit's stars and moon were not visible over the shader pack");
                check(pixels("iris-shaders-sunset", rgb -> red(rgb) > blue(rgb) + 25) > 60000,
                        "the sunset under the shader pack was not warm");
                check(pixels("iris-shaders-day", rgb -> blue(rgb) > red(rgb) + 20 && blue(rgb) > green(rgb)) > 250000,
                        "the day sky under the shader pack was not blue");
            } else {
                check(pixels("iris-no-shaders", rgb -> red(rgb) + green(rgb) + blue(rgb) > 450) > 1000,
                        "Starlit's stars and moon were not visible with Iris installed and shaders off");
            }
        });
    }

    // Distant Horizons under an Iris shader pack: the LOD terrain past the render distance must hide Starlit's sky.
    private void planDh(Minecraft mc, StarlitConfig config) {
        then(20, () -> {
            run(mc, "tp @p 0.5 -30 0.5 0 8");
            log("Far depth texture: " + Compat.farDepthTexture());
        });
        then(Integer.getInteger("starlit.dhwait", 900), () -> {
            log("Far depth texture: " + Compat.farDepthTexture() + ", masks applied: " + dev.starlit.client.render.DhMask.applied);
            check(Boolean.getBoolean("starlit.nodhmask") || dev.starlit.client.render.DhMask.applied > 0, "the Distant Horizons mask never ran");
            config.setShaderMode(StarlitConfig.ShaderMode.OVERLAY);
        });
        then(40, () -> {
            shoot(mc, "dh-overlay");
            config.setShaderMode(StarlitConfig.ShaderMode.REPLACE);
        });
        then(40, () -> shoot(mc, "dh-replace"));
    }

    // A modded dimension with its own sky renderer: Starlit's fallback must draw over it, and stop when toggled off.
    private void planDimension(Minecraft mc, StarlitConfig config) {
        String dim = System.getProperty("starlit.dim", "twilightforest:twilight_forest");
        then(20, () -> run(mc, "execute in " + dim + " run tp @p 0.5 200 0.5 180 -55"));
        int[] start = new int[2];
        then(Integer.getInteger("starlit.dimwait", 300), () -> {
            String setup = System.getProperty("starlit.dimcmd", "");
            if (!setup.isEmpty()) run(mc, setup);
            run(mc, "time set 18000");
            log("dimension: " + mc.level.dimension() + ", fallback draws: " + SkyPainter.fallbacks);
            start[0] = SkyPainter.fallbacks;
            start[1] = SkyPainter.overlays;
        });
        then(60, () -> {
            log("fallback draws: " + SkyPainter.fallbacks + ", star draws: " + SkyPainter.draws(Layer.STARS));
            check(!mc.level.dimension().toString().contains("minecraft:overworld"), "the player never left the overworld");
            check(SkyPainter.fallbacks > 0, "the modded-dimension fallback never drew");
            // Every frame must get the fallback; skipping some shows the dimension's own sky through (flicker).
            int drawn = SkyPainter.fallbacks - start[0];
            int frames = SkyPainter.overlays - start[1];
            log("fallback frames: " + drawn + "/" + frames);
            check(frames > 0 && drawn >= frames - 2, "the fallback skipped frames (" + drawn + "/" + frames + "), the sky flickers");
            check(SkyPainter.draws(Layer.STARS) > 0, "no Starlit stars were drawn in the modded dimension");
            shoot(mc, "dim-fallback");
            config.setModdedSkies(false);
        });
        int[] before = new int[1];
        then(30, () -> before[0] = SkyPainter.fallbacks);
        then(30, () -> {
            check(SkyPainter.fallbacks == before[0], "the fallback kept drawing with modded skies off");
            shoot(mc, "dim-native");
            config.setModdedSkies(true);
        });
    }

    private void planLook(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
            SkyState.auroraLevel = 1.0F;
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-25.0F);
        });
        within(600, () -> SkyState.auroraLevel > 0.95F, () -> {
        });
        for (StarlitConfig.AuroraColors colors : StarlitConfig.AuroraColors.values()) {
            then(5, () -> config.setAuroraColors(colors));
            then(15, () -> shoot(mc, "look-aurora-" + colors.name().toLowerCase(Locale.ROOT)));
        }
        then(5, () -> {
            config.setAuroraColors(StarlitConfig.AuroraColors.CLASSIC);
            mc.player.setXRot(-70.0F);
        });
        then(15, () -> shoot(mc, "look-aurora-up"));
        then(5, () -> {
            float[] head = SkyEvents.cometDirection(3L);
            SkyPainter.forceComet(head);
            float[] tail = dev.starlit.core.Vec.tangentAway(head, new float[]{0.0F, 1.0F, 0.0F});
            face(mc, dev.starlit.core.Vec.normalize(dev.starlit.core.Vec.axpy(head, 0.15F, tail)));
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
        });
        then(20, () -> shoot(mc, "look-comet"));
        then(5, () -> {
            SkyPainter.forceComet(null);
            Opts.fov(mc).set(100);
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-80.0F);
            double now = mc.level.getGameTime() / 20.0;
            for (int i = 0; i < 10; i++) {
                MeteorSky.spawnNow(now, i < 3);
            }
        });
        then(10, () -> shoot(mc, "look-meteors"));
        then(5, () -> Opts.fov(mc).set(fov));
        planEnd(mc, config);
    }

    private void planDeepSky(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            Opts.fov(mc).set(30);
            face(mc, DeepSky.GALAXIES.get(0).direction());
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.GALAXIES) > 0, "no galaxies were drawn");
            shoot(mc, "galaxy");
            face(mc, DeepSky.GALAXIES.get(1).direction());
        });
        then(20, () -> {
            shoot(mc, "galaxy-2");
            face(mc, SkyEvents.planetDirection(1, SkyState.night));
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.PLANETS) > 0, "no planets were drawn");
            shoot(mc, "planet");
            Opts.fov(mc).set(fov);
        });
    }

    private void planConstellations(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            config.setConstellationLines(true);
            Opts.fov(mc).set(50);
            face(mc, Constellations.SHAPES.get(0).center());
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.LINES) > 0, "constellation lines were not drawn");
            shoot(mc, "constellation-creeper");
            face(mc, Constellations.SHAPES.get(1).center());
        });
        then(20, () -> {
            shoot(mc, "constellation-sword");
            face(mc, Constellations.SHAPES.get(4).center());
        });
        then(20, () -> {
            shoot(mc, "constellation-ghast");
            config.setConstellationLines(false);
            Opts.fov(mc).set(fov);
        });
    }

    private void planComet(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            float[] head = SkyEvents.cometDirection(3L);
            SkyPainter.forceComet(head);
            Opts.fov(mc).set(60);
            float[] tail = dev.starlit.core.Vec.tangentAway(head, new float[]{0.0F, 1.0F, 0.0F});
            face(mc, dev.starlit.core.Vec.normalize(dev.starlit.core.Vec.axpy(head, 0.12F, tail)));
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.COMET) > 0, "the comet was not drawn");
            shoot(mc, "comet");
            SkyPainter.forceComet(null);
            Opts.fov(mc).set(fov);
        });
    }

    private void planMeteors(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            Opts.fov(mc).set(100);
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-80.0F);
            double now = mc.level.getGameTime() / 20.0;
            MeteorSky.forceShower(now, 600.0);
            for (int i = 0; i < 6; i++) {
                MeteorSky.spawnNow(now, false);
            }
            MeteorSky.spawnNow(now, true);
        });
        then(1, () -> showerStart = MeteorSky.spawned());
        within(400, () -> MeteorSky.spawned() - showerStart >= 12, () -> check(MeteorSky.spawned() - showerStart >= 12,
                "the forced shower made only " + (MeteorSky.spawned() - showerStart) + " meteors"));
        then(1, () -> {
            double now = mc.level.getGameTime() / 20.0;
            for (int i = 0; i < 8; i++) {
                MeteorSky.spawnNow(now, false);
            }
        });
        then(8, () -> {
            check(SkyPainter.draws(Layer.METEORS) > 0, "no meteors were drawn");
            shoot(mc, "meteor-shower");
        });
        then(30, () -> {
            double now = mc.level.getGameTime() / 20.0;
            for (int i = 0; i < 4; i++) {
                MeteorSky.spawnNow(now, true);
            }
        });
        then(14, () -> shoot(mc, "fireballs"));
        then(5, () -> {
            MeteorSky.clear();
            Opts.fov(mc).set(fov);
        });
    }

    private void planAurora(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-18.0F);
        });
        within(600, () -> SkyState.auroraLevel > 0.95F, () -> {
            check(SkyState.auroraLevel > 0.9F, "the aurora did not fade in: " + SkyState.auroraLevel);
            check(SkyPainter.draws(Layer.AURORA) > 0, "the aurora was not drawn");
        });
        then(10, () -> shoot(mc, "aurora-classic"));
        for (StarlitConfig.AuroraColors colors : new StarlitConfig.AuroraColors[]{
                StarlitConfig.AuroraColors.VIVID, StarlitConfig.AuroraColors.CRIMSON, StarlitConfig.AuroraColors.ICE}) {
            then(5, () -> config.setAuroraColors(colors));
            then(15, () -> shoot(mc, "aurora-" + colors.name().toLowerCase(Locale.ROOT)));
        }
        then(5, () -> {
            check(average("aurora-classic") > average("vanilla-no-clouds") + 2.0, "the aurora screenshot was not brighter than vanilla");
            config.setAuroraColors(StarlitConfig.AuroraColors.CLASSIC);
            config.setAuroraMode(StarlitConfig.AuroraMode.COLD);
        });
        within(1200, () -> SkyState.auroraLevel < 0.05F || SkyEvents.geomagneticStorm(SkyState.night), () -> {
            if (!SkyEvents.geomagneticStorm(SkyState.night)) {
                check(SkyState.auroraLevel < 0.06F, "the aurora stayed in a warm biome: " + SkyState.auroraLevel);
            }
            check(StarlitRuntime.coldness(mc) < 0.01F, "plains counted as cold: " + StarlitRuntime.coldness(mc));
            run(mc, Terrain.coldCommand(mc));
        });
        within(400, () -> StarlitRuntime.coldness(mc) > 0.9F, () ->
                check(StarlitRuntime.coldness(mc) > 0.9F, "snowy plains did not count as cold: " + StarlitRuntime.coldness(mc)));
        within(1200, () -> SkyState.auroraLevel > 0.9F, () -> {
            check(SkyState.auroraLevel > 0.85F, "the aurora did not appear in a cold biome: " + SkyState.auroraLevel);
            shoot(mc, "aurora-cold-biome");
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
        });
    }

    private void planStarCount(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-55.0F);
            config.setStarCount(StarlitConfig.MAX_STARS);
            config.setMilkyWay(0);
            config.setNebulae(false);
            config.setGalaxies(false);
        });
        then(20, () -> shoot(mc, "stars-12000"));
        then(5, () -> config.setStarCount(StarlitConfig.MIN_STARS));
        then(20, () -> {
            shoot(mc, "stars-500");
            config.setStarColors(false);
            config.setStarCount(5000);
        });
        then(20, () -> {
            shoot(mc, "stars-white");
            check(average("stars-12000") > average("stars-500") + 1.0,
                    "12,000 stars were not brighter than 500: " + average("stars-12000") + " vs " + average("stars-500"));
            config.setStarCount(0);
        });
        then(10, () -> {
            check(!SkyPainter.replacesVanillaStars(), "a star count of Vanilla still hid vanilla stars");
            config.resetToDefaults();
            config.setShootingStars(0);
            config.setMeteorShowers(0);
            config.setComets(0);
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
        });
        planPerformance(mc, config);
    }

    private void planPerformance(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            limit = Opts.framerateLimit(mc).get();
            vsync = Opts.enableVsync(mc).get();
            Fps.unthrottle(mc);
            Opts.framerateLimit(mc).set(260);
            Opts.enableVsync(mc).set(false);
            mc.player.setYRot(180.0F);
            mc.player.setXRot(-30.0F);
            config.setEnabled(false);
        });
        then(40, this::measure);
        then(300, () -> {
            report("Starlit off");
            config.setEnabled(true);
        });
        then(40, this::measure);
        then(300, () -> {
            report("Starlit on, no aurora");
            config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
            SkyState.auroraLevel = 1.0F;
            config.setStarCount(StarlitConfig.MAX_STARS);
        });
        then(40, this::measure);
        then(300, () -> {
            report("Starlit on, aurora and 12,000 stars");
            config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
            config.setStarCount(5000);
            run(mc, "time set 9000");
            mc.player.setYRot(-90.0F);
            config.setEnabled(false);
        });
        then(40, this::measure);
        then(300, () -> {
            report("Day, Starlit off");
            config.setEnabled(true);
            StarlitRuntime.forceHalo(true);
            SkyState.haloLevel = 1.0F;
            StarlitRuntime.forceRainbow(mc.level.getGameTime() / 20.0);
            SkyState.rainbowLevel = 1.0F;
        });
        then(40, this::measure);
        then(300, () -> {
            report("Day, Starlit on with halo and rainbow");
            StarlitRuntime.forceHalo(false);
            SkyState.haloLevel = 0.0F;
            StarlitRuntime.endRainbow();
            SkyState.rainbowLevel = 0.0F;
            run(mc, "time set 18000");
            mc.player.setYRot(180.0F);
            Opts.framerateLimit(mc).set(limit);
            Opts.enableVsync(mc).set(vsync);
            Fps.restore(mc);
        });
    }

    private int limit;
    private boolean vsync;

    private void planFades(Minecraft mc, StarlitConfig config) {
        then(5, () -> {
            config.setMoonGlow(true);
            Opts.fov(mc).set(fov);
            mc.player.setYRot(0.0F);
            mc.player.setXRot(-89.0F);
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.MOON_GLOW) > 0, "the moon glow was not drawn at full moon");
            shoot(mc, "moon-glow");
            run(mc, "time set 6000");
        });
        then(40, () -> {
            check(SkyState.darkness < 0.01F, "the sky stayed dark at noon: " + SkyState.darkness);
            int before = SkyPainter.draws(Layer.STARS);
            mc.player.setXRot(-55.0F);
            run(mc, "time set 6001");
            starsAtNoon = before;
        });
        then(20, () -> {
            check(SkyPainter.draws(Layer.STARS) == starsAtNoon, "stars were drawn at noon");
            shoot(mc, "day");
            run(mc, "time set 18000");
            run(mc, "weather rain 1000000");
        });
        within(600, () -> SkyState.darkness < 0.05F, () -> {
            check(SkyState.darkness < 0.05F, "rain did not hide the night sky: " + SkyState.darkness);
            shoot(mc, "rain");
            run(mc, "weather clear 1000000");
        });
        within(600, () -> SkyState.darkness > 0.9F, () -> check(SkyState.darkness > 0.9F,
                "the stars did not come back after rain: " + SkyState.darkness));
        then(5, () -> run(mc, "time set 13000"));
        then(30, () -> {
            float dusk = SkyState.darkness;
            check(dusk > 0.3F && dusk < 1.01F, "dusk darkness out of range: " + dusk);
            shoot(mc, "dusk");
            run(mc, "time set 18000");
        });
        then(10, () -> {
            StarlitRuntime.resetAnnouncements();
            config.setMeteorShowers(100);
            config.setComets(100);
            config.setAuroraMode(StarlitConfig.AuroraMode.COLD);
            long night = findNight(true);
            run(mc, "time set " + (night * SkyEvents.DAY + 12600));
        });
        within(200, () -> StarlitRuntime.lastAnnouncement() != null, () -> {
            Component said = StarlitRuntime.lastAnnouncement();
            check(said != null, "no announcement at dusk on a meteor shower night");
            log("announcement: " + said.getString());
            check(said.getString().toLowerCase(Locale.ROOT).contains("meteor"), "the announcement did not mention the shower");
        });
        then(5, () -> {
            config.setAnnounce(false);
            StarlitRuntime.resetAnnouncements();
            run(mc, "time set " + (SkyState.night * SkyEvents.DAY + 24000 + 12600));
        });
        then(60, () -> {
            check(StarlitRuntime.lastAnnouncement() == null, "an announcement came with announcements off");
            config.resetToDefaults();
            run(mc, "time set 18000");
        });
    }

    private int starsAtNoon;
    private int showerStart;

    private long findNight(boolean shower) {
        for (long n = 1; n < 400; n++) {
            if (SkyEvents.meteorShower(n, 100) == shower) {
                return n;
            }
        }
        throw new AssertionError("no night with a meteor shower in 400 nights at 100%");
    }

    private void planSettings(Minecraft mc, StarlitConfig config) {
        then(20, () -> Screens.open(mc, new StarlitSettingsScreen(null, mc.options)));
        then(10, () -> {
            shoot(mc, "settings");
            clickRightEdge(mc, slider(Screens.current(mc), "Twinkle"));
        });
        then(3, () -> {
            check(config.twinkle() == 100, "the Twinkle slider did not reach 100: " + config.twinkle());
            click(mc, find(Screens.current(mc), "starlit.options.star_colors"));
        });
        then(3, () -> {
            check(!config.starColors(), "the Star Colors button did not switch them off");
        });
        revealing(mc, "starlit.options.planets");
        then(3, () -> click(mc, find(Screens.current(mc), "starlit.options.planets")));
        then(3, () -> {
            check(!config.planets(), "the Planets button did not switch them off");
        });
        revealing(mc, "starlit.options.aurora");
        then(3, () -> click(mc, find(Screens.current(mc), "starlit.options.aurora")));
        then(3, () -> {
            check(config.auroraMode() == StarlitConfig.AuroraMode.ALWAYS, "the Aurora button did not cycle: " + config.auroraMode());
            click(mc, find(Screens.current(mc), "starlit.options.aurora_colors"));
        });
        then(3, () -> {
            check(config.auroraColors() == StarlitConfig.AuroraColors.VIVID, "the Aurora Colors button did not cycle");
            scroll(Screens.current(mc), true);
        });
        then(3, () -> {
            shoot(mc, "settings-bottom");
            click(mc, find(Screens.current(mc), "starlit.options.reset"));
        });
        then(5, () -> {
            check(config.planets(), "reset did not switch Planets back on");
            check(config.starColors(), "reset did not switch Star Colors back on");
            check(config.auroraMode() == StarlitConfig.AuroraMode.COLD, "reset did not restore the aurora mode");
            check(config.twinkle() == 50, "reset did not restore Twinkle");
            AbstractSliderButton stars = slider(Screens.current(mc), "Stars");
            reveal(Screens.current(mc), stars);
        });
        then(3, () -> reveal(Screens.current(mc), slider(Screens.current(mc), "Stars")));
        then(3, () -> clickRightEdge(mc, slider(Screens.current(mc), "Stars")));
        then(3, () -> {
            check(config.starCount() == StarlitConfig.MAX_STARS, "the Stars slider did not reach the maximum: " + config.starCount());
            Screens.open(mc, null);
        });
        then(5, () -> {
            String saved = read(StarlitClient.configPath());
            check(saved.contains("\"starCount\": 12000"), "settings were not saved on close: " + saved);
        });
        then(10, () -> log("settings checks done"));
    }

    private void revealing(Minecraft mc, String key) {
        for (int i = 0; i < 8; i++) {
            then(2, () -> reveal(Screens.current(mc), find(Screens.current(mc), key)));
        }
    }

    private static void reveal(Screen screen, AbstractWidget widget) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list && !Lists.shows(list, widget)) {
                Lists.reveal(list, widget);
            }
        }
    }

    private void measure() {
        frameStart = System.nanoTime();
        frameCount = frames;
    }

    private void report(String label) {
        double seconds = (System.nanoTime() - frameStart) / 1.0e9;
        int count = frames - frameCount;
        log(String.format(Locale.ROOT, "%s: %d frames in %.1f s = %.0f fps", label, count, seconds, count / seconds));
    }

    private static void face(Minecraft mc, float[] starDirection) {
        float[] world = MeteorSky.toWorld(starDirection, SkyState.starAngle);
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, world[1]))));
        float yaw = (float) Math.toDegrees(Math.atan2(-world[0], world[2]));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        log(String.format(Locale.ROOT, "facing yaw %.1f pitch %.1f", yaw, pitch));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> Cmds.run(server, command));
    }

    private void shoot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        File dir = new File(mc.gameDirectory, "screenshots");
        Screenshot.grab(mc.gameDirectory, Screens.renderTarget(mc), message -> {
        });
        pending.put(name, dir);
    }

    private final Map<String, File> pending = new HashMap<>();

    private double average(String name) {
        Double known = brightness.get(name);
        if (known != null) {
            return known;
        }
        File file = newestFor(name);
        check(file != null, "no screenshot file for " + name);
        try {
            BufferedImage image = ImageIO.read(file);
            long sum = 0;
            long n = 0;
            int h = image.getHeight() * 3 / 4;
            for (int y = 0; y < h; y += 4) {
                for (int x = 0; x < image.getWidth(); x += 4) {
                    int rgb = image.getRGB(x, y);
                    sum += ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255);
                    n += 3;
                }
            }
            double value = sum / (double) n;
            brightness.put(name, value);
            log(String.format(Locale.ROOT, "brightness %s: %.2f", name, value));
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage image(String name) {
        File file = newestFor(name);
        check(file != null, "no screenshot file for " + name);
        try {
            BufferedImage image = ImageIO.read(file);
            check(image != null, "the screenshot for " + name + " could not be read: " + file.getName());
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int pixels(String name, java.util.function.IntPredicate test) {
        BufferedImage image = image(name);
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                if (test.test(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        log("pixels " + name + ": " + count);
        return count;
    }

    private double difference(String a, String b) {
        BufferedImage first = image(a);
        BufferedImage second = image(b);
        long sum = 0;
        long n = 0;
        for (int y = 0; y < Math.min(first.getHeight(), second.getHeight()); y += 4) {
            for (int x = 0; x < Math.min(first.getWidth(), second.getWidth()); x += 4) {
                int p = first.getRGB(x, y);
                int q = second.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    sum += Math.abs(((p >> shift) & 255) - ((q >> shift) & 255));
                    n++;
                }
            }
        }
        double value = sum / (double) n;
        log(String.format(Locale.ROOT, "difference %s vs %s: %.2f", a, b, value));
        return value;
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 255;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 255;
    }

    private static int blue(int rgb) {
        return rgb & 255;
    }

    private File newestFor(String name) {
        int position = shots.lastIndexOf(name);
        File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, file) -> file.endsWith(".png") && !existingShots.contains(file)
                && !file.startsWith("sl-"));
        if (files == null || files.length <= position) {
            return null;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparing(File::getName));
        return fresh.get(position);
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name)
                && !name.startsWith("sl-"));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "sl-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void click(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), Ui.x(widget) + widget.getWidth() / 2.0, Ui.y(widget) + widget.getHeight() / 2.0);
    }

    private static void clickRightEdge(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), Ui.x(widget) + widget.getWidth() - 2.0, Ui.y(widget) + widget.getHeight() / 2.0);
    }

    private static void scroll(Screen screen, boolean bottom) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                list.setScrollAmount(bottom ? Double.MAX_VALUE : 0.0);
            }
        }
    }

    private static void visible(Screen screen, AbstractWidget widget) {
        Ui.reveal(screen, widget);
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                check(Lists.shows(list, widget), widget.getMessage().getString() + " is scrolled out of view");
            }
        }
    }

    static AbstractWidget find(GuiEventListener node, String key) {
        if (node instanceof AbstractWidget widget && labelled(widget, key)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Screen) {
            throw new AssertionError("no widget " + key + " on " + node);
        }
        return null;
    }

    private static boolean labelled(AbstractWidget widget, String key) {
        String own = Labels.key(widget.getMessage());
        if (own == null) {
            return false;
        }
        if (own.equals(key)) {
            return true;
        }
        Object[] args = Labels.args(widget.getMessage());
        return args.length > 0
                && args[0] instanceof Component name
                && key.equals(Labels.key(name));
    }

    private static AbstractSliderButton slider(GuiEventListener node, String caption) {
        AbstractSliderButton found = findSlider(node, caption);
        check(found != null, "no slider " + caption);
        return found;
    }

    private static AbstractSliderButton findSlider(GuiEventListener node, String caption) {
        if (node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption + ":")) {
            return slider;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractSliderButton found = findSlider(child, caption);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[starlit-selftest] " + message);
    }
}
