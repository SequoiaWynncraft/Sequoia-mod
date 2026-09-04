package com.seqwawa.seq.wynnbuilder.live;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsAbilityTreeAccess;
import com.seqwawa.seq.integrations.WynntilsEquipmentAccess;
import com.seqwawa.seq.integrations.WynntilsSkillPointAccess;
import com.seqwawa.seq.integrations.WynntilsTomeAccess;
import com.seqwawa.seq.wynnbuilder.WynnBuilderSession;
import com.seqwawa.seq.wynnbuilder.calc.BuildEvaluation;
import com.seqwawa.seq.wynnbuilder.calc.BuildStats;
import com.seqwawa.seq.wynnbuilder.calc.DamageSources;
import com.seqwawa.seq.wynnbuilder.calc.IdentificationRolls;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the equipped build's numbers ready for the inventory panel.
 *
 * <p>Split across two threads, on the line between what is safe to touch. Reading the player's gear
 * walks live item stacks and so happens on the game thread; everything downstream of that works on
 * an immutable snapshot and runs off it, because a full evaluation is two ability tree passes and
 * two aggregations, which is far too much to repeat on a frame.
 *
 * <p>Nothing is recomputed until something actually changes. The nine equipped stacks are compared
 * against the ones the current numbers were built from, so browsing the inventory, moving unrelated
 * items around or simply leaving it open costs nothing at all.
 */
public final class EquippedBuildSession {
    private static final EquippedBuildSession INSTANCE = new EquippedBuildSession();

    private final WynnBuilderSession builder = WynnBuilderSession.getInstance();

    private List<ItemStack> observedStacks = List.of();
    private long observedRevisions = Long.MIN_VALUE;
    private boolean dataRequested;

    private volatile State state = State.empty();
    private volatile boolean evaluating;
    private volatile boolean auditing;

    private EquippedBuildSession() {}

    public static EquippedBuildSession getInstance() {
        return INSTANCE;
    }

    /**
     * Everything the panel draws.
     *
     * @param status a line to show instead of numbers when there are none yet
     */
    public record State(
            EquippedBuild.Snapshot snapshot,
            BuildStats stats,
            DamageSources.Report report,
            GearAudit.Result audit,
            String status) {

        public static State empty() {
            return new State(EquippedBuild.Snapshot.empty(), null, null, null, "Reading your gear...");
        }

        public boolean hasNumbers() {
            return stats != null && report != null;
        }

        public EquippedBuild.Readiness readiness() {
            return snapshot == null ? EquippedBuild.Readiness.none() : snapshot.readiness();
        }

        State withAudit(GearAudit.Result audit) {
            return new State(snapshot, stats, report, audit, status);
        }

        static State of(String status) {
            return new State(EquippedBuild.Snapshot.empty(), null, null, null, status);
        }
    }

    public State state() {
        return state;
    }

    public boolean isEvaluating() {
        return evaluating;
    }

    public boolean isAuditing() {
        return auditing;
    }

    /**
     * Refreshes if anything about the character changed.
     *
     * <p>Called from the render path while the panel is up, so the common case has to be cheap: it
     * compares nine item stacks against a revision counter and returns.
     */
    public void refresh() {
        if (!WynntilsEquipmentAccess.isAvailable()) {
            state = State.of("Wynntils is required to read equipped gear");
            return;
        }
        WynnDataSet data = data();
        if (data == null) {
            state = State.of(builder.isLoading() ? builder.status() : "Loading item data...");
            return;
        }
        // Free, and it can only add what the player has already looked at.
        WynntilsAbilityTreeAccess.harvestFromWynntils();

        List<ItemStack> stacks = WynntilsEquipmentAccess.equippedStacks();
        long revisions = revisions();
        if (!evaluating && (revisions != observedRevisions || changed(stacks))) {
            observedStacks = stacks.stream().map(ItemStack::copy).toList();
            observedRevisions = revisions;
            evaluate(data);
        }
    }

    /** Forces a recomputation, for when the player has just scanned something. */
    public void invalidate() {
        observedStacks = List.of();
        observedRevisions = Long.MIN_VALUE;
    }

