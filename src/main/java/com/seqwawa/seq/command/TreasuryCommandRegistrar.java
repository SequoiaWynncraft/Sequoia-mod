package com.seqwawa.seq.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class TreasuryCommandRegistrar {
    private TreasuryCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildCommand(SeqCommand::runTreasuryOut));
    }

    static <S> LiteralArgumentBuilder<S> buildCommand(SeqCommand.TreasuryCommandExecutor<S> executor) {
        RequiredArgumentBuilder<S, String> reasonArgument = RequiredArgumentBuilder
                .<S, String>argument("reason", StringArgumentType.greedyString())
                .executes(ctx -> executor.execute(
                        ctx,
                        new SeqCommand.TreasuryCommandArguments(
                                StringArgumentType.getString(ctx, "amount"),
                                StringArgumentType.getString(ctx, "payouter"),
                                StringArgumentType.getString(ctx, "reason"))));
        RequiredArgumentBuilder<S, String> payouterArgument = RequiredArgumentBuilder
                .<S, String>argument("payouter", StringArgumentType.word())
                .then(reasonArgument);
        RequiredArgumentBuilder<S, String> amountArgument = RequiredArgumentBuilder
                .<S, String>argument("amount", StringArgumentType.word())
                .suggests(SeqCommand::suggestTreasuryAmounts)
                .then(payouterArgument);
        return LiteralArgumentBuilder.<S>literal("treasury")
                .then(LiteralArgumentBuilder.<S>literal("out").then(amountArgument));
    }
}
