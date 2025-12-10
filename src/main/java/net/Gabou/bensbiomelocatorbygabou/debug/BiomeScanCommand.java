package net.Gabou.bensbiomelocatorbygabou.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class BiomeScanCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("biome_scan_all")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.argument("radius", IntegerArgumentType.integer(100, 60000))
                        .executes(ctx -> runScan(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius")))
                )
        );
    }

    private static int runScan(CommandSourceStack source, int radius) {

        int step = 16;

        source.sendSuccess(() ->
                Component.literal("Starting multi dimension biome scan radius " + radius), false);

        BiomeScanner.scanAllDimensions(source.getServer(), radius, step).thenAccept(result -> {

            for (var entry : result.perDimension().entrySet()) {

                ResourceKey<Level> dimKey = entry.getKey();
                BiomeDimensionScanTask.Result dimRes = entry.getValue();

                source.sendSuccess(() ->
                        Component.literal("----- Dimension " + dimKey.location() + " -----"), false);

                int total = dimRes.totalSamples;
                int foundCount = dimRes.found.size();
                int missingCount = dimRes.missing.size();

                source.sendSuccess(() ->
                        Component.literal("Found " + foundCount + " biomes out of " + (foundCount + missingCount)), false);

                dimRes.found.entrySet()
                        .stream()
                        .sorted((a, b) -> {
                            double da = Math.sqrt(a.getValue().firstFound.getX() * a.getValue().firstFound.getX()
                                    + a.getValue().firstFound.getZ() * a.getValue().firstFound.getZ());
                            double db = Math.sqrt(b.getValue().firstFound.getX() * b.getValue().firstFound.getX()
                                    + b.getValue().firstFound.getZ() * b.getValue().firstFound.getZ());
                            return Double.compare(da, db);
                        })
                        .forEach(e -> {
                            var pos = e.getValue().firstFound;
                            double dist = Math.sqrt(pos.getX() * pos.getX() + pos.getZ() * pos.getZ());
                            int count = e.getValue().count;
                            double percent = (count * 100.0) / total;

                            source.sendSuccess(() ->
                                    Component.literal("Biome " + e.getKey()
                                            + " at " + pos
                                            + " distance " + String.format("%.0f", dist)
                                            + " coverage " + String.format("%.2f", percent) + " percent"), false);
                        });

                source.sendSuccess(() ->
                        Component.literal("--- Missing biomes ---"), false);

                for (var miss : dimRes.missing.keySet()) {
                    source.sendSuccess(() ->
                            Component.literal("Missing " + miss), false);
                }
            }
        });

        return 1;
    }
}
