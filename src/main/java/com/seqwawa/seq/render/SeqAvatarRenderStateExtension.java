package com.seqwawa.seq.render;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public interface SeqAvatarRenderStateExtension {
    UUID seq$getPlayerUuid();

    void seq$setPlayerUuid(UUID playerUuid);

    boolean seq$isLocalPlayer();

    void seq$setLocalPlayer(boolean localPlayer);

    Vec3 seq$getNameTagAttachment();

    void seq$setNameTagAttachment(Vec3 nameTagAttachment);
}
