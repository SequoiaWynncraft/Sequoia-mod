package com.seqwawa.seq.wynnbuilder;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.wynnbuilder.calc.BuildStats;
import com.seqwawa.seq.wynnbuilder.calc.IdentificationRolls;
import com.seqwawa.seq.wynnbuilder.codec.BuildCodec;
import com.seqwawa.seq.wynnbuilder.codec.CraftedCodec;
import com.seqwawa.seq.wynnbuilder.codec.LegacyBuildCodec;
import com.seqwawa.seq.wynnbuilder.codec.WynnBuilderLinks;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataRepository;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The builder's working state: the current build, the data it resolves against, and derived stats.
 *
 * <p>Shared between the builder and crafter screens so a build survives navigating away and back.
 * Statistics are recomputed lazily and cached, since a screen renders every frame but the build only
 * changes on interaction.
 */
public final class WynnBuilderSession {
    private static final WynnBuilderSession INSTANCE = new WynnBuilderSession();

    private final WynnDataRepository repository = WynnDataRepository.getInstance();

    private WynnDataSet data;
    private WynnBuild build;
    private CraftedItem craft;
    // The best roll, which is what the website shows and therefore what a shared link means. A
    // build is a target to aim for, not a lottery ticket: quoting its average would understate
    // every number against the site by the same margin and make the two impossible to compare.
    private IdentificationRolls.RollMode rollMode = IdentificationRolls.RollMode.BEST;

    private BuildStats cachedStats;
    private com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation cachedEvaluation;
    private boolean statsDirty = true;
    private String message = "";
    private boolean messageIsError;

    private com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState abilityTreeState;
    private String abilityTreeClass;
    private final java.util.Set<String> enabledToggles = new java.util.LinkedHashSet<>();
    private final Map<String, Integer> sliderValues = new java.util.LinkedHashMap<>();
    /** Raid buffs the player has switched on, by name. */
    private final java.util.Set<String> enabledRaidBuffs = new java.util.LinkedHashSet<>();
    /** Chosen level per powder special, by name. */
    private final Map<String, Integer> powderSpecialLevels = new java.util.LinkedHashMap<>();
    /** Boosts coming from other players, such as a totem or war scream. */
    private final java.util.Set<String> enabledExternalBoosts = new java.util.LinkedHashSet<>();

    public static WynnBuilderSession getInstance() {
        return INSTANCE;
    }

    private WynnBuilderSession() {}

    /**
     * A session backed by an already-parsed data set, bypassing the download.
     *
     * <p>Only the parity harness uses this: it lets the whole pipeline run outside Minecraft so a
     * discrepancy against the website can be reproduced and bisected offline. Goes away with
     * {@link WynnBuilderDiagnostics}.
     */
    static WynnBuilderSession offline(WynnDataSet dataSet) {
        WynnBuilderSession session = new WynnBuilderSession();
        session.adoptData(dataSet);
        return session;
    }

    /** Starts loading the newest data set if it is not already available. */
    public CompletableFuture<WynnDataSet> ensureData() {
        if (data != null) {
            return CompletableFuture.completedFuture(data);
        }
        return repository.loadLatest().thenApply(loaded -> {
            adoptData(loaded);
            return loaded;
        });
    }

    private synchronized void adoptData(WynnDataSet loaded) {
        this.data = loaded;
        if (build == null) {
            build = newBuild(loaded);
        }
        if (craft == null) {
            craft = defaultCraft(loaded);
        }
        statsDirty = true;
    }

    private WynnBuild newBuild(WynnDataSet dataSet) {
        return new WynnBuild(
                repository.versions().indexOf(dataSet.version()) >= 0
                        ? repository.versions().indexOf(dataSet.version())
                        : repository.versions().latestIndex(),
                dataSet.encodingConsts().maxLevel(),
                dataSet.encodingConsts().tomeCount(),
                dataSet.encodingConsts().aspectCount());
    }

    private CraftedItem defaultCraft(WynnDataSet dataSet) {
        return dataSet.recipes().isEmpty()
                ? CraftedItem.empty(0)
                : CraftedItem.empty(dataSet.recipes().get(0).id());
    }

    public WynnDataSet data() {
        return data;
    }

    public boolean isReady() {
        return data != null && build != null;
    }

    public String status() {
        return repository.status();
    }

    public boolean isLoading() {
        return repository.isLoading();
    }

    public WynnBuild build() {
        return build;
    }

    public CraftedItem craft() {
        return craft;
    }

    public void setCraft(CraftedItem craft) {
        this.craft = craft;
    }

    public IdentificationRolls.RollMode rollMode() {
        return rollMode;
    }

    public void cycleRollMode() {
        rollMode = rollMode.next();
        statsDirty = true;
    }

