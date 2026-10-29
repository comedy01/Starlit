package dev.starlit.mixin;

import dev.starlit.client.StarlitRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void starlit$keybinds(CallbackInfo ci) {
        StarlitRuntime.beforeKeybinds();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void starlit$tick(CallbackInfo ci) {
        StarlitRuntime.tick();
    }
}
