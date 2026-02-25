package org.sequoia.seq.managers;

public class PartyMember {
    public final String name;
    public final String className;
    public String role;
    public boolean isLeader;

    public PartyMember(String name, String className, String role, boolean isLeader) {
        this.name = name;
        this.className = className;
        this.role = role;
        this.isLeader = isLeader;
    }
}
