package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.model.AchievementAnnouncementClaim;
import com.seqwawa.seq.model.AchievementAnnouncementClaim.Announcement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AchievementAnnouncementManagerTest {
    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final AtomicReference<UUID> player = new AtomicReference<>(UUID.randomUUID());
    private final AtomicLong now = new AtomicLong(1000);
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> commands = new ArrayList<>();
    private final List<Runnable> callbacks = new ArrayList<>();
    private CompletableFuture<AchievementAnnouncementClaim> response = new CompletableFuture<>();
    private final AchievementAnnouncementManager manager = new AchievementAnnouncementManager(
            () -> { calls.incrementAndGet(); return response; }, ready::get, player::get, now::get, callbacks::add, commands::add);

    @Test
    void sendsBackendMessageOnClientThreadWithGuildCommand() {
        manager.tick();
        response.complete(earned());
        assertTrue(commands.isEmpty());
        flush();
        assertEquals(List.of("g Baptiste reached Diamond (1,000 raids) in TNA!"), commands);
        manager.tick();
        assertEquals(1, calls.get());
    }

    @Test
    void baselineIsSilentAndChecksAreThrottled() {
        manager.tick();
        response.complete(new AchievementAnnouncementClaim(null));
        flush();
        now.addAndGet(29_999);
        manager.tick();
        assertEquals(1, calls.get());
        assertTrue(commands.isEmpty());
        now.incrementAndGet();
        manager.tick();
        assertEquals(2, calls.get());
    }

    @Test
    void disconnectBeforeResponseDropsCommandEvenWithoutAnotherTick() {
        manager.tick();
        ready.set(false);
        response.complete(earned());
        flush();
        assertTrue(commands.isEmpty());
    }

    @Test
    void accountSwitchBeforeResponseCannotAnnounceForOtherPlayer() {
        manager.tick();
        player.set(UUID.randomUUID());
        response.complete(earned());
        flush();
        assertTrue(commands.isEmpty());
    }

    @Test
    void reconnectInvalidatesOldResponseAndDoesNotBlockNewRequest() {
        manager.tick();
        var old = response;
        ready.set(false);
        manager.tick();
        response = new CompletableFuture<>();
        ready.set(true);
        manager.tick();
        old.complete(earned());
        flush();
        assertTrue(commands.isEmpty());
        manager.tick();
        assertEquals(2, calls.get());
        response.complete(earned());
        flush();
        assertEquals(1, commands.size());
    }

    @Test
    void neverSendsSameEventTwiceAndSpacesMultipleAnnouncements() {
        var earned = earned();
        manager.tick();
        response.complete(earned);
        flush();
        now.addAndGet(3000);
        manager.tick();
        flush();
        assertEquals(1, commands.size());
        assertEquals(2, calls.get());
    }

    @Test
    void failedRequestsBackOffAndDoNotSend() {
        manager.tick();
        response.completeExceptionally(new IllegalStateException("offline"));
        flush();
        manager.tick();
        assertEquals(1, calls.get());
        assertTrue(commands.isEmpty());
        now.addAndGet(30_000);
        manager.tick();
        assertEquals(2, calls.get());
    }

    @Test
    void invalidCommandsAreRejected() {
        assertFalse(AchievementAnnouncementManager.validMessage("hello\n/g hello"));
        assertFalse(AchievementAnnouncementManager.validMessage("§cHello"));
        assertFalse(AchievementAnnouncementManager.validMessage("a".repeat(241)));
        assertFalse(AchievementAnnouncementManager.validMessage(null));
        assertTrue(AchievementAnnouncementManager.validMessage(earned().announcement().message()));
    }

    private static AchievementAnnouncementClaim earned() {
        return new AchievementAnnouncementClaim(new Announcement(UUID.randomUUID(), "Baptiste reached Diamond (1,000 raids) in TNA!"));
    }

    private void flush() {
        var pending = List.copyOf(callbacks);
        callbacks.clear();
        pending.forEach(Runnable::run);
    }
}
