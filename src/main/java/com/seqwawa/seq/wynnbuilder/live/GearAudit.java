package com.seqwawa.seq.wynnbuilder.live;

import com.seqwawa.seq.wynnbuilder.calc.BuildEvaluation;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.calc.IdentificationRolls;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which equipped piece is holding the build back, and by how much.
 *
 * <p>The question a player actually has is "what do I replace first", and the honest answer is not
 * how good an item looks in isolation. A perfectly rolled ring of stats this build does not scale on
 * is worth less than a mediocre one that happens to carry the right damage type, and a major
 * identification can make health matter more than damage. So nothing here reads an item and judges
 * it. Each piece is re-run through the whole pipeline twice — once as it is, once as it would be at
 * its ceiling — and the difference in the build's own damage is the verdict. Whatever the build
 * scales on, that measurement already accounts for it.
 *
 * <p>A piece's ceiling depends on where it came from. A dropped item's is its best possible roll; a
 * drawback printed on a mythic is part of the item and cannot be rolled away. A crafted item's
 * ceiling also drops the stats that actively hurt, because those came from an ingredient and are a
 * recipe decision rather than luck — which is the difference between "reroll this" and "recraft
 * this".
 */
public final class GearAudit {

    private GearAudit() {}

    /** What one equipped piece is costing. */
    public record Finding(
            EquipmentSlot slot,
            String itemName,
            boolean crafted,
            Float quality,
            double dpsNow,
            double dpsAtCeiling,
            double dpsWithout,
            int healthNow,
            int healthWithout,
            List<LiveItem.Roll> weakestRolls,
            List<LiveItem.Roll> harmfulRolls) {

        public Finding {
            weakestRolls = List.copyOf(weakestRolls);
            harmfulRolls = List.copyOf(harmfulRolls);
        }

        /** Damage the build is missing because of how this piece rolled. */
        public double headroom() {
            return Math.max(0, dpsAtCeiling - dpsNow);
        }

        /** The same as a share of the build's current damage. */
        public double headroomPercent() {
            return dpsNow <= 0 ? 0 : 100.0 * headroom() / dpsNow;
        }

        /** Damage the piece is worth at all, which is what taking it off would cost. */
        public double contribution() {
            return Math.max(0, dpsNow - dpsWithout);
        }

        public double contributionPercent() {
            return dpsNow <= 0 ? 0 : 100.0 * contribution() / dpsNow;
        }

        /** Effective health the piece is worth, so a defensive piece is not condemned for silence. */
        public int healthContribution() {
            return healthNow - healthWithout;
        }

        public boolean hasHarmfulRolls() {
            return !harmfulRolls.isEmpty();
        }

        /** What to do about it, in the words the player is thinking in. */
        public String advice() {
            if (crafted && hasHarmfulRolls()) {
                return "recraft without its negative ingredients";
            }
            if (crafted) {
                return "recraft with better ingredients";
            }
            if (quality != null && quality < 100) {
                return "reroll or replace";
            }
            return "replace";
        }
    }

    /**
     * The audit.
     *
     * @param referenceSource the spell or attack every measurement was taken against, so a headline
     *     figure and a delta are always about the same thing
     * @param findings worst first, meaning the most damage recoverable from one slot
     */
    public record Result(String referenceSource, double dps, List<Finding> findings, List<String> notes) {

        public Result {
            findings = List.copyOf(findings);
            notes = List.copyOf(notes);
        }

        public static Result empty(String note) {
            return new Result(null, 0, List.of(), note == null ? List.of() : List.of(note));
        }

        public boolean isEmpty() {
            return findings.isEmpty();
        }

        public Finding worst() {
            return findings.isEmpty() ? null : findings.get(0);
        }
    }

