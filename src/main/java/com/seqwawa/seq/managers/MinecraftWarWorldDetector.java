package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Detects Wynncraft's pre-war world without depending on Wynntils. */
final class MinecraftWarWorldDetector {
    private static final Pattern WAR_HEADER = Pattern.compile("(?i)(?:^|\\s)War\\s*:");
    private static String enteredTerritory;

    private MinecraftWarWorldDetector() {}

    static synchronized String enteredTerritory() {
        if (!isWarInstance()) {
            enteredTerritory = null;
            return null;
        }
        if (enteredTerritory != null) {
            return enteredTerritory;
        }
        WarTerritoryQueueManager queues = SeqClient.getWarTerritoryQueueManager();
        enteredTerritory = queues == null
                ? null
                : queues.enteredTerritoryForLocalPlayer().orElse(null);
        return enteredTerritory;
    }

    static synchronized void reset() {
        enteredTerritory = null;
    }

    static boolean isWarInstance() {
        Minecraft client = Minecraft.getInstance();
        return hasWarSidebar(sidebarLines(client)) || hasWarInstanceCoordinates(client);
    }

    static boolean hasWarInstanceCoordinates(int x, int z) {
        return Math.abs(x + 65_536) <= 1_024 && Math.abs(z + 65_536) <= 1_024;
    }

    static boolean hasWarSidebar(Iterable<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (line != null && WAR_HEADER.matcher(PacketTextNormalizer.normalizeForParsing(line)).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sidebarLines(Minecraft client) {
        if (client == null || client.level == null) {
            return List.of();
        }
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component rendered = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            lines.add(rendered.getString());
        }
        return lines;
    }

    private static boolean hasWarInstanceCoordinates(Minecraft client) {
        return client != null
                && client.player != null
                && hasWarInstanceCoordinates(client.player.getBlockX(), client.player.getBlockZ());
    }
}
