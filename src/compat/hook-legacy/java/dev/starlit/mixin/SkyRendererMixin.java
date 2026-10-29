package dev.starlit.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.starlit.client.render.SkyPainter;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class SkyRendererMixin {
    @Shadow
    private VertexBuffer starBuffer;

    @Shadow
    private ClientLevel level;

    @Unique
    private final Matrix4f starlit$base = new Matrix4f();

    @Unique
    private float starlit$partial;

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void starlit$capture(Matrix4f view, Matrix4f projection, float partial, Camera camera, boolean foggy,
                                 Runnable fog, CallbackInfo ci) {
        starlit$base.set(view);
        starlit$partial = partial;
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V"))
    private void starlit$before(CallbackInfo ci) {
        float partial = starlit$partial;
        float angle = level.getTimeOfDay(partial) * (float) (Math.PI * 2.0);
        float rain = 1.0F - level.getRainLevel(partial);
        SkyPainter.setBase(starlit$base);
        SkyPainter.beforeCelestials(null, angle, angle + (float) Math.PI, angle, level.getMoonPhase(), rain,
                level.getStarBrightness(partial) * rain, partial);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V"))
    private void starlit$after(CallbackInfo ci) {
        float partial = starlit$partial;
        float rain = 1.0F - level.getRainLevel(partial);
        SkyPainter.setBase(starlit$base);
        SkyPainter.afterCelestials(null, rain, level.getStarBrightness(partial) * rain);
    }

    @Inject(method = "renderEndSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;depthMask(Z)V", ordinal = 1))
    private void starlit$endSky(PoseStack poseStack, CallbackInfo ci) {
        SkyPainter.setBase(poseStack.last().pose());
        SkyPainter.endSky(null);
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private boolean starlit$stars(VertexBuffer buffer, Matrix4f pose, Matrix4f projection, ShaderInstance shader) {
        return buffer != starBuffer || !SkyPainter.replacesVanillaStars();
    }
}
