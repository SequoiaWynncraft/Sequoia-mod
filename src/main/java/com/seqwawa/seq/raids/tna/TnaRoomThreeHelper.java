package com.seqwawa.seq.raids.tna;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.utils.PacketTextNormalizer;
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

/** World-space standing and aiming markers for TNA room 3, challenge 2. */
public final class TnaRoomThreeHelper {
    static final Vec3 STAND_POINT = new Vec3(25_586.0, 31.0, -23_539.4);
    static final Vec3 AIM_POINT = new Vec3(25_591.4, 32.8, -23_548.0);
    static final double DISPLAY_RADIUS = 12.0;

    private static final Pattern CHALLENGE_TWO_OF_FOUR =
            Pattern.compile("(?i)(?:^|\\s)Challenges?\\s*:?\\s*2/4(?:\\s|$)");
    private static final int SIDEBAR_SCAN_INTERVAL_TICKS = 5;
    private static final double STAND_CROSS_HALF_SIZE = 0.35;
    private static final double STAND_CROSS_Y_OFFSET = 0.03;
    private static final float LINE_WIDTH = 3.0F;
    private static final int STAND_RED = 0x55;
    private static final int STAND_GREEN = 0xFF;
    private static final int STAND_BLUE = 0x80;
    private static final int AIM_RED = 0xFF;
    private static final int AIM_GREEN = 0xD8;
    private static final int AIM_BLUE = 0x4D;
    private static final int MARKER_ALPHA = 0xE0;

    private static boolean challengeActive;
    private static int sidebarScanTicksRemaining;

    private TnaRoomThreeHelper() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TnaRoomThreeHelper::tick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> reset());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TnaRoomThreeHelper::render);
    }

    private static void tick(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null) {
            reset();
            return;
        }
        if (sidebarScanTicksRemaining <= 0) {
            challengeActive = readChallengeState(client);
            sidebarScanTicksRemaining = SIDEBAR_SCAN_INTERVAL_TICKS;
        }
        sidebarScanTicksRemaining--;
    }

    private static boolean readChallengeState(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return false;
        }
        if (isChallengeTwoOfFour(sidebar.getDisplayName().getString())) {
            return true;
        }
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component renderedLine = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            if (isChallengeTwoOfFour(renderedLine.getString())) {
                return true;
            }
        }
        return false;
    }

    static boolean detectChallengeTwoOfFour(Iterable<String> sidebarLines) {
        if (sidebarLines == null) {
            return false;
        }
        for (String line : sidebarLines) {
            if (isChallengeTwoOfFour(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChallengeTwoOfFour(String line) {
        String cleaned = PacketTextNormalizer.normalizeForParsing(line);
        return CHALLENGE_TWO_OF_FOUR.matcher(cleaned).find();
    }

    static boolean shouldRender(boolean activeChallenge, double distanceSquared) {
        return activeChallenge && distanceSquared <= DISPLAY_RADIUS * DISPLAY_RADIUS;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled()
                || client.player == null
                || client.level == null
                || !shouldRender(challengeActive, client.player.position().distanceToSqr(STAND_POINT))) {
            return;
        }

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES_TRANSLUCENT);
        renderStandCross(lines, pose, camera);
        renderAimGuide(lines, pose, camera);
    }

    private static void renderStandCross(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera) {
        double y = STAND_POINT.y + STAND_CROSS_Y_OFFSET;
        addLine(
                lines,
                pose,
                new Vec3(STAND_POINT.x - STAND_CROSS_HALF_SIZE, y, STAND_POINT.z),
                new Vec3(STAND_POINT.x + STAND_CROSS_HALF_SIZE, y, STAND_POINT.z),
                camera,
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
        addLine(
                lines,
                pose,
                new Vec3(STAND_POINT.x, y, STAND_POINT.z - STAND_CROSS_HALF_SIZE),
                new Vec3(STAND_POINT.x, y, STAND_POINT.z + STAND_CROSS_HALF_SIZE),
                camera,
                STAND_RED,
                STAND_GREEN,
                STAND_BLUE);
    }

    private static void renderAimGuide(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera) {
        addLine(
                lines,
                pose,
                aimGuideStart(),
                AIM_POINT,
                camera,
                AIM_RED,
                AIM_GREEN,
                AIM_BLUE);
    }

    static Vec3 aimGuideStart() {
        return STAND_POINT.add(0.0, STAND_CROSS_Y_OFFSET, 0.0);
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

    private static boolean isEnabled() {
        return WynncraftServerPolicy.isCurrentServerAllowed()
                && (SeqClient.getTnaRoomThreeHelperSetting() == null
                        || SeqClient.getTnaRoomThreeHelperSetting().getValue());
    }

    private static void reset() {
        challengeActive = false;
        sidebarScanTicksRemaining = 0;
    }
}
