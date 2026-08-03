package com.seqwawa.seq.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.PartyRole;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class PartyCommandRegistrar {
    private PartyCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildPartyCommand("party"));
        root.then(buildPartyCommand("p"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildPartyCommand(String literalName) {
        return ClientCommandManager.literal(literalName)
                .executes(ctx -> SeqCommand.openPartyScreen())
                .then(ClientCommandManager.literal("create-ui")
                        .executes(ctx -> SeqCommand.openPartyCreateScreen()))
                .then(ClientCommandManager.literal("list").executes(SeqCommand::runPartyList))
                .then(ClientCommandManager.literal("status").executes(SeqCommand::runPartyStatus))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("activities", StringArgumentType.greedyString())
                                .suggests(SeqCommand::suggestActivities)
                                .executes(SeqCommand::runPartyCreate)))
                .then(ClientCommandManager.literal("update")
                        .then(ClientCommandManager.argument("activities", StringArgumentType.greedyString())
                                .suggests(SeqCommand::suggestActivities)
                                .executes(SeqCommand::runPartyUpdate)))
                .then(buildPartyJoinCommand())
                .then(ClientCommandManager.literal("deny")
                        .then(ClientCommandManager.argument("listingId", LongArgumentType.longArg(1))
                                .executes(SeqCommand::runPartyDeny)))
                .then(ClientCommandManager.literal("leave")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().leavePartyFromCommand())))
                .then(ClientCommandManager.literal("invite")
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                                .executes(SeqCommand::runPartyInvite)))
                .then(ClientCommandManager.literal("reserve")
                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0))
                                .executes(SeqCommand::runPartyReserve)))
                .then(ClientCommandManager.literal("open")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().reopenPartyFromCommand())))
                .then(ClientCommandManager.literal("close")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().closePartyFromCommand())))
                .then(ClientCommandManager.literal("extend")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().extendPartyFromCommand()))
                        .then(ClientCommandManager.argument("listingId", LongArgumentType.longArg(1))
                                .executes(ctx -> SeqCommand.relayCommandResult(
                                        ctx,
                                        SeqClient.getPartyFinderManager().extendPartyFromCommand(
                                                LongArgumentType.getLong(ctx, "listingId"))))))
                .then(ClientCommandManager.literal("disband")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().disbandPartyFromCommand())))
                .then(ClientCommandManager.literal("role")
                        .then(ClientCommandManager.argument("role", StringArgumentType.word())
                                .suggests(SeqCommand::suggestRoles)
                                .executes(SeqCommand::runPartyRole)))
                .then(ClientCommandManager.literal("kick")
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                                .executes(SeqCommand::runPartyKick)))
                .then(ClientCommandManager.literal("promote")
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                                .executes(SeqCommand::runPartyPromote)))
                .then(ClientCommandManager.literal("invite-all")
                        .executes(ctx -> SeqCommand.relayCommandResult(
                                ctx, SeqClient.getPartyFinderManager().inviteAllCurrentMembersFromCommand())));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildPartyJoinCommand() {
        return ClientCommandManager.literal("join")
                .then(ClientCommandManager.argument("listingId", LongArgumentType.longArg(1))
                        .executes(ctx -> SeqCommand.runPartyJoin(ctx, PartyRole.DPS, null))
                        .then(ClientCommandManager.literal("token")
                                .then(ClientCommandManager.argument("inviteToken", StringArgumentType.string())
                                        .executes(SeqCommand::runPartyJoinWithToken)))
                        .then(ClientCommandManager.argument("role", StringArgumentType.word())
                                .suggests(SeqCommand::suggestRoles)
                                .executes(SeqCommand::runPartyJoinWithRole)
                                .then(ClientCommandManager.literal("token")
                                        .then(ClientCommandManager.argument(
                                                        "inviteToken", StringArgumentType.string())
                                                .executes(SeqCommand::runPartyJoinWithRoleAndToken)))));
    }
}
