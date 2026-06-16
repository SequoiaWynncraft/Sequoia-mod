package org.sequoia.seq.radiance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class PingManager {
    private static final int WORLD_RING_REFRESH_COOLDOWN_TICKS = 16;
    private static final int TELEPORT_MOVEMENT_HISTORY_TICKS = 3;
    private static final double TELEPORT_CLEAR_DISTANCE = 32.0;
    private static final List<WorldPing> ACTIVE_PINGS = new ArrayList<>();
    private static final Deque<Double> RECENT_PLAYER_MOVEMENTS = new ArrayDeque<>();
    private static Vec3 lastPlayerPos;

    private PingManager() {}

    public static void clear() {
        ACTIVE_PINGS.clear();
        RECENT_PLAYER_MOVEMENTS.clear();
        lastPlayerPos = null;
    }

    public static void addPing(UUID id, Vec3 pos, float radius, int durationTicks, boolean showCircle) {
        for (int i = ACTIVE_PINGS.size() - 1; i >= 0; i--) {
            WorldPing ping = ACTIVE_PINGS.get(i);

            if (!ping.id().equals(id)) {
                continue;
            }

            ACTIVE_PINGS.set(
                    i,
                    new WorldPing(
                            ping.id(),
                            pos,
                            Math.max(ping.radius(), radius),
                            Math.max(ping.ticksLeft(), durationTicks),
                            ping.showCircle() || showCircle,
                            ping.showCircle() ? ping.ringPhase() : 0,
                            ping.showCircle() ? ping.ringRefreshCooldownTicks() : 0));
            return;
        }

        ACTIVE_PINGS.add(new WorldPing(id, pos, radius, durationTicks, showCircle, 0, 0));
    }

    public static void updatePingRadius(UUID id, float radius) {
        for (int i = ACTIVE_PINGS.size() - 1; i >= 0; i--) {
            WorldPing ping = ACTIVE_PINGS.get(i);

            if (!ping.id().equals(id)) {
                continue;
            }

            ACTIVE_PINGS.set(
                    i,
                    new WorldPing(
                            ping.id(),
                            ping.pos(),
                            Math.max(ping.radius(), radius),
                            ping.ticksLeft(),
                            true,
                            ping.showCircle() ? ping.ringPhase() : 0,
                            ping.showCircle() ? ping.ringRefreshCooldownTicks() : 0));
            return;
        }
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            clear();
            return;
        }

        Vec3 playerPos = client.player.position();
        if (movedTooFar(playerPos)) {
            clear();
            lastPlayerPos = playerPos;
            return;
        }

        lastPlayerPos = playerPos;

        List<WorldPing> remainingPings = new ArrayList<>();

        for (WorldPing ping : ACTIVE_PINGS) {
            RingState ringState = tickRing(client, ping);

            int ticksLeft = ping.ticksLeft() - 1;
            if (ticksLeft > 0) {
                remainingPings.add(
                        new WorldPing(
                                ping.id(),
                                ping.pos(),
                                ping.radius(),
                                ticksLeft,
                                ping.showCircle(),
                                ringState.phase,
                                ringState.cooldownTicks));
            }
        }

        ACTIVE_PINGS.clear();
        ACTIVE_PINGS.addAll(remainingPings);
    }

    public static List<WorldPing> getActivePings() {
        return List.copyOf(ACTIVE_PINGS);
    }

    private static RingState tickRing(Minecraft client, WorldPing ping) {
        if (!ping.showCircle()) {
            return new RingState(ping.ringPhase(), ping.ringRefreshCooldownTicks());
        }

        if (ping.ringRefreshCooldownTicks() > 0) {
            return new RingState(0, ping.ringRefreshCooldownTicks() - 1);
        }

        boolean completedRing = PingRenderer.renderWorld(client, ping);
        if (completedRing) {
            return new RingState(0, WORLD_RING_REFRESH_COOLDOWN_TICKS);
        }

        return new RingState(ping.ringPhase() + 1, 0);
    }

    private record RingState(int phase, int cooldownTicks) {}

    private static boolean movedTooFar(Vec3 playerPos) {
        if (lastPlayerPos != null) {
            addRecentMovement(Math.sqrt(playerPos.distanceToSqr(lastPlayerPos)));
        }

        double recentMovement = 0.0;
        for (double movement : RECENT_PLAYER_MOVEMENTS) {
            recentMovement += movement;
        }

        return recentMovement > TELEPORT_CLEAR_DISTANCE;
    }

    private static void addRecentMovement(double movement) {
        while (RECENT_PLAYER_MOVEMENTS.size() > TELEPORT_MOVEMENT_HISTORY_TICKS) {
            RECENT_PLAYER_MOVEMENTS.removeFirst();
        }

        if (RECENT_PLAYER_MOVEMENTS.size() == TELEPORT_MOVEMENT_HISTORY_TICKS) {
            RECENT_PLAYER_MOVEMENTS.removeFirst();
        }

        RECENT_PLAYER_MOVEMENTS.addLast(movement);

        while (RECENT_PLAYER_MOVEMENTS.size() > TELEPORT_MOVEMENT_HISTORY_TICKS) {
            RECENT_PLAYER_MOVEMENTS.removeFirst();
        }
    }
}
