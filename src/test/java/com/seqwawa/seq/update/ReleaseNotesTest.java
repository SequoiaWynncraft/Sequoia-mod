package com.seqwawa.seq.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.update.ReleaseNotes.Heading;
import com.seqwawa.seq.update.ReleaseNotes.Item;
import com.seqwawa.seq.update.ReleaseNotes.Paragraph;
import com.seqwawa.seq.update.ReleaseNotes.Span;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Release notes as the update prompt and "What's new" read them from a GitHub release's Markdown. */
class ReleaseNotesTest {
    /** The shape docs/development.md describes, with every part it allows. */
    private static final String NEW_SHAPE = """
            ## Sequoia 1.16.0

            Reroll a daily or weekly quest from its card.
            The quest board shows your newest Leaf first.

            ### New

            - **Rerolls.** Each slot's first reroll of the day is free. Each one after costs 100 Leaf.
              - Point at ↻ to see the cost, and click it twice to swap.
              - Leaf charges exactly the cost the card showed, or nothing.
            - Run `/seq quests` or see [Quests and Leaf](https://example.invalid/quests.md).

            ### Fixed

            - The Recent list shows your latest Leaf again. It showed the oldest of your last 50 events.
            """;

    private static Item item(int depth, Span... spans) {
        return new Item(depth, List.of(spans));
    }

    private static Span plain(String text) {
        return new Span(text, false);
    }

    @Test
    void theSummaryIsThePlainLinesUnderTheReleasesHeading() {
        ReleaseNotes notes = ReleaseNotes.parse(NEW_SHAPE);
        assertEquals(List.of("Reroll a daily or weekly quest from its card.", "The quest board shows your newest Leaf first."),
                notes.summary());
    }

    @Test
    void sectionsItemsSubItemsBoldCodeAndLinksAreRead() {
        ReleaseNotes notes = ReleaseNotes.parse(NEW_SHAPE);
        assertEquals(List.of(
                new Heading("New"),
                item(0, new Span("Rerolls.", true), plain(" Each slot's first reroll of the day is free. Each one after costs 100 Leaf.")),
                item(1, plain("Point at ↻ to see the cost, and click it twice to swap.")),
                item(1, plain("Leaf charges exactly the cost the card showed, or nothing.")),
                item(0, plain("Run /seq quests or see Quests and Leaf.")),
                new Heading("Fixed"),
                item(0, plain("The Recent list shows your latest Leaf again. It showed the oldest of your last 50 events."))),
                notes.blocks());
    }

    @Test
    void notesWithoutASummaryAreTheirItems() {
        ReleaseNotes notes = ReleaseNotes.parse("""
                ## Sequoia 1.14.1

                - Report each player's raid gambits to Sequoia again.
                - Refresh the world map's built-in gathering spots from Wynncraft.
                """);
        assertEquals(List.of(), notes.summary());
        assertEquals(List.of(item(0, plain("Report each player's raid gambits to Sequoia again.")),
                item(0, plain("Refresh the world map's built-in gathering spots from Wynncraft."))), notes.blocks());
    }

