package com.seqwawa.seq.wynnbuilder;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.wynnbuilder.calc.BuildStats;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.Map;

/**
 * Temporary diagnostic dump for the WynnBuilder screens.
 *
 * <p>Exists so a discrepancy against the website can be read from the log rather than reconstructed
 * from screenshots. Off by default and scoped to this feature; delete once the numbers agree.
 */
public final class WynnBuilderDiagnostics {

    private static boolean enabled;

    private WynnBuilderDiagnostics() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        SeqClient.LOGGER.info("[WynnBuilder] Diagnostics {}", value ? "enabled" : "disabled");
    }

    /** Dumps the whole picture: gear, buffs, stats and every damage source. */
    public static void dump(WynnBuilderSession session) {
        if (!enabled) {
            return;
        }
        try {
            WynnDataSet data = session.data();
            WynnBuild build = session.build();
            BuildStats stats = session.stats();
            if (data == null || build == null || stats == null) {
                SeqClient.LOGGER.info("[WynnBuilder] Nothing to dump: data not ready");
                return;
            }

            StringBuilder out = new StringBuilder("\n=== WynnBuilder diagnostics ===\n");
            out.append("data version = ").append(data.version()).append('\n');
            out.append("level = ").append(build.level()).append('\n');

            for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
                BuildEquipment equipment = build.equipment(slot);
                String name = "empty";
                if (equipment instanceof BuildEquipment.Normal normal) {
                    WynnItem item = data.item(normal.itemId());
                    name = item == null ? "UNKNOWN#" + normal.itemId() : item.displayName();
                } else if (equipment instanceof BuildEquipment.Crafted) {
                    name = "crafted";
                } else if (equipment instanceof BuildEquipment.Custom) {
                    name = "custom";
                }
                out.append("  ").append(slot.label()).append(" = ").append(name);
                if (!build.powders(slot).isEmpty()) {
                    out.append("  powders=").append(build.powders(slot));
                }
                out.append('\n');
            }

            out.append("class = ").append(session.playerClass()).append('\n');
            out.append("external boosts = ").append(session.enabledExternalBoosts()).append('\n');
            out.append("ability toggles = ").append(session.enabledToggles()).append('\n');
            out.append("sliders = ").append(session.sliderValues()).append('\n');
            out.append("raid buffs = ").append(session.enabledRaidBuffs()).append('\n');
            out.append("powder specials = ").append(session.powderSpecialLevels()).append('\n');
            out.append("roll mode = ").append(session.rollMode()).append('\n');

            out.append("health = ").append(stats.health())
                    .append("  ehp = ").append(stats.effectiveHealth()).append('\n');
            out.append("skill totals = ").append(java.util.Arrays.toString(stats.skillPointTotals()))
                    .append("  assigned = ").append(java.util.Arrays.toString(stats.assignedSkillPoints()))
                    .append(" (").append(stats.assignedTotal()).append('/')
                    .append(stats.availableSkillPoints()).append(")\n");
            out.append("problems = ").append(stats.problems()).append('\n');
            out.append("active sets = ").append(stats.activeSets()).append('\n');
            out.append("major ids = ").append(stats.majorIds()).append('\n');

            // Every identification, including the namespaced multipliers the UI hides: those are
            // exactly what a damage discrepancy usually comes down to.
            out.append("identifications:\n");
            stats.identifications().entrySet().stream()
                    .filter(entry -> entry.getValue() != 0)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> out.append("    ").append(entry.getKey())
                            .append(" = ").append(entry.getValue()).append('\n'));

            var evaluation = session.abilityTreeEvaluation();
            out.append("ability tree: spells=").append(evaluation.spells().size())
                    .append(" toggles=").append(evaluation.toggles())
                    .append(" sliders=").append(evaluation.sliders()).append('\n');

            DamageSources.Report report = DamageSources.compute(build, data, stats, evaluation);
            out.append("weapon = ").append(report.weaponName())
                    .append(" (").append(report.attackSpeed()).append(")\n");
            if (report.melee() != null) {
                out.append(String.format(java.util.Locale.ROOT,
                        "melee: per hit %.2f, dps %.2f%n",
                        report.melee().perHit(), report.melee().perSecond()));
            }
            for (DamageSources.SpellGroup spell : report.spells()) {
                out.append(String.format(java.util.Locale.ROOT,
                        "SPELL %s  cost=%d  headline=%.2f  casts/s=%.3f%n",
                        spell.name(), spell.cost(), spell.headline(), spell.castsPerSecond()));
                for (DamageSources.Source part : spell.parts()) {
                    out.append(String.format(java.util.Locale.ROOT, "    %-26s %14.2f", part.name(), part.perHit()));
                    if (part.result() != null) {
                        out.append("  shares=").append(java.util.Arrays.toString(
                                        java.util.Arrays.stream(part.result().conversions())
                                                .mapToLong(Math::round).toArray()))
                                .append(String.format(java.util.Locale.ROOT,
                                        "  noncrit=%.2f crit=%.2f",
                                        part.result().averageNormal(), part.result().averageCrit()));
                    }
                    if (!part.composition().isEmpty()) {
                        out.append("  from=").append(part.composition());
                    }
                    out.append('\n');
                }
            }
            SeqClient.LOGGER.info(out.toString());
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[WynnBuilder] Diagnostics failed", exception);
        }
    }
}
