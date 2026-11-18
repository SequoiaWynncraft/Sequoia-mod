package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.LEFT;
import static com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign.RIGHT;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.LeafQuestService.Snapshot;
import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.model.LeafQuests.Daily;
import com.seqwawa.seq.model.LeafQuests.Goal;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.model.LeafQuests.Reward;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.ui.unlock.PixelArt;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
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
import java.util.stream.Collectors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * The member's Leaf quests: one card each for the daily, the weekly and the
 * endless quest, with progress, reward and how long is left. Opened with
 * {@code /seq quests} or from the sidebar.
 */
public final class QuestBoardScreen extends SequoiaPanelScreen {

    static final List<String> SLOTS = List.of(LeafQuests.DAILY, LeafQuests.WEEKLY, LeafQuests.REPEATABLE);
    static final float CARD_HEIGHT = 160;
    /** Read out when Leaf could not be reached, over the quests from the last read that worked. */
    static final String STALE_LINE = "Couldn't reach Leaf, showing earlier progress";

    private static final float MARGIN = SequoiaUiStyle.CONTENT_PADDING;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float CARD_GAP = 6;
    private static final float MIN_CARD_WIDTH = 140;
    private static final float SCROLL_SPEED = 30;

    private final LeafQuestService service = LeafQuestService.getInstance();
    private final ScrollPane scroll = new ScrollPane();

    private State state = State.LOADING;
    private Snapshot shown = Snapshot.EMPTY;
    private boolean stale;

    public QuestBoardScreen(Screen parent) {
        super(Component.literal("Sequoia Quests"), parent, SequoiaSidebarNavigation.Destination.QUESTS);
    }

    @Override
    protected void init() {
        super.init();
        scroll.reset();
        // Runs again on every resize, so it takes the once-a-minute limit like the rest.
        service.requestRefresh();
        state = service.state();
        shown = service.snapshot();
        stale = service.refreshFailed();
    }

    @Override
    protected void beforeFrame() {
        service.requestRefresh();
        if (state != service.state() || !shown.equals(service.snapshot()) || stale != service.refreshFailed()) {
            state = service.state();
            shown = service.snapshot();
            stale = service.refreshFailed();
            triggerImmediateNarration(true);
        }
    }

    @Override
    protected void draw(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        Panel panel = panel(width, height);
        SequoiaUiStyle.drawPanelFrame(canvas, HEADER_HEIGHT);
        text(canvas, "Quests", width - MARGIN, HEADER_HEIGHT / 2, 18, color(ACCENT_PRIMARY_HOVER), RIGHT);
        drawSidebar(canvas);

        if (state != State.READY) {
            float p = QuestCard.P;
            float w = PixelArt.snap(Math.max(0, panel.width() - 6), p);
            float h = 24 * p;
            java.awt.Color green = color(CONTROL_SUCCESS);
            PixelArt.pixelPanel(canvas, panel.x(), panel.y(), w, h, p, PixelArt.mix(green, color(BACKGROUND_POPUP), 0.35f), 1f,
                    PixelArt.SEE_THROUGH);
            PixelArt.outlinedText(canvas, "YOUR QUESTS", panel.x() + 5 * p, panel.y() + 8 * p, 10, green, 1f, LEFT);
            PixelArt.fitted(canvas, statusLine(state), panel.x() + 5 * p, panel.y() + 16 * p, w - 10 * p, 10,
                    color(TEXT_SECONDARY), 1f);
            return;
        }

        Instant now = Instant.now();
        List<Card> cards = cards(shown.quests(), now);
        List<RecentLeaf.Row> recent = RecentLeaf.rows(shown.recent(), RecentLeaf.SHOWN);
        // On the art-pixel grid, so every frame's edge lands on a whole art pixel.
        float contentWidth = PixelArt.snap(Math.max(0, panel.width() - 6) - QuestCard.P / 2, QuestCard.P);
        // The header, the quest cards under it, and the latest Leaf under them, each a card's gap apart.
        float cardsTop = headerHeight(shown.account(), contentWidth) + CARD_GAP;
        float recentTop = cardsTop + cardsHeight(contentWidth, cards.size()) + CARD_GAP;
        scroll.setMax(recentTop + RecentLeaf.height(recent.size()) - panel.height());
        canvas.save();
        canvas.scissor(panel.x(), panel.y(), panel.width(), panel.height());
        float top = panel.y() - scroll.offset();
        LeafHeader.render(canvas, shown.account(), stale, panel.x(), top, contentWidth);
        List<Placement> placements = placements(contentWidth, cards.size(), cardsTop);
        for (int i = 0; i < cards.size(); i++) {
            Placement placement = placements.get(i);
            QuestCard.render(canvas, cards.get(i), panel.x() + placement.x(), top + placement.y(), placement.width());
        }
        RecentLeaf.render(canvas, recent, shown.recentFailed(), now, panel.x(), top + recentTop, contentWidth);
        canvas.restore();
        scroll.draw(canvas, new UiBounds(panel.right() - 3, panel.y(), 3, panel.height()));
    }

