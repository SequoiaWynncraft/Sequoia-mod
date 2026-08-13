package com.seqwawa.seq.wynnbuilder.ui;

import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.awt.Color;

/**
 * One row of a statistics panel.
 *
 * <p>Rows carry their own height so a panel can breathe: headings reserve space above themselves,
 * dividers are thin, and spacers separate groups. A single fixed line height made every panel read
 * as one undifferentiated block.
 */
public record StatLine(
        String label,
        String value,
        Color color,
        Kind kind,
        Runnable onClick,
        java.util.List<Chip> chips,
        Slider slider) {

    public StatLine(String label, String value, Color color, Kind kind, Runnable onClick) {
        this(label, value, color, kind, onClick, java.util.List.of(), null);
    }

    /** One selectable pill in a wrapped row. */
    public record Chip(String label, boolean active, Runnable onClick) {}

    /** A value the player drags or steps between zero and a maximum. */
    public record Slider(int value, int maximum, java.util.function.IntConsumer onChange) {}

    /** How a row is drawn, which also decides how much vertical space it takes. */
    public enum Kind {
        /** A section title, with space above it and a rule beneath. */
        HEADING(26),
        /** A group label inside a section. */
        SUBHEADING(19),
        /** A label and its value. */
        TEXT(15),
        /** A horizontal rule separating entries. */
        DIVIDER(11),
        /** Empty space. */
        SPACER(8),
        /** A clickable button. */
        BUTTON(24),
        /** A clickable title that folds a section. */
        EXPANDER(21),
        /** A foldable row with a value on the right. */
        EXPANDER_VALUE(18),
        /** A wrapped row of selectable pills; its height depends on how many fit. */
        CHIPS(0),
        /** A labelled slider with a track. */
        SLIDER(34);

        private final float height;

        Kind(float height) {
            this.height = height;
        }

        public float height() {
            return height;
        }
    }

    /** Height of one row of chips; a wrapped chip row may occupy several. */
    public static final float CHIP_ROW_HEIGHT = 22;

    /**
     * The fixed height of this row.
     *
     * <p>Chip rows are the exception: they wrap, so only the renderer knows how tall they are once
     * it knows the width. {@link StatLineRenderer} measures those.
     */
    public float height() {
        return kind.height();
    }

    public boolean clickable() {
        return onClick != null;
    }

    public static StatLine heading(String label) {
        return new StatLine(label, "", null, Kind.HEADING, null);
    }

    public static StatLine subheading(String label) {
        return new StatLine(label, "", null, Kind.SUBHEADING, null);
    }

    public static StatLine text(String label, String value, Color color) {
        return new StatLine(label, value, color, Kind.TEXT, null);
    }

    public static StatLine divider() {
        return new StatLine("", "", null, Kind.DIVIDER, null);
    }

    public static StatLine spacer() {
        return new StatLine("", "", null, Kind.SPACER, null);
    }

    public static StatLine button(String label, Runnable onClick) {
        return new StatLine(label, "", null, Kind.BUTTON, onClick);
    }

    /** A foldable section title. A {@code null} tier means "no rarity colour". */
    /**
     * A wrapped row of selectable pills.
     *
     * <p>Named apart from the {@code chips} accessor so the static factory and the record component
     * cannot be confused at a call site.
     */
    /** A foldable row that shows a value on the right, used for damage sources. */
    public static StatLine expanderValue(String label, String value, Color color, Runnable onClick) {
        return new StatLine(label, value, color, Kind.EXPANDER_VALUE, onClick, java.util.List.of(), null);
    }

    public static StatLine chipRow(java.util.List<Chip> chips) {
        return new StatLine("", "", null, Kind.CHIPS, null, java.util.List.copyOf(chips), null);
    }

    /** A labelled value the player steps between zero and a maximum. */
    public static StatLine sliderRow(String label, int value, int maximum,
            java.util.function.IntConsumer onChange) {
        return new StatLine(label, "", null, Kind.SLIDER, null, java.util.List.of(),
                new Slider(value, maximum, onChange));
    }

    public static StatLine expander(String label, WynnItem.Tier tier, Runnable onClick) {
        return new StatLine(label, "", tier == null ? null : WynnBuilderUi.rarityColor(tier), Kind.EXPANDER, onClick);
    }
}
