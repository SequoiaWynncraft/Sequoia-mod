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
import java.util.Objects;
import java.util.UUID;

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
    private static final int SIDEBAR_SCAN_INTERVAL_TICKS = 5;
    private static final int LIGHT_HOLDER_CONFIRM_TICKS = 60;
    private static final int DEFAULT_RING_COLOR = 0x00FFFF;
    private static final int RING_ALPHA = 230;
    private static boolean wasPrep = false;
    private static boolean colorPreviewActive = false;
    private static int sidebarScanTicksRemaining = 0;
    private static UUID lightHolderCandidateId = null;

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

    private static RoomState readSidebarState(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return RoomState.empty();

        RoomState state = RoomState.empty().observe(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            Component display = entry.display();
            state = state.observe(display != null ? display.getString() : entry.owner());
            if (state.complete()) {
                break;
            }
        }
        return state;
    }

    static RoomState detectRoomState(Iterable<String> sidebarLines) {
        RoomState state = RoomState.empty();
        for (String line : sidebarLines) {
            state = state.observe(line);
            if (state.complete()) {
                break;
            }
        }
        return state;
    }

    static HolderCounter updateHolderCounter(UUID previousCandidateId, int previousTicks, UUID soleCandidateId) {
        if (soleCandidateId == null) {
            return HolderCounter.empty();
        }
        if (!Objects.equals(previousCandidateId, soleCandidateId)) {
            return new HolderCounter(soleCandidateId, 1);
        }
        return new HolderCounter(soleCandidateId, Math.min(LIGHT_HOLDER_CONFIRM_TICKS, previousTicks + 1));
    }

    private static void clearTracking(){
        prepRoom = false;
        inRoom = false;
        wasPrep = false;
        LightHolder = null;
        possibleLightHolders.clear();
        playerUnderLight = 0;
        sidebarScanTicksRemaining = 0;
        lightHolderCandidateId = null;
    }

    public static void Tick(Minecraft client){
        if(!isEnabled() || client.player == null || client.level == null){
            clearTracking();
            return;
        }

        if (sidebarScanTicksRemaining <= 0) {
            RoomState roomState = readSidebarState(client);
            prepRoom = roomState.prepRoom();
            inRoom = roomState.inRoom();
            sidebarScanTicksRemaining = SIDEBAR_SCAN_INTERVAL_TICKS;
        }
        sidebarScanTicksRemaining--;

        if(prepRoom && !wasPrep){
            LightHolder = null;
            possibleLightHolders.clear();
            playerUnderLight = 0;
            lightHolderCandidateId = null;
        }
        wasPrep = prepRoom;

        if(!prepRoom)return;

        possibleLightHolders.clear();
        client.level.players().forEach(player -> {
            if(player.isSpectator())return;
            if(player.position().distanceToSqr(LightPos) <= 1.5 * 1.5){
                possibleLightHolders.add(player);
            }
        });

        AbstractClientPlayer soleCandidate =
                possibleLightHolders.size() == 1 ? possibleLightHolders.getFirst() : null;
        HolderCounter counter = updateHolderCounter(
                lightHolderCandidateId,
                playerUnderLight,
                soleCandidate != null ? soleCandidate.getUUID() : null);
        lightHolderCandidateId = counter.candidateId();
        playerUnderLight = counter.ticks();
        if(playerUnderLight >= LIGHT_HOLDER_CONFIRM_TICKS && soleCandidate != null){
            LightHolder = soleCandidate;
        }
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

    record RoomState(boolean prepRoom, boolean inRoom) {
        private static RoomState empty() {
            return new RoomState(false, false);
        }

        private RoomState observe(String text) {
            if (text == null || text.isEmpty()) {
                return this;
            }
            return new RoomState(
                    prepRoom || text.contains("Gather the Light!"),
                    inRoom || text.contains("Find and kill"));
        }

        private boolean complete() {
            return prepRoom && inRoom;
        }
    }

    record HolderCounter(UUID candidateId, int ticks) {
        private static HolderCounter empty() {
            return new HolderCounter(null, 0);
        }
    }
}
