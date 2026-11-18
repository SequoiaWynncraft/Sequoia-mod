package com.seqwawa.seq.ui;

import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Where the pointer is, in UI pixels: the space Sequoia screens lay out and draw in.
 * Minecraft hands screens the mouse in its own GUI units, and the two differ by the
 * GUI scale and the mod's UI size, so every screen converts it here.
 */
public record UiPointer(float x, float y) {

    /** Where a screen's pointer starts, before its first frame. */
    public static final UiPointer ORIGIN = new UiPointer(0, 0);

    /**
     * The pointer at Minecraft GUI coordinates, as {@code render}, {@code mouseScrolled}
     * and the like receive them. While the development preview holds the pointer still
     * for its screenshots, this is where it holds it.
     */
    public static UiPointer at(double guiX, double guiY) {
        return new UiPointer(MinecraftUiRenderer.mouseX(guiX), MinecraftUiRenderer.mouseY(guiY));
    }

    /** Where a click, a release or a drag happened. */
    public static UiPointer at(MouseButtonEvent event) {
        return at(event.x(), event.y());
    }
}
