package dev.starlit.forge;

import dev.starlit.client.StarlitClient;
import dev.starlit.client.render.ShaderSetup;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(StarlitClient.MOD_ID)
public final class StarlitForge {
    public StarlitForge() {
        ModLoadingContext context = ModLoadingContext.get();
        ForgeClient.clientOnly(context);
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        StarlitClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = StarlitClient.createToggleKey();
        KeyMapping settings = StarlitClient.createSettingsKey();
        StarlitClient.setKeys(toggle, settings);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ShaderSetup.init(modBus);
        ForgeClient.keys(modBus, toggle, settings);
        ForgeClient.configScreen(context);
    }
}
