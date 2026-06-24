package com.seqwawa.seq.managers;

public interface SeqBadgeNametagRendererHandle {
    default void tick() {}

    default String status() {
        return "enabled";
    }
}
