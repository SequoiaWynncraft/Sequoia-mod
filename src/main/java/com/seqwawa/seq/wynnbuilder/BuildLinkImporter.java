package com.seqwawa.seq.wynnbuilder;

import com.seqwawa.seq.wynnbuilder.codec.BuildCodec;
import com.seqwawa.seq.wynnbuilder.codec.CraftedCodec;
import com.seqwawa.seq.wynnbuilder.codec.LegacyBuildCodec;
import com.seqwawa.seq.wynnbuilder.codec.WynnBuilderLinks;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.List;

/**
 * Turns a pasted link into a build.
 *
 * <p>Separate from the session so the whole path — link parsing, format detection, version handling
 * and the legacy conversion — can be tested without a running client.
 */
public final class BuildLinkImporter {

    private BuildLinkImporter() {}

    /** The outcome of an import attempt. */
    public record Result(WynnBuild build, String message, boolean success) {
        static Result failure(String message) {
            return new Result(null, message, false);
        }

        static Result success(WynnBuild build, String message) {
            return new Result(build, message, true);
        }
    }

    /** Which data version a hash was written against, so the caller can fetch its constants. */
    public record LinkTarget(String hash, boolean legacy, int versionIndex) {}

    /**
     * Inspects a pasted link without decoding it.
     *
     * @return the target, or {@code null} when the text is not a usable build link
     */
    public static LinkTarget inspect(String text) {
        WynnBuilderLinks.ParsedLink parsed = WynnBuilderLinks.parse(text);
        if (!parsed.isValid()) {
            return null;
        }
        String hash = parsed.hash();
        if (LegacyBuildCodec.isLegacy(hash)) {
            return new LinkTarget(hash, true, -1);
        }
        if (!BuildCodec.isBinary(hash)) {
            return null;
        }
        try {
            return new LinkTarget(hash, false, BuildCodec.peekDataVersionIndex(hash));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Decodes a link into a build.
     *
     * @param consts the encoding constants of the version the link was written with; the current
     *     data set's constants are a safe default when that version is unknown
     * @param data the data set used to resolve IDs, which need not match the link's version because
     *     item, tome and aspect IDs are stable between versions
     */
    public static Result importBuild(String text, EncodingConsts consts, WynnDataSet data) {
        if (data == null) {
            return Result.failure("Item data is still loading");
        }
        LinkTarget target = inspect(text);
        if (target == null) {
            return Result.failure("That does not look like a WynnBuilder build link");
        }
        try {
            if (target.legacy()) {
                return Result.success(
                        fromLegacy(LegacyBuildCodec.decode(target.hash()), data), "Imported build (legacy link)");
            }
            EncodingConsts effective = consts == null ? data.encodingConsts() : consts;
            WynnBuild build = BuildCodec.decode(target.hash(), effective, data::recipeType);
            return Result.success(build, "Imported build");
        } catch (RuntimeException exception) {
            return Result.failure("Could not read that link: " + describe(exception));
        }
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** Rebuilds a working build from a decoded legacy hash. */
    static WynnBuild fromLegacy(LegacyBuildCodec.LegacyBuild legacy, WynnDataSet data) {
        WynnBuild imported = new WynnBuild(
                0,
                legacy.level(),
                data.encodingConsts().tomeCount(),
                data.encodingConsts().aspectCount());
        imported.setLevel(legacy.level());

        List<EquipmentSlot> slots = EquipmentSlot.encodingOrder();
        for (int i = 0; i < slots.size() && i < legacy.equipment().size(); i++) {
            LegacyBuildCodec.LegacyEquipment piece = legacy.equipment().get(i);
            EquipmentSlot slot = slots.get(i);
            if (piece.itemId() != null && !piece.isNone()) {
                WynnItem item = data.item(piece.itemId());
                if (item != null && !item.isNoneItem()) {
                    imported.setEquipment(slot, new BuildEquipment.Normal(item.id()));
                }
            } else if (piece.craftedHash() != null) {
                CraftedItem decoded = CraftedCodec.decodeLegacy(piece.craftedHash());
                if (decoded != null) {
                    imported.setEquipment(slot, new BuildEquipment.Crafted(decoded));
                }
            }
            // Custom items are dropped: there is no custom item editor, and keeping the raw hash
            // would not survive being re-encoded.
        }

        // Legacy stores powders for the five powderable slots in helmet-to-weapon order.
        List<EquipmentSlot> powderable = slots.stream().filter(EquipmentSlot::powderable).toList();
        for (int i = 0; i < powderable.size() && i < legacy.powders().size(); i++) {
            imported.setPowders(powderable.get(i), legacy.powders().get(i));
        }

        for (int i = 0; i < legacy.skillPoints().length && i < WynnBuild.SKILL_POINT_TYPES; i++) {
            Integer value = legacy.skillPoints()[i];
            if (value != null && value != 0) {
                imported.setAssignedSkillPoint(i, value);
            }
        }

        for (int i = 0; i < legacy.tomeIds().size() && i < imported.tomeIds().size(); i++) {
            int tomeId = legacy.tomeIds().get(i);
            if (data.tome(tomeId) != null) {
                imported.tomeIds().set(i, tomeId);
            }
        }

        for (int i = 0; i < legacy.aspects().size() && i < imported.aspects().size(); i++) {
            LegacyBuildCodec.LegacyAspect aspect = legacy.aspects().get(i);
            if (aspect != null) {
                imported.aspects().set(i, new WynnBuild.AspectSelection(aspect.aspectId(), aspect.tier()));
            }
        }

        imported.setAbilityTreeBits(legacy.abilityTreeBits());
        return imported;
    }
}
