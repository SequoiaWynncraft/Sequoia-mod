package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeEditorScreenTest {
    @TempDir Path themes;
    private Setting.ChoiceSetting previousSetting;
    private Theme previousTheme;
    private ThemeEditorScreen editor;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() throws Exception {
        previousSetting = SeqClient.themeSetting;
        previousTheme = ThemeManager.currentTheme();
        var initialize = ThemeManager.class.getDeclaredMethod("initialize", Path.class);
        initialize.setAccessible(true);
        initialize.invoke(null, themes);
        SeqClient.themeSetting = new Setting.ChoiceSetting("theme", "ui", "default", List.of("default"), v -> {});
        editor = newEditor();
    }

    @AfterEach
    void restore() {
        if (editor != null) editor.removed();
        SeqClient.themeSetting = previousSetting;
        ThemeManager.previewTheme(previousTheme);
    }

    @Test
    void galleryShowsDraftWithoutSelectingOrSavingItAndBackPreservesEdits() throws Exception {
        Color configured = ThemeManager.color(UiColor.ACCENT_PRIMARY);
        Color draft = new Color(12, 145, 98, 173);
        invoke("updateColor", new Class<?>[]{UiColor.class, Color.class}, UiColor.ACCENT_PRIMARY, draft);
        assertEquals(configured, ThemeManager.color(UiColor.ACCENT_PRIMARY));

        invoke("showWidgets", new Class<?>[]{boolean.class}, true);
        assertEquals(draft, ThemeManager.color(UiColor.ACCENT_PRIMARY));
        assertEquals("default", SeqClient.themeSetting.getValue());
        try (var files = java.nio.file.Files.list(themes)) { assertEquals(0, files.count()); }

        editor.onClose();
        assertEquals(configured, ThemeManager.color(UiColor.ACCENT_PRIMARY));
        invoke("showWidgets", new Class<?>[]{boolean.class}, true);
        assertEquals(draft, ThemeManager.color(UiColor.ACCENT_PRIMARY));
        editor.removed();
        assertEquals(configured, ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void returningToEditorKeepsAnAlreadyEnabledLivePreview() throws Exception {
        Color configured = ThemeManager.color(UiColor.ACCENT_PRIMARY);
        Color draft = new Color(42, 170, 220);
        invoke("updateColor", new Class<?>[]{UiColor.class, Color.class}, UiColor.ACCENT_PRIMARY, draft);
        invoke("togglePreview", new Class<?>[0]);
        invoke("showWidgets", new Class<?>[]{boolean.class}, true);
        editor.onClose();
        assertEquals(draft, ThemeManager.color(UiColor.ACCENT_PRIMARY));
        editor.removed();
        assertEquals(configured, ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    // Screen's constructor reads the client font even though these lifecycle tests never render.
    static ThemeEditorScreen newEditor() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var instance = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            instance.set(null, unsafe.allocateInstance(net.minecraft.client.Minecraft.class));
            return new ThemeEditorScreen(null);
        } finally {
            instance.set(null, previous);
        }
    }

    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        var method = ThemeEditorScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(editor, args);
    }
}
