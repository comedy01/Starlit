package dev.starlit.client;

import net.minecraft.world.level.biome.Biome;

final class Biomes {
    private Biomes() {
    }

    static boolean rainy(Biome biome) {
        return biome.getPrecipitation() != Biome.Precipitation.NONE;
    }
}
