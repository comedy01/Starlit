package dev.starlit.client.render;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.LoggerFactory;

public final class ShaderSetup {
    private static final String PREFIX = "shaders/core/starlit_";
    private static final List<ShaderInstance> OWNED = new ArrayList<>();

    private ShaderSetup() {
    }

    public static void init() {
        boolean optifine = FabricLoader.getInstance().isModLoaded("optifabric");
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            if (optifine) {
                loadVanilla();
                return;
            }
            for (GpuPipeline pipeline : SkyPainter.pipelines()) {
                context.register(Gpu.id(pipeline.name), Gpu.format(pipeline.textured), shader -> Gpu.loaded(pipeline.name, shader));
            }
        });
    }

    private static void loadVanilla() {
        for (ShaderInstance shader : OWNED) {
            shader.close();
        }
        OWNED.clear();
        ResourceProvider base = Minecraft.getInstance().getResourceManager();
        ResourceProvider provider = location -> base.getResource(redirect(location));
        for (GpuPipeline pipeline : SkyPainter.pipelines()) {
            try {
                ShaderInstance shader = new ShaderInstance(provider, "starlit_" + pipeline.name, Gpu.format(pipeline.textured));
                OWNED.add(shader);
                Gpu.loaded(pipeline.name, shader);
            } catch (Exception e) {
                LoggerFactory.getLogger("starlit").error("Starlit shader {} failed to load", pipeline.name, e);
            }
        }
    }

    private static ResourceLocation redirect(ResourceLocation location) {
        String path = location.getPath();
        int colon = path.indexOf(':');
        if (colon >= 0) {
            return ResourceLocation.tryParse(path);
        }
        if (location.getNamespace().equals("minecraft") && path.startsWith(PREFIX)) {
            return ResourceLocation.tryParse("starlit:shaders/core/" + path.substring(PREFIX.length()));
        }
        return location;
    }
}
