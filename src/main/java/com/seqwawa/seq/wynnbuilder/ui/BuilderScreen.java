package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT_FOCUSED;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_DANGER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_SUCCESS;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_TRACK;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_WARNING;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.wynnbuilder.BuildLibrary;
import com.seqwawa.seq.wynnbuilder.WynnBuilderSession;
import com.seqwawa.seq.wynnbuilder.calc.PowderCalc;
import com.seqwawa.seq.wynnbuilder.calc.BuildStats;
import com.seqwawa.seq.wynnbuilder.calc.DamageCalc;
import com.seqwawa.seq.wynnbuilder.calc.ExternalBoosts;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.calc.PowderSpecials;
import com.seqwawa.seq.wynnbuilder.calc.RaidBuffs;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine;
import com.seqwawa.seq.wynnbuilder.calc.SpellCalc;
import com.seqwawa.seq.wynnbuilder.calc.ItemDetails;
import com.seqwawa.seq.wynnbuilder.calc.SkillPoints;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import com.seqwawa.seq.wynnbuilder.data.WynnTome;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * The build editor: nine equipment slots with powders on the left, aggregated statistics on the
 * right, and the link import/export controls in the header.
 */
public final class BuilderScreen extends Screen {
    private static final float SLOT_HEIGHT = 30;
    private static final float SLOT_GAP = 4;
    private static final float ACTION_HEIGHT = 22;
    private static final float SCROLL_STEP = 30;
    private static final String SECTION_IDENTIFICATIONS = "Identifications";
    private static final String SECTION_DAMAGE = "Damage";
    private static final String SECTION_BUFFS = "Buffs";
    private static final String SECTION_TOMES = "Tomes";
    private static final String SECTION_ASPECTS = "Aspects";

    private final Screen parent;
    private final WynnBuilderSession session = WynnBuilderSession.getInstance();
    private final ItemPickerOverlay picker = new ItemPickerOverlay();
    private final Map<EquipmentSlot, Rect> slotHitboxes = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, Rect> powderHitboxes = new EnumMap<>(EquipmentSlot.class);
    private final List<Action> actions = new ArrayList<>();

    private float mouseX;
    private float mouseY;
    /**
     * True while the picker overlay is up.
     *
     * <p>The panels behind a modal must not react at all: hit boxes are not registered, so nothing
     * behind can be clicked, and the pointer is moved off screen while they draw, so nothing behind
     * lights up under the cursor either.
     */
    private boolean modal;
    private float statsScroll;
    private float maxStatsScroll;
    private Rect levelDown;
    private Rect levelUp;
    private EquipmentSlot powderSlot;
    private final List<Track> tracks = new ArrayList<>();
    private Track draggedTrack;

    /** A slider track and the value range it maps a pointer position onto. */
    private record Track(Rect bounds, StatLine.Slider slider) {
        void applyAt(float pointerX) {
            float fraction = WynnBuilderUi.clamp((pointerX - bounds.x()) / Math.max(1, bounds.width()), 0, 1);
            slider.onChange().accept(Math.round(fraction * slider.maximum()));
        }
    }
    private String saveName = "";
    private boolean editingSaveName;
    private Tab activeTab = Tab.BUILD;
    private BuffCategory buffCategory = BuffCategory.ABILITY;
    private RaidBuffs.Raid activeRaid = RaidBuffs.Raid.NOTG;
    private Powder.PowderElement activeSpecialElement = Powder.PowderElement.EARTH;

    /** The three families of temporary boost, matching how the site groups them. */
    private enum BuffCategory {
        ABILITY("Ability boosts"),
        POWDER("Powder specials"),
        RAID("Raid buffs");

        private final String label;

        BuffCategory(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
    /** Slots whose breakdown is expanded; everything starts collapsed. */
    private final java.util.EnumSet<EquipmentSlot> expandedSlots = java.util.EnumSet.noneOf(EquipmentSlot.class);
    /**
     * Build sections the user has folded away.
     *
     * <p>Identifications starts folded because it is by far the longest section and pushes the
     * summary off screen; the short summaries start open since they are why the screen is opened.
     */
    private final java.util.Set<String> expandedSources = new java.util.HashSet<>();
    private final java.util.Set<String> collapsedSections = new java.util.HashSet<>(java.util.Set.of(SECTION_IDENTIFICATIONS));

    /** The right panel shows either the aggregated build, or a per-item breakdown. */
    private enum Tab {
        BUILD("Build"),
        ITEMS("Items"),
        DAMAGE("Damage"),
        LOADOUT("Tomes"),
        LIBRARY("Saved");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record Rect(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return WynnBuilderUi.contains(pointX, pointY, x, y, width, height);
        }
    }

    private record Action(Rect bounds, Runnable onClick) {}

    /** Registers a clickable region, unless a modal is covering the screen. */
    private void register(Rect bounds, Runnable onClick) {
        if (!modal) {
            actions.add(new Action(bounds, onClick));
        }
    }

    public BuilderScreen(Screen parent) {
        super(Component.literal("WynnBuilder Builder"));
        this.parent = parent;
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
        modal = picker.isOpen();
        actions.clear();
        tracks.clear();
        slotHitboxes.clear();
        powderHitboxes.clear();

        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 205));
        canvas.fillRect(0, 0, width, WynnBuilderUi.HEADER_HEIGHT, color(BACKGROUND_HEADER, 245));

        WynnBuilderUi.drawLeft(canvas, "Builder", WynnBuilderUi.OUTER_MARGIN,
                WynnBuilderUi.HEADER_HEIGHT / 2f, 20, color(ACCENT_PRIMARY));

        if (!session.isReady()) {
            WynnBuilderUi.drawCentered(canvas, session.status(), width / 2f, height / 2f, 13, color(TEXT_MUTED));
            return;
        }

        drawHeaderActions(canvas, width);

        float top = WynnBuilderUi.HEADER_HEIGHT + WynnBuilderUi.OUTER_MARGIN;
        float panelHeight = Math.max(120, height - top - WynnBuilderUi.OUTER_MARGIN);
        // Damage and loadout do not refer to the slot list, so they take the whole width. Buff and
        // spell rows need it far more than a column of equipment the player is not editing.
        boolean showEquipment = activeTab == Tab.BUILD || activeTab == Tab.ITEMS;
        float leftWidth = showEquipment ? WynnBuilderUi.clamp(width * 0.42f, 250, 360) : 0;
        float leftX = WynnBuilderUi.OUTER_MARGIN;
        float rightX = showEquipment ? leftX + leftWidth + 10 : leftX;
        float rightWidth = Math.max(150, width - rightX - WynnBuilderUi.OUTER_MARGIN);

        // The panels read the pointer from the fields, so parking it off screen for the duration
        // suppresses every hover highlight behind the modal without touching each call site.
        float pointerX = mouseX;
        float pointerY = mouseY;
        if (modal) {
            mouseX = Float.NEGATIVE_INFINITY;
            mouseY = Float.NEGATIVE_INFINITY;
        }
        if (showEquipment) {
            drawEquipmentPanel(canvas, leftX, top, leftWidth, panelHeight);
        }
        drawStatsPanel(canvas, rightX, top, rightWidth, panelHeight);
        mouseX = pointerX;
        mouseY = pointerY;

