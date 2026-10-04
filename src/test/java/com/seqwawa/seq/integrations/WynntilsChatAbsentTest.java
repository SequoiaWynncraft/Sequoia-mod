package com.seqwawa.seq.integrations;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/** Loads production chat classes with every Wynntils class genuinely inaccessible. */
class WynntilsChatAbsentTest {
    @Test
    void coreChatClassesInitializeAndSkipGuildTagsWithoutLoadingWynntils() throws Exception {
        try (NoWynntilsLoader loader = new NoWynntilsLoader()) {
            Class<?> decorator = Class.forName("com.seqwawa.seq.managers.PrivateMessageGuildTagDecorator", true, loader);
            assertFalse((Boolean) decorator.getMethod("enabled").invoke(null));
            Component message = Component.literal("PM");
            assertFalse((Boolean) decorator.getMethod("isPrivateMessage", Component.class).invoke(null, message));
            assertSame(message, decorator.getMethod("decorate", Component.class, Runnable.class)
                    .invoke(null, message, (Runnable) () -> fail("No lookup or refresh without Wynntils")));
            decorator.getMethod("tick").invoke(null);
            Class<?> mixin = Class.forName("com.seqwawa.seq.mixins.ChatComponentMixin", true, loader);
            assertDoesNotThrow(mixin::getDeclaredMethods);
            assertNotNull(mixin.getDeclaredConstructor().newInstance());
            Class<?> access = Class.forName(WynntilsChatAccess.class.getName(), true, loader);
            assertEquals("", ((CompletableFuture<?>) access.getMethod("lookupGuildTag", String.class)
                    .invoke(null, "Baptiste")).join());
            assertEquals(0, loader.optionalLoadAttempts);
        }
    }

    @Test
    void vanillaRefreshCommitsLinesWhileOptionalEventPostingIsANoOp() throws Exception {
        try (NoWynntilsLoader loader = new NoWynntilsLoader()) {
            Class<?> access = Class.forName(WynntilsChatAccess.class.getName(), true, loader);
            Method post = access.getMethod("postAddedLine", GuiMessage.class, GuiMessage.Line.class);
            Class<?> viewClass = Class.forName("com.seqwawa.seq.managers.PrivateMessageGuildTagView", true, loader);
            Object view = viewClass.getConstructor(Runnable.class).newInstance((Runnable) () -> {});
            GuiMessage message = new GuiMessage(42, Component.literal("PM"), null, null);
            AtomicReference<Component> decorated = new AtomicReference<>(message.content());
            AtomicReference<Runnable> changed = new AtomicReference<>();
            FormattedCharSequence original = FormattedCharSequence.forward("PM", Style.EMPTY);
            FormattedCharSequence replacement = FormattedCharSequence.forward("updated PM", Style.EMPTY);
            BiFunction<Component, Runnable, Component> decorate = (component, callback) -> {
                changed.set(callback);
                return decorated.get();
            };
            Function<Component, List<FormattedCharSequence>> split = component ->
                    List.of(component == message.content() ? original : replacement);
            viewClass.getMethod("wrap", GuiMessage.class, BiFunction.class, Function.class)
                    .invoke(view, message, decorate, split);
            List<GuiMessage.Line> displayed = new ArrayList<>(List.of(new GuiMessage.Line(42, original, null, true)));
            decorated.set(Component.literal("updated PM"));
            changed.get().run();
            BiConsumer<GuiMessage, GuiMessage.Line> addedLine = (addedMessage, line) -> {
                try {
                    post.invoke(null, addedMessage, line);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            };
            assertEquals(0, viewClass.getMethod("refresh", List.class, int.class, int.class, int.class, BiConsumer.class)
                    .invoke(view, displayed, 0, 10, 100, addedLine));
            assertSame(replacement, displayed.getFirst().content());
            assertEquals(42, displayed.getFirst().addedTime());
            assertEquals(0, loader.optionalLoadAttempts);
        }
    }

    private static final class NoWynntilsLoader extends URLClassLoader {
        int optionalLoadAttempts;

        NoWynntilsLoader() {
            super(new URL[] {WynntilsChatAccess.class.getProtectionDomain().getCodeSource().getLocation()},
                    WynntilsChatAbsentTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("com.wynntils.")
                        || name.equals("com.seqwawa.seq.integrations.WynntilsChatBridge")) {
                    optionalLoadAttempts++;
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("com.seqwawa.seq.")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
