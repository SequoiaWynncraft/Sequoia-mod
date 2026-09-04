package com.seqwawa.seq.wynnbuilder.live;

import com.seqwawa.seq.integrations.WynntilsAbilityTreeAccess;
import com.seqwawa.seq.integrations.WynntilsEquipmentAccess;
import com.seqwawa.seq.integrations.WynntilsSkillPointAccess;
import com.seqwawa.seq.integrations.WynntilsTomeAccess;
import com.seqwawa.seq.wynnbuilder.atree.AbilityNode;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTree;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns what was read off the player into the build the calculator understands.
 *
 * <p>Four sources have to agree on one character: the nine equipped pieces, the skill point totals,
 * the ability tree and the mastery tomes. They arrive with different freshness — gear is always
 * there, the rest depend on menus having been seen — so each is optional and its absence is reported
 * rather than papered over. A missing ability tree costs the spells, missing skill points cost the
 * critical rate and the spell costs, missing tomes cost whatever the tomes carry; in every case the
 * panel says so instead of printing a confident wrong number.
 */
public final class EquippedBuild {

    private EquippedBuild() {}

    /**
     * How much of the character could be read.
     *
     * @param treeCoverage abilities whose state is known, as a percentage of the class tree
     */
    public record Readiness(
            boolean gear, boolean skillPoints, boolean tree, int treeCoverage, boolean tomes, int tomeCount) {

        public static Readiness none() {
            return new Readiness(false, false, false, 0, false, 0);
        }

        /** Whether every number on the panel can be trusted. */
        public boolean isComplete() {
            return gear && skillPoints && tree && treeCoverage >= 100 && tomes;
        }

        /** What the player still has to open, in the order it is worth doing. */
        public List<String> missing() {
            List<String> missing = new ArrayList<>();
            if (!gear) {
                missing.add("equipment");
            }
            if (!skillPoints) {
                missing.add("skill points");
            }
            if (!tree) {
                missing.add("ability tree");
            } else if (treeCoverage < 100) {
                missing.add("ability tree (" + treeCoverage + "%)");
            }
            if (!tomes) {
                missing.add("tomes");
            }
            return missing;
        }
    }

    /** Everything a panel needs about the equipped character. */
    public record Snapshot(
            WynnBuild build,
            AbilityTreeState treeState,
            Map<String, Integer> tomeBonuses,
            WynntilsEquipmentAccess.Loadout loadout,
            String playerClass,
            Readiness readiness) {

        public static Snapshot empty() {
            return new Snapshot(
                    null, null, Map.of(), WynntilsEquipmentAccess.Loadout.empty(), null, Readiness.none());
        }

        public boolean isEmpty() {
            return build == null;
        }
    }

    /**
     * Assembles the build.
     *
     * @param data the WynnBuilder data set, needed for the ability tree and for naming items
     */
    public static Snapshot assemble(
            WynnDataSet data,
            WynntilsEquipmentAccess.Loadout loadout,
            WynntilsSkillPointAccess.Snapshot skillPoints,
            WynntilsAbilityTreeAccess.Snapshot tree,
            WynntilsTomeAccess.Snapshot tomes) {

        if (data == null || loadout == null || loadout.isEmpty()) {
            return Snapshot.empty();
        }

        int level = loadout.level() > 0 ? loadout.level() : data.encodingConsts().maxLevel();
        WynnBuild build = new WynnBuild(
                0, level, data.encodingConsts().tomeCount(), data.encodingConsts().aspectCount());

        for (Map.Entry<EquipmentSlot, LiveItem> entry : loadout.items().entrySet()) {
            build.setEquipment(entry.getKey(), entry.getValue().toEquipment());
        }

        // The game's own totals are final totals, gear and tomes included, which is exactly what a
        // manual skill point assignment means to the calculator.
        if (skillPoints != null && skillPoints.known()) {
            int[] totals = skillPoints.totals();
            for (int i = 0; i < totals.length; i++) {
                build.setAssignedSkillPoint(i, totals[i]);
            }
        }

        String playerClass = playerClass(build, data, tree);
        AbilityTree abilityTree = playerClass == null ? null : data.abilityTree(playerClass);
        AbilityTreeState treeState = null;
        int coverage = 0;
        boolean treeRead = false;

        if (abilityTree != null && !abilityTree.isEmpty()) {
            treeState = new AbilityTreeState(abilityTree);
            treeState.setAbilityPoints(AbilityTree.abilityPointsForLevel(level));
            Match match = match(abilityTree, tree);
            treeState.setActive(match.activeIds());
            coverage = match.coverage();
            treeRead = match.observed() > 0;
        }

        Readiness readiness = new Readiness(
                true,
                skillPoints != null && skillPoints.known(),
                treeRead,
                coverage,
                tomes != null && tomes.read(),
                tomes == null ? 0 : tomes.count());

        Map<String, Integer> tomeBonuses = tomes == null ? Map.of() : tomes.identifications();
        return new Snapshot(build, treeState, tomeBonuses, loadout, playerClass, readiness);
    }

    /**
     * The class being played.
     *
     * <p>The equipped weapon settles it, since ability trees are per class and a relik is only ever
     * a shaman's. Wynntils' own answer is the fallback for a character holding nothing usable.
     */
    private static String playerClass(
            WynnBuild build, WynnDataSet data, WynntilsAbilityTreeAccess.Snapshot tree) {

        WynnItem weapon = com.seqwawa.seq.wynnbuilder.calc.BuildStats.resolveItem(
                build.equipment(EquipmentSlot.WEAPON), data);
        if (weapon != null) {
            String fromWeapon = AbilityTree.classForWeaponType(weapon.type());
            if (fromWeapon != null) {
                return fromWeapon;
            }
        }
        return tree == null ? null : tree.playerClass();
    }

    /** The outcome of lining the observed ability names up against the data set's tree. */
    private record Match(Set<Integer> activeIds, int observed, int coverage) {}

    /**
     * Matches abilities by name.
     *
     * <p>The two sides describe the same tree from different sources — Wynncraft's own menu and
     * WynnBuilder's data file — and the only field they reliably share is the name the game prints,
     * so that is the join. Names are normalised for case and spacing because the two disagree on
     * neither consistently.
     *
     * <p>An unobserved node is treated as not taken. That understates a build read from a
     * half-scrolled tree rather than overstating it, and the coverage figure is what tells the
     * player the number is still climbing.
     */
    private static Match match(AbilityTree tree, WynntilsAbilityTreeAccess.Snapshot observed) {
        if (observed == null || observed.isEmpty()) {
            return new Match(Set.of(), 0, 0);
        }
        Map<String, Boolean> states = observed.nodeStates();
        Set<Integer> active = new HashSet<>();
        int seen = 0;
        int total = 0;

        for (AbilityNode node : tree.nodes()) {
            if (node.isRoot()) {
                // The root is not an ability the game shows; the state adds it back on its own.
                continue;
            }
            total++;
            Boolean unlocked = states.get(normalise(node.displayName()));
            if (unlocked == null) {
                continue;
            }
            seen++;
            if (unlocked) {
                active.add(node.id());
            }
        }
        int coverage = total == 0 ? 0 : (int) Math.round(100.0 * seen / total);
        return new Match(active, seen, coverage);
    }

    /** Kept identical to the reader's normalisation, since the two halves have to meet. */
    private static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
