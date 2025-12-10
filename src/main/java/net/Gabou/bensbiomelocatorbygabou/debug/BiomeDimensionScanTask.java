package net.Gabou.bensbiomelocatorbygabou.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

public class BiomeDimensionScanTask {

    public static class Entry {
        public BlockPos firstFound;
        public int count;

        public Entry(BlockPos pos) {
            this.firstFound = pos;
            this.count = 1;
        }
    }

    public static class Result {
        public final Map<ResourceLocation, Entry> found;
        public final Map<ResourceLocation, Integer> missing;
        public final int totalSamples;

        public Result(Map<ResourceLocation, Entry> found,
                      Map<ResourceLocation, Integer> missing,
                      int totalSamples) {
            this.found = found;
            this.missing = missing;
            this.totalSamples = totalSamples;
        }
    }

    public static Result scanDimension(ServerLevel level, int radiusBlocks, int step) {

        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);

        Map<ResourceLocation, Entry> found = new HashMap<>();
        Map<ResourceLocation, Integer> missing = new HashMap<>();

        int totalSamples = 0;

        for (int x = -radiusBlocks; x <= radiusBlocks; x += step) {
            for (int z = -radiusBlocks; z <= radiusBlocks; z += step) {

                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                Biome biome = level.getBiome(pos).value();

                ResourceLocation id = reg.getKey(biome);
                if (id == null) {
                    continue;
                }

                totalSamples++;

                if (!found.containsKey(id)) {
                    found.put(id, new Entry(pos));
                } else {
                    found.get(id).count++;
                }
            }
        }

        for (var e : reg.entrySet()) {
            ResourceLocation id = e.getKey().location();
            if (!found.containsKey(id)) {
                missing.put(id, 0);
            }
        }

        return new Result(found, missing, totalSamples);
    }
}
