package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class WynnPartySyncManagerTest {

    @Test
    void preInitLeaveEventIsIgnored() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Guest has left the party!"));

        assertFalse(isInitialized(manager));
    }

    @Test
    void authoritativeMembersSnapshotReplacesRollbackGhostMembers() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("You have successfully created a party."));
        manager.onSystemChat(Component.literal("Guildsman has joined your party, say hello!"));
        manager.onSystemChat(Component.literal("C0INZS has joined your party, say hello!"));
        manager.onSystemChat(Component.literal("Orphion_ has joined your party, say hello!"));
        manager.onSystemChat(Component.literal("Party members: SophiaChan, and Guildsman"));

        assertEquals(List.of("SophiaChan", "Guildsman"), memberUsernames(manager));
        assertNull(leaderUsername(manager));
        assertEquals(true, isActive(manager));
        assertEquals(true, isInitialized(manager));
    }

    @Test
    void authoritativeMembersSnapshotParsesFourMemberNormalizedFormInOrder() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, C0INZS, and Orphion_"));

        assertEquals(List.of("SophiaChan", "Guildsman", "C0INZS", "Orphion_"), memberUsernames(manager));
        assertNull(leaderUsername(manager));
        assertEquals(true, isActive(manager));
        assertEquals(true, isInitialized(manager));
    }

    @Test
    void joinBeforeLeavePreservesReplacementUntilPartyReturnsWithinCapacity() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: cela41, tung, tungtung, and tungtungtung"));
        manager.onSystemChat(Component.literal("sahur has joined your party, say hello!"));

        assertEquals(List.of("cela41", "tung", "tungtung", "tungtungtung", "sahur"), memberUsernames(manager));
        assertEquals(true, WynnPartySyncManager.shouldDeferOverCapacitySnapshot(memberUsernames(manager).size(), 4));

        manager.onSystemChat(Component.literal("tung has left the party!"));

        assertEquals(List.of("cela41", "tungtung", "tungtungtung", "sahur"), memberUsernames(manager));
        assertEquals(false, WynnPartySyncManager.shouldDeferOverCapacitySnapshot(memberUsernames(manager).size(), 4));
    }

    @Test
    void authoritativeMembersSnapshotClearsLeaderWhenCurrentLeaderMissing() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, and C0INZS"));
        manager.onSystemChat(Component.literal("Guildsman is now the Party Leader!"));
        manager.onSystemChat(Component.literal("Party members: Orphion_, and SophiaChan"));

        assertEquals(List.of("Orphion_", "SophiaChan"), memberUsernames(manager));
        assertNull(leaderUsername(manager));
    }

    @Test
    void authoritativeMembersSnapshotPreservesExplicitLeader() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, and C0INZS"));
        manager.onSystemChat(Component.literal("Guildsman is now the Party Leader!"));
        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, and C0INZS"));

        assertEquals("Guildsman", leaderUsername(manager));
    }

    @Test
    void observedMemberLookupIsCaseInsensitive() {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, and Guildsman"));

        assertEquals(true, manager.isObservedMember("sophiachan"));
        assertFalse(manager.isObservedMember("SomeoneElse"));
    }

    @Test
    void manualScanNoPartyResponseProducesInactiveSnapshot() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();
        manager.onSystemChat(Component.literal("Party members: SophiaChan, and Guildsman"));
        setBooleanField(manager, "manualScanPending", true);

        manager.onSystemChat(Component.literal("You must be in a party to use this."));

        assertEquals(true, isInitialized(manager));
        assertFalse(isActive(manager));
        assertEquals(List.of(), memberUsernames(manager));
        assertFalse(booleanField(manager, "manualScanPending"));
        assertEquals(true, booleanField(manager, "manualSnapshotReady"));
    }

    @Test
    void unrelatedNoPartyResponseDoesNotOverwriteObservedState() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();
        manager.onSystemChat(Component.literal("Party members: SophiaChan, and Guildsman"));

        manager.onSystemChat(Component.literal("You must be in a party to use this."));

        assertEquals(true, isActive(manager));
        assertEquals(List.of("SophiaChan", "Guildsman"), memberUsernames(manager));
    }

    private boolean isInitialized(WynnPartySyncManager manager) throws Exception {
        Object observedState = observedState(manager);
        Field initializedField = observedState.getClass().getDeclaredField("initialized");
        initializedField.setAccessible(true);
        return initializedField.getBoolean(observedState);
    }

    private boolean isActive(WynnPartySyncManager manager) throws Exception {
        Object observedState = observedState(manager);
        Field activeField = observedState.getClass().getDeclaredField("active");
        activeField.setAccessible(true);
        return activeField.getBoolean(observedState);
    }

    @SuppressWarnings("unchecked")
    private List<String> memberUsernames(WynnPartySyncManager manager) throws Exception {
        Object observedState = observedState(manager);
        Field memberUsernamesField = observedState.getClass().getDeclaredField("memberUsernames");
        memberUsernamesField.setAccessible(true);
        return List.copyOf((Set<String>) memberUsernamesField.get(observedState));
    }

    private String leaderUsername(WynnPartySyncManager manager) throws Exception {
        Object observedState = observedState(manager);
        Field leaderField = observedState.getClass().getDeclaredField("leaderUsername");
        leaderField.setAccessible(true);
        return (String) leaderField.get(observedState);
    }

    private boolean booleanField(WynnPartySyncManager manager, String fieldName) throws Exception {
        Field field = WynnPartySyncManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(manager);
    }

    private void setBooleanField(WynnPartySyncManager manager, String fieldName, boolean value) throws Exception {
        Field field = WynnPartySyncManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(manager, value);
    }

    private Object observedState(WynnPartySyncManager manager) throws Exception {
        Field field = WynnPartySyncManager.class.getDeclaredField("observedState");
        field.setAccessible(true);
        return field.get(manager);
    }
}