    /** Marks derived state stale after a build edit. */
    public void invalidate() {
        statsDirty = true;
    }

    /** Cached statistics, recomputed only when the build changed. */
    public BuildStats stats() {
        if (data == null || build == null) {
            return null;
        }
        if (statsDirty || cachedStats == null) {
            // Two passes: some abilities scale off the build's own totals, so a first pass without
            // them produces the stats the second pass reads. One extra pass settles it; iterating
            // further would chase a fixed point for a bonus that is small by construction.
            var firstPass = abilityTreeEvaluation(Map.of());
            double skillPointBoost =
                    com.seqwawa.seq.wynnbuilder.calc.ExternalBoosts.skillPointMultiplier(enabledExternalBoosts);
            BuildStats provisional = BuildStats.compute(
                    build, data, rollMode, withBuffs(firstPass.statBonuses()), skillPointBoost);
            // Major identifications can rewrite a spell's own properties, so they have to be known
            // before the spells are assembled rather than merged into the stats afterwards.
            var propertyModifiers = com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine
                    .collectPropertyModifiers(majorIdAbilities(provisional));
            var secondPass = abilityTreeEvaluation(provisional.identifications(), propertyModifiers);
            Map<String, Integer> combined = withBuffs(secondPass.statBonuses());
            // Major identifications carry ability effects of their own, from gear and raid buffs
            // alike, and they read the totals the earlier pass produced.
            majorIdEffects(provisional, combined.keySet())
                    .forEach((key, value) -> combined.merge(key, value, Integer::sum));
            cachedStats = BuildStats.compute(build, data, rollMode, combined, skillPointBoost);
            cachedEvaluation = secondPass;
            statsDirty = false;
        }
        return cachedStats;
    }

    /**
     * The build's statistics with one slot emptied, for comparing what a piece contributes.
     *
     * <p>Computed on a copy so the working build is untouched.
     */
    public BuildStats statsWithout(EquipmentSlot slot) {
        if (data == null || build == null) {
            return null;
        }
        WynnBuild without = build.copy();
        without.setEquipment(slot, BuildEquipment.none());
        without.powders(slot).clear();
        return BuildStats.compute(without, data, rollMode, withBuffs(Map.of()));
    }

    // ------------------------------------------------------------------ ability tree

    /**
     * The class the build plays as, taken from the equipped weapon.
     *
     * <p>Ability trees and aspects are per class, so with no weapon there is no tree to show.
     */
    public String playerClass() {
        if (data == null || build == null) {
            return null;
        }
        if (build.equipment(EquipmentSlot.WEAPON) instanceof BuildEquipment.Normal normal) {
            WynnItem weapon = data.item(normal.itemId());
            if (weapon != null) {
                return com.seqwawa.seq.wynnbuilder.atree.AbilityTree.classForWeaponType(weapon.type());
            }
        }
        return null;
    }

    /**
     * The ability tree selection for the current class.
     *
     * <p>Rebuilt when the weapon changes class, decoding the bits carried by the build so a shared
     * link's tree survives the trip.
     */
    public com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState abilityTreeState() {
        String playerClass = playerClass();
        if (playerClass == null || data == null) {
            return null;
        }
        if (abilityTreeState == null || !playerClass.equals(abilityTreeClass)) {
            var tree = data.abilityTree(playerClass);
            if (tree == null || tree.isEmpty()) {
                return null;
            }
            abilityTreeState = new com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState(tree);
            abilityTreeClass = playerClass;
            decodeAbilityTreeBits(tree);
        }
        // Refreshed every time rather than only on creation, so changing the build's level moves the
        // budget with it.
        abilityTreeState.setAbilityPoints(
                com.seqwawa.seq.wynnbuilder.atree.AbilityTree.abilityPointsForLevel(build.level()));
        return abilityTreeState;
    }

    private void decodeAbilityTreeBits(com.seqwawa.seq.wynnbuilder.atree.AbilityTree tree) {
        var bits = build.abilityTreeBits();
        if (bits == null || bits.length() == 0 || tree.codecRoot() == null) {
            return;
        }
        try {
            // Decoding consumes the vector, so rewind in case these bits were already read once.
            bits.seek(0);
            var decoded = com.seqwawa.seq.wynnbuilder.codec.AbilityTreeCodec.decode(tree.codecRoot(), bits);
            abilityTreeState.setActive(decoded);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("[WynnBuilder] Ability tree bits could not be decoded.", exception);
        }
    }

