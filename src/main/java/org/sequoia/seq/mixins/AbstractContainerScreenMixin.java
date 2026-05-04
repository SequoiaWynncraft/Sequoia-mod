package org.sequoia.seq.mixins;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.sequoia.seq.ui.GuildStorageShortcutOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
    @Shadow
    protected int imageWidth;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected T menu;

    @Inject(method = "render", at = @At("TAIL"))
    private void seq$renderGuildStorageShortcut(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuildStorageShortcutOverlay.render(graphics, menu, leftPos, topPos, imageWidth, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void seq$clickGuildStorageShortcut(
            MouseButtonEvent event, boolean outsideScreen, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }

        if (GuildStorageShortcutOverlay.mouseClicked(menu, leftPos, topPos, imageWidth, event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
