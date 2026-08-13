package com.seqwawa.seq.wynnbuilder.atree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.codec.AbilityTreeCodec;
import com.seqwawa.seq.wynnbuilder.codec.BitVector;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fixtures follow the upstream schema but are hand-written, since the WynnBuilder repository is
 * GPL-3 while this mod is MIT.
 */
class AbilityTreeTest {

    /**
     * A small tree:
     *
     * <pre>
     *        0 root
     *       /     \
     *      1       2
     *      |      / \
     *      3     4   5   (5 also depends on 3, and blocks 4)
     * </pre>
     */
    private static final String TREE_JSON =
            """
            {"Archer": [
              {"id": 0, "display_name": "Root", "desc": "", "parents": [], "dependencies": [], "blockers": [],
               "cost": 0, "display": {"row": 0, "col": 4, "icon": "node"}, "properties": {}, "effects": []},
              {"id": 1, "display_name": "Left", "desc": "", "parents": [0], "dependencies": [], "blockers": [],
               "cost": 1, "display": {"row": 1, "col": 2, "icon": "node"}, "archetype": "Trapper",
               "effects": [{"type": "raw_stat", "bonuses": [{"type": "stat", "name": "sdPct", "value": 10}]}]},
              {"id": 2, "display_name": "Right", "desc": "", "parents": [0], "dependencies": [], "blockers": [],
               "cost": 2, "display": {"row": 1, "col": 6, "icon": "node"}, "archetype": "Trapper",
               "effects": [{"type": "raw_stat", "toggle": "Activate Stance", "bonuses": [{"type": "stat", "name": "mdPct", "value": 25}]}]},
              {"id": 3, "display_name": "Left Child", "desc": "", "parents": [1], "dependencies": [], "blockers": [],
               "cost": 3, "display": {"row": 2, "col": 2, "icon": "node"}, "effects": []},
              {"id": 4, "display_name": "Right Child", "desc": "", "parents": [2], "dependencies": [], "blockers": [],
               "cost": 1, "display": {"row": 2, "col": 5, "icon": "node"},
               "effects": [{"type": "raw_stat", "toggle": "Focus", "bonuses": [{"type": "stat", "name": "sdRaw", "value": 15}]}]},
              {"id": 5, "display_name": "Gated", "desc": "", "parents": [2], "dependencies": [3], "blockers": [4],
               "cost": 1, "display": {"row": 2, "col": 7, "icon": "node"},
               "archetype": "Trapper", "archetype_req": 2, "effects": []}
            ]}
            """;

    private static AbilityTree tree() {
        AbilityTree tree = AbilityTree.parseAll(TREE_JSON).get("Archer");
        assertNotNull(tree);
        return tree;
    }

    @Test
    void parsesNodesAndFindsTheRoot() {
        AbilityTree tree = tree();
        assertEquals(6, tree.nodes().size());
        assertNotNull(tree.root());
        assertEquals(0, tree.root().id());
        assertTrue(tree.root().isRoot());
        assertEquals("Left", tree.node(1).displayName());
        assertEquals(2, tree.node(2).cost());
        assertEquals(1, tree.node(1).row());
        assertEquals(2, tree.node(1).column());
    }

    @Test
    void childrenFollowRawArrayOrder() {
        // The encoding walks children in this order, so it must match the data file's order exactly.
        assertEquals(List.of(1, 2), tree().childrenOf(0));
        assertEquals(List.of(4, 5), tree().childrenOf(2));
        assertEquals(List.of(), tree().childrenOf(3));
    }

    @Test
    void classIsResolvedFromTheWeaponType() {
        assertEquals("Archer", AbilityTree.classForWeaponType("bow"));
        assertEquals("Mage", AbilityTree.classForWeaponType("wand"));
        assertEquals("Shaman", AbilityTree.classForWeaponType("relik"));
        assertNull(AbilityTree.classForWeaponType("helmet"));
        assertNull(AbilityTree.classForWeaponType(null));
    }

    @Test
    void rootIsAlwaysActiveAndFree() {
        AbilityTreeState state = new AbilityTreeState(tree());
        assertTrue(state.isActive(0));
        assertEquals(0, state.spentPoints());
        assertFalse(state.toggle(0), "the root cannot be turned off");
    }

    @Test
    void abilitiesNeedAConnectedParent() {
        AbilityTreeState state = new AbilityTreeState(tree());

        assertFalse(state.canActivate(3), "node 3 needs node 1 first");
        assertTrue(state.toggle(1));
        assertTrue(state.canActivate(3));
        assertTrue(state.toggle(3));
        assertEquals(4, state.spentPoints());
    }

