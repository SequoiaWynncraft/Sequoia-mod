package org.sequoia.seq.ui;

import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.integrations.WynntilsGuildRankAccess;
import org.sequoia.seq.managers.GuildStorageTracker;

public final class GuildStorageShortcutOverlay {
    private static final String RECIPIENT = "cinfrascitizen";
    private static final String BUTTON_LABEL = "Send emeralds to " + RECIPIENT;
    private static final int SEND_EMERALDS_HOTBAR_BUTTON = 2;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 6;
    private static final int BUTTON_COLOR = 0xCC1D6B3A;
    private static final int BUTTON_HOVER_COLOR = 0xE0268147;
    private static final int BUTTON_DISABLED_COLOR = 0xAA3A3A3A;
    private static final int BUTTON_BORDER_COLOR = 0xFFB7E4B7;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DISABLED_TEXT_COLOR = 0xFFB8B8B8;

    private GuildStorageShortcutOverlay() {}

    public static void render(
            GuiGraphics graphics,
            AbstractContainerMenu menu,
            int leftPos,
            int topPos,
            int imageWidth,
            int mouseX,
            int mouseY) {
        OptionalLong currentEmeralds = GuildStorageTracker.extractCurrentEmeralds(menu);
        if (currentEmeralds.isEmpty() || !WynntilsGuildRankAccess.isChiefOrOwner()) {
            return;
        }

        ButtonBounds bounds = bounds(graphics.guiWidth(), leftPos, topPos, imageWidth);
        boolean enabled = currentEmeralds.getAsLong() > 0;
        boolean hovered = bounds.contains(mouseX, mouseY);
        int buttonColor = enabled ? (hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR) : BUTTON_DISABLED_COLOR;

        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), buttonColor);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BUTTON_BORDER_COLOR);
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                BUTTON_LABEL,
                bounds.x() + bounds.width() / 2,
                bounds.y() + (bounds.height() - Minecraft.getInstance().font.lineHeight) / 2,
                enabled ? TEXT_COLOR : DISABLED_TEXT_COLOR);

        if (hovered) {
            Component tooltip = enabled
                    ? Component.literal("Presses 3 on " + RECIPIENT + "'s storage head.")
                    : Component.literal("No emeralds are currently in guild storage.");
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }
    }

    public static boolean mouseClicked(
            AbstractContainerMenu menu,
            int leftPos,
            int topPos,
            int imageWidth,
            double mouseX,
            double mouseY) {
        OptionalLong currentEmeralds = GuildStorageTracker.extractCurrentEmeralds(menu);
        if (currentEmeralds.isEmpty() || currentEmeralds.getAsLong() <= 0) {
            return false;
        }
        if (!WynntilsGuildRankAccess.isChiefOrOwner()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ButtonBounds bounds = bounds(minecraft.getWindow().getGuiScaledWidth(), leftPos, topPos, imageWidth);
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }

        if (minecraft.player == null || minecraft.player.connection == null) {
            return true;
        }
        if (minecraft.gameMode == null) {
            NotificationAccessor.notifyPlayer("Could not send emeralds: no game mode is available.");
            return true;
        }

        Slot recipientSlot = findRecipientSlot(menu);
        if (recipientSlot == null) {
            NotificationAccessor.notifyPlayer("Could not find " + RECIPIENT + " in this guild storage menu.");
            return true;
        }

        minecraft.gameMode.handleInventoryMouseClick(
                menu.containerId,
                recipientSlot.index,
                SEND_EMERALDS_HOTBAR_BUTTON,
                ClickType.SWAP,
                minecraft.player);
        NotificationAccessor.notifyPlayer("Sending guild storage emeralds to " + RECIPIENT + ".");
        SeqClient.LOGGER.info(
                "[GuildStorage] Shortcut pressed hotbar button {} on slot {} for {}",
                SEND_EMERALDS_HOTBAR_BUTTON,
                recipientSlot.index,
                RECIPIENT);
        return true;
    }

    private static Slot findRecipientSlot(AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }

        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            if (matchesRecipient(slot.getItem())) {
                return slot;
            }
        }
        return null;
    }

    private static boolean matchesRecipient(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getHoverName().getString().toLowerCase().contains(RECIPIENT);
    }

    private static ButtonBounds bounds(int screenWidth, int leftPos, int topPos, int imageWidth) {
        int x = leftPos + imageWidth + BUTTON_MARGIN;
        if (x + BUTTON_WIDTH > screenWidth - BUTTON_MARGIN) {
            x = Math.max(BUTTON_MARGIN, leftPos - BUTTON_WIDTH - BUTTON_MARGIN);
        }
        int y = Math.max(BUTTON_MARGIN, topPos + BUTTON_MARGIN);
        return new ButtonBounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private record ButtonBounds(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
