package dev.starlit.mixin;

import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1100)
abstract class LevelRendererOverlayMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void starlit$overlay(CallbackInfo ci) {
        SkyPainter.overlay();
    }
}