        picker.draw(canvas, mouseX, mouseY);
    }

    private void drawHeaderActions(UiCanvas canvas, float width) {
        float buttonY = 9;
        float x = width - WynnBuilderUi.OUTER_MARGIN;

        x -= 64;
        addAction(canvas, x, buttonY, 64, ACTION_HEIGHT, "Clear", session::clearBuild);
        x -= 70;
        addAction(canvas, x, buttonY, 66, ACTION_HEIGHT, "Paste link", this::pasteLink);
        x -= 74;
        addAction(canvas, x, buttonY, 70, ACTION_HEIGHT, "Copy link", this::copyLink);
        x -= 86;
        addAction(canvas, x, buttonY, 82, ACTION_HEIGHT, "Ability tree", this::openAbilityTree);

        // Message line sits under the header so it never overlaps the buttons.
        if (!session.message().isEmpty()) {
            WynnBuilderUi.drawRight(canvas, session.message(), width - WynnBuilderUi.OUTER_MARGIN,
                    WynnBuilderUi.HEADER_HEIGHT - 6, 10,
                    session.messageIsError() ? color(CONTROL_DANGER) : color(CONTROL_SUCCESS));
        }
    }

    private void addAction(UiCanvas canvas, float x, float y, float width, float height, String label, Runnable onClick) {
        WynnBuilderUi.drawButton(canvas, x, y, width, height, label, mouseX, mouseY);
        register(new Rect(x, y, width, height), onClick);
    }

    // ------------------------------------------------------------------ equipment

