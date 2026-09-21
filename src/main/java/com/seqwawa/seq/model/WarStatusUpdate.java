package com.seqwawa.seq.model;

import java.util.Objects;

/** Immutable live location state for a player participating in guild wars. */
public record WarStatusUpdate(
        Status status,
        WynnClassType classType,
        String territory,
        Integer x,
        Integer z) {

    public enum Status {
        WAR,
        WORLD,
        REMOVE
    }

    public WarStatusUpdate {
        status = Objects.requireNonNull(status, "status");
        if (status != Status.REMOVE) {
            classType = Objects.requireNonNull(classType, "classType");
        }

        territory = normalize(territory);
        switch (status) {
            case WAR -> {
                if (territory == null) {
                    throw new IllegalArgumentException("A WAR status requires a territory.");
                }
                x = null;
                z = null;
            }
            case WORLD -> {
                if (x == null || z == null) {
                    throw new IllegalArgumentException("A WORLD status requires x and z coordinates.");
                }
                territory = null;
            }
            case REMOVE -> {
                territory = null;
                x = null;
                z = null;
            }
        }
    }

    public static WarStatusUpdate war(WynnClassType classType, String territory) {
        return new WarStatusUpdate(Status.WAR, classType, territory, null, null);
    }

    public static WarStatusUpdate world(WynnClassType classType, int x, int z) {
        return new WarStatusUpdate(Status.WORLD, classType, null, x, z);
    }

    public static WarStatusUpdate remove(WynnClassType classType) {
        return new WarStatusUpdate(Status.REMOVE, classType, null, null, null);
    }

    public static WarStatusUpdate remove() {
        return remove(null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
