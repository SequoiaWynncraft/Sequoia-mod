package com.seqwawa.seq.raids.tna;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.mixins.GameRendererFogAccessor;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** World-space standing and aiming markers for TNA lineups. */
public final class TnaLineupHelper {
    static final Vec3 BERRY_STAND_POINT = new Vec3(27_758.2, 6.0, -22_049.5);
    static final Vec3 BERRY_AIM_POINT = new Vec3(27_739.0, 9.0, -22_049.6);
    static final Vec3 ROOM_THREE_STAND_POINT = new Vec3(25_586.0, 31.0, -23_539.4);
    static final Vec3 ROOM_THREE_AIM_POINT = new Vec3(25_591.4, 32.8, -23_548.0);
    static final double DISPLAY_RADIUS = 12.0;

    private static final int BERRY_CHALLENGE = 0;
    private static final int ROOM_THREE_CHALLENGE = 2;
    private static final int NO_CHALLENGE = -1;
    private static final Pattern CHALLENGE_PROGRESS =
            Pattern.compile("(?i)(?:^|\\s)Challenges?\\s*:?\\s*([0-4])/4(?:\\s|$)");
    private static final int SIDEBAR_SCAN_INTERVAL_TICKS = 5;
    private static final double CROSS_HALF_SIZE = 0.35;
    private static final double FLOOR_MARKER_OFFSET = 0.03;
    private static final double WALL_MARKER_OFFSET = 0.03;
    private static final float LINE_WIDTH = 3.0F;
    private static final int STAND_RED = 0x55;
    private static final int STAND_GREEN = 0xFF;
    private static final int STAND_BLUE = 0x80;
    private static final int AIM_RED = 0xFF;
    private static final int AIM_GREEN = 0xD8;
    private static final int AIM_BLUE = 0x4D;
    private static final int MARKER_ALPHA = 0xE0;

    private static int activeChallenge = NO_CHALLENGE;
    private static int sidebarScanTicksRemaining;

