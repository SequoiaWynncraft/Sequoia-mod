package com.seqwawa.seq.consumables;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Remembers which war consumables have an active effect on the current server and picks
 * the one to use next.
 *
 * <p>Copies of one consumable share a status: they apply the same effect, so once any of
 * them is used they all count as active until that effect wears off. The others are ranked
 * with the fewest penalties first, so consumables with only bonuses go before the ones with
 * negative stats; ties keep inventory order (hotbar first), so the next pick moves through
 * the inventory one consumable at a time.
 */
public final class WarConsumableTracker {
    /** How long after a right click the server may take to answer it. */
    static final long USE_CONFIRM_WINDOW_MILLIS = 3_000;
    /**
     * How long to keep a consumable active when Wynncraft still reports an effect this tracker
     * saw start and expected to be over: server lag stretches effect durations.
     */
    static final long LAG_RECHECK_MILLIS = 30_000;

    public enum Status {
        ACTIVE,
        NEXT,
        UNUSED
    }

    private final Map<String, Long> activeUntilMillis = new HashMap<>();
    private Map<Integer, WarConsumable> previousSlots = Map.of();
    private Map<String, Status> statuses = Map.of();
    private List<String> order = List.of();
    private Long session;
    private UseAttempt pendingUse;

    /** Records a right click with {@code consumable} held in inventory slot {@code slot}. */
    public void onUseAttempt(int slot, WarConsumable consumable, long nowMillis) {
        pendingUse = new UseAttempt(slot, consumable, nowMillis);
    }

    /**
     * Wynncraft refused the last right click because that consumable's effect is still
     * active, even if this tracker did not see it start or expected it to be over.
     */
    public void onAlreadyActive(long nowMillis) {
        if (pendingUse == null || nowMillis - pendingUse.atMillis() > USE_CONFIRM_WINDOW_MILLIS) {
            return;
        }
        WarConsumable consumable = pendingUse.consumable();
        Long knownEnd = activeUntilMillis.get(consumable.fingerprint());
        // An effect seen starting only outlasts its expected end through server lag, so check
        // again soon. One never seen starting may have just begun: assume its full duration.
        long activeUntil = knownEnd != null
                ? Math.max(knownEnd, nowMillis + LAG_RECHECK_MILLIS)
                : effectEnd(consumable, nowMillis);
        activeUntilMillis.put(consumable.fingerprint(), activeUntil);
        pendingUse = null;
    }

    /**
     * Compares the consumables now in the inventory, keyed by inventory slot, with the
     * previous observation. A different {@code session} means a new server or character,
     * which ends every effect.
     */
    public void observe(long session, Map<Integer, WarConsumable> slots, long nowMillis) {
        if (this.session == null || this.session != session) {
            reset();
            this.session = session;
        } else {
            detectUses(slots, nowMillis);
        }
        if (pendingUse != null && nowMillis - pendingUse.atMillis() > USE_CONFIRM_WINDOW_MILLIS) {
            pendingUse = null;
        }
        previousSlots = Map.copyOf(slots);
        rank(slots, nowMillis);
    }

    /** The status of the consumable with {@code fingerprint}, or {@code null} when it is not held. */
    public Status statusOf(String fingerprint) {
        return statuses.get(fingerprint);
    }

    /** Fingerprints of the held consumables in the order they should be used. */
    public List<String> order() {
        return order;
    }

    public void reset() {
        activeUntilMillis.clear();
        previousSlots = Map.of();
        statuses = Map.of();
        order = List.of();
        session = null;
        pendingUse = null;
    }

    private void detectUses(Map<Integer, WarConsumable> slots, long nowMillis) {
        previousSlots.forEach((slot, before) -> {
            WarConsumable after = slots.get(slot);
            boolean sameConsumable = after != null && after.fingerprint().equals(before.fingerprint());
            if (sameConsumable ? after.uses() < before.uses() : wasRightClicked(slot, before, nowMillis)) {
                activeUntilMillis.put(before.fingerprint(), effectEnd(before, nowMillis));
                if (pendingUse != null && pendingUse.consumable().fingerprint().equals(before.fingerprint())) {
                    pendingUse = null;
                }
            }
        });
    }

    /**
     * Using the last charge removes the item, which cannot be told apart from moving or
     * dropping it unless the player right clicked it just before.
     */
    private boolean wasRightClicked(int slot, WarConsumable before, long nowMillis) {
        return pendingUse != null
                && pendingUse.slot() == slot
                && pendingUse.consumable().fingerprint().equals(before.fingerprint())
                && nowMillis - pendingUse.atMillis() <= USE_CONFIRM_WINDOW_MILLIS;
    }

    /** Without a known duration the effect is assumed to last until the session ends. */
    private static long effectEnd(WarConsumable consumable, long startMillis) {
        return consumable.durationSeconds() > 0
                ? startMillis + consumable.durationSeconds() * 1_000L
                : Long.MAX_VALUE;
    }

    private void rank(Map<Integer, WarConsumable> slots, long nowMillis) {
        Map<String, WarConsumable> distinct = new LinkedHashMap<>();
        new TreeMap<>(slots).values().forEach(consumable -> distinct.putIfAbsent(consumable.fingerprint(), consumable));

        List<WarConsumable> ranked = new ArrayList<>(distinct.values());
        // List.sort is stable, so equally ranked consumables stay in inventory order.
        ranked.sort(Comparator.comparingInt(WarConsumable::negativeStats));
        order = ranked.stream().map(WarConsumable::fingerprint).toList();

        String next = order.stream()
                .filter(fingerprint -> !isActive(fingerprint, nowMillis))
                .findFirst()
                .orElse(null);
        Map<String, Status> ranking = new HashMap<>();
        for (String fingerprint : order) {
            Status status = isActive(fingerprint, nowMillis)
                    ? Status.ACTIVE
                    : fingerprint.equals(next) ? Status.NEXT : Status.UNUSED;
            ranking.put(fingerprint, status);
        }
        statuses = ranking;
    }

    private boolean isActive(String fingerprint, long nowMillis) {
        Long activeUntil = activeUntilMillis.get(fingerprint);
        return activeUntil != null && nowMillis < activeUntil;
    }

    private record UseAttempt(int slot, WarConsumable consumable, long atMillis) {}
}
