package dev.starlit.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

final class Terrain {
    private Terrain() {
    }

    @SuppressWarnings("unchecked")
    static String coldCommand(Minecraft mc) {
        Holder<Biome> snow = mc.level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)
                .getHolderOrThrow(Biomes.SNOWY_PLAINS);
        int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX());
        int cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                LevelChunk chunk = mc.level.getChunk(x, z);
                for (LevelChunkSection section : chunk.getSections()) {
                    PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) (Object) section.getBiomes();
                    for (int i = 0; i < 64; i++) {
                        biomes.getAndSetUnchecked(i & 3, (i >> 2) & 3, i >> 4, snow);
                    }
                }
            }
        }
        return "say painted snowy plains for the cold biome check";
    }
}
