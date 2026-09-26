package com.seqwawa.seq.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Flattens a chat {@link Component} into styled text fragments so callers can
 * edit it by character index and rebuild it without losing styles, hover text,
 * click actions or shift-click insertions.
 */
public final class ComponentTextEditor {

    private ComponentTextEditor() {}

    /** Splits a component into its styled leaves, in display order. */
    public static List<Fragment> flatten(Component component) {
        if (component == null) {
            return List.of();
        }

        List<Fragment> fragments = new ArrayList<>();
        for (Component leaf : component.toFlatList()) {
            String text = leaf.getString();
            if (!text.isEmpty()) {
                fragments.add(new Fragment(text, leaf.getStyle()));
            }
        }
        return fragments;
    }

    /** The full visible text of {@code fragments}, whose indices the editors below use. */
    public static String textOf(List<Fragment> fragments) {
        StringBuilder text = new StringBuilder();
        for (Fragment fragment : fragments) {
            text.append(fragment.text());
        }
        return text.toString();
    }

    /**
     * Rebuilds {@code fragments} into a component, each leaf keeping its own style.
     * <p>
     * For callers that only restyle, since {@link #replaceRange} rebuilds as a side
     * effect of splicing and so needs a replacement it has nothing to put.
     */
    public static MutableComponent toComponent(List<Fragment> fragments) {
        MutableComponent rebuilt = Component.empty();
        if (fragments != null) {
            for (Fragment fragment : fragments) {
                appendIfPresent(rebuilt, fragment.text(), fragment.style());
            }
        }
        return rebuilt;
    }

    /**
     * Rebuilds {@code fragments} with {@code [start, endExclusive)} swapped for
     * {@code replacement}. Fragments straddling a boundary are split, keeping their
     * style on both halves.
     *
     * @return the rebuilt component, or {@code null} when the range is invalid
     */
    public static MutableComponent replaceRange(
            List<Fragment> fragments, int start, int endExclusive, Component replacement) {
        if (fragments == null || replacement == null || start < 0 || endExclusive < start) {
            return null;
        }

        MutableComponent rebuilt = Component.empty();
        boolean replacementInserted = false;
        int cursor = 0;

        for (Fragment fragment : fragments) {
            int fragmentStart = cursor;
            int fragmentEnd = cursor + fragment.text().length();
            cursor = fragmentEnd;

            if (fragmentEnd <= start || fragmentStart >= endExclusive) {
                appendIfPresent(rebuilt, fragment.text(), fragment.style());
                continue;
            }

            appendIfPresent(rebuilt, fragment.text().substring(0, Math.max(0, start - fragmentStart)), fragment.style());
            if (!replacementInserted) {
                rebuilt.append(replacement);
                replacementInserted = true;
            }
            if (fragmentEnd > endExclusive) {
                appendIfPresent(rebuilt, fragment.text().substring(endExclusive - fragmentStart), fragment.style());
            }
        }

        return replacementInserted ? rebuilt : null;
    }

    /**
     * Applies {@code styleMapper} to the fragments covering
     * {@code [start, endExclusive)}, splitting any that straddle a boundary. Text is
     * untouched, so indices computed against the original fragments stay valid and a
     * caller can restyle first and then {@link #replaceRange} using the same offsets.
     */
    public static List<Fragment> restyleRange(
            List<Fragment> fragments, int start, int endExclusive, UnaryOperator<Style> styleMapper) {
        if (fragments == null || styleMapper == null || start < 0 || endExclusive <= start) {
            return fragments;
        }

        List<Fragment> restyled = new ArrayList<>();
        int cursor = 0;
        for (Fragment fragment : fragments) {
            int fragmentStart = cursor;
            int fragmentEnd = cursor + fragment.text().length();
            cursor = fragmentEnd;

            if (fragmentEnd <= start || fragmentStart >= endExclusive) {
                restyled.add(fragment);
                continue;
            }

            String text = fragment.text();
            int localStart = Math.max(0, start - fragmentStart);
            int localEnd = Math.min(text.length(), endExclusive - fragmentStart);
            addIfPresent(restyled, text.substring(0, localStart), fragment.style());
            addIfPresent(restyled, text.substring(localStart, localEnd), styleMapper.apply(fragment.style()));
            addIfPresent(restyled, text.substring(localEnd), fragment.style());
        }
        return List.copyOf(restyled);
    }

    /**
     * Applies a position-dependent style across {@code [start, endExclusive)}. The
     * position runs from {@code 0} on the first code point to {@code 1} on the last,
     * which lets callers paint a gradient without breaking surrogate pairs.
     * <p>
     * Every code point in the range becomes its own fragment because Minecraft gives a
     * component leaf only one colour. Styling outside the range, and styling other than
     * what {@code styleMapper} changes, is preserved.
     */
    public static List<Fragment> restyleRangeByPosition(
            List<Fragment> fragments,
            int start,
            int endExclusive,
            BiFunction<Style, Double, Style> styleMapper) {
        if (fragments == null || styleMapper == null || start < 0 || endExclusive <= start) {
            return fragments;
        }
        int codePointCount = codePointCount(fragments, start, endExclusive);
        return restyleRangeByCodePoint(fragments, start, endExclusive, (style, index) -> styleMapper.apply(
                style, codePointCount <= 1 ? 0d : (double) index / (codePointCount - 1)));
    }

