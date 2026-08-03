package com.seqwawa.seq.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class MapCommandRegistrar {
    private MapCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildMapCommand());
        root.then(ClientCommandManager.literal("ingredients").executes(SeqCommand::openIngredientGuideScreen));
        root.then(ClientCommandManager.literal("ingredient").executes(SeqCommand::openIngredientGuideScreen));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildMapCommand() {
        return ClientCommandManager.literal("map")
                .executes(SeqCommand::openWorldMapScreen)
                .then(ClientCommandManager.literal("params").executes(SeqCommand::runMapParams))
                .then(ClientCommandManager.literal("eps")
                        .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 500))
                                .executes(SeqCommand::runMapClusterEps)))
                .then(buildMapMinSamplesCommand("minSamples"))
                .then(ClientCommandManager.literal("reset").executes(SeqCommand::runMapClusterReset))
                .then(ClientCommandManager.literal("debug").executes(SeqCommand::runMapDebugToggle))
                .then(ClientCommandManager.literal("cache")
                        .executes(SeqCommand::runMapClusterCacheStatus)
                        .then(ClientCommandManager.literal("status")
                                .executes(SeqCommand::runMapClusterCacheStatus))
                        .then(ClientCommandManager.literal("cluster")
                                .executes(SeqCommand::runMapClusterCacheStatus)
                                .then(ClientCommandManager.literal("status")
                                        .executes(SeqCommand::runMapClusterCacheStatus))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(SeqCommand::runMapClusterCacheClear)))
                        .then(ClientCommandManager.literal("map")
                                .executes(SeqCommand::runMapImageCacheStatus)
                                .then(ClientCommandManager.literal("status")
                                        .executes(SeqCommand::runMapImageCacheStatus))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(SeqCommand::runMapImageCacheClear))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildMapMinSamplesCommand(String literalName) {
        return ClientCommandManager.literal(literalName)
                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(SeqCommand::runMapClusterMinSamples));
    }
}
