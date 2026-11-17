package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.CENTER;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.LEFT;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.RIGHT;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.LeafQuestService.Snapshot;
import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.model.LeafQuests.Daily;
import com.seqwawa.seq.model.LeafQuests.Objective;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.model.LeafQuests.Reward;
import com.seqwawa.seq.model.SeqRaid;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * The member's Leaf quests: one card each for the daily, the weekly and the
 * repeatable, with progress, reward and how long is left. Opened with
 * {@code /seq quests} or from the sidebar.
 */
public final class QuestBoardScreen extends Screen {

    static final List<String> SLOTS = List.of(LeafQuests.DAILY, LeafQuests.WEEKLY, LeafQuests.REPEATABLE);
    static final float CARD_HEIGHT = 160;
    static final float SUMMARY_HEIGHT = 30;

    private static final float MARGIN = SequoiaUiStyle.CONTENT_PADDING;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float CARD_GAP = 6;
    private static final float MIN_CARD_WIDTH = 140;
    private static final float PADDING = 12;
    private static final float ICON_SIZE = 40;
    private static final float SCROLL_SPEED = 30;

    private final Screen parent;
    private final LeafQuestService service = LeafQuestService.getInstance();

    private State state = State.LOADING;
    private Snapshot shown = Snapshot.EMPTY;
    private float scrollOffset;
    private float maxScroll;
    private float mouseX;
    private float mouseY;

