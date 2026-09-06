package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.CENTER;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.LEFT;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.RIGHT;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.GuildRaidProgressService.State;
import com.seqwawa.seq.model.GuildRaidProgress;
import com.seqwawa.seq.model.SeqRaid;
import com.seqwawa.seq.model.SeqTier;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public final class AchievementsScreen extends Screen {

    private static final float MARGIN = 14;
    private static final float HEADER_HEIGHT = 42;
    private static final float CARD_HEIGHT = 126;
    private static final float CARD_GAP = 10;
    private static final float SUMMARY_HEIGHT = 144;
    private static final float SECTION_HEIGHT = 38;
    private static final float SCROLL_SPEED = 30;

    private final Screen parent;
    private final GuildRaidProgressService service = GuildRaidProgressService.getInstance();

    private List<Row> rows = List.of();
    private State state = State.LOADING;
    private GuildRaidProgress shown = GuildRaidProgress.EMPTY;
    private float scrollOffset;
    private float maxScroll;
    private float mouseX;
    private float mouseY;

    public AchievementsScreen(Screen parent) {
        super(Component.literal("Sequoia Achievements"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        refresh();
    }

    private void refresh() {
        state = service.state();
        shown = service.progress();
        rows = buildRows(shown);
        if (state != State.READY) {
            scrollOffset = 0;
            maxScroll = 0;
        }
    }

    static List<Row> buildRows(GuildRaidProgress progress) {
        List<Row> rows = new ArrayList<>(SeqRaid.values().length + 1);
        for (SeqRaid raid : SeqRaid.values()) {
            rows.add(row(
                    raid.displayName(),
                    raid.code(),
                    raid.assetKey(),
                    progress.count(raid),
                    progress.tier(raid),
                    SeqTier.SINGLE_RAID,
                    false));
        }
        rows.add(row(
                "All Guild Raids",
                "All Raids",
                "icon",
                progress.totalCount(),
                progress.totalTier(),
                SeqTier.ALL_RAIDS,
                true));
        return List.copyOf(rows);
    }

    private static Row row(
            String name, String compactName, String icon, int count, SeqTier tier, int scale, boolean total) {
        SeqTier next = SeqTier.next(count, tier, scale);
        return new Row(name, compactName, icon, count, tier, next == null ? 0 : next.threshold(scale), next, total);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pointerX, int pointerY, float partialTick) {
        super.render(guiGraphics, pointerX, pointerY, partialTick);
        if (state != service.state() || !shown.equals(service.progress())) {
            refresh();
            triggerImmediateNarration(true);
        }
        mouseX = MinecraftUiRenderer.mouseX(pointerX);
        mouseY = MinecraftUiRenderer.mouseY(pointerY);
        UiRenderer.renderScreen(this, this::renderScreen);
    }

    private void renderScreen(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        float contentX = SequoiaSidebarNavigation.WIDTH;
        PanelLayout panel = panelLayout(width, height, Float.MAX_VALUE);
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(contentX, 0, Math.max(0, width - contentX), HEADER_HEIGHT, color(BACKGROUND_HEADER));
        canvas.fillRect(contentX, HEADER_HEIGHT - 1, Math.max(0, width - contentX), 1, color(ACCENT_PRIMARY_DARK));
        text(canvas, "Achievements", width - MARGIN, HEADER_HEIGHT / 2, 18, color(ACCENT_PRIMARY_HOVER), RIGHT);
        SequoiaSidebarNavigation.render(canvas, SequoiaSidebarNavigation.Destination.ACHIEVEMENTS, mouseX, mouseY);

        if (state != State.READY) {
            canvas.fillRect(panel.x(), panel.y(), panel.width(), panel.height(), color(BACKGROUND_CONTENT));
            fittedText(canvas, "Guild raid progress", panel.x() + 14, panel.y() + 26,
                    Math.max(0, panel.width() - 28), 16, color(ACCENT_PRIMARY_HOVER));
            fittedText(canvas, statusLine(), panel.x() + 14, panel.y() + 54,
                    Math.max(0, panel.width() - 28), 12, color(TEXT_SECONDARY));
            return;
        }

        maxScroll = Math.max(0, contentHeight(panel.width(), rows.size() - 1) - panel.height());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        canvas.save();
        canvas.scissor(panel.x(), panel.y(), panel.width(), panel.height());
        renderProgress(canvas, rows, panel.x(), panel.y() - scrollOffset, Math.max(0, panel.width() - 8));
        canvas.restore();
        if (maxScroll > 0 && panel.height() > 0) {
            float thumb = Math.min(panel.height(), Math.max(20, panel.height() * panel.height() / (panel.height() + maxScroll)));
            float y = panel.y() + (panel.height() - thumb) * scrollOffset / maxScroll;
            canvas.fillRect(panel.right() - 3, panel.y(), 3, panel.height(), color(CONTROL_TRACK));
            canvas.fillRect(panel.right() - 3, y, 3, thumb, color(CONTROL_THUMB));
        }
    }

    /** Draws the same progress composition for the live screen and rendering checks. */
    static void renderProgress(UiCanvas canvas, List<Row> rows, float x, float y, float width) {
        if (rows.isEmpty() || width <= 0) return;
        renderCard(canvas, rows.getLast(), x, y, width, true);
        y += SUMMARY_HEIGHT;
        text(canvas, "Guild raids", x, y + SECTION_HEIGHT / 2, 14, color(ACCENT_PRIMARY_HOVER), LEFT);
        canvas.fillRect(x, y + SECTION_HEIGHT - 7, width, 1, color(ACCENT_PRIMARY_DARK));
        y += SECTION_HEIGHT;
        int columns = columns(width);
        float cardWidth = (width - CARD_GAP * (columns - 1)) / columns;
        for (int index = 0; index < rows.size() - 1; index++) {
            renderCard(canvas, rows.get(index), x + (index % columns) * (cardWidth + CARD_GAP),
                    y + (index / columns) * (CARD_HEIGHT + CARD_GAP), cardWidth, false);
        }
    }

    static int columns(float width) {
        return width >= 580 ? 2 : 1;
    }

    static float contentHeight(float width, int raidCount) {
        int gridRows = (Math.max(0, raidCount) + columns(Math.max(0, width - 8)) - 1)
                / columns(Math.max(0, width - 8));
        return SUMMARY_HEIGHT + SECTION_HEIGHT + Math.max(0, gridRows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP);
    }

    static PanelLayout panelLayout(float screenWidth, float screenHeight, float desiredHeight) {
        float x = SequoiaSidebarNavigation.WIDTH + MARGIN;
        float width = Math.max(0, screenWidth - x - MARGIN);
        float y = Math.min(HEADER_HEIGHT + MARGIN, Math.max(0, screenHeight - MARGIN));
        float height = Math.min(Math.max(0, screenHeight - y - MARGIN), Math.max(0, desiredHeight));
        return new PanelLayout(x, y, width, height);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0 && SequoiaSidebarNavigation.click(
                MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()),
                MinecraftUiRenderer.screenHeight(), SequoiaSidebarNavigation.Destination.ACHIEVEMENTS, parent)) {
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PanelLayout panel = panelLayout(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight(), Float.MAX_VALUE);
        float x = MinecraftUiRenderer.mouseX(mouseX);
        float y = MinecraftUiRenderer.mouseY(mouseY);
        if (x < panel.x() || x >= panel.right() || y < panel.y() || y >= panel.bottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollBy((float) -scrollY * SCROLL_SPEED);
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-SCROLL_SPEED);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(SCROLL_SPEED);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(CARD_HEIGHT + CARD_GAP) * 2);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy((CARD_HEIGHT + CARD_GAP) * 2);
            case GLFW.GLFW_KEY_HOME -> scrollOffset = 0;
            case GLFW.GLFW_KEY_END -> scrollOffset = maxScroll;
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    private void scrollBy(float amount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + amount));
    }

    private String statusLine() {
        return state == State.LOADING ? "Loading your guild raids..." : "Progress unavailable right now";
    }

    private static void renderCard(UiCanvas canvas, Row row, float x, float y, float width, boolean summary) {
        float height = summary ? SUMMARY_HEIGHT : CARD_HEIGHT;
        canvas.fillRect(x, y, width, height, color(BACKGROUND_CONTENT));
        canvas.fillRect(x, y, 3, height, color(ACCENT_PRIMARY_DARK));
        // Keep icon, heading, and count on separate lines so long names never compete with values.
        float padding = Math.min(14, width / 8);
        float left = x + padding;
        float usableWidth = Math.max(0, width - padding * 2);
        boolean compact = usableWidth < 200;
        float iconSize = compact ? 20 : 28;
        drawIcon(canvas, row, left, y + 12, iconSize);
        float titleX = left + iconSize + 10;
        fittedText(canvas, row.name(), row.compactName(), titleX, y + 12 + iconSize / 2,
                Math.max(0, x + width - padding - titleX), summary ? 16 : 13, color(TEXT_PRIMARY));

        float countY = y + (summary ? 63 : 56);
        String countLabel = formatCount(row.count());
        float countSize = summary ? 26 : 22;
        if (compact) countSize = 18;
        text(canvas, countLabel, left, countY, countSize, color(TEXT_PRIMARY), LEFT);
        float countWidth = measure(countLabel, countSize);
        if (usableWidth - countWidth > 94) {
            text(canvas, "completions", left + countWidth + 8, countY + 3, 10, color(TEXT_SECONDARY), LEFT);
        }
        float tierY = y + (summary ? 88 : 78);
        String tierLabel = row.tier() == null ? "Unranked" : row.tier().label();
        fittedText(canvas, tierLabel, left, tierY, usableWidth, 11, tierColor(row.tier()));
        float tierWidth = measure(tierLabel, 11);
        String next = row.nextTier() == null ? "Highest tier reached"
                : "Next: " + row.nextTier().label() + " at " + formatCount(row.nextAt());
        if (!compact && usableWidth - tierWidth - 16 >= measure(next, 10)) {
            text(canvas, next, x + width - padding, tierY, 10, color(TEXT_SECONDARY), RIGHT);
        }

        float barY = y + height - 30;
        canvas.fillRect(left, barY, usableWidth, 4, color(CONTROL_INPUT));
        float ratio = progressRatio(row);
        if (ratio > 0) canvas.fillRect(left, barY, usableWidth * ratio, 4, color(ACCENT_PRIMARY));
        String remaining = row.nextAt() <= 0 ? "All milestones complete"
                : formatCount(Math.max(0, row.nextAt() - row.count())) + " to " + row.nextTier().label();
        fittedText(canvas, remaining, left, y + height - 13, usableWidth, 10, color(TEXT_SECONDARY));
    }

    private static void fittedText(UiCanvas canvas, String value, float x, float y, float width, float size, Color textColor) {
        fittedText(canvas, value, value, x, y, width, size, textColor);
    }

    private static void fittedText(UiCanvas canvas, String value, String compact, float x, float y, float width, float size, Color textColor) {
        if (width <= 0) return;
        String shown = measure(value, size) <= width ? value : compact;
        if (measure(shown, size) > width) {
            while (!shown.isEmpty() && measure(shown + "...", size) > width) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown.isEmpty() ? "" : shown + "...";
        }
        text(canvas, shown, x, y, size, textColor, LEFT);
    }

    static float progressRatio(Row row) {
        if (row.nextAt() <= 0) {
            return 1f;
        }
        return Math.min(1f, Math.max(0f, (float) row.count() / row.nextAt()));
    }

    private static void drawIcon(UiCanvas canvas, Row row, float x, float y, float size) {
        AssetManager.Asset asset = SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(row.icon());
        float ringWidth = 1.5f;
        float radius = (size - ringWidth) / 2f;
        canvas.fillCircle(x + size / 2f, y + size / 2f, radius, color(CONTROL_INPUT));
        if (asset != null && asset.getImage() != null) {
            canvas.drawImage(asset.getImage(), x + 2, y + 2, size - 4, size - 4, 1f);
        } else {
            text(canvas, row.name().substring(0, 1), x + size / 2, y + size / 2, 13, color(TEXT_SECONDARY), CENTER);
        }
        canvas.strokeCircle(x + size / 2f, y + size / 2f, radius, ringWidth, tierColor(row.tier()));
    }

    static UiColor tierToken(SeqTier tier) {
        return switch (tier) {
            case BRONZE -> ACHIEVEMENT_BRONZE;
            case SILVER -> ACHIEVEMENT_SILVER;
            case GOLD -> ACHIEVEMENT_GOLD;
            case PLATINUM -> ACHIEVEMENT_PLATINUM;
            case DIAMOND -> ACHIEVEMENT_DIAMOND;
            case OBSIDIAN -> ACHIEVEMENT_OBSIDIAN;
            case MYTHRIL -> ACHIEVEMENT_MYTHRIL;
        };
    }

    private static Color tierColor(SeqTier tier) {
        return tier == null ? color(TEXT_DISABLED) : color(tierToken(tier));
    }

    static String formatCount(int count) {
        return String.format(Locale.US, "%,d", count);
    }

    @Override
    public Component getNarrationMessage() {
        StringBuilder narration = new StringBuilder("Sequoia Achievements. ");
        if (state != State.READY) {
            return Component.literal(narration.append(statusLine()).toString());
        }
        for (Row row : rows) {
            narration.append(row.name()).append(": ").append(formatCount(row.count())).append(" completions, ");
            narration.append(row.tier() == null ? "unranked" : row.tier().label());
            if (row.nextAt() > 0) {
                narration.append(", next tier at ").append(formatCount(row.nextAt()));
            }
            narration.append(". ");
        }
        return Component.literal(narration.toString());
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void text(
            UiCanvas canvas, String value, float x, float y, float size, Color textColor, HorizontalAlign align) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor, align, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static float measure(String value, float size) {
        return UiRenderer.measureText(value, SeqClient.getFontManager().getSelectedFont(), size).width();
    }

    record Row(
            String name,
            String compactName,
            String icon,
            int count,
            SeqTier tier,
            int nextAt,
            SeqTier nextTier,
            boolean total) {}

    record PanelLayout(float x, float y, float width, float height) {
        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }
}
