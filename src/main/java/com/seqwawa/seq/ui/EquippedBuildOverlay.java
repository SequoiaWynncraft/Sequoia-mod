package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.live.EquippedBuild;
import com.seqwawa.seq.wynnbuilder.live.EquippedBuildSession;
import com.seqwawa.seq.wynnbuilder.live.GearAudit;
import com.seqwawa.seq.wynnbuilder.live.LiveItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The panel beside the inventory: what each spell costs, what it deals, and what to replace first.
 *
 * <p>Drawn with plain {@link GuiGraphics} rather than the mod's canvas renderer, matching {@link
 * GuildStorageShortcutOverlay}, because it lives inside a vanilla screen's render pass rather than
 * owning one.
 *
 * <p>Everything shown is read from {@link EquippedBuildSession}, which never recomputes on a frame;
 * this class only lays out whatever numbers are currently available and says plainly when some are
 * not.
 */
public final class EquippedBuildOverlay {
    private static final int PANEL_WIDTH = 176;
    private static final int MARGIN = 6;
    private static final int PADDING = 7;
    private static final int LINE = 10;
    private static final int BUTTON_HEIGHT = 14;

    private static final int PANEL_COLOR = 0xE8101216;
    private static final int PANEL_BORDER = 0xFF2C3540;
    private static final int DIVIDER = 0xFF232B34;
    private static final int TEXT_PRIMARY = 0xFFE8EDF2;
    private static final int TEXT_SECONDARY = 0xFF8C99A6;
    private static final int TEXT_ACCENT = 0xFF6FD3B0;
    private static final int TEXT_WARNING = 0xFFE0B060;
    private static final int TEXT_BAD = 0xFFE07070;
    private static final int BUTTON_COLOR = 0xFF1B222A;
    private static final int BUTTON_HOVER = 0xFF27313C;

    /** Where the buttons ended up on the last frame, so a click can be matched to one. */
    private static Bounds auditButton;
    private static Bounds scanButton;

    private EquippedBuildOverlay() {}

    public static void render(
            GuiGraphics graphics,
            AbstractContainerMenu menu,
            int leftPos,
            int topPos,
            int imageWidth,
            int mouseX,
            int mouseY) {

        auditButton = null;
        scanButton = null;
        if (!isEnabled(menu)) {
            return;
        }

        EquippedBuildSession session = EquippedBuildSession.getInstance();
        session.refresh();
        EquippedBuildSession.State state = session.state();

        List<Line> lines = layout(state, session);
        if (lines.isEmpty()) {
            return;
        }

        int height = PADDING * 2;
        for (Line line : lines) {
            height += line.height();
        }
        int x = leftPos - PANEL_WIDTH - MARGIN;
        if (x < MARGIN) {
            // No room on the left, which happens at small GUI scales; the right side always has it.
            x = leftPos + imageWidth + MARGIN;
        }
        int y = Math.max(MARGIN, topPos);

        graphics.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_COLOR);
        graphics.renderOutline(x, y, PANEL_WIDTH, height, PANEL_BORDER);

        int textY = y + PADDING;
        for (Line line : lines) {
            line.draw(graphics, x + PADDING, textY, PANEL_WIDTH - PADDING * 2, mouseX, mouseY);
            textY += line.height();
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    public static boolean mouseClicked(AbstractContainerMenu menu, double mouseX, double mouseY) {
        if (!isEnabled(menu)) {
            return false;
        }
        EquippedBuildSession session = EquippedBuildSession.getInstance();
        if (auditButton != null && auditButton.contains(mouseX, mouseY)) {
            session.requestAudit();
            return true;
        }
        if (scanButton != null && scanButton.contains(mouseX, mouseY)) {
            // Closes the inventory: the scan works by opening menus of its own, so it cannot run
            // while this one is up.
            session.requestScan();
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.closeContainer();
            }
            return true;
        }
        return false;
    }

