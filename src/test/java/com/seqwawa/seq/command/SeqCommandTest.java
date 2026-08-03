package com.seqwawa.seq.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

class SeqCommandTest {

    @Test
    void commandTreeMatchesCompatibilitySnapshot() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();

        SeqCommand.registerCommands(dispatcher, null);

        assertEquals(
                resourceText("/snapshots/seq-command-tree.txt").strip(),
                commandTreeSnapshot(dispatcher.getRoot()).strip());
    }

    @Test
    void registersStandaloneEmeraldAlias() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();

        SeqCommand.registerCommands(dispatcher, null);

        assertNotNull(dispatcher.getRoot().getChild("e"));
        assertNotNull(dispatcher.getRoot().getChild("seq").getChild("e"));
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

    private static <S> String commandTreeSnapshot(CommandNode<S> root) {
        StringBuilder snapshot = new StringBuilder();
        appendChildren(snapshot, root, "");
        return snapshot.toString();
    }

    private static <S> void appendChildren(StringBuilder snapshot, CommandNode<S> parent, String parentPath) {
        List<CommandNode<S>> children = new ArrayList<>(parent.getChildren());
        children.sort(Comparator.comparing(CommandNode::getName));
        for (CommandNode<S> child : children) {
            String segment = commandSegment(child);
            String path = parentPath.isEmpty() ? segment : parentPath + " " + segment;
            snapshot.append(path)
                    .append(" | executes=")
                    .append(child.getCommand() != null)
                    .append(" | redirects=")
                    .append(child.getRedirect() != null)
                    .append('\n');
            appendChildren(snapshot, child, path);
        }
    }

    private static String commandSegment(CommandNode<?> node) {
        if (node instanceof LiteralCommandNode<?>) {
            return node.getName();
        }
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            String type = argument.getType().toString();
            if (argument.getType() instanceof StringArgumentType stringArgument) {
                type = switch (stringArgument.getType()) {
                    case SINGLE_WORD -> "word";
                    case QUOTABLE_PHRASE -> "string";
                    case GREEDY_PHRASE -> "greedyString";
                };
            }
            return "<" + node.getName() + ":" + type + ">";
        }
        return "<" + node.getName() + ":" + node.getClass().getSimpleName() + ">";
    }

    private static String resourceText(String path) {
        try (InputStream stream = SeqCommandTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read test resource " + path, exception);
        }
    }
}
