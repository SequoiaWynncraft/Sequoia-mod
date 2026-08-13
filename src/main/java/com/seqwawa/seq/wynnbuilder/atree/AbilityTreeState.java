package com.seqwawa.seq.wynnbuilder.atree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which abilities are selected, and whether that selection is legal.
 *
 * <p>A selection must stay connected to the root: every active node needs an active parent. That is
 * not merely a UI nicety — the encoding only descends into active nodes, so a disconnected selection
 * cannot be represented in a link at all.
 */
public final class AbilityTreeState {
    private final AbilityTree tree;
    private final Set<Integer> active = new HashSet<>();
    private int abilityPoints = AbilityTree.DEFAULT_ABILITY_POINTS;

    public AbilityTreeState(AbilityTree tree) {
        this.tree = tree;
        if (tree != null && tree.root() != null) {
            // The root is always taken and costs nothing.
            active.add(tree.root().id());
        }
    }

    public AbilityTree tree() {
        return tree;
    }

    public Set<Integer> active() {
        return Set.copyOf(active);
    }

    public boolean isActive(int id) {
        return active.contains(id);
    }

    public int abilityPoints() {
        return abilityPoints;
    }

    public void setAbilityPoints(int abilityPoints) {
        this.abilityPoints = abilityPoints;
    }

    /** Points spent on the current selection; the root is free. */
    public int spentPoints() {
        int spent = 0;
        for (int id : active) {
            AbilityNode node = tree.node(id);
            if (node != null && !node.isRoot()) {
                spent += node.cost();
            }
        }
        return spent;
    }

    public int remainingPoints() {
        return abilityPoints - spentPoints();
    }

    /** Replaces the selection wholesale, used when decoding a link. */
    public void setActive(Set<Integer> ids) {
        active.clear();
        active.addAll(ids);
        if (tree != null && tree.root() != null) {
            active.add(tree.root().id());
        }
    }

    public void clear() {
        active.clear();
        if (tree != null && tree.root() != null) {
            active.add(tree.root().id());
        }
    }

    /** Why an ability cannot be taken, or {@code null} when it can. */
    public String blockedReason(int id) {
        AbilityNode node = tree == null ? null : tree.node(id);
        if (node == null) {
            return "Unknown ability";
        }
        if (node.isRoot()) {
            return null;
        }
        boolean parentActive = false;
        for (int parentId : node.parentIds()) {
            if (active.contains(parentId)) {
                parentActive = true;
                break;
            }
        }
        if (!parentActive) {
            return "Requires a connected parent ability";
        }
        for (int dependencyId : node.dependencyIds()) {
            if (!active.contains(dependencyId)) {
                AbilityNode dependency = tree.node(dependencyId);
                return "Requires " + (dependency == null ? "another ability" : dependency.displayName());
            }
        }
        for (int blockerId : node.blockerIds()) {
            if (active.contains(blockerId)) {
                AbilityNode blocker = tree.node(blockerId);
                return "Conflicts with " + (blocker == null ? "another ability" : blocker.displayName());
            }
        }
        if (node.cost() > remainingPoints()) {
            return "Not enough ability points";
        }
        if (node.archetypeRequirement() > 0 && archetypeCount(node.archetype()) < node.archetypeRequirement()) {
            return "Requires " + node.archetypeRequirement() + " " + node.archetype() + " abilities";
        }
        return null;
    }

    public boolean canActivate(int id) {
        return blockedReason(id) == null;
    }

    /** Number of active abilities belonging to an archetype. */
    public int archetypeCount(String archetype) {
        if (archetype == null) {
            return 0;
        }
        int count = 0;
        for (int id : active) {
            AbilityNode node = tree.node(id);
            if (node != null && archetype.equals(node.archetype())) {
                count++;
            }
        }
        return count;
    }

    /** Active ability counts per archetype, for the tree header. */
    public Map<String, Integer> archetypeCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int id : active) {
            AbilityNode node = tree.node(id);
            if (node != null && node.archetype() != null) {
                counts.merge(node.archetype(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Turns an ability on, or off along with everything that depended on it.
     *
     * @return whether the selection changed
     */
    public boolean toggle(int id) {
        AbilityNode node = tree == null ? null : tree.node(id);
        if (node == null || node.isRoot()) {
            return false;
        }
        if (active.contains(id)) {
            deactivate(id);
            return true;
        }
        if (!canActivate(id)) {
            return false;
        }
        active.add(id);
        return true;
    }

    /**
     * Removes an ability and anything that would be left dangling.
     *
     * <p>Deselecting a node mid-branch would otherwise strand its descendants, producing a selection
     * that cannot be encoded.
     */
    private void deactivate(int id) {
        active.remove(id);
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Integer> orphans = new ArrayList<>();
            for (int activeId : active) {
                AbilityNode node = tree.node(activeId);
                if (node == null || node.isRoot()) {
                    continue;
                }
                boolean connected = false;
                for (int parentId : node.parentIds()) {
                    if (active.contains(parentId)) {
                        connected = true;
                        break;
                    }
                }
                boolean dependenciesMet = true;
                for (int dependencyId : node.dependencyIds()) {
                    if (!active.contains(dependencyId)) {
                        dependenciesMet = false;
                        break;
                    }
                }
                if (!connected || !dependenciesMet) {
                    orphans.add(activeId);
                }
            }
            if (!orphans.isEmpty()) {
                active.removeAll(orphans);
                changed = true;
            }
        }
    }

    /** Whether every active node is reachable from the root through active nodes. */
    public boolean isConnected() {
        if (tree == null || tree.root() == null) {
            return active.isEmpty();
        }
        Set<Integer> reachable = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(tree.root().id());
        reachable.add(tree.root().id());
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int childId : tree.childrenOf(current)) {
                if (active.contains(childId) && reachable.add(childId)) {
                    queue.add(childId);
                }
            }
        }
        return reachable.containsAll(active);
    }
}
