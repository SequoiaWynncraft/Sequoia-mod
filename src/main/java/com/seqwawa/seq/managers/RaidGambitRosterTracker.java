package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.ChatIdentityResolver;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

/** Tracks the gambits shown for every player in Wynncraft's raid-start menu. */
public final class RaidGambitRosterTracker {
    static final List<Integer> PLAYER_SLOTS = List.of(18, 19, 20, 21);
    private static final int MAX_GAMBIT_COUNT = 4;
    private static final String GAMBIT_HEADER = "Enabled Gambits:";
    private static final Pattern GAMBIT_NAME_PATTERN = Pattern.compile("(?i)^-\\s+.+\\sGambit$");
    private static final int MAX_LOGGED_GAMBIT_LINES = 5;

    private static AbstractContainerMenu activeMenu;
    private static volatile Map<String, Integer> latestCounts = Map.of();
    private static List<String> latestSlotDiagnostics = List.of();

    private RaidGambitRosterTracker() {}

    public static void observe(AbstractContainerMenu menu, Component screenTitle) {
        if (menu == null || !RaidStartScreenDetector.isRaidStartScreen(screenTitle)) {
            return;
        }
        if (activeMenu != menu) {
            activeMenu = menu;
            latestCounts = Map.of();
            latestSlotDiagnostics = List.of();
            SeqClient.LOGGER.info("[RaidGambits] Detected raid-start menu containerSlots={}", menu.slots.size());
        }

        Map<String, Integer> observed = new LinkedHashMap<>();
        List<String> slotDiagnostics = new ArrayList<>();
        for (int slotIndex : PLAYER_SLOTS) {
            if (slotIndex >= menu.slots.size()) {
                slotDiagnostics.add(slotIndex + ":missing");
                continue;
            }
            SlotInspection inspection = inspectPlayerSlot(menu.slots.get(slotIndex).getItem());
            slotDiagnostics.add(slotIndex + ":" + inspection.summary());
            if (inspection.observation() != null) {
                observed.put(inspection.observation().username(), inspection.observation().gambitCount());
            }
        }

        Map<String, Integer> snapshot = Map.copyOf(observed);
        List<String> diagnosticsSnapshot = List.copyOf(slotDiagnostics);
        if (!snapshot.equals(latestCounts) || !diagnosticsSnapshot.equals(latestSlotDiagnostics)) {
            SeqClient.LOGGER.info(
                    "[RaidGambits] Raid-start snapshot counts={} slots={}", snapshot, diagnosticsSnapshot);
            latestCounts = snapshot;
            latestSlotDiagnostics = diagnosticsSnapshot;
        }
    }

    public static Map<String, Integer> snapshotForParty(List<String> partyMembers) {
        Map<String, Integer> matched = filterForParty(latestCounts, partyMembers);
        List<String> missing = partyMembers == null
                ? List.of()
                : partyMembers.stream()
                        .filter(username -> username != null && !containsUsername(matched, username))
                        .toList();
        SeqClient.LOGGER.info(
                "[RaidGambits] Completion snapshot observed={} matched={} missing={}",
                latestCounts,
                matched,
                missing);
        return matched;
    }

    private static boolean containsUsername(Map<String, Integer> counts, String username) {
        return counts.keySet().stream().anyMatch(username::equalsIgnoreCase);
    }

