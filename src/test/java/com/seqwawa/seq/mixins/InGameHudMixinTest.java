package com.seqwawa.seq.mixins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

class InGameHudMixinTest {
    @Test
    void chatScreenHelperRemainsMixinSafeAndRecognizesOnlyChatScreens() throws Exception {
        Method helper = InGameHudMixin.class.getDeclaredMethod("isChatScreen", Class.class);
        helper.setAccessible(true);

        assertTrue(Modifier.isPrivate(helper.getModifiers()));
        assertTrue(Modifier.isStatic(helper.getModifiers()));
        assertTrue((boolean) helper.invoke(null, ChatScreen.class));
        assertTrue((boolean) helper.invoke(null, InBedChatScreen.class));
        assertFalse((boolean) helper.invoke(null, Screen.class));
    }
}
