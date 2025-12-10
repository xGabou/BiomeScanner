package net.Gabou.bensbiomelocatorbygabou.debug;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BiomeScanner {

    public record MultiResult(Map<ResourceKey<Level>, BiomeDimensionScanTask.Result> perDimension) {}

    public static CompletableFuture<MultiResult> scanAllDimensions(MinecraftServer server,
                                                                   int radiusBlocks,
                                                                   int step) {

        return CompletableFuture.supplyAsync(() -> {

            Map<ResourceKey<Level>, BiomeDimensionScanTask.Result> res = new LinkedHashMap<>();

            for (ServerLevel level : server.getAllLevels()) {
                ResourceKey<Level> key = level.dimension();
                BiomeDimensionScanTask.Result scan = BiomeDimensionScanTask.scanDimension(level, radiusBlocks, step);
                res.put(key, scan);
            }

            return new MultiResult(res);
        });
    }
}
