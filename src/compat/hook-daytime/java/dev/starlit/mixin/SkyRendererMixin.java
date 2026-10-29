package dev.starlit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void starlit$before(PoseStack poseStack, float timeOfDay, int moonPhase, float rainBrightness,
                                float starBrightness, CallbackInfo ci) {
        float angle = timeOfDay * (float) (Math.PI * 2.0);
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()).mul(poseStack.last().pose()));
        SkyPainter.beforeCelestials(Minecraft.getInstance().getMainRenderTarget(), angle, angle + (float) Math.PI, angle,
                moonPhase, rainBrightness, starBrightness, 0.0F);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void starlit$after(PoseStack poseStack, float timeOfDay, int moonPhase, float rainBrightness,
                               float starBrightness, CallbackInfo ci) {
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()).mul(poseStack.last().pose()));
        SkyPainter.afterCelestials(Minecraft.getInstance().getMainRenderTarget(), rainBrightness, starBrightness);
    }

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void starlit$endSky(CallbackInfo ci) {
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()));
        SkyPainter.endSky(Minecraft.getInstance().getMainRenderTarget());
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void starlit$stars(CallbackInfo ci) {
        if (SkyPainter.replacesVanillaStars()) {
            ci.cancel();
        }
    }
}
