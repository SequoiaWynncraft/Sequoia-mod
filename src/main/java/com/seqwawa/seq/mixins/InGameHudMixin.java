package com.seqwawa.seq.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import com.seqwawa.seq.accessors.EventBusAccessor;
import com.seqwawa.seq.events.Render2DEvent;
import com.seqwawa.seq.managers.ChatMediaEmbedManager;
import com.seqwawa.seq.ui.SequoiaScreen;
import com.seqwawa.seq.ui.SettingsScreen;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.seqwawa.seq.client.SeqClient.mc;

@Mixin(Gui.class)
public class InGameHudMixin implements EventBusAccessor {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void seq$onRenderCrosshair(GuiGraphics context, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (mc.screen instanceof SequoiaScreen || mc.screen instanceof SettingsScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void seq$onRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (mc.screen == null) {
            UiRenderer.renderHud(canvas -> {
                seqdispatch(new Render2DEvent(context, tickCounter.getGameTimeDeltaPartialTick(true)));
                ChatMediaEmbedManager.getInstance().render(canvas);
            });
        } else if (mc.screen instanceof ChatScreen chatScreen) {
            UiRenderer.renderScreen(chatScreen, ChatMediaEmbedManager.getInstance()::render);
        }
    }
}
