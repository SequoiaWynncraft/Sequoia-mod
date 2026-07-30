package com.seqwawa.seq.LightRoomTnaRange;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;

import static com.seqwawa.seq.halcyon.HalcyonRingRenderer.renderRingWall;

public final class LightRoom {
    private static final Vec3 LightPos = new Vec3(-12743, 70, 8380);
    private static final List<AbstractClientPlayer> possibleLightHolders = new ArrayList<>();
    private static int playerUnderLight = 0;
    private static AbstractClientPlayer LightHolder = null;
    private static boolean prepRoom = false;
    private static boolean inRoom = false;
    private static final int radius = 7;
    private static final int DEFAULT_RING_COLOR = 0x00FFFF;
    private static final int RING_ALPHA = 230;
    private static boolean wasPrep = false;
    private static boolean colorPreviewActive = false;

    private LightRoom() {}

    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(LightRoom::Tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LightRoom::Render);
    }

    private static boolean isEnabled() {
        return WynncraftServerPolicy.isCurrentServerAllowed()
            && (SeqClient.getLightRoomVisualiserSetting() == null || SeqClient.getLightRoomVisualiserSetting().getValue());
    }

    public static void setColorPreviewActive(boolean active) {
        colorPreviewActive = active;
    }

    private static int getRingColor() {
        return SeqClient.getLightRoomRingColorSetting() == null
            ? DEFAULT_RING_COLOR
            : SeqClient.getLightRoomRingColorSetting().getValue();
    }

    /**
     * Reads the actual sidebar text (title + score lines). {@code Scoreboard.toString()} only returns
     * the object's identity string, so the room-state checks must read the SIDEBAR objective directly.
     */
    private static String readSidebarText(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return "";

        StringBuilder text = new StringBuilder(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            Component display = entry.display();
            text.append('\n').append(display != null ? display.getString() : entry.owner());
        }
        return text.toString();
    }

    private static void clearTracking(){
        prepRoom = false;
        inRoom = false;
        wasPrep = false;
        LightHolder = null;
        possibleLightHolders.clear();
        playerUnderLight = 0;
    }

    public static void Tick(Minecraft client){
        if(!isEnabled() || client.player == null || client.level == null){
            clearTracking();
            return;
        }

        String scoreboard = readSidebarText(client);
        prepRoom = scoreboard.contains("Gather the Light!");
        inRoom = scoreboard.contains("Find and kill");

        if(prepRoom && !wasPrep){
            LightHolder = null;
            possibleLightHolders.clear();
            playerUnderLight = 0;
        }
        wasPrep = prepRoom;

        if(!prepRoom)return;

        client.level.players().forEach(player -> {
            if(player.isSpectator())return;
            if(player.position().distanceTo(LightPos) <= 1.5){
                if(!possibleLightHolders.contains(player)){
                    possibleLightHolders.add(player);
                }
                playerUnderLight++;
            }
            else{
                if(possibleLightHolders.contains(player)){
                    possibleLightHolders.remove(player);
                }
            }
            if(playerUnderLight >= 60 && possibleLightHolders.size() == 1){
                LightHolder = possibleLightHolders.getFirst();
            }
        });
    }

    public static void Render(WorldRenderContext context) {
        if(!isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if(client.player == null || client.level == null) return;

        AbstractClientPlayer ringCenter;
        if(colorPreviewActive) {
            ringCenter = client.player;
        } else {
            if(!inRoom) return;
            if(LightHolder == null) return;
            ringCenter = LightHolder;
        }

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 center = ringCenter.getPosition(tickDelta);
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.debugQuads());

        renderRingWall(vertices, pose, center, camera, radius, getRingColor(), RING_ALPHA);
    }
}
