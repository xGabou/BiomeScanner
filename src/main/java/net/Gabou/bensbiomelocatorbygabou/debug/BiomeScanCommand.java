package net.Gabou.bensbiomelocatorbygabou.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BiomeScanCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("biome_scan_all")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.argument("radius", IntegerArgumentType.integer(100, 60000))
                        .executes(ctx -> runScan(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                32,
                                false))
                        .then(Commands.argument("step", IntegerArgumentType.integer(4, 512))
                                .executes(ctx -> runScan(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "step"),
                                        false))
                                .then(Commands.literal("save")
                                        .executes(ctx -> runScan(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "step"),
                                                true))))
                        .then(Commands.literal("save")
                                .executes(ctx -> runScan(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                        32,
                                        true)))
                )
        );
    }

    private static int runScan(CommandSourceStack source, int radius, int step, boolean save) {

        int effectiveStep = Math.max(4, Math.min(step, 512));

        source.sendSuccess(() ->
                Component.literal("Starting multi dimension biome scan radius " + radius + " step " + effectiveStep + (save ? " (saving results)" : "")), false);

        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player != null) {
            player.displayClientMessage(Component.literal("Biome scan started (r=" + radius + ", step=" + effectiveStep + ")"), true);
        }

        BiomeScanner.scanAllDimensions(source.getServer(), radius, effectiveStep).thenAccept(result -> {

            StringBuilder saveBuilder = save ? new StringBuilder() : null;
            if (saveBuilder != null) {
                saveBuilder.append("Biome scan results radius=").append(radius).append(" step=").append(effectiveStep).append("\n");
            }

            for (var entry : result.perDimension().entrySet()) {

                ResourceKey<Level> dimKey = entry.getKey();
                BiomeDimensionScanTask.Result dimRes = entry.getValue();

                sendDimensionReport(source, dimKey, dimRes, effectiveStep, saveBuilder);
            }

            if (save && saveBuilder != null) {
                Path saved = persistResults(source, saveBuilder.toString());
                if (saved != null) {
                    source.sendSuccess(() -> Component.literal("Saved biome scan results to " + saved), false);
                    if (player != null) {
                        player.displayClientMessage(Component.literal("Biome scan saved to " + saved.getFileName()), true);
                    }
                }
            } else if (player != null) {
                player.displayClientMessage(Component.literal("Biome scan finished"), true);
            }
        });

        return 1;
    }

    private static void sendDimensionReport(CommandSourceStack source,
                                            ResourceKey<Level> dimKey,
                                            BiomeDimensionScanTask.Result dimRes,
                                            int step,
                                            StringBuilder saveBuilder) {

        int total = dimRes.totalSamples;
        int foundCount = dimRes.found.size();
        int missingCount = dimRes.missing.size();
        int possible = dimRes.possibleBiomeCount;

        source.sendSuccess(() ->
                Component.literal("----- Dimension " + dimKey.location() + " -----"), false);

        source.sendSuccess(() ->
                Component.literal("Biomes " + foundCount + "/" + possible
                        + " (missing " + missingCount + ") | samples " + total
                        + " | step " + step), false);

        if (saveBuilder != null) {
            saveBuilder.append("Dimension: ").append(dimKey.location())
                    .append(" | found ").append(foundCount).append("/").append(possible)
                    .append(" missing ").append(missingCount)
                    .append(" samples ").append(total)
                    .append(" step ").append(step)
                    .append("\n");
        }

        var sorted = dimRes.found.entrySet()
                .stream()
                .sorted((a, b) -> {
                    double da = Math.sqrt(a.getValue().firstFound.getX() * a.getValue().firstFound.getX()
                            + a.getValue().firstFound.getZ() * a.getValue().firstFound.getZ());
                    double db = Math.sqrt(b.getValue().firstFound.getX() * b.getValue().firstFound.getX()
                            + b.getValue().firstFound.getZ() * b.getValue().firstFound.getZ());
                    return Double.compare(da, db);
                })
                .toList();

        int maxShown = 20;
        int index = 0;
        for (var e : sorted) {
            if (index >= maxShown) {
                break;
            }
            var pos = e.getValue().firstFound;
            double dist = Math.sqrt(pos.getX() * pos.getX() + pos.getZ() * pos.getZ());
            int count = e.getValue().count;
            double percent = total > 0 ? (count * 100.0) / total : 0;
            source.sendSuccess(() ->
                    Component.literal(String.format(" - %s @ (%d, %d) dist %.0f, cover %.2f%%",
                            e.getKey(),
                            pos.getX(), pos.getZ(),
                            dist,
                            percent)), false);
            if (saveBuilder != null) {
                saveBuilder.append(String.format("  - %s @ (%d,%d) dist %.0f cover %.2f%%\n",
                        e.getKey(), pos.getX(), pos.getZ(), dist, percent));
            }
            index++;
        }

        if (sorted.size() > maxShown) {
            source.sendSuccess(() ->
                    Component.literal(" - ... " + (sorted.size() - maxShown) + " more biomes"), false);
            if (saveBuilder != null) {
                saveBuilder.append("  ... ").append(sorted.size() - maxShown).append(" more biomes\n");
            }
        }

        if (missingCount > 0) {
            int maxMissing = 10;
            var missingList = dimRes.missing.keySet().stream().sorted().toList();
            source.sendSuccess(() ->
                    Component.literal("Missing: " + joinList(missingList, maxMissing)), false);
            if (saveBuilder != null) {
                saveBuilder.append("  Missing: ").append(joinList(missingList, Integer.MAX_VALUE)).append("\n");
            }
        } else {
            source.sendSuccess(() -> Component.literal("No missing biomes for this dimension."), false);
            if (saveBuilder != null) {
                saveBuilder.append("  No missing biomes\n");
            }
        }
    }

    private static String joinList(java.util.List<net.minecraft.resources.ResourceLocation> list, int max) {
        int size = list.size();
        int take = Math.min(max, size);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(list.get(i));
        }
        if (size > max) {
            builder.append(" ... +").append(size - max).append(" more");
        }
        return builder.toString();
    }

    private static Path persistResults(CommandSourceStack source, String content) {
        try {
            Path dir = source.getServer().getFile("biome_scan_results").toPath();
            Files.createDirectories(dir);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path file = dir.resolve("biome_scan_" + stamp + ".txt");
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to save biome scan results: " + e.getMessage()));
            return null;
        }
    }
}
