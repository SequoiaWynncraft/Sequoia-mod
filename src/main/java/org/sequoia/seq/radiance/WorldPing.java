package org.sequoia.seq.radiance;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record WorldPing(
        UUID id, Vec3 pos, float radius, int ticksLeft, boolean showCircle, int particleSpawnCooldownTicks) {}