    private TnaLineupHelper() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TnaLineupHelper::tick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> reset());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TnaLineupHelper::render);
    }

    private static void tick(Minecraft client) {
        if (!WynncraftServerPolicy.isCurrentServerAllowed() || client.player == null || client.level == null) {
            reset();
            return;
        }
        if (sidebarScanTicksRemaining <= 0) {
            activeChallenge = readChallengeProgress(client);
            sidebarScanTicksRemaining = SIDEBAR_SCAN_INTERVAL_TICKS;
        }
        sidebarScanTicksRemaining--;
    }

    private static int readChallengeProgress(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return NO_CHALLENGE;
        }

        List<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component renderedLine = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            lines.add(renderedLine.getString());
        }
        return detectChallengeProgress(lines);
    }

    static int detectChallengeProgress(Iterable<String> sidebarLines) {
        if (sidebarLines == null) {
            return NO_CHALLENGE;
        }
        for (String line : sidebarLines) {
            int challenge = challengeProgress(line);
            if (challenge != NO_CHALLENGE) {
                return challenge;
            }
        }
        return NO_CHALLENGE;
    }

    private static int challengeProgress(String line) {
        String cleaned = PacketTextNormalizer.normalizeForParsing(line);
        Matcher matcher = CHALLENGE_PROGRESS.matcher(cleaned);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : NO_CHALLENGE;
    }

    static boolean shouldRender(int expectedChallenge, int challenge, double distanceSquared) {
        return challenge == expectedChallenge && distanceSquared <= DISPLAY_RADIUS * DISPLAY_RADIUS;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!WynncraftServerPolicy.isCurrentServerAllowed() || client.player == null || client.level == null) {
            return;
        }

        if (activeChallenge == BERRY_CHALLENGE && isBerryEnabled()) {
            renderBerry(context, client);
        } else if (activeChallenge == ROOM_THREE_CHALLENGE && isRoomThreeEnabled()) {
            renderRoomThree(context, client);
        }
    }

    private static void renderBerry(WorldRenderContext context, Minecraft client) {
        if (!shouldRender(
                BERRY_CHALLENGE,
                activeChallenge,
                client.player.position().distanceToSqr(BERRY_STAND_POINT))) {
            return;
        }

        boolean blinded = client.player.hasEffect(MobEffects.BLINDNESS);
        RenderType lines = blinded ? RenderTypes.SECONDARY_BLOCK_OUTLINE : RenderTypes.LINES_TRANSLUCENT;
        RenderState state = renderState(context, client, lines);
        renderFloorCross(state.lines(), state.pose(), state.camera(), BERRY_STAND_POINT);
        renderAimCross(state.lines(), state.pose(), state.camera(), BERRY_STAND_POINT, BERRY_AIM_POINT);
        if (blinded) {
            flushWithoutFog(context, client, lines);
        }
    }

    private static void renderRoomThree(WorldRenderContext context, Minecraft client) {
        if (!shouldRender(
                ROOM_THREE_CHALLENGE,
                activeChallenge,
                client.player.position().distanceToSqr(ROOM_THREE_STAND_POINT))) {
            return;
        }

        RenderState state = renderState(context, client, RenderTypes.LINES_TRANSLUCENT);
        renderFloorCross(state.lines(), state.pose(), state.camera(), ROOM_THREE_STAND_POINT);
        addLine(
                state.lines(),
                state.pose(),
                floorMarkerCenter(ROOM_THREE_STAND_POINT),
                ROOM_THREE_AIM_POINT,
                state.camera(),
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
    }

    private static RenderState renderState(WorldRenderContext context, Minecraft client, RenderType lines) {
        return new RenderState(
                context.consumers().getBuffer(lines),
                context.matrices().last(),
                client.gameRenderer.getMainCamera().position());
    }

    private static void flushWithoutFog(WorldRenderContext context, Minecraft client, RenderType lines) {
        GpuBufferSlice worldFog = RenderSystem.getShaderFog();
        FogRenderer fogRenderer = ((GameRendererFogAccessor) client.gameRenderer).seq$getFogRenderer();
        try {
            RenderSystem.setShaderFog(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
            if (context.consumers() instanceof MultiBufferSource.BufferSource buffers) {
                buffers.endBatch(lines);
            }
        } finally {
            RenderSystem.setShaderFog(worldFog);
        }
    }

    private static void renderFloorCross(
            VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, Vec3 point) {
        Vec3 center = floorMarkerCenter(point);
        addLine(
                lines,
                pose,
                center.add(-CROSS_HALF_SIZE, 0.0, 0.0),
                center.add(CROSS_HALF_SIZE, 0.0, 0.0),
                camera,
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
        addLine(
                lines,
                pose,
                center.add(0.0, 0.0, -CROSS_HALF_SIZE),
                center.add(0.0, 0.0, CROSS_HALF_SIZE),
                camera,
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
    }

    private static void renderAimCross(
            VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, Vec3 standPoint, Vec3 aimPoint) {
        Vec3 center = wallMarkerCenter(standPoint, aimPoint);
        addLine(
                lines,
                pose,
                center.add(0.0, -CROSS_HALF_SIZE, 0.0),
                center.add(0.0, CROSS_HALF_SIZE, 0.0),
                camera,
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
        addLine(
                lines,
                pose,
                center.add(0.0, 0.0, -CROSS_HALF_SIZE),
                center.add(0.0, 0.0, CROSS_HALF_SIZE),
                camera,
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
    }

    static Vec3 floorMarkerCenter(Vec3 point) {
        return point.add(0.0, FLOOR_MARKER_OFFSET, 0.0);
    }

    static Vec3 wallMarkerCenter(Vec3 standPoint, Vec3 aimPoint) {
        double direction = Math.signum(standPoint.x - aimPoint.x);
        return aimPoint.add(direction * WALL_MARKER_OFFSET, 0.0, 0.0);
    }

    private static void addLine(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 worldStart,
            Vec3 worldEnd,
            Vec3 camera,
            int red,
            int green,
            int blue) {
        Vec3 start = worldStart.subtract(camera);
        Vec3 end = worldEnd.subtract(camera);
        Vec3 normal = end.subtract(start).normalize();
        addVertex(lines, pose, start, normal, red, green, blue);
        addVertex(lines, pose, end, normal, red, green, blue);
    }

    private static void addVertex(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 point,
            Vec3 normal,
            int red,
            int green,
            int blue) {
        lines.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, MARKER_ALPHA)
                .setLineWidth(LINE_WIDTH)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    static int activeChallenge() {
        return activeChallenge;
    }

    private static boolean isBerryEnabled() {
        return SeqClient.getTnaBerryLineupSetting() == null || SeqClient.getTnaBerryLineupSetting().getValue();
    }

    private static boolean isRoomThreeEnabled() {
        return SeqClient.getTnaRoomThreeHelperSetting() == null
                || SeqClient.getTnaRoomThreeHelperSetting().getValue();
    }

    private static void reset() {
        activeChallenge = NO_CHALLENGE;
        sidebarScanTicksRemaining = 0;
    }

    private record RenderState(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera) {}
}
