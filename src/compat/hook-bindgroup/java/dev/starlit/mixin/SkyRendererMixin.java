package dev.starlit.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void starlit$before(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
                                float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()).mul(poseStack.last().pose()));
        SkyPainter.beforeCelestials(renderTarget, sunAngle, moonAngle, starAngle, moonPhase.index(), rainBrightness,
                starBrightness, 0.0F);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void starlit$after(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
                               float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()).mul(poseStack.last().pose()));
        SkyPainter.afterCelestials(renderTarget, rainBrightness, starBrightness);
    }

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void starlit$endSky(CallbackInfo ci) {
        SkyPainter.setBase(new Matrix4f(RenderSystem.getModelViewStack()));
        SkyPainter.endSky(renderTarget);
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void starlit$stars(CallbackInfo ci) {
        if (SkyPainter.replacesVanillaStars()) {
            ci.cancel();
        }
    }
}
