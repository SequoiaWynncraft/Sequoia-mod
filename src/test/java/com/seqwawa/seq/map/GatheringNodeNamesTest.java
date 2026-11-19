package com.seqwawa.seq.map;

import static com.seqwawa.seq.map.GatheringProfession.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatheringNodeNamesTest {
    private static final Set<String> KINDS = Set.of(
            "DERNIC_ORE", "DERNIC_TREE", "DERNIC_CROPS", "DERNIC_FISH", "MOLTEN", "MOLTEN_EEL");

    private static GatheringNode node(int x, int z, String type, String resource) {
        return new GatheringNode(x, 64, z, 90, type, resource, 105);
    }

    /** The node as the API sends it: every Dernic kind DERNIC, and Molten Eel MOLTEN. */
    private static GatheringNode asTheApiSendsIt(GatheringNode node) {
        String plain = node.resource().startsWith("DERNIC_") ? "DERNIC"
                : node.resource().equals("MOLTEN_EEL") ? "MOLTEN" : node.resource();
        return new GatheringNode(node.x(), node.y(), node.z(), node.angle(), node.type(), plain, node.level());
    }

    private static List<String> resources(List<GatheringNode> nodes) {
        return nodes.stream().map(GatheringNode::resource).toList();
    }

    @Test
    void theApisNamesAndTheOldNamesAllHaveAProfession() {
        assertEquals(WOODCUTTING, fromResource("DERNIC_TREE"));
        assertEquals(MINING, fromResource("DERNIC_ORE"));
        assertEquals(FARMING, fromResource("DERNIC_CROPS"));
        assertEquals(FISHING, fromResource("DERNIC_FISH"));
        assertEquals(FISHING, fromResource("MOLTEN_EEL"));
        assertEquals(FISHING, fromResource(" molten eel "));
        assertEquals(MINING, fromResource("MOLTEN"));
        assertEquals(MINING, fromResource("DERNIC"), "A Dernic node nothing named is read as ore, its largest kind.");
        assertEquals(UNKNOWN, fromResource("DERNIC_SOMETHING"));
    }

    @Test
    void wallAndCornerNodesAreOreEvenBesideAFishingSpot() {
        List<GatheringNode> bundled = List.of(node(0, 0, "NODE", "MOLTEN_EEL"), node(0, 1, "NODE", "DERNIC_FISH"));
        List<GatheringNode> named = GatheringNodeNames.resolve(
                List.of(node(0, 0, "WALL", "MOLTEN"), node(0, 1, "CORNER", "DERNIC")), bundled);
        assertEquals(List.of("MOLTEN", "DERNIC_ORE"), resources(named));
    }

    @Test
    void otherNodesTakeTheNameAtTheirPositionOrOfTheNearestNodeOfTheirMaterial() {
        List<GatheringNode> bundled = List.of(
                node(0, 0, "NODE", "DERNIC_FISH"),
                node(100, 0, "NODE", "DERNIC_TREE"),
                node(60, 0, "NODE", "OAK"),
                node(0, 100, "NODE", "MOLTEN_EEL"),
                node(0, 140, "NODE", "MOLTEN"),
                node(10, 0, "NODE", "DERNIC"));
        List<GatheringNode> api = List.of(
                node(100, 0, "NODE", "DERNIC"),
                // The oak and the bundled plain DERNIC are nearer, but neither names a Dernic kind.
                node(40, 0, "NODE", "DERNIC"),
                node(0, 125, "NODE", "MOLTEN"),
                node(0, 110, "NODE", "MOLTEN"),
                node(5, 5, "NODE", "OAK"));
        List<GatheringNode> named = GatheringNodeNames.resolve(api, bundled);
        assertEquals(List.of("DERNIC_TREE", "DERNIC_FISH", "MOLTEN", "MOLTEN_EEL", "OAK"), resources(named));
        assertEquals(List.of(WOODCUTTING, FISHING, MINING, FISHING, WOODCUTTING),
                named.stream().map(GatheringNode::profession).toList());
        assertEquals(new GatheringNode(100, 64, 0, 90, "NODE", "DERNIC_TREE", 105), named.getFirst(),
                "Only the name changes.");
        assertSame(api.getLast(), named.getLast());
        assertThrows(UnsupportedOperationException.class, named::clear);
    }

    @Test
    void withoutBundledNodesThePlainNamesStayAndReadAsOre() {
        List<GatheringNode> named = GatheringNodeNames.resolve(
                List.of(node(0, 0, "NODE", "DERNIC"), node(0, 1, "NODE", "MOLTEN")), List.of());
        assertEquals(List.of("DERNIC", "MOLTEN"), resources(named));
        assertEquals(List.of(MINING, MINING), named.stream().map(GatheringNode::profession).toList());
    }

    @Test
    void theBundledFileGetsEveryNameBackAndNamesNodesItLacksByTheirNeighbours() throws IOException {
        List<GatheringNode> bundled;
        try (var input = GatheringNodeNamesTest.class.getResourceAsStream("/assets/seq/map/gathering-nodes.json")) {
            assertNotNull(input);
            bundled = GatheringNodeService.parseNodes(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(bundled, GatheringNodeNames.resolve(
                bundled.stream().map(GatheringNodeNamesTest::asTheApiSendsIt).toList(), bundled));

        // A node the bundled file lacks: leave each one out in turn and name it from the rest.
        List<GatheringNode> named = bundled.stream().filter(node -> KINDS.contains(node.resource())).toList();
        int tried = 0;
        List<GatheringNode> missed = new ArrayList<>();
        for (GatheringNode node : named) {
            if (!node.type().equals("NODE")) continue;
            List<GatheringNode> rest = named.stream().filter(other -> other != node).toList();
            tried++;
            if (!GatheringNodeNames.resolve(List.of(asTheApiSendsIt(node)), rest).getFirst().equals(node)) {
                missed.add(node);
            }
        }
        assertTrue(tried > 400, "The bundled file has the API's Dernic and Molten nodes: " + tried);
        assertTrue(missed.size() * 50 <= tried, "Nearest-neighbour names 98% right; it missed " + missed);
    }
}
