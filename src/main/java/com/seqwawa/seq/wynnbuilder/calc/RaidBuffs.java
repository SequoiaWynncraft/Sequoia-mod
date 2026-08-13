package com.seqwawa.seq.wynnbuilder.calc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The raid buffs a party can be carrying, as published by WynnBuilder.
 *
 * <p>These are not part of a build: they are temporary boosts granted inside a raid, so they are
 * toggled by the player rather than derived from the gear. Some also grant a major identification.
 *
 * <p>The table is generated from the upstream constants so the numbers cannot drift by transcription.
 */
public final class RaidBuffs {

    /** The raids that grant buffs, in the order the site lists them. */
    public enum Raid {
        NOTG("Nest of the Grootslangs"),
        NOL("Orphion's Nexus of Light"),
        TCC("The Canyon Colossus"),
        TNA("The Nameless Anomaly"),
        WTP("The Whispering Peaks");

        private final String label;

        Raid(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One buff: the stats it grants and any major identification it brings. */
    public record Buff(Raid raid, String name, Map<String, Integer> stats, List<String> majorIds) {
        public Buff {
            stats = Map.copyOf(stats);
            majorIds = List.copyOf(majorIds);
        }
    }

    private static final List<Buff> ALL = List.of(
            entry(Raid.NOTG, "Lightbearer-I", stats("int", 25, "healPct", 20), List.of()),
            entry(Raid.NOTG, "Lightbearer-II", stats("mr", 15, "sdPct", 40), List.of()),
            entry(Raid.NOTG, "Lightbearer-III", stats("int", 40, "healPct", 35), List.of("ARCANES")),
            entry(Raid.NOTG, "Bioluminescent-I", stats("rDamPct", 30), List.of()),
            entry(Raid.NOTG, "Bioluminescent-II", stats("mdPct", 50, "sdPct", 50), List.of()),
            entry(Raid.NOTG, "Bioluminescent-III", stats("str", 10, "dex", 10, "int", 10, "def", 10, "agi", 10, "rDamPct", 50), List.of()),
            entry(Raid.NOTG, "Berserk-I", stats("mdPct", 75), List.of("EXPLOSIVE_IMPACT")),
            entry(Raid.NOTG, "Berserk-II", stats("expd", 25, "sprintReg", 100, "mdRaw", 500), List.of()),
            entry(Raid.NOTG, "Berserk-III", stats("str", 30, "mdPct", 75, "mdRaw", 500), List.of()),
            entry(Raid.NOTG, "Pestilent-I", stats("str", 20, "poison", 6000, "ls", 250), List.of()),
            entry(Raid.NOTG, "Pestilent-II", stats("str", 25, "poison", 9000, "ms", 12), List.of()),
            entry(Raid.NOTG, "Pestilent-III", stats("ls", 400, "poison", 12500), List.of("PLAGUE")),
            entry(Raid.NOTG, "Bedrock-I", stats("hpBonus", 1000, "ms", 10, "ls", 150), List.of()),
            entry(Raid.NOTG, "Bedrock-II", stats("hpBonus", 1250, "ls", 300, "hprRaw", 700), List.of()),
            entry(Raid.NOTG, "Bedrock-III", stats("ms", 15, "hprRaw", 900), List.of("ALTRUISM")),
            entry(Raid.NOTG, "Palisade", stats("ls", 300, "spPct4", -50), List.of("INTANGIBLE")),
            entry(Raid.NOL, "Cherubim-I", stats("spd", 45, "mdPct", 90), List.of()),
            entry(Raid.NOL, "Cherubim-II", stats("ls", 500, "rDamPct", 40), List.of()),
            entry(Raid.NOL, "Cherubim-III", stats("str", 20, "dex", 20, "int", 20, "def", 20, "agi", 20), List.of()),
            entry(Raid.NOL, "Seraphim-I", stats("agi", 20, "sdPct", 25, "ref", 30), List.of()),
            entry(Raid.NOL, "Seraphim-II", stats("mr", 20, "sdPct", 30), List.of()),
            entry(Raid.NOL, "Seraphim-III", stats("dex", 30), List.of("SORCERY")),
            entry(Raid.NOL, "Ophanim-I", stats("def", 30, "healPct", 30), List.of()),
            entry(Raid.NOL, "Ophanim-II", stats("agi", 30, "healPct", 25, "hprPct", 40), List.of()),
            entry(Raid.NOL, "Ophanim-III", stats("hpBonus", 5000, "hprRaw", 600), List.of()),
            entry(Raid.NOL, "Throne-I", stats("int", 35, "rDamPct", 25), List.of()),
            entry(Raid.NOL, "Throne-II", stats("mr", 25, "ms", 15), List.of()),
            entry(Raid.NOL, "Throne-III", stats("int", 50), List.of("ARCANES")),
            entry(Raid.NOL, "Anti-I", stats("expd", 50, "mainAttackRange", 50), List.of()),
            entry(Raid.NOL, "Anti-II", stats("str", 30, "poison", 20000), List.of()),
            entry(Raid.NOL, "Anti-III", stats("def", 50, "sprint", 300), List.of("EXPLOSIVE_IMPACT")),
            entry(Raid.NOL, "Neophyte", stats("mainAttackRange", 300, "ms", 50, "atktier", -50), List.of()),
            entry(Raid.TCC, "Intrepid-I", stats("spd", 40, "esteal", 30), List.of("GREED")),
            entry(Raid.TCC, "Intrepid-II", stats("hprRaw", 250, "hprPct", 20), List.of("ALTRUISM")),
            entry(Raid.TCC, "Intrepid-III", stats("def", 25, "healPct", 25, "weakenEnemy", 5), List.of()),
            entry(Raid.TCC, "Stonewalker-I", stats("str", 20, "mdRaw", 700, "thorns", 100), List.of()),
            entry(Raid.TCC, "Stonewalker-II", stats("str", 30, "mdPct", 100, "expd", 50), List.of("EXPLOSIVE_IMPACT")),
            entry(Raid.TCC, "Stonewalker-III", stats("mdRaw", 1500, "mdPct", 100), List.of()),
            entry(Raid.TCC, "Giant-I", stats("str", 10, "def", 40), List.of()),
            entry(Raid.TCC, "Giant-II", stats("hpBonus", 3000, "hprRaw", 400), List.of()),
            entry(Raid.TCC, "Giant-III", stats("hpBonus", 4000, "hprRaw", 500, "hprPct", 20), List.of()),
            entry(Raid.TCC, "Elder-I", stats("agi", 30, "sdPct", 25, "ref", 100), List.of()),
            entry(Raid.TCC, "Elder-II", stats("int", 40, "mr", 12, "rDamPct", 30), List.of()),
            entry(Raid.TCC, "Elder-III", stats("mr", 18, "sdPct", 30, "rDamPct", 30), List.of()),
            entry(Raid.TCC, "Boulderbreaker-I", stats("dex", 20, "ls", 300), List.of()),
            entry(Raid.TCC, "Boulderbreaker-II", stats("agi", 25, "sdPct", 30, "spd", 35), List.of()),
            entry(Raid.TCC, "Boulderbreaker-III", stats("str", 30, "dex", 30, "mdPct", 80), List.of()),
            entry(Raid.TCC, "Cirrus", stats("agi", 30, "jh", 15, "healPct", 30), List.of()),
            entry(Raid.TNA, "Hollowed-I", stats("def", 20, "hpBonus", 2000, "ref", 50), List.of()),
            entry(Raid.TNA, "Hollowed-II", stats("mr", 30, "hprRaw", 400, "thorns", 50), List.of()),
            entry(Raid.TNA, "Hollowed-III", stats("hpBonus", 5000, "hprRaw", 500, "damPct", -30), List.of()),
            entry(Raid.TNA, "Sojourner-I", stats("agi", 20, "mr", 20, "sprintReg", 80), List.of()),
            entry(Raid.TNA, "Sojourner-II", stats("def", 40, "sprint", -100), List.of("FREERUNNER")),
            entry(Raid.TNA, "Sojourner-III", stats("str", 30, "dex", 30, "hprRaw", -250), List.of()),
            entry(Raid.TNA, "Fading-I", stats("hprPct", 25, "spd", 30), List.of("ALTRUISM")),
            entry(Raid.TNA, "Fading-II", stats("agi", 25, "ms", 15, "healPct", 25), List.of()),
            entry(Raid.TNA, "Fading-III", stats("damRaw", 400, "damPct", 40, "str", -10, "dex", -10, "int", -10, "def", -10, "agi", -10), List.of()),
            entry(Raid.TNA, "Insidious-I", stats("int", 30, "ms", 12, "sdPct", 25), List.of()),
            entry(Raid.TNA, "Insidious-II", stats("ls", 325, "sdPct", 40, "maxMana", 50), List.of()),
            entry(Raid.TNA, "Insidious-III", stats("sdPct", 60, "spd", -40), List.of("SORCERY")),
            entry(Raid.TNA, "Hopeless-I", stats("str", 20, "mainAttackRange", 30, "mdPct", 75), List.of()),
            entry(Raid.TNA, "Hopeless-II", stats("dex", 25, "expd", 50), List.of("FISSION")),
            entry(Raid.TNA, "Hopeless-III", stats("mdPct", 135, "spd", 60, "mr", -15), List.of()),
            entry(Raid.TNA, "Manic", stats("mr", 20, "rDefPct", -100), List.of("MADNESS")),
            entry(Raid.WTP, "Relentless-I", stats("spd", 30, "mdRaw", 600, "expd", 35), List.of()),
            entry(Raid.WTP, "Relentless-II", stats("weakenEnemy", 8, "spPct2", -75), List.of()),
            entry(Raid.WTP, "Ingenious-I", stats("hpBonus", 3000, "spPct4", -40, "healPct", 25), List.of()),
            entry(Raid.WTP, "Ingenious-II", stats("def", 30, "hprRaw", 1000), List.of("PHOENIXBORN")),
            entry(Raid.WTP, "Unrestrained-I", stats("spRaw1", -5, "spRaw3", -5, "int", 25), List.of()),
            entry(Raid.WTP, "Unrestrained-II", stats("damRaw", 250, "mdPct", 250, "poison", 30000), List.of()),
            entry(Raid.WTP, "Opulent-I", stats("mr", 25, "maxMana", 40, "rDamPct", 25), List.of()),
            entry(Raid.WTP, "Opulent-II", stats("rDamRaw", 200, "rDamPct", 30), List.of("ARCANES")),
            entry(Raid.WTP, "Omniscient-I", stats("ls", 650, "maxMana", 40, "dex", 20), List.of()),
            entry(Raid.WTP, "Omniscient-II", stats("ms", 20, "mainAttackRange", 75, "str", 20), List.of()),
            entry(Raid.WTP, "Restless", stats("sdPct", -35, "maxMana", -25), List.of()),
            entry(Raid.WTP, "Apathetic", stats("spd", -45, "mdPct", -40), List.of()),
            entry(Raid.WTP, "Faceless", stats("str", -10, "dex", -10, "def", -10, "agi", -10), List.of()),
            entry(Raid.WTP, "Prideful", stats("hpBonus", -3000, "rDefPct", -50), List.of()),
            entry(Raid.WTP, "Isolated", stats("hprRaw", -375, "mr", -10), List.of()));

    private RaidBuffs() {}

    private static Buff entry(Raid raid, String name, Map<String, Integer> stats, List<String> majorIds) {
        return new Buff(raid, name, stats, majorIds);
    }

    /** Builds a stat map from alternating key and value arguments. */
    private static Map<String, Integer> stats(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    public static List<Buff> all() {
        return ALL;
    }

    /** The buffs a single raid grants. */
    public static List<Buff> forRaid(Raid raid) {
        List<Buff> buffs = new ArrayList<>();
        for (Buff buff : ALL) {
            if (buff.raid() == raid) {
                buffs.add(buff);
            }
        }
        return buffs;
    }

    public static Buff byName(String name) {
        for (Buff buff : ALL) {
            if (buff.name().equals(name)) {
                return buff;
            }
        }
        return null;
    }
}