    /**
     * Audits an equipped build.
     *
     * <p>Runs the full pipeline nineteen times over, so it belongs off the render thread.
     */
    public static Result run(WynnDataSet data, EquippedBuild.Snapshot snapshot) {
        if (data == null || snapshot == null || snapshot.isEmpty()) {
            return Result.empty("Nothing equipped to measure");
        }
        WynnBuild build = snapshot.build();
        BuildEvaluation baseline = evaluate(build, data, snapshot);
        if (baseline.stats() == null) {
            return Result.empty("The build could not be measured");
        }
        DamageSources.Report report = baseline.damage(build, data);
        String reference = referenceSource(report);
        if (reference == null) {
            return Result.empty(report.message().isEmpty()
                    ? "No damage source to measure against"
                    : report.message());
        }
        double dpsNow = damage(report, reference);
        int healthNow = baseline.stats().effectiveHealthWithoutDodge();

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<EquipmentSlot, LiveItem> entry : snapshot.loadout().items().entrySet()) {
            EquipmentSlot slot = entry.getKey();
            LiveItem item = entry.getValue();

            WynnBuild atCeiling = swap(build, slot, ceiling(item));
            WynnBuild without = swap(build, slot, BuildEquipment.none());
            BuildEvaluation ceilingEvaluation = evaluate(atCeiling, data, snapshot);
            BuildEvaluation withoutEvaluation = evaluate(without, data, snapshot);

            findings.add(new Finding(
                    slot,
                    item.name(),
                    item.crafted(),
                    item.quality(),
                    dpsNow,
                    damage(ceilingEvaluation.damage(atCeiling, data), reference),
                    damage(withoutEvaluation.damage(without, data), reference),
                    healthNow,
                    withoutEvaluation.stats() == null
                            ? healthNow
                            : withoutEvaluation.stats().effectiveHealthWithoutDodge(),
                    item.weakestRolls(3),
                    item.harmfulRolls()));
        }

        findings.sort(Comparator.comparingDouble(Finding::headroom).reversed());
        return new Result(reference, dpsNow, findings, snapshot.loadout().notes());
    }

    /**
     * The piece as good as it could be.
     *
     * <p>For a craft that also means dropping the identifications that hurt, since an ingredient
     * that subtracts from the build is a choice rather than a bad roll.
     */
    private static BuildEquipment ceiling(LiveItem item) {
        if (!item.crafted() || item.harmfulRolls().isEmpty()) {
            return item.toBestEquipment();
        }
        return new BuildEquipment.Live(withoutHarmful(item.best()), true, item.best());
    }

    private static WynnItem withoutHarmful(WynnItem item) {
        Map<String, Integer> kept = new LinkedHashMap<>();
        item.identifications().forEach((key, value) -> {
            boolean harmful = Identifications.isInverted(key) ? value > 0 : value < 0;
            if (!harmful) {
                kept.put(key, value);
            }
        });
        return new WynnItem(
                item.id(), item.name(), item.displayName(), item.category(), item.type(), item.tier(),
                item.level(), item.classRequirement(), item.attackSpeed(), item.powderSlots(),
                item.requirements(), item.damages(), item.baseDefences(), item.baseHealth(),
                kept, item.majorIds(), item.setName(), item.fixedIds(), item.restriction(), item.remapId());
    }

    /** A copy of the build with one slot replaced, so the player's own build is never touched. */
    private static WynnBuild swap(WynnBuild build, EquipmentSlot slot, BuildEquipment replacement) {
        WynnBuild copy = build.copy();
        copy.setEquipment(slot, replacement);
        return copy;
    }

    private static BuildEvaluation evaluate(WynnBuild build, WynnDataSet data, EquippedBuild.Snapshot snapshot) {
        return BuildEvaluation.compute(
                build,
                data,
                IdentificationRolls.RollMode.BEST,
                snapshot.treeState(),
                Map.of(),
                java.util.Set.of(),
                snapshot.tomeBonuses(),
                List.of(),
                1.0);
    }

    /**
     * The build's strongest sustained output, named.
     *
     * <p>Every variant is then measured against that same name rather than against whatever happens
     * to be strongest in the variant, because a delta between two different spells is not a delta.
     */
    private static String referenceSource(DamageSources.Report report) {
        String best = null;
        double bestDps = 0;
        if (report.melee() != null && report.melee().perSecond() > 0) {
            best = report.melee().name();
            bestDps = report.melee().perSecond();
        }
        for (DamageSources.SpellGroup spell : report.spells()) {
            if (spell.sustainedDps() > bestDps) {
                bestDps = spell.sustainedDps();
                best = spell.name();
            }
        }
        return best;
    }

    private static double damage(DamageSources.Report report, String source) {
        if (report == null || source == null) {
            return 0;
        }
        if (report.melee() != null && source.equals(report.melee().name())) {
            return report.melee().perSecond();
        }
        for (DamageSources.SpellGroup spell : report.spells()) {
            if (source.equals(spell.name())) {
                return spell.sustainedDps();
            }
        }
        // The reference can genuinely vanish: taking the weapon off removes every spell with it.
        return 0;
    }
}
