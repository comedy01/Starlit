package dev.starlit.neoforge;

import dev.starlit.client.StarlitClient;
import dev.starlit.client.gui.StarlitSettingsScreen;
import dev.starlit.client.render.ShaderSetup;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = StarlitClient.MOD_ID, dist = Dist.CLIENT)
public final class StarlitNeoForge {
    public StarlitNeoForge(IEventBus modBus, ModContainer container) {
        StarlitClient.init(FMLPaths.CONFIGDIR.get());
        ShaderSetup.init(modBus);

        KeyMapping toggle = StarlitClient.createToggleKey();
        KeyMapping settings = StarlitClient.createSettingsKey();
        StarlitClient.setKeys(toggle, settings);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(toggle);
            event.register(settings);
        });

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new StarlitSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
