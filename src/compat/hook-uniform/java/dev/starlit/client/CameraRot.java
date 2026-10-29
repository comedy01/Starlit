package dev.starlit.client;

import net.minecraft.client.Minecraft;

public final class CameraRot {
    private CameraRot() {
    }

    public static float x() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getXRot();
    }

    public static float y() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
    }
}