    private void drawEquipmentPanel(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, WynnBuilderUi.PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));

        WynnBuild build = session.build();
        WynnDataSet data = session.data();
        BuildStats stats = session.stats();

        float cursorY = y + 10;
        drawLevelControl(canvas, x + 10, cursorY, width - 20, build);
        cursorY += 28;

        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            drawSlot(canvas, x + 10, cursorY, width - 20, slot, build, data, stats);
            cursorY += SLOT_HEIGHT + SLOT_GAP;
        }

        if (powderSlot != null && powderSlot.powderable()) {
            drawPowderEditor(canvas, x + 10, cursorY + 4, width - 20, build, stats);
        }
    }

    /** The powder editor: what is applied, and a chip per element and tier to add one. */
    private void drawPowderEditor(UiCanvas canvas, float x, float y, float width, WynnBuild build, BuildStats stats) {
        WynnItem item = stats == null ? null : stats.resolvedItems().get(powderSlot);
        int capacity = item == null ? 5 : item.powderSlots();
        List<Powder> powders = build.powders(powderSlot);

        WynnBuilderUi.drawLeft(canvas, powderSlot.label() + " powders  (" + powders.size() + " / " + capacity + ")",
                x, y + 8, 10, color(ACCENT_PRIMARY));

        float cursorY = y + 20;
        List<StatLine> lines = new ArrayList<>();
        if (!powders.isEmpty()) {
            List<StatLine.Chip> applied = new ArrayList<>();
            for (int i = 0; i < powders.size(); i++) {
                int index = i;
                applied.add(new StatLine.Chip(powders.get(i).displayName(), true, () -> {
                    build.powders(powderSlot).remove(index);
                    session.invalidate();
                }));
            }
            lines.add(StatLine.chipRow(applied));
        }
        for (Powder.PowderElement element : Powder.PowderElement.encodingOrder()) {
            List<StatLine.Chip> tiers = new ArrayList<>();
            for (int tier = 1; tier <= PowderCalc.MAX_TIER; tier++) {
                int chosen = tier;
                tiers.add(new StatLine.Chip(element.symbol() + String.valueOf(tier), false, () -> {
                    if (build.powders(powderSlot).size() < capacity) {
                        build.powders(powderSlot).add(new Powder(element, chosen));
                        session.invalidate();
                    } else {
                        session.setMessage("All powder slots are full", true);
                    }
                }));
            }
            lines.add(StatLine.chipRow(tiers));
        }

        float height = StatLineRenderer.contentHeight(lines, width);
        StatLineRenderer.draw(canvas, lines, x - 12, cursorY, width + 12, height, 0, mouseX, mouseY,
                (rectX, rectY, rectWidth, rectHeight, onClick) ->
                        register(new Rect(rectX, rectY, rectWidth, rectHeight), onClick));
    }

    private void drawLevelControl(UiCanvas canvas, float x, float y, float width, WynnBuild build) {
        canvas.fillRoundedRect(x, y, width, 22, 5, color(CONTROL_INPUT, 200));
        WynnBuilderUi.drawLeft(canvas, "Level", x + 8, y + 11, 11, color(TEXT_SECONDARY));
        WynnBuilderUi.drawCentered(canvas, String.valueOf(build.level()), x + width / 2f, y + 11, 12, color(TEXT_PRIMARY));

        levelDown = modal ? null : new Rect(x + width - 52, y + 2, 22, 18);
        levelUp = modal ? null : new Rect(x + width - 26, y + 2, 22, 18);
        WynnBuilderUi.drawButton(canvas, x + width - 52, y + 2, 22, 18, "-", mouseX, mouseY);
        WynnBuilderUi.drawButton(canvas, x + width - 26, y + 2, 22, 18, "+", mouseX, mouseY);
    }

    private void drawSlot(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            EquipmentSlot slot,
            WynnBuild build,
            WynnDataSet data,
            BuildStats stats) {

        boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, x, y, width, SLOT_HEIGHT);
        canvas.fillRoundedRect(x, y, width, SLOT_HEIGHT, 5,
                hovered ? color(CONTROL_INPUT_HOVER, 220) : color(CONTROL_INPUT, 200));
        if (!modal) {
            slotHitboxes.put(slot, new Rect(x, y, width, SLOT_HEIGHT));
        }

        WynnBuilderUi.drawLeft(canvas, slot.label(), x + 8, y + 10, 9, color(TEXT_MUTED));

        BuildEquipment equipment = build.equipment(slot);
        String label;
        Color labelColor = color(TEXT_MUTED);
        if (equipment instanceof BuildEquipment.Normal normal) {
            WynnItem item = data.item(normal.itemId());
            label = item == null ? "Unknown item #" + normal.itemId() : item.displayName();
            labelColor = item == null ? color(CONTROL_WARNING) : WynnBuilderUi.rarityColor(item.tier());
        } else if (equipment instanceof BuildEquipment.Crafted) {
            label = "Crafted item";
            labelColor = WynnBuilderUi.rarityColor(WynnItem.Tier.CRAFTED);
        } else if (equipment instanceof BuildEquipment.Custom) {
            label = "Custom item";
            labelColor = color(CONTROL_WARNING);
        } else {
            label = "Empty";
        }

        float powderWidth = slot.powderable() ? 92 : 0;
        WynnBuilderUi.drawLeft(canvas, WynnBuilderUi.ellipsize(label, width - 20 - powderWidth, 11),
                x + 8, y + 21, 11, labelColor);

        if (slot.powderable()) {
            float powderX = x + width - powderWidth - 6;
            Rect powderRect = new Rect(powderX, y + 6, powderWidth, SLOT_HEIGHT - 12);
            if (!modal) {
                powderHitboxes.put(slot, powderRect);
            }
            boolean powderHovered = powderRect.contains(mouseX, mouseY);
            canvas.fillRoundedRect(powderRect.x(), powderRect.y(), powderRect.width(), powderRect.height(), 4,
                    powderHovered ? color(CONTROL_INPUT_HOVER, 235) : color(CONTROL_TRACK, 180));
            List<Powder> powders = build.powders(slot);
            String powderText = powders.isEmpty()
                    ? "no powders"
                    : powders.stream().map(Powder::shortName).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
            WynnBuilderUi.drawCentered(canvas, WynnBuilderUi.ellipsize(powderText, powderWidth - 8, 9),
                    powderRect.x() + powderRect.width() / 2f, powderRect.y() + powderRect.height() / 2f, 9,
                    powders.isEmpty() ? color(TEXT_MUTED) : color(TEXT_SECONDARY));
        }

        // Flag an item the build cannot actually wear.
        WynnItem resolved = stats == null ? null : stats.resolvedItems().get(slot);
        if (resolved != null && resolved.level() > build.level()) {
            canvas.strokeRect(x, y, width, SLOT_HEIGHT, 1, color(CONTROL_DANGER));
        }
    }

    // ------------------------------------------------------------------ stats

    private void drawStatsPanel(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, WynnBuilderUi.PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        BuildStats stats = session.stats();
        if (stats == null) {
            return;
        }

        float tabWidth = 44;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            float tabX = x + 10 + i * (tabWidth + 4);
            boolean active = activeTab == tab;
            canvas.fillRoundedRect(tabX, y + 6, tabWidth, 20, 4,
                    active ? color(BACKGROUND_CONTENT_FOCUSED, 255) : color(CONTROL_INPUT, 200));
            WynnBuilderUi.drawCentered(canvas, tab.label(), tabX + tabWidth / 2f, y + 16, 10,
                    active ? color(ACCENT_PRIMARY) : color(TEXT_SECONDARY));
            register(new Rect(tabX, y + 6, tabWidth, 20), () -> {
                activeTab = tab;
                // Each tab keeps its own reading position; switching resets only this one.
                statsScroll = 0;
            });
        }
        addAction(canvas, x + width - 82, y + 6, 72, 20, "Roll: " + session.rollMode().label(), session::cycleRollMode);

        float contentTop = y + 32;
        float contentHeight = Math.max(0, height - (contentTop - y) - 8);

        List<StatLine> lines = switch (activeTab) {
            case BUILD -> buildStatLines(stats);
            case ITEMS -> buildItemLines();
            case DAMAGE -> buildDamageLines(stats);
            case LOADOUT -> buildLoadoutLines();
            case LIBRARY -> buildLibraryLines();
        };
        float total = StatLineRenderer.contentHeight(lines, width);
        maxStatsScroll = Math.max(0, total - contentHeight);
        statsScroll = WynnBuilderUi.clamp(statsScroll, 0, maxStatsScroll);

        StatLineRenderer.draw(canvas, lines, x, contentTop, width, contentHeight, statsScroll, mouseX, mouseY,
                new StatLineRenderer.ClickRegistrar() {
                    @Override
                    public void register(float rectX, float rectY, float rectWidth, float rectHeight,
                            Runnable onClick) {
                        BuilderScreen.this.register(new Rect(rectX, rectY, rectWidth, rectHeight), onClick);
                    }

                    @Override
                    public void registerTrack(float rectX, float rectY, float rectWidth, float rectHeight,
                            StatLine.Slider slider) {
                        if (!modal) {
                            tracks.add(new Track(new Rect(rectX, rectY, rectWidth, rectHeight), slider));
                        }
                    }
                });
        StatLineRenderer.drawScrollbar(canvas, x, contentTop, width, contentHeight, statsScroll, total);
    }

    private List<StatLine> buildStatLines(BuildStats stats) {
        List<StatLine> lines = new ArrayList<>();

        if (section(lines, "Survivability")) {
            lines.add(StatLine.text("Health", String.valueOf(stats.health()), color(TEXT_PRIMARY)));
            int effective = stats.effectiveHealth();
            lines.add(StatLine.text("Effective health",
                    effective == Integer.MAX_VALUE ? "huge" : String.valueOf(effective), color(TEXT_PRIMARY)));
            boolean anyDefence = false;
            for (int value : stats.elementalDefences()) {
                if (value != 0) {
                    anyDefence = true;
                    break;
                }
            }
            if (anyDefence) {
                lines.add(StatLine.subheading("Elemental defence"));
                for (int i = 0; i < stats.elementalDefences().length; i++) {
                    int value = stats.elementalDefences()[i];
                    if (value != 0) {
                        String element = Identifications.elementLabel(Identifications.ELEMENT_PREFIXES.get(i + 1));
                        lines.add(StatLine.text(element, String.valueOf(value),
                                WynnBuilderUi.statColor(value, false)));
                    }
                }
            }
        }

        if (section(lines, "Skill points")) {
            for (int i = 0; i < SkillPoints.TYPES; i++) {
                int index = i;
                String name = WynnBuild.SKILL_POINT_ORDER.get(i);
                int assigned = stats.assignedSkillPoints()[i];
                int total = stats.skillPointTotals()[i];
                boolean manual = session.build().assignedSkillPoint(i) != null;
                // The slider doubles as the manual editor: touching it pins the value, and the
                // reset chip hands the element back to the solver.
                // The slider reads and writes the same number the setter expects: the element's
                // final total, gear included. Showing the points spent while writing the total made
                // every click jump by the gear's contribution, and could drive the spend negative.
                lines.add(StatLine.sliderRow(
                        name + (manual ? " (manual)" : " (auto)") + "  ->  " + assigned + " assigned",
                        total,
                        SkillPoints.SOFT_CAP,
                        value -> {
                            session.build().setAssignedSkillPoint(index, value);
                            session.invalidate();
                        }));
            }
            lines.add(StatLine.chipRow(List.of(new StatLine.Chip("Reset to automatic", false, () -> {
                session.build().clearManualSkillPoints();
                session.invalidate();
            }))));
            lines.add(StatLine.divider());
            boolean withinBudget = stats.assignedTotal() <= stats.availableSkillPoints();
            lines.add(StatLine.text("Assigned total",
                    stats.assignedTotal() + " / " + stats.availableSkillPoints(),
                    withinBudget ? color(CONTROL_SUCCESS) : color(CONTROL_DANGER)));
        }

        if (!stats.problems().isEmpty() && section(lines, "Problems")) {
            for (String problem : stats.problems()) {
                lines.add(StatLine.text(problem, "", color(CONTROL_DANGER)));
            }
        }

        if (!stats.activeSets().isEmpty() && section(lines, "Set bonuses")) {
            for (Map.Entry<String, Integer> entry : stats.activeSets().entrySet()) {
                lines.add(StatLine.text(entry.getKey(), entry.getValue() + " pieces", color(CONTROL_SUCCESS)));
            }
        }

        if (!stats.majorIds().isEmpty() && section(lines, "Major IDs")) {
            var majorIdData = session.data().majorIds();
            for (String majorId : stats.majorIds()) {
                lines.add(StatLine.text(majorIdData.displayName(majorId), "", color(CONTROL_WARNING)));
                String description = majorIdData.description(majorId);
                if (!description.isEmpty()) {
                    lines.add(StatLine.text(stripMarkup(description), "", color(TEXT_MUTED)));
                }
            }
        }

        if (section(lines, SECTION_IDENTIFICATIONS)) {
            appendGroupedIdentifications(lines, stats);
        }

        lines.add(StatLine.spacer());
        return lines;
    }

    /**
     * Lists the identifications in labelled blocks.
     *
     * <p>A flat alphabetical list of forty-odd stats is unreadable, so they are split into offence,
     * defence and so on, each block sorted by the name shown rather than the internal key.
     */
    private void appendGroupedIdentifications(List<StatLine> lines, BuildStats stats) {
        Map<Identifications.Group, List<Map.Entry<String, Integer>>> grouped =
                new java.util.EnumMap<>(Identifications.Group.class);
        for (Map.Entry<String, Integer> entry : stats.identifications().entrySet()) {
            if (entry.getValue() == 0 || !Identifications.isDisplayable(entry.getKey())) {
                continue;
            }
            grouped.computeIfAbsent(Identifications.group(entry.getKey()), ignored -> new ArrayList<>()).add(entry);
        }
        if (grouped.isEmpty()) {
            lines.add(StatLine.text("No identifications yet", "", color(TEXT_MUTED)));
            return;
        }

        boolean first = true;
        for (Identifications.Group group : Identifications.Group.values()) {
            List<Map.Entry<String, Integer>> entries = grouped.get(group);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            if (!first) {
                lines.add(StatLine.divider());
            }
            first = false;
            lines.add(StatLine.subheading(group.label()));
            entries.stream()
                    .sorted(java.util.Comparator.comparing(
                            entry -> Identifications.displayName(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> lines.add(StatLine.text(
                            Identifications.displayName(entry.getKey()),
                            WynnBuilderUi.formatStat(entry.getValue(), Identifications.isPercentage(entry.getKey())),
                            WynnBuilderUi.statColor(entry.getValue(), Identifications.isInverted(entry.getKey())))));
        }
    }

    /**
     * Appends a foldable section header.
     *
     * @return whether the section is open, so the caller can skip building its contents
     */
    private boolean section(List<StatLine> lines, String title) {
        boolean expanded = !collapsedSections.contains(title);
        lines.add(StatLine.expander((expanded ? "[-] " : "[+] ") + title, null, () -> {
            if (!collapsedSections.remove(title)) {
                collapsedSections.add(title);
            }
        }));
        return expanded;
    }

    /**
     * Damage from every source the build has, plus the buffs that change it.
     *
     * <p>Buffs come from the selected abilities: each one that the player can switch on and off is
     * offered here, because the same build deals very different damage depending on which are up.
     */
    private List<StatLine> buildDamageLines(BuildStats stats) {
        List<StatLine> lines = new ArrayList<>();
        var evaluation = session.abilityTreeEvaluation();
        DamageSources.Report report =
                DamageSources.compute(session.build(), session.data(), stats, evaluation);

        if (section(lines, SECTION_BUFFS)) {
            appendBuffPicker(lines, evaluation);
        }

        if (report.isEmpty()) {
            lines.add(StatLine.heading("Damage"));
            lines.add(StatLine.text(report.message(), "", color(TEXT_MUTED)));
            return lines;
        }

        if (section(lines, SECTION_DAMAGE)) {
            if (report.weaponName() != null) {
                lines.add(StatLine.text(report.weaponName(),
                        capitalise(report.attackSpeed()), color(TEXT_PRIMARY)));
            }
            if (report.melee() != null) {
                lines.add(StatLine.divider());
                lines.add(StatLine.subheading("Melee"));
                lines.add(StatLine.text("Per hit", format(report.melee().perHit()), color(CONTROL_WARNING)));
                lines.add(StatLine.text("Damage per second",
                        format(report.melee().perSecond()), color(CONTROL_WARNING)));
            }
            lines.add(StatLine.divider());
            lines.add(StatLine.subheading("Sustain"));
            lines.add(StatLine.text("Critical chance",
                    Math.round(DamageCalc.critChance(stats) * 100) + "%", color(TEXT_SECONDARY)));
            lines.add(StatLine.text("Mana per second",
                    String.format(java.util.Locale.ROOT, "%.1f", SpellCalc.manaPerSecond(stats)),
                    color(TEXT_SECONDARY)));
        }

        if (!report.spells().isEmpty()) {
            lines.add(StatLine.heading("Spells"));
            boolean first = true;
            for (DamageSources.SpellGroup spell : report.spells()) {
                if (!first) {
                    lines.add(StatLine.divider());
                }
                first = false;

                // The spell is the row: its name and its damage. Everything else is the explanation
                // behind it, which only appears when asked for.
                String key = "spell/" + spell.name();
                boolean open = expandedSources.contains(key);
                lines.add(StatLine.expanderValue(
                        (open ? "[-] " : "[+] ") + spell.name()
                                + (spell.cost() > 0
                                        ? String.format(
                                                java.util.Locale.ROOT, "  (%.2f mana)", spell.cost())
                                        : ""),
                        format(spell.headline()),
                        color(CONTROL_WARNING),
                        () -> {
                            if (!expandedSources.remove(key)) {
                                expandedSources.add(key);
                            }
                        }));
                if (!open) {
                    continue;
                }
                for (DamageSources.Source part : spell.parts()) {
                    appendSource(lines, spell.name(), part);
                }
                if (spell.castsPerSecond() > 0) {
                    lines.add(StatLine.text("Casts per second",
                            String.format(java.util.Locale.ROOT, "%.2f", spell.castsPerSecond()),
                            color(TEXT_MUTED)));
                    lines.add(StatLine.text("Sustained DPS", format(spell.sustainedDps()), color(CONTROL_WARNING)));
                }
            }
        } else if (!report.message().isEmpty()) {
            lines.add(StatLine.heading("Spells"));
            lines.add(StatLine.text(report.message(), "", color(TEXT_MUTED)));
        }

        lines.add(StatLine.spacer());
        return lines;
    }

    /**
     * Tomes and aspects: the parts of a loadout that are not gear.
     *
     * <p>Both are carried in the link already; this makes them editable in game rather than only
     * arriving with an imported build.
     */
    private List<StatLine> buildLoadoutLines() {
        List<StatLine> lines = new ArrayList<>();
        WynnDataSet data = session.data();
        WynnBuild build = session.build();

        if (section(lines, SECTION_TOMES)) {
            List<WynnTome.Slot> slots = WynnTome.Slot.encodingOrder();
            for (int i = 0; i < slots.size() && i < build.tomeIds().size(); i++) {
                WynnTome.Slot slot = slots.get(i);
                Integer tomeId = build.tomeIds().get(i);
                WynnTome tome = tomeId == null ? null : data.tome(tomeId);
                String name = tome == null ? "Empty" : tome.displayName();
                int index = i;
                lines.add(StatLine.button(slot.label() + ":  " + name, () -> openTomePicker(index, slot)));
            }
        }

        String playerClass = session.playerClass();
        if (section(lines, SECTION_ASPECTS)) {
            if (playerClass == null) {
                lines.add(StatLine.text("Equip a weapon to choose aspects", "", color(TEXT_MUTED)));
            } else {
                for (int i = 0; i < build.aspects().size(); i++) {
                    WynnBuild.AspectSelection selection = build.aspects().get(i);
                    String name = "Empty";
                    if (selection != null) {
                        var aspect = data.aspect(playerClass, selection.aspectId());
                        name = (aspect == null ? "Unknown aspect" : aspect.displayName())
                                + "  (tier " + selection.tier() + ")";
                    }
                    int index = i;
                    lines.add(StatLine.button("Aspect " + (i + 1) + ":  " + name,
                            () -> openAspectPicker(index, playerClass)));
                }
                lines.add(StatLine.divider());
                lines.add(StatLine.text("Click an aspect again to raise its tier", "", color(TEXT_MUTED)));
            }
        }

        lines.add(StatLine.spacer());
        return lines;
    }

    private void openTomePicker(int index, WynnTome.Slot slot) {
        List<ItemPickerOverlay.Entry> entries = new ArrayList<>();
        for (WynnTome tome : session.data().tomesForSlot(slot)) {
            entries.add(new ItemPickerOverlay.Entry(
                    tome.id(), tome.displayName(), "Lv. " + tome.level(), tome.tier(), tome.level()));
        }
        picker.open("Choose " + slot.label(), entries,
                entry -> {
                    session.build().tomeIds().set(index, entry.id());
                    session.invalidate();
                },
                () -> {
                    session.build().tomeIds().set(index, null);
                    session.invalidate();
                });
    }

    /**
     * Picks an aspect for a slot.
     *
     * <p>Choosing the aspect already in the slot steps its tier instead, which is the quickest way to
     * set one without a second control.
     */
    private void openAspectPicker(int index, String playerClass) {
        WynnBuild build = session.build();
        List<ItemPickerOverlay.Entry> entries = new ArrayList<>();
        for (var aspect : session.data().aspects(playerClass)) {
            entries.add(new ItemPickerOverlay.Entry(
                    aspect.id(), aspect.displayName(), aspect.tier().label(), aspect.tier(), 0));
        }
        picker.open("Choose aspect " + (index + 1), entries,
                entry -> {
                    WynnBuild.AspectSelection current = build.aspects().get(index);
                    int tier = 1;
                    if (current != null && current.aspectId() == entry.id()) {
                        var aspect = session.data().aspect(playerClass, entry.id());
                        int maximum = aspect == null || aspect.tiers().isEmpty() ? 4 : aspect.tiers().size();
                        tier = current.tier() % maximum + 1;
                    }
                    build.aspects().set(index, new WynnBuild.AspectSelection(entry.id(), tier));
                    session.invalidate();
                },
                () -> {
                    build.aspects().set(index, null);
                    session.invalidate();
                });
    }

    /**
     * Every boost the player can switch on, in three families.
     *
     * <p>Ability boosts come from the selected abilities and so vary per build; powder specials and
     * raid buffs are fixed tables. All three feed the same stat totals, which is why they sit
     * together rather than being scattered.
     */
    private void appendBuffPicker(List<StatLine> lines, AbilityTreeEngine.Evaluation evaluation) {
        lines.add(StatLine.chipRow(
                java.util.Arrays.stream(BuffCategory.values())
                        .map(category -> new StatLine.Chip(
                                category.label(),
                                buffCategory == category,
                                () -> buffCategory = category))
                        .toList()));

        switch (buffCategory) {
            case ABILITY -> appendAbilityBoosts(lines, evaluation);
            case POWDER -> appendPowderSpecials(lines);
            case RAID -> appendRaidBuffs(lines);
        }
    }

    private void appendAbilityBoosts(List<StatLine> lines, AbilityTreeEngine.Evaluation evaluation) {
        // Party boosts first: they are always available, unlike the build's own toggles.
        lines.add(StatLine.subheading("From your party"));
        lines.add(StatLine.chipRow(ExternalBoosts.all().stream()
                .map(boost -> new StatLine.Chip(
                        boost.label(),
                        session.enabledExternalBoosts().contains(boost.id()),
                        () -> session.toggleExternalBoost(boost.id())))
                .toList()));
        for (ExternalBoosts.Boost boost : ExternalBoosts.all()) {
            if (session.enabledExternalBoosts().contains(boost.id())) {
                lines.add(StatLine.text(boost.label(), boost.detail(), color(CONTROL_SUCCESS)));
            }
        }

        lines.add(StatLine.divider());
        lines.add(StatLine.subheading("From your abilities"));
        if (evaluation.toggles().isEmpty() && evaluation.sliders().isEmpty()) {
            lines.add(StatLine.text("Select abilities to unlock their boosts", "", color(TEXT_MUTED)));
            return;
        }
        if (!evaluation.toggles().isEmpty()) {
            lines.add(StatLine.chipRow(evaluation.toggles().stream()
                    .map(toggle -> new StatLine.Chip(
                            toggle,
                            session.enabledToggles().contains(toggle),
                            () -> {
                                session.toggleAbilityToggle(toggle);
                                session.invalidate();
                            }))
                    .toList()));
        }
        // Sliders drive effects that scale with something the player does, such as hits landed.
        for (AbilityTreeEngine.Slider slider : evaluation.sliders()) {
            int value = session.sliderValues().getOrDefault(slider.name(), 0);
            lines.add(StatLine.sliderRow(slider.name(), value, slider.maximum(),
                    next -> session.setSliderValue(slider.name(), next)));
        }
    }

    private void appendPowderSpecials(List<StatLine> lines) {
        lines.add(StatLine.chipRow(Powder.PowderElement.encodingOrder().stream()
                .map(element -> new StatLine.Chip(
                        element.label(),
                        activeSpecialElement == element,
                        () -> activeSpecialElement = element))
                .toList()));

        for (PowderSpecials.Special special : PowderSpecials.forElement(activeSpecialElement)) {
            int current = session.powderSpecialLevels().getOrDefault(special.name(), 0);
            lines.add(StatLine.subheading(special.name() + "  (" + special.kind().label() + ")"));
            lines.add(StatLine.text(special.description(),
                    current > 0 && special.boostStat() != null
                            ? WynnBuilderUi.formatStat(special.valueAt(current),
                                    Identifications.isPercentage(special.boostStat()))
                            : "",
                    color(TEXT_MUTED)));

            List<StatLine.Chip> levels = new ArrayList<>();
            levels.add(new StatLine.Chip("Off", current == 0,
                    () -> session.cyclePowderSpecial(special.name(), 0)));
            for (int level = 1; level <= PowderSpecials.MAX_LEVEL; level++) {
                int chosen = level;
                levels.add(new StatLine.Chip("Lv." + level, current == level,
                        () -> session.cyclePowderSpecial(special.name(), chosen)));
            }
            lines.add(StatLine.chipRow(levels));
        }
    }

    private void appendRaidBuffs(List<StatLine> lines) {
        lines.add(StatLine.chipRow(java.util.Arrays.stream(RaidBuffs.Raid.values())
                .map(raid -> new StatLine.Chip(raid.name(), activeRaid == raid, () -> activeRaid = raid))
                .toList()));
        lines.add(StatLine.text(activeRaid.label(), "", color(TEXT_MUTED)));

        lines.add(StatLine.chipRow(RaidBuffs.forRaid(activeRaid).stream()
                .map(buff -> new StatLine.Chip(
                        buff.name(),
                        session.enabledRaidBuffs().contains(buff.name()),
                        () -> {
                            session.toggleRaidBuff(buff.name());
                            session.invalidate();
                        }))
                .toList()));

        if (!session.enabledRaidBuffs().isEmpty()) {
            lines.add(StatLine.divider());
            lines.add(StatLine.subheading("Active"));
            for (String name : session.enabledRaidBuffs()) {
                RaidBuffs.Buff buff = RaidBuffs.byName(name);
                if (buff == null) {
                    continue;
                }
                String summary = buff.stats().entrySet().stream()
                        .map(entry -> Identifications.displayName(entry.getKey()) + " "
                                + WynnBuilderUi.formatStat(entry.getValue(),
                                        Identifications.isPercentage(entry.getKey())))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                lines.add(StatLine.text(name, "", color(CONTROL_SUCCESS)));
                if (!summary.isEmpty()) {
                    lines.add(StatLine.text(summary, "", color(TEXT_MUTED)));
                }
            }
        }
    }

    /** Builds saved on disk, with the current one savable and each entry loadable. */
    private List<StatLine> buildLibraryLines() {
        List<StatLine> lines = new ArrayList<>();
        var library = BuildLibrary.getInstance();

        lines.add(StatLine.heading("Save current build"));
        lines.add(StatLine.text("Name", saveName.isEmpty() ? "(click to type)" : saveName + "_",
                saveName.isEmpty() ? color(TEXT_MUTED) : color(TEXT_PRIMARY)));
        lines.add(StatLine.chipRow(List.of(
                new StatLine.Chip("Type name", editingSaveName, () -> editingSaveName = !editingSaveName),
                new StatLine.Chip("Save", false, this::saveCurrentBuild))));

        lines.add(StatLine.heading("Saved builds"));
        var saved = library.all();
        if (saved.isEmpty()) {
            lines.add(StatLine.text("Nothing saved yet", "", color(TEXT_MUTED)));
        }
        for (var build : saved) {
            lines.add(StatLine.subheading(build.name()));
            lines.add(StatLine.chipRow(List.of(
                    new StatLine.Chip("Load", false, () -> {
                        session.importBuildLink(build.hash());
                        activeTab = Tab.BUILD;
                    }),
                    new StatLine.Chip("Copy link", false, () -> {
                        SeqClient.mc.keyboardHandler.setClipboard(
                                com.seqwawa.seq.wynnbuilder.codec.WynnBuilderLinks.buildUrl(build.hash()));
                        session.setMessage("Link copied", false);
                    }),
                    new StatLine.Chip("Delete", false, () -> {
                        BuildLibrary.getInstance().delete(build.name());
                        session.setMessage("Deleted " + build.name(), false);
                    }))));
        }
        lines.add(StatLine.spacer());
        return lines;
    }

    private void saveCurrentBuild() {
        String link = session.exportBuildLink();
        if (link == null) {
            session.setMessage("Nothing to save yet", true);
            return;
        }
        int hashIndex = link.indexOf('#');
        String hash = hashIndex >= 0 ? link.substring(hashIndex + 1) : link;
        String used = BuildLibrary.getInstance().save(saveName, hash);
        saveName = "";
        editingSaveName = false;
        session.setMessage("Saved as " + used, false);
    }

    /**
     * One damage source: its number, and the full working behind it when unfolded.
     *
     * <p>Collapsed by default because a build has dozens of sources and only the totals are read at
     * a glance; the breakdown matters when a number looks wrong.
     */
    private void appendSource(List<StatLine> lines, String spellName, DamageSources.Source part) {
        String key = spellName + "/" + part.name();
        boolean expanded = expandedSources.contains(key);
        boolean heal = "heal".equals(part.detail());
        String value = heal ? "+" + format(part.perHit()) + " hp" : format(part.perHit());

        lines.add(StatLine.expanderValue(
                "    " + (expanded ? "[-] " : "[+] ") + part.name(),
                value,
                "total".equals(part.detail()) ? color(CONTROL_WARNING)
                        : heal ? color(CONTROL_SUCCESS) : color(TEXT_SECONDARY),
                () -> {
                    if (!expandedSources.remove(key)) {
                        expandedSources.add(key);
                    }
                }));

        if (!expanded) {
            return;
        }
        if (part.result() == null) {
            // A total has no calculation of its own: what explains it is what it is built from.
            if (part.composition().isEmpty()) {
                lines.add(StatLine.text("      No breakdown available", "", color(TEXT_MUTED)));
                return;
            }
            for (var entry : part.composition().entrySet()) {
                String count = entry.getValue() == Math.floor(entry.getValue())
                        ? String.valueOf(entry.getValue().intValue())
                        : String.format(java.util.Locale.ROOT, "%.2f", entry.getValue());
                lines.add(StatLine.text("      " + entry.getKey(), "x " + count, color(TEXT_SECONDARY)));
            }
            return;
        }
        DamageCalc.Result result = part.result();
        lines.add(StatLine.text("      Share  (" + Math.round(result.totalConversion()) + "% total)",
                "", color(TEXT_MUTED)));
        // One row per element, in that element's colour, so the split reads at a glance.
        double[] shares = result.conversions();
        for (int i = 0; i < shares.length; i++) {
            if (Math.round(shares[i]) == 0) {
                continue;
            }
            lines.add(StatLine.text(
                    "      " + Identifications.elementLabel(Identifications.ELEMENT_PREFIXES.get(i)),
                    Math.round(shares[i]) + "%", elementColor(i)));
        }

        lines.add(StatLine.text("      Non-crit average", format(result.averageNormal()), color(TEXT_SECONDARY)));
        lines.add(StatLine.text("      Crit average", format(result.averageCrit()), color(TEXT_SECONDARY)));

        boolean anyRange = false;
        for (int i = 0; i < DamageCalc.ELEMENTS; i++) {
            if (result.perElementNormal()[i][1] > 0) {
                anyRange = true;
                break;
            }
        }
        if (anyRange) {
            lines.add(StatLine.text("      Damage range", "non-crit", color(TEXT_MUTED)));
            for (int i = 0; i < DamageCalc.ELEMENTS; i++) {
                double[] normal = result.perElementNormal()[i];
                if (normal[1] <= 0) {
                    continue;
                }
                lines.add(StatLine.text(
                        "      " + Identifications.elementLabel(Identifications.ELEMENT_PREFIXES.get(i)),
                        format(normal[0]) + " - " + format(normal[1]), elementColor(i)));
            }
        }
    }

    /** Wynncraft's element colours, which are game constants rather than theme choices. */
    private static Color elementColor(int element) {
        return switch (element) {
            case 1 -> new Color(0, 170, 0);
            case 2 -> new Color(255, 255, 85);
            case 3 -> new Color(85, 255, 255);
            case 4 -> new Color(255, 85, 85);
            case 5 -> new Color(255, 255, 255);
            default -> new Color(170, 170, 170);
        };
    }

    /** Damage figures are large; thousands separators make them readable at a glance. */
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%,.0f", value);
    }

    /** Major ID descriptions arrive with the site's inline markup. */
    private static String stripMarkup(String text) {
        return text.replaceAll("</?br>", " ").replaceAll("<[^>]*>", "").replace("&emsp;", " ").trim();
    }

    private static String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cleaned = text.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    /**
     * The per-item breakdown: a plain list of what is equipped, each entry folding open.
     *
     * <p>Entries are separated by a rule so a long expanded breakdown cannot be mistaken for part of
     * the next item.
     */
    private List<StatLine> buildItemLines() {
        List<StatLine> lines = new ArrayList<>();
        WynnDataSet data = session.data();
        WynnBuild build = session.build();

        boolean first = true;
        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            BuildEquipment equipment = build.equipment(slot);
            if (equipment.isEmpty()) {
                continue;
            }
            if (!first) {
                lines.add(StatLine.divider());
            }
            first = false;

            boolean expanded = expandedSlots.contains(slot);
            // ASCII markers: the UI font does not carry the triangle glyphs.
            String marker = expanded ? "[-] " : "[+] ";

            if (equipment instanceof BuildEquipment.Normal normal) {
                WynnItem item = data.item(normal.itemId());
                String name = item == null ? "Unknown item #" + normal.itemId() : item.displayName();
                lines.add(StatLine.expander(marker + slot.label() + "  ·  " + name,
                        item == null ? WynnItem.Tier.NORMAL : item.tier(), () -> toggleSlot(slot)));
                if (expanded && item != null) {
                    appendContribution(lines, slot);
                    appendDetails(lines, ItemDetails.forItem(item));
                }
            } else if (equipment instanceof BuildEquipment.Crafted crafted) {
                lines.add(StatLine.expander(marker + slot.label() + "  ·  Crafted item",
                        WynnItem.Tier.CRAFTED, () -> toggleSlot(slot)));
                if (expanded) {
                    lines.add(StatLine.button("Open in crafter", () -> openCraftInCrafter(crafted.craft())));
                    appendDetails(lines, ItemDetails.forCraft(crafted.craft(), data));
                }
            } else if (equipment instanceof BuildEquipment.Custom) {
                lines.add(StatLine.expander(marker + slot.label() + "  ·  Custom item",
                        WynnItem.Tier.NORMAL, () -> toggleSlot(slot)));
                if (expanded) {
                    lines.add(StatLine.text("Custom items are kept as-is and cannot be edited here", "",
                            color(TEXT_MUTED)));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(StatLine.spacer());
            lines.add(StatLine.text("No items equipped yet", "", color(TEXT_MUTED)));
        }
        lines.add(StatLine.spacer());
        return lines;
    }

    /**
     * What a slot is worth to this build, measured by removing it and comparing.
     *
     * <p>An item's own stat list does not tell you its value in context: skill point requirements,
     * set bonuses and the solver's equip order all shift when a piece leaves. Recomputing without it
     * is the only honest answer.
     */
    private void appendContribution(List<StatLine> lines, EquipmentSlot slot) {
        BuildStats current = session.stats();
        BuildStats without = session.statsWithout(slot);
        if (current == null || without == null) {
            return;
        }
        lines.add(StatLine.subheading("Contribution to this build"));
        appendDelta(lines, "Health", current.health() - without.health(), false);
        appendDelta(lines, "Effective health",
                current.effectiveHealth() - without.effectiveHealth(), false);
        appendDelta(lines, "Skill points needed",
                current.assignedTotal() - without.assignedTotal(), true);

        for (String key : List.of("sdPct", "mdPct", "sdRaw", "mdRaw", "mr", "ms", "ls", "spd")) {
            appendDelta(lines, Identifications.displayName(key),
                    current.identification(key) - without.identification(key),
                    Identifications.isInverted(key));
        }
    }

    private void appendDelta(List<StatLine> lines, String label, int delta, boolean lowerIsBetter) {
        if (delta == 0) {
            return;
        }
        lines.add(StatLine.text(label, WynnBuilderUi.formatStat(delta, false),
                WynnBuilderUi.statColor(delta, lowerIsBetter)));
    }

    private void appendDetails(List<StatLine> lines, List<ItemDetails.Line> details) {
        for (ItemDetails.Line detail : details) {
            lines.add(switch (detail.kind()) {
                case HEADING -> StatLine.subheading(detail.label());
                case PLAIN -> StatLine.text(detail.label(), detail.value(), color(TEXT_SECONDARY));
                case STAT -> StatLine.text(detail.label(), detail.value(),
                        WynnBuilderUi.statColor(detail.signedValue(), detail.inverted()));
            });
        }
    }

    private void toggleSlot(EquipmentSlot slot) {
        // Deliberately keeps the scroll position: folding a row should not move the page under the
        // pointer that just clicked it.
        if (!expandedSlots.remove(slot)) {
            expandedSlots.add(slot);
        }
    }

    /** Loads a crafted piece into the crafter and switches to it. */
    private void openCraftInCrafter(com.seqwawa.seq.wynnbuilder.data.CraftedItem craft) {
        session.setCraft(craft);
        session.setMessage("Opened this craft in the crafter", false);
        SeqClient.mc.setScreen(new CrafterScreen(this));
    }


    // ------------------------------------------------------------------ actions

    private void copyLink() {
        String link = session.exportBuildLink();
        if (link == null) {
            session.setMessage("Nothing to copy yet", true);
            return;
        }
        SeqClient.mc.keyboardHandler.setClipboard(link);
        session.setMessage("Link copied to clipboard", false);
    }

    private void pasteLink() {
        String clipboard = SeqClient.mc.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            session.setMessage("Clipboard is empty", true);
            return;
        }
        session.importBuildLink(clipboard);
    }

    private void openAbilityTree() {
        if (session.playerClass() == null) {
            session.setMessage("Equip a weapon to pick a class ability tree", true);
            return;
        }
        SeqClient.mc.setScreen(new AbilityTreeScreen(this));
    }

    private void openPickerFor(EquipmentSlot slot) {
        WynnDataSet data = session.data();
        List<ItemPickerOverlay.Entry> entries = new ArrayList<>();
        for (WynnItem item : data.itemsForSlot(slot)) {
            entries.add(ItemPickerOverlay.Entry.of(item));
        }
        picker.open("Choose " + slot.label(), entries,
                entry -> {
                    session.build().setEquipment(slot, new BuildEquipment.Normal(entry.id()));
                    session.invalidate();
                },
                () -> {
                    session.build().setEquipment(slot, BuildEquipment.none());
                    session.build().powders(slot).clear();
                    session.invalidate();
                });
    }

    /**
     * Opens the powder editor for a slot.
     *
     * <p>Powders are ordered and capped by the item, so the editor shows what is applied and offers
     * one chip per element and tier rather than cycling blindly.
     */
    private void openPowderPicker(EquipmentSlot slot) {
        powderSlot = powderSlot == slot ? null : slot;
    }

    private void cyclePowder(EquipmentSlot slot, boolean remove) {
        WynnBuild build = session.build();
        List<Powder> powders = build.powders(slot);
        WynnItem item = session.stats() == null ? null : session.stats().resolvedItems().get(slot);
        int capacity = item == null ? 5 : item.powderSlots();

        if (remove) {
            if (!powders.isEmpty()) {
                powders.remove(powders.size() - 1);
                session.invalidate();
            }
            return;
        }
        if (powders.size() >= capacity) {
            session.setMessage(capacity == 0 ? "This item has no powder slots" : "All powder slots are full", true);
            return;
        }
        // Cycle the element of the last powder, or start a new one at the highest usable tier.
        int tier = item == null ? PowderCalcTier.DEFAULT : Math.max(1, PowderCalcTier.forLevel(item.level()));
        Powder.PowderElement element = Powder.PowderElement.EARTH;
        if (!powders.isEmpty()) {
            Powder last = powders.get(powders.size() - 1);
            element = Powder.PowderElement.byIndex(last.elementIndex() + 1);
            tier = last.tier();
        }
        powders.add(new Powder(element, tier));
        session.invalidate();
    }

    /** Small indirection so the powder tier rule stays next to its explanation. */
    private static final class PowderCalcTier {
        private static final int DEFAULT = 6;

        private PowderCalcTier() {}

        static int forLevel(int itemLevel) {
            return com.seqwawa.seq.wynnbuilder.calc.PowderCalc.maxTierForLevel(itemLevel);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        float pointerX = MinecraftUiRenderer.mouseX(click.x());
        float pointerY = MinecraftUiRenderer.mouseY(click.y());

        if (picker.isOpen()) {
            picker.mouseClicked(pointerX, pointerY);
            return true;
        }
        if (click.button() != 0 && click.button() != 1) {
            return super.mouseClicked(click, outsideScreen);
        }

        if (click.button() == 0) {
            for (Action action : actions) {
                if (action.bounds().contains(pointerX, pointerY)) {
                    action.onClick().run();
                    return true;
                }
            }
            if (levelDown != null && levelDown.contains(pointerX, pointerY)) {
                session.build().setLevel(Math.max(1, session.build().level() - 1));
                session.invalidate();
                return true;
            }
            if (levelUp != null && levelUp.contains(pointerX, pointerY)) {
                session.build().setLevel(Math.min(session.data().encodingConsts().maxLevel(),
                        session.build().level() + 1));
                session.invalidate();
                return true;
            }
            // Slider tracks are tested after the buttons, since the minus and plus sit on the same
            // row and should win a click that lands on both. Grabbing the track sets the value
            // straight away, so a click anywhere along it jumps there rather than needing a drag.
            for (Track track : tracks) {
                if (track.bounds().contains(pointerX, pointerY)) {
                    draggedTrack = track;
                    track.applyAt(pointerX);
                    return true;
                }
            }
        }

        // Powder controls take priority over the slot they sit on.
        for (Map.Entry<EquipmentSlot, Rect> entry : powderHitboxes.entrySet()) {
            if (entry.getValue().contains(pointerX, pointerY)) {
                if (click.button() == 1) {
                    cyclePowder(entry.getKey(), true);
                } else {
                    openPowderPicker(entry.getKey());
                }
                return true;
            }
        }
        if (click.button() == 0) {
            for (Map.Entry<EquipmentSlot, Rect> entry : slotHitboxes.entrySet()) {
                if (entry.getValue().contains(pointerX, pointerY)) {
                    openPickerFor(entry.getKey());
                    return true;
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent click, double dragX, double dragY) {
        if (draggedTrack != null) {
            // Tracked by the grabbed slider rather than by what is under the pointer, so the value
            // keeps following the mouse once it leaves the row.
            draggedTrack.applyAt(MinecraftUiRenderer.mouseX(click.x()));
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        draggedTrack = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double pointerX, double pointerY, double horizontalAmount, double verticalAmount) {
        if (picker.isOpen()) {
            picker.mouseScrolled(verticalAmount);
            return true;
        }
        statsScroll = WynnBuilderUi.clamp(statsScroll - (float) verticalAmount * SCROLL_STEP, 0, maxStatsScroll);
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        boolean shortcut = (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (editingSaveName && !picker.isOpen()) {
            if (keyEvent.key() == GLFW.GLFW_KEY_BACKSPACE && !saveName.isEmpty()) {
                saveName = saveName.substring(0, saveName.length() - 1);
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingSaveName = false;
                return true;
            }
        }
        if (picker.isOpen()) {
            if (shortcut && keyEvent.key() == GLFW.GLFW_KEY_V) {
                picker.charTyped(SeqClient.mc.keyboardHandler.getClipboard());
                return true;
            }
            picker.keyPressed(keyEvent.key());
            return true;
        }
        if (shortcut && keyEvent.key() == GLFW.GLFW_KEY_V) {
            pasteLink();
            return true;
        }
        if (shortcut && keyEvent.key() == GLFW.GLFW_KEY_C) {
            copyLink();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (editingSaveName && !picker.isOpen()) {
            String typed = TextInputHelper.getTypedText(characterEvent);
            if (typed != null && saveName.length() + typed.length() <= 40) {
                saveName += typed;
            }
            return true;
        }
        if (picker.isOpen()) {
            String typed = TextInputHelper.getTypedText(characterEvent);
            if (typed != null) {
                picker.charTyped(typed);
            }
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public void onClose() {
        if (picker.isOpen()) {
            picker.close();
            return;
        }
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