    private static boolean isEnabled(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu
                && (SeqClient.getEquippedBuildOverlaySetting() == null
                        || SeqClient.getEquippedBuildOverlaySetting().getValue());
    }

    // ------------------------------------------------------------------ layout

    private static List<Line> layout(EquippedBuildSession.State state, EquippedBuildSession session) {
        List<Line> lines = new ArrayList<>();
        if (!state.hasNumbers()) {
            if (state.status().isEmpty()) {
                return List.of();
            }
            lines.add(Line.heading("Equipped build"));
            lines.add(Line.text(state.status(), TEXT_SECONDARY));
            return lines;
        }

        EquippedBuild.Snapshot snapshot = state.snapshot();
        lines.add(Line.heading(session.isEvaluating() ? "Equipped build (updating)" : "Equipped build"));
        lines.add(Line.pair(
                "Lv. " + snapshot.build().level() + (snapshot.playerClass() == null
                        ? ""
                        : " " + snapshot.playerClass()),
                compact(state.stats().effectiveHealthWithoutDodge()) + " ehp",
                TEXT_SECONDARY));
        lines.add(Line.divider());

        DamageSources.Report report = state.report();
        if (report.spells().isEmpty()) {
            lines.add(Line.text(report.message().isEmpty() ? "No spells" : report.message(), TEXT_SECONDARY));
        } else {
            lines.add(Line.columns("Spell", "Mana", "DPS", TEXT_SECONDARY));
            for (DamageSources.SpellGroup spell : report.spells()) {
                lines.add(Line.columns(
                        spell.name(),
                        String.format(Locale.ROOT, "%.1f", spell.cost()),
                        compact(spell.sustainedDps()),
                        TEXT_PRIMARY));
            }
        }
        if (report.melee() != null) {
            lines.add(Line.columns("Melee", "-", compact(report.melee().perSecond()), TEXT_PRIMARY));
        }

        lines.add(Line.divider());
        layoutAudit(lines, state, session);
        layoutReadiness(lines, state, session);
        return lines;
    }

    private static void layoutAudit(
            List<Line> lines, EquippedBuildSession.State state, EquippedBuildSession session) {

        if (session.isAuditing()) {
            lines.add(Line.text("Measuring every piece...", TEXT_SECONDARY));
            return;
        }
        GearAudit.Result audit = state.audit();
        if (audit == null) {
            lines.add(Line.button("Find my weakest piece", Line.Action.AUDIT));
            return;
        }
        GearAudit.Finding worst = audit.worst();
        if (worst == null) {
            lines.add(Line.text(audit.notes().isEmpty() ? "Nothing to improve" : audit.notes().get(0),
                    TEXT_SECONDARY));
            return;
        }

        lines.add(Line.pair(
                worst.slot().label(),
                String.format(Locale.ROOT, "+%.1f%% %s", worst.headroomPercent(), audit.referenceSource()),
                worst.headroomPercent() >= 1 ? TEXT_WARNING : TEXT_ACCENT));
        lines.add(Line.text(worst.itemName(), TEXT_PRIMARY));
        lines.add(Line.text(worst.advice(), TEXT_SECONDARY));

        if (worst.hasHarmfulRolls()) {
            LiveItem.Roll harmful = worst.harmfulRolls().get(0);
            lines.add(Line.text(harmful.displayName() + " " + harmful.actual() + " hurts", TEXT_BAD));
        } else if (!worst.weakestRolls().isEmpty()) {
            LiveItem.Roll weakest = worst.weakestRolls().get(0);
            lines.add(Line.text(
                    String.format(Locale.ROOT, "%s rolled %.0f%%", weakest.displayName(), weakest.percentage()),
                    TEXT_SECONDARY));
        }
        lines.add(Line.button("Re-measure", Line.Action.AUDIT));
    }

