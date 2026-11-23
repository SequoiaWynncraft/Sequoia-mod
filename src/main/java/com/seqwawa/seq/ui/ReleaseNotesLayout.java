package com.seqwawa.seq.ui;

import com.seqwawa.seq.update.ReleaseNotes;
import java.util.ArrayList;
import java.util.List;

/**
 * Where each line of a release's notes goes in a column of a given width: the summary, then
 * the section headings, items and sub-items, word-wrapped with each item's later lines under
 * its first word. Drawing and scrolling both use the one layout, measured by the text backend
 * on screen and by a stand-in in tests.
 */
final class ReleaseNotesLayout {
    static final float TEXT_SIZE = 11;
    static final float HEADING_SIZE = 13;
    static final float LINE_HEIGHT = 15;
    static final float HEADING_HEIGHT = 18;
    /** How far each depth of item is indented: its marker sits in this column, its text after. */
    static final float INDENT = 12;
    /** Bold text is drawn twice this far apart, the way Minecraft draws bold. */
    static final float BOLD_OFFSET = 0.6f;
    private static final float GAP_AFTER_SUMMARY = 10;
    private static final float GAP_BEFORE_HEADING = 10;
    private static final float GAP_AFTER_HEADING = 2;
    private static final float GAP_BETWEEN_ITEMS = 4;
    private static final float GAP_BEFORE_SUB_ITEM = 1;
    private static final float GAP_BEFORE_PARAGRAPH = 8;

    /** How wide {@code text} is at {@code size}, in UI pixels. */
    interface Measure {
        float width(String text, float size);
    }

    enum Style { SUMMARY, HEADING, BODY }

    /** The square drawn before an item's first line, by depth. */
    enum Marker { NONE, ITEM, SUB_ITEM }

    /** Text drawn from {@code x}, bold or not. */
    record Run(String text, boolean bold, float x) {}

    /**
     * One line, {@code y} from the top of the notes to the top of the line. Its runs' x and
     * {@code markerX} are from the column's left edge.
     */
    record Line(float y, Style style, Marker marker, float markerX, List<Run> runs) {
        float height() {
            return style == Style.HEADING ? HEADING_HEIGHT : LINE_HEIGHT;
        }

        String text() {
            StringBuilder text = new StringBuilder();
            for (Run run : runs) {
                if (!text.isEmpty()) text.append(' ');
                text.append(run.text());
            }
            return text.toString();
        }
    }

    /** Every line, and how tall the notes are in all. */
    record Layout(List<Line> lines, float height) {}

    private ReleaseNotesLayout() {}

    static Layout layout(ReleaseNotes notes, float width, Measure measure) {
        Builder builder = new Builder(Math.max(1, width), measure);
        for (String line : notes.summary()) {
            builder.wrap(List.of(new ReleaseNotes.Span(line, false)), Style.SUMMARY, Marker.NONE, 0, 0);
        }
        if (!notes.summary().isEmpty() && !notes.blocks().isEmpty()) builder.y += GAP_AFTER_SUMMARY;
        ReleaseNotes.Block previous = null;
        for (ReleaseNotes.Block block : notes.blocks()) {
            switch (block) {
                case ReleaseNotes.Heading heading -> {
                    // The summary's own gap is enough above the first section.
                    if (previous != null) builder.y += GAP_BEFORE_HEADING;
                    // Headings are bold throughout.
                    builder.wrap(List.of(new ReleaseNotes.Span(heading.text(), true)), Style.HEADING, Marker.NONE, 0, 0);
                    builder.y += GAP_AFTER_HEADING;
                }
                case ReleaseNotes.Item item -> {
                    if (previous instanceof ReleaseNotes.Item)
                        builder.y += item.depth() > 0 ? GAP_BEFORE_SUB_ITEM : GAP_BETWEEN_ITEMS;
                    else if (previous instanceof ReleaseNotes.Paragraph) builder.y += GAP_BEFORE_PARAGRAPH;
                    float markerX = item.depth() * INDENT;
                    builder.wrap(item.text(), Style.BODY, item.depth() == 0 ? Marker.ITEM : Marker.SUB_ITEM, markerX,
                            markerX + INDENT);
                }
                case ReleaseNotes.Paragraph paragraph -> {
                    if (builder.y > 0 && !(previous instanceof ReleaseNotes.Heading)) builder.y += GAP_BEFORE_PARAGRAPH;
                    builder.wrap(paragraph.text(), Style.BODY, Marker.NONE, 0, 0);
                }
            }
            previous = block;
        }
        return new Layout(List.copyOf(builder.lines), builder.y);
    }

