package com.seqwawa.seq.render;

import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import net.minecraft.client.gui.GuiGraphics;

public interface MinecraftGuiOverlay {
    void renderMinecraftGuiOverlay(GuiGraphics guiGraphics, UiRenderMetrics metrics);
}
