package dev.starlit.showcase;

import dev.starlit.client.BirdSky;
import dev.starlit.client.MeteorSky;
import dev.starlit.client.SkyState;
import dev.starlit.client.StarlitClient;
import dev.starlit.client.StarlitRuntime;
import dev.starlit.client.gui.StarlitSettingsScreen;
import dev.starlit.client.render.SkyPainter;
import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Constellations;
import dev.starlit.core.Galaxy;
import dev.starlit.core.Meteor;
import dev.starlit.core.SkyEvents;
import dev.starlit.core.Vec;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class Showcase {
    private static final String WORLD = "starlit-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static final int GLIDE = 24;
    private static final String[] ARROW = {
            "B",
            "BB",
            "BWB",
            "BWWB",
            "BWWWB",
            "BWWWWB",
            "BWWWWWB",
            "BWWWWWWB",
            "BWWWWWWWB",
            "BWWWWWWWWB",
            "BWWWWWWWWWB",
            "BWWWWWWBBBBB",
            "BWWWBWWB",
            "BWWB BWWB",
            "BWB  BWWB",
            "BB    BWWB",
            "B     BWWB",
            "       BWWB",
            "        BB"};
    private static final String[] SNOW = {"snowy_slopes", "grove", "jagged_peaks", "frozen_peaks", "snowy_taiga", "snowy_plains"};
    private static final String[] MEADOW = {"meadow", "cherry_grove", "sunflower_plains", "plains", "savanna"};
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private volatile int[] snowSpot;
    private volatile int[] meadowSpot;
    private volatile int[] endSpot;
    private volatile boolean spotsReady;
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;
    private boolean cursorShown;
    private double cursorX;
    private double cursorY;
    private int fov;
    private CloudStatus clouds;
    private int renderDistance;
    private int guiScale;

    private interface Action {
        boolean run(int frame);
    }

    private record Stop(int frame, Supplier<double[]> where) {
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static void start() {
        String ffmpeg = System.getProperty("starlit.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("starlit.seed", "20261008"));
        instance = new Showcase(Path.of(System.getProperty("starlit.showcase")), ffmpeg, seed);
    }

    public static boolean hideHud() {
        return instance != null && instance.started;
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    public static void drawCursor(GuiGraphicsExtractor graphics) {
        if (instance == null || !instance.started || !instance.cursorShown) {
            return;
        }
        float cell = 2.0F / (float) Minecraft.getInstance().getWindow().getGuiScale();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) instance.cursorX, (float) instance.cursorY);
        graphics.pose().scale(cell, cell);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            int x = 0;
            while (x < line.length()) {
                char c = line.charAt(x);
                int end = x;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                if (c != ' ') {
                    graphics.fill(x, row, end, row + 1, c == 'B' ? 0xFF000000 : 0xFFFFFFFF);
                }
                x = end;
            }
        }
        graphics.pose().popMatrix();
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    if (cursorShown && Screens.current(mc) != null) {
                        double scale = mc.getWindow().getGuiScale();
                        Ui.moveMouse(mc, cursorX * scale, cursorY * scale);
                    }
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        Clock.release();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "starlit-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        StarlitClient.config().resetToDefaults();
        StarlitClient.saveConfig();
        mc.options.fov().set(fov);
        mc.options.cloudStatus().set(clouds);
        mc.options.renderDistance().set(renderDistance);
        mc.options.guiScale().set(guiScale);
        mc.options.save();
        log("showcase done");
        mc.stop();
    }

    private void setUpOptions(Minecraft mc) {
        fov = mc.options.fov().get();
        clouds = mc.options.cloudStatus().get();
        renderDistance = mc.options.renderDistance().get();
        guiScale = mc.options.guiScale().get();
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(16);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.fov().set(80);
        mc.options.bobView().set(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.guiScale().set(3);
        mc.resizeGui();
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, IntConsumer script) {
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < frames) {
                script.accept(f);
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    private void shot(String name) {
        pendingStill = out.resolve("stills").resolve(name + ".png");
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void face(Minecraft mc, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
        player.yBodyRot = (float) yaw;
        player.yBodyRotO = (float) yaw;
    }

    private static void place(Minecraft mc, Vec3 feet) {
        LocalPlayer player = mc.player;
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(feet.x, feet.y, feet.z);
        player.xo = feet.x;
        player.yo = feet.y;
        player.zo = feet.z;
        player.xOld = feet.x;
        player.yOld = feet.y;
        player.zOld = feet.z;
    }

    private static double yawOf(float[] world) {
        return Math.toDegrees(Math.atan2(-world[0], world[2]));
    }

    private static double pitchOf(float[] world) {
        return -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, world[1]))));
    }

    private static float[] toStar(float[] world, float starAngle) {
        Matrix3f rotation = new Matrix3f().rotateY((float) Math.toRadians(-90.0)).rotateX(starAngle).transpose();
        Vector3f v = rotation.transform(new Vector3f(world[0], world[1], world[2]));
        return new float[]{v.x, v.y, v.z};
    }

    private static float[] look(double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new float[]{(float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p))};
    }

    private static double now(Minecraft mc) {
        return mc.level.getGameTime() / 20.0;
    }

    private static void streak(Minecraft mc, Random random, double yaw, double pitch, boolean fireball) {
        float[] forward = look(yaw, pitch);
        float[] right = Vec.normalize(Vec.cross(forward, Vec.of(0.0F, 1.0F, 0.0F)));
        float[] up = Vec.cross(right, forward);
        float a = (float) (random.nextDouble() * 0.9 - 0.45);
        float b = (float) (random.nextDouble() * 0.35 + 0.05);
        float[] start = Vec.normalize(Vec.add(forward, Vec.add(Vec.scale(right, a), Vec.scale(up, b))));
        float side = a > 0 ? -1.0F : 1.0F;
        float[] travel = Vec.normalize(Vec.add(Vec.scale(right, side * (0.5F + random.nextFloat() * 0.5F)), Vec.scale(up, -1.0F)));
        float[][] colors = {{0.4F, 1.0F, 0.55F}, {1.0F, 0.55F, 0.25F}, {1.0F, 0.4F, 0.8F}};
        Meteor meteor = fireball
                ? new Meteor(start, travel, 0.2F, 0.3F, 1.6F, 2.2F, 2.2F, colors[random.nextInt(colors.length)], true, now(mc))
                : new Meteor(start, travel, 0.32F + random.nextFloat() * 0.2F, 0.12F + random.nextFloat() * 0.08F,
                0.55F, 0.75F + random.nextFloat() * 0.3F, 1.0F + random.nextFloat() * 0.3F,
                new float[]{0.85F, 0.9F, 1.0F}, false, now(mc));
        MeteorSky.meteors().add(meteor);
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("starlit.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private static int rank(String biome, String[] wanted) {
        for (int i = 0; i < wanted.length; i++) {
            if (biome.contains("minecraft:" + wanted[i] + "]") || biome.endsWith("minecraft:" + wanted[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int[] surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new int[]{x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z};
    }

    private static int[] lookout(ServerLevel level, int x, int z) {
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
            ServerLevel level = server.overworld();
            int bestSnow = 99;
            int bestMeadow = 99;
            int[] snow = null;
            int[] meadow = null;
            for (int r = 256; r <= 3200 && (bestSnow > 0 || bestMeadow > 0); r += 64) {
                for (int i = -r; i < r; i += 64) {
                    int[][] points = {{i, -r}, {r, i}, {-i, r}, {-r, -i}};
                    for (int[] p : points) {
                        String high = String.valueOf(level.getBiome(new BlockPos(p[0], 140, p[1])).unwrapKey());
                        String low = String.valueOf(level.getBiome(new BlockPos(p[0], 70, p[1])).unwrapKey());
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
            ServerLevel end = server.getLevel(Level.END);
            outer:
            for (int r = 1200; r <= 3000; r += 150) {
                for (int a = 0; a < 16; a++) {
                    int[] spot = surface(end, (int) (Math.cos(a * Math.PI / 8) * r), (int) (Math.sin(a * Math.PI / 8) * r));
                    if (spot[1] > 40) {
                        endSpot = spot;
                        break outer;
                    }
                }
            }
            log("spots: snow " + java.util.Arrays.toString(snowSpot) + ", meadow " + java.util.Arrays.toString(meadowSpot)
                    + ", end " + java.util.Arrays.toString(endSpot));
            spotsReady = true;
        });
    }

    private static Vec3 above(int[] spot, double lift) {
        return new Vec3(spot[0] + 0.5, spot[1] + lift, spot[2] + 0.5);
    }

    private void scene(Minecraft mc, StarlitConfig config, Supplier<Vec3> feet, int time, Runnable setup, Runnable hold) {
        once(() -> {
            Screens.open(mc, null);
            cursorShown = false;
            config.resetToDefaults();
            config.setComets(0);
            StarlitRuntime.forceHalo(false);
            StarlitRuntime.endRainbow();
            SkyPainter.forceComet(null);
            MeteorSky.clear();
            BirdSky.clear();
            SkyState.haloLevel = 0.0F;
            SkyState.rainbowLevel = 0.0F;
            cmd(mc, "time set %d", time);
            Vec3 p = feet.get();
            cmd(mc, "tp @p %.3f %.3f %.3f", p.x, p.y, p.z);
        });
        holdFor(30, () -> place(mc, feet.get()));
        once(setup);
        settle(mc, () -> {
            place(mc, feet.get());
            hold.run();
        });
    }

    private void plan(Minecraft mc) {
        StarlitConfig config = StarlitClient.config();

        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule advance_weather false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "weather clear 1000000");
            run(mc, "time set 18000");
            run(mc, "gamemode spectator @p");
            findSpots(mc);
        });
        until(() -> spotsReady);
        once(() -> {
            if (snowSpot == null || meadowSpot == null || endSpot == null) {
                throw new IllegalStateException("missing a filming spot");
            }
        });
        filmSnow(mc, config);
        filmMeadow(mc, config);
        filmEnd(mc, config);
    }

    private void filmSnow(Minecraft mc, StarlitConfig config) {
        Supplier<Vec3> peak = () -> above(snowSpot, 6);

        if (wanted("intro")) {
            scene(mc, config, peak, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
                SkyState.auroraLevel = 1.0F;
            }, () -> face(mc, 165, -12));
            record("01-intro", 330, f -> {
                place(mc, peak.get());
                double t = smooth(f / 330.0) * 0.8 + f / 330.0 * 0.2;
                face(mc, lerp(165, 195, t), lerp(-12, -16, t));
                if (f == 200) {
                    shot("intro");
                }
            });
        }

        if (wanted("stars")) {
            double[] dir = new double[2];
            scene(mc, config, peak, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
                SkyState.auroraLevel = 0.0F;
            }, () -> {
                float[] core = MeteorSky.toWorld(Galaxy.CORE, SkyState.starAngle);
                dir[0] = yawOf(core);
                dir[1] = Math.max(-55, Math.min(-18, pitchOf(core)));
                face(mc, dir[0] - 10, dir[1] + 8);
            });
            record("02-stars", 330, f -> {
                place(mc, peak.get());
                double t = smooth(f / 330.0) * 0.8 + f / 330.0 * 0.2;
                face(mc, dir[0] - 10 + 20 * t, dir[1] + 8 - 10 * t);
                if (f == 220) {
                    shot("milky-way");
                }
            });
        }

        if (wanted("constellations")) {
            double[] dir = new double[2];
            scene(mc, config, peak, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.OFF);
                SkyState.auroraLevel = 0.0F;
                config.setShootingStars(0);
            }, () -> {
                float[] best = null;
                for (Constellations.Shape shape : Constellations.SHAPES) {
                    float[] world = MeteorSky.toWorld(shape.center(), SkyState.starAngle);
                    double score = Math.abs(world[1] - 0.5);
                    if (best == null || score < Math.abs(best[1] - 0.5)) {
                        best = world;
                    }
                }
                dir[0] = yawOf(best);
                dir[1] = pitchOf(best);
                face(mc, dir[0] + 4, dir[1]);
            });
            record("04-constellations", 330, f -> {
                place(mc, peak.get());
                face(mc, dir[0] + 4 - 8 * smooth(f / 330.0), dir[1]);
                if (f == 120) {
                    config.setConstellationLines(true);
                }
                if (f == 100) {
                    shot("constellation");
                } else if (f == 280) {
                    shot("constellation-lines");
                }
            });
        }

        if (wanted("aurora")) {
            scene(mc, config, peak, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
                SkyState.auroraLevel = 1.0F;
            }, () -> face(mc, 215, -8));
            record("07-aurora", 330, f -> {
                place(mc, peak.get());
                double t = smooth(f / 330.0) * 0.8 + f / 330.0 * 0.2;
                face(mc, lerp(215, 185, t), lerp(-8, -24, t));
                if (f == 200) {
                    shot("aurora");
                }
            });
        }

        if (wanted("palettes")) {
            StarlitConfig.AuroraColors[] order = {StarlitConfig.AuroraColors.VIVID, StarlitConfig.AuroraColors.CRIMSON,
                    StarlitConfig.AuroraColors.ICE, StarlitConfig.AuroraColors.CLASSIC};
            scene(mc, config, peak, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
                config.setAuroraColors(order[0]);
                SkyState.auroraLevel = 1.0F;
            }, () -> face(mc, 180, -14));
            record("08-palettes", 320, f -> {
                place(mc, peak.get());
                face(mc, 180 + 6 * Math.sin(f / 90.0), -14);
                if (f % 80 == 0) {
                    config.setAuroraColors(order[f / 80]);
                }
                if (f % 80 == 50) {
                    shot("aurora-" + order[f / 80].name().toLowerCase(Locale.ROOT));
                }
            });
        }

        if (wanted("halos")) {
            double[] dir = new double[2];
            scene(mc, config, peak, 11500, () -> {
                StarlitRuntime.forceHalo(true);
                SkyState.haloLevel = 1.0F;
            }, () -> {
                dir[0] = yawOf(SkyState.sun);
                dir[1] = pitchOf(SkyState.sun);
                face(mc, dir[0] - 6, dir[1] + 4);
            });
            record("10-halos", 330, f -> {
                place(mc, peak.get());
                double t = smooth(f / 330.0);
                face(mc, dir[0] - 6 + 12 * t, dir[1] + 4 - 4 * t);
                if (f == 200) {
                    shot("sun-dogs");
                }
            });
        }
    }

    private void filmMeadow(Minecraft mc, StarlitConfig config) {
        Supplier<Vec3> hill = () -> above(meadowSpot, 6);

        if (wanted("sunset")) {
            double[] dir = new double[2];
            scene(mc, config, hill, 12150, () -> {
            }, () -> {
                dir[0] = yawOf(SkyState.sun);
                face(mc, dir[0] - 14, -4);
            });
            record("09-sunset", 330, f -> {
                place(mc, hill.get());
                double t = smooth(f / 330.0) * 0.8 + f / 330.0 * 0.2;
                face(mc, dir[0] - 14 + 24 * t, -4 - 2 * t);
                if (f == 200) {
                    shot("sunset");
                }
            });
        }

        if (wanted("dusk")) {
            double[] dir = new double[2];
            scene(mc, config, hill, 12350, () -> config.setShootingStars(0), () -> {
                dir[0] = yawOf(SkyState.sun) + 180;
                face(mc, dir[0], -14);
            });
            once(() -> {
                run(mc, "tick rate 160");
                run(mc, "gamerule advance_time true");
            });
            record("03-dusk", 360, f -> {
                place(mc, hill.get());
                face(mc, dir[0] + 6 * smooth(f / 360.0), -14 - 10 * smooth(f / 360.0));
                if (f == 340) {
                    shot("dusk");
                }
            });
            once(() -> {
                run(mc, "gamerule advance_time false");
                run(mc, "tick rate 20");
            });
            holdFor(20, () -> place(mc, hill.get()));
        }

        if (wanted("rainbow")) {
            double[] dir = new double[2];
            scene(mc, config, hill, 9000, () -> {
                config.setBirds(false);
                StarlitRuntime.forceRainbow(now(mc));
                SkyState.rainbowLevel = 1.0F;
            }, () -> {
                float[] centre = SkyPainter.rainbowCenter(SkyState.sun);
                dir[0] = yawOf(centre);
                face(mc, dir[0] - 8, -12);
            });
            record("11-rainbow", 330, f -> {
                place(mc, hill.get());
                face(mc, dir[0] - 8 + 16 * smooth(f / 330.0), -12);
                if (f == 200) {
                    shot("rainbow");
                }
            });
        }

        if (wanted("birds")) {
            scene(mc, config, hill, 6000, () -> config.setBirds(false), () -> face(mc, -90, -14));
            once(() -> config.setBirds(true));
            record("12-birds", 330, f -> {
                place(mc, hill.get());
                face(mc, -90 + 4 * smooth(f / 330.0), -14);
                if (f == 0) {
                    BirdSky.spawnToward(now(mc) + 3.0, mc.player.getEyePosition(), look(-92, -18));
                } else if (f == 120) {
                    BirdSky.spawnToward(now(mc) + 4.0, mc.player.getEyePosition(), look(-80, -10));
                }
                if (f == 160) {
                    shot("birds");
                }
            });
        }

        if (wanted("comet")) {
            double[] dir = new double[2];
            scene(mc, config, hill, 14500, () -> {
                config.setShootingStars(0);
                SkyPainter.forceComet(SkyEvents.cometDirection(3L));
            }, () -> {
                float[] world = MeteorSky.toWorld(SkyEvents.cometDirection(3L), SkyState.starAngle);
                dir[0] = yawOf(world);
                dir[1] = Math.max(-30, Math.min(-9, pitchOf(world)));
                face(mc, dir[0] - 8, dir[1] + 3);
            });
            record("06-comet", 330, f -> {
                place(mc, hill.get());
                double t = smooth(f / 330.0);
                face(mc, dir[0] - 8 + 14 * t, dir[1] + 3 - 4 * t);
                if (f == 200) {
                    shot("comet");
                }
            });
        }

        if (wanted("meteors")) {
            Random random = new Random(seed);
            scene(mc, config, hill, 18000, () -> {
                config.setShootingStars(0);
                MeteorSky.forceShower(now(mc), 600.0);
            }, () -> face(mc, 40, -38));
            record("05-meteors", 330, f -> {
                place(mc, hill.get());
                double yaw = 40 + 8 * smooth(f / 330.0);
                face(mc, yaw, -38);
                if (f % 22 == 4 && f < 300) {
                    streak(mc, random, yaw, -38, false);
                }
                if (f == 110 || f == 230) {
                    streak(mc, random, yaw, -38, true);
                }
                if (f == 150) {
                    shot("meteors");
                }
            });
        }

        if (wanted("settings")) {
            scene(mc, config, hill, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
                SkyState.auroraLevel = 1.0F;
            }, () -> face(mc, 180, -20));
            once(() -> Screens.open(mc, new StarlitSettingsScreen(null, mc.options)));
            holdFor(20, () -> {
                place(mc, hill.get());
                cursor(0, new Stop(0, () -> corner(mc)));
            });
            record("14-settings", 330, f -> {
                place(mc, hill.get());
                AbstractSelectionList<?> list = list(Screens.current(mc));
                if (list != null && f >= 100 && f <= 200) {
                    list.setScrollAmount(list.maxScrollAmount() * smooth((f - 100) / 100.0));
                }
                cursor(f, new Stop(0, () -> corner(mc)),
                        new Stop(40, () -> at(mc, "starlit.options.constellation_lines", 0.5)),
                        new Stop(95, () -> right(mc)),
                        new Stop(225, () -> at(mc, "starlit.options.aurora_colors", 0.5)),
                        new Stop(310, () -> corner(mc)));
                if (f == 50) {
                    clickAt(mc, "starlit.options.constellation_lines");
                } else if (f == 235 || f == 270) {
                    clickAt(mc, "starlit.options.aurora_colors");
                } else if (f == 150) {
                    shot("settings");
                }
            });
            once(() -> {
                cursorShown = false;
                Screens.open(mc, null);
            });
        }

        if (wanted("outro")) {
            Random random = new Random(seed + 1);
            scene(mc, config, hill, 18000, () -> {
                config.setAuroraMode(StarlitConfig.AuroraMode.ALWAYS);
                SkyState.auroraLevel = 1.0F;
            }, () -> face(mc, 180, -4));
            record("15-outro", 360, f -> {
                place(mc, hill.get());
                double t = smooth(f / 360.0);
                face(mc, 180 + 10 * t, lerp(-4, -30, t));
                if (f == 60 || f == 170) {
                    streak(mc, random, 180 + 10 * t, lerp(-4, -30, t) - 10, false);
                }
                if (f == 120) {
                    shot("outro");
                }
            });
        }
    }

    private void filmEnd(Minecraft mc, StarlitConfig config) {
        if (!wanted("end")) {
            return;
        }
        once(() -> {
            config.resetToDefaults();
            run(mc, "execute in minecraft:the_end run kill @e[type=minecraft:ender_dragon]");
            cmd(mc, "execute in minecraft:the_end run tp @p %d.5 %d %d.5 0 -20", endSpot[0], endSpot[1] + 6, endSpot[2]);
        });
        until(() -> mc.level != null && mc.level.dimension() == Level.END && Screens.current(mc) == null);
        Supplier<Vec3> island = () -> above(endSpot, 6);
        holdFor(30, () -> place(mc, island.get()));
        settle(mc, () -> {
            place(mc, island.get());
            face(mc, 0, -18);
        });
        record("13-end", 330, f -> {
            place(mc, island.get());
            double t = smooth(f / 330.0) * 0.8 + f / 330.0 * 0.2;
            face(mc, lerp(0, 30, t), lerp(-18, -34, t));
            if (f == 200) {
                shot("end");
            }
        });
    }

    private void cursor(int f, Stop... stops) {
        double[] at = stops[0].where().get();
        for (int i = 1; i < stops.length; i++) {
            Stop stop = stops[i];
            if (f >= stop.frame()) {
                at = stop.where().get();
            } else if (f >= stop.frame() - GLIDE) {
                double[] to = stop.where().get();
                double s = smooth((f - (stop.frame() - GLIDE)) / (double) GLIDE);
                at = new double[]{lerp(at[0], to[0], s), lerp(at[1], to[1], s)};
                break;
            } else {
                break;
            }
        }
        cursorShown = true;
        cursorX = at[0];
        cursorY = at[1];
    }

    private static double[] corner(Minecraft mc) {
        Screen screen = Screens.current(mc);
        return new double[]{screen.width - 36, screen.height - 30};
    }

    private static double[] right(Minecraft mc) {
        Screen screen = Screens.current(mc);
        return new double[]{screen.width - 26, screen.height * 0.5};
    }

    private static double[] at(Minecraft mc, String key, double along) {
        AbstractWidget widget = find(Screens.current(mc), key);
        if (widget == null) {
            throw new IllegalStateException("no widget " + key);
        }
        return new double[]{widget.getX() + widget.getWidth() * along, widget.getY() + widget.getHeight() / 2.0};
    }

    private static void clickAt(Minecraft mc, String key) {
        double[] p = at(mc, key, 0.5);
        Ui.click(Screens.current(mc), p[0], p[1]);
    }

    private static AbstractSelectionList<?> list(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                return list;
            }
        }
        return null;
    }

    private static AbstractWidget find(GuiEventListener node, String key) {
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
        return null;
    }

    private static boolean labelled(AbstractWidget widget, String key) {
        if (!(widget.getMessage().getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        if (contents.getKey().equals(key)) {
            return true;
        }
        Object[] args = contents.getArgs();
        return args.length > 0
                && args[0] instanceof Component name
                && name.getContents() instanceof TranslatableContents inner
                && inner.getKey().equals(key);
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[starlit-showcase] " + message);
    }
}
