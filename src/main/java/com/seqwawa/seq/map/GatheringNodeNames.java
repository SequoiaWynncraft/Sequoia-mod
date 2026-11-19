package com.seqwawa.seq.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Names the Wynncraft API's shared Dernic and Molten nodes by what they gather.
 *
 * <p>The gathering-node route (API v3.7.2 on 2026-11-19) calls all four Dernic node kinds plain
 * {@code DERNIC}, and Molten Eel fishing spots {@code MOLTEN} like Molten ore. No field says which
 * profession such a node is, so this gives each one the name the bundled file uses
 * ({@code DERNIC_ORE}, {@code DERNIC_TREE}, {@code DERNIC_CROPS}, {@code DERNIC_FISH},
 * {@code MOLTEN} or {@code MOLTEN_EEL}):
 * <ol>
 * <li>A wall or corner node is ore: ores are the only nodes set into walls and corners (all
 * 1,449 such nodes on 2026-11-19).</li>
 * <li>Any other node takes the name of the bundled node at its position, or else of the nearest
 * bundled node of its material. Each Dernic kind grows in a region of its own, and the nearest
 * bundled node named 451 of the 453 such nodes right when each was left out in turn.</li>
 * <li>Without bundled nodes to compare, the plain name stays, which {@link GatheringProfession}
 * reads as ore.</li>
 * </ol>
 * scripts/gathering_nodes.py applies the same rules when it regenerates the bundled file.
 */
final class GatheringNodeNames {
    /** The API's shared names, each with its ore's name and the names the bundled file tells apart. */
    private static final Map<String, Shared> SHARED = Map.of(
            "DERNIC", new Shared("DERNIC_ORE", Set.of("DERNIC_ORE", "DERNIC_TREE", "DERNIC_CROPS", "DERNIC_FISH")),
            "MOLTEN", new Shared("MOLTEN", Set.of("MOLTEN", "MOLTEN_EEL")));

    private record Shared(String ore, Set<String> names) {}

    private GatheringNodeNames() {}

    /** The nodes with every shared name replaced, as an immutable list; the rest are unchanged. */
    static List<GatheringNode> resolve(List<GatheringNode> nodes, List<GatheringNode> bundled) {
        Map<String, List<GatheringNode>> known = new HashMap<>();
        SHARED.forEach((material, shared) -> known.put(material, bundled.stream()
                .filter(node -> shared.names().contains(node.resource()))
                .toList()));
        List<GatheringNode> resolved = new ArrayList<>(nodes.size());
        for (GatheringNode node : nodes) {
            String name = name(node, known);
            resolved.add(name.equals(node.resource()) ? node
                    : new GatheringNode(node.x(), node.y(), node.z(), node.angle(), node.type(), name, node.level()));
        }
        return List.copyOf(resolved);
    }

    private static String name(GatheringNode node, Map<String, List<GatheringNode>> known) {
        Shared shared = SHARED.get(node.resource());
        if (shared == null) {
            return node.resource();
        }
        if (node.type().equals("CORNER") || node.type().equals("WALL")) {
            return shared.ore();
        }
        GatheringNode nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (GatheringNode candidate : known.get(node.resource())) {
            long dx = candidate.x() - node.x();
            long dy = candidate.y() - node.y();
            long dz = candidate.z() - node.z();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null ? node.resource() : nearest.resource();
    }
}
