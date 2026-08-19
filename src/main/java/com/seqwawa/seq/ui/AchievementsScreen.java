package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_SECONDARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_BRONZE;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_DIAMOND;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_GOLD;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_MYTHRIL;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_OBSIDIAN;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_PLATINUM;
import static com.seqwawa.seq.ui.theme.UiColor.ACHIEVEMENT_SILVER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_DISABLED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;
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
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AchievementsScreen extends Screen {

    private static final float MARGIN = 14;
    private static final float HEADER_HEIGHT = 42;
    private static final float PANEL_RADIUS = 7;
    private static final float PANEL_MAX_WIDTH = 460;
    private static final float ROW_HEIGHT = 46;
    private static final float ROW_GAP = 4;
    private static final float ICON = 26;
    private static final float ROW_PADDING = 8;
    private static final float LINE_HALF = 6.5f;
    private static final float NAME_SIZE = 13;
    private static final float COUNT_SIZE = 13;
    private static final float BAR_HEIGHT = 5;
    private static final float BAR_RATIO = 0.75f;
    private static final float RING_WIDTH = 1.5f;
    private static final float DIVIDER_GAP = 10;
    private static final float PANEL_PADDING = 12;
    private static final float STATUS_PANEL_HEIGHT = 120;
    private static final float SCROLL_SPEED = 18;

    private final Screen parent;
    private final GuildRaidProgressService service = GuildRaidProgressService.getInstance();

    private List<Row> rows = List.of();
    private State state = State.LOADING;
    private GuildRaidProgress shown = GuildRaidProgress.EMPTY;
    private float scrollOffset;
    private float maxScroll;

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
    }

    static List<Row> buildRows(GuildRaidProgress progress) {
        List<Row> rows = new ArrayList<>(SeqRaid.values().length + 1);
        for (SeqRaid raid : SeqRaid.values()) {
            rows.add(row(
                    raid.displayName(),
                    raid.assetKey(),
                    progress.count(raid),
                    progress.tier(raid),
                    SeqTier.SINGLE_RAID,
                    false));
        }
        rows.add(row(
                "All Guild Raids",
                "icon",
                progress.totalCount(),
                progress.totalTier(),
                SeqTier.ALL_RAIDS,
                true));
        return List.copyOf(rows);
    }

    private static Row row(String name, String icon, int count, SeqTier tier, int scale, boolean total) {
        SeqTier next = SeqTier.next(count, scale);
        return new Row(name, icon, count, tier, next == null ? 0 : next.threshold(scale), next, total);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pointerX, int pointerY, float partialTick) {
        super.render(guiGraphics, pointerX, pointerY, partialTick);
        if (state != service.state() || shown != service.progress()) {
            refresh();
        }
        UiRenderer.renderScreen(this, this::renderScreen);
    }

    private void renderScreen(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        float panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(260, width - MARGIN * 2));
        float panelX = (width - panelWidth) / 2f;
        float panelY = HEADER_HEIGHT + MARGIN;
        float available = Math.max(STATUS_PANEL_HEIGHT, height - panelY - MARGIN);
        float panelHeight = Math.min(available, state == State.READY ? contentHeight() : STATUS_PANEL_HEIGHT);

        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 210));
        canvas.fillRect(0, 0, width, HEADER_HEIGHT, color(BACKGROUND_HEADER, 248));
        canvas.fillHorizontalGradient(
                0, HEADER_HEIGHT - 1, width, 1, color(ACCENT_PRIMARY, 190), color(ACCENT_PRIMARY, 0));
        text(canvas, "Sequoia Achievements", MARGIN, HEADER_HEIGHT / 2f, 19, color(ACCENT_PRIMARY), LEFT);

        canvas.fillRoundedRect(
                panelX, panelY, panelWidth, panelHeight, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));

        if (state != State.READY) {
            text(canvas, statusLine(), panelX + panelWidth / 2f, panelY + panelHeight / 2f, 12,
                    color(TEXT_MUTED), CENTER);
            return;
        }

        maxScroll = Math.max(0, contentHeight() - panelHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        float rowX = panelX + 10;
        float rowWidth = panelWidth - 20;
        float rowY = panelY + PANEL_PADDING - scrollOffset;
        float counterWidth = counterColumnWidth(rows);

        canvas.scissor(panelX, panelY, panelWidth, panelHeight);
        for (Row row : rows) {
            if (row.total()) {
                canvas.fillRect(rowX, rowY + DIVIDER_GAP, rowWidth, 1, color(ACCENT_DIVIDER));
                rowY += DIVIDER_GAP * 2 + ROW_GAP + 1;
            }
            renderRow(canvas, row, rowX, rowY, rowWidth, counterWidth);
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        canvas.resetScissor();
    }

    private float contentHeight() {
        float height = PANEL_PADDING * 2 - ROW_GAP;
        for (Row row : rows) {
            if (row.total()) {
                height += DIVIDER_GAP * 2 + ROW_GAP + 1;
            }
            height += ROW_HEIGHT + ROW_GAP;
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) scrollY * SCROLL_SPEED));
        return true;
    }

    private String statusLine() {
        return state == State.LOADING ? "Loading your graids..." : "Progress unavailable right now";
    }

    private static void renderRow(UiCanvas canvas, Row row, float x, float y, float width, float counterWidth) {
        Color metal = tierColor(row.tier());
        canvas.fillRoundedRect(x, y, width, ROW_HEIGHT, 6, color(BACKGROUND_CONTENT, 225));

        drawIcon(canvas, row, x + 12, y + ROW_HEIGHT / 2f);

        float textX = x + 12 + ICON + 12;
        float textRight = x + width - 14;
        float topLine = y + ROW_PADDING + LINE_HALF;
        float bottomLine = y + ROW_HEIGHT - ROW_PADDING - LINE_HALF;

        String count = formatCount(row.count());
        String target = target(row);
        float targetWidth = target.isEmpty() ? 0 : measure(target, COUNT_SIZE);

        text(canvas, row.name(), textX, topLine, NAME_SIZE, color(TEXT_PRIMARY), LEFT);
        if (!target.isEmpty()) {
            text(canvas, target, textRight, topLine, COUNT_SIZE, color(TEXT_MUTED), RIGHT);
        }
        text(canvas, count, textRight - targetWidth, topLine, COUNT_SIZE,
                row.count() > 0 ? metal : color(TEXT_DISABLED), RIGHT);

        chip(canvas, textX, bottomLine, row.tier());

        float barWidth = counterWidth * BAR_RATIO;
        progressBar(canvas, textRight - barWidth, bottomLine - BAR_HEIGHT / 2f, barWidth, row);
    }

    private static float counterColumnWidth(List<Row> rows) {
        String widest = formatCount(SeqTier.MYTHRIL.threshold(SeqTier.ALL_RAIDS));
        float reserved = measure(widest + " / " + widest, COUNT_SIZE);
        for (Row row : rows) {
            float used = measure(formatCount(row.count()) + target(row), COUNT_SIZE);
            reserved = Math.max(reserved, used);
        }
        return reserved;
    }

    private static String target(Row row) {
        return row.nextAt() > 0 ? " / " + formatCount(row.nextAt()) : "";
    }

    private static void progressBar(UiCanvas canvas, float x, float y, float width, Row row) {
        float radius = BAR_HEIGHT / 2f;
        canvas.fillRoundedRect(x, y, width, BAR_HEIGHT, radius, color(CONTROL_INPUT, 190));

        float ratio = progressRatio(row);
        if (ratio > 0) {
            float filled = Math.min(width, Math.max(BAR_HEIGHT, width * ratio));
            canvas.fillRoundedRect(x, y, filled, BAR_HEIGHT, radius, alpha(barColor(row), 235));
        }
    }

    static float progressRatio(Row row) {
        if (row.nextAt() <= 0) {
            return 1f;
        }
        return Math.min(1f, Math.max(0f, (float) row.count() / row.nextAt()));
    }

    private static Color ringColor(SeqTier tier) {
        return tier == null ? color(ACCENT_SECONDARY, 120) : alpha(color(tierToken(tier)), 215);
    }

    private static Color barColor(Row row) {
        SeqTier reached = row.nextTier() != null ? row.nextTier() : row.tier();
        return reached == null ? color(ACCENT_PRIMARY) : color(tierToken(reached));
    }

    private static void chip(UiCanvas canvas, float x, float centerY, SeqTier tier) {
        Color metal = tierColor(tier);
        String label = tier == null ? "UNRANKED" : tier.label().toUpperCase(Locale.ROOT);
        float width = measure(label, 8) + 14;

        canvas.fillRoundedRect(x, centerY - LINE_HALF, width, LINE_HALF * 2, LINE_HALF,
                tier == null ? color(CONTROL_INPUT, 200) : shade(alpha(metal, 235), 0.32f));
        text(canvas, label, x + width / 2f, centerY, 8,
                tier == null ? color(TEXT_DISABLED) : lighten(metal, 0.25f), CENTER);
    }

    private static void drawIcon(UiCanvas canvas, Row row, float x, float centerY) {
        float radius = ICON / 2f;
        float centerX = x + radius;
        AssetManager.Asset asset =
                SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(row.icon());

        canvas.fillCircle(centerX, centerY, radius, color(CONTROL_INPUT, 235));
        if (asset != null && asset.getImage() != null) {
            canvas.save();
            canvas.beginPath();
            canvas.circle(centerX, centerY, radius - 1);
            canvas.fillCurrentPathWithImage(
                    asset.getImage(), centerX - radius + 1, centerY - radius + 1, ICON - 2, ICON - 2,
                    row.count() > 0 ? 1f : 0.45f);
            canvas.closePath();
            canvas.restore();
        } else {
            text(canvas, row.name().substring(0, 1), centerX, centerY, radius, color(TEXT_SECONDARY), CENTER);
        }
        canvas.strokeCircle(centerX, centerY, radius, RING_WIDTH, ringColor(row.tier()));
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

    private static Color alpha(Color base, int value) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, value)));
    }

    private static Color shade(Color base, float factor) {
        return new Color(
                Math.round(base.getRed() * factor),
                Math.round(base.getGreen() * factor),
                Math.round(base.getBlue() * factor),
                base.getAlpha());
    }

    private static Color lighten(Color base, float amount) {
        return new Color(
                Math.round(base.getRed() + (255 - base.getRed()) * amount),
                Math.round(base.getGreen() + (255 - base.getGreen()) * amount),
                Math.round(base.getBlue() + (255 - base.getBlue()) * amount),
                base.getAlpha());
    }

    record Row(String name, String icon, int count, SeqTier tier, int nextAt, SeqTier nextTier, boolean total) {}
}
