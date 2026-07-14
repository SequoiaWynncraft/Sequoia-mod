package com.seqwawa.seq.map;

import java.util.Objects;

public record GuildTerritory(String name, MapBounds bounds) {
    public GuildTerritory {
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Territory name must not be blank.");
        }
        Objects.requireNonNull(bounds, "bounds");
        bounds = new MapBounds(
                Math.min(bounds.minX(), bounds.maxX()),
                Math.min(bounds.minZ(), bounds.maxZ()),
                Math.max(bounds.minX(), bounds.maxX()),
                Math.max(bounds.minZ(), bounds.maxZ()));
    }

    public static GuildTerritory fromCorners(
            String name,
            double startX,
            double startZ,
            double endX,
            double endZ) {
        return new GuildTerritory(name, new MapBounds(startX, startZ, endX, endZ));
    }

    public boolean contains(double x, double z) {
        return bounds.contains(x, z);
    }

    public double centerX() {
        return (bounds.minX() + bounds.maxX()) / 2.0;
    }

    public double centerZ() {
        return (bounds.minZ() + bounds.maxZ()) / 2.0;
    }

    public double area() {
        return (bounds.maxX() - bounds.minX()) * (bounds.maxZ() - bounds.minZ());
    }
}
