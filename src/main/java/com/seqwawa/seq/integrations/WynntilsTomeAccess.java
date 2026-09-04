package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.containers.containers.MasteryTomesContainer;
import com.wynntils.models.items.items.game.TomeItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Learns the player's mastery tomes by watching them open the tome menu.
 *
 * <p>Wynntils has no model for equipped tomes — its own skill point scan only reads the aggregate
 * line in the character sheet, which gives skill points and nothing else. Tomes carry mana regen and
 * spell cost reductions too, and those move a spell's cost directly, so the whole tome has to be
 * read rather than just its skill points.
 *
 * <p>Unlike the ability tree the tome menu is a single page, so one opening reads every tome at
 * once and coverage is never partial. Every stack in the container that parses as a tome is counted
 * as equipped, which is what the menu shows; the count is reported so a wrong reading is visible
 * rather than silently inflating the build.
 */
public final class WynntilsTomeAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";

    /** Every Wynncraft container menu ends with the player's own 36 inventory slots. */
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private static final Map<String, Integer> IDENTIFICATIONS = new LinkedHashMap<>();
    /** Matches the ability tree reader: often enough to be live, rarely enough to be free. */
    private static final long OBSERVE_INTERVAL_MILLIS = 250;

    private static int tomeCount;
    private static ClassType observedClass;
    private static long lastObservedAt;

    /** Bumped whenever the reading changes, so callers can notice in constant time. */
    private static int revision;

    private WynntilsTomeAccess() {}

    /**
     * The tomes read so far.
     *
     * @param identifications every tome's stats summed, in the calculator's namespace
     * @param count how many tomes those came from, so an implausible reading can be spotted
     * @param read whether the tome menu has been seen at all
     */
    public record Snapshot(Map<String, Integer> identifications, int count, boolean read) {
        public Snapshot {
            identifications = Map.copyOf(identifications);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), 0, false);
        }
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID);
    }

    /**
     * Records the tomes the open container is showing.
     *
     * <p>Safe on every container and every frame: it returns immediately unless the tome menu is
     * what is open. The menu is replaced wholesale rather than merged, because it always shows the
     * full set — merging would keep a tome the player has since unequipped.
     */
    public static void observe(AbstractContainerMenu menu) {
        if (menu == null || !isAvailable()) {
            return;
        }
        try {
            if (!(Models.Container.getCurrentContainer() instanceof MasteryTomesContainer)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastObservedAt < OBSERVE_INTERVAL_MILLIS) {
                return;
            }
            lastObservedAt = now;
            Map<String, Integer> identifications = new LinkedHashMap<>();
            int count = 0;

            int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SLOTS;
            for (int index = 0; index < containerSlots; index++) {
                Slot slot = menu.slots.get(index);
                ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                Optional<TomeItem> tome = Models.Item.asWynnItem(stack, TomeItem.class);
                if (tome.isEmpty()) {
                    continue;
                }
                count++;
                WynntilsStatRolls.Rolls rolls =
                        WynntilsStatRolls.read(tome.get().getIdentifications(), tome.get().getPossibleValues());
                rolls.actual().forEach((key, value) -> identifications.merge(key, value, Integer::sum));
            }
            if (count == 0) {
                // An empty tome menu is a menu that has not finished filling in, not a player with
                // no tomes; keeping the previous reading is the safer of the two mistakes.
                return;
            }
            observedClass = Models.Character.getClassType();
            if (!IDENTIFICATIONS.equals(identifications) || tomeCount != count) {
                IDENTIFICATIONS.clear();
                IDENTIFICATIONS.putAll(identifications);
                tomeCount = count;
                revision++;
            }
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Tome menu could not be read", e);
        }
    }

    public static Snapshot snapshot() {
        if (!isAvailable()) {
            return Snapshot.empty();
        }
        try {
            // Tomes are per character, so a reading from another class means nothing here.
            if (observedClass != null && observedClass != Models.Character.getClassType()) {
                forget();
            }
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Tome state could not be checked", e);
            return Snapshot.empty();
        }
        return new Snapshot(new LinkedHashMap<>(IDENTIFICATIONS), tomeCount, observedClass != null);
    }

    /** A value that changes whenever the reading does, for cheap staleness checks. */
    public static int revision() {
        return revision;
    }

    /** Forgets the current reading, so the next tome menu opening starts clean. */
    public static void forget() {
        IDENTIFICATIONS.clear();
        tomeCount = 0;
        observedClass = null;
        revision++;
    }
}
