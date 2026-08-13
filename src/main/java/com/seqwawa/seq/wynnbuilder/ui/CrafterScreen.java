package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_DANGER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_SUCCESS;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_WARNING;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.wynnbuilder.WynnBuilderSession;
import com.seqwawa.seq.wynnbuilder.calc.CraftCalc;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnIngredient;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.awt.Color;
import com.seqwawa.seq.wynnbuilder.data.WynnRecipe;
import java.util.ArrayList;
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
 * The crafting calculator: a recipe, two material tiers and a two-by-three ingredient grid, with the
 * resulting item shown alongside.
 */
public final class CrafterScreen extends Screen {
    private static final float ACTION_HEIGHT = 22;
    private static final float CELL_HEIGHT = 30;
    private static final float CELL_GAP = 4;
    private static final float SCROLL_STEP = 30;

    private final Screen parent;
    private final WynnBuilderSession session = WynnBuilderSession.getInstance();
    private final ItemPickerOverlay picker = new ItemPickerOverlay();
    private final List<Action> actions = new ArrayList<>();
    private final List<Rect> ingredientCells = new ArrayList<>();

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
    private float resultScroll;
    private float maxResultScroll;

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

    public CrafterScreen(Screen parent) {
        super(Component.literal("WynnBuilder Crafter"));
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
        ingredientCells.clear();

        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 205));
        canvas.fillRect(0, 0, width, WynnBuilderUi.HEADER_HEIGHT, color(BACKGROUND_HEADER, 245));
        WynnBuilderUi.drawLeft(canvas, "Crafter", WynnBuilderUi.OUTER_MARGIN,
                WynnBuilderUi.HEADER_HEIGHT / 2f, 20, color(ACCENT_PRIMARY));

        if (!session.isReady()) {
            WynnBuilderUi.drawCentered(canvas, session.status(), width / 2f, height / 2f, 13, color(TEXT_MUTED));
            return;
        }

        float x = width - WynnBuilderUi.OUTER_MARGIN - 66;
        addAction(canvas, x, 9, 66, ACTION_HEIGHT, "Paste link", this::pasteLink);
        x -= 74;
        addAction(canvas, x, 9, 70, ACTION_HEIGHT, "Copy link", this::copyLink);

        if (!session.message().isEmpty()) {
            WynnBuilderUi.drawRight(canvas, session.message(), width - WynnBuilderUi.OUTER_MARGIN,
                    WynnBuilderUi.HEADER_HEIGHT - 6, 10,
                    session.messageIsError() ? color(CONTROL_DANGER) : color(CONTROL_SUCCESS));
        }

        float top = WynnBuilderUi.HEADER_HEIGHT + WynnBuilderUi.OUTER_MARGIN;
        float panelHeight = Math.max(120, height - top - WynnBuilderUi.OUTER_MARGIN);
        float leftWidth = WynnBuilderUi.clamp(width * 0.44f, 260, 380);
        float leftX = WynnBuilderUi.OUTER_MARGIN;
        float rightX = leftX + leftWidth + 10;
        float rightWidth = Math.max(150, width - rightX - WynnBuilderUi.OUTER_MARGIN);

        // The panels read the pointer from the fields, so parking it off screen for the duration
        // suppresses every hover highlight behind the modal without touching each call site.
        float pointerX = mouseX;
        float pointerY = mouseY;
        if (modal) {
            mouseX = Float.NEGATIVE_INFINITY;
            mouseY = Float.NEGATIVE_INFINITY;
        }
        drawRecipePanel(canvas, leftX, top, leftWidth, panelHeight);
        drawResultPanel(canvas, rightX, top, rightWidth, panelHeight);
        mouseX = pointerX;
        mouseY = pointerY;

        picker.draw(canvas, mouseX, mouseY);
    }

    private void addAction(UiCanvas canvas, float x, float y, float width, float height, String label, Runnable onClick) {
        WynnBuilderUi.drawButton(canvas, x, y, width, height, label, mouseX, mouseY);
        register(new Rect(x, y, width, height), onClick);
    }

    private void drawRecipePanel(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, WynnBuilderUi.PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        WynnDataSet data = session.data();
        CraftedItem craft = session.craft();
        WynnRecipe recipe = data.recipe(craft.recipeId());

        float cursorY = y + 10;

        // Recipe selector
        boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, x + 10, cursorY, width - 20, 24);
        canvas.fillRoundedRect(x + 10, cursorY, width - 20, 24, 5,
                hovered ? color(CONTROL_INPUT_HOVER, 235) : color(CONTROL_INPUT, 210));
        WynnBuilderUi.drawLeft(canvas, "Recipe", x + 18, cursorY + 12, 9, color(TEXT_MUTED));
        WynnBuilderUi.drawRight(canvas,
                recipe == null ? "Select a recipe" : recipe.displayLabel() + "  ·  " + recipe.profession(),
                x + width - 18, cursorY + 12, 11, color(TEXT_PRIMARY));
        register(new Rect(x + 10, cursorY, width - 20, 24), this::openRecipePicker);
        cursorY += 30;

        // Material tiers
        drawMaterialTier(canvas, x + 10, cursorY, (width - 24) / 2f, "Material 1", craft.materialTier1(), true);
        drawMaterialTier(canvas, x + 14 + (width - 24) / 2f, cursorY, (width - 24) / 2f, "Material 2",
                craft.materialTier2(), false);
        cursorY += 28;

        // Attack speed, only meaningful for weapon recipes.
        if (recipe != null && recipe.isWeapon()) {
            boolean speedHovered = WynnBuilderUi.contains(mouseX, mouseY, x + 10, cursorY, width - 20, 22);
            canvas.fillRoundedRect(x + 10, cursorY, width - 20, 22, 5,
                    speedHovered ? color(CONTROL_INPUT_HOVER, 235) : color(CONTROL_INPUT, 210));
            WynnBuilderUi.drawLeft(canvas, "Attack speed", x + 18, cursorY + 11, 9, color(TEXT_MUTED));
            WynnBuilderUi.drawRight(canvas, craft.attackSpeed().label(), x + width - 18, cursorY + 11, 11,
                    color(TEXT_PRIMARY));
            register(new Rect(x + 10, cursorY, width - 20, 22), this::cycleAttackSpeed);
            cursorY += 28;
        }

        WynnBuilderUi.drawLeft(canvas, "Ingredients", x + 12, cursorY + 6, 11, color(ACCENT_PRIMARY));
        cursorY += 18;

        // The two-by-three grid, in the same order the encoding uses.
        CraftCalc.Result result = CraftCalc.compute(craft, data);
        float cellWidth = (width - 24) / 2f;
        for (int index = 0; index < CraftedItem.INGREDIENT_SLOTS; index++) {
            int row = index / 2;
            int column = index % 2;
            float cellX = x + 10 + column * (cellWidth + 4);
            float cellY = cursorY + row * (CELL_HEIGHT + CELL_GAP);
            Rect cell = new Rect(cellX, cellY, cellWidth, CELL_HEIGHT);
            if (!modal) {
                ingredientCells.add(cell);
            }

            boolean cellHovered = cell.contains(mouseX, mouseY);
            canvas.fillRoundedRect(cellX, cellY, cellWidth, CELL_HEIGHT, 5,
                    cellHovered ? color(CONTROL_INPUT_HOVER, 220) : color(CONTROL_INPUT, 200));

            int ingredientId = craft.ingredientIds().get(index);
            WynnIngredient ingredient = ingredientId == CraftedItem.NO_INGREDIENT ? null : data.ingredient(ingredientId);
            String label = ingredient == null ? "Empty" : ingredient.displayName();
            WynnBuilderUi.drawLeft(canvas, WynnBuilderUi.ellipsize(label, cellWidth - 44, 10),
                    cellX + 7, cellY + 12, 10,
                    ingredient == null ? color(TEXT_MUTED) : color(TEXT_PRIMARY));

            // Effectiveness is what makes placement matter, so it is always visible.
            int effectiveness = result.effectiveness()[index];
            Color effectivenessColor = effectiveness > 100
                    ? color(CONTROL_SUCCESS)
                    : effectiveness < 100 ? color(CONTROL_DANGER) : color(TEXT_MUTED);
            WynnBuilderUi.drawRight(canvas, effectiveness + "%", cellX + cellWidth - 7, cellY + 12, 9, effectivenessColor);
            if (ingredient != null) {
                WynnBuilderUi.drawLeft(canvas, "Tier " + ingredient.tier() + "  ·  Lv. " + ingredient.level(),
                        cellX + 7, cellY + 23, 8, color(TEXT_MUTED));
            }
        }
    }

    private void drawMaterialTier(UiCanvas canvas, float x, float y, float width, String label, int tier, boolean first) {
        boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, x, y, width, 22);
        canvas.fillRoundedRect(x, y, width, 22, 5, hovered ? color(CONTROL_INPUT_HOVER, 235) : color(CONTROL_INPUT, 210));
        WynnBuilderUi.drawLeft(canvas, label, x + 8, y + 11, 9, color(TEXT_MUTED));
        // Spelled out rather than drawn with a star glyph: the UI font has no U+272B, so the
        // tier rendered as an empty box and the control looked blank.
        WynnBuilderUi.drawRight(canvas, "Tier " + tier, x + width - 8, y + 11, 11, color(CONTROL_WARNING));
        register(new Rect(x, y, width, 22), () -> cycleMaterialTier(first));
    }

    private void drawResultPanel(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, WynnBuilderUi.PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        WynnBuilderUi.drawLeft(canvas, "Result", x + 12, y + 16, 13, color(ACCENT_PRIMARY));

        CraftCalc.Result result = CraftCalc.compute(session.craft(), session.data());
        List<StatLine> lines = resultLines(result);

        float contentTop = y + 28;
        float contentHeight = Math.max(0, height - (contentTop - y) - 8);
        float total = StatLineRenderer.contentHeight(lines, width);
        maxResultScroll = Math.max(0, total - contentHeight);
        resultScroll = WynnBuilderUi.clamp(resultScroll, 0, maxResultScroll);

        StatLineRenderer.draw(canvas, lines, x, contentTop, width, contentHeight, resultScroll, mouseX, mouseY,
                (rectX, rectY, rectWidth, rectHeight, onClick) ->
                        register(new Rect(rectX, rectY, rectWidth, rectHeight), onClick));
        StatLineRenderer.drawScrollbar(canvas, x, contentTop, width, contentHeight, resultScroll, total);
    }

    private List<StatLine> resultLines(CraftCalc.Result result) {
        List<StatLine> lines = new ArrayList<>();

        lines.add(StatLine.heading("Crafted " + (result.type().isEmpty() ? "item" : result.type())));
        lines.add(StatLine.text("Level", result.levelMin() + "-" + result.levelMax(), color(TEXT_PRIMARY)));

        if (result.isWeapon()) {
            lines.add(StatLine.text("Neutral damage",
                    result.neutralDamage()[0] + "-" + result.neutralDamage()[1], color(TEXT_PRIMARY)));
        } else if (result.isArmour()) {
            lines.add(StatLine.text("Health",
                    result.healthOrDamage()[0] + "-" + result.healthOrDamage()[1], color(TEXT_PRIMARY)));
        }
        if (result.isConsumable()) {
            lines.add(StatLine.text("Duration", result.duration()[0] + "-" + result.duration()[1] + "s",
                    color(TEXT_PRIMARY)));
            lines.add(StatLine.text("Charges", String.valueOf(result.charges()), color(TEXT_PRIMARY)));
        } else {
            lines.add(StatLine.text("Durability",
                    result.durability()[0] + "-" + result.durability()[1], color(TEXT_PRIMARY)));
            lines.add(StatLine.text("Powder slots", String.valueOf(result.powderSlots()), color(TEXT_PRIMARY)));
        }

        boolean anyRequirement = result.requirements().values().stream().anyMatch(value -> value != 0);
        if (anyRequirement) {
            lines.add(StatLine.heading("Requirements"));
            for (int i = 0; i < Identifications.REQUIREMENT_KEYS.size(); i++) {
                int value = result.requirements().getOrDefault(Identifications.REQUIREMENT_KEYS.get(i), 0);
                if (value != 0) {
                    lines.add(StatLine.text(Identifications.SKILL_POINT_ORDER_NAMES.get(i),
                            String.valueOf(value), color(TEXT_SECONDARY)));
                }
            }
        }

        appendIdentifications(lines, result);

        if (!result.warnings().isEmpty()) {
            lines.add(StatLine.heading("Warnings"));
            for (String warning : result.warnings()) {
                lines.add(StatLine.text(warning, "", color(CONTROL_DANGER)));
            }
        }
        lines.add(StatLine.spacer());
        return lines;
    }

    /** The resulting identifications, split into the same labelled blocks the builder uses. */
    private void appendIdentifications(List<StatLine> lines, CraftCalc.Result result) {
        Map<Identifications.Group, List<Map.Entry<String, int[]>>> grouped =
                new java.util.EnumMap<>(Identifications.Group.class);
        for (Map.Entry<String, int[]> entry : result.identificationRanges().entrySet()) {
            if (!Identifications.isDisplayable(entry.getKey())) {
                continue;
            }
            grouped.computeIfAbsent(Identifications.group(entry.getKey()), ignored -> new ArrayList<>()).add(entry);
        }
        if (grouped.isEmpty()) {
            return;
        }
        lines.add(StatLine.heading("Identifications"));

        boolean first = true;
        for (Identifications.Group group : Identifications.Group.values()) {
            List<Map.Entry<String, int[]>> entries = grouped.get(group);
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
                    .forEach(entry -> {
                        int[] range = entry.getValue();
                        boolean percentage = Identifications.isPercentage(entry.getKey());
                        String suffix = percentage ? "%" : "";
                        String text = range[0] == range[1]
                                ? WynnBuilderUi.formatStat(range[0], percentage)
                                : WynnBuilderUi.formatStat(range[0], false) + " to "
                                        + WynnBuilderUi.formatStat(range[1], false) + suffix;
                        lines.add(StatLine.text(Identifications.displayName(entry.getKey()), text,
                                WynnBuilderUi.statColor(range[1], Identifications.isInverted(entry.getKey()))));
                    });
        }
    }

    // ------------------------------------------------------------------ actions

    private void openRecipePicker() {
        List<ItemPickerOverlay.Entry> entries = new ArrayList<>();
        for (WynnRecipe recipe : session.data().recipes()) {
            entries.add(new ItemPickerOverlay.Entry(
                    recipe.id(),
                    recipe.displayLabel(),
                    recipe.profession(),
                    WynnItem.Tier.CRAFTED,
                    recipe.minLevel()));
        }
        picker.open("Choose recipe", entries,
                entry -> {
                    session.setCraft(session.craft().withRecipe(entry.id()));
                    session.setMessage("", false);
                },
                null);
    }

    private void openIngredientPicker(int slot) {
        List<ItemPickerOverlay.Entry> entries = new ArrayList<>();
        for (WynnIngredient ingredient : session.data().ingredients()) {
            entries.add(new ItemPickerOverlay.Entry(
                    ingredient.id(),
                    ingredient.displayName(),
                    "Tier " + ingredient.tier() + " · Lv. " + ingredient.level(),
                    WynnItem.Tier.NORMAL,
                    ingredient.level()));
        }
        picker.open("Choose ingredient " + (slot + 1), entries,
                entry -> session.setCraft(session.craft().withIngredient(slot, entry.id())),
                () -> session.setCraft(session.craft().withIngredient(slot, CraftedItem.NO_INGREDIENT)));
    }

    private void cycleMaterialTier(boolean first) {
        CraftedItem craft = session.craft();
        if (first) {
            session.setCraft(craft.withMaterialTiers(craft.materialTier1() % 3 + 1, craft.materialTier2()));
        } else {
            session.setCraft(craft.withMaterialTiers(craft.materialTier1(), craft.materialTier2() % 3 + 1));
        }
    }

    private void cycleAttackSpeed() {
        CraftedItem craft = session.craft();
        CraftedItem.AttackSpeed[] values = CraftedItem.AttackSpeed.values();
        session.setCraft(craft.withAttackSpeed(values[(craft.attackSpeed().ordinal() + 1) % values.length]));
    }

    private void copyLink() {
        String link = session.exportCraftLink();
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
        session.importCraftLink(clipboard);
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
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        for (Action action : actions) {
            if (action.bounds().contains(pointerX, pointerY)) {
                action.onClick().run();
                return true;
            }
        }
        for (int index = 0; index < ingredientCells.size(); index++) {
            if (ingredientCells.get(index).contains(pointerX, pointerY)) {
                openIngredientPicker(index);
                return true;
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseScrolled(double pointerX, double pointerY, double horizontalAmount, double verticalAmount) {
        if (picker.isOpen()) {
            picker.mouseScrolled(verticalAmount);
            return true;
        }
        resultScroll = WynnBuilderUi.clamp(resultScroll - (float) verticalAmount * SCROLL_STEP, 0, maxResultScroll);
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        boolean shortcut = (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
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
