package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.update.UpdateManager;
import com.seqwawa.seq.update.UpdateState;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * The update prompt: a view of {@link UpdateManager#state()}, redrawn every frame, so progress,
 * results and failures appear as they happen. Buttons call the manager's actions; the manager
 * decides what each does in the current state.
 */
public class UpdatePromptScreen extends Screen {
    private static final float PANEL_WIDTH = 380;
    private static final float PANEL_HEIGHT = 210;
    private static final float BUTTON_WIDTH = 108;
    private static final float BUTTON_HEIGHT = 22;
    private static final float BUTTON_SPACING = 10;
    private static final float LINE_HEIGHT = 15;
    /** Characters per line at the prompt's text size; leaves a margin for wide glyphs. */
    private static final int WRAP_CHARACTERS = 54;
    private static final int MAX_LINES = 7;
    private static final Duration LONG_INSTALLER_WAIT = Duration.ofMinutes(10);

    /** A button's label and what it does, given the open prompt. */
    public record Button(String label, Consumer<UpdatePromptScreen> action) {}

    public record Placed(Button button, float x, float y) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + BUTTON_WIDTH && pointY >= y && pointY <= y + BUTTON_HEIGHT;
        }
    }

    /** @param progress 0..1 for a download bar, or negative for none */
    public record Content(String title, List<String> lines, float progress, List<Button> buttons) {}

    private final Screen parent;
    private float mouseX;
    private float mouseY;

    public UpdatePromptScreen(Screen parent) {
        super(Component.literal("Sequoia update"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.mouseX = MinecraftUiRenderer.mouseX(mouseX);
        this.mouseY = MinecraftUiRenderer.mouseY(mouseY);
        UpdateManager manager = UpdateManager.getInstance();
        Content content = content(manager.state(), manager.runningVersion(), manager.testSource());

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));
            float panelX = (screenWidth - PANEL_WIDTH) / 2f;
            float panelY = (screenHeight - PANEL_HEIGHT) / 2f;
            canvas.fillRect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, color(BACKGROUND_BODY_OPAQUE));

            String font = SeqClient.getFontManager().getSelectedFont();
            float centerX = panelX + PANEL_WIDTH / 2f;
            text(canvas, font, 16, color(ACCENT_PRIMARY_HOVER), centerX, panelY + 24, content.title());
            float lineY = panelY + 52;
            for (String line : content.lines()) {
                text(canvas, font, 11, color(TEXT_SECONDARY), centerX, lineY, line);
                lineY += LINE_HEIGHT;
            }
            if (content.progress() >= 0) {
                float barX = panelX + 30;
                float barY = panelY + PANEL_HEIGHT - 66;
                float barWidth = PANEL_WIDTH - 60;
                canvas.fillRect(barX, barY, barWidth, 8, color(CONTROL_INPUT_HOVER));
                canvas.fillRect(barX, barY, barWidth * content.progress(), 8, color(ACCENT_PRIMARY_HOVER));
            }
            for (Placed placed : layout(content.buttons(), screenWidth, screenHeight)) {
                boolean hovered = placed.contains(this.mouseX, this.mouseY);
                canvas.fillRect(placed.x(), placed.y(), BUTTON_WIDTH, BUTTON_HEIGHT,
                        hovered ? color(ACCENT_PRIMARY_DARK_HOVER) : color(CONTROL_INPUT_HOVER));
                text(canvas, font, 11, color(TEXT_SECONDARY), placed.x() + BUTTON_WIDTH / 2f,
                        placed.y() + BUTTON_HEIGHT / 2f, placed.button().label());
            }
        });
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        float x = MinecraftUiRenderer.mouseX(click.x());
        float y = MinecraftUiRenderer.mouseY(click.y());
        UpdateManager manager = UpdateManager.getInstance();
        Content content = content(manager.state(), manager.runningVersion(), manager.testSource());
        for (Placed placed : layout(content.buttons(), MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight())) {
            if (placed.contains(x, y)) {
                placed.button().action().accept(this);
                return true;
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    /** Esc does nothing while downloading (use Cancel) or while the JAR swap runs, which cannot be undone part-way. */
    @Override
    public boolean shouldCloseOnEsc() {
        UpdateState state = UpdateManager.getInstance().state();
        return !(state instanceof UpdateState.Downloading || state instanceof UpdateState.Installing);
    }

    /** Esc on an offered update means Ignore; elsewhere it just closes the prompt. */
    @Override
    public void onClose() {
        if (UpdateManager.getInstance().state() instanceof UpdateState.Available) UpdateManager.getInstance().ignore();
        close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void close() {
        SeqClient.mc.setScreen(parent);
    }

    /** One row of buttons, centred; rendering and clicking both place buttons with this. */
    public static List<Placed> layout(List<Button> buttons, float screenWidth, float screenHeight) {
        float panelX = (screenWidth - PANEL_WIDTH) / 2f;
        float panelY = (screenHeight - PANEL_HEIGHT) / 2f;
        float rowWidth = buttons.size() * BUTTON_WIDTH + Math.max(0, buttons.size() - 1) * BUTTON_SPACING;
        float x = panelX + (PANEL_WIDTH - rowWidth) / 2f;
        float y = panelY + PANEL_HEIGHT - 42;
        List<Placed> placed = new ArrayList<>();
        for (Button button : buttons) {
            placed.add(new Placed(button, x, y));
            x += BUTTON_WIDTH + BUTTON_SPACING;
        }
        return placed;
    }

    /** What the prompt shows in {@code state}; drawing and clicking both use this. */
    public static Content content(UpdateState state, String running, String testSource) {
        Button close = new Button("Close", UpdatePromptScreen::close);
        Button releasePage = new Button("Release page", screen -> UpdateManager.getInstance().openReleasePage());
        Button ignore = new Button("Ignore", UpdatePromptScreen::ignore);
        List<String> lines = new ArrayList<>();
        Content content = switch (state) {
            case UpdateState.Idle idle -> new Content("Checking for updates", lines(lines, "Asking GitHub for the latest release..."), -1, List.of(close));
            case UpdateState.Checking checking -> new Content("Checking for updates", lines(lines, "Asking GitHub for the latest release..."), -1, List.of(close));
            case UpdateState.UpToDate upToDate -> new Content("Sequoia is up to date",
                    lines(lines, "Sequoia " + upToDate.running() + " is the latest release."), -1, List.of(close));
            case UpdateState.CheckFailed failed -> new Content("Couldn't check for updates",
                    lines(lines, failed.reason(), note(failed.previous())), -1,
                    List.of(close, new Button("Try again", screen -> UpdateManager.getInstance().checkNow())));
            case UpdateState.Available available when available.release().downloadedCopy() -> new Content(
                    "Sequoia update ready to install",
                    lines(lines, "Current: " + running + "   Downloaded: " + available.release().tag(),
                            "The download is verified and ready.", platformLine(), note(available.previous())),
                    -1, List.of(ignore, new Button("Install", screen -> UpdateManager.getInstance().update(false)),
                            new Button("Install + Exit", screen -> UpdateManager.getInstance().update(true))));
            case UpdateState.Available available -> new Content("Sequoia update available",
                    lines(lines, "Current: " + running + "   Latest: " + available.release().tag(),
                            UpdateState.megabytes(available.release().jar().size()) + " download. " + platformLine(),
                            note(available.previous())),
                    -1, List.of(ignore, new Button("Update", screen -> UpdateManager.getInstance().update(false)),
                            new Button("Update + Exit", screen -> UpdateManager.getInstance().update(true))));
            case UpdateState.Attention attention -> new Content("Sequoia update",
                    lines(lines, note(attention.previous())), -1,
                    List.of(close, new Button("Check for updates", screen -> UpdateManager.getInstance().checkNow())));
            case UpdateState.WaitingForInstaller waiting -> new Content("Sequoia update installing",
                    lines(lines, note(waiting.previous()) + (waiting.pid() > 0 ? " (installer process " + waiting.pid() + ")." : "."),
                            "Sequoia will check again when it finishes.",
                            Duration.between(waiting.since(), Instant.now()).compareTo(LONG_INSTALLER_WAIT) > 0
                                    ? "If it doesn't finish, end that process or restart Windows." : null),
                    -1, List.of(close));
            case UpdateState.Downloading downloading -> downloading(downloading, lines);
            case UpdateState.Installing installing -> new Content("Installing Sequoia " + installing.release().tag(),
                    lines(lines, "Replacing the mod file..."), -1, List.of());
            case UpdateState.Installed installed -> new Content("Sequoia " + installed.release().tag() + " installed",
                    lines(lines, "Restart Minecraft to use it."), -1,
                    List.of(close, new Button("Exit now", screen -> UpdateManager.getInstance().exitNow())));
            case UpdateState.Staged staged -> new Content("Sequoia " + staged.release().tag() + " is ready",
                    lines(lines, "It installs when Minecraft closes."), -1,
                    List.of(new Button("Cancel install", screen -> UpdateManager.getInstance().cancelStaged()), close,
                            new Button("Exit now", screen -> UpdateManager.getInstance().exitNow())));
            case UpdateState.Failed failed -> new Content("Sequoia update failed",
                    lines(lines, failed.reason(), "Details are in logs/latest.log."), -1,
                    List.of(close, releasePage, new Button("Retry", screen -> UpdateManager.getInstance().retry())));
            case UpdateState.ManualOnly manual -> new Content("Install Sequoia " + manual.release().tag() + " by hand",
                    lines(lines, manual.reason(), "Download it from the release page and replace the Sequoia JAR in your mods folder."),
                    -1, List.of(close, releasePage,
                            new Button("Mods folder", screen -> UpdateManager.getInstance().openModsFolder())));
            case UpdateState.Incompatible incompatible -> new Content("Sequoia " + incompatible.release().tag() + " can't run here",
                    lines(lines, incompatible.reason()), -1, List.of(new Button("Close", UpdatePromptScreen::ignore), releasePage));
        };
        if (testSource != null) content.lines().add("(test source: " + testSource + ")");
        return content;
    }

    private static Content downloading(UpdateState.Downloading downloading, List<String> lines) {
        long received = downloading.received();
        long total = downloading.total();
        lines(lines, "Downloading " + downloading.release().tag() + ": " + UpdateState.megabytes(received) + " of "
                + UpdateState.megabytes(total) + String.format(Locale.ROOT, " (%d%%)", total > 0 ? received * 100 / total : 0));
        if (downloading.secondsWithoutData() >= 5)
            lines(lines, "Waiting for data (" + downloading.secondsWithoutData() + " s)...");
        if (downloading.exitAfter()) lines(lines, "Minecraft will close when the update is installed.");
        return new Content("Downloading Sequoia update", lines, total > 0 ? Math.min(1f, (float) received / total) : 0,
                List.of(new Button("Cancel", screen -> UpdateManager.getInstance().cancel())));
    }

    private void ignore() {
        UpdateManager.getInstance().ignore();
        close();
    }

    private static String platformLine() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "It installs when Minecraft closes."
                : "Restart Minecraft after it installs.";
    }

    private static String note(UpdateState.Attempt attempt) {
        return attempt == null ? null : attempt.detail();
    }

    /**
     * Appends each non-null text, word-wrapped, up to the panel's line budget. A word longer than a
     * line, such as a file path, is broken into line-sized pieces rather than running off the panel.
     */
    private static List<String> lines(List<String> into, String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                for (int start = 0; start < word.length() || start == 0; start += WRAP_CHARACTERS) {
                    String piece = word.substring(start, Math.min(word.length(), start + WRAP_CHARACTERS));
                    if (line.length() > 0 && line.length() + 1 + piece.length() > WRAP_CHARACTERS) {
                        add(into, line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0 && start == 0) line.append(' ');
                    line.append(piece);
                }
            }
            if (line.length() > 0) add(into, line.toString());
        }
        return into;
    }

    private static void add(List<String> into, String line) {
        if (into.size() < MAX_LINES) into.add(line);
        else if (into.size() == MAX_LINES && !into.get(MAX_LINES - 1).endsWith("...")) into.set(MAX_LINES - 1, into.get(MAX_LINES - 1) + "...");
    }

    private static void text(UiCanvas canvas, String font, float size, Color color, float x, float y, String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(font, size, color, UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }
}
