package dev.starlit.mixin;

import dev.starlit.client.Compat;
import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class OptiFineGameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void starlit$afterLevel(CallbackInfo ci) {
        if (Compat.optifineShaders() && !Compat.optifineShadowPass()) {
            SkyPainter.drawOverlay();
        }
    }
}
