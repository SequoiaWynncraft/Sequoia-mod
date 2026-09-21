package com.seqwawa.seq.model.war;

/** Desired capability counts for a team. Targets are advisory and may overlap per player. */
public record WarCompositionTargets(int solo, int dps, int tank) {
    public static final WarCompositionTargets NONE = new WarCompositionTargets(0, 0, 0);

    public WarCompositionTargets {
        validate(solo);
        validate(dps);
        validate(tank);
    }

    public int target(WarCompositionRole role) {
        return switch (role) {
            case SOLO -> solo;
            case DPS -> dps;
            case TANK -> tank;
        };
    }

    public WarCompositionTargets with(WarCompositionRole role, int value) {
        return switch (role) {
            case SOLO -> new WarCompositionTargets(value, dps, tank);
            case DPS -> new WarCompositionTargets(solo, value, tank);
            case TANK -> new WarCompositionTargets(solo, dps, value);
        };
    }

    public boolean configured() {
        return solo > 0 || dps > 0 || tank > 0;
    }

    private static void validate(int value) {
        if (value < 0 || value > 5) {
            throw new IllegalArgumentException("Composition targets must be between 0 and 5.");
        }
    }
}
