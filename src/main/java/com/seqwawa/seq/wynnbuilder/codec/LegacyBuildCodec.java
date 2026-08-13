package com.seqwawa.seq.wynnbuilder.codec;

import com.seqwawa.seq.wynnbuilder.data.Powder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decoder for the legacy build hashes, versions 0 through 11.
 *
 * <p>Decode only: new links are always written in the V12 binary format. Legacy support matters
 * because it is what years of links shared in Discord actually use, and the formats are fixed-width
 * Base64 fields rather than a bit stream.
 *
 * <p>The layout grew by accretion, so each version adds a section rather than redefining the
 * previous ones: skill points appear at v2, level at v3, tomes at v6, the ability tree at v7,
 * wider tome IDs at v8, more tome slots at v9 and v10, and aspects at v11.
 */
public final class LegacyBuildCodec {
    /** Synthetic IDs 10000-10008 stand for the empty slots, in equipment order. */
    public static final int NONE_ITEM_ID_BASE = 10000;
    /** The "No Aspect" sentinel used by version 11. */
    public static final int NONE_ASPECT_ID = 256;
    /** Legacy powders were written when only six tiers existed. */
    private static final int LEGACY_POWDER_TIERS = 6;

    private static final int EQUIPMENT_COUNT = 9;
    private static final int POWDERABLE_COUNT = 5;
    private static final int SKILL_POINT_COUNT = 5;
    private static final int ASPECT_COUNT = 5;

    private LegacyBuildCodec() {}

    /** One decoded equipment slot: exactly one field is set. */
    public record LegacyEquipment(Integer itemId, String craftedHash, String customHash) {
        public static LegacyEquipment item(int id) {
            return new LegacyEquipment(id, null, null);
        }

        /** Whether this slot holds one of the synthetic "no item" entries. */
        public boolean isNone() {
            return itemId != null && itemId >= NONE_ITEM_ID_BASE && itemId <= NONE_ITEM_ID_BASE + EQUIPMENT_COUNT;
        }
    }

    /** An aspect slot from a version 11 hash. */
    public record LegacyAspect(int aspectId, int tier) {}

    /** The decoded contents of a legacy hash, with IDs left for the data layer to resolve. */
    public record LegacyBuild(
            int version,
            List<LegacyEquipment> equipment,
            List<List<Powder>> powders,
            Integer[] skillPoints,
            int level,
            List<Integer> tomeIds,
            List<LegacyAspect> aspects,
            BitVector abilityTreeBits) {}

