package dev.starlit.selftest.mixin;

import dev.starlit.selftest.StarlitSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void starlitSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        StarlitSelfTest.frame();
    }
}