    private static float headerHeight(LeafAccount account, float width) {
        String amount = formatCount(account.leaf());
        return LeafHeader.layout(width, PixelArt.leafAmountWidth(amount, 30, LeafHeader.P),
                PixelArt.leafAmountWidth(amount, 20, LeafHeader.P)).height();
    }

    /** "950", "1.2k", "12.5k", "250k", "1.5M": short enough for a narrow card. */
    static String shortCount(long count) {
        if (count < 1_000) return Long.toString(count);
        if (count < 1_000_000) return trimmed(count / 1_000.0) + "k";
        return trimmed(count / 1_000_000.0) + "M";
    }

    private static String trimmed(double value) {
        String text = value >= 100 ? Long.toString(Math.round(value)) : String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    // ── What the cards say. Pure, so they are tested without a game. ──

    /**
     * What one slot's card shows.
     *
     * @param banked dailies waiting, shown as lit pips; {@code bankSize} pips in all, none outside the daily slot
     */
    record Card(String slot, String heading, String timer, Quest quest, String emptyLine, int banked, int bankSize) {}

    static List<Card> cards(LeafQuests quests, Instant now) {
        LeafQuests source = quests == null ? LeafQuests.EMPTY : quests;
        List<Card> cards = new ArrayList<>(SLOTS.size());
        for (String slot : SLOTS) {
            Quest quest = source.quest(slot);
            Daily daily = source.dailyOrEmpty();
            boolean isDaily = LeafQuests.DAILY.equals(slot);
            cards.add(new Card(slot, heading(slot, daily), timer(slot, quest, daily, now), quest, emptyLine(slot),
                    isDaily ? daily.charges() : 0, isDaily ? daily.chargeCap() : 0));
        }
        return List.copyOf(cards);
    }

    static String heading(String slot, Daily daily) {
        return switch (slot) {
            case LeafQuests.DAILY -> daily.charges() > 0
                    ? "Daily (" + daily.charges() + "/" + daily.chargeCap() + ")"
                    : "Daily";
            case LeafQuests.WEEKLY -> "Weekly";
            default -> UnlockPopup.slotName(LeafQuests.REPEATABLE);
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

    /** "1 / 3", or "2,350 / 6,000 SR" for season rating. */
    static String progressLabel(Goal goal) {
        String counts = formatCount(goal.progress()) + " / " + formatCount(goal.target());
        return "season_rating".equals(goal.type()) ? counts + " SR" : counts;
    }

    static float progressRatio(Goal goal) {
        if (goal.target() <= 0) return 0f;
        return Math.min(1f, Math.max(0f, (float) goal.progress() / goal.target()));
    }

    /** Every goal's count, as narration reads them: "1 / 3 raids, 4 / 9 wars". */
    static String progressLabel(Quest quest) {
        List<Goal> goals = quest.goalsOrObjective();
        if (goals.size() == 1) return progressLabel(goals.getFirst());
        return goals.stream().map(goal -> progressLabel(goal) + unit(goal)).collect(Collectors.joining(", "));
    }

    private static String unit(Goal goal) {
        return switch (goal.type() == null ? "" : goal.type()) {
            case "raids" -> " raids";
            case "wars" -> " wars";
            default -> "";
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
            case SIGNED_OUT -> "Connect to Sequoia to see your quests.";
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

    /** Where each card goes, its rows starting {@code top} below the board's top. */
    static List<Placement> placements(float width, int count, float top) {
        int columns = columnCount(width);
        // Whole art pixels wide; what is left over goes to the last column, so the row ends where the board does.
        float cardWidth = Math.max(0, (float) Math.floor((width - CARD_GAP * (columns - 1)) / columns / QuestCard.P) * QuestCard.P);
        float last = Math.max(0, width - CARD_GAP * (columns - 1) - cardWidth * (columns - 1));
        List<Placement> placements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            placements.add(new Placement(column * (cardWidth + CARD_GAP),
                    top + i / columns * (CARD_HEIGHT + CARD_GAP), column == columns - 1 ? last : cardWidth));
        }
        return List.copyOf(placements);
    }

    /** How tall the cards are together, in as many rows as they need. */
    static float cardsHeight(float width, int count) {
        if (count == 0) return 0;
        int rows = (count + columnCount(width) - 1) / columnCount(width);
        return rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
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
        if (click.button() == 0 && clickSidebar(UiPointer.at(click))) {
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    /** Scrolls the board by {@code amount} UI pixels, down for positive, as far as the board goes. */
    public void scrollBy(float amount) {
        scroll.scrollBy(amount);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Panel panel = panel(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        UiPointer at = UiPointer.at(mouseX, mouseY);
        if (at.x() < panel.x() || at.x() >= panel.right() || at.y() < panel.y() || at.y() >= panel.bottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll.scrollBy(-(float) scrollY * SCROLL_SPEED);
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
        if (stale) narration.append(STALE_LINE).append(". ");
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
