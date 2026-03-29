package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.lang.reflect.Field;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class WynnPartySyncManagerTest {

    @Test
    void preInitLeaveEventIsIgnored() throws Exception {
        WynnPartySyncManager manager = new WynnPartySyncManager();

        manager.onSystemChat(Component.literal("Guest has left the party!"));

        assertFalse(isInitialized(manager));
    }
    private boolean isInitialized(WynnPartySyncManager manager) throws Exception {
        Object observedState = observedState(manager);
        Field initializedField = observedState.getClass().getDeclaredField("initialized");
        initializedField.setAccessible(true);
        return initializedField.getBoolean(observedState);
    }

    private Object observedState(WynnPartySyncManager manager) throws Exception {
        Field field = WynnPartySyncManager.class.getDeclaredField("observedState");
        field.setAccessible(true);
        return field.get(manager);
    }
}
