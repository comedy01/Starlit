package dev.starlit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.starlit.config.StarlitConfig;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

public final class StarlitClient {
    public static final String MOD_ID = "starlit";

    private static StarlitConfig config = new StarlitConfig();
    private static Path configPath;
    private static KeyMapping toggleKey;
    private static KeyMapping settingsKey;

    private StarlitClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(StarlitConfig.FILE_NAME);
        config = StarlitConfig.load(configPath);
    }

    public static KeyMapping createToggleKey() {
        return KeyFactory.create("key.starlit.toggle", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createSettingsKey() {
        return KeyFactory.create("key.starlit.settings", InputConstants.UNKNOWN.getValue());
    }

    public static void setKeys(KeyMapping toggle, KeyMapping settings) {
        toggleKey = toggle;
        settingsKey = settings;
    }

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    public static KeyMapping settingsKey() {
        return settingsKey;
    }

    public static StarlitConfig config() {
        return config;
    }

    public static Path configPath() {
        return configPath;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
