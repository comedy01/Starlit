package dev.starlit.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;

public final class SkyType {
    private SkyType() {
    }

    public static boolean overworld(ClientLevel level) {
        return level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
    }

    public static boolean none(ClientLevel level) {
        return level.effects().skyType() == DimensionSpecialEffects.SkyType.NONE;
    }
}
