package dev.starlit.selftest;

import net.minecraft.client.Minecraft;

final class Terrain {
    private Terrain() {
    }

    static String coldCommand(Minecraft mc) {
        return "fillbiome -16 -64 -16 15 -33 15 minecraft:snowy_plains";
    }
}
