package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.integrations.WynntilsItemScaleAccess;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ItemScaleTooltip {
    private static final AtomicBoolean UNAVAILABLE = new AtomicBoolean();

    // Rebuilding a tooltip re-enters the tooltip callback, so guard against recursion.
    private static final ThreadLocal<Boolean> DECORATING = ThreadLocal.withInitial(() -> false);

    private static Boolean wynntilsLoaded;

    private ItemScaleTooltip() {}

    public static void decorate(ItemStack stack, List<Component> lines) {
        if (stack == null || lines == null || lines.isEmpty() || UNAVAILABLE.get() || DECORATING.get()) {
            return;
        }
        if (!showItemScale() || !isWynntilsLoaded()) {
            return;
        }

        DECORATING.set(true);
        try {
            WynntilsItemScaleAccess.decorate(stack, lines);
        } catch (LinkageError | RuntimeException e) {
            if (UNAVAILABLE.compareAndSet(false, true)) {
                SeqClient.LOGGER.warn("[ItemScale] Wynntils item support unavailable; feature disabled.", e);
            }
        } finally {
            DECORATING.set(false);
        }
    }

    public static boolean showItemScale() {
        return isEnabled(SeqClient.getShowItemScaleSetting());
    }

    public static boolean showStatWeights() {
        return isEnabled(SeqClient.getShowItemScaleStatWeightsSetting());
    }

    private static boolean isEnabled(Setting.BooleanSetting setting) {
        return setting != null && setting.getValue();
    }

    private static boolean isWynntilsLoaded() {
        if (wynntilsLoaded == null) {
            wynntilsLoaded = FabricLoader.getInstance().isModLoaded("wynntils");
            if (!wynntilsLoaded) {
                SeqClient.LOGGER.info("[ItemScale] Wynntils not found; item tooltip and scale disabled.");
            }
        }
        return wynntilsLoaded;
    }
}
