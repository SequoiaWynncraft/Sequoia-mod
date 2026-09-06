package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PartyMemberClassIconTest {
    @Test
    void cachedMemberPicksUpLateClassDetectionAndClassChanges() {
        var member = new PartyMember(new Member("local", PartyRole.DPS, null, Instant.EPOCH), "local");
        var currentClass = new AtomicReference<String>();
        assertNull(member.classIconKey(uuid -> currentClass.get()));
        currentClass.set("mage");
        assertEquals("mage", member.classIconKey(uuid -> currentClass.get()));
        currentClass.set("shaman");
        assertEquals("shaman", member.classIconKey(uuid -> currentClass.get()));
    }

    @Test
    void remoteMembersUseTheBackendClassAndLocalDetectionCanSupersedeIt() {
        var member = new PartyMember(
                new Member("member", PartyRole.HEALER, WynnClassType.ARCHER, Instant.EPOCH), "leader");
        assertEquals("archer", member.classIconKey(uuid -> null));
        assertEquals("warrior", member.classIconKey(uuid -> "warrior"));
        assertEquals("archer", member.classIconKey(uuid -> null));
    }

    @Test
    void unknownPlayersAndEmptyReservationsDoNotInventAnIcon() {
        var unknown = new PartyMember(new Member("unknown", PartyRole.TANK, null, Instant.EPOCH), "leader");
        assertNull(unknown.classIconKey(uuid -> null));
        var reserved = PartyMember.reserved(new ReservedSlot(null, PartyRole.OTHER, Instant.EPOCH));
        assertNull(reserved.classIconKey(uuid -> "mage"));
        var observed = PartyMember.reserved(new ReservedSlot("local", "ObservedPlayer", PartyRole.OTHER, Instant.EPOCH));
        assertEquals("mage", observed.classIconKey(uuid -> "mage"));
    }
}
