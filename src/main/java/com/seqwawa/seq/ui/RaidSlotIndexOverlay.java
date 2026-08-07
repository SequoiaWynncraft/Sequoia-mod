package com.seqwawa.seq.ui;

import com.seqwawa.seq.managers.RaidStartScreenDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Debug overlay that labels every menu slot while the raid-start screen is open. */
public final class RaidSlotIndexOverlay {
    private static final int SLOT_SIZE = 16;
    private static final int LABEL_HEIGHT = 10;
    private static final int BACKGROUND_COLOR = 0xC0000000;
    private static final int SLOT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int GAMBIT_SLOT_TEXT_COLOR = 0xFFFFFF55;
    private static final String DETECTED_LABEL = "SeqMod: raid start detected (gambit slots in yellow)";

    private RaidSlotIndexOverlay() {}

    public static void render(
            GuiGraphics graphics,
            AbstractContainerMenu menu,
            Component screenTitle,
            int leftPos,
            int topPos) {
        if (graphics == null
                || menu == null
                || !RaidStartScreenDetector.isRaidStartScreen(screenTitle)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        if (font == null) {
            return;
        }

        renderDetectionLabel(graphics, font, leftPos, topPos);
        for (int menuSlotIndex = 0; menuSlotIndex < menu.slots.size(); menuSlotIndex++) {
            Slot slot = menu.slots.get(menuSlotIndex);
            renderSlotIndex(graphics, font, slot, menuSlotIndex, leftPos, topPos);
        }
    }

    private static void renderDetectionLabel(GuiGraphics graphics, Font font, int leftPos, int topPos) {
        int labelX = leftPos;
        int labelY = Math.max(2, topPos - LABEL_HEIGHT - 2);
        int labelWidth = font.width(DETECTED_LABEL) + 4;
        graphics.fill(labelX, labelY, labelX + labelWidth, labelY + LABEL_HEIGHT, BACKGROUND_COLOR);
        graphics.drawString(font, DETECTED_LABEL, labelX + 2, labelY + 1, GAMBIT_SLOT_TEXT_COLOR);
    }

    private static void renderSlotIndex(
            GuiGraphics graphics,
            Font font,
            Slot slot,
            int menuSlotIndex,
            int leftPos,
            int topPos) {
        String label = Integer.toString(menuSlotIndex);
        int textWidth = font.width(label);
        int slotX = leftPos + slot.x;
        int slotY = topPos + slot.y;
        int textX = slotX + (SLOT_SIZE - textWidth) / 2;
        int textY = slotY + (SLOT_SIZE - font.lineHeight) / 2;
        int backgroundX = textX - 1;
        int backgroundY = textY - 1;
        int textColor = RaidStartScreenDetector.isGambitSlot(menuSlotIndex)
                ? GAMBIT_SLOT_TEXT_COLOR
                : SLOT_TEXT_COLOR;

        graphics.fill(
                backgroundX,
                backgroundY,
                backgroundX + textWidth + 2,
                backgroundY + font.lineHeight + 2,
                BACKGROUND_COLOR);
        graphics.drawString(font, label, textX, textY, textColor);
    }
}
