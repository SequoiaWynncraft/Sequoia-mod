package org.sequoia.seq.managers;

import lombok.Getter;
import org.sequoia.seq.client.SeqClient;

import java.util.ArrayList;
import java.util.List;

public class PartyFinderManager {

    @Getter
    private final List<PartyListing> parties = new ArrayList<>();
    @Getter
    private int joinedPartyIndex = -1;
    @Getter
    private int myPartyIndex = -1;
    @Getter
    private boolean isPartyLeader = false;
    private boolean hasListedParty = false;

    public PartyFinderManager() {
        buildDemoData();
    }

    // ── Accessors ──

    public boolean hasListedParty() { return hasListedParty; }

    // ── Party actions ──

    public void joinParty(int partyIndex, String role) {
        if (joinedPartyIndex >= 0) return;
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        String playerName = SeqClient.mc.getUser().getName();
        String actualRole = role != null ? role : "DPS";
        parties.get(partyIndex).members.add(new PartyMember(playerName, "assassin", actualRole, false));
        joinedPartyIndex = partyIndex;
    }

    public void leaveParty() {
        if (joinedPartyIndex < 0 || joinedPartyIndex >= parties.size()) return;
        String playerName = SeqClient.mc.getUser().getName();
        parties.get(joinedPartyIndex).members.removeIf(m -> m.name.equals(playerName));
        joinedPartyIndex = -1;
    }

    public void createParty(List<String> tags, String role) {
        String playerName = SeqClient.mc.getUser().getName();
        String actualRole = role != null ? role : "DPS";
        PartyListing newParty = new PartyListing(tags);
        newParty.members.add(new PartyMember(playerName, "assassin", actualRole, true));
        newParty.expanded = true;
        parties.add(0, newParty);
        myPartyIndex = 0;
        if (joinedPartyIndex >= 0) joinedPartyIndex++;
        isPartyLeader = true;
        hasListedParty = true;
        joinedPartyIndex = myPartyIndex;
    }

    public void updateParty(List<String> tags) {
        if (myPartyIndex < 0 || myPartyIndex >= parties.size()) return;
        PartyListing existing = parties.get(myPartyIndex);
        PartyListing updated = new PartyListing(tags);
        updated.members.addAll(existing.members);
        updated.expanded = true;
        parties.set(myPartyIndex, updated);
        isPartyLeader = true;
        hasListedParty = true;
        joinedPartyIndex = myPartyIndex;
    }

    public void delistParty() {
        if (myPartyIndex >= 0 && myPartyIndex < parties.size()) {
            parties.remove(myPartyIndex);
            if (joinedPartyIndex == myPartyIndex) {
                joinedPartyIndex = -1;
            } else if (joinedPartyIndex > myPartyIndex) {
                joinedPartyIndex--;
            }
        }
        myPartyIndex = -1;
        joinedPartyIndex = -1;
        isPartyLeader = false;
        hasListedParty = false;
    }

    public void kickMember(int partyIndex, int memberIndex) {
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size()) return;
        party.members.remove(memberIndex);
    }

    public void promoteMember(int partyIndex, int memberIndex) {
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size()) return;
        for (PartyMember pm : party.members) {
            pm.isLeader = false;
        }
        party.members.get(memberIndex).isLeader = true;
        isPartyLeader = false;
    }

    public void setRole(String role) {
        if (role == null || joinedPartyIndex < 0 || joinedPartyIndex >= parties.size()) return;
        String playerName = SeqClient.mc.getUser().getName();
        for (PartyMember m : parties.get(joinedPartyIndex).members) {
            if (m.name.equals(playerName)) {
                m.role = role;
                break;
            }
        }
    }

    public void setHasListedParty(boolean managing) {
        hasListedParty = managing && isPartyLeader;
    }

    //TODO: delete this once we actually can goon to ts

    private void buildDemoData() {
        PartyListing p1 = new PartyListing(List.of("NOTG", "Chill"));
        p1.members.add(new PartyMember("GAZTheMiner", "archer", "DPS", true));
        p1.members.add(new PartyMember("Vicvir", "warrior", "Tank", false));
        p1.expanded = true;
        parties.add(p1);

        PartyListing p2 = new PartyListing(List.of("TCC", "NOL", "Grind"));
        p2.members.add(new PartyMember("PlayerOne", "mage", "Support", true));
        p2.members.add(new PartyMember("PlayerTwo", "warrior", "DPS", false));
        parties.add(p2);

        PartyListing p3 = new PartyListing(List.of("NOL", "NOTG", "Chill"));
        p3.members.add(new PartyMember("Leader123", "assassin", "DPS", true));
        p3.members.add(new PartyMember("MemberA", "warrior", "Tank", false));
        p3.members.add(new PartyMember("MemberB", "mage", "Support", false));
        p3.members.add(new PartyMember("MemberC", "archer", "DPS", false));
        parties.add(p3);

        PartyListing p4 = new PartyListing(List.of("ANNI", "Chill"));
        p4.members.add(new PartyMember("RaidLeader", "shaman", "Support", true));
        p4.members.add(new PartyMember("DPS1", "assassin", "DPS", false));
        p4.members.add(new PartyMember("DPS2", "archer", "DPS", false));
        parties.add(p4);
    }
}
