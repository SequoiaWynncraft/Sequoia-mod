package com.seqwawa.seq.model;

import java.util.List;

public record IngredientGuideEntry(
        String displayName,
        String internalName,
        int tier,
        int level,
        List<String> skills,
        Icon icon,
        List<DropSource> dropSources) {

    public IngredientGuideEntry {
        skills = List.copyOf(skills);
        dropSources = List.copyOf(dropSources);
    }

    public record Icon(String format, String itemId, int modelData, String textureHash) {
        public static Icon unavailable() {
            return new Icon("unavailable", null, 0, null);
        }

        public String cacheKey() {
            return format + ":" + (textureHash != null ? textureHash : itemId + ":" + modelData);
        }
    }

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
