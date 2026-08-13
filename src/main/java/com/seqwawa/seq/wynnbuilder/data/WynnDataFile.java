package com.seqwawa.seq.wynnbuilder.data;

/**
 * The data files that make up one WynnBuilder data version.
 *
 * <p>Only {@link #required} files block the builder from opening; the rest degrade individual
 * features. That keeps a partial download usable rather than failing outright.
 */
public enum WynnDataFile {
    ENCODING_CONSTS("encoding_consts.json", true),
    ITEMS("items.json", true),
    RECIPES("recipes.json", false),
    INGREDIENTS("ingreds.json", false),
    TOMES("tomes.json", false),
    ASPECTS("aspects.json", false),
    ABILITY_TREE("atree.json", false),
    MAJOR_IDS("majid.json", false);

    private final String fileName;
    private final boolean required;

    WynnDataFile(String fileName, boolean required) {
        this.fileName = fileName;
        this.required = required;
    }

    public String fileName() {
        return fileName;
    }

    public boolean required() {
        return required;
    }
}
