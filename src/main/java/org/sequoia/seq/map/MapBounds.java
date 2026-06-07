package org.sequoia.seq.map;

public record MapBounds(double minX, double minZ, double maxX, double maxZ) {
    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
