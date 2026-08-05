package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class ComponentTextEditorTest {

    @Test
    void flattensNestedComponentsInDisplayOrder() {
        Component message = Component.literal("a")
                .append(Component.literal("bb").withStyle(Style.EMPTY.withInsertion("Player")))
                .append(Component.literal("ccc"));

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);

        assertEquals("abbccc", ComponentTextEditor.textOf(fragments));
        assertEquals("Player", fragments.get(1).style().getInsertion());
    }

    @Test
    void replacesRangeSpanningMultipleFragmentsAndKeepsSurroundingStyles() {
        Component message = Component.empty()
                .append(Component.literal("keep-"))
                .append(Component.literal("DROPME"))
                .append(Component.literal("-tail").withStyle(Style.EMPTY.withInsertion("Player")));
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);
        int start = "keep-".length();

        Component rebuilt = ComponentTextEditor.replaceRange(
                fragments, start, start + "DROPME".length(), Component.literal("NEW"));

        assertEquals("keep-NEW-tail", rebuilt.getString());
        assertEquals(
                "Player",
                ComponentTextEditor.flatten(rebuilt).stream()
                        .filter(fragment -> fragment.text().equals("-tail"))
                        .findFirst()
                        .orElseThrow()
                        .style()
                        .getInsertion());
    }

    @Test
    void splitsAFragmentWhenTheRangeSitsInsideIt() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("abcdef").withStyle(Style.EMPTY.withInsertion("P")));

        Component rebuilt = ComponentTextEditor.replaceRange(fragments, 2, 4, Component.literal("__"));

        assertEquals("ab__ef", rebuilt.getString());
        assertEquals(
                List.of("ab", "__", "ef"),
                ComponentTextEditor.flatten(rebuilt).stream()
                        .map(ComponentTextEditor.Fragment::text)
                        .toList());
    }

    @Test
    void restylesARangeWithoutChangingTheText() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(Component.empty()
                .append(Component.literal("Nick").withStyle(Style.EMPTY.withInsertion("Real")))
                .append(Component.literal("(Real)"))
                .append(Component.literal(": hi")));

        List<ComponentTextEditor.Fragment> restyled = ComponentTextEditor.restyleRange(
                fragments, 0, "Nick".length(), style -> style.withColor(0x4CB4FA));

        assertEquals("Nick(Real): hi", ComponentTextEditor.textOf(restyled));
        assertEquals(0x4CB4FA, restyled.get(0).style().getColor().getValue());
        assertEquals("Real", restyled.get(0).style().getInsertion(), "other styling survives");
        assertNull(restyled.get(1).style().getColor(), "text outside the range is untouched");
    }

    @Test
    void restyleSplitsAFragmentAtTheRangeBoundary() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("abcdef"));

        List<ComponentTextEditor.Fragment> restyled =
                ComponentTextEditor.restyleRange(fragments, 2, 4, style -> style.withColor(0x112233));

        assertEquals(
                List.of("ab", "cd", "ef"),
                restyled.stream().map(ComponentTextEditor.Fragment::text).toList());
        assertEquals(0x112233, restyled.get(1).style().getColor().getValue());
    }

    @Test
    void restylesARangeByCodePointPositionWithoutSplittingSurrogatePairs() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                Component.literal("-A😀B-").withStyle(Style.EMPTY.withInsertion("Player")));

        List<ComponentTextEditor.Fragment> restyled = ComponentTextEditor.restyleRangeByPosition(
                fragments,
                1,
                "-A😀B".length(),
                (style, position) -> style.withColor((int) Math.round(position * 100)));

        assertEquals(List.of("-", "A", "😀", "B", "-"), restyled.stream()
                .map(ComponentTextEditor.Fragment::text)
                .toList());
        assertEquals(
                List.of(0, 50, 100),
                restyled.subList(1, 4).stream()
                        .map(fragment -> fragment.style().getColor().getValue())
                        .toList());
        restyled.subList(1, 4).forEach(fragment -> assertEquals("Player", fragment.style().getInsertion()));
        assertNull(restyled.getFirst().style().getColor(), "text before the range is untouched");
        assertNull(restyled.getLast().style().getColor(), "text after the range is untouched");
    }

    @Test
    void insertsAtAFragmentBoundary() {
        // The very case that silently dropped the insignia: the ':' starts its own
        // fragment, so an empty replaceRange range matched nothing.
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(
                Component.empty().append(Component.literal("Name")).append(Component.literal(": hi")));
        int colon = ComponentTextEditor.textOf(fragments).indexOf(':');

        List<ComponentTextEditor.Fragment> spliced =
                ComponentTextEditor.insertAt(fragments, colon, Component.literal("*"));

        assertEquals("Name*: hi", ComponentTextEditor.textOf(spliced));
    }

    @Test
    void insertsInsideAFragmentAndAtTheEnd() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("abcdef"));

        assertEquals(
                "abc*def",
                ComponentTextEditor.textOf(
                        ComponentTextEditor.insertAt(fragments, 3, Component.literal("*"))));
        assertEquals(
                "abcdef*",
                ComponentTextEditor.textOf(
                        ComponentTextEditor.insertAt(fragments, 6, Component.literal("*"))));
    }

    @Test
    void insertionKeepsItsOwnStyling() {
        List<ComponentTextEditor.Fragment> fragments =
                ComponentTextEditor.flatten(Component.literal("ab"));

        List<ComponentTextEditor.Fragment> spliced = ComponentTextEditor.insertAt(
                fragments, 1, Component.literal("*").withStyle(Style.EMPTY.withColor(0x112233)));

        assertEquals(
                0x112233,
                spliced.stream()
                        .filter(fragment -> fragment.text().equals("*"))
                        .findFirst()
                        .orElseThrow()
                        .style()
                        .getColor()
                        .getValue());
    }

    @Test
    void returnsNullWhenTheRangeMatchesNothing() {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(Component.literal("abc"));

        assertNull(ComponentTextEditor.replaceRange(fragments, 9, 12, Component.literal("x")));
        assertNull(ComponentTextEditor.replaceRange(fragments, 2, 1, Component.literal("x")));
    }
}
