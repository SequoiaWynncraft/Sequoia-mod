package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_POPUP;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_DANGER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_SUCCESS;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_WARNING;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.wynnbuilder.WynnBuilderSession;
import com.seqwawa.seq.wynnbuilder.atree.AbilityNode;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTree;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * The ability tree: a scrollable grid of nodes laid out on the same nine-column grid the game uses,
 * with connections drawn between parents and children.
 */
public final class AbilityTreeScreen extends Screen {
    private static final int GRID_COLUMNS = 9;
    private static final float CELL_SIZE = 26;
    private static final float NODE_RADIUS = 9;
    private static final float SCROLL_STEP = 30;

    private final Screen parent;
    private final WynnBuilderSession session = WynnBuilderSession.getInstance();
    private final List<NodeHit> nodeHits = new ArrayList<>();

    private float mouseX;
    private float mouseY;
    private float scroll;
    private float maxScroll;
    private AbilityNode hovered;

    private record NodeHit(AbilityNode node, float x, float y) {}

    public AbilityTreeScreen(Screen parent) {
        super(Component.literal("Ability Tree"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pointerX, int pointerY, float partialTick) {
        super.render(guiGraphics, pointerX, pointerY, partialTick);
        mouseX = MinecraftUiRenderer.mouseX(pointerX);
        mouseY = MinecraftUiRenderer.mouseY(pointerY);
        UiRenderer.renderScreen(this, this::draw);
    }

    private void draw(UiCanvas canvas) {
        nodeHits.clear();
        hovered = null;

        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY, 210));
        canvas.fillRect(0, 0, width, WynnBuilderUi.HEADER_HEIGHT, color(BACKGROUND_HEADER, 245));
        WynnBuilderUi.drawLeft(canvas, "Ability Tree", WynnBuilderUi.OUTER_MARGIN,
                WynnBuilderUi.HEADER_HEIGHT / 2f, 20, color(ACCENT_PRIMARY));

        AbilityTreeState state = session.abilityTreeState();
        if (state == null) {
            WynnBuilderUi.drawCentered(canvas,
                    session.playerClass() == null
                            ? "Equip a weapon to choose a class ability tree"
                            : "Ability tree data is unavailable",
                    width / 2f, height / 2f, 13, color(TEXT_MUTED));
            return;
        }

        AbilityTree tree = state.tree();

        // Header summary: points and archetype progress.
        String points = state.remainingPoints() + " / " + state.abilityPoints() + " points";
        WynnBuilderUi.drawRight(canvas, points, width - WynnBuilderUi.OUTER_MARGIN,
                WynnBuilderUi.HEADER_HEIGHT / 2f, 12,
                state.remainingPoints() < 0 ? color(CONTROL_DANGER) : color(CONTROL_SUCCESS));

        StringBuilder archetypes = new StringBuilder();
        for (Map.Entry<String, Integer> entry : state.archetypeCounts().entrySet()) {
            if (archetypes.length() > 0) {
                archetypes.append("   ");
            }
            archetypes.append(entry.getKey()).append(' ').append(entry.getValue());
        }
        WynnBuilderUi.drawCentered(canvas, archetypes.toString(), width / 2f,
                WynnBuilderUi.HEADER_HEIGHT / 2f, 11, color(TEXT_SECONDARY));

        float top = WynnBuilderUi.HEADER_HEIGHT + 6;
        float viewHeight = Math.max(60, height - top - 30);
        float gridWidth = GRID_COLUMNS * CELL_SIZE;
        float gridLeft = (width - gridWidth) / 2f;

        int maxRow = 0;
        for (AbilityNode node : tree.nodes()) {
            maxRow = Math.max(maxRow, node.row());
        }
        maxScroll = Math.max(0, (maxRow + 2) * CELL_SIZE - viewHeight);
        scroll = WynnBuilderUi.clamp(scroll, 0, maxScroll);

        canvas.scissor(0, top, width, viewHeight);
        try {
            // Connections first so nodes draw on top of them.
            for (AbilityNode node : tree.nodes()) {
                float childX = gridLeft + node.column() * CELL_SIZE + CELL_SIZE / 2f;
                float childY = top + node.row() * CELL_SIZE + CELL_SIZE / 2f - scroll;
                for (int parentId : node.parentIds()) {
                    AbilityNode parentNode = tree.node(parentId);
                    if (parentNode == null) {
                        continue;
                    }
                    float parentX = gridLeft + parentNode.column() * CELL_SIZE + CELL_SIZE / 2f;
                    float parentY = top + parentNode.row() * CELL_SIZE + CELL_SIZE / 2f - scroll;
                    boolean live = state.isActive(node.id()) && state.isActive(parentId);
                    canvas.strokeLine(parentX, parentY, childX, childY, live ? 2f : 1f,
                            live ? color(ACCENT_PRIMARY) : color(ACCENT_DIVIDER, 160));
                }
            }

            for (AbilityNode node : tree.nodes()) {
                float centreX = gridLeft + node.column() * CELL_SIZE + CELL_SIZE / 2f;
                float centreY = top + node.row() * CELL_SIZE + CELL_SIZE / 2f - scroll;
                if (centreY < top - CELL_SIZE || centreY > top + viewHeight + CELL_SIZE) {
                    continue;
                }
                nodeHits.add(new NodeHit(node, centreX, centreY));

                boolean active = state.isActive(node.id());
                boolean available = !active && state.canActivate(node.id());
                boolean isHovered = WynnBuilderUi.contains(
                        mouseX, mouseY, centreX - NODE_RADIUS, centreY - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
                if (isHovered) {
                    hovered = node;
                }

                Color fill = active
                        ? color(ACCENT_PRIMARY)
                        : available ? color(CONTROL_INPUT, 235) : color(CONTROL_INPUT, 120);
                canvas.fillCircle(centreX, centreY, NODE_RADIUS, fill);
                canvas.strokeCircle(centreX, centreY, NODE_RADIUS, isHovered ? 2f : 1f,
                        isHovered ? color(TEXT_PRIMARY) : color(ACCENT_DIVIDER));
                WynnBuilderUi.drawCentered(canvas, String.valueOf(node.cost()), centreX, centreY, 9,
                        active ? color(TEXT_PRIMARY) : color(TEXT_MUTED));
            }
        } finally {
            canvas.resetScissor();
        }

        if (hovered != null) {
            drawTooltip(canvas, hovered, state, width, height);
        }

        WynnBuilderUi.drawCentered(canvas, "Click a node to toggle it · scroll to move · Esc to go back",
                width / 2f, height - 14, 10, color(TEXT_MUTED));
    }

    private void drawTooltip(UiCanvas canvas, AbilityNode node, AbilityTreeState state, float width, float height) {
        List<String> lines = new ArrayList<>();
        lines.add(node.displayName());
        if (node.archetype() != null) {
            lines.add(node.archetype() + (node.archetypeRequirement() > 0
                    ? " · requires " + node.archetypeRequirement()
                    : ""));
        }
        lines.add("Cost: " + node.cost());
        String blocked = state.isActive(node.id()) ? null : state.blockedReason(node.id());
        if (blocked != null) {
            lines.add(blocked);
        }
        for (String line : wrapDescription(node.description())) {
            lines.add(line);
        }

        float tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, WynnBuilderUi.measure(line, 10));
        }
        tooltipWidth += 16;
        float tooltipHeight = lines.size() * 13 + 10;
        float tooltipX = Math.min(mouseX + 12, width - tooltipWidth - 6);
        float tooltipY = Math.min(mouseY + 12, height - tooltipHeight - 6);

