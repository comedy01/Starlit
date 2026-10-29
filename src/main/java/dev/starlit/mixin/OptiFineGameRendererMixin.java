package dev.starlit.mixin;

import dev.starlit.client.Compat;
import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// OptiFabric exposes OptiFine's classes too late for OptiFineShadersMixin, so on Fabric the deferred overlay is drawn
// once the level (and OptiFine's final pass) is done. On Forge both fire; drawOverlay only draws once per frame.
@Mixin(GameRenderer.class)
abstract class OptiFineGameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void starlit$afterLevel(CallbackInfo ci) {
        if (Compat.optifineShaders() && !Compat.optifineShadowPass()) {
            SkyPainter.drawOverlay();
        }
    }
}
