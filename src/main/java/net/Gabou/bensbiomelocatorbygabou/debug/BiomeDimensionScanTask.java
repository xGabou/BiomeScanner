package net.Gabou.bensbiomelocatorbygabou.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class BiomeDimensionScanTask {

    private static final Logger LOGGER = LogUtils.getLogger();

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
        public final int possibleBiomeCount;

        public Result(Map<ResourceLocation, Entry> found,
                      Map<ResourceLocation, Integer> missing,
                      int totalSamples,
                      int possibleBiomeCount) {
            this.found = found;
            this.missing = missing;
            this.totalSamples = totalSamples;
            this.possibleBiomeCount = possibleBiomeCount;
        }
    }

    private record PartialResult(Map<ResourceLocation, Entry> found, int totalSamples) {}

    public static Result scanDimension(ServerLevel level, int radiusBlocks, int step) {

        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler climateSampler = level.getChunkSource().randomState().sampler();
        int sampleY = level.getSeaLevel();
        Set<ResourceLocation> possibleBiomes = biomeSource.possibleBiomes().stream()
                .map((biomeHolder -> reg.getKey(biomeHolder.get())))
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<Integer> xCoords = new ArrayList<>();
        for (int x = -radiusBlocks; x <= radiusBlocks; x += step) {
            xCoords.add(x);
        }

        List<Integer> zCoords = new ArrayList<>();
        for (int z = -radiusBlocks; z <= radiusBlocks; z += step) {
            zCoords.add(z);
        }

        int stripes = xCoords.size();
        int defaultThreads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        int threadCap = Math.max(1, Integer.getInteger("biomeScan.maxThreads", defaultThreads));
        int maxThreads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), threadCap));
        int threadCount = Math.max(1, Math.min(maxThreads, stripes));
        int slice = (stripes + threadCount - 1) / threadCount;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger processed = new AtomicInteger(0);
        long totalSamples = (long) xCoords.size() * (long) zCoords.size();
        final long totalSamplesForScan = totalSamples <= 0 ? 1 : totalSamples;
        AtomicInteger lastLoggedPercent = new AtomicInteger(-1);

        List<Callable<PartialResult>> tasks = new ArrayList<>();
        for (int start = 0; start < stripes; start += slice) {
            final int startIndex = start;
            final int endIndex = Math.min(start + slice, stripes);
            tasks.add(() -> scanStripe(level, reg, biomeSource, climateSampler, sampleY, xCoords, zCoords, startIndex, endIndex, processed, totalSamplesForScan, lastLoggedPercent));
        }

        Map<ResourceLocation, Entry> mergedFound = new HashMap<>();
        int totalCount = 0;

        try {
            List<Future<PartialResult>> futures = executor.invokeAll(tasks);
            for (Future<PartialResult> future : futures) {
                PartialResult partial = future.get();
                totalCount += partial.totalSamples();
                mergeFound(mergedFound, partial.found());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Biome scan interrupted in {}", level.dimension().location(), e);
        } catch (ExecutionException e) {
            LOGGER.error("Biome scan failed in {}", level.dimension().location(), e);
        } finally {
            executor.shutdown();
        }

        Map<ResourceLocation, Integer> missing = new HashMap<>();
        for (ResourceLocation id : possibleBiomes) {
            if (!mergedFound.containsKey(id)) {
                missing.put(id, 0);
            }
        }

        return new Result(mergedFound, missing, totalCount, possibleBiomes.size());
    }

    private static PartialResult scanStripe(ServerLevel level,
                                            Registry<Biome> reg,
                                            BiomeSource biomeSource,
                                            Climate.Sampler climateSampler,
                                            int sampleY,
                                            List<Integer> xCoords,
                                            List<Integer> zCoords,
                                            int startIndex,
                                            int endIndex,
                                            AtomicInteger processed,
                                            long totalSamples,
                                            AtomicInteger lastLoggedPercent) {

        Map<ResourceLocation, Entry> localFound = new HashMap<>();
        int localTotal = 0;

        for (int xi = startIndex; xi < endIndex; xi++) {
            int x = xCoords.get(xi);
            for (int z : zCoords) {
                BlockPos pos = new BlockPos(x, sampleY, z);
                Biome biome = biomeSource.getNoiseBiome(x >> 2, sampleY >> 2, z >> 2, climateSampler).value();

                ResourceLocation id = reg.getKey(biome);
                if (id == null) {
                    continue;
                }

                localTotal++;
                localFound.compute(id, (key, entry) -> {
                    if (entry == null) {
                        return new Entry(pos);
                    }
                    entry.count++;
                    return entry;
                });

                int processedNow = processed.incrementAndGet();
                long processedNowLong = processedNow;
                int percent = (int) ((processedNowLong * 100L) / totalSamples);
                int previous = lastLoggedPercent.get();
                if (percent > previous && lastLoggedPercent.compareAndSet(previous, percent)) {
                    LOGGER.info("Biome scan {} progress: {}% ({} / {})",
                            level.dimension().location(),
                            percent,
                            processedNow,
                            totalSamples);
                }
            }
        }

        return new PartialResult(localFound, localTotal);
    }

    private static void mergeFound(Map<ResourceLocation, Entry> merged, Map<ResourceLocation, Entry> partial) {
        for (var entry : partial.entrySet()) {
            ResourceLocation id = entry.getKey();
            Entry incoming = entry.getValue();
            Entry existing = merged.get(id);
            if (existing == null) {
                Entry copy = new Entry(incoming.firstFound);
                copy.count = incoming.count;
                merged.put(id, copy);
            } else {
                existing.count += incoming.count;
                if (distanceSq(incoming.firstFound) < distanceSq(existing.firstFound)) {
                    existing.firstFound = incoming.firstFound;
                }
            }
        }
    }

    private static double distanceSq(BlockPos pos) {
        return (double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ();
    }
}
