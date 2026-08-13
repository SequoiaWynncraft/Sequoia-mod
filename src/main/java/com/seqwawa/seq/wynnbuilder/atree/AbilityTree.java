package com.seqwawa.seq.wynnbuilder.atree;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seqwawa.seq.wynnbuilder.codec.AbilityTreeCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A class ability tree: its nodes, the parent/child graph, and the traversal the encoding depends on.
 *
 * <p>Child order is load-bearing. The encoding walks children depth-first and writes one bit each, so
 * children must be visited in the order the data file lists them — that is, a parent's children
 * appear in raw array order, not sorted by ID or grid position.
 */
public final class AbilityTree {
    /** Ability points available at the level cap. */
    public static final int DEFAULT_ABILITY_POINTS = 50;

    /**
     * Ability points granted at each level, indexed by level.
     *
     * <p>A table rather than a formula because the game's is not regular: the gaps between levels
     * that grant a point widen and narrow as the curve goes up. Index 0 is a placeholder so a level
     * indexes itself, and anything past the end is capped at the table's last value.
     */
    private static final int[] ABILITY_POINTS_BY_LEVEL = {
        0,
        1, 2, 2, 3, 3, 4, 4, 5, 5, 6,
        6, 7, 8, 8, 9, 9, 10, 11, 11, 12,
        12, 13, 14, 14, 15, 16, 16, 17, 17, 18,
        18, 19, 19, 20, 20, 20, 21, 21, 22, 22,
        23, 23, 23, 24, 24, 25, 25, 26, 26, 27,
        27, 28, 28, 29, 29, 30, 30, 31, 31, 32,
        32, 33, 33, 34, 34, 34, 35, 35, 35, 36,
        36, 36, 37, 37, 37, 38, 38, 38, 38, 39,
        39, 39, 39, 40, 40, 40, 40, 41, 41, 41,
        41, 42, 42, 42, 42, 43, 43, 43, 43, 44,
        44, 44, 44, 45, 45, 45, 46, 46, 46, 47,
        47, 47, 48, 48, 48, 49, 49, 49, 49, 50,
        50
    };

    /** How many ability points a character of this level has to spend. */
    public static int abilityPointsForLevel(int level) {
        if (level < 1) {
            return 0;
        }
        return level >= ABILITY_POINTS_BY_LEVEL.length
                ? ABILITY_POINTS_BY_LEVEL[ABILITY_POINTS_BY_LEVEL.length - 1]
                : ABILITY_POINTS_BY_LEVEL[level];
    }

    private final String playerClass;
    private final List<AbilityNode> nodes;
    private final Map<Integer, AbilityNode> byId;
    private final Map<Integer, List<Integer>> childIds;
    private final AbilityNode root;

    private AbilityTree(String playerClass, List<AbilityNode> nodes) {
        this.playerClass = playerClass;
        this.nodes = List.copyOf(nodes);

        Map<Integer, AbilityNode> index = new LinkedHashMap<>();
        for (AbilityNode node : nodes) {
            index.put(node.id(), node);
        }
        this.byId = Map.copyOf(index);

        // Children in raw array order: iterate nodes in order and append to each parent's list.
        Map<Integer, List<Integer>> children = new LinkedHashMap<>();
        for (AbilityNode node : nodes) {
            for (int parentId : node.parentIds()) {
                children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(node.id());
            }
        }
        Map<Integer, List<Integer>> immutableChildren = new LinkedHashMap<>();
        children.forEach((key, value) -> immutableChildren.put(key, List.copyOf(value)));
        this.childIds = Map.copyOf(immutableChildren);

        AbilityNode found = null;
        for (AbilityNode node : nodes) {
            if (node.isRoot()) {
                found = node;
                break;
            }
        }
        this.root = found;
    }

    public String playerClass() {
        return playerClass;
    }

    public List<AbilityNode> nodes() {
        return nodes;
    }

    public AbilityNode node(int id) {
        return byId.get(id);
    }

    public AbilityNode root() {
        return root;
    }

    public boolean isEmpty() {
        return nodes.isEmpty() || root == null;
    }

    public List<Integer> childrenOf(int id) {
        return childIds.getOrDefault(id, List.of());
    }

    /** Adapter letting the codec walk this tree without depending on the data model. */
    public AbilityTreeCodec.Node codecRoot() {
        return root == null ? null : new CodecNode(root.id());
    }

    private final class CodecNode implements AbilityTreeCodec.Node {
        private final int id;

        private CodecNode(int id) {
            this.id = id;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public List<? extends AbilityTreeCodec.Node> children() {
            List<CodecNode> children = new ArrayList<>();
            for (int childId : childrenOf(id)) {
                children.add(new CodecNode(childId));
            }
            return children;
        }
    }

    /** Parses every class tree from {@code atree.json}. */
    public static Map<String, AbilityTree> parseAll(String json) {
        Map<String, AbilityTree> trees = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return trees;
        }
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) {
            return trees;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonArray()) {
                continue;
            }
            List<AbilityNode> nodes = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    AbilityNode node = AbilityNode.parse(element.getAsJsonObject());
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
            trees.put(entry.getKey(), new AbilityTree(entry.getKey(), nodes));
        }
        return trees;
    }

    /**
     * The class that wields a weapon type.
     *
     * <p>Aspects and ability trees are per class, and the equipped weapon is what identifies it.
     */
    public static String classForWeaponType(String weaponType) {
        if (weaponType == null) {
            return null;
        }
        return switch (weaponType.toLowerCase(Locale.ROOT)) {
            case "bow" -> "Archer";
            case "spear" -> "Warrior";
            case "wand" -> "Mage";
            case "dagger" -> "Assassin";
            case "relik" -> "Shaman";
            default -> null;
        };
    }
}
