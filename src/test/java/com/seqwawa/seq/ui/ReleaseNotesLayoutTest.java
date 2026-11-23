package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.ReleaseNotesLayout.Line;
import com.seqwawa.seq.ui.ReleaseNotesLayout.Marker;
import com.seqwawa.seq.ui.ReleaseNotesLayout.Run;
import com.seqwawa.seq.ui.ReleaseNotesLayout.Style;
import com.seqwawa.seq.update.ReleaseNotes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Where "What's new" puts each line of a release's notes, at any width. */
class ReleaseNotesLayoutTest {
    /** Every character, space included, 5 pixels wide at the body's size. */
    private static final ReleaseNotesLayout.Measure FIXED = (text, size) -> text.length() * 5 * size / ReleaseNotesLayout.TEXT_SIZE;

    private static final ReleaseNotes NOTES = ReleaseNotes.parse("""
            ## Sequoia 1.16.0

            Reroll a quest from its card.

            ### New

            - **Rerolls.** The first one each day is free.
              - Click it twice to swap.
            - Shorter.
            """);

    private static List<Line> lines(ReleaseNotes notes, float width) {
        return ReleaseNotesLayout.layout(notes, width, FIXED).lines();
    }

    @Test
    void theSummaryComesFirstThenEachSectionWithItsItems() {
        List<Line> lines = lines(NOTES, 1000);
        assertEquals(List.of(Style.SUMMARY, Style.HEADING, Style.BODY, Style.BODY, Style.BODY),
                lines.stream().map(Line::style).toList());
        assertEquals(List.of("Reroll a quest from its card.", "New", "Rerolls. The first one each day is free.",
                "Click it twice to swap.", "Shorter."), lines.stream().map(Line::text).toList());
        assertEquals(List.of(Marker.NONE, Marker.NONE, Marker.ITEM, Marker.SUB_ITEM, Marker.ITEM),
                lines.stream().map(Line::marker).toList());
        // One gap between the summary and the first section, not two.
        assertEquals(ReleaseNotesLayout.LINE_HEIGHT + 10, lines.get(1).y());
    }

    @Test
    void boldRunsAreKeptApartAndPlacedAfterTheMarker() {
        Line item = lines(NOTES, 1000).get(2);
        float bold = "Rerolls.".length() * 5 + ReleaseNotesLayout.BOLD_OFFSET;
        assertEquals(List.of(new Run("Rerolls.", true, ReleaseNotesLayout.INDENT),
                new Run("The first one each day is free.", false, ReleaseNotesLayout.INDENT + (bold + 5))), item.runs());
        Line sub = lines(NOTES, 1000).get(3);
        assertEquals(ReleaseNotesLayout.INDENT, sub.markerX());
        assertEquals(2 * ReleaseNotesLayout.INDENT, sub.runs().getFirst().x());
    }

    @Test
    void longItemsWrapUnderTheirFirstWordWithTheMarkerOnlyOnTheFirstLine() {
        // Room for 13 characters after the marker's column.
        float width = ReleaseNotesLayout.INDENT + 13 * 5;
        List<Line> lines = lines(NOTES, width);
        List<Line> item = lines.stream().filter(line -> line.style() == Style.BODY).toList().subList(0, 4);
        assertEquals(List.of("Rerolls. The", "first one", "each day is", "free."), item.stream().map(Line::text).toList());
        assertEquals(List.of(Marker.ITEM, Marker.NONE, Marker.NONE, Marker.NONE), item.stream().map(Line::marker).toList());
        for (Line line : item) assertEquals(ReleaseNotesLayout.INDENT, line.runs().getFirst().x());
        for (Line line : lines) {
            for (Run run : line.runs()) assertTrue(run.x() + run.text().length() * 5 <= width + 1, run.toString());
        }
    }

    @Test
    void aWordLongerThanTheColumnIsBrokenIntoPiecesThatFit() {
        ReleaseNotes notes = ReleaseNotes.parse("- https://example.invalid/a/very/long/path");
        List<Line> lines = lines(notes, ReleaseNotesLayout.INDENT + 10 * 5);
        assertEquals(List.of("https://ex", "ample.inva", "lid/a/very", "/long/path"), lines.stream().map(Line::text).toList());
    }

    @Test
    void linesFollowOneAnotherAndTheHeightCoversThemAll() {
        ReleaseNotesLayout.Layout layout = ReleaseNotesLayout.layout(NOTES, 1000, FIXED);
        float bottom = 0;
        for (Line line : layout.lines()) {
            assertTrue(line.y() >= bottom, line.toString());
            bottom = line.y() + line.height();
        }
        assertEquals(bottom, layout.height());
        assertEquals(ReleaseNotesLayout.HEADING_HEIGHT, layout.lines().get(1).height());
    }

    @Test
    void notesWithoutASummaryStartWithTheirItems() {
        ReleaseNotes notes = ReleaseNotes.parse("## Sequoia 1.14.1\n\n- One.\n- Two.");
        List<Line> lines = lines(notes, 1000);
        assertEquals(0, lines.getFirst().y());
        assertEquals(List.of("One.", "Two."), lines.stream().map(Line::text).toList());
    }
}
