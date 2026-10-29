package dev.starlit.mixin;

import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Priority above 1000 so this runs after Iris has composited the frame at the same point.
@Mixin(value = LevelRenderer.class, priority = 1100)
abstract class LevelRendererOverlayMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void starlit$overlay(CallbackInfo ci) {
        SkyPainter.overlay();
    }
}