    static Map<String, Integer> filterForParty(
            Map<String, Integer> observedCounts, List<String> partyMembers) {
        if (partyMembers == null || partyMembers.isEmpty() || observedCounts == null || observedCounts.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> byNormalizedName = new LinkedHashMap<>();
        observedCounts.forEach((username, count) -> byNormalizedName.put(username.toLowerCase(Locale.ROOT), count));

        Map<String, Integer> matched = new LinkedHashMap<>();
        for (String partyMember : partyMembers) {
            if (partyMember == null) {
                continue;
            }
            Integer count = byNormalizedName.get(partyMember.toLowerCase(Locale.ROOT));
            if (count != null) {
                matched.put(partyMember, count);
            }
        }
        return Map.copyOf(matched);
    }

    public static void reset() {
        activeMenu = null;
        latestCounts = Map.of();
        latestSlotDiagnostics = List.of();
    }

    static Optional<PlayerGambitObservation> parsePlayerSlot(ItemStack stack) {
        return Optional.ofNullable(inspectPlayerSlot(stack).observation());
    }

    private static SlotInspection inspectPlayerSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
            return new SlotInspection(null, stack == null || stack.isEmpty() ? "empty" : "placeholder");
        }
        String username = resolveUsername(stack);
        if (username == null) {
            return new SlotInspection(null, "unresolved-head{name='" + compact(stack.getHoverName().getString()) + "'}");
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        GambitLoreAnalysis analysis = analyzeGambitLore(lore == null ? List.of() : lore.lines());
        String summary = username + "=" + analysis.count() + "{header=" + analysis.headerPresent()
                + ",matched=" + analysis.matchedLines() + ",candidates=" + analysis.candidateLines()
                + ",lore=" + analysis.loreLines()
                + (analysis.count() == 0 || analysis.count() < 0 ? ",gambitText=" + analysis.gambitText() : "")
                + "}";
        PlayerGambitObservation observation = analysis.count() < 0
                ? null
                : new PlayerGambitObservation(username, analysis.count());
        return new SlotInspection(observation, summary);
    }

    static int parseGambitCount(List<Component> loreLines) {
        return analyzeGambitLore(loreLines).count();
    }

    private static GambitLoreAnalysis analyzeGambitLore(List<Component> loreLines) {
        boolean insideGambits = false;
        boolean headerPresent = false;
        int count = 0;
        int candidateLines = 0;
        List<String> gambitText = new ArrayList<>();
        for (Component line : loreLines) {
            String text = PacketTextNormalizer.normalizeForParsing(line.getString());
            boolean gambitName = GAMBIT_NAME_PATTERN.matcher(text).matches();
            if (gambitName) {
                candidateLines++;
            }
            if (text.toLowerCase(Locale.ROOT).contains("gambit")
                    && gambitText.size() < MAX_LOGGED_GAMBIT_LINES) {
                gambitText.add(compact(text));
            }
            if (GAMBIT_HEADER.equalsIgnoreCase(text)) {
                insideGambits = true;
                headerPresent = true;
                continue;
            }
            if (insideGambits && gambitName) {
                count++;
                if (count > MAX_GAMBIT_COUNT) {
                    return new GambitLoreAnalysis(
                            -1, headerPresent, count, candidateLines, loreLines.size(), List.copyOf(gambitText));
                }
            }
        }
        return new GambitLoreAnalysis(
                count, headerPresent, count, candidateLines, loreLines.size(), List.copyOf(gambitText));
    }

    private static String compact(String value) {
        String normalized = PacketTextNormalizer.normalizeForParsing(value);
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private static String resolveUsername(ItemStack stack) {
        Component hoverName = stack.getHoverName();
        String fromHover = ChatIdentityResolver.findRealUsername(hoverName);
        if (ChatIdentityResolver.isValidUsername(fromHover)) {
            return fromHover;
        }

        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile != null) {
            String profileName = profile.name().orElse(null);
            if (ChatIdentityResolver.isValidUsername(profileName)) {
                return profileName;
            }
        }

        return ChatIdentityResolver.resolveCanonicalUsername(hoverName, hoverName.getString());
    }

    record PlayerGambitObservation(String username, int gambitCount) {}

    private record GambitLoreAnalysis(
            int count,
            boolean headerPresent,
            int matchedLines,
            int candidateLines,
            int loreLines,
            List<String> gambitText) {}

    private record SlotInspection(PlayerGambitObservation observation, String summary) {}
}
