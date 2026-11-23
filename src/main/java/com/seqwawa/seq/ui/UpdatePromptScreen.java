package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.update.ReleaseNotes;
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
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * The update prompt: a view of {@link UpdateManager#state()}, redrawn every frame, so progress,
 * results and failures appear as they happen. Buttons call the manager's actions; the manager
 * decides what each does in the current state. A release's summary shows under the title, and
 * "What's new" opens its full notes.
 */
public class UpdatePromptScreen extends Screen {
    private static final float PANEL_WIDTH = 380;
    /** The panel's height with up to {@link #MAX_LINES} lines; more lines make it taller. */
    private static final float PANEL_HEIGHT = 210;
    /** From the panel's top to the middle of its first line. */
    private static final float FIRST_LINE = 52;
    /** From the middle of the last line to the panel's foot, with the buttons and progress bar. */
    private static final float FOOT = 68;
    /** Space kept between the panel and the window's top and bottom. */
    private static final float SCREEN_MARGIN = 8;
    private static final float BUTTON_WIDTH = 108;
    private static final float BUTTON_HEIGHT = 22;
    private static final float BUTTON_SPACING = 10;
    /** Buttons that do not fit at full width share the row at this spacing, this far inside the panel's edges. */
    private static final float NARROW_BUTTON_SPACING = 8;
    private static final float BUTTON_ROW_MARGIN = 12;
    private static final float LINE_HEIGHT = 15;
    private static final float SUMMARY_GAP = 6;
    /** Characters per line at the prompt's text size; leaves a margin for wide glyphs. */
    private static final int WRAP_CHARACTERS = 54;
    private static final int MAX_LINES = 7;
    /** The summary is written as two to four lines; this leaves room for some of them to wrap. */
    private static final int MAX_SUMMARY_LINES = 6;
    private static final Duration LONG_INSTALLER_WAIT = Duration.ofMinutes(10);

    /** A button's label and what it does, given the open prompt. */
    public record Button(String label, Consumer<UpdatePromptScreen> action) {}

    public record Placed(Button button, float x, float y, float width) {
        UiBounds bounds() {
            return new UiBounds(x, y, width, BUTTON_HEIGHT);
        }
    }

    /**
     * @param summary the release's summary, wrapped, shown under the title; empty for none
     * @param progress 0..1 for a download bar, or negative for none
     */
    public record Content(String title, List<String> summary, List<String> lines, float progress, List<Button> buttons) {
        public Content(String title, List<String> lines, float progress, List<Button> buttons) {
            this(title, List.of(), lines, progress, buttons);
        }
    }

    /**
     * Where the panel and its buttons go at one window size, and the summary lines that fit;
     * drawing and clicking both use it.
     */
    public record Frame(UiBounds panel, List<String> summary, float linesY, List<Placed> buttons) {}

    private final Screen parent;
    private final Supplier<UpdateState> state;
    /** Where the pointer was when this frame began. */
    private UiPointer pointer = UiPointer.ORIGIN;

    public UpdatePromptScreen(Screen parent) {
        this(parent, () -> UpdateManager.getInstance().state());
    }

    /** A prompt showing {@code state} instead of the updater's, as the development preview does. */
    public UpdatePromptScreen(Screen parent, Supplier<UpdateState> state) {
        super(Component.literal("Sequoia update"));
        this.parent = parent;
        this.state = state;
    }

    private Content content() {
        UpdateManager manager = UpdateManager.getInstance();
        return content(state.get(), manager.runningVersion(), manager.testSource());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        pointer = UiPointer.at(mouseX, mouseY);
        Content content = content();

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            Frame frame = frame(content, screenWidth, screenHeight);
            UiBounds panel = frame.panel();
            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY));
            panel.fill(canvas, color(BACKGROUND_BODY_OPAQUE));

            String font = SeqClient.getFontManager().getSelectedFont();
            float centerX = panel.centerX();
            text(canvas, font, 16, color(ACCENT_PRIMARY_HOVER), centerX, panel.y() + 24, content.title());
            float lineY = panel.y() + FIRST_LINE;
            for (String line : frame.summary()) {
                text(canvas, font, 11, color(TEXT_PRIMARY), centerX, lineY, line);
                lineY += LINE_HEIGHT;
            }
            lineY = frame.linesY();
            for (String line : content.lines()) {
                text(canvas, font, 11, color(TEXT_SECONDARY), centerX, lineY, line);
                lineY += LINE_HEIGHT;
            }
            if (content.progress() >= 0) {
                float barX = panel.x() + 30;
                float barY = panel.bottom() - 66;
                float barWidth = PANEL_WIDTH - 60;
                canvas.fillRect(barX, barY, barWidth, 8, color(CONTROL_INPUT_HOVER));
                canvas.fillRect(barX, barY, barWidth * content.progress(), 8, color(ACCENT_PRIMARY_HOVER));
            }
            for (Placed placed : frame.buttons()) {
                UiBounds button = placed.bounds();
                button.fill(canvas, button.contains(pointer) ? color(ACCENT_PRIMARY_DARK_HOVER) : color(CONTROL_INPUT_HOVER));
                text(canvas, font, 11, color(TEXT_SECONDARY), button.centerX(), button.centerY(), placed.button().label());
            }
        });
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        UiPointer at = UiPointer.at(click);
        Frame frame = frame(content(), MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        for (Placed placed : frame.buttons()) {
            if (placed.bounds().contains(at)) {
                placed.button().action().accept(this);
                return true;
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    /** Esc does nothing while downloading (use Cancel) or while the JAR swap runs, which cannot be undone part-way. */
    @Override
    public boolean shouldCloseOnEsc() {
        UpdateState shown = state.get();
        return !(shown instanceof UpdateState.Downloading || shown instanceof UpdateState.Installing);
    }

    /** Esc on an offered update means Ignore; elsewhere it just closes the prompt. */
    @Override
    public void onClose() {
        if (state.get() instanceof UpdateState.Available) UpdateManager.getInstance().ignore();
        close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void close() {
        SeqClient.mc.setScreen(parent);
    }

    private void openWhatsNew(String heading, ReleaseNotes notes) {
        SeqClient.mc.setScreen(new WhatsNewScreen(this, heading, notes));
    }

    /**
     * The panel, centred and as tall as its lines need, and one row of buttons along its foot.
     * A summary that would make the panel taller than the window keeps only the lines that fit;
     * "What's new" shows all of it.
     */
    public static Frame frame(Content content, float screenWidth, float screenHeight) {
        int lines = content.lines().size();
        List<String> summary = content.summary();
        float height = height(summary.size(), lines);
        int fit = summary.size();
        while (fit > 0 && height > screenHeight - 2 * SCREEN_MARGIN) height = height(--fit, lines);
        if (fit < summary.size()) {
            summary = new ArrayList<>(summary.subList(0, fit));
            if (fit > 0) summary.set(fit - 1, summary.get(fit - 1) + "...");
        }
        UiBounds panel = new UiBounds((screenWidth - PANEL_WIDTH) / 2f, (screenHeight - height) / 2f, PANEL_WIDTH, height);
        float linesY = panel.y() + FIRST_LINE + summary.size() * LINE_HEIGHT + (summary.isEmpty() || lines == 0 ? 0 : SUMMARY_GAP);
        return new Frame(panel, List.copyOf(summary), linesY, buttons(content.buttons(), panel));
    }

    private static float height(int summaryLines, int lines) {
        int all = summaryLines + lines;
        float gap = summaryLines > 0 && lines > 0 ? SUMMARY_GAP : 0;
        return Math.max(PANEL_HEIGHT, FIRST_LINE + Math.max(0, all - 1) * LINE_HEIGHT + gap + FOOT);
    }

    /** One row, centred; buttons that would not fit at full width share the panel's width. */
    private static List<Placed> buttons(List<Button> buttons, UiBounds panel) {
        int count = buttons.size();
        float width = BUTTON_WIDTH;
        float spacing = BUTTON_SPACING;
        if (count * BUTTON_WIDTH + (count - 1) * BUTTON_SPACING > PANEL_WIDTH - 2 * BUTTON_ROW_MARGIN) {
            spacing = NARROW_BUTTON_SPACING;
            width = (PANEL_WIDTH - 2 * BUTTON_ROW_MARGIN - (count - 1) * spacing) / count;
        }
        float rowWidth = count * width + Math.max(0, count - 1) * spacing;
        float x = panel.x() + (PANEL_WIDTH - rowWidth) / 2f;
        float y = panel.bottom() - 42;
        List<Placed> placed = new ArrayList<>();
        for (Button button : buttons) {
            placed.add(new Placed(button, x, y, width));
            x += width + spacing;
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
        return withNotes(content, state, running);
    }

    /**
     * The release's summary under the title and a "What's new" button first in the row, for an offered,
     * installed or running release whose notes are known. Other states show only what happened.
     */
    private static Content withNotes(Content content, UpdateState state, String running) {
        record Shown(String version, ReleaseNotes notes) {}
        Shown shown = switch (state) {
            case UpdateState.Available available -> new Shown(available.release().version(), available.release().notes());
            case UpdateState.ManualOnly manual -> new Shown(manual.release().version(), manual.release().notes());
            case UpdateState.Installed installed -> new Shown(installed.release().version(), installed.release().notes());
            case UpdateState.Staged staged -> new Shown(staged.release().version(), staged.release().notes());
            case UpdateState.UpToDate upToDate -> new Shown(running, upToDate.notes());
            default -> null;
        };
        if (shown == null || shown.notes() == null || shown.notes().isEmpty()) return content;
        String heading = "What's new in Sequoia " + shown.version();
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button("What's new", screen -> screen.openWhatsNew(heading, shown.notes())));
        buttons.addAll(content.buttons());
        List<String> summary = lines(new ArrayList<>(), MAX_SUMMARY_LINES, shown.notes().summary().toArray(String[]::new));
        return new Content(content.title(), summary, content.lines(), content.progress(), List.copyOf(buttons));
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

    private static List<String> lines(List<String> into, String... texts) {
        return lines(into, MAX_LINES, texts);
    }

    /**
     * Appends each non-null text, word-wrapped, up to {@code max} lines. A word longer than a line,
     * such as a file path, is broken into line-sized pieces rather than running off the panel.
     */
    private static List<String> lines(List<String> into, int max, String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                for (int start = 0; start < word.length() || start == 0; start += WRAP_CHARACTERS) {
                    String piece = word.substring(start, Math.min(word.length(), start + WRAP_CHARACTERS));
                    if (line.length() > 0 && line.length() + 1 + piece.length() > WRAP_CHARACTERS) {
                        add(into, max, line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0 && start == 0) line.append(' ');
                    line.append(piece);
                }
            }
            if (line.length() > 0) add(into, max, line.toString());
        }
        return into;
    }

    private static void add(List<String> into, int max, String line) {
        if (into.size() < max) into.add(line);
        else if (into.size() == max && !into.get(max - 1).endsWith("...")) into.set(max - 1, into.get(max - 1) + "...");
    }

    private static void text(UiCanvas canvas, String font, float size, Color color, float x, float y, String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(font, size, color, UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }
}
