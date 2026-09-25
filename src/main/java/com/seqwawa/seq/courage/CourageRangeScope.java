package com.seqwawa.seq.courage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Decides where Courage rings are allowed to show. Wynncraft replaces the party
 * sidebar header with a raid header for the length of a raid, which is the same
 * signal Sequoia already uses to read raid rosters.
 */
public final class CourageRangeScope {
    private static final String RAID_HEADER = "Raid:";
    private static final int SIDEBAR_SCAN_INTERVAL_TICKS = 5;

    private static boolean inRaid;
    private static int sidebarScanTicksRemaining;

    private CourageRangeScope() {}

    public static boolean inRaid() {
        return inRaid;
    }

    public static void reset() {
        inRaid = false;
        sidebarScanTicksRemaining = 0;
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            reset();
            return;
        }
        if (sidebarScanTicksRemaining <= 0) {
            inRaid = isRaidSidebarActive(readSidebarLines(client));
            sidebarScanTicksRemaining = SIDEBAR_SCAN_INTERVAL_TICKS;
        }
        sidebarScanTicksRemaining--;
    }

    static boolean isRaidSidebarActive(Iterable<String> sidebarLines) {
        for (String line : sidebarLines) {
            if (line != null && line.contains(RAID_HEADER)) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<String> readSidebarLines(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            Component display = entry.display();
            lines.add(display != null ? display.getString() : entry.owner());
        }
        return lines;
    }
}
