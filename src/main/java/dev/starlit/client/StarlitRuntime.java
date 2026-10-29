package dev.starlit.client;

import dev.starlit.client.gui.ScreenOpener;
import dev.starlit.client.gui.StarlitSettingsScreen;
import dev.starlit.config.StarlitConfig;
import dev.starlit.core.Meteor;
import dev.starlit.core.SkyEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class StarlitRuntime {
    private static final float AURORA_EASE = 0.01F;
    private static final float DAY_EASE = 0.006F;
    private static final double RAINBOW_SECONDS = 150.0;

    private static boolean wasRaining;
    private static boolean forcedHalo;
    private static double rainbowUntil = -1.0e9;

    private static long announcedNight = Long.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static Component lastAnnouncement;
    private static double lastWish = -1.0e9;
    private static int wishes;
    private static final double WISH_COOLDOWN = 45.0;
    private static final float WISH_CONE = (float) Math.cos(Math.toRadians(9.0));

    private StarlitRuntime() {
    }

    public static void beforeKeybinds() {
        Minecraft mc = Minecraft.getInstance();
        StarlitConfig config = StarlitClient.config();

        KeyMapping toggle = StarlitClient.toggleKey();
        while (toggle != null && toggle.consumeClick()) {
            config.setEnabled(!config.enabled());
            StarlitClient.saveConfig();
            if (mc.player != null) {
                Messages.overlay(mc.player, Texts.tr(
                        config.enabled() ? "starlit.message.on" : "starlit.message.off"));
            }
        }

        KeyMapping settings = StarlitClient.settingsKey();
        boolean open = false;
        while (settings != null && settings.consumeClick()) {
            open = true;
        }
        if (open) {
            StarlitSettingsScreen.open(mc);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        StarlitConfig config = StarlitClient.config();
        if (level == null || !config.enabled() || !starlitSky(level, config)) {
            MeteorSky.clear();
            BirdSky.clear();
            lastLevel = level;
            SkyState.auroraLevel = 0.0F;
            SkyState.haloLevel = 0.0F;
            SkyState.rainbowLevel = 0.0F;
            return;
        }
        long clock = Clock.time(level);
        SkyState.night = SkyEvents.night(clock);
        SkyState.timeOfNight = SkyEvents.timeOfNight(clock, 0.0F);
        double now = level.getGameTime() / 20.0;

        boolean fresh = level != lastLevel;
        lastLevel = level;
        if (fresh) {
            MeteorSky.clear();
            BirdSky.clear();
            wasRaining = false;
            rainbowUntil = -1.0e9;
        }

        float target = auroraTarget(mc, config);
        SkyState.auroraLevel = fresh ? target : SkyState.auroraLevel + (target - SkyState.auroraLevel) * AURORA_EASE;
        float rain = level.getRainLevel(1.0F);
        float halo = haloTarget(mc, config, rain);
        SkyState.haloLevel = fresh ? halo : SkyState.haloLevel + (halo - SkyState.haloLevel) * DAY_EASE;
        float rainbow = rainbowTarget(mc, config, rain, now);
        SkyState.rainbowLevel = fresh ? rainbow : SkyState.rainbowLevel + (rainbow - SkyState.rainbowLevel) * DAY_EASE;
        if (mc.player != null) {
            BirdSky.tick(config, now, mc.player.getEyePosition(), rain);
        }

        MeteorSky.tick(config, now);
        announce(mc, config);
        wish(mc, config, now);
    }

    // Overworld-type skies always; other mods' dimensions only when Modded Skies is on.
    public static boolean starlitSky(ClientLevel level, StarlitConfig config) {
        return SkyType.overworld(level) || (config.moddedSkies() && moddedDimension(level));
    }

    // Dimension keys print as "ResourceKey[minecraft:dimension / ns:path]" on every supported version.
    public static boolean moddedDimension(ClientLevel level) {
        String key = String.valueOf(level.dimension());
        int slash = key.lastIndexOf(" / ");
        String id = slash < 0 ? key : key.substring(slash + 3);
        return !id.startsWith("minecraft:");
    }

    private static float auroraTarget(Minecraft mc, StarlitConfig config) {
        if (config.auroraMode() == StarlitConfig.AuroraMode.OFF || mc.player == null) {
            return 0.0F;
        }
        float cold = coldness(mc);
        SkyState.auroraNorthBias = 0.9F - 0.5F * cold;
        if (config.auroraMode() == StarlitConfig.AuroraMode.ALWAYS) {
            return 1.0F;
        }
        float level = cold;
        if (SkyEvents.geomagneticStorm(SkyState.night)) {
            level = Math.max(level, 0.8F);
        }
        return level;
    }

    private static float haloTarget(Minecraft mc, StarlitConfig config, float rain) {
        if (!config.halos() || mc.player == null || rain > 0.2F) {
            return 0.0F;
        }
        if (forcedHalo) {
            return 1.0F;
        }
        if (coldness(mc) > 0.5F) {
            return 1.0F;
        }
        return SkyEvents.haloDay(SkyState.night) ? 0.8F : 0.0F;
    }

    private static float rainbowTarget(Minecraft mc, StarlitConfig config, float rain, double now) {
        if (rain > 0.6F) {
            wasRaining = true;
        } else if (wasRaining && rain < 0.35F) {
            wasRaining = false;
            if (config.rainbows() && mc.player != null && SkyState.sunHeight > -0.02F && rainyBiome(mc)) {
                rainbowUntil = now + RAINBOW_SECONDS;
            }
        }
        if (!config.rainbows() || now >= rainbowUntil) {
            return 0.0F;
        }
        return 1.0F;
    }

    private static boolean rainyBiome(Minecraft mc) {
        var biome = mc.level.getBiome(mc.player.blockPosition()).value();
        return Biomes.rainy(biome) && coldness(mc) < 0.5F;
    }

    public static void forceHalo(boolean on) {
        forcedHalo = on;
    }

    public static void forceRainbow(double now) {
        rainbowUntil = now + RAINBOW_SECONDS;
    }

    public static void endRainbow() {
        rainbowUntil = -1.0e9;
        wasRaining = false;
    }

    public static boolean rainbowPending(double now) {
        return now < rainbowUntil;
    }

    public static float coldness(Minecraft mc) {
        float temperature = mc.level.getBiome(mc.player.blockPosition()).value().getBaseTemperature();
        return Math.max(0.0F, Math.min(1.0F, (0.4F - temperature) / 0.4F));
    }

    private static void announce(Minecraft mc, StarlitConfig config) {
        if (!config.announce() || mc.player == null) {
            return;
        }
        float t = SkyState.timeOfNight;
        if (t < 0.03F || t > 0.2F || announcedNight == SkyState.night) {
            return;
        }
        announcedNight = SkyState.night;
        List<Component> parts = new ArrayList<>();
        if (SkyEvents.meteorShower(SkyState.night, config.meteorShowers())) {
            parts.add(Texts.tr("starlit.event.shower"));
        }
        if (SkyEvents.comet(SkyState.night, config.comets())) {
            parts.add(Texts.tr("starlit.event.comet"));
        }
        if (config.auroraMode() != StarlitConfig.AuroraMode.OFF && SkyEvents.geomagneticStorm(SkyState.night)) {
            parts.add(Texts.tr("starlit.event.storm"));
        }
        if (parts.isEmpty()) {
            return;
        }
        Component message = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            message = Texts.tr("starlit.event.join", message, parts.get(i));
        }
        lastAnnouncement = message;
        Messages.overlay(mc.player, message);
    }

    private static void wish(Minecraft mc, StarlitConfig config, double now) {
        if (!config.wishes() || mc.player == null || now - lastWish < WISH_COOLDOWN || ScreenOpener.current(mc) != null) {
            return;
        }
        Vec3 look = mc.player.getViewVector(1.0F);
        for (Meteor meteor : MeteorSky.meteors()) {
            if (meteor.fade(now) < 0.3F) {
                continue;
            }
            float[] head = meteor.head(now);
            double dot = head[0] * look.x + head[1] * look.y + head[2] * look.z;
            if (dot > WISH_CONE) {
                lastWish = now;
                wishes++;
                Messages.overlay(mc.player, Texts.tr(meteor.fireball ? "starlit.message.wish.fireball" : "starlit.message.wish"));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
                return;
            }
        }
    }

    public static int wishes() {
        return wishes;
    }

    public static void resetWishCooldown() {
        lastWish = -1.0e9;
    }

    public static Component lastAnnouncement() {
        return lastAnnouncement;
    }

    public static void resetAnnouncements() {
        announcedNight = Long.MIN_VALUE;
        lastAnnouncement = null;
    }
}
