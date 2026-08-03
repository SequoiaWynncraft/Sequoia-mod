package com.seqwawa.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class RewardCommandRegistrar {
    private RewardCommandRegistrar() {}

    static void registerRewards(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildRequestCommand());
        root.then(buildEmeraldRewardCommand("e"));
        root.then(buildEmeraldRewardCommand("emeralds"));
        root.then(buildAspectRewardCommand());
        root.then(buildTomeRewardCommand());
        root.then(buildBombCommand());
    }

    static void registerBadges(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildBadgeCommand("badges"));
        root.then(buildBadgeCommand("badge"));
    }

    static void registerStandaloneAliases(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildEmeraldRewardCommand("e"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRequestCommand() {
        return ClientCommandManager.literal("request")
                .then(ClientCommandManager.literal("aspects")
                        .executes(ctx -> SeqCommand.runRewardQueueRequest(ctx, "aspect", null)))
                .then(ClientCommandManager.literal("tome")
                        .then(ClientCommandManager.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> SeqCommand.runRewardQueueRequest(
                                        ctx,
                                        "tome",
                                        StringArgumentType.getString(ctx, "reason")))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildEmeraldRewardCommand(String literalName) {
        return ClientCommandManager.literal(literalName)
                .executes(ctx -> {
                    SeqClient.getGuildRewardAutomationManager().sendAllEmeraldsToCinfrascitizen();
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildTomeRewardCommand() {
        return ClientCommandManager.literal("tome")
                .executes(ctx -> SeqCommand.runQueuedGuildReward(
                        ctx, GuildRewardAutomationManager.RewardType.TOME, 1))
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .executes(ctx -> SeqCommand.runDirectTomeReward(
                                ctx, StringArgumentType.getString(ctx, "username"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildAspectRewardCommand() {
        return ClientCommandManager.literal("aspects")
                .then(ClientCommandManager.argument("amount", LongArgumentType.longArg(1))
                        .executes(SeqCommand::runQueuedAspectReward)
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                                .executes(SeqCommand::runDirectAspectReward)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildBombCommand() {
        return ClientCommandManager.literal("bomb")
                .then(ClientCommandManager.literal("_share")
                        .then(ClientCommandManager.argument("requestId", StringArgumentType.word())
                                .executes(ctx -> SeqClient.getBombShareManager().sharePrompt(
                                        StringArgumentType.getString(ctx, "requestId")))))
                .then(ClientCommandManager.literal("_mute-requests")
                        .executes(ctx -> SeqClient.getBombShareManager().muteRequests()))
                .then(ClientCommandManager.argument("selectors", StringArgumentType.greedyString())
                        .suggests(SeqCommand::suggestBombSelectors)
                        .executes(ctx -> SeqClient.getBombShareManager().requestBombShare(
                                StringArgumentType.getString(ctx, "selectors"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildBadgeCommand(String literalName) {
        return ClientCommandManager.literal(literalName)
                .executes(SeqCommand::runBadgeStatus)
                .then(ClientCommandManager.literal("status").executes(SeqCommand::runBadgeStatus))
                .then(ClientCommandManager.literal("refresh").executes(SeqCommand::runBadgeRefresh));
    }
}
