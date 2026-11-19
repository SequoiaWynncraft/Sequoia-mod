package com.seqwawa.seq.ui;

import com.seqwawa.seq.model.LeafEvents;
import java.util.List;

/** The board's package-private views of Leaf's answers, for the contract test in another package. */
public final class LeafContractViews {

    private LeafContractViews() {}

    /** The Recent list's first row for these events, or null when it shows none. */
    public static Object firstRecentRow(LeafEvents events) {
        List<RecentLeaf.Row> rows = RecentLeaf.rows(events, RecentLeaf.SHOWN);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** The Recent list's row for one event, or null when the list leaves it out. */
    public static Object recentRow(LeafEvents.Event event) {
        return RecentLeaf.row(event);
    }
}