        canvas.fillRoundedRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 5, color(BACKGROUND_POPUP, 250));
        canvas.strokeRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 1, color(ACCENT_DIVIDER));

        float lineY = tooltipY + 12;
        for (int i = 0; i < lines.size(); i++) {
            Color lineColor = i == 0
                    ? color(ACCENT_PRIMARY)
                    : blocked != null && lines.get(i).equals(blocked) ? color(CONTROL_WARNING) : color(TEXT_SECONDARY);
            WynnBuilderUi.drawLeft(canvas, lines.get(i), tooltipX + 8, lineY, 10, lineColor);
            lineY += 13;
        }
    }

    /** Turns the HTML-flavoured ability description into plain wrapped lines. */
    private static List<String> wrapDescription(String description) {
        List<String> lines = new ArrayList<>();
        if (description == null || description.isEmpty()) {
            return lines;
        }
        String plain = description
                .replaceAll("</br>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&emsp;", "  ")
                .replace("&nbsp;", " ");
        for (String rawLine : plain.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // Keep the tooltip narrow enough to stay on screen.
            while (WynnBuilderUi.measure(line, 10) > 260) {
                int cut = line.length();
                while (cut > 0 && WynnBuilderUi.measure(line.substring(0, cut), 10) > 260) {
                    cut -= 4;
                }
                int space = line.lastIndexOf(' ', Math.max(0, cut));
                if (space <= 0) {
                    break;
                }
                lines.add(line.substring(0, space));
                line = line.substring(space + 1);
            }
            lines.add(line);
            if (lines.size() > 12) {
                break;
            }
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0) {
            float pointerX = MinecraftUiRenderer.mouseX(click.x());
            float pointerY = MinecraftUiRenderer.mouseY(click.y());
            AbilityTreeState state = session.abilityTreeState();
            if (state != null) {
                for (NodeHit hit : nodeHits) {
                    if (WynnBuilderUi.contains(pointerX, pointerY, hit.x() - NODE_RADIUS, hit.y() - NODE_RADIUS,
                            NODE_RADIUS * 2, NODE_RADIUS * 2)) {
                        if (state.toggle(hit.node().id())) {
                            // Write the new selection straight back so the link stays in sync.
                            session.syncAbilityTreeToBuild();
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseScrolled(double pointerX, double pointerY, double horizontalAmount, double verticalAmount) {
        scroll = WynnBuilderUi.clamp(scroll - (float) verticalAmount * SCROLL_STEP, 0, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
