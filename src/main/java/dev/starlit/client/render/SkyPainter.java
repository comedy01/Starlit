package dev.starlit.client.render;

import dev.starlit.client.BirdSky;
import dev.starlit.client.CameraRot;
import dev.starlit.client.Clock;
import dev.starlit.client.Compat;
import dev.starlit.client.Frames;
import dev.starlit.client.MeteorSky;
import dev.starlit.client.SkyState;
import dev.starlit.client.SkyType;
import dev.starlit.client.StarlitClient;
import dev.starlit.client.StarlitRuntime;
import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Constellations;
import dev.starlit.core.DeepSky;
import dev.starlit.core.Flock;
import dev.starlit.core.Meteor;
import dev.starlit.core.SkyEvents;
import dev.starlit.core.Star;
import dev.starlit.core.StarCatalog;
import dev.starlit.core.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class SkyPainter {
    private static final Logger LOGGER = LoggerFactory.getLogger("starlit");
    private static final float DISTANCE = 100.0F;
    private static final float[][] AURORA_PALETTES = {
            {0.12F, 1.0F, 0.42F, 0.18F, 0.85F, 0.62F, 0.55F, 0.25F, 0.9F},
            {0.1F, 1.0F, 0.5F, 0.95F, 0.25F, 0.78F, 0.4F, 0.3F, 1.0F},
            {1.0F, 0.22F, 0.3F, 0.95F, 0.18F, 0.5F, 0.5F, 0.1F, 0.65F},
            {0.3F, 0.85F, 1.0F, 0.42F, 0.6F, 1.0F, 0.75F, 0.5F, 1.0F}
    };
    private static final float[][] PLANET_COLORS = {
            {1.0F, 0.96F, 0.86F, 0.95F, 0.55F},
            {1.0F, 0.52F, 0.36F, 0.75F, 0.42F},
            {1.0F, 0.88F, 0.7F, 0.85F, 0.5F}
    };

    public enum Layer {
        MILKY_WAY, GALAXIES, STARS, LINES, PLANETS, COMET, MOON_GLOW, AURORA, METEORS, END_SKY, DAY_SKY, HALO, RAINBOW, BIRDS, BACKDROP, SUN_MOON
    }

    private static final float[] UP = {0.0F, 1.0F, 0.0F};

    private static final GpuPipeline MILKY_WAY = new GpuPipeline("milky_way", "dome", "milky_way", false, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline END_SKY = new GpuPipeline("end_sky", "dome", "end_sky", false, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline AURORA = new GpuPipeline("aurora", "aurora", "aurora", false, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline STARS = new GpuPipeline("stars", "point", "star", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline GLOW = new GpuPipeline("glow", "point", "glow", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline LINES = new GpuPipeline("lines", "point", "line", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline GALAXY = new GpuPipeline("galaxy", "point", "galaxy", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline TRAIL = new GpuPipeline("trail", "point", "trail", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline COMET = new GpuPipeline("comet", "point", "comet", true, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline DAY_SHADE = new GpuPipeline("day_shade", "dome", "day_shade", false, GpuPipeline.Blend.MULTIPLY);
    private static final GpuPipeline DAY_GLOW = new GpuPipeline("day_glow", "dome", "day_glow", false, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline HALO = new GpuPipeline("halo", "dome", "halo", false, GpuPipeline.Blend.ADDITIVE);
    private static final GpuPipeline RAINBOW = new GpuPipeline("rainbow", "dome", "rainbow", false, GpuPipeline.Blend.PREMULTIPLIED);
    private static final GpuPipeline BIRD = new GpuPipeline("bird", "point", "bird", true, GpuPipeline.Blend.TRANSLUCENT);
    private static final GpuPipeline BACKDROP = new GpuPipeline("backdrop", "dome", "backdrop", false, GpuPipeline.Blend.TRANSLUCENT);
    private static final GpuPipeline DISC = new GpuPipeline("disc", "point", "disc", true, GpuPipeline.Blend.TRANSLUCENT);
    private static final List<GpuPipeline> PIPELINES = List.of(MILKY_WAY, END_SKY, AURORA, STARS, GLOW, LINES, GALAXY, TRAIL, COMET, DAY_SHADE, DAY_GLOW, HALO, RAINBOW, BIRD,
            BACKDROP, DISC);

    private static final int[] DRAWS = new int[Layer.values().length];
    private static final Set<Layer> BROKEN = EnumSet.noneOf(Layer.class);
    private static final Matrix4f BASE = new Matrix4f();

    private static final int CURTAINS = 7;
    private static final int SEGMENTS = 72;

    private static GpuMesh dome;
    private static GpuMesh curtains;
    private static GpuMesh centered;
    private static GpuMesh strip;
    private static GpuMesh galaxies;
    private static int galaxyIndices;
    private static GpuMesh stars;
    private static int starIndices;
    private static String starKey = "";
    private static GpuMesh lines;
    private static int lineIndices;
    private static int frames;

    private SkyPainter() {
    }

    public static List<GpuPipeline> pipelines() {
        return PIPELINES;
    }

    public static void setBase(Matrix4f base) {
        BASE.set(base);
    }

    public static int draws(Layer layer) {
        return DRAWS[layer.ordinal()];
    }

    public static int frames() {
        return frames;
    }

    public static boolean broken(Layer layer) {
        return BROKEN.contains(layer);
    }

    public static boolean active() {
        StarlitConfig config = StarlitClient.config();
        return config.enabled() && (GpuPipeline.overlay || !Compat.shaderPackInUse());
    }

    // With a shader pack the sky hooks only record; overlay() draws after the pack has composited the frame.
    private static boolean deferred() {
        StarlitConfig config = StarlitClient.config();
        return config.enabled() && !GpuPipeline.overlay && config.shaderMode() != StarlitConfig.ShaderMode.OFF
                && Gpu.canOverlay() && Compat.shaderPackInUse();
    }

    public static boolean replacesVanillaStars() {
        StarlitConfig config = StarlitClient.config();
        return config.enabled() && (config.shaderMode() != StarlitConfig.ShaderMode.OFF && Gpu.canOverlay() || !Compat.shaderPackInUse())
                && config.starCount() > 0 && !BROKEN.contains(Layer.STARS);
    }

    private static final Matrix4f SAVED_BASE = new Matrix4f();
    private static float[] saved;
    private static boolean savedEnd;
    private static Object savedProjection;
    // Set whenever a sky hook ran this frame; a modded dimension whose own renderer skipped them gets the fallback.
    private static boolean hooked;
    private static boolean forceReplace;
    public static int fallbacks;
    public static int overlays;

    public static void overlay() {
        overlays++;
        boolean wasHooked = hooked;
        hooked = false;
        if (!wasHooked) {
            fallbackSky();
            return;
        }
        // OptiFine shader packs clear color and depth in their final pass; OptiFineShadersMixin draws after it instead.
        if (!Compat.optifineShaders()) {
            drawOverlay();
        }
    }

    public static void drawOverlay() {
        if (!deferred() || (saved == null && !savedEnd)) {
            return;
        }
        float[] p = saved;
        boolean end = savedEnd;
        saved = null;
        savedEnd = false;
        Object pass = Gpu.beginOverlay(savedProjection);
        if (pass == null) {
            return;
        }
        GpuPipeline.overlay = true;
        try {
            BASE.set(SAVED_BASE);
            if (end) {
                endSky(pass);
            } else {
                beforeCelestials(pass, p[0], p[1], p[2], (int) p[3], p[4], p[5], p[6]);
                afterCelestials(pass, p[4], p[5]);
            }
        } finally {
            GpuPipeline.overlay = false;
            Gpu.endOverlay(pass);
        }
    }

    // Draws the whole Starlit sky over a modded dimension's custom sky, with the angles vanilla would have used.
    private static void fallbackSky() {
        StarlitConfig config = StarlitClient.config();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!config.enabled() || !config.moddedSkies() || level == null || !StarlitRuntime.moddedDimension(level)
                || SkyType.none(level) || !Gpu.canOverlay()
                || Compat.shaderPackInUse() && config.shaderMode() == StarlitConfig.ShaderMode.OFF) {
            return;
        }
        Object pass = Gpu.beginOverlay(Gpu.projection());
        if (pass == null) {
            return;
        }
        float partial = Frames.partialTick(mc);
        long t = Clock.time(level);
        double d = t / 24000.0 - 0.25;
        d -= Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        float tod = (float) ((d * 2.0 + e) / 3.0);
        float angle = tod * (float) (Math.PI * 2.0);
        int phase = (int) Math.floorMod(t / 24000L, 8L);
        float g = Math.max(0.0F, Math.min(1.0F, 1.0F - ((float) Math.cos(angle) * 2.0F + 0.25F)));
        float rain = 1.0F - level.getRainLevel(partial);
        float starBrightness = g * g * 0.5F * rain;
        fallbacks++;
        GpuPipeline.overlay = true;
        forceReplace = true;
        try {
            BASE.identity().rotateX((float) Math.toRadians(CameraRot.x())).rotateY((float) Math.toRadians(CameraRot.y() + 180.0F));
            beforeCelestials(pass, angle, angle + (float) Math.PI, angle, phase, rain, starBrightness, partial);
            afterCelestials(pass, rain, starBrightness);
        } finally {
            GpuPipeline.overlay = false;
            forceReplace = false;
            // Our own beforeCelestials set it; leaving it would skip the fallback every other frame.
            hooked = false;
            Gpu.endOverlay(pass);
        }
    }

    private static float darkness(float starBrightness, float rainBrightness) {
        float sunDown = 1.0F - smoothstep(-0.2F, 0.08F, SkyState.sunHeight);
        return Math.max(0.0F, Math.min(1.0F, starBrightness / 0.5F)) * rainBrightness * sunDown;
    }

    public static void beforeCelestials(Object pass, float sunAngle, float moonAngle, float starAngle,
                                        int moonPhase, float rainBrightness, float starBrightness, float partial) {
        hooked = true;
        if (deferred()) {
            saved = new float[]{sunAngle, moonAngle, starAngle, moonPhase, rainBrightness, starBrightness, partial};
            savedEnd = false;
            SAVED_BASE.set(BASE);
            savedProjection = Gpu.projection();
            return;
        }
        frames++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            SkyState.time = (mc.level.getGameTime() + Frames.partialTick(mc)) / 20.0;
        }
        SkyState.starAngle = starAngle;
        float[] sun = MeteorSky.toWorld(UP, sunAngle);
        SkyState.sunHeight = sun[1];
        SkyState.sun = sun;
        float dark = darkness(starBrightness, rainBrightness);
        SkyState.darkness = dark;
        if (!active()) {
            return;
        }
        StarlitConfig config = StarlitClient.config();
        if (GpuPipeline.overlay && (config.shaderMode() == StarlitConfig.ShaderMode.REPLACE || forceReplace)) {
            replaceSky(pass, config, sun, sunAngle, moonAngle, moonPhase, rainBrightness);
        }
        daySky(pass, config, sun, MeteorSky.toWorld(UP, moonAngle), moonPhase, rainBrightness, dark);
        if (dark <= 0.001F) {
            return;
        }
        float master = config.brightness() / 100.0F;
        float lift = altitudeLift(mc);
        float scope = config.spyglass() && mc.player != null && mc.player.isScoping() ? 1.0F : 0.0F;
        float deep = dark * dark * master * (1.0F + lift + scope * 1.2F);
        float starFade = (config.starsAtDusk() ? (float) Math.pow(dark, 0.75) : dark * dark) * master * (1.0F + lift * 0.6F + scope * 0.5F);

        Matrix4f base = new Matrix4f(BASE);
        Matrix4f starFrame = new Matrix4f(base).rotateY((float) Math.toRadians(-90.0)).rotateX(starAngle);

        if (config.milkyWay() > 0 || config.nebulae()) {
            ensureShapes();
            draw(pass, Layer.MILKY_WAY, MILKY_WAY, dome, 36, 0, starFrame,
                    new Vector4f(deep, config.milkyWay() / 100.0F, config.nebulae() ? 1.0F : 0.0F, (float) SkyState.time),
                    new Vector3f(), new Matrix4f());
        }
        if (config.galaxies()) {
            ensureGalaxies();
            draw(pass, Layer.GALAXIES, GALAXY, galaxies, galaxyIndices, 0, starFrame,
                    new Vector4f(deep * 0.55F, 0.0F, 0.0F, 0.0F), new Vector3f(), new Matrix4f());
        }
        if (config.starCount() > 0 || config.constellations()) {
            ensureStars(config);
            if (starIndices > 0) {
                draw(pass, Layer.STARS, STARS, stars, starIndices, 0, starFrame,
                        new Vector4f(starFade * 1.15F, config.twinkle() / 100.0F * 0.8F, (float) (SkyState.time % 3600.0),
                                config.starSpikes() ? 1.0F : 0.0F),
                        new Vector3f(), new Matrix4f());
            }
        }
        if (config.constellations() && config.constellationLines()) {
            ensureLines();
            draw(pass, Layer.LINES, LINES, lines, lineIndices, 0, starFrame,
                    new Vector4f(starFade * 0.5F, 0.0F, 0.0F, 0.0F), new Vector3f(), new Matrix4f());
        }
        if (config.planets()) {
            ensureShapes();
            for (int i = 0; i < PLANET_COLORS.length; i++) {
                float[] color = PLANET_COLORS[i];
                Matrix4f m = facing(starFrame, SkyEvents.planetDirection(i, SkyState.night), DISTANCE - 1.0F, color[4]);
                draw(pass, Layer.PLANETS, GLOW, centered, 6, 0, m,
                        new Vector4f(color[0], color[1], color[2], color[3] * starFade * 1.6F), new Vector3f(), new Matrix4f());
            }
        }
        if (SkyEvents.comet(SkyState.night, config.comets()) || cometForced) {
            ensureShapes();
            float[] head = cometForced ? forcedComet : SkyEvents.cometDirection(SkyState.night);
            float strength = cometForced ? 1.0F : SkyEvents.cometStrength(SkyState.night);
            float[] tail = Vec.tangentAway(head, Vec.of(0.0F, 1.0F, 0.0F));
            Matrix4f m = stripMatrix(starFrame, head, tail, 36.0F, 9.0F, DISTANCE - 2.0F);
            draw(pass, Layer.COMET, COMET, strip, 6, 0, m,
                    new Vector4f(strength * deep * 2.0F, (float) (SkyState.time % 3600.0), 0.0F, 0.0F), new Vector3f(), new Matrix4f());
        }
        if (config.moonGlow()) {
            ensureShapes();
            float phaseLight = moonLight(moonPhase);
            if (phaseLight > 0.0F) {
                Matrix4f moonFrame = new Matrix4f(base).rotateY((float) Math.toRadians(-90.0)).rotateX(moonAngle)
                        .translate(0.0F, DISTANCE - 1.0F, 0.0F).scale(34.0F, 1.0F, 34.0F);
                draw(pass, Layer.MOON_GLOW, GLOW, centered, 6, 0, moonFrame,
                        new Vector4f(0.62F, 0.72F, 1.0F, 0.32F * phaseLight * dark * master * rainBrightness),
                        new Vector3f(), new Matrix4f());
            }
        }
    }

    public static void afterCelestials(Object pass, float rainBrightness, float starBrightness) {
        if (deferred()) {
            SAVED_BASE.set(BASE);
            return;
        }
        if (!active()) {
            return;
        }
        StarlitConfig config = StarlitClient.config();
        float dark = darkness(starBrightness, rainBrightness);
        float master = config.brightness() / 100.0F;
        Matrix4f base = new Matrix4f(BASE);
        birds(pass, config, base, rainBrightness);

        float aurora = SkyState.auroraLevel * SkyEvents.auroraActivity(SkyState.night) * dark * dark
                * config.auroraBrightness() / 100.0F * master;
        if (aurora > 0.003F) {
            ensureShapes();
            float[] p = AURORA_PALETTES[config.auroraColors().ordinal()];
            Matrix4f palette = new Matrix4f(
                    p[0], p[1], p[2], 0.0F,
                    p[3], p[4], p[5], 0.0F,
                    p[6], p[7], p[8], 0.0F,
                    0.0F, 0.0F, 0.0F, 1.0F);
            ensureCurtains();
            draw(pass, Layer.AURORA, AURORA, curtains, CURTAINS * SEGMENTS * 6, 0, base,
                    new Vector4f(aurora, (float) (SkyState.time % 3600.0), SkyState.auroraNorthBias, 0.0F),
                    new Vector3f(), palette);
        }

        List<Meteor> meteors = MeteorSky.meteors();
        if (!meteors.isEmpty() && dark > 0.05F) {
            ensureShapes();
            double now = SkyState.time;
            for (Meteor meteor : new ArrayList<>(meteors)) {
                float fade = meteor.fade(now);
                float tail = meteor.tail(now);
                if (fade <= 0.0F || tail <= 0.0F) {
                    continue;
                }
                float[] head = meteor.head(now);
                Matrix4f m = stripMatrix(base, head, Vec.scale(meteor.travel, -1.0F), tail * 95.0F, meteor.width, 95.0F);
                draw(pass, Layer.METEORS, TRAIL, strip, 6, 0, m,
                        new Vector4f(meteor.color[0], meteor.color[1], meteor.color[2], fade * dark * master),
                        new Vector3f(), new Matrix4f());
            }
        }
    }

    // REPLACE mode: paint over the shader pack's sky (it sits at far depth) with our own backdrop, sun and moon.
    private static void replaceSky(Object pass, StarlitConfig config, float[] sun, float sunAngle, float moonAngle,
                                   int moonPhase, float rainBrightness) {
        ensureShapes();
        Matrix4f base = new Matrix4f(BASE);
        float time = (float) (SkyState.time % 3600.0);
        draw(pass, Layer.BACKDROP, BACKDROP, dome, 36, 0, base, new Vector4f(sun[0], sun[1], sun[2], rainBrightness),
                new Vector3f(), params(1.0F, config.sunsets() ? 1.0F : 0.0F, time));
        Matrix4f sunFrame = new Matrix4f(base).rotateY((float) Math.toRadians(-90.0)).rotateX(sunAngle)
                .translate(0.0F, DISTANCE - 1.0F, 0.0F).scale(30.0F, 1.0F, 30.0F);
        float sunUp = smoothstep(-0.12F, 0.04F, sun[1]);
        if (sunUp > 0.0F) {
            draw(pass, Layer.SUN_MOON, DISC, centered, 6, 0, sunFrame,
                    new Vector4f(1.0F, 0.86F, 0.6F, sunUp * rainBrightness), new Vector3f(), params(0.0F, 0.0F, time));
        }
        float moonUp = 1.0F - smoothstep(-0.05F, 0.25F, sun[1]) * 0.85F;
        Matrix4f moonFrame = new Matrix4f(base).rotateY((float) Math.toRadians(-90.0)).rotateX(moonAngle)
                .translate(0.0F, DISTANCE - 1.0F, 0.0F).scale(22.0F, 1.0F, 22.0F);
        draw(pass, Layer.SUN_MOON, DISC, centered, 6, 0, moonFrame,
                new Vector4f(0.92F, 0.94F, 1.0F, moonUp * (0.35F + 0.65F * rainBrightness)), new Vector3f(),
                params(1.0F, Math.floorMod(moonPhase, 8) / 8.0F, time));
    }

    private static void daySky(Object pass, StarlitConfig config, float[] sun, float[] moon, int moonPhase,
                               float rainBrightness, float dark) {
        float master = config.brightness() / 100.0F;
        Matrix4f base = new Matrix4f(BASE);
        float time = (float) (SkyState.time % 3600.0);
        if (sun[1] > -0.35F && (config.atmosphere() || config.sunsets())) {
            ensureShapes();
            Matrix4f params = params(config.atmosphere() ? 1.0F : 0.0F, config.sunsets() ? 1.0F : 0.0F, time);
            if (!GpuPipeline.overlay) {
                draw(pass, Layer.DAY_SKY, DAY_SHADE, dome, 36, 0, base,
                    new Vector4f(sun[0], sun[1], sun[2], rainBrightness * Math.min(1.0F, master)), new Vector3f(), params);
            }
            draw(pass, Layer.DAY_SKY, DAY_GLOW, dome, 36, 0, base,
                    new Vector4f(sun[0], sun[1], sun[2], rainBrightness * master), new Vector3f(), params);
        }
        float halo = SkyState.haloLevel * rainBrightness * master;
        if (config.halos() && halo > 0.01F) {
            ensureShapes();
            float sunHalo = halo * smoothstep(-0.04F, 0.08F, sun[1]);
            if (sunHalo > 0.01F) {
                draw(pass, Layer.HALO, HALO, dome, 36, 0, base, new Vector4f(sun[0], sun[1], sun[2], sunHalo),
                        new Vector3f(), params(1.0F, 1.0F, time));
            }
            float moonHalo = halo * dark * moonLight(moonPhase) * 0.7F;
            if (moonHalo > 0.01F && moon[1] > -0.05F) {
                draw(pass, Layer.HALO, HALO, dome, 36, 0, base, new Vector4f(moon[0], moon[1], moon[2], moonHalo),
                        new Vector3f(), params(0.35F, 0.6F, time));
            }
        }
        float rainbow = SkyState.rainbowLevel * rainBrightness * rainBrightness * master * smoothstep(-0.02F, 0.1F, sun[1]);
        if (config.rainbows() && rainbow > 0.01F) {
            ensureShapes();
            float[] center = rainbowCenter(sun);
            draw(pass, Layer.RAINBOW, RAINBOW, dome, 36, 0, base, new Vector4f(center[0], center[1], center[2], rainbow),
                    new Vector3f(), params(0.0F, 0.0F, time));
        }
    }

    public static float[] rainbowCenter(float[] sun) {
        float[] away = Vec.normalize(Vec.of(-sun[0], 0.0F, -sun[2] + 0.45F));
        float altitude = -Math.min((float) Math.asin(Math.max(-1.0F, Math.min(1.0F, sun[1]))), 0.6F) * 0.6F - 0.02F;
        float c = (float) Math.cos(altitude);
        return Vec.of(away[0] * c, (float) Math.sin(altitude), away[2] * c);
    }

    private static Matrix4f params(float a, float b, float time) {
        return new Matrix4f(
                a, b, 0.0F, time,
                0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static void birds(Object pass, StarlitConfig config, Matrix4f base, float rainBrightness) {
        List<Flock> flocks = BirdSky.flocks();
        Minecraft mc = Minecraft.getInstance();
        if (!config.birds() || flocks.isEmpty() || mc.player == null) {
            return;
        }
        ensureShapes();
        double now = SkyState.time;
        Vec3 eye = mc.player.getEyePosition(Frames.partialTick(mc));
        float daylight = smoothstep(0.0F, 0.15F, SkyState.sunHeight) * rainBrightness;
        float d = DISTANCE - 5.0F;
        for (Flock flock : new ArrayList<>(flocks)) {
            float fade = flock.fade(now) * daylight;
            if (fade <= 0.01F) {
                continue;
            }
            for (int i = 0; i < flock.size; i++) {
                float[] p = flock.bird(i, now);
                float[] rel = Vec.of((float) (flock.anchorX + p[0] - eye.x), (float) (flock.anchorY + p[1] - eye.y),
                        (float) (flock.anchorZ + p[2] - eye.z));
                float distance = (float) Math.sqrt(Vec.dot(rel, rel));
                if (distance < 4.0F) {
                    continue;
                }
                float[] dir = Vec.scale(rel, 1.0F / distance);
                if (dir[1] < -0.02F) {
                    continue;
                }
                float size = Math.min(6.0F, 1.0F * d / distance);
                float alpha = fade * Math.max(0.0F, Math.min(0.9F, 1.25F - distance / 380.0F));
                float[] side = Vec.cross(dir, UP);
                float[] right = Vec.dot(side, side) < 1.0e-6F ? Vec.of(1.0F, 0.0F, 0.0F) : Vec.normalize(side);
                float[] up = Vec.cross(right, dir);
                if (up[1] < 0.0F) {
                    up = Vec.scale(up, -1.0F);
                }
                Matrix4f m = new Matrix4f(base).mul(new Matrix4f(
                        right[0] * size, right[1] * size, right[2] * size, 0.0F,
                        dir[0], dir[1], dir[2], 0.0F,
                        up[0] * size, up[1] * size, up[2] * size, 0.0F,
                        dir[0] * d, dir[1] * d, dir[2] * d, 1.0F));
                draw(pass, Layer.BIRDS, BIRD, centered, 6, 0, m, new Vector4f(flock.flap(i, now), alpha, 0.0F, 0.0F),
                        new Vector3f(), new Matrix4f());
            }
        }
    }

    private static float altitudeLift(Minecraft mc) {
        if (mc.player == null) {
            return 0.0F;
        }
        double y = mc.player.getEyeY();
        return (float) Math.max(0.0, Math.min(0.35, (y - 110.0) / 300.0));
    }

    public static void endSky(Object pass) {
        hooked = true;
        StarlitConfig config = StarlitClient.config();
        if (deferred()) {
            saved = null;
            savedEnd = true;
            SAVED_BASE.set(BASE);
            savedProjection = Gpu.projection();
            return;
        }
        if (!active() || !config.endSky()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            SkyState.time = (mc.level.getGameTime() + Frames.partialTick(mc)) / 20.0;
        }
        float master = config.brightness() / 100.0F;
        Matrix4f base = new Matrix4f(BASE);
        ensureShapes();
        draw(pass, Layer.END_SKY, END_SKY, dome, 36, 0, base,
                new Vector4f(master, (float) (SkyState.time % 3600.0), 0.0F, 0.0F), new Vector3f(), new Matrix4f());
        if (config.starCount() > 0) {
            ensureStars(config);
            if (starIndices > 0) {
                Matrix4f tilted = new Matrix4f(base).rotateZ(0.6F).rotateY((float) (SkyState.time * 0.002));
                draw(pass, Layer.END_SKY, STARS, stars, starIndices, 0, tilted,
                        new Vector4f(master * 0.85F, config.twinkle() / 100.0F * 0.8F, (float) (SkyState.time % 3600.0),
                                config.starSpikes() ? 1.0F : 0.0F),
                        new Vector3f(), new Matrix4f());
            }
        }
    }

    private static boolean cometForced;
    private static float[] forcedComet;

    public static void forceComet(float[] direction) {
        cometForced = direction != null;
        forcedComet = direction;
    }

    private static float moonLight(int phase) {
        int fromFull = Math.min(phase, 8 - phase);
        return 1.0F - fromFull / 4.0F;
    }

    private static Matrix4f facing(Matrix4f frame, float[] direction, float distance, float size) {
        Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(direction[0], direction[1], direction[2]));
        return new Matrix4f(frame).rotate(turn).translate(0.0F, distance, 0.0F).scale(size, 1.0F, size);
    }

    private static Matrix4f stripMatrix(Matrix4f frame, float[] head, float[] along, float length, float width, float distance) {
        float[] across = Vec.normalize(Vec.cross(along, head));
        Matrix4f local = new Matrix4f(
                along[0] * length, along[1] * length, along[2] * length, 0.0F,
                across[0] * width, across[1] * width, across[2] * width, 0.0F,
                head[0], head[1], head[2], 0.0F,
                head[0] * distance, head[1] * distance, head[2] * distance, 1.0F);
        return new Matrix4f(frame).mul(local);
    }

    private static void draw(Object pass, Layer layer, GpuPipeline pipeline, GpuMesh vertices, int indexCount,
                             int baseVertex, Matrix4f modelView, Vector4f color, Vector3f offset, Matrix4f extra) {
        if (BROKEN.contains(layer) || vertices == null || vertices.handle == null || indexCount <= 0) {
            return;
        }
        try {
            if (pipeline.failed || !Gpu.ready(pipeline)) {
                pipeline.failed = true;
                fail(layer, "its shader did not compile", null);
                return;
            }
            Gpu.draw(pass, pipeline, vertices, indexCount, baseVertex, modelView, color, offset, extra);
            DRAWS[layer.ordinal()]++;
        } catch (RuntimeException e) {
            fail(layer, "drawing failed", e);
        }
    }

    private static void fail(Layer layer, String why, Throwable error) {
        if (BROKEN.add(layer)) {
            LOGGER.error("Starlit turned off the {} layer: {}", layer, why, error);
        }
    }

    private static void ensureShapes() {
        if (dome != null) {
            return;
        }
        dome = Gpu.mesh("Starlit dome", false, 24, builder -> {
            float s = 50.0F;
            float[][] faces = {
                    {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s},
                    {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
                    {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s},
                    {-s, -s, s, s, -s, s, s, s, s, -s, s, s},
                    {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s},
                    {s, -s, -s, s, s, -s, s, s, s, s, -s, s}};
            for (float[] f : faces) {
                for (int i = 0; i < 12; i += 3) {
                    builder.vertex(f[i], f[i + 1], f[i + 2]);
                }
            }
        });
        centered = Gpu.mesh("Starlit glow quad", true, 4, builder -> {
            builder.vertex(-1.0F, 0.0F, -1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(1.0F, 0.0F, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(-1.0F, 0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        });
        strip = Gpu.mesh("Starlit strip", true, 4, builder -> {
            builder.vertex(0.0F, -1.0F, 0.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(1.0F, -1.0F, 0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
            builder.vertex(0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        });
    }

    private static void ensureCurtains() {
        if (curtains != null) {
            return;
        }
        curtains = Gpu.mesh("Starlit aurora curtains", false, CURTAINS * SEGMENTS * 4, builder -> {
            for (int c = 0; c < CURTAINS; c++) {
                for (int i = 0; i < SEGMENTS; i++) {
                    float u0 = i / (float) SEGMENTS;
                    float u1 = (i + 1) / (float) SEGMENTS;
                    builder.vertex(u0, 0.0F, c);
                    builder.vertex(u1, 0.0F, c);
                    builder.vertex(u1, 1.0F, c);
                    builder.vertex(u0, 1.0F, c);
                }
            }
        });
    }

    private static void ensureGalaxies() {
        if (galaxies != null) {
            return;
        }
        List<DeepSky.Object> objects = DeepSky.GALAXIES;
        galaxies = Gpu.mesh("Starlit galaxies", true, objects.size() * 4, builder -> {
            for (DeepSky.Object object : objects) {
                float[][] c = DeepSky.corners(object, DISTANCE);
                float[][] uv = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int i = 0; i < 4; i++) {
                    builder.vertex(c[i][0], c[i][1], c[i][2], uv[i][0], uv[i][1],
                            object.red(), object.green(), object.blue(), object.kind());
                }
            }
        });
        galaxyIndices = objects.size() * 6;
    }

    private static void ensureStars(StarlitConfig config) {
        String key = config.starCount() + "/" + config.starColors() + "/" + config.constellations();
        if (stars != null && key.equals(starKey)) {
            return;
        }
        if (stars != null) {
            Gpu.close(stars);
            stars = null;
        }
        starKey = key;
        List<Star> list = new ArrayList<>(StarCatalog.generate(config.starCount(), config.starColors(), StarCatalog.SEED));
        if (config.constellations()) {
            int n = 0;
            for (Constellations.Shape shape : Constellations.SHAPES) {
                for (float[] dir : Constellations.stars(shape)) {
                    float bright = 0.85F + 0.15F * (n++ % 3) / 2.0F;
                    list.add(new Star(dir[0], dir[1], dir[2], 0.5F, bright * 0.92F, bright * 0.95F, bright, 0.3F + (n % 7) * 0.09F));
                }
            }
        }
        starIndices = list.size() * 6;
        if (list.isEmpty()) {
            return;
        }
        stars = Gpu.mesh("Starlit stars", true, list.size() * 4, builder -> {
            for (Star star : list) {
                float[] d = star.direction();
                float[] east = Vec.anyPerpendicular(d);
                float[] north = Vec.cross(d, east);
                float[] center = Vec.scale(d, DISTANCE);
                float s = star.size();
                float reach = Math.max(star.red(), Math.max(star.green(), star.blue())) > 0.72F ? 2.4F : 1.0F;
                float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (float[] c : corner) {
                    float[] p = Vec.add(center, Vec.add(Vec.scale(east, c[0] * s * reach), Vec.scale(north, c[1] * s * reach)));
                    builder.vertex(p[0], p[1], p[2], c[0] * reach, c[1] * reach,
                            star.red(), star.green(), star.blue(), star.phase());
                }
            }
        });
    }

    private static void ensureLines() {
        if (lines != null) {
            return;
        }
        List<Constellations.Segment> segments = new ArrayList<>();
        for (Constellations.Shape shape : Constellations.SHAPES) {
            segments.addAll(Constellations.segments(shape));
        }
        lineIndices = segments.size() * 6;
        lines = Gpu.mesh("Starlit constellation lines", true, segments.size() * 4, builder -> {
            for (Constellations.Segment segment : segments) {
                float[] a = Vec.scale(segment.from(), DISTANCE);
                float[] b = Vec.scale(segment.to(), DISTANCE);
                float[] along = Vec.normalize(Vec.sub(b, a));
                float[] across = Vec.scale(Vec.normalize(Vec.cross(along, segment.from())), 0.09F);
                float[][] p = {Vec.sub(a, across), Vec.sub(b, across), Vec.add(b, across), Vec.add(a, across)};
                float[][] uv = {{0, -1}, {1, -1}, {1, 1}, {0, 1}};
                for (int i = 0; i < 4; i++) {
                    builder.vertex(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], 0.55F, 0.7F, 1.0F, 1.0F);
                }
            }
        });
    }
}
