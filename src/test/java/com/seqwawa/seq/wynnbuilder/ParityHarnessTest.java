package com.seqwawa.seq.wynnbuilder;

import com.seqwawa.seq.wynnbuilder.calc.BuildStats;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Reproduces a real build outside Minecraft so its numbers can be compared with the website.
 *
 * <p>Skipped unless a directory of WynnBuilder data files is named by the {@code WB_DATA}
 * environment variable, which keeps it out of the way of the normal test run. Temporary
 * scaffolding: it goes away with {@link WynnBuilderDiagnostics} once the numbers agree.
 */
class ParityHarnessTest {

    /** Overridden by the {@code WB_LINK} environment variable so any build can be checked. */
    private static final String DEFAULT_LINK =
            "CXG4Oa8MutGbmgutid81fuer8b7169F9OCtDtDMA7AIGAEdQInZ0p4p4u2k4y5ka9b482CJCJWBuImNuIcKI0IE2RG4vbYBjtmTwzuxf0";

    @Test
    void dumpBuild() throws Exception {
        String configured = System.getenv("WB_DATA");
        Path directory = Path.of(configured == null ? "nonexistent" : configured);
        Assumptions.assumeTrue(Files.isDirectory(directory), "no data directory supplied");

        // Gradle swallows a test's standard output, so send the dump to a file next to the data.
        System.setOut(new java.io.PrintStream(
                Files.newOutputStream(directory.resolveSibling("parity-out.txt")),
                true,
                java.nio.charset.StandardCharsets.UTF_8));

        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        for (WynnDataFile file : WynnDataFile.values()) {
            Path path = directory.resolve(file.fileName());
            if (Files.exists(path)) {
                contents.put(file, Files.readString(path));
            }
        }
        WynnDataSet data = WynnDataSet.parse("2.2.3.0", contents);
        WynnBuilderSession session = WynnBuilderSession.offline(data);
        String link = System.getenv("WB_LINK");
        session.importBuildLink(link == null || link.isBlank() ? DEFAULT_LINK : link);

        BuildStats stats = session.stats();
        System.out.println("message      = " + session.message());
        System.out.println("class        = " + session.playerClass());
        System.out.println("level        = " + session.build().level());
        System.out.printf(
                "skill totals = %s  assigned = %s (%d/%d)%n",
                java.util.Arrays.toString(stats.skillPointTotals()),
                java.util.Arrays.toString(stats.assignedSkillPoints()),
                stats.assignedTotal(),
                stats.availableSkillPoints());
        System.out.println("aspects      = " + session.build().aspects());
        System.out.println("tomes        = " + session.build().tomeIds());
        for (com.seqwawa.seq.wynnbuilder.data.EquipmentSlot slot :
                com.seqwawa.seq.wynnbuilder.data.EquipmentSlot.encodingOrder()) {
            System.out.println("  " + slot.label() + " = " + session.build().equipment(slot)
                    + "  powders=" + session.build().powders(slot));
        }
        System.out.println("problems     = " + stats.problems());
        System.out.println("toggles      = " + session.abilityTreeEvaluation().toggles());
        System.out.println("major ids    = " + stats.majorIds());

        System.out.println("identifications (non-zero):");
        stats.identifications().entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.printf("  %-28s = %d%n", entry.getKey(), entry.getValue()));

        DamageSources.Report report =
                DamageSources.compute(session.build(), data, stats, session.abilityTreeEvaluation());
        System.out.println("weapon       = " + report.weaponName() + " (" + report.attackSpeed() + ")");
        if (report.melee() != null) {
            System.out.printf(
                    Locale.ROOT,
                    "melee        per hit %.2f  dps %.2f%n",
                    report.melee().perHit(),
                    report.melee().perSecond());
        }
        for (DamageSources.SpellGroup spell : report.spells()) {
            System.out.printf(
                    Locale.ROOT,
                    "SPELL %-16s cost=%.2f headline=%.2f casts/s=%.3f sustained=%.2f%n",
                    spell.name(),
                    spell.cost(),
                    spell.headline(),
                    spell.castsPerSecond(),
                    spell.sustainedDps());
            for (DamageSources.Source part : spell.parts()) {
                System.out.printf(Locale.ROOT, "    %-26s %14.2f", part.name(), part.perHit());
                if (part.result() != null) {
                    System.out.printf(
                            Locale.ROOT,
                            "  noncrit=%.2f crit=%.2f shares=%s",
                            part.result().averageNormal(),
                            part.result().averageCrit(),
                            java.util.Arrays.toString(java.util.Arrays.stream(part.result().conversions())
                                    .mapToLong(Math::round)
                                    .toArray()));
                }
                if (!part.composition().isEmpty()) {
                    System.out.print("  from=" + part.composition());
                }
                System.out.println();
                if (part.result() != null && part.perHit() > 0) {
                    String[] elements = {"neutral", "earth", "thunder", "water", "fire", "air"};
                    double[][] perElement = part.result().perElementNormal();
                    for (int i = 0; i < elements.length; i++) {
                        if (perElement[i][1] > 0) {
                            System.out.printf(
                                    Locale.ROOT,
                                    "        %-8s noncrit %.2f - %.2f%n",
                                    elements[i],
                                    perElement[i][0],
                                    perElement[i][1]);
                        }
                    }
                }
            }
        }
    }
}
