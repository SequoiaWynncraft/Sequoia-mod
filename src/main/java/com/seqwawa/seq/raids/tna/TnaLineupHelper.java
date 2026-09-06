package com.seqwawa.seq.raids.tna;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
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

    private static final int ROOM_THREE_CHALLENGE = 2;
    private static final int NO_CHALLENGE = -1;
    private static final String TNA_TITLE = "the nameless anomaly";
    private static final List<String> OTHER_RAID_TITLES = List.of(
            "nest of the grootslangs", "nexus of light", "the canyon colossus", "the wartorn palace");
    private static final Pattern CHALLENGE_PROGRESS =
            Pattern.compile("(?i)(?:^|\\s)Challenges?\\s*:?\\s*([0-4])/4(?:\\s|$)");
    private static final int SIDEBAR_SCAN_INTERVAL_TICKS = 5;
    private static final double CROSS_HALF_SIZE = 0.35;
    private static final double STRIP_HALF_WIDTH = 0.04;
    private static final double FLOOR_MARKER_OFFSET = 0.03;
    private static final double WALL_MARKER_OFFSET = 0.03;
    private static final int STAND_RED = 0x55;
    private static final int STAND_GREEN = 0xFF;
    private static final int STAND_BLUE = 0x80;
    private static final int AIM_RED = 0xFF;
    private static final int AIM_GREEN = 0xD8;
    private static final int AIM_BLUE = 0x4D;
    private static final int MARKER_ALPHA = 0xE0;

    private static int activeChallenge = NO_CHALLENGE;
    private static int sidebarScanTicksRemaining;
    private static boolean inTnaRaid;

    private TnaLineupHelper() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TnaLineupHelper::tick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> reset());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TnaLineupHelper::render);
    }

    private static void tick(Minecraft client) {
        if (!supportsScope(WynncraftServerPolicy.currentScope()) || client.player == null || client.level == null) {
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
        if (!inTnaRaid) {
            return NO_CHALLENGE;
        }

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

    public static void onTitle(Component title) {
        if (title == null) {
            return;
        }
        String rawTitle = title.getString();
        if (isTnaTitle(rawTitle)) {
            inTnaRaid = true;
        } else if (OTHER_RAID_TITLES.stream().anyMatch(cleanTitle(rawTitle)::contains)) {
            inTnaRaid = false;
        }
    }

    static boolean isTnaTitle(String title) {
        return title != null && cleanTitle(title).contains(TNA_TITLE);
    }

    private static String cleanTitle(String title) {
        return PacketTextNormalizer.normalizeForParsing(title).toLowerCase(Locale.ROOT);
    }

    static int activeTnaChallenge() {
        return inTnaRaid ? activeChallenge : NO_CHALLENGE;
    }

    static boolean supportsScope(WynncraftServerPolicy.Scope scope) {
        return scope != WynncraftServerPolicy.Scope.BLOCKED;
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
        if (!supportsScope(WynncraftServerPolicy.currentScope()) || client.player == null || client.level == null) {
            return;
        }

        if (isBerryEnabled()) {
            renderBerry(context, client);
        }
        if (activeChallenge == ROOM_THREE_CHALLENGE && isRoomThreeEnabled()) {
            renderRoomThree(context, client);
        }
    }

    private static void renderBerry(WorldRenderContext context, Minecraft client) {
        if (!isWithinDisplayRadius(client.player.position().distanceToSqr(BERRY_STAND_POINT))) {
            return;
        }

        RenderState state = renderState(context, client);
        renderFloorCross(state.lines(), state.pose(), state.camera(), BERRY_STAND_POINT);
        renderAimCross(state.lines(), state.pose(), state.camera(), BERRY_STAND_POINT, BERRY_AIM_POINT);
    }

    static boolean isWithinDisplayRadius(double distanceSquared) {
        return distanceSquared <= DISPLAY_RADIUS * DISPLAY_RADIUS;
    }

    private static void renderRoomThree(WorldRenderContext context, Minecraft client) {
        if (!shouldRender(
                ROOM_THREE_CHALLENGE,
                activeChallenge,
                client.player.position().distanceToSqr(ROOM_THREE_STAND_POINT))) {
            return;
        }

        RenderState state = renderState(context, client);
        renderFloorCross(state.lines(), state.pose(), state.camera(), ROOM_THREE_STAND_POINT);
        renderBeam(
                state.lines(),
                state.pose(),
                floorMarkerCenter(ROOM_THREE_STAND_POINT),
                ROOM_THREE_AIM_POINT,
                state.camera(),
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
    }

    private static RenderState renderState(WorldRenderContext context, Minecraft client) {
        return new RenderState(
                context.consumers().getBuffer(RenderTypes.debugQuads()),
                context.matrices().last(),
                client.gameRenderer.getMainCamera().position());
    }

    private static void renderFloorCross(
            VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, Vec3 point) {
        Vec3 center = floorMarkerCenter(point);
        addDoubleSidedQuad(
                lines,
                pose,
                camera,
                center.add(-CROSS_HALF_SIZE, 0.0, -STRIP_HALF_WIDTH),
                center.add(CROSS_HALF_SIZE, 0.0, -STRIP_HALF_WIDTH),
                center.add(CROSS_HALF_SIZE, 0.0, STRIP_HALF_WIDTH),
                center.add(-CROSS_HALF_SIZE, 0.0, STRIP_HALF_WIDTH),
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
        addDoubleSidedQuad(
                lines,
                pose,
                camera,
                center.add(-STRIP_HALF_WIDTH, 0.0, -CROSS_HALF_SIZE),
                center.add(STRIP_HALF_WIDTH, 0.0, -CROSS_HALF_SIZE),
                center.add(STRIP_HALF_WIDTH, 0.0, CROSS_HALF_SIZE),
                center.add(-STRIP_HALF_WIDTH, 0.0, CROSS_HALF_SIZE),
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
    }

    private static void renderAimCross(
            VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, Vec3 standPoint, Vec3 aimPoint) {
        Vec3 center = wallMarkerCenter(standPoint, aimPoint);
        Vec3 direction = aimPoint.subtract(standPoint).normalize();
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x).normalize();
        if (side.lengthSqr() == 0.0) {
            side = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 thinSide = side.scale(STRIP_HALF_WIDTH);
        Vec3 wideSide = side.scale(CROSS_HALF_SIZE);
        addDoubleSidedQuad(
                lines,
                pose,
                camera,
                center.add(0.0, -CROSS_HALF_SIZE, 0.0).subtract(thinSide),
                center.add(0.0, CROSS_HALF_SIZE, 0.0).subtract(thinSide),
                center.add(0.0, CROSS_HALF_SIZE, 0.0).add(thinSide),
                center.add(0.0, -CROSS_HALF_SIZE, 0.0).add(thinSide),
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
        addDoubleSidedQuad(
                lines,
                pose,
                camera,
                center.subtract(wideSide).add(0.0, -STRIP_HALF_WIDTH, 0.0),
                center.subtract(wideSide).add(0.0, STRIP_HALF_WIDTH, 0.0),
                center.add(wideSide).add(0.0, STRIP_HALF_WIDTH, 0.0),
                center.add(wideSide).add(0.0, -STRIP_HALF_WIDTH, 0.0),
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

    private static void renderBeam(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 worldStart,
            Vec3 worldEnd,
            Vec3 camera,
            int red,
            int green,
            int blue) {
        Vec3 direction = worldEnd.subtract(worldStart).normalize();
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0)).normalize().scale(STRIP_HALF_WIDTH);
        Vec3 up = direction.cross(side).normalize().scale(STRIP_HALF_WIDTH);
        addDoubleSidedQuad(
                lines, pose, camera, worldStart.subtract(side), worldEnd.subtract(side), worldEnd.add(side),
                worldStart.add(side), red, green, blue);
        addDoubleSidedQuad(
                lines, pose, camera, worldStart.subtract(up), worldEnd.subtract(up), worldEnd.add(up),
                worldStart.add(up), red, green, blue);
    }

    private static void addDoubleSidedQuad(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 camera,
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth,
            int red,
            int green,
            int blue) {
        addVertex(lines, pose, first.subtract(camera), red, green, blue);
        addVertex(lines, pose, second.subtract(camera), red, green, blue);
        addVertex(lines, pose, third.subtract(camera), red, green, blue);
        addVertex(lines, pose, fourth.subtract(camera), red, green, blue);
        addVertex(lines, pose, fourth.subtract(camera), red, green, blue);
        addVertex(lines, pose, third.subtract(camera), red, green, blue);
        addVertex(lines, pose, second.subtract(camera), red, green, blue);
        addVertex(lines, pose, first.subtract(camera), red, green, blue);
    }

    private static void addVertex(
            VertexConsumer lines, PoseStack.Pose pose, Vec3 point, int red, int green, int blue) {
        lines.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, MARKER_ALPHA);
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
        inTnaRaid = false;
    }

    private record RenderState(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera) {}
}