    /** Re-encodes the current selection into the build so it is written to the link. */
    public void syncAbilityTreeToBuild() {
        var state = abilityTreeState();
        if (state == null || state.tree().codecRoot() == null) {
            return;
        }
        build.setAbilityTreeBits(com.seqwawa.seq.wynnbuilder.codec.AbilityTreeCodec.encode(
                state.tree().codecRoot(), state::isActive));
        statsDirty = true;
    }

    /** The evaluation behind the cached stats, so callers see the same spells the stats used. */
    public com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation abilityTreeEvaluation() {
        stats();
        return cachedEvaluation == null
                ? com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation.empty()
                : cachedEvaluation;
    }

    private com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation abilityTreeEvaluation(
            Map<String, Integer> buildStats) {
        return abilityTreeEvaluation(buildStats, Map.of());
    }

    private com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation abilityTreeEvaluation(
            Map<String, Integer> buildStats, Map<String, double[]> propertyModifiers) {
        var state = abilityTreeState();
        if (state == null) {
            return com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.Evaluation.empty();
        }
        return com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.evaluate(
                state, sliderValues, enabledToggles, buildStats, propertyModifiers);
    }

    /** Drops the cached tree so the next access rebuilds it from the build's bits. */
    private void resetAbilityTree() {
        abilityTreeState = null;
        abilityTreeClass = null;
        enabledToggles.clear();
        sliderValues.clear();
    }

    /**
     * Combines the ability bonuses with the toggled raid buffs and powder specials.
     *
     * <p>All three are temporary boosts rather than gear, so they are applied together at the same
     * point in the calculation.
     */
    private Map<String, Integer> withBuffs(Map<String, Integer> abilityBonuses) {
        Map<String, Integer> combined = new java.util.LinkedHashMap<>(abilityBonuses);
        for (String name : enabledRaidBuffs) {
            var buff = com.seqwawa.seq.wynnbuilder.calc.RaidBuffs.byName(name);
            if (buff != null) {
                buff.stats().forEach((key, value) -> combined.merge(key, value, Integer::sum));
            }
        }
        com.seqwawa.seq.wynnbuilder.calc.PowderSpecials.statsFor(powderSpecialLevels)
                .forEach((key, value) -> combined.merge(key, value, Integer::sum));
        com.seqwawa.seq.wynnbuilder.calc.ExternalBoosts.statsFor(enabledExternalBoosts)
                .forEach((key, value) -> combined.merge(key, value, Integer::sum));
        return combined;
    }

    /** Major identifications the toggled raid buffs bring, on top of the ones from gear. */
    public java.util.List<String> buffMajorIds() {
        java.util.List<String> majorIds = new java.util.ArrayList<>();
        for (String name : enabledRaidBuffs) {
            var buff = com.seqwawa.seq.wynnbuilder.calc.RaidBuffs.byName(name);
            if (buff != null) {
                majorIds.addAll(buff.majorIds());
            }
        }
        return majorIds;
    }

