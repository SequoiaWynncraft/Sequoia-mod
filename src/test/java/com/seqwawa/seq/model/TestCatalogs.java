package com.seqwawa.seq.model;

import java.util.List;
import java.util.Set;

/** Sample guild meta shared by client tests. */
public final class TestCatalogs {

    private TestCatalogs() {}

    public static RaidCatalog sequoia() {
        return new RaidCatalog(
                List.of(
                        new RaidBuild("ASCENDANCY", "Ascendancy", 1),
                        new RaidBuild("CSPRING", "Cspring", 2),
                        new RaidBuild("RESONANCE", "Resonance", 3),
                        new RaidBuild("HALCYON", "Halcyon", 4),
                        new RaidBuild("CATACLYSM", "Cataclysm", 5),
                        new RaidBuild("TCRACK", "Tcrack", 6),
                        new RaidBuild("HERO", "Hero", 7),
                        new RaidBuild("HADAL", "Hadal", 8)),
                List.of(
                        new RaidType(
                                "TNA",
                                "TNA",
                                "The Nameless Anomaly",
                                1,
                                Set.of("ASCENDANCY", "CSPRING", "RESONANCE")),
                        new RaidType(
                                "TCC", "TCC", "The Canyon Colossus", 2, Set.of("CSPRING", "ASCENDANCY", "HALCYON")),
                        new RaidType(
                                "NOTG",
                                "NOTG",
                                "Nest of the Grootslangs",
                                3,
                                Set.of("CATACLYSM", "TCRACK", "HERO", "HADAL")),
                        new RaidType(
                                "NOL",
                                "NOL",
                                "Orphion's Nexus of Light",
                                4,
                                Set.of("ASCENDANCY", "CATACLYSM", "HALCYON")),
                        new RaidType(
                                "WTP", "WTP", "The Wartorn Palace", 5, Set.of("CSPRING", "ASCENDANCY", "RESONANCE"))));
    }
}