    /** Whether the text looks like a legacy hash, i.e. {@code <version>_<data>} with a version 0-11. */
    public static boolean isLegacy(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        int separator = hash.indexOf('_');
        if (separator <= 0 || separator > 2) {
            return false;
        }
        try {
            int version = Integer.parseInt(hash.substring(0, separator));
            return version >= 0 && version <= BuildCodec.MAX_LEGACY_VERSION;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static LegacyBuild decode(String hash) {
        int separator = hash.indexOf('_');
        if (separator < 0) {
            throw new IllegalArgumentException("Not a legacy build hash: missing version separator");
        }
        int version = Integer.parseInt(hash.substring(0, separator));
        Cursor cursor = new Cursor(hash.substring(separator + 1));

        List<LegacyEquipment> equipment = decodeEquipment(cursor, version);

        Integer[] skillPoints = new Integer[SKILL_POINT_COUNT];
        int level = 106; // The default the site used before levels were encoded.
        List<List<Powder>> powders = emptyPowders();

        if (version == 1) {
            powders = decodePowders(cursor);
        } else if (version == 2) {
            skillPoints = decodeSkillPoints(cursor);
            powders = decodePowders(cursor);
        } else if (version >= 3) {
            // Skill points and level share one fixed-width block, level sitting after the five pairs.
            String block = cursor.take(12);
            for (int i = 0; i < SKILL_POINT_COUNT; i++) {
                skillPoints[i] = WynnBase64.toSignedInt(block.substring(i * 2, i * 2 + 2));
            }
            level = WynnBase64.toInt(block.substring(10, 12));
            powders = decodePowders(cursor);
        }

        List<Integer> tomeIds = decodeTomes(cursor, version);
        List<LegacyAspect> aspects = decodeAspects(cursor, version);

        BitVector abilityTree = version >= 7 ? BitVector.fromBase64(cursor.rest()) : new BitVector();

        return new LegacyBuild(version, equipment, powders, skillPoints, level, tomeIds, aspects, abilityTree);
    }

    private static List<LegacyEquipment> decodeEquipment(Cursor cursor, int version) {
        List<LegacyEquipment> equipment = new ArrayList<>(EQUIPMENT_COUNT);
        for (int i = 0; i < EQUIPMENT_COUNT; i++) {
            if (version < 4) {
                equipment.add(LegacyEquipment.item(WynnBase64.toInt(cursor.take(3))));
            } else if (version == 4) {
                if (cursor.peek(1).equals("-")) {
                    cursor.take(1);
                    equipment.add(new LegacyEquipment(null, cursor.take(17), null));
                } else {
                    equipment.add(LegacyEquipment.item(WynnBase64.toInt(cursor.take(3))));
                }
            } else if ("CR-".equals(cursor.peek(3))) {
                equipment.add(new LegacyEquipment(null, cursor.take(20), null));
            } else if ("CI-".equals(cursor.peekAt(3, 3))) {
                // A custom item stores its own length in the three characters that precede it.
                int length = WynnBase64.toInt(cursor.peek(3));
                cursor.take(3);
                equipment.add(new LegacyEquipment(null, null, cursor.take(length)));
            } else {
                equipment.add(LegacyEquipment.item(WynnBase64.toInt(cursor.take(3))));
            }
        }
        return equipment;
    }

    private static Integer[] decodeSkillPoints(Cursor cursor) {
        Integer[] skillPoints = new Integer[SKILL_POINT_COUNT];
        String block = cursor.take(10);
        for (int i = 0; i < SKILL_POINT_COUNT; i++) {
            skillPoints[i] = WynnBase64.toSignedInt(block.substring(i * 2, i * 2 + 2));
        }
        return skillPoints;
    }

    /**
     * Legacy powders pack six powders into five Base64 characters, five bits each.
     *
     * <p>Each powderable slot starts with a block count. Within a block, powders are read from the
     * low bits upwards and a zero value ends the slot early.
     */
    private static List<List<Powder>> decodePowders(Cursor cursor) {
        List<List<Powder>> powders = new ArrayList<>(POWDERABLE_COUNT);
        for (int slot = 0; slot < POWDERABLE_COUNT; slot++) {
            List<Powder> slotPowders = new ArrayList<>();
            int blocks = WynnBase64.toInt(cursor.take(1));
            for (int block = 0; block < blocks; block++) {
                long packed = WynnBase64.toLong(cursor.take(5));
                for (int i = 0; i < 6 && packed != 0; i++) {
                    int powderId = (int) ((packed & 0x1F) - 1);
                    if (powderId >= 0) {
                        slotPowders.add(Powder.decodeId(powderId, LEGACY_POWDER_TIERS));
                    }
                    packed >>>= 5;
                }
            }
            powders.add(slotPowders);
        }
        return powders;
    }

    private static List<Integer> decodeTomes(Cursor cursor, int version) {
        List<Integer> tomeIds = new ArrayList<>();
        if (version < 6) {
            return tomeIds;
        }
        if (version < 8) {
            // One character per tome, seven slots.
            for (int i = 0; i < 7; i++) {
                tomeIds.add(WynnBase64.toInt(cursor.take(1)));
            }
            return tomeIds;
        }
        // Two characters per tome; the slot count grew as new tome types were added.
        int tomeCount = version <= 8 ? 7 : version <= 9 ? 8 : 14;
        for (int i = 0; i < tomeCount; i++) {
            tomeIds.add(WynnBase64.toInt(cursor.take(2)));
        }
        return tomeIds;
    }

    private static List<LegacyAspect> decodeAspects(Cursor cursor, int version) {
        List<LegacyAspect> aspects = new ArrayList<>();
        if (version < 11) {
            return aspects;
        }
        for (int i = 0; i < ASPECT_COUNT; i++) {
            int aspectId = WynnBase64.toInt(cursor.take(2));
            int tier = WynnBase64.toInt(cursor.take(1));
            aspects.add(aspectId == NONE_ASPECT_ID ? null : new LegacyAspect(aspectId, tier));
        }
        return aspects;
    }

    private static List<List<Powder>> emptyPowders() {
        List<List<Powder>> powders = new ArrayList<>(POWDERABLE_COUNT);
        for (int i = 0; i < POWDERABLE_COUNT; i++) {
            powders.add(List.of());
        }
        return powders;
    }

    /** Minimal forward-only reader over a fixed-width Base64 string. */
    private static final class Cursor {
        private final String text;
        private int index;

        private Cursor(String text) {
            this.text = text;
        }

        private String take(int count) {
            if (index + count > text.length()) {
                throw new IllegalArgumentException(
                        "Truncated legacy hash: wanted " + count + " characters at " + index);
            }
            String slice = text.substring(index, index + count);
            index += count;
            return slice;
        }

        private String peek(int count) {
            return peekAt(0, count);
        }

        private String peekAt(int offset, int count) {
            int start = index + offset;
            if (start + count > text.length()) {
                return "";
            }
            return text.substring(start, start + count);
        }

        private String rest() {
            String slice = text.substring(Math.min(index, text.length()));
            index = text.length();
            return slice;
        }
    }

    /** Convenience for callers that only need the skill point array shape. */
    public static Integer[] emptySkillPoints() {
        Integer[] skillPoints = new Integer[SKILL_POINT_COUNT];
        Arrays.fill(skillPoints, null);
        return skillPoints;
    }
}
