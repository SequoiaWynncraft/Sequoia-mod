package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.DiscordRankService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.map.MapPlayerHeadRenderer;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.ui.unlock.PixelArt;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import java.awt.Color;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;

/**
 * The member's profile card at the foot of the Sequoia sidebar, in the popups'
 * pixel art: their head, name, rank in its colours and insignia, and under them
 * their Leaf level, balance and XP bar. Clicking it opens the quest board. The
 * sidebar's buttons keep clear of it.
 */
public final class SidebarProfile {

    /** UI pixels per art pixel. */
    static final float P = 2;
    /** Art pixels. */
    static final int WIDTH = 60;
    static final int HEIGHT = 38;
    /** The head's frame, and the face inside it at two art pixels per skin pixel. */
    static final int HEAD = 18;
    /** Art pixels between the card's frame and what is inside it, and between the head and the name. */
    static final int PAD = 3;
    /** UI pixels from the sidebar's bottom and left edges, the same as its buttons'. */
    static final float MARGIN = 8;
    static final float INSET = 10;
    /** Marks a balance Leaf could not refresh. */
    static final String STALE_MARK = "!";

    private static final MapPlayerHeadRenderer HEADS = new MapPlayerHeadRenderer();

    private SidebarProfile() {}

    /** Whether there is a signed-in player to show, and room under every sidebar button for the card. */
    static boolean shown(float screenHeight) {
        return SeqClient.mc != null && SeqClient.mc.getUser() != null
                && fits(screenHeight, SequoiaSidebarNavigation.destinations().size());
    }

    /**
     * Whether the card fits under {@code rows} sidebar buttons, packed as tightly as
     * the sidebar packs them, on a screen this tall. On a shorter one the buttons
     * come first: the card is left out rather than drawn over one.
     */
    static boolean fits(float screenHeight, int rows) {
        float top = top(screenHeight);
        var layout = SequoiaSidebarNavigation.sidebarLayout(screenHeight, rows, 22, 6, screenHeight - top);
        return layout.bottom() + MARGIN / 2 <= top;
    }

    /**
     * How much of the sidebar's bottom the card takes, from its top on the
     * art-pixel grid down to the screen's edge, or nothing while it is left out.
     */
    public static float reserved(float screenHeight) {
        return shown(screenHeight) ? screenHeight - top(screenHeight) : 0;
    }

    /** The card's top, for a sidebar on a screen {@code screenHeight} tall, on the art-pixel grid. */
    static float top(float screenHeight) {
        return PixelArt.snap(screenHeight - MARGIN - HEIGHT * P, P);
    }

    static boolean contains(float mouseX, float mouseY, float screenHeight) {
        float y = top(screenHeight);
        return shown(screenHeight) && mouseX >= INSET && mouseX < INSET + WIDTH * P && mouseY >= y && mouseY < y + HEIGHT * P;
    }

    /**
     * Opens the quest board on a click inside the card. Returns whether the click
     * was the card's, so the screen does nothing else with it.
     */
    public static boolean click(float mouseX, float mouseY, float screenHeight, Screen parent) {
        if (!contains(mouseX, mouseY, screenHeight)) return false;
        if (!(SeqClient.mc.screen instanceof QuestBoardScreen)) SeqClient.mc.setScreen(new QuestBoardScreen(parent));
        return true;
    }

    /** How wide the rank may run: its whole row, less the stale mark at the row's end and an art pixel before it. */
    static float rankRoom(float row, float markWidth, float p) {
        return markWidth > 0 ? row - markWidth - p : row;
    }

    /** "12,480" while it fits, then "12.5k". */
    static String balanceText(long leaf, float room, java.util.function.Function<String, Float> width) {
        String full = QuestBoardScreen.formatCount(leaf);
        return width.apply(full) <= room ? full : QuestBoardScreen.shortCount(leaf);
    }

    /** What the Leaf row says before there is a balance to show. */
    static String leafStatus(LeafQuestService.State state, boolean connected) {
        return switch (state) {
            case NOT_MEMBER -> "Leaf is for members";
            case UNAVAILABLE -> "Leaf is unavailable";
            default -> connected ? "Loading Leaf..." : "Connect for Leaf";
        };
    }