    /**
     * Measures which equipped piece is costing the most damage.
     *
     * <p>Nineteen evaluations, so it runs off-thread and lands whenever it lands; the panel shows
     * the previous answer until then.
     */
    public void requestAudit() {
        if (auditing || state.snapshot() == null || state.snapshot().isEmpty()) {
            return;
        }
        WynnDataSet data = data();
        if (data == null) {
            return;
        }
        EquippedBuild.Snapshot snapshot = state.snapshot();
        auditing = true;
        CompletableFuture.supplyAsync(() -> GearAudit.run(data, snapshot))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        SeqClient.LOGGER.warn("[WynnBuilder] Gear audit failed.", error);
                    } else if (state.snapshot() == snapshot) {
                        state = state.withAudit(result);
                    }
                    // An audit that finished after the player changed a piece describes gear they
                    // are no longer wearing, so it is dropped rather than shown against the new
                    // numbers.
                    auditing = false;
                });
    }

    /**
     * Asks Wynntils to read the character sheet and the ability tree.
     *
     * <p>The only thing here that talks to the server, and the only thing that closes the player's
     * open container, so it is never called on its own initiative.
     */
    public void requestScan() {
        WynntilsSkillPointAccess.requestScan();
        WynntilsAbilityTreeAccess.requestFullScan();
        invalidate();
    }

    private void evaluate(WynnDataSet data) {
        // Reading gear walks live item stacks, so it stays on the game thread; the snapshot it
        // produces is immutable and knows nothing about Minecraft, which is what makes the rest safe
        // to hand to another thread.
        EquippedBuild.Snapshot snapshot = EquippedBuild.assemble(
                data,
                WynntilsEquipmentAccess.read(name -> itemId(data, name)),
                WynntilsSkillPointAccess.snapshot(),
                WynntilsAbilityTreeAccess.snapshot(),
                WynntilsTomeAccess.snapshot());

        if (snapshot.isEmpty()) {
            state = State.of("Equip your gear to see this build's damage");
            return;
        }

        evaluating = true;
        CompletableFuture.supplyAsync(() -> {
                    BuildEvaluation evaluation = BuildEvaluation.compute(
                            snapshot.build(),
                            data,
                            IdentificationRolls.RollMode.BEST,
                            snapshot.treeState(),
                            Map.of(),
                            java.util.Set.of(),
                            snapshot.tomeBonuses(),
                            List.of(),
                            1.0);
                    DamageSources.Report report = evaluation.damage(snapshot.build(), data);
                    return new State(snapshot, evaluation.stats(), report, null, "");
                })
                .whenComplete((computed, error) -> {
                    if (error != null) {
                        SeqClient.LOGGER.warn("[WynnBuilder] Equipped build could not be evaluated.", error);
                        state = State.of("This build could not be measured");
                        invalidate();
                    } else {
                        // The audit describes the previous gear, so it goes with it.
                        state = computed;
                    }
                    evaluating = false;
                });
    }

    private WynnDataSet data() {
        WynnDataSet data = builder.data();
        if (data == null && !dataRequested) {
            dataRequested = true;
            builder.ensureData().whenComplete((loaded, error) -> {
                if (error != null) {
                    SeqClient.LOGGER.warn("[WynnBuilder] Item data could not be loaded.", error);
                    dataRequested = false;
                }
            });
        }
        return data;
    }

    private static int itemId(WynnDataSet data, String name) {
        WynnItem item = data.itemByName(name);
        return item == null ? -1 : item.id();
    }

    private boolean changed(List<ItemStack> stacks) {
        if (stacks.size() != observedStacks.size()) {
            return true;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(stacks.get(i), observedStacks.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything read from a menu rather than from the gear, as one comparable number.
     *
     * <p>Those change without any stack changing — the player opens their ability tree, or their
     * tomes — so comparing equipment alone would never notice. This runs on every frame the panel is
     * up, which is why it counts revisions instead of comparing the readings themselves.
     */
    private static long revisions() {
        long skills = WynntilsSkillPointAccess.snapshot().sum();
        return (long) WynntilsAbilityTreeAccess.revision() * 1_000_003L
                + (long) WynntilsTomeAccess.revision() * 1_009L
                + skills;
    }
}
