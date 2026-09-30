package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.consumables.WarConsumable;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.game.CraftedConsumableItem;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.models.wynnitem.type.ItemEffect;
import com.wynntils.utils.type.CappedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.world.item.ItemStack;

/** Reads war consumables through Wynntils. Only call this once Wynntils is known to be loaded. */
public final class WynntilsWarConsumableAccess {
    private WynntilsWarConsumableAccess() {}

    /**
     * The crafted consumable held in {@code stack}, or empty when there is none or it has
     * no stats, as with plain healing and mana potions.
     */
    public static Optional<WarConsumable> read(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Models.Item.asWynnItem(stack, CraftedConsumableItem.class)
                    .flatMap(WynntilsWarConsumableAccess::toWarConsumable);
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read consumable", e);
            return Optional.empty();
        }
    }

    /**
     * When the player joined the world they are on, or empty outside a world. Wynntils moves
     * it on every world join, including coming back from character selection.
     */
    public static OptionalLong currentSession() {
        try {
            if (!Models.WorldState.onWorld()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Models.WorldState.getServerJoinTimestamp());
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read world session", e);
            return OptionalLong.empty();
        }
    }

    private static Optional<WarConsumable> toWarConsumable(CraftedConsumableItem item) {
        List<String> affectedStats = new ArrayList<>();
        int positiveStats = 0;
        int negativeStats = 0;
        for (StatActualValue stat : item.getIdentifications()) {
            StatType type = stat.statType();
            affectedStats.add(type.getKey());
            int polarity = WarConsumable.statPolarity(
                    stat.value(), type.calculateAsInverted(), type.displayAsInverted());
            if (polarity > 0) {
                positiveStats++;
            } else if (polarity < 0) {
                negativeStats++;
            }
        }
        for (ItemEffect effect : item.getEffects()) {
            affectedStats.add(effect.type());
            if (effect.value() > 0) {
                positiveStats++;
            } else if (effect.value() < 0) {
                negativeStats++;
            }
        }
        if (positiveStats == 0 && negativeStats == 0) {
            return Optional.empty();
        }

        String fingerprint = WarConsumable.fingerprint(
                String.valueOf(item.getConsumableType()), item.getName(), affectedStats);
        CappedValue uses = item.getUses();
        return Optional.of(new WarConsumable(
                fingerprint,
                uses == null ? 0 : uses.current(),
                item.getDuration(),
                positiveStats,
                negativeStats));
    }
}
