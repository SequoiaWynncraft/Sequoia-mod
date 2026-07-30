package com.seqwawa.seq.model;

import java.util.List;

public record IngredientGuideEntry(
        String displayName,
        String internalName,
        int tier,
        int level,
        List<String> skills,
        Icon icon,
        List<Effect> effects,
        CraftingModifiers craftingModifiers,
        List<DropSource> dropSources) {

    public IngredientGuideEntry(
            String displayName,
            String internalName,
            int tier,
            int level,
            List<String> skills,
            Icon icon,
            List<DropSource> dropSources) {
        this(
                displayName,
                internalName,
                tier,
                level,
                skills,
                icon,
                List.of(),
                CraftingModifiers.empty(),
                dropSources);
    }

    public IngredientGuideEntry {
        skills = skills == null ? List.of() : List.copyOf(skills);
        effects = effects == null ? List.of() : List.copyOf(effects);
        craftingModifiers = craftingModifiers == null ? CraftingModifiers.empty() : craftingModifiers;
        dropSources = dropSources == null ? List.of() : List.copyOf(dropSources);
    }

    public record Icon(String format, String itemId, int modelData, String textureHash) {
        public static Icon unavailable() {
            return new Icon("unavailable", null, 0, null);
        }

        public String cacheKey() {
            return format + ":" + (textureHash != null ? textureHash : itemId + ":" + modelData);
        }
    }

    public record Effect(String apiName, int min, int max) {}

    public record CraftingModifiers(
            int duration,
            int charges,
            int durability,
            List<Modifier> requirements,
            List<Modifier> positions) {
        public CraftingModifiers {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            positions = positions == null ? List.of() : List.copyOf(positions);
        }

        public static CraftingModifiers empty() {
            return new CraftingModifiers(0, 0, 0, List.of(), List.of());
        }
    }

    public record Modifier(String apiName, int value) {}

    public record DropSource(String name, List<SpawnLocation> locations) {
        public DropSource {
            locations = List.copyOf(locations);
        }
    }

    public record SpawnLocation(int x, int y, int z, int radius) {
        public String coordinates() {
            return x + ", " + y + ", " + z;
        }
    }
}
