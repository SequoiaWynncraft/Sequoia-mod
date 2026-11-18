package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.ui.SequoiaSidebarNavigation.Destination;
import com.seqwawa.seq.ui.SequoiaSidebarNavigation.Foot;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A Sequoia screen: the sidebar down the left, and the screen's own panel beside it.
 *
 * <p>It holds what each of these screens used to repeat: the screen Esc goes back to,
 * where the pointer is this frame, the sidebar's drawing and clicks, and not pausing
 * the game. A screen draws itself in {@link #draw}, which runs at the end of the frame.
 */
abstract class SequoiaPanelScreen extends Screen {
    /** The screen Esc goes back to, or null to go back to the game. */
    protected final Screen parent;
    /** Where the pointer was when this frame began. */
    protected UiPointer pointer = UiPointer.ORIGIN;
    private final Destination destination;

    protected SequoiaPanelScreen(Component title, Screen parent, Destination destination) {
        super(title);
        this.parent = parent;
        this.destination = destination;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        pointer = UiPointer.at(mouseX, mouseY);
        beforeFrame();
        UiRenderer.renderScreen(this, this::draw);
    }

    /** Catches up with what changed since the last frame, before it is drawn. Nothing by default. */
    protected void beforeFrame() {}

    /** Draws the screen, its sidebar included. */
    protected abstract void draw(UiCanvas canvas);

    /** What the sidebar keeps at its foot on a screen this tall. By default, the profile card. */
    protected Foot sidebarFoot(float screenHeight) {
        return Foot.profileCard(screenHeight);
    }

    /**
     * The screen a sidebar button hands on to the screen it opens, for Esc there to
     * go back to. By default this screen's parent, so hopping between panels doesn't
     * pile them up behind Esc.
     */
    protected Screen sidebarParent() {
        return parent;
    }

    protected final void drawSidebar(UiCanvas canvas) {
        SequoiaSidebarNavigation.render(canvas, destination, pointer, sidebarFoot(canvas.metrics().height()));
    }

    /** Hands a click to the sidebar. Returns whether one of its buttons or the profile card took it. */
    protected final boolean clickSidebar(UiPointer at) {
        float height = MinecraftUiRenderer.screenHeight();
        return SequoiaSidebarNavigation.press(at, height, destination, sidebarParent(), sidebarFoot(height));
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
