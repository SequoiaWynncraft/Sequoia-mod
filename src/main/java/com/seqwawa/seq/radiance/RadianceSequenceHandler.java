package com.seqwawa.seq.radiance;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class RadianceSequenceHandler {
    private RadianceSequenceHandler() {}

    public static void onRadianceDetected(Vec3 pos, float model, Set<Integer> models, boolean showCircle) {
        UUID pingId = RadianceInvestigationProbe.start(pos, model, models);
        PingManager.addPing(pingId, pos, 6.0f, showCircle ? 120 : 20, showCircle);
    }
}
