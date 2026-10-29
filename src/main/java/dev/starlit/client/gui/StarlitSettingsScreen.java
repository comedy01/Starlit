package dev.starlit.client.gui;

import dev.starlit.client.StarlitClient;
import dev.starlit.config.StarlitConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StarlitSettingsScreen extends StarlitOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public StarlitSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("starlit.options.title"));
    }

    public static void open(Minecraft client) {
        ScreenOpener.open(client, new StarlitSettingsScreen(null, client.options));
    }

    @Override
    protected void addOptions() {
        StarlitConfig config = StarlitClient.config();

        addRow(
                toggle("starlit.options.enabled", config::enabled, config::setEnabled),
                slider("starlit.options.brightness", 10, 200, 10, config.brightness(), config::setBrightness, StarlitSettingsScreen::percent));

        addHeader(Component.translatable("starlit.options.section.stars"));
        addRow(
                slider("starlit.options.star_count", 0, StarlitConfig.MAX_STARS, StarlitConfig.STAR_STEP, config.starCount(),
                        config::setStarCount, value -> value < StarlitConfig.MIN_STARS
                                ? Component.translatable("starlit.options.vanilla")
                                : Component.literal(String.format(Locale.ROOT, "%,d", value))),
                slider("starlit.options.twinkle", 0, 100, 10, config.twinkle(), config::setTwinkle, StarlitSettingsScreen::percentOrOff));
        addRow(
                toggle("starlit.options.star_colors", config::starColors, config::setStarColors),
                toggle("starlit.options.stars_at_dusk", config::starsAtDusk, config::setStarsAtDusk));
        addRow(
                toggle("starlit.options.constellations", config::constellations, config::setConstellations),
                toggle("starlit.options.constellation_lines", config::constellationLines, config::setConstellationLines));
        addRow(
                toggle("starlit.options.star_spikes", config::starSpikes, config::setStarSpikes),
                toggle("starlit.options.spyglass", config::spyglass, config::setSpyglass));

        addHeader(Component.translatable("starlit.options.section.deep"));
        addRow(
                slider("starlit.options.milky_way", 0, 200, 10, config.milkyWay(), config::setMilkyWay, StarlitSettingsScreen::percentOrOff),
                toggle("starlit.options.nebulae", config::nebulae, config::setNebulae));
        addRow(
                toggle("starlit.options.galaxies", config::galaxies, config::setGalaxies),
                toggle("starlit.options.planets", config::planets, config::setPlanets));
        addRow(
                toggle("starlit.options.moon_glow", config::moonGlow, config::setMoonGlow),
                slider("starlit.options.comets", 0, 100, 5, config.comets(), config::setComets, StarlitSettingsScreen::percentOrOff));
        addRow(toggle("starlit.options.end_sky", config::endSky, config::setEndSky), null);
        addRow(toggle("starlit.options.modded_skies", config::moddedSkies, config::setModdedSkies), null);

        addHeader(Component.translatable("starlit.options.section.day"));
        addRow(
                toggle("starlit.options.atmosphere", config::atmosphere, config::setAtmosphere),
                toggle("starlit.options.sunsets", config::sunsets, config::setSunsets));
        addRow(
                toggle("starlit.options.halos", config::halos, config::setHalos),
                toggle("starlit.options.rainbows", config::rainbows, config::setRainbows));
        addRow(toggle("starlit.options.birds", config::birds, config::setBirds), null);

        addHeader(Component.translatable("starlit.options.section.meteors"));
        addRow(
                slider("starlit.options.shooting_stars", 0, 100, 10, config.shootingStars(), config::setShootingStars, StarlitSettingsScreen::percentOrOff),
                slider("starlit.options.meteor_showers", 0, 100, 5, config.meteorShowers(), config::setMeteorShowers, StarlitSettingsScreen::percentOrOff));
        addRow(
                toggle("starlit.options.fireballs", config::fireballs, config::setFireballs),
                toggle("starlit.options.announce", config::announce, config::setAnnounce));
        addRow(toggle("starlit.options.wishes", config::wishes, config::setWishes), null);

        addHeader(Component.translatable("starlit.options.section.aurora"));
        addRow(
                cycle("starlit.options.aurora", () -> "starlit.options.aurora." + config.auroraMode().name().toLowerCase(Locale.ROOT),
                        () -> config.setAuroraMode(config.auroraMode().next())),
                cycle("starlit.options.aurora_colors", () -> "starlit.options.aurora_colors." + config.auroraColors().name().toLowerCase(Locale.ROOT),
                        () -> config.setAuroraColors(config.auroraColors().next())));
        addRow(
                slider("starlit.options.aurora_brightness", 10, 200, 10, config.auroraBrightness(), config::setAuroraBrightness, StarlitSettingsScreen::percent),
                null);

        addHeader(Component.translatable("starlit.options.section.other"));
        addRow(
                cycle("starlit.options.shader_mode", () -> "starlit.options.shader_mode." + config.shaderMode().name().toLowerCase(Locale.ROOT),
                        () -> config.setShaderMode(config.shaderMode().next())),
                null);
        addRow(keyBindsButton(), resetButton(config));
    }

    @Override
    public void removed() {
        super.removed();
        StarlitClient.saveConfig();
    }

    private AbstractWidget keyBindsButton() {
        return Button.builder(Component.translatable("starlit.options.keys"),
                        button -> ScreenOpener.open(minecraft, KeyBinds.screen(this, options)))
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("starlit.options.keys.tooltip")))
                .build();
    }

    private AbstractWidget resetButton(StarlitConfig config) {
        return Button.builder(Component.translatable("starlit.options.reset"), button -> {
                    config.resetToDefaults();
                    ScreenOpener.open(minecraft, new StarlitSettingsScreen(lastScreen, options));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("starlit.options.reset.tooltip")))
                .build();
    }

    private static Component percent(int value) {
        return Component.literal(value + "%");
    }

    private static Component percentOrOff(int value) {
        return value == 0 ? Component.translatable("options.off") : Component.literal(value + "%");
    }

    private static AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
                    setter.accept(!getter.getAsBoolean());
                    button.setMessage(toggleLabel(key, getter.getAsBoolean()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Component.translatable(on ? "options.on" : "options.off");
        return Component.translatable("options.generic_value", Component.translatable(key), state);
    }

    private static AbstractWidget cycle(String key, Supplier<String> valueKey, Runnable advance) {
        return Button.builder(cycleLabel(key, valueKey.get()), button -> {
                    advance.run();
                    button.setMessage(cycleLabel(key, valueKey.get()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component cycleLabel(String key, String valueKey) {
        return Component.translatable("options.generic_value", Component.translatable(key), Component.translatable(valueKey));
    }

    private static AbstractWidget slider(String key, int min, int max, int step, int initial, IntConsumer onChange,
                                         IntFunction<Component> format) {
        return new ValueSlider(key, min, max, step, initial, onChange, format);
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String captionKey;
        private final int min;
        private final int max;
        private final int step;
        private final IntConsumer onChange;
        private final IntFunction<Component> format;

        ValueSlider(String captionKey, int min, int max, int step, int initial, IntConsumer onChange,
                    IntFunction<Component> format) {
            super(0, 0, WIDTH, HEIGHT, Component.empty(), (initial - min) / (double) (max - min));
            this.captionKey = captionKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            this.format = format;
            setTooltip(Tooltip.create(Component.translatable(captionKey + ".tooltip")));
            updateMessage();
        }

        private int current() {
            int raw = min + (int) Math.round(value * (max - min));
            int snapped = min + Math.round((raw - min) / (float) step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("options.generic_value", Component.translatable(captionKey), format.apply(current())));
        }

        @Override
        protected void applyValue() {
            int snapped = current();
            value = (snapped - min) / (double) (max - min);
            onChange.accept(snapped);
        }
    }
}
