package org.sequoia.seq.managers;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects raid starts from Wynncraft chat and announces them to the guild via the backend.
 * <p>
 * Wynncraft raids typically show party members in a system message when entering.
 * This tracker watches for those patterns and sends announcements.
 */
public class RaidTracker {

    /**
     * Pattern for Wynncraft raid start messages.
     * e.g. "You have entered The Canyon Colossus raid!" or similar.
     */
    private static final Pattern RAID_ENTER_PATTERN =
            Pattern.compile("(?:You have entered|Starting) (?:the )?(.+?)(?:\\s+raid)?[!.]", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern for party members shown when entering a raid.
     * e.g. " - PlayerName" or "- PlayerName (Lv 106)"
     */
    private static final Pattern PARTY_MEMBER_PATTERN =
            Pattern.compile("^\\s*[-•]\\s+(\\w{3,16})");

    private boolean collectingParty = false;
    private String currentRaid = null;
    private final List<String> partyMembers = new ArrayList<>();
    private long collectStartTime = 0;

    public RaidTracker() {
        registerChatHook();
    }

    private void registerChatHook() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            if (!ConnectionManager.isConnected()) return;
            if (!SeqClient.getRaidAutoAnnounceSetting().getValue()) return;

            String plain = message.getString();

            // Check if we're timed out on collecting party members
            if (collectingParty && System.currentTimeMillis() - collectStartTime > 3000) {
                // Timed out, send what we have
                sendAnnouncement();
            }

            // Check for raid entry
            Matcher raidMatcher = RAID_ENTER_PATTERN.matcher(plain);
            if (raidMatcher.find()) {
                currentRaid = raidMatcher.group(1).trim();
                partyMembers.clear();
                collectingParty = true;
                collectStartTime = System.currentTimeMillis();

                // Add self
                if (SeqClient.mc.player != null) {
                    partyMembers.add(SeqClient.mc.player.getName().getString());
                }
                return;
            }

            // Collect party members if we're in collection mode
            if (collectingParty) {
                Matcher memberMatcher = PARTY_MEMBER_PATTERN.matcher(plain);
                if (memberMatcher.find()) {
                    partyMembers.add(memberMatcher.group(1));
                } else if (!plain.isBlank()) {
                    // Non-member line → done collecting
                    sendAnnouncement();
                }
            }
        });
    }

    private void sendAnnouncement() {
        if (currentRaid != null && !partyMembers.isEmpty()) {
            ConnectionManager instance = ConnectionManager.getInstance();
            if (instance != null) {
                // Loot counts are sent later when raid completes; start with zeros
                instance.sendRaidAnnouncement(
                        partyMembers,
                        currentRaid,
                        0, 0, 0.0, 0
                );
            }
        }
        collectingParty = false;
        currentRaid = null;
        partyMembers.clear();
    }
}
