package com.seqwawa.seq.scroll;

import static com.seqwawa.seq.halcyon.HalcyonRingRenderer.renderRingWall;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

public final class CraftedScrollRangeVisualiserClient {
    private static final double RANGE = 9.5;
    private static final int DEFAULT_COLOR_RGB = 0x00FFFF;
    private static final int RING_ALPHA = 230;
    private static final String SCROLL_GLYPH = "\uE032";
    private static final FontDescription SCROLL_FONT =
            new FontDescription.Resource(Identifier.withDefaultNamespace("tooltip/emblem/sprite"));
    private static boolean holdingScroll;
    private static boolean colorPreviewActive;

    private CraftedScrollRangeVisualiserClient() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(CraftedScrollRangeVisualiserClient::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(CraftedScrollRangeVisualiserClient::render);
    }

    public static void setColorPreviewActive(boolean active) {
        colorPreviewActive = active;
    }

    private static void tick(Minecraft client) {
        holdingScroll = WynncraftServerPolicy.isCurrentServerAllowed()
                && client.player != null
                && isCraftedScroll(client.player.getMainHandItem());
    }

    static boolean isCraftedScroll(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;

        for (Component line : lore.lines()) {
            for (Component part : line.toFlatList()) {
                if (SCROLL_FONT.equals(part.getStyle().getFont()) && part.getString().contains(SCROLL_GLYPH)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!WynncraftServerPolicy.isCurrentServerAllowed()
                || (!holdingScroll && !colorPreviewActive)
                || client.player == null
                || client.level == null) {
            return;
        }

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 center = client.player.getPosition(tickDelta);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());
        renderRingWall(vertices, pose, center, camera, RANGE, configuredColor(), RING_ALPHA);
    }

    static int configuredColor() {
        return SeqClient.getCraftedScrollRangeColorSetting() == null
                ? DEFAULT_COLOR_RGB
                : SeqClient.getCraftedScrollRangeColorSetting().getValue();
    }
}