    /** Styles one code point, given its style so far and its index within the range. */
    @FunctionalInterface
    public interface CodePointStyler {
        Style apply(Style style, int index);
    }

    /**
     * Applies a style per code point across {@code [start, endExclusive)}, each styled
     * with its index in the range, and each becoming its own fragment because Minecraft
     * gives a component leaf only one colour. Styling outside the range, and styling
     * other than what {@code styler} changes, is preserved.
     */
    public static List<Fragment> restyleRangeByCodePoint(
            List<Fragment> fragments,
            int start,
            int endExclusive,
            CodePointStyler styler) {
        if (fragments == null || styler == null || start < 0 || endExclusive <= start) {
            return fragments;
        }

        int codePointCount = codePointCount(fragments, start, endExclusive);
        if (codePointCount == 0) {
            return fragments;
        }

        List<Fragment> restyled = new ArrayList<>();
        int cursor = 0;
        int styledIndex = 0;
        for (Fragment fragment : fragments) {
            int fragmentStart = cursor;
            int fragmentEnd = cursor + fragment.text().length();
            cursor = fragmentEnd;

            if (fragmentEnd <= start || fragmentStart >= endExclusive) {
                restyled.add(fragment);
                continue;
            }

            String text = fragment.text();
            int localStart = Math.max(0, start - fragmentStart);
            int localEnd = Math.min(text.length(), endExclusive - fragmentStart);
            addIfPresent(restyled, text.substring(0, localStart), fragment.style());

            for (int offset = localStart; offset < localEnd; ) {
                int codePoint = text.codePointAt(offset);
                int width = Character.charCount(codePoint);
                restyled.add(new Fragment(
                        new String(Character.toChars(codePoint)), styler.apply(fragment.style(), styledIndex)));
                offset += width;
                styledIndex++;
            }

            addIfPresent(restyled, text.substring(localEnd), fragment.style());
        }
        return List.copyOf(restyled);
    }

    /** The code points in {@code [start, endExclusive)}, one fragment each, with their styles. */
    public static List<Fragment> codePoints(List<Fragment> fragments, int start, int endExclusive) {
        List<Fragment> codePoints = new ArrayList<>();
        int cursor = 0;
        for (Fragment fragment : fragments == null ? List.<Fragment>of() : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();
            int localStart = Math.max(0, start - fragmentStart);
            int localEnd = Math.min(fragment.text().length(), endExclusive - fragmentStart);
            for (int offset = localStart; offset < localEnd; ) {
                int codePoint = fragment.text().codePointAt(offset);
                codePoints.add(new Fragment(new String(Character.toChars(codePoint)), fragment.style()));
                offset += Character.charCount(codePoint);
            }
        }
        return List.copyOf(codePoints);
    }

    private static int codePointCount(List<Fragment> fragments, int start, int endExclusive) {
        int count = 0;
        int cursor = 0;
        for (Fragment fragment : fragments) {
            int fragmentStart = cursor;
            int fragmentEnd = cursor + fragment.text().length();
            cursor = fragmentEnd;
            if (fragmentEnd <= start || fragmentStart >= endExclusive) {
                continue;
            }
            int localStart = Math.max(0, start - fragmentStart);
            int localEnd = Math.min(fragment.text().length(), endExclusive - fragmentStart);
            count += fragment.text().codePointCount(localStart, localEnd);
        }
        return count;
    }

    /**
     * Splices {@code insertion} in at {@code index}, splitting a fragment when the
     * index falls inside one.
     * <p>
     * Deliberately separate from {@link #replaceRange}: an empty range there matches
     * no fragment at a boundary (the one before ends at the index and the one after
     * starts at it), so the insertion would be dropped.
     */
    public static List<Fragment> insertAt(List<Fragment> fragments, int index, Component insertion) {
        if (fragments == null || insertion == null || index < 0) {
            return fragments;
        }

        List<Fragment> spliced = new ArrayList<>();
        boolean inserted = false;
        int cursor = 0;

        for (Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();

            if (!inserted && index <= fragmentStart) {
                spliced.addAll(flatten(insertion));
                inserted = true;
            }
            if (!inserted && index < cursor) {
                int local = index - fragmentStart;
                addIfPresent(spliced, fragment.text().substring(0, local), fragment.style());
                spliced.addAll(flatten(insertion));
                addIfPresent(spliced, fragment.text().substring(local), fragment.style());
                inserted = true;
                continue;
            }
            spliced.add(fragment);
        }

        if (!inserted) {
            spliced.addAll(flatten(insertion));
        }
        return List.copyOf(spliced);
    }

    private static void addIfPresent(List<Fragment> target, String text, Style style) {
        if (!text.isEmpty()) {
            target.add(new Fragment(text, style));
        }
    }

    private static void appendIfPresent(MutableComponent target, String text, Style style) {
        if (!text.isEmpty()) {
            target.append(Component.literal(text).withStyle(style));
        }
    }

    /** A styled leaf of a flattened component. */
    public record Fragment(String text, Style style) {}
}
