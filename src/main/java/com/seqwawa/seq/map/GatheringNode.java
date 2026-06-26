package com.seqwawa.seq.map;

public record GatheringNode(int x, int y, int z, int angle, String type, String resource, int level) {
    public GatheringProfession profession() {
        return GatheringProfession.fromResource(resource);
    }
}
