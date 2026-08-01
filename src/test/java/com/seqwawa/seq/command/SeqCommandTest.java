package com.seqwawa.seq.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SeqCommandTest {

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
