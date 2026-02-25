package org.sequoia.seq.managers;

import java.util.*;

public class PartyListing {
    private static final Set<String> RAID_TYPE_SET = new HashSet<>(Arrays.asList("NOTG", "NOL", "TCC", "TNA", "ANNI"));

    public final int maxSize;
    public final List<String> tags;
    public final List<PartyMember> members = new ArrayList<>();
    public boolean expanded = false;

    public PartyListing(List<String> tags) {
        this.tags = new ArrayList<>(tags);
        this.maxSize = tags.contains("ANNI") ? 10 : 4;
    }

    public List<String> getRaidTags() {
        List<String> raids = new ArrayList<>();
        for (String tag : tags) {
            if (RAID_TYPE_SET.contains(tag)) {
                raids.add(tag);
            }
        }
        return raids;
    }

    public String displayLabel() {
        return String.join(", ", tags);
    }

    public PartyMember getLeader() {
        for (PartyMember m : members) { if (m.isLeader) return m; }
        return members.isEmpty() ? null : members.get(0);
    }
}
