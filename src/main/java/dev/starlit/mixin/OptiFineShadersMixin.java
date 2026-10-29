package dev.starlit.mixin;

import dev.starlit.client.Compat;
import dev.starlit.client.render.SkyPainter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.optifine.shaders.Shaders", remap = false)
abstract class OptiFineShadersMixin {
    @Inject(method = "endRender", at = @At("RETURN"), require = 0, remap = false)
    private static void starlit$endRender(CallbackInfo ci) {
        if (!Compat.optifineShadowPass()) {
            SkyPainter.drawOverlay();
        }
    }
}