    /**
     * Every release's notes read, in either shape: those written before the summary start with an item
     * and have none, those after have one; and every item and sub-item line is an item.
     */
    @Test
    void everyReleasesNotesReadInEitherShape() throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(Path.of("docs", "releases"))) {
            files = listed.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            ReleaseNotes notes = ReleaseNotes.parse(String.join("\n", lines));
            String name = file.getFileName().toString();
            String first = lines.stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("## "))
                    .findFirst()
                    .orElse("");
            assertEquals(first.startsWith("- ") || first.startsWith("### "), notes.summary().isEmpty(), name);
            long items = lines.stream().filter(line -> line.stripLeading().startsWith("- ")).count();
            assertEquals(items, notes.blocks().stream().filter(Item.class::isInstance).count(), name);
        }
    }

    /** A note in the new shape, as the template in docs/development.md has it, reads with its summary and sections. */
    @Test
    void notesInTheNewShapeHaveTheirSummaryAndSections() {
        ReleaseNotes notes = ReleaseNotes.parse("""
                ## Sequoia 1.16.0

                One line on the change players will notice most.
                One line on the next one.

                ### New

                - **Short lead.** What a player can do now.
                  - A detail only some players need.

                ### Fixed

                - What works now.
                """);
        assertEquals(2, notes.summary().size());
        assertEquals(3, notes.blocks().stream().filter(Item.class::isInstance).count());
    }

    /** A bracket in plain text is kept, and a link after it is still read as its text. */
    @Test
    void aLiteralBracketBeforeALinkStaysAsItIs() {
        ReleaseNotes notes = ReleaseNotes.parse("""
                ## Sequoia 1.16.0

                - Show [VIP] tags; see [Ranks](https://example.invalid).
                """);
        assertEquals(List.of(item(0, plain("Show [VIP] tags; see Ranks."))), notes.blocks());
    }

    @Test
    void textAfterTheItemsIsAParagraphAndALineStraightAfterAnItemCarriesItOn() {
        ReleaseNotes notes = ReleaseNotes.parse("""
                ## Sequoia 1.11.1

                - Keep the HQ crown clear of territory
                  and queue labels on the war map.
                - Replace manual HQ assignment
                with observed in-game data.

                This release corrects the publishing setup.
                The versions now agree.

                HQ sharing needs an updated client.
                """);
        assertEquals(List.of(
                item(0, plain("Keep the HQ crown clear of territory and queue labels on the war map.")),
                item(0, plain("Replace manual HQ assignment with observed in-game data.")),
                new Paragraph(List.of(plain("This release corrects the publishing setup. The versions now agree."))),
                new Paragraph(List.of(plain("HQ sharing needs an updated client.")))), notes.blocks());
    }

    @Test
    void windowsLineEndsAndDeeperIndentsAreReadLikeTheRest() {
        ReleaseNotes notes = ReleaseNotes.parse("## Sequoia 1.0.0\r\n\r\nSummary.\r\n\r\n- One\r\n    - Two\r\n\t\t- Three\r\n");
        assertEquals(List.of("Summary."), notes.summary());
        assertEquals(List.of(item(0, plain("One")), item(1, plain("Two")), item(1, plain("Three"))), notes.blocks());
    }

    @Test
    void markersWithoutTheirPairStayAsWritten() {
        assertEquals(List.of(plain("2 ** 3 and __init and a [bracket and `tick")),
                ReleaseNotes.inline("2 ** 3 and __init and a [bracket and `tick"));
        // The pair's second half is code, so the first stays as written; code keeps its text.
        assertEquals(List.of(plain("**not bold ** here")), ReleaseNotes.inline("**not bold `**` here"));
        assertEquals(List.of(plain("a "), new Span("b", true), plain(" c "), new Span("d", true)),
                ReleaseNotes.inline("a **b** c __d__"));
        assertEquals(List.of(plain("literal *stars* and 1. item")), ReleaseNotes.inline("literal \\*stars\\* and 1\\. item"));
    }

    @Test
    void emptyNotesAndNotesWithNoHeadingStillRead() {
        assertSame(ReleaseNotes.EMPTY, ReleaseNotes.parse(null));
        assertSame(ReleaseNotes.EMPTY, ReleaseNotes.parse("  \n\n"));
        assertTrue(ReleaseNotes.parse("## Sequoia 1.0.0\n").isEmpty());
        ReleaseNotes headless = ReleaseNotes.parse("A line on its own.\n\n- An item.");
        assertEquals(List.of("A line on its own."), headless.summary());
        assertEquals(List.of(item(0, plain("An item."))), headless.blocks());
        // A heading deeper than the release's own is a section, even first.
        ReleaseNotes sectioned = ReleaseNotes.parse("### Fixed\n- An item.");
        assertEquals(List.of(new Heading("Fixed"), item(0, plain("An item."))), sectioned.blocks());
    }
}
