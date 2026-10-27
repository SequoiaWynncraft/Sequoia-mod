package com.seqwawa.seq.managers;

import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Session evidence for the own HQ's trade style, independent of war telemetry. */
public final class CheapestQueueTracker {
    static final long ATTEMPT_TIMEOUT_MS = 15_000;
    private static final int MAX_STYLES = 512;
    private static final Pattern STYLE = Pattern.compile(
            "^[^:]{1,64} changed the style of (.{1,128}?) to (cheapest|fastest)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HQ = Pattern.compile(
            "^[^:]{1,64} set the guild headquarters to (.{1,128})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOADOUT = Pattern.compile(
            "^[^:]{1,64} applied the loadout(?:\\s+|:).+ on .+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACK = Pattern.compile("^Attacking: (.{1,128})$");
    private static final Pattern HQ_ITEM = Pattern.compile("^(?:\\[!] )?([^()]{1,128}) \\(HQ\\)$");
    private static final List<String> TERRITORY_FOOTERS =
            List.of("Left-Click to view territory", "Click to Select", "Click to Unselect");

    public record TerritoryEntry(String name, String lastLoreLine) {}

    private final LongSupplier clock;
    private final Consumer<String> effect;
    private final LinkedHashMap<String, Boolean> styles = new LinkedHashMap<>();
    private String headquarters;
    private Attempt pending;
    private int menuId = -1;
    private String menuTitle;
    private List<TerritoryEntry> menuEntries = List.of();

    public CheapestQueueTracker(LongSupplier clock, Consumer<String> effect) {
        this.clock = Objects.requireNonNull(clock);
        this.effect = Objects.requireNonNull(effect);
    }

    public void onMenuOpened(int id, String title, int slots) {
        closeMenu();
        if (!isTerritoryMenu(title) || slots < 9 || slots > 54 || slots % 9 != 0) return;
        menuId = id;
        menuTitle = title;
        menuEntries = new ArrayList<>(Collections.nCopies(slots, null));
    }

    public int menuSlots(int id) {
        return id == menuId ? menuEntries.size() : 0;
    }

    public void onMenuContents(int id, List<TerritoryEntry> entries) {
        if (id != menuId || entries.size() < menuEntries.size()) return;
        for (int slot = 0; slot < menuEntries.size(); slot++) menuEntries.set(slot, entries.get(slot));
        observeTerritories(menuTitle, menuEntries);
    }

    public void onMenuSlot(int id, int slot, TerritoryEntry entry) {
        if (id != menuId || slot < 0 || slot >= menuEntries.size()) return;
        menuEntries.set(slot, entry);
        observeTerritories(menuTitle, menuEntries);
    }

    public void onMenuClosed(int id) {
        if (id == menuId) closeMenu();
    }

    /** Entries must come only from server menu slots, excluding the player's inventory. */
    public void observeTerritories(String title, List<TerritoryEntry> entries) {
        if (!isTerritoryMenu(title)) return;
        String found = null;
        for (TerritoryEntry entry : entries) {
            if (entry == null) continue;
            if (!TERRITORY_FOOTERS.contains(normalize(entry.lastLoreLine()))) continue;
            Matcher matcher = HQ_ITEM.matcher(normalize(entry.name()));
            if (!matcher.matches()) continue;
            String candidate = key(matcher.group(1));
            if (found != null && !found.equals(candidate)) {
                headquarters = null;
                pending = null;
                return;
            }
            found = candidate;
        }
        // Territory lists may be paginated; a page without HQ proves nothing.
        if (found != null) setHeadquarters(found);
    }

    public static boolean isTerritoryMenu(String title) {
        String normalized = normalize(title);
        return normalized.matches(".+: Territories") || normalized.equals("Select Territories");
    }

    public void onSystemChat(String message) {
        String text = normalize(message);
        Matcher hq = HQ.matcher(text);
        if (hq.matches()) {
            setHeadquarters(key(hq.group(1)));
            return;
        }
        Matcher style = STYLE.matcher(text);
        if (style.matches()) {
            String territory = key(style.group(1));
            styles.remove(territory);
            styles.put(territory, style.group(2).equalsIgnoreCase("cheapest"));
            if (styles.size() > MAX_STYLES) styles.remove(styles.keySet().iterator().next());
            if (territory.equals(headquarters)) pending = null;
            return;
        }
        if (LOADOUT.matcher(text).matches()) {
            // A loadout can change trade style without a separate style notice.
            styles.clear();
            pending = null;
            return;
        }
        if (pending == null) return;
        if (clock.getAsLong() - pending.startedAtMs() >= ATTEMPT_TIMEOUT_MS) {
            pending = null;
            return;
        }
        var confirmation = WarTerritoryQueueManager.parseQueueConfirmation(text).orElse(null);
        if (confirmation == null || !pending.territory().equalsIgnoreCase(confirmation.territory())) return;
        Attempt confirmed = pending;
        pending = null;
        if (confirmed.headquarters().equals(headquarters) && Boolean.TRUE.equals(styles.get(headquarters))) {
            effect.accept(confirmation.territory());
        }
    }

    /** Called for a local Attack click, never a guildmate's queue broadcast. */
    public void onAttackClick(String screenTitle, String itemName) {
        Matcher attack = ATTACK.matcher(normalize(screenTitle));
        if (!attack.matches() || !normalize(itemName).startsWith("Attack")) return;
        // Every new attack replaces the old attempt, including fastest/unknown ones.
        pending = headquarters != null && Boolean.TRUE.equals(styles.get(headquarters))
                ? new Attempt(attack.group(1).trim(), headquarters, clock.getAsLong())
                : null;
    }

    public void reset() {
        closeMenu();
        headquarters = null;
        styles.clear();
        pending = null;
    }

    private void closeMenu() {
        menuId = -1;
        menuTitle = null;
        menuEntries = List.of();
    }

    private void setHeadquarters(String territory) {
        if (!Objects.equals(headquarters, territory)) pending = null;
        headquarters = territory;
    }

    private static String normalize(String text) {
        return PacketTextNormalizer.normalizeForParsing(text);
    }

    private static String key(String territory) {
        return territory.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(String territory, String headquarters, long startedAtMs) {}
}
