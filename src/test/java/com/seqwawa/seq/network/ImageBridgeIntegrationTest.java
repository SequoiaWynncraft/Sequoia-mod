package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.images.BridgeMedia;
import com.seqwawa.seq.images.ImagePreviewSettings;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageBridgeIntegrationTest {
    private static final String BARE = "https://cdn.discordapp.com/attachments/1/2/test.gif";
    private static final String SIGNED = BARE + "?ex=abc&is=def&hm=123";
    private static final String VIDEO = "https://static.klipy.com/ii/test/preview.mp4";

    @TempDir
    Path directory;

    private ConnectionHarness harness;
    private final AtomicReference<ConnectionManager.DiscordChatMessage> received = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        harness = new ConnectionHarness(directory);
        ConnectionManager.onDiscordChat(received::set);
        harness.connection();
        ImagePreviewSettings.setting().setValue(true);
        ImagePreviewSettings.mediaSetting().reset();
        BridgeMedia.clear();
    }

    @AfterEach
    void tearDown() {
        harness.close();
        ImagePreviewSettings.setting().reset();
        ImagePreviewSettings.mediaSetting().reset();
        BridgeMedia.clear();
    }

    private String payload(String type, String text, String media) {
        var json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("username", "BridgeTester");
        json.addProperty("discord_id", "123");
        if (text != null) json.addProperty("message", text);
        var urls = new JsonArray();
        urls.add(media);
        json.add("media_urls", urls);
        return json.toString();
    }

    @Test
    void signedAttachmentReachesTheChatHandlerWithItsSender() throws Exception {
        harness.receive(payload("discord_chat", BARE, SIGNED));

        assertNotNull(received.get());
        assertEquals(SIGNED, received.get().message());
        assertEquals("BridgeTester", received.get().username());
        assertEquals("123", received.get().discordId());
        assertEquals(URI.create(SIGNED), BridgeMedia.resolve(URI.create(BARE)));
    }

    @Test
    void hiddenMediaDropsMediaOnlyMessagesBeforeDispatchAndPreservesTextAndIdentity() throws Exception {
        ImagePreviewSettings.mediaSetting().setValue(false);
        assertFalse(ImagePreviewSettings.enabled());
        harness.receive(payload("discord_chat", BARE, SIGNED));
        assertNull(received.get());
        harness.receive(payload("discord_media", null, VIDEO));
        assertNull(received.get());
        harness.receive(payload("discord_chat", "hello " + BARE + " https://example.com/help", SIGNED));
        assertEquals("hello https://example.com/help", received.get().message());
        assertEquals("BridgeTester", received.get().username());
        assertEquals("123", received.get().discordId());
    }

    @Test
    void mediaVisibilityPersistsWithoutLosingTheSavedPreviewOptIn() throws Exception {
        Files.writeString(directory.resolve("config.json"), "{\"chat.image_previews\":true}");
        ConfigManager config = harness.config;
        config.register(ImagePreviewSettings.mediaSetting());
        config.register(ImagePreviewSettings.setting());
        config.load();
        assertTrue(ImagePreviewSettings.enabled(), "legacy preview opt-in must still work");
        ImagePreviewSettings.mediaSetting().setValue(false);
        config.save();
        ImagePreviewSettings.mediaSetting().reset();
        ImagePreviewSettings.setting().reset();
        config.load();
        assertTrue(ImagePreviewSettings.hidden());
        assertFalse(ImagePreviewSettings.enabled());
        assertTrue(ImagePreviewSettings.setting().getValue());
        ImagePreviewSettings.mediaSetting().setValue(true);
        assertTrue(ImagePreviewSettings.enabled());
        ImagePreviewSettings.setting().setValue(false);
        String original = payload("discord_chat", BARE, SIGNED);
        harness.receive(original);
        assertEquals(BARE, received.get().message(), "links mode must preserve the original URL");
    }

    @Test
    void mediaUpdatesRespectIgnoredBridgeUsers() throws Exception {
        harness.config.addIgnoredBridgeUser("BridgeTester");
        harness.receive(payload("discord_media", null, VIDEO));
        assertNull(received.get());

        harness.config.removeIgnoredBridgeUser("BridgeTester");
        harness.receive(payload("discord_media", null, VIDEO));
        assertNotNull(received.get());
        assertEquals(VIDEO, received.get().message());
        assertEquals("BridgeTester", received.get().username());
    }
}
