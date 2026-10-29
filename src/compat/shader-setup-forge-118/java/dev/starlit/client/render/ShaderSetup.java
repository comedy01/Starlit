package dev.starlit.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class ShaderSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger("starlit");

    private ShaderSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterShadersEvent event) -> {
            for (GpuPipeline pipeline : SkyPainter.pipelines()) {
                try {
                    event.registerShader(new ShaderInstance(event.getResourceManager(), Gpu.id(pipeline.name),
                            Gpu.format(pipeline.textured)), shader -> Gpu.loaded(pipeline.name, shader));
                } catch (IOException e) {
                    LOGGER.error("Could not load the Starlit {} shader", pipeline.name, e);
                }
            }
        });
    }
}