    /** The stat effects of every major identification the build has. */
    /** The ability definitions every major identification on the build contributes. */
    private java.util.List<com.google.gson.JsonObject> majorIdAbilities(BuildStats provisional) {
        java.util.List<com.google.gson.JsonObject> abilities = new java.util.ArrayList<>();
        if (data == null || provisional == null) {
            return abilities;
        }
        java.util.List<String> names = new java.util.ArrayList<>(provisional.majorIds());
        names.addAll(buffMajorIds());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                // A major ID granted twice still only applies once.
                continue;
            }
            var entry = data.majorIds().get(name);
            if (entry != null) {
                abilities.addAll(entry.abilities());
            }
        }
        return abilities;
    }

    private Map<String, Integer> majorIdEffects(BuildStats provisional, java.util.Set<String> ignored) {
        java.util.List<String> names = new java.util.ArrayList<>(provisional.majorIds());
        names.addAll(buffMajorIds());
        if (names.isEmpty() || data == null) {
            return Map.of();
        }
        return com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine.applyAbilityEffects(
                majorIdAbilities(provisional), sliderValues, enabledToggles, provisional.identifications());
    }

    public java.util.Set<String> enabledExternalBoosts() {
        return enabledExternalBoosts;
    }

    public void toggleExternalBoost(String id) {
        if (!enabledExternalBoosts.remove(id)) {
            enabledExternalBoosts.add(id);
        }
        statsDirty = true;
    }

    public java.util.Set<String> enabledRaidBuffs() {
        return enabledRaidBuffs;
    }

    public void toggleRaidBuff(String name) {
        if (!enabledRaidBuffs.remove(name)) {
            enabledRaidBuffs.add(name);
        }
        statsDirty = true;
    }

    public Map<String, Integer> powderSpecialLevels() {
        return powderSpecialLevels;
    }

    /** Steps a powder special through its levels and back to off. */
    public void cyclePowderSpecial(String name, int level) {
        if (level <= 0) {
            powderSpecialLevels.remove(name);
        } else {
            powderSpecialLevels.put(name, level);
        }
        statsDirty = true;
    }

    /** Sets the value of an ability slider, such as the number of hits landed. */
    public void setSliderValue(String name, int value) {
        if (value <= 0) {
            sliderValues.remove(name);
        } else {
            sliderValues.put(name, value);
        }
        statsDirty = true;
    }

    public java.util.Set<String> enabledToggles() {
        return enabledToggles;
    }

    public Map<String, Integer> sliderValues() {
        return sliderValues;
    }

    public void toggleAbilityToggle(String name) {
        if (!enabledToggles.remove(name)) {
            enabledToggles.add(name);
        }
        statsDirty = true;
    }

    public String message() {
        return message;
    }

    public boolean messageIsError() {
        return messageIsError;
    }

    public void setMessage(String message, boolean isError) {
        this.message = message == null ? "" : message;
        this.messageIsError = isError;
    }

    public void clearBuild() {
        if (data != null) {
            build = newBuild(data);
            resetAbilityTree();
            statsDirty = true;
            setMessage("Build cleared", false);
        }
    }

    // ------------------------------------------------------------------ links

    /** The shareable builder URL for the current build. */
    public String exportBuildLink() {
        if (data == null || build == null) {
            return null;
        }
        String hash = BuildCodec.encode(build, data.encodingConsts(), data::recipeType);
        return WynnBuilderLinks.buildUrl(hash);
    }

    /** The shareable crafter URL for the current craft. */
    public String exportCraftLink() {
        if (data == null || craft == null) {
            return null;
        }
        String recipeType = data.recipeType(craft.recipeId());
        String hash = CraftedCodec.encodeToBase64(craft, CraftedItem.isWeaponType(recipeType));
        return WynnBuilderLinks.craftUrl(hash);
    }

    /**
     * Imports a pasted build link or bare hash.
     *
     * <p>Runs asynchronously: a link written against an older data version needs that version's
     * encoding constants, which may have to be fetched. Only the constants are needed, not the whole
     * data set, because item IDs are stable between versions.
     */
    public void importBuildLink(String text) {
        if (data == null) {
            setMessage("Item data is still loading", true);
            return;
        }
        BuildLinkImporter.LinkTarget target = BuildLinkImporter.inspect(text);
        if (target == null) {
            setMessage("That does not look like a WynnBuilder build link", true);
            return;
        }

        String linkVersion = target.legacy() ? null : repository.versions().byIndex(target.versionIndex());
        CompletableFuture<com.seqwawa.seq.wynnbuilder.data.EncodingConsts> constants =
                linkVersion == null || linkVersion.equals(data.version())
                        ? CompletableFuture.completedFuture(data.encodingConsts())
                        : repository.encodingConsts(linkVersion);

        setMessage("Importing...", false);
        constants
                .exceptionally(throwable -> data.encodingConsts())
                .thenAccept(consts -> applyImport(text, consts, linkVersion));
    }

    private synchronized void applyImport(
            String text, com.seqwawa.seq.wynnbuilder.data.EncodingConsts consts, String linkVersion) {
        BuildLinkImporter.Result result = BuildLinkImporter.importBuild(text, consts, data);
        if (!result.success()) {
            setMessage(result.message(), true);
            return;
        }
        build = result.build();
        resetAbilityTree();
        statsDirty = true;
        boolean olderVersion = linkVersion != null && !linkVersion.equals(data.version());
        setMessage(olderVersion ? result.message() + " (data " + linkVersion + ")" : result.message(), false);
    }

    /** Imports a pasted crafter link or bare hash. */
    public boolean importCraftLink(String text) {
        if (data == null) {
            setMessage("Data is still loading", true);
            return false;
        }
        WynnBuilderLinks.ParsedLink parsed = WynnBuilderLinks.parse(text);
        if (!parsed.isValid()) {
            setMessage("That does not look like a WynnBuilder link", true);
            return false;
        }
        try {
            CraftedItem decoded = parsed.hash().startsWith("CR-") || parsed.hash().startsWith("1")
                    ? CraftedCodec.decodeLegacy(parsed.hash())
                    : CraftedCodec.decodeBase64(parsed.hash(), data::recipeType);
            if (decoded == null) {
                decoded = CraftedCodec.decodeLegacy(parsed.hash());
            }
            if (decoded == null) {
                setMessage("Unrecognised craft link", true);
                return false;
            }
            craft = decoded;
            setMessage("Imported craft", false);
            return true;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[WynnBuilder] Could not import craft link.", exception);
            setMessage("Could not read that craft link", true);
            return false;
        }
    }

}
