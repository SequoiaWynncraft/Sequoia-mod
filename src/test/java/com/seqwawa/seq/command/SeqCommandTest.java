package com.seqwawa.seq.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.client.SeqClient;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

class SeqCommandTest {

    @Test
    void parsesOtherPartyRole() {
        assertEquals(PartyRole.OTHER, SeqCommand.parseRole("other"));
        assertEquals(PartyRole.OTHER, SeqCommand.parseRole(" OTHER "));
    }

    @Test
    void registersStandaloneEmeraldAlias() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();

        SeqCommand.registerCommands(dispatcher, null);

        assertNotNull(dispatcher.getRoot().getChild("e"));
        assertNotNull(dispatcher.getRoot().getChild("seq").getChild("e"));
    }

    @Test
    void registersStandaloneThirtyAspectAlias() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();

        SeqCommand.registerCommands(dispatcher, null);

        assertNotNull(dispatcher.getRoot().getChild("a"));
        assertNotNull(dispatcher.getRoot().getChild("a").getCommand());
    }

    @Test
    void warSubtreeExistsButFailsClosedBeforeAuthorizedSnapshot() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        var previous = SeqClient.warPlannerManager;
        try {
            SeqClient.warPlannerManager = null;
            SeqCommand.registerCommands(dispatcher, null);

            var war = dispatcher.getRoot().getChild("seq").getChild("war");
            assertNotNull(war);
            assertFalse(war.canUse(null));
        } finally {
            SeqClient.warPlannerManager = previous;
        }
    }

    @Test
    void normalizesSequoiaCommandLiteralsWithoutChangingArguments() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        SeqCommand.registerCommands(dispatcher, null);

        assertEquals("seq aspects 30", SeqCommand.normalizeCommandCapitalization("SeQ AsPeCtS 30"));
        assertEquals(
                "seq party invite Status",
                SeqCommand.normalizeCommandCapitalization("SEQ PARTY INVITE Status"));
        assertEquals(
                "seq party join 42 DPS token AbCdEf",
                SeqCommand.normalizeCommandCapitalization("SEQ PARTY JOIN 42 DPS TOKEN AbCdEf"));
        assertEquals("a", SeqCommand.normalizeCommandCapitalization("A"));
    }

    @Test
    void leavesNonSequoiaCommandsUntouched() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        SeqCommand.registerCommands(dispatcher, null);

        assertEquals("Party INVITE Status", SeqCommand.normalizeCommandCapitalization("Party INVITE Status"));
    }

    @Test
    void treasuryOutParsesMultiWordReasonGreedily() throws Exception {
        AtomicReference<SeqCommand.TreasuryCommandArguments> captured = new AtomicReference<>();
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("seq")
                .then(SeqCommand.buildTreasuryCommand((context, arguments) -> {
                    captured.set(arguments);
                    return 1;
                })));

        assertEquals(1, dispatcher.execute(
                "seq treasury out 2stx5le+1stx5le+4stx4le Solo season payout", new Object()));
        assertEquals("2stx5le+1stx5le+4stx4le", captured.get().amount());
        assertEquals("Solo", captured.get().payouter());
        assertEquals("season payout", captured.get().reason());
    }

    @Test
    void treasuryOutAcceptsBackendNormalizedAmountForms() throws Exception {
        AtomicReference<SeqCommand.TreasuryCommandArguments> captured = new AtomicReference<>();
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("seq")
                .then(SeqCommand.buildTreasuryCommand((context, arguments) -> {
                    captured.set(arguments);
                    return 1;
                })));

        assertEquals(1, dispatcher.execute("seq treasury out 50 Solo plain le amount", new Object()));
        assertEquals("50", captured.get().amount());

        assertEquals(1, dispatcher.execute("seq treasury out 50s Solo stack alias", new Object()));
        assertEquals("50s", captured.get().amount());
    }
}
