package com.seqwawa.seq.courage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.courage.CourageRangeTracker.CourageCircle;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.WynnWeaponClassIndex;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;

/** Draws the Courage aura around Shamans and counts the players standing inside it. */
public final class CourageRangeClient {
    public static final int DEFAULT_COLOR_RGB = 0xFF0000;
    /** Half opacity, so the fill reads as an overlay rather than repainting the ground. */
    private static final int AURA_ALPHA = 128;

    private static boolean colorPreviewActive;

    private CourageRangeClient() {}

    public static void initialize() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
                .registerReloader(
                        Identifier.fromNamespaceAndPath("seq", "courage_weapon_model_scanner"),
                        new ResourceManagerReloadListener() {
                            @Override
                            public void onResourceManagerReload(ResourceManager manager) {
                                WynnWeaponClassIndex.reset();
                                CourageRangeTracker.reset();
                            }
                        });

        // A character switch always crosses a world boundary, so no observed class outlives one.
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            CourageRangeScope.reset();
            CourageRangeTracker.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(CourageRangeClient::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(CourageRangeClient::render);
    }

    public static void setColorPreviewActive(boolean active) {
        colorPreviewActive = active;
    }

    /** Courage only matters inside a raid, so nothing shows anywhere else. */
    public static boolean isActive() {
        return isEnabled() && CourageRangeScope.inRaid();
    }

    static int ringColor() {
        return SeqClient.getCourageRangeColorSetting() == null
                ? DEFAULT_COLOR_RGB
                : SeqClient.getCourageRangeColorSetting().getValue();
    }

    private static void tick(Minecraft client) {
        if (!isEnabled()) {
            CourageRangeScope.reset();
            CourageRangeTracker.reset();
            return;
        }

        CourageRangeScope.tick(client);
        if (isActive()) {
            CourageRangeTracker.tick(client);
        } else {
            CourageRangeTracker.reset();
        }
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        if (!colorPreviewActive && !isActive()) {
            return;
        }

        // This event runs before the frame installs its matrix stack, so the first
        // frame of a world has none yet and simply goes unringed.
        PoseStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = matrices.last();
        VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());
        int color = ringColor();

        if (colorPreviewActive) {
            CourageAuraRenderer.render(
                    vertices,
                    pose,
                    client.player.getPosition(tickDelta),
                    camera,
                    CourageRangeTracker.RADIUS,
                    color,
                    AURA_ALPHA);
            return;
        }

        for (CourageCircle circle : CourageRangeTracker.circles()) {
            if (!shouldDraw(circle)) {
                continue;
            }
            CourageAuraRenderer.render(
                    vertices,
                    pose,
                    circle.player().getPosition(tickDelta),
                    camera,
                    CourageRangeTracker.RADIUS,
                    color,
                    AURA_ALPHA);
        }
    }

    /**
     * The local player's own aura waits for a full Courage bar, since there is no
     * point standing in a ring that cannot fire. Wynncraft publishes nobody else's
     * charge, so other Shamans' rings are never gated.
     */
    private static boolean shouldDraw(CourageCircle circle) {
        return !circle.self() || CourageCharge.allowsOwnAura();
    }

    private static boolean isEnabled() {
        return WynncraftServerPolicy.isCurrentServerAllowed()
                && (SeqClient.getCourageRangeSetting() == null
                        || SeqClient.getCourageRangeSetting().getValue());
    }
}