    /** A word and whether a space came before it. */
    private record Word(String text, boolean bold, boolean spaced) {}

    private static final class Builder {
        final float width;
        final Measure measure;
        final List<Line> lines = new ArrayList<>();
        float y;

        Builder(float width, Measure measure) {
            this.width = width;
            this.measure = measure;
        }

        float size(Style style) {
            return style == Style.HEADING ? HEADING_SIZE : TEXT_SIZE;
        }

        float wordWidth(String text, boolean bold, float size) {
            return measure.width(text, size) + (bold ? BOLD_OFFSET : 0);
        }

        /** Lays {@code spans} out from {@code textX}, wrapping within the column; the marker goes on the first line. */
        void wrap(List<ReleaseNotes.Span> spans, Style style, Marker marker, float markerX, float textX) {
            float size = size(style);
            // A trailing space has no ink, so it is measured between two letters.
            float space = Math.max(0, measure.width("x x", size) - measure.width("xx", size));
            float room = Math.max(1, width - textX);
            List<Run> runs = new ArrayList<>();
            StringBuilder run = new StringBuilder();
            boolean runBold = false;
            float runX = 0;
            float x = 0;
            boolean first = true;
            for (Word word : words(spans)) {
                for (String piece : pieces(word, room, size)) {
                    float w = wordWidth(piece, word.bold(), size);
                    float gap = x > 0 && word.spaced() ? space : 0;
                    if (x > 0 && x + gap + w > room) {
                        if (!run.isEmpty()) runs.add(new Run(run.toString(), runBold, textX + runX));
                        add(style, first ? marker : Marker.NONE, markerX, runs);
                        first = false;
                        runs = new ArrayList<>();
                        run.setLength(0);
                        x = 0;
                        gap = 0;
                    }
                    if (!run.isEmpty() && runBold == word.bold()) {
                        if (gap > 0) run.append(' ');
                    } else {
                        if (!run.isEmpty()) runs.add(new Run(run.toString(), runBold, textX + runX));
                        run.setLength(0);
                        runBold = word.bold();
                        runX = x + gap;
                    }
                    run.append(piece);
                    x += gap + w;
                }
            }
            if (!run.isEmpty()) runs.add(new Run(run.toString(), runBold, textX + runX));
            if (!runs.isEmpty() || first) add(style, first ? marker : Marker.NONE, markerX, runs);
        }

        void add(Style style, Marker marker, float markerX, List<Run> runs) {
            Line line = new Line(y, style, marker, markerX, List.copyOf(runs));
            lines.add(line);
            y += line.height();
        }

        /** A word longer than a whole line, such as a link, broken into pieces that each fit. */
        List<String> pieces(Word word, float room, float size) {
            String text = word.text();
            if (wordWidth(text, word.bold(), size) <= room) return List.of(text);
            List<String> pieces = new ArrayList<>();
            int start = 0;
            while (start < text.length()) {
                int end = start + 1;
                while (end < text.length() && wordWidth(text.substring(start, end + 1), word.bold(), size) <= room) end++;
                pieces.add(text.substring(start, end));
                start = end;
            }
            return pieces;
        }
    }

    /** The words of {@code spans}, each with its weight; runs of spaces count as one. */
    private static List<Word> words(List<ReleaseNotes.Span> spans) {
        List<Word> words = new ArrayList<>();
        boolean spaced = false;
        for (ReleaseNotes.Span span : spans) {
            String[] parts = span.text().split(" ", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) spaced = true;
                if (parts[i].isEmpty()) continue;
                words.add(new Word(parts[i], span.bold(), spaced));
                spaced = false;
            }
        }
        return words;
    }
}
