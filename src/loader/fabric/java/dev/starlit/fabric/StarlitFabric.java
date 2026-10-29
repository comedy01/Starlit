package dev.starlit.fabric;

import dev.starlit.client.KeyRegistrar;
import dev.starlit.client.StarlitClient;
import dev.starlit.client.render.ShaderSetup;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class StarlitFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StarlitClient.init(FabricLoader.getInstance().getConfigDir());
        ShaderSetup.init();
        StarlitClient.setKeys(
                KeyRegistrar.register(StarlitClient.createToggleKey()),
                KeyRegistrar.register(StarlitClient.createSettingsKey()));
    }
}
