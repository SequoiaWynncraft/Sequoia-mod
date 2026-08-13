package com.seqwawa.seq.wynnbuilder.codec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Ability tree bits: a depth-first walk of the tree emitting one bit per node.
 *
 * <p>The traversal only descends into active nodes, so the bit stream describes the selected
 * subtree rather than the whole tree. That makes it compact but also order-sensitive: children must
 * be visited in the same order when encoding and decoding, and a node reachable by several paths is
 * visited only the first time. This algorithm is only well defined for connected selections.
 */
public final class AbilityTreeCodec {

    private AbilityTreeCodec() {}

    /** A node of the tree, supplied by the caller so this class stays independent of the data model. */
    public interface Node {
        int id();

        List<? extends Node> children();
    }

    /** Encodes the active set, walking from {@code root}. */
    public static BitVector encode(Node root, Predicate<Integer> isActive) {
        BitVector vector = new BitVector();
        traverseEncode(root, isActive, new HashSet<>(), vector);
        return vector;
    }

    private static void traverseEncode(Node head, Predicate<Integer> isActive, Set<Integer> visited, BitVector out) {
        for (Node child : head.children()) {
            if (!visited.add(child.id())) {
                continue;
            }
            if (isActive.test(child.id())) {
                out.appendBit(true);
                traverseEncode(child, isActive, visited, out);
            } else {
                out.appendBit(false);
            }
        }
    }

    /**
     * Decodes the active set from {@code bits}.
     *
     * <p>Reads stop early when the stream runs out, which happens for trailing Base64 padding or a
     * selection that ends before the tree does.
     */
    public static Set<Integer> decode(Node root, BitVector bits) {
        Set<Integer> active = new HashSet<>();
        traverseDecode(root, bits, new HashSet<>(), active);
        return active;
    }

    private static void traverseDecode(Node head, BitVector bits, Set<Integer> visited, Set<Integer> active) {
        for (Node child : head.children()) {
            if (!visited.add(child.id())) {
                continue;
            }
            if (!bits.hasRemaining(1)) {
                return;
            }
            if (bits.readBit()) {
                active.add(child.id());
                traverseDecode(child, bits, visited, active);
            }
        }
    }
}