    /** Draws the card, and asks Leaf for a fresh balance at most once a minute while connected. */
    public static void render(UiCanvas canvas, float screenHeight, float mouseX, float mouseY) {
        LeafQuestService service = LeafQuestService.getInstance();
        boolean connected = ConnectionManager.isConnected();
        if (connected) service.requestRefresh();
        if (!shown(screenHeight)) return;
        String username = SeqClient.mc.getUser().getName();
        UUID uuid = SeqClient.mc.getUser().getProfileId();
        DiscordRankService ranks = DiscordRankService.getInstance();
        RankPresentation rank = ranks.presentationForMinecraftAccount(uuid, username);
        SeqBadgeTier insignia = ranks.insigniaForMinecraftUsername(username);

        float p = P;
        float x = INSET;
        float y = top(screenHeight);
        float w = WIDTH * p;
        float h = HEIGHT * p;
        boolean hovered = contains(mouseX, mouseY, screenHeight);
        Color green = color(CONTROL_SUCCESS);
        Color frame = hovered ? green : PixelArt.mix(green, color(BACKGROUND_POPUP), 0.35f);
        PixelArt.pixelPanel(canvas, x, y, w, h, p, frame, 1f, PixelArt.SEE_THROUGH);
        if (hovered) canvas.fillRect(x + p, y + 2 * p, w - 2 * p, h - 4 * p, new Color(255, 255, 255, 14));

        // The head, in a frame of the rank's colour.
        Color rankColour = rank == null || rank.colors().isEmpty() ? color(TEXT_MUTED) : new Color(rank.colors().first());
        float headX = x + PAD * p;
        float headY = y + PAD * p;
        canvas.fillRect(headX + p, headY + p, (HEAD - 2) * p, (HEAD - 2) * p, color(CONTROL_INPUT));
        HEADS.retain(Set.of(username));
        HEADS.render(canvas, username, headX + HEAD * p / 2, headY + HEAD * p / 2, (HEAD - 2) * p);
        PixelArt.pixelBorder(canvas, headX, headY, HEAD * p, HEAD * p, p, rankColour);

        // The name, the rank under it, and the insignia in the corner.
        float textX = headX + (HEAD + PAD) * p;
        float right = x + w - PAD * p;
        float badge = 10 * p;
        boolean hasInsignia = insignia != null && PixelArt.asset(insigniaKey(insignia)) != null;
        if (hasInsignia) {
            canvas.drawImage(PixelArt.asset(insigniaKey(insignia)).getImage(), right - badge, headY - p, badge, badge, 1f);
        }
        float nameRoom = right - textX - (hasInsignia ? badge + p : 0);
        PixelArt.fitted(canvas, username, textX, headY + 5 * p, nameRoom, 10, color(TEXT_PRIMARY), 1f);
        // While the balance may be out of date, a "!" ends the rank's row, the way the quest board marks it.
        boolean stale = service.state() == LeafQuestService.State.READY && service.refreshFailed();
        if (stale) {
            PixelArt.outlinedText(canvas, STALE_MARK, right, headY + 12 * p, 10, color(CONTROL_WARNING), 1f,
                    HorizontalAlign.RIGHT);
        }
        float rankRoom = rankRoom(right - textX, stale ? PixelArt.outlinedInkWidth(STALE_MARK, 10) : 0, p);
        if (rank != null) {
            gradientText(canvas, PixelArt.fit(rank.label(), rankRoom, 10), textX, headY + 12 * p, rank.colors());
        } else {
            PixelArt.fitted(canvas, "Guest", textX, headY + 12 * p, rankRoom, 10, color(TEXT_MUTED), 1f);
        }

        // Leaf: the level and balance on one row, the XP bar under them.
        float rowY = y + 26 * p;
        float barY = y + 30 * p;
        float left = x + PAD * p;
        if (service.state() != LeafQuestService.State.READY) {
            PixelArt.fitted(canvas, leafStatus(service.state(), connected), left, rowY + 2 * p, right - left, 10,
                    color(TEXT_MUTED), 1f);
            return;
        }
        LeafAccount account = service.snapshot().account();
        LeafAccount.Level level = account.levelOrFirst();
        String lv = "Lv " + level.number();
        PixelArt.levelText(canvas, lv, left, rowY, 10, 1f, HorizontalAlign.LEFT);
        float room = right - left - PixelArt.outlinedInkWidth(lv, 10) - 3 * p;
        String amount = balanceText(account.leaf(), room, text -> PixelArt.leafAmountWidth(text, 10, p));
        float amountWidth = PixelArt.leafAmountWidth(amount, 10, p);
        PixelArt.leafAmount(canvas, amount, right - amountWidth, rowY, 10, 1f, p);
        PixelArt.progressBar(canvas, left, barY, right - left, 4 * p, p, LeafHeader.xpRatio(level), green, 1f, 10);
    }

    /** The asset key of an insignia's badge art. */
    static String insigniaKey(SeqBadgeTier tier) {
        return "badge:insignia_" + tier.name().toLowerCase(Locale.ROOT);
    }

    /** Text with its letters coloured along the rank's palette, as the chat pills colour it. */
    private static void gradientText(UiCanvas canvas, String value, float x, float y, ColorRamp colours) {
        if (colours == null || colours.isEmpty()) {
            PixelArt.text(canvas, value, x, y, 10, color(TEXT_SECONDARY), HorizontalAlign.LEFT);
            return;
        }
        float cursor = x;
        int count = value.length();
        for (int i = 0; i < count; i++) {
            String letter = value.substring(i, i + 1);
            double position = count <= 1 ? 0 : (double) i / (count - 1);
            PixelArt.text(canvas, letter, cursor, y, 10, new Color(colours.sample(position)), HorizontalAlign.LEFT);
            cursor += PixelArt.measure(letter, 10);
        }
    }
}
