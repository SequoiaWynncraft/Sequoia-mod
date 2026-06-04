package org.sequoia.seq.radiance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public final class RadianceInvestigationProbe {
    private static final int DURATION_TICKS = 5;
    private static final double MAX_PARTICLE_DISTANCE = 16.0;

    private static final List<ProbeSession> ACTIVE_SESSIONS = new ArrayList<>();

    private RadianceInvestigationProbe() {}

    public static void clear() {
        ACTIVE_SESSIONS.clear();
    }

    public static UUID start(Vec3 center, float model, Set<Integer> models) {
        for (ProbeSession existing : ACTIVE_SESSIONS) {
            if (horizontalDistance(existing.center, center) <= 9.0) {
                return existing.id;
            }
        }

        ProbeSession session = new ProbeSession(UUID.randomUUID(), center, model, models);
        ACTIVE_SESSIONS.add(session);
        return session.id;
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            ACTIVE_SESSIONS.clear();
            return;
        }

        if (ACTIVE_SESSIONS.isEmpty()) {
            return;
        }

        for (Iterator<ProbeSession> iterator = ACTIVE_SESSIONS.iterator(); iterator.hasNext(); ) {
            ProbeSession session = iterator.next();
            session.age++;

            if (session.age >= DURATION_TICKS) {
                session.finish();
                iterator.remove();
            }
        }
    }

    public static void onParticle(ParticleOptions particle, double x, double y, double z) {
        if (particle.getType() != ParticleTypes.SQUID_INK) {
            return;
        }
        if (ACTIVE_SESSIONS.isEmpty()) {
            return;
        }

        Vec3 particlePos = new Vec3(x, y, z);
        for (ProbeSession session : ACTIVE_SESSIONS) {
            session.onSquidInkParticle(particlePos);
        }
    }

    private static final class ProbeSession {
        private final UUID id;
        private final Vec3 center;
        private final float model;
        private final Set<Integer> models;
        private int age;
        private double furthestSquidInkDistance;

        private ProbeSession(UUID id, Vec3 center, float model, Set<Integer> models) {
            this.id = id;
            this.center = center;
            this.model = model;
            this.models = new HashSet<>(models);
        }

        private void onSquidInkParticle(Vec3 pos) {
            double horizontalDistance = horizontalDistance(pos, center);
            if (horizontalDistance > MAX_PARTICLE_DISTANCE) {
                return;
            }

            furthestSquidInkDistance = Math.max(furthestSquidInkDistance, horizontalDistance);
        }

        private void finish() {
            float resolvedRadius = resolveRadius();
            if (resolvedRadius > 0.0f) {
                ResourcePackModelScanner.learnRadianceModels(models);
                PingManager.updatePingRadius(id, resolvedRadius);
                EntityDebugTracker.confirmModel(model);
            } else if (EntityDebugTracker.getKnownModel() <= 0.0f) {
                EntityDebugTracker.markBadModel(model);
            }
        }

        private float resolveRadius() {
            if (furthestSquidInkDistance <= 0.0) {
                return 0.0f;
            }
            return (float) Math.ceil(furthestSquidInkDistance);
        }
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