    private static void layoutReadiness(
            List<Line> lines, EquippedBuildSession.State state, EquippedBuildSession session) {

        EquippedBuild.Readiness readiness = state.readiness();
        if (readiness.isComplete()) {
            return;
        }
        lines.add(Line.divider());
        lines.add(Line.text("Not counted: " + String.join(", ", readiness.missing()), TEXT_WARNING));
        if (!readiness.tree() || readiness.treeCoverage() < 100 || !readiness.skillPoints()) {
            lines.add(Line.button("Scan (closes inventory)", Line.Action.SCAN));
        }
    }

    private static void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (scanButton != null && scanButton.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    Component.literal(
                            "Reads your character sheet and ability tree through Wynntils. "
                                    + "Opening those menus yourself does the same thing for free."),
                    mouseX,
                    mouseY);
            return;
        }
        if (auditButton != null && auditButton.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    Component.literal(
                            "Re-runs the whole build once per piece to see which one's rolls cost the most damage."),
                    mouseX,
                    mouseY);
        }
    }

    // ------------------------------------------------------------------ primitives

    /** Compact figures, because a build's damage does not fit a 176 pixel panel in full. */
    private static String compact(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
        }
        if (magnitude >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** One row of the panel. */
    private sealed interface Line {

        void draw(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);

        /** How much vertical room the row takes, since a button needs more than a line of text. */
        default int height() {
            return LINE;
        }

        static Line heading(String text) {
            return new Text(text, TEXT_ACCENT);
        }

        static Line text(String text, int color) {
            return new Text(text, color);
        }

        static Line pair(String left, String right, int color) {
            return new Columns(left, null, right, color);
        }

        static Line columns(String left, String middle, String right, int color) {
            return new Columns(left, middle, right, color);
        }

        static Line divider() {
            return new Divider();
        }

        /** What a button does, which is also how a click finds its way back to it. */
        enum Action {
            AUDIT,
            SCAN
        }

        static Line button(String label, Action action) {
            return new Button(label, action);
        }

        record Text(String text, int color) implements Line {
            @Override
            public void draw(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
                var font = Minecraft.getInstance().font;
                graphics.drawString(font, font.plainSubstrByWidth(text, width), x, y, color, false);
            }
        }

        record Columns(String left, String middle, String right, int color) implements Line {
            @Override
            public void draw(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
                var font = Minecraft.getInstance().font;
                int rightWidth = font.width(right);
                int middleWidth = middle == null ? 0 : font.width(middle);
                int leftWidth = width - rightWidth - middleWidth - 8;

                graphics.drawString(font, font.plainSubstrByWidth(left, Math.max(8, leftWidth)), x, y, color, false);
                if (middle != null) {
                    graphics.drawString(
                            font, middle, x + width - rightWidth - middleWidth - 4, y, TEXT_SECONDARY, false);
                }
                graphics.drawString(font, right, x + width - rightWidth, y, color, false);
            }
        }

        record Divider() implements Line {
            @Override
            public void draw(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
                graphics.fill(x, y + 4, x + width, y + 5, DIVIDER);
            }
        }

        record Button(String label, Action action) implements Line {
            @Override
            public int height() {
                return BUTTON_HEIGHT + 3;
            }

            @Override
            public void draw(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
                Bounds bounds = new Bounds(x, y, width, BUTTON_HEIGHT);
                boolean hovered = bounds.contains(mouseX, mouseY);
                graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                        bounds.y() + bounds.height(), hovered ? BUTTON_HOVER : BUTTON_COLOR);
                graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), DIVIDER);
                var font = Minecraft.getInstance().font;
                graphics.drawCenteredString(
                        font, label, x + width / 2, y + 3, hovered ? TEXT_ACCENT : TEXT_PRIMARY);
                // Recorded at draw time rather than at layout time: the panel's height follows its
                // content, so where a button lands is not known until the rows above it are placed.
                if (action == Action.SCAN) {
                    scanButton = bounds;
                } else {
                    auditButton = bounds;
                }
            }
        }
    }
}
