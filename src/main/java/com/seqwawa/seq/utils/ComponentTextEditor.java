package com.seqwawa.seq.utils;

import java.util.ArrayList;
import java.util.List;
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