    public QuestBoardScreen(Screen parent) {
        super(Component.literal("Sequoia Quests"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        service.refreshNow();
        state = service.state();
        shown = service.snapshot();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pointerX, int pointerY, float partialTick) {
        super.render(guiGraphics, pointerX, pointerY, partialTick);
        service.requestRefresh();
        if (state != service.state() || !shown.equals(service.snapshot())) {
            state = service.state();
            shown = service.snapshot();
            triggerImmediateNarration(true);
        }
        mouseX = MinecraftUiRenderer.mouseX(pointerX);
        mouseY = MinecraftUiRenderer.mouseY(pointerY);
        UiRenderer.renderScreen(this, this::renderScreen);
    }

    private void renderScreen(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        Panel panel = panel(width, height);
        SequoiaUiStyle.drawPanelFrame(canvas, HEADER_HEIGHT);
        text(canvas, "Quests", width - MARGIN, HEADER_HEIGHT / 2, 18, color(ACCENT_PRIMARY_HOVER), RIGHT);
        SequoiaSidebarNavigation.render(canvas, SequoiaSidebarNavigation.Destination.QUESTS, mouseX, mouseY);

        if (state != State.READY) {
            canvas.fillRect(panel.x(), panel.y(), panel.width(), panel.height(), color(BACKGROUND_CONTENT));
            fittedText(canvas, "Your quests", panel.x() + 14, panel.y() + 26,
                    Math.max(0, panel.width() - 28), 16, color(ACCENT_PRIMARY_HOVER));
            fittedText(canvas, statusLine(state), panel.x() + 14, panel.y() + 54,
                    Math.max(0, panel.width() - 28), 12, color(TEXT_SECONDARY));
            return;
        }

        Instant now = Instant.now();
        List<Card> cards = cards(shown.quests(), now);
        float contentWidth = Math.max(0, panel.width() - 6);
        maxScroll = Math.max(0, contentHeight(contentWidth, cards.size()) - panel.height());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        canvas.save();
        canvas.scissor(panel.x(), panel.y(), panel.width(), panel.height());
        float top = panel.y() - scrollOffset;
        renderSummary(canvas, shown.account(), panel.x(), top + 12, contentWidth);
        List<Placement> placements = placements(contentWidth, cards.size());
        for (int i = 0; i < cards.size(); i++) {
            Placement placement = placements.get(i);
            renderCard(canvas, cards.get(i), panel.x() + placement.x(), top + placement.y(), placement.width());
        }
        canvas.restore();
        if (maxScroll > 0 && panel.height() > 0) {
            float thumb = Math.min(panel.height(), Math.max(20, panel.height() * panel.height() / (panel.height() + maxScroll)));
            float y = panel.y() + (panel.height() - thumb) * scrollOffset / maxScroll;
            canvas.fillRect(panel.right() - 3, panel.y(), 3, panel.height(), color(CONTROL_TRACK));
            canvas.fillRect(panel.right() - 3, y, 3, thumb, color(CONTROL_THUMB));
        }
    }

    private static void renderSummary(UiCanvas canvas, LeafAccount account, float x, float y, float width) {
        float after = leafAmount(canvas, formatCount(account.leaf()), x, y, 14, color(CONTROL_SUCCESS));
        LeafAccount.Level level = account.levelOrFirst();
        fittedText(canvas, levelLine(level), after + 14, y, Math.max(0, x + width - after - 14), 12,
                color(TEXT_SECONDARY));
    }

    /**
     * One slot, laid out like a quest card in a game: the slot and its timer on
     * top, the objective's tile in the middle, the quest under it, and progress
     * and reward along the bottom. Narrow enough for three side by side.
     */
    private static void renderCard(UiCanvas canvas, Card card, float x, float y, float width) {
        canvas.fillRect(x, y, width, CARD_HEIGHT, color(BACKGROUND_CONTENT));
        canvas.fillRect(x, y, width, 1, color(ACCENT_DIVIDER));
        float inner = Math.max(0, width - PADDING * 2);
        float centre = x + width / 2;
        centredText(canvas, card.heading(), centre, y + 16, inner, 13, color(ACCENT_PRIMARY_HOVER));
        centredText(canvas, card.timer(), centre, y + 31, inner, 11, color(TEXT_MUTED));

        Quest quest = card.quest();
        if (quest == null) {
            List<String> lines = wrap(card.emptyLine(), inner, 12);
            for (int i = 0; i < lines.size(); i++) {
                centredText(canvas, lines.get(i), centre, y + 80 + i * 16, inner, 12, color(TEXT_SECONDARY));
            }
            return;
        }
        drawObjectiveTile(canvas, quest.objective(), slotAccent(card.slot()), centre - ICON_SIZE / 2, y + 44);
        // Up to two lines, so a long quest is read in full instead of cut short.
        List<String> title = wrap(quest.title(), inner, 13);
        for (int i = 0; i < Math.min(2, title.size()); i++) {
            String line = i == 1 && title.size() > 2 ? String.join(" ", title.subList(1, title.size())) : title.get(i);
            centredText(canvas, line, centre, y + 100 + i * 15, inner, 13, color(TEXT_PRIMARY));
        }

        float rowY = y + 132;
        fittedText(canvas, progressLabel(quest), x + PADDING, rowY, inner / 2, 12, color(TEXT_SECONDARY));
        renderReward(canvas, quest.reward(), x + width - PADDING, rowY, inner / 2 + 10);
        if (quest.completed()) {
            canvas.fillRect(x + PADDING, y + 145, inner, 3, color(CONTROL_SUCCESS));
        } else {
            canvas.fillRect(x + PADDING, y + 145, inner, 3, color(CONTROL_INPUT));
            float ratio = progressRatio(quest);
            if (ratio > 0) canvas.fillRect(x + PADDING, y + 145, inner * ratio, 3, color(ACCENT_PRIMARY));
        }
    }

    /**
     * "+300 [leaf] +150 XP", right-aligned at {@code right}, with the Leaf icon in
     * place of the word. The XP is left out when there is no room for it.
     */
    private static void renderReward(UiCanvas canvas, Reward reward, float right, float y, float room) {
        if (reward == null) return;
        String leaf = "+" + formatCount(reward.leaf());
        String xp = "  +" + formatCount(reward.xp()) + " XP";
        float leafWidth = measure(leaf, 13) + 3 + 13;
        boolean withXp = leafWidth + measure(xp, 11) <= room;
        float x = right - leafWidth - (withXp ? measure(xp, 11) : 0);
        float after = leafAmount(canvas, leaf, x, y, 13, color(CONTROL_SUCCESS));
        if (withXp) text(canvas, xp, after, y, 11, color(TEXT_SECONDARY), LEFT);
    }

    private static void centredText(UiCanvas canvas, String value, float centre, float y, float width, float size,
            Color textColor) {
        if (value == null || width <= 0) return;
        String shown = value;
        if (measure(shown, size) > width) {
            while (!shown.isEmpty() && measure(shown + "...", size) > width) shown = shown.substring(0, shown.length() - 1);
            shown = shown.isEmpty() ? "" : shown + "...";
        }
        text(canvas, shown, centre, y, size, textColor, CENTER);
    }

    /** Splits text into lines no wider than {@code width}, breaking at spaces. */
    private static List<String> wrap(String value, float width, float size) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String tried = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && measure(tried, size) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(tried);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** Draws an amount followed by the Leaf icon, or the word when the icon is not loaded. Returns where it ends. */
    private static float leafAmount(UiCanvas canvas, String amount, float x, float y, float size, Color amountColor) {
        text(canvas, amount, x, y, size, amountColor, LEFT);
        float end = x + measure(amount, size) + 3;
        AssetManager.Asset leaf = asset("leaf");
        if (leaf == null) {
            text(canvas, "Leaf", end, y, size, amountColor, LEFT);
            return end + measure("Leaf", size);
        }
        float iconSize = Math.round(size);
        canvas.drawImage(leaf.getImage(), end, y - iconSize / 2, iconSize, iconSize, 1f);
        return end + iconSize;
    }

    /** The raid's artwork in a frame of the slot's colour, or a short code for wars and season rating. */
    private static void drawObjectiveTile(UiCanvas canvas, Objective objective, UiColor accent, float x, float y) {
        canvas.fillRect(x, y, ICON_SIZE, ICON_SIZE, color(CONTROL_INPUT));
        canvas.strokeRect(x, y, ICON_SIZE, ICON_SIZE, 1.5f, color(accent));
        String key = objectiveAsset(objective);
        AssetManager.Asset artwork = key == null ? null : asset(key);
        if (artwork != null) {
            float available = ICON_SIZE - 8;
            float scale = available / Math.max(artwork.getWidth(), artwork.getHeight());
            float width = artwork.getWidth() * scale;
            float height = artwork.getHeight() * scale;
            canvas.drawImage(artwork.getImage(), x + (ICON_SIZE - width) / 2, y + (ICON_SIZE - height) / 2,
                    width, height, 1f);
            return;
        }
        text(canvas, objectiveCode(objective), x + ICON_SIZE / 2, y + ICON_SIZE / 2, 12, color(TEXT_PRIMARY), CENTER);
    }

    private static AssetManager.Asset asset(String key) {
        AssetManager.Asset asset = SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(key);
        return asset == null || asset.getImage() == null ? null : asset;
    }

    // ── What the cards say. Pure, so they are tested without a game. ──

    /** What one slot's card shows. */
    record Card(String slot, String heading, String timer, Quest quest, String emptyLine) {}

    static List<Card> cards(LeafQuests quests, Instant now) {
        LeafQuests source = quests == null ? LeafQuests.EMPTY : quests;
        List<Card> cards = new ArrayList<>(SLOTS.size());
        for (String slot : SLOTS) {
            Quest quest = source.quest(slot);
            cards.add(new Card(slot, heading(slot, source.dailyOrEmpty()), timer(slot, quest, source.dailyOrEmpty(), now),
                    quest, emptyLine(slot)));
        }
        return List.copyOf(cards);
    }

    static String heading(String slot, Daily daily) {
        return switch (slot) {
            case LeafQuests.DAILY -> daily.charges() > 0
                    ? "Daily (" + daily.charges() + "/" + daily.chargeCap() + ")"
                    : "Daily";
            case LeafQuests.WEEKLY -> "Weekly";
            default -> "Repeatable";
        };
    }

    static String timer(String slot, Quest quest, Daily daily, Instant now) {
        return switch (slot) {
            case LeafQuests.DAILY -> daily.nextChargeAt() == null
                    ? "Bank full"
                    : "Next daily in " + durationLabel(Duration.between(now, daily.nextChargeAt()));
            case LeafQuests.WEEKLY -> {
                if (quest == null || quest.expiresAt() == null) yield "Ends on Monday";
                String left = durationLabel(Duration.between(now, quest.expiresAt()));
                yield quest.completed() ? "Done · new weekly in " + left : "Ends in " + left;
            }
            default -> "Next one when done";
        };
    }

    static String emptyLine(String slot) {
        return LeafQuests.DAILY.equals(slot) ? "No daily waiting. Dailies bank up, so nothing is lost."
                : "Nothing here right now";
    }

    /** "2d 22h", "6h 12m", "12m" or "under a minute". */
    static String durationLabel(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / (24 * 60);
        long hours = minutes / 60 % 24;
        if (days > 0) return days + "d " + hours + "h";
        if (minutes >= 60) return hours + "h " + minutes % 60 + "m";
        if (minutes > 0) return minutes + "m";
        return "under a minute";
    }

    static String progressLabel(Quest quest) {
        String counts = formatCount(quest.progress()) + " / " + formatCount(quest.target());
        return quest.objective() != null && "season_rating".equals(quest.objective().type()) ? counts + " SR" : counts;
    }

    static float progressRatio(Quest quest) {
        if (quest.completed()) return 1f;
        if (quest.target() <= 0) return 0f;
        return Math.min(1f, Math.max(0f, (float) quest.progress() / quest.target()));
    }

    /** The raid the objective names, or null for any raid, a war or season rating. */
    static SeqRaid objectiveRaid(Objective objective) {
        if (objective == null || objective.raid() == null) return null;
        // Leaf calls The Wartorn Palace "wtp"; the mod calls it TWP.
        String code = "wtp".equalsIgnoreCase(objective.raid()) ? "TWP" : objective.raid().toUpperCase(Locale.ROOT);
        for (SeqRaid raid : SeqRaid.values()) {
            if (raid.code().equals(code)) return raid;
        }
        return null;
    }

    static String objectiveAsset(Objective objective) {
        if (objective == null || !"raids".equals(objective.type())) return null;
        SeqRaid raid = objectiveRaid(objective);
        if (raid != null) return raid.assetKey();
        return objective.raid() == null ? "icon" : null;
    }

    static String objectiveCode(Objective objective) {
        if (objective == null) return "?";
        return switch (objective.type() == null ? "" : objective.type()) {
            case "raids" -> {
                SeqRaid raid = objectiveRaid(objective);
                if (raid != null) yield raid.code();
                yield objective.raid() == null ? "ALL" : objective.raid().toUpperCase(Locale.ROOT);
            }
            case "wars" -> "WAR";
            case "season_rating" -> "SR";
            default -> "?";
        };
    }

    static UiColor slotAccent(String slot) {
        return switch (slot) {
            case LeafQuests.DAILY -> ACHIEVEMENT_GOLD;
            case LeafQuests.WEEKLY -> ACHIEVEMENT_DIAMOND;
            default -> ACHIEVEMENT_BRONZE;
        };
    }

    static String levelLine(LeafAccount.Level level) {
        return "Level " + level.number() + "  ·  " + formatCount(level.xpIntoLevel()) + " / "
                + formatCount(level.xpForNextLevel()) + " XP";
    }

    static String rewardLabel(Reward reward) {
        return reward == null ? "" : "+" + formatCount(reward.leaf()) + " Leaf, +" + formatCount(reward.xp()) + " XP";
    }

    static String statusLine(State state) {
        return switch (state) {
            case LOADING -> "Loading your quests...";
            case NOT_MEMBER -> "Quests are for current Sequoia members.";
            default -> "Quests are unavailable right now. Try again in a minute.";
        };
    }

    static String formatCount(long count) {
        return String.format(Locale.US, "%,d", count);
    }

    // ── Layout ──

    static int columnCount(float width) {
        return Math.max(1, Math.min(SLOTS.size(), (int) ((width + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP))));
    }

    record Placement(float x, float y, float width) {}

    static List<Placement> placements(float width, int count) {
        int columns = columnCount(width);
        float cardWidth = Math.max(0, (width - CARD_GAP * (columns - 1)) / columns);
        List<Placement> placements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            placements.add(new Placement(i % columns * (cardWidth + CARD_GAP),
                    SUMMARY_HEIGHT + i / columns * (CARD_HEIGHT + CARD_GAP), cardWidth));
        }
        return List.copyOf(placements);
    }

    static float contentHeight(float width, int count) {
        if (count == 0) return SUMMARY_HEIGHT;
        int rows = (count + columnCount(width) - 1) / columnCount(width);
        return SUMMARY_HEIGHT + rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
    }

    record Panel(float x, float y, float width, float height) {
        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }

    static Panel panel(float screenWidth, float screenHeight) {
        float x = SequoiaSidebarNavigation.WIDTH + MARGIN;
        float width = Math.max(0, screenWidth - x - MARGIN);
        float y = Math.min(HEADER_HEIGHT + MARGIN, Math.max(0, screenHeight - MARGIN));
        float height = Math.max(0, screenHeight - y - MARGIN);
        return new Panel(x, y, width, height);
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0 && SequoiaSidebarNavigation.click(
                MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()),
                MinecraftUiRenderer.screenHeight(), SequoiaSidebarNavigation.Destination.QUESTS, parent)) {
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Panel panel = panel(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        float x = MinecraftUiRenderer.mouseX(mouseX);
        float y = MinecraftUiRenderer.mouseY(mouseY);
        if (x < panel.x() || x >= panel.right() || y < panel.y() || y >= panel.bottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (float) scrollY * SCROLL_SPEED));
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        StringBuilder narration = new StringBuilder("Sequoia Quests. ");
        if (state != State.READY) {
            return Component.literal(narration.append(statusLine(state)).toString());
        }
        narration.append(formatCount(shown.account().leaf())).append(" Leaf, ")
                .append(levelLine(shown.account().levelOrFirst())).append(". ");
        for (Card card : cards(shown.quests(), Instant.now())) {
            narration.append(card.heading()).append(", ").append(card.timer()).append(": ");
            if (card.quest() == null) {
                narration.append(card.emptyLine()).append(". ");
                continue;
            }
            narration.append(card.quest().title()).append(", ").append(progressLabel(card.quest()))
                    .append(card.quest().completed() ? ", done" : "")
                    .append(", ").append(rewardLabel(card.quest().reward())).append(". ");
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

    // ── Text ──

    private static void fittedText(UiCanvas canvas, String value, float x, float y, float width, float size, Color textColor) {
        if (width <= 0 || value == null) return;
        String shown = value;
        if (measure(shown, size) > width) {
            while (!shown.isEmpty() && measure(shown + "...", size) > width) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown.isEmpty() ? "" : shown + "...";
        }
        text(canvas, shown, x, y, size, textColor, LEFT);
    }

    private static void text(
            UiCanvas canvas, String value, float x, float y, float size, Color textColor, HorizontalAlign align) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor, align, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static float measure(String value, float size) {
        return UiRenderer.measureText(value, SeqClient.getFontManager().getSelectedFont(), size).width();
    }
}
