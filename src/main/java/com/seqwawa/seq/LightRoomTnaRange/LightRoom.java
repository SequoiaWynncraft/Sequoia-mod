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

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.PacketTextNormalizer;

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
    private static final long DEBUG_SIDEBAR_LOG_INTERVAL_MS = 1_000L;
    private static boolean wasPrep = false;
    private static boolean colorPreviewActive = false;
    private static String lastDebugStatus;
    private static String lastDebugSidebar;
    private static String lastDebugCandidates;
    private static String lastDebugHolder;
    private static boolean lastDebugThresholdReached;
    private static long nextDebugSidebarLogAtMs;

    private LightRoom() {}

    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(LightRoom::Tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LightRoom::Render);
    }

    private static boolean isEnabled() {
        return WynncraftServerPolicy.isCurrentServerAllowed()
            && (SeqClient.getLightRoomVisualiserSetting() == null || SeqClient.getLightRoomVisualiserSetting().getValue());
    }

    private static boolean isDebugLoggingEnabled() {
        return SeqClient.getLightRoomDebugLoggingSetting() != null
            && SeqClient.getLightRoomDebugLoggingSetting().getValue();
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

    public static void onSystemChat(Component message, boolean overlay) {
        if (!isDebugLoggingEnabled() || message == null) {
            return;
        }
        String plain = message.getString();
        String normalized = PacketTextNormalizer.normalizeForParsing(plain);
        SeqClient.LOGGER.info(
                "[TnaR2Debug] chat channel={} state={} plain=\"{}\" normalized=\"{}\"",
                overlay ? "overlay" : "system",
                trackerState(),
                escapeForLog(plain),
                escapeForLog(normalized));
    }

    public static void Tick(Minecraft client){
        if (!isDebugLoggingEnabled()) {
            resetDebugTracking();
        }
        if(!isEnabled() || client.player == null || client.level == null){
            if (isDebugLoggingEnabled()) {
                String reason = !WynncraftServerPolicy.isCurrentServerAllowed()
                    ? "blocked_server"
                    : SeqClient.getLightRoomVisualiserSetting() != null
                            && !SeqClient.getLightRoomVisualiserSetting().getValue()
                        ? "visualiser_disabled"
                        : client.player == null ? "missing_player" : "missing_level";
                logDebugStatus("inactive:" + reason);
            }
            clearTracking();
            return;
        }
        logDebugStatus("active");

        String scoreboard = readSidebarText(client);
        boolean previousPrepRoom = prepRoom;
        boolean previousInRoom = inRoom;
        prepRoom = scoreboard.contains("Gather the Light!");
        inRoom = scoreboard.contains("Find and kill");
        logDebugSidebar(client, scoreboard, previousPrepRoom, previousInRoom);

        if(prepRoom && !wasPrep){
            LightHolder = null;
            possibleLightHolders.clear();
            playerUnderLight = 0;
        }
        wasPrep = prepRoom;

        if(!prepRoom) {
            logDebugCandidates();
            return;
        }

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
        logDebugCandidates();
    }

    private static void logDebugStatus(String status) {
        if (!isDebugLoggingEnabled() || Objects.equals(lastDebugStatus, status)) {
            return;
        }
        lastDebugStatus = status;
        SeqClient.LOGGER.info("[TnaR2Debug] tracker_status {}", status);
    }

    private static void logDebugSidebar(
            Minecraft client,
            String scoreboard,
            boolean previousPrepRoom,
            boolean previousInRoom) {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        if (previousPrepRoom != prepRoom || previousInRoom != inRoom) {
            SeqClient.LOGGER.info(
                    "[TnaR2Debug] phase_transition prep={} -> {} room={} -> {}",
                    previousPrepRoom,
                    prepRoom,
                    previousInRoom,
                    inRoom);
        }

        long now = System.currentTimeMillis();
        if (Objects.equals(lastDebugSidebar, scoreboard) || now < nextDebugSidebarLogAtMs) {
            return;
        }
        lastDebugSidebar = scoreboard;
        nextDebugSidebarLogAtMs = now + DEBUG_SIDEBAR_LOG_INTERVAL_MS;
        SeqClient.LOGGER.info(
                "[TnaR2Debug] sidebar player={} position={} prep={} room={} text=\"{}\"",
                client.player.getGameProfile().name(),
                formatPosition(client.player.position()),
                prepRoom,
                inRoom,
                escapeForLog(scoreboard));
    }

    private static void logDebugCandidates() {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        String candidates = possibleLightHolders.stream()
                .map(player -> player.getGameProfile().name())
                .sorted()
                .toList()
                .toString();
        String holder = LightHolder == null ? "none" : LightHolder.getGameProfile().name();
        boolean thresholdReached = playerUnderLight >= 60;
        if (!Objects.equals(lastDebugCandidates, candidates)
                || !Objects.equals(lastDebugHolder, holder)
                || lastDebugThresholdReached != thresholdReached) {
            SeqClient.LOGGER.info(
                    "[TnaR2Debug] light_tracking candidates={} accumulatedTicks={} thresholdReached={} holder={}",
                    candidates,
                    playerUnderLight,
                    thresholdReached,
                    holder);
            lastDebugCandidates = candidates;
            lastDebugHolder = holder;
            lastDebugThresholdReached = thresholdReached;
        }
    }

    private static String trackerState() {
        return "enabled=" + isEnabled()
            + ",prep=" + prepRoom
            + ",room=" + inRoom
            + ",candidates=" + possibleLightHolders.size()
            + ",holder=" + (LightHolder == null ? "none" : LightHolder.getGameProfile().name());
    }

    private static String formatPosition(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", position.x, position.y, position.z);
    }

    private static String escapeForLog(String value) {
        return value == null
            ? ""
            : value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\"", "\\\"");
    }

    private static void resetDebugTracking() {
        lastDebugStatus = null;
        lastDebugSidebar = null;
        lastDebugCandidates = null;
        lastDebugHolder = null;
        lastDebugThresholdReached = false;
        nextDebugSidebarLogAtMs = 0L;
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