    @Test
    void dependenciesAndBlockersAreEnforced() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(1);
        state.toggle(2);

        // Node 5 depends on node 3, which is not taken yet.
        assertFalse(state.canActivate(5));
        assertTrue(state.blockedReason(5).contains("Left Child"));

        state.toggle(3);
        assertTrue(state.canActivate(5));

        // Taking the blocker makes it unavailable again.
        state.toggle(4);
        assertFalse(state.canActivate(5));
        assertTrue(state.blockedReason(5).contains("Conflicts"));
    }

    @Test
    void archetypeRequirementIsChecked() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(1);
        state.toggle(3);
        // Only one Trapper ability is active, but node 5 wants two.
        assertEquals(1, state.archetypeCount("Trapper"));
        assertTrue(state.blockedReason(5).contains("Requires a connected parent"));

        state.toggle(2);
        assertEquals(2, state.archetypeCount("Trapper"));
        assertNull(state.blockedReason(5));
    }

    @Test
    void abilityPointsLimitTheSelection() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.setAbilityPoints(2);

        assertTrue(state.toggle(1));
        assertEquals(1, state.remainingPoints());
        assertFalse(state.canActivate(3), "node 3 costs 3 but only 1 point is left");
    }

    @Test
    void deselectingAParentRemovesItsDescendants() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(1);
        state.toggle(3);
        assertTrue(state.isActive(3));

        state.toggle(1);

        assertFalse(state.isActive(1));
        assertFalse(state.isActive(3), "a stranded child cannot be encoded, so it is removed too");
        assertTrue(state.isConnected());
    }

    @Test
    void deselectingBreaksDependenciesToo() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(1);
        state.toggle(2);
        state.toggle(3);
        state.toggle(5);
        assertTrue(state.isActive(5));

        // Node 5 depends on node 3, so removing node 1 cascades through node 3 to node 5.
        state.toggle(1);

        assertFalse(state.isActive(3));
        assertFalse(state.isActive(5));
    }

    @Test
    void selectionSurvivesEncodeAndDecode() {
        AbilityTree tree = tree();
        AbilityTreeState state = new AbilityTreeState(tree);
        state.toggle(1);
        state.toggle(2);
        state.toggle(3);

        BitVector bits = AbilityTreeCodec.encode(tree.codecRoot(), state::isActive);
        Set<Integer> decoded = AbilityTreeCodec.decode(tree.codecRoot(), bits);

        assertEquals(Set.of(1, 2, 3), decoded);
    }

    @Test
    void emptySelectionEncodesToOneBitPerRootChild() {
        AbilityTree tree = tree();
        AbilityTreeState state = new AbilityTreeState(tree);

        BitVector bits = AbilityTreeCodec.encode(tree.codecRoot(), state::isActive);

        // Nothing is active, so only the root's two children are visited.
        assertEquals(2, bits.length());
        assertEquals("00", bits.toString());
    }

    @Test
    void rawStatEffectsFeedTheBuildStats() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(1);

        AbilityTreeEngine.Evaluation evaluation = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());

        assertEquals(10, evaluation.statBonuses().get("sdPct"));
    }

    @Test
    void activeAbilityBonusesOnlyCountWhenSwitchedOn() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(2);

        AbilityTreeEngine.Evaluation off = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());
        assertNull(off.statBonuses().get("mdPct"));
        assertTrue(off.toggles().contains("Activate Stance"), "the toggle is still offered to the player");

        AbilityTreeEngine.Evaluation on = AbilityTreeEngine.evaluate(state, Map.of(), Set.of("Activate Stance"));
        assertEquals(25, on.statBonuses().get("mdPct"));
    }

    @Test
    void passiveAbilityBonusesApplyAsSoonAsTheNodeIsTaken() {
        AbilityTreeState state = new AbilityTreeState(tree());
        state.toggle(2);
        state.toggle(4);

        AbilityTreeEngine.Evaluation evaluation = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());

        // A passive is in effect the moment the ability is unlocked, so it neither waits for the
        // player to switch it on nor clutters the buff list with a switch that should never be off.
        assertEquals(15, evaluation.statBonuses().get("sdRaw"));
        assertFalse(evaluation.toggles().contains("Focus"), "a passive is not offered as a toggle");
    }

    @Test
    void inactiveAbilitiesContributeNothing() {
        AbilityTreeState state = new AbilityTreeState(tree());

        AbilityTreeEngine.Evaluation evaluation = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());

        assertTrue(evaluation.statBonuses().isEmpty());
    }
}
