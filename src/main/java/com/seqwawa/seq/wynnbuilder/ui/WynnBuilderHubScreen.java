package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_SUCCESS;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_WARNING;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.wynnbuilder.WynnBuilderSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Entry point for the WynnBuilder tools: pick the builder or the crafter. */
public final class WynnBuilderHubScreen extends Screen {
    private static final float BUTTON_WIDTH = 150;
    private static final float BUTTON_HEIGHT = 26;
    private static final float BUTTON_SPACING = 9;

    private final Screen parent;
    private final WynnBuilderSession session = WynnBuilderSession.getInstance();

    private float mouseX;
    private float mouseY;

    public WynnBuilderHubScreen(Screen parent) {
        super(Component.literal("WynnBuilder"));
        this.parent = parent;
        // Start the download as soon as the section is opened so the tools are ready on arrival.
        session.ensureData();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pointerX, int pointerY, float partialTick) {
        super.render(guiGraphics, pointerX, pointerY, partialTick);
        mouseX = MinecraftUiRenderer.mouseX(pointerX);
        mouseY = MinecraftUiRenderer.mouseY(pointerY);
        UiRenderer.renderScreen(this, this::draw);
    }

    private void draw(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();

        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 180));

        float titleY = height * 0.3f;
        WynnBuilderUi.drawCentered(canvas, "WynnBuilder", width / 2f, titleY, 24, color(ACCENT_PRIMARY));

        float startY = titleY + 40;
        float centerX = width / 2f - BUTTON_WIDTH / 2f;

        boolean ready = session.isReady();
        WynnBuilderUi.drawButton(canvas, centerX, startY, BUTTON_WIDTH, BUTTON_HEIGHT, "Builder", mouseX, mouseY, ready);
        WynnBuilderUi.drawButton(canvas, centerX, startY + BUTTON_HEIGHT + BUTTON_SPACING,
                BUTTON_WIDTH, BUTTON_HEIGHT, "Crafter", mouseX, mouseY, ready);

        String status = ready ? "Data " + session.data().version() + " ready" : session.status();
        WynnBuilderUi.drawCentered(canvas, status, width / 2f,
                startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2 + 16, 11,
                ready ? color(CONTROL_SUCCESS) : color(CONTROL_WARNING));

        if (!ready) {
            WynnBuilderUi.drawCentered(canvas,
                    "Item data is downloaded from wynnbuilder.github.io on first use",
                    width / 2f, startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2 + 34, 10, color(TEXT_MUTED));
        }
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0 && session.isReady()) {
            float pointerX = MinecraftUiRenderer.mouseX(click.x());
            float pointerY = MinecraftUiRenderer.mouseY(click.y());
            float height = MinecraftUiRenderer.screenHeight();
            float width = MinecraftUiRenderer.screenWidth();
            float startY = height * 0.3f + 40;
            float centerX = width / 2f - BUTTON_WIDTH / 2f;

            if (WynnBuilderUi.contains(pointerX, pointerY, centerX, startY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                SeqClient.mc.setScreen(new BuilderScreen(this));
            } else if (WynnBuilderUi.contains(pointerX, pointerY, centerX,
                    startY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                SeqClient.mc.setScreen(new CrafterScreen(this));
            }
        }
        return super.mouseClicked(click, outsideScreen);
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
