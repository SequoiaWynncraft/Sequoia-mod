package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertEquals("SophiaChan", leaderUsername(manager));
        assertEquals(true, isActive(manager));
        assertEquals(true, isInitialized(manager));
    }

    @Test
    void authoritativeMembersSnapshotParsesFourMemberNormalizedFormInOrder() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, C0INZS, and Orphion_"));

        assertEquals(List.of("SophiaChan", "Guildsman", "C0INZS", "Orphion_"), memberUsernames(manager));
        assertEquals("SophiaChan", leaderUsername(manager));
        assertEquals(true, isActive(manager));
        assertEquals(true, isInitialized(manager));
    }

    @Test
    void authoritativeMembersSnapshotFallsBackLeaderToFirstMemberWhenCurrentLeaderMissing() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Party members: SophiaChan, Guildsman, and C0INZS"));
        manager.onSystemChat(Component.literal("Guildsman is now the Party Leader!"));
        manager.onSystemChat(Component.literal("Party members: Orphion_, and SophiaChan"));

        assertEquals(List.of("Orphion_", "SophiaChan"), memberUsernames(manager));
        assertEquals("Orphion_", leaderUsername(manager));
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

    private Object observedState(WynnPartySyncManager manager) throws Exception {
        Field field = WynnPartySyncManager.class.getDeclaredField("observedState");
        field.setAccessible(true);
        return field.get(manager);
    }
}
