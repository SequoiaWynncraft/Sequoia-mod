package com.seqwawa.seq.managers;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public interface SeqBadgeNametagRendererHandle {
    default void tick() {}

    default String status() {
        return "enabled";
    }

    default boolean shouldSuppressVanillaHealthBar(AvatarRenderState state, boolean localPlayer) {
        return false;
    }
}
