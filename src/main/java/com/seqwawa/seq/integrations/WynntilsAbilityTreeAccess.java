package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.wynntils.core.components.Models;
import com.wynntils.models.abilitytree.AbilityTreeModel;
import com.wynntils.models.abilitytree.type.AbilityTreeInfo;
import com.wynntils.models.abilitytree.type.AbilityTreeNodeState;
import com.wynntils.models.abilitytree.type.AbilityTreeSkillNode;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.containers.containers.AbilityTreeContainer;
import com.wynntils.utils.type.Pair;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Learns which abilities the player has taken by watching them browse their own ability tree.
 *
 * <p>Wynntils can fetch the tree on demand, but its query closes whatever container is open and
 * walks the seven pages by sending clicks. Reading the pages the player is already looking at costs
 * nothing and sends nothing, which is why that is the default here: {@link
 * com.wynntils.models.abilitytree.parser.AbilityTreeParser} is public, so the same item parsing
 * Wynntils performs during its query works just as well on a container the player opened.
 *
 * <p>The trade is coverage. The tree scrolls, and a player who never scrolls to the bottom leaves
 * pages unread, so observations accumulate across openings rather than replacing each other and the
 * caller is told how much of the tree has been seen. Editing an ability necessarily means looking at
 * it, so a change is picked up the moment it is made.
 */
public final class WynntilsAbilityTreeAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";

    /** Every Wynncraft container menu ends with the player's own 36 inventory slots. */
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    /**
     * The page a node was seen on, which the parser wants but this class does not.
     *
     * <p>Nodes are keyed by name here, so the page only has to be a value the parser accepts.
     */
    private static final int UNKNOWN_PAGE = 1;

    /** Ability name to whether it is taken, for the class it was read from. */
    private static final Map<String, Boolean> STATES = new LinkedHashMap<>();

    /**
     * How often the open container is re-read.
     *
     * <p>Parsing a page walks the lore of every node on it, and this is called from the render path,
     * so it is deliberately not per-frame. A few reads a second is far quicker than a player can
     * click an ability.
     */
    private static final long OBSERVE_INTERVAL_MILLIS = 250;

    /**
     * Wynntils' own copy is consulted rarely: {@code getNodeState} scans the whole parsed tree per
     * node, so harvesting is quadratic and has no business happening on a frame.
     */
    private static final long HARVEST_INTERVAL_MILLIS = 2000;

    private static ClassType observedClass;
    private static long lastObservedAt;
    private static long lastHarvestedAt;

    /** Bumped whenever anything here changes, so callers can notice in constant time. */
    private static int revision;

    private WynntilsAbilityTreeAccess() {}

    /** What has been learned about the current class's tree. */
    public record Snapshot(String playerClass, Map<String, Boolean> nodeStates) {
        public Snapshot {
            nodeStates = Map.copyOf(nodeStates);
        }

        public static Snapshot empty() {
            return new Snapshot(null, Map.of());
        }

        public boolean isEmpty() {
            return nodeStates.isEmpty();
        }

        /** The abilities the player has taken, by name. */
        public java.util.Set<String> unlocked() {
            return nodeStates.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID);
    }

    /**
     * Records whatever ability nodes the open container is showing.
     *
     * <p>Safe to call on every container and every frame: it returns immediately unless the ability
     * tree is what is open.
     */
    public static void observe(AbstractContainerMenu menu) {
        if (menu == null || !isAvailable()) {
            return;
        }
        try {
            if (!(Models.Container.getCurrentContainer() instanceof AbilityTreeContainer)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastObservedAt < OBSERVE_INTERVAL_MILLIS) {
                return;
            }
            lastObservedAt = now;
            forgetOnClassChange();

            int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SLOTS;
            for (int index = 0; index < containerSlots; index++) {
                Slot slot = menu.slots.get(index);
                ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
                if (stack.isEmpty()
                        || !AbilityTreeModel.ABILITY_TREE_PARSER.isNodeItem(stack, index)) {
                    continue;
                }
                Pair<AbilityTreeSkillNode, AbilityTreeNodeState> parsed =
                        AbilityTreeModel.ABILITY_TREE_PARSER.parseNodeFromItem(
                                stack, UNKNOWN_PAGE, index, STATES.size() + 1);
                if (parsed != null && parsed.key() != null && parsed.key().name() != null) {
                    Boolean previous = STATES.put(
                            normalise(parsed.key().name()), parsed.value() == AbilityTreeNodeState.UNLOCKED);
                    if (previous == null || previous != (parsed.value() == AbilityTreeNodeState.UNLOCKED)) {
                        revision++;
                    }
                }
            }
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Ability tree page could not be read", e);
        }
    }

    /**
     * Takes whatever Wynntils itself has already parsed, if anything.
     *
     * <p>Free and silent, so it is worth trying alongside the passive read: another feature, or a
     * scan the player asked for, may have filled Wynntils' own copy. A tree it has never parsed
     * reports every node as locked, which is indistinguishable from a character with no abilities at
     * all — impossible past the first level — so an all-locked answer is discarded rather than
     * merged, where it would otherwise overwrite what watching the player had established.
     */
    public static void harvestFromWynntils() {
        if (!isAvailable()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHarvestedAt < HARVEST_INTERVAL_MILLIS) {
            return;
        }
        lastHarvestedAt = now;
        try {
            ClassType classType = Models.Character.getClassType();
            if (classType == null || classType == ClassType.NONE) {
                return;
            }
            AbilityTreeInfo tree = Models.AbilityTree.getAbilityTree(classType);
            if (tree == null || tree.nodes().isEmpty()) {
                return;
            }
            Map<String, Boolean> harvested = new LinkedHashMap<>();
            boolean anyUnlocked = false;
            for (AbilityTreeSkillNode node : tree.nodes()) {
                if (node == null || node.name() == null) {
                    continue;
                }
                boolean unlocked = Models.AbilityTree.getNodeState(node) == AbilityTreeNodeState.UNLOCKED;
                anyUnlocked |= unlocked;
                harvested.put(normalise(node.name()), unlocked);
            }
            if (!anyUnlocked) {
                return;
            }
            forgetOnClassChange();
            if (!STATES.equals(harvested)) {
                STATES.putAll(harvested);
                revision++;
            }
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Ability tree could not be harvested", e);
        }
    }

    /**
     * Asks Wynntils to walk the whole tree.
     *
     * <p>The one path here that talks to the server: it closes the open container and pages through
     * the ability menu. Reserved for an explicit request, since everything else in this class is
     * passive.
     */
    public static boolean requestFullScan() {
        if (!isAvailable()) {
            return false;
        }
        try {
            AbilityTreeModel.ABILITY_TREE_CONTAINER_QUERIES.updateParsedAbilityTree();
            return true;
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Ability tree scan could not be started", e);
            return false;
        }
    }

    public static Snapshot snapshot() {
        if (!isAvailable()) {
            return Snapshot.empty();
        }
        try {
            forgetOnClassChange();
            ClassType classType = Models.Character.getClassType();
            String name = classType == null || classType == ClassType.NONE ? null : classType.getName();
            return new Snapshot(name, Collections.unmodifiableMap(new LinkedHashMap<>(STATES)));
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Ability tree state could not be read", e);
            return Snapshot.empty();
        }
    }

    /** A value that changes whenever the observations do, for cheap staleness checks. */
    public static int revision() {
        return revision;
    }

    /** Drops everything when the player switches character, since trees are per class. */
    private static void forgetOnClassChange() {
        ClassType classType = Models.Character.getClassType();
        if (classType != observedClass) {
            STATES.clear();
            observedClass = classType;
            revision++;
        }
    }

    /** Ability names are compared across two data sources, so case and spacing are normalised. */
    static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
