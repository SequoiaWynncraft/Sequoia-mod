package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.WynnIngredient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The ingredient grid is two columns by three rows, filled left to right then top to bottom:
 *
 * <pre>
 *   0 1
 *   2 3
 *   4 5
 * </pre>
 */
class CraftCalcTest {

    private static WynnIngredient withModifiers(WynnIngredient.PositionModifiers modifiers) {
        return new WynnIngredient(
                1, "Test", "Test", 1, 1, List.of(), Map.of(), modifiers,
                WynnIngredient.ItemModifiers.NONE, WynnIngredient.ConsumableModifiers.NONE);
    }

    private static List<WynnIngredient> slots(int position, WynnIngredient ingredient) {
        List<WynnIngredient> ingredients = new ArrayList<>(java.util.Collections.nCopies(6, null));
        ingredients.set(position, ingredient);
        return ingredients;
    }

    @Test
    void emptyGridIsAllHundredPercent() {
        int[] effectiveness = CraftCalc.effectiveness(java.util.Collections.nCopies(6, null));
        assertTrue(Arrays.stream(effectiveness).allMatch(value -> value == 100));
    }

    @Test
    void rightModifierAffectsTheRowNeighbour() {
        // Position 0 is the left column, so its "right" bonus lands on position 1 only.
        int[] effectiveness = CraftCalc.effectiveness(
                slots(0, withModifiers(new WynnIngredient.PositionModifiers(0, 20, 0, 0, 0, 0))));

        assertEquals(100, effectiveness[0]);
        assertEquals(120, effectiveness[1]);
        assertEquals(100, effectiveness[2]);
    }

    @Test
    void leftModifierOnlyAppliesFromTheRightColumn() {
        // Position 0 is already leftmost, so a "left" bonus has nowhere to go.
        int[] fromLeftColumn = CraftCalc.effectiveness(
                slots(0, withModifiers(new WynnIngredient.PositionModifiers(20, 0, 0, 0, 0, 0))));
        assertTrue(Arrays.stream(fromLeftColumn).allMatch(value -> value == 100));

        int[] fromRightColumn = CraftCalc.effectiveness(
                slots(1, withModifiers(new WynnIngredient.PositionModifiers(20, 0, 0, 0, 0, 0))));
        assertEquals(120, fromRightColumn[0]);
        assertEquals(100, fromRightColumn[1]);
    }

    @Test
    void underModifierAffectsTheWholeColumnBelow() {
        int[] effectiveness = CraftCalc.effectiveness(
                slots(0, withModifiers(new WynnIngredient.PositionModifiers(0, 0, 0, 15, 0, 0))));

        assertEquals(100, effectiveness[0]);
        assertEquals(100, effectiveness[1], "the other column is untouched");
        assertEquals(115, effectiveness[2]);
        assertEquals(115, effectiveness[4]);
    }

    @Test
    void aboveModifierAffectsTheWholeColumnAbove() {
        int[] effectiveness = CraftCalc.effectiveness(
                slots(4, withModifiers(new WynnIngredient.PositionModifiers(0, 0, 10, 0, 0, 0))));

        assertEquals(110, effectiveness[0]);
        assertEquals(110, effectiveness[2]);
        assertEquals(100, effectiveness[4]);
        assertEquals(100, effectiveness[5]);
    }

    @Test
    void touchingAffectsOrthogonalNeighboursOnly() {
        // Position 2 is the middle-left cell; it touches 0, 3 and 4.
        int[] effectiveness = CraftCalc.effectiveness(
                slots(2, withModifiers(new WynnIngredient.PositionModifiers(0, 0, 0, 0, 25, 0))));

        assertEquals(125, effectiveness[0]);
        assertEquals(100, effectiveness[1], "diagonal is not touching");
        assertEquals(100, effectiveness[2], "an ingredient does not modify itself");
        assertEquals(125, effectiveness[3]);
        assertEquals(125, effectiveness[4]);
        assertEquals(100, effectiveness[5], "diagonal is not touching");
    }

    @Test
    void notTouchingIsTheComplementOfTouching() {
        int[] effectiveness = CraftCalc.effectiveness(
                slots(2, withModifiers(new WynnIngredient.PositionModifiers(0, 0, 0, 0, 0, 30))));

        assertEquals(100, effectiveness[0]);
        assertEquals(130, effectiveness[1], "diagonal counts as not touching");
        assertEquals(100, effectiveness[2]);
        assertEquals(100, effectiveness[3]);
        assertEquals(100, effectiveness[4]);
        assertEquals(130, effectiveness[5]);
    }

    @Test
    void modifiersFromSeveralIngredientsAccumulate() {
        List<WynnIngredient> ingredients = new ArrayList<>(java.util.Collections.nCopies(6, null));
        ingredients.set(0, withModifiers(new WynnIngredient.PositionModifiers(0, 20, 0, 0, 0, 0)));
        ingredients.set(2, withModifiers(new WynnIngredient.PositionModifiers(0, 10, 0, 0, 0, 0)));

        int[] effectiveness = CraftCalc.effectiveness(ingredients);

        assertEquals(120, effectiveness[1]);
        assertEquals(110, effectiveness[3]);
    }

    @Test
    void negativeModifiersReduceEffectiveness() {
        int[] effectiveness = CraftCalc.effectiveness(
                slots(0, withModifiers(new WynnIngredient.PositionModifiers(0, -40, 0, 0, 0, 0))));

        assertEquals(60, effectiveness[1]);
    }
}
