package org.sequoia.seq.radiance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class PingManager {
    private static final int WORLD_RING_SPAWN_INTERVAL_TICKS = 12;
    private static final List<WorldPing> ACTIVE_PINGS = new ArrayList<>();

    private PingManager() {}

    public static void clear() {
        ACTIVE_PINGS.clear();
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
                            ping.particleSpawnCooldownTicks()));
            return;
        }

        ACTIVE_PINGS.add(new WorldPing(id, pos, radius, durationTicks, showCircle, 0));
    }

    public static void updatePingRadius(UUID id, float radius) {
        for (int i = ACTIVE_PINGS.size() - 1; i >= 0; i--) {
            WorldPing ping = ACTIVE_PINGS.get(i);

            if (!ping.id().equals(id)) {
                continue;
            }

            int particleSpawnCooldownTicks = ping.showCircle() ? ping.particleSpawnCooldownTicks() : 0;
            ACTIVE_PINGS.set(
                    i,
                    new WorldPing(
                            ping.id(),
                            ping.pos(),
                            Math.max(ping.radius(), radius),
                            ping.ticksLeft(),
                            true,
                            particleSpawnCooldownTicks));
            return;
        }
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            ACTIVE_PINGS.clear();
            return;
        }

        List<WorldPing> remainingPings = new ArrayList<>();

        for (WorldPing ping : ACTIVE_PINGS) {
            int particleSpawnCooldownTicks = tickParticleSpawnCooldown(client, ping);

            int ticksLeft = ping.ticksLeft() - 1;
            if (ticksLeft > 0) {
                remainingPings.add(
                        new WorldPing(
                                ping.id(),
                                ping.pos(),
                                ping.radius(),
                                ticksLeft,
                                ping.showCircle(),
                                particleSpawnCooldownTicks));
            }
        }

        ACTIVE_PINGS.clear();
        ACTIVE_PINGS.addAll(remainingPings);
    }

    public static List<WorldPing> getActivePings() {
        return List.copyOf(ACTIVE_PINGS);
    }

    private static int tickParticleSpawnCooldown(Minecraft client, WorldPing ping) {
        if (!ping.showCircle()) {
            return ping.particleSpawnCooldownTicks();
        }

        if (ping.particleSpawnCooldownTicks() <= 0) {
            PingRenderer.renderWorld(client, ping);
            return WORLD_RING_SPAWN_INTERVAL_TICKS - 1;
        }

        return ping.particleSpawnCooldownTicks() - 1;
    }
}
