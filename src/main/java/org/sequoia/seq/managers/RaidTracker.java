package org.sequoia.seq.managers;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects raid completions from Wynncraft chat and announces them to the guild.
 * <p>
 * Matches messages like: "Player1, Player2, and Player3 finished The Canyon Colossus 
 * and claimed 2x Aspects, 2048x Emeralds, +10367m Guild Experience, and +440 Seasonal Rating"
 */
public class RaidTracker {

    /**
     * Pattern for Wynncraft raid completion.
     * Group 1: Comma-separated player names (with optional "and")
     * Group 2: Raid name (e.g., "The Canyon Colossus")
     * Group 3: Aspects count
     * Group 4: Emeralds count  
     * Group 5: Guild Experience (numeric value before 'm')
     * Group 6: Seasonal Rating
     */
    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
        "^(.+?) finished (.+?) and claimed (\\d+)x Aspects, (\\d+)x Emeralds, \\+([\\d.]+)m Guild Experience, and \\+(\\d+) Seasonal Rating$"
    );

    public RaidTracker() {
        registerChatHook();
    }

    private void registerChatHook() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            if (!ConnectionManager.isConnected()) return;
            if (!SeqClient.getRaidAutoAnnounceSetting().getValue()) return;

            String plain = message.getString();
            Matcher matcher = RAID_FINISH_PATTERN.matcher(plain);

            if (!matcher.find()) return;

            // Parse player names: "A, B, C, and D" -> [A, B, C, D]
            String namesPart = matcher.group(1).replace(", and ", ", ");
            List<String> partyMembers = Arrays.stream(namesPart.split(", "))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            String raidName = matcher.group(2);
            int aspects = Integer.parseInt(matcher.group(3));
            int emeralds = Integer.parseInt(matcher.group(4));
            double guildExp = Double.parseDouble(matcher.group(5));
            int seasonalRating = Integer.parseInt(matcher.group(6));

            ConnectionManager instance = ConnectionManager.getInstance();
            if (instance != null && !partyMembers.isEmpty()) {
                instance.sendRaidAnnouncement(
                    partyMembers,
                    raidName,
                    aspects,
                    emeralds,
                    guildExp,
                    seasonalRating
                );
            }
        });
    }
}