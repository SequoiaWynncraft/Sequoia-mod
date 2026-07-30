package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuildAllianceSnapshotManagerTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recognizesOnlyGuildDiplomacyTitles() {
        assertTrue(GuildAllianceSnapshotManager.isDiplomacyMenuTitle("Sequoia: Diplomacy"));
        assertTrue(GuildAllianceSnapshotManager.isDiplomacyMenuTitle("Nefarious Ravens: Diplomacy"));
        assertFalse(GuildAllianceSnapshotManager.isDiplomacyMenuTitle("Sequoia: Members"));
        assertFalse(GuildAllianceSnapshotManager.isDiplomacyMenuTitle("Sequoia: Diplomacy Settings"));
        assertFalse(GuildAllianceSnapshotManager.isDiplomacyMenuTitle(": Diplomacy"));
    }

    @Test
    void waitsForPopulatedContainerContents() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));

        manager.onMenuOpened(12, "Sequoia: Diplomacy");

        assertEquals(List.of(), published);

        manager.onContainerContents(12, List.of());

        assertEquals(List.of(), published);

        manager.onContainerContents(12, diplomacyContents(alliedGuild("Avicia", "AVO")));

        assertEquals(List.of(List.of("Avicia")), published);
    }

    @Test
    void parsesMultipleAlliedGuilds() {
        List<List<String>> published = new ArrayList<>();
        Object session = new Object();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));
        manager.onMenuOpened(4, "Sequoia: Diplomacy");
        manager.onContainerContents(
                4,
                diplomacyContents(
                        alliedGuild("Avicia", "AVO"),
                        alliedGuild("Nefarious Ravens", "NRA")));

        assertEquals(List.of(List.of("Avicia", "Nefarious Ravens")), published);
    }

    @Test
    void normalizesFormattingEmbeddedInAlliedGuildNames() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));

        manager.onMenuOpened(4, "Sequoia: Diplomacy");
        manager.onContainerContents(4, diplomacyContents(namedItem("§aNefarious Ravens§b [NRA]")));

        assertEquals(List.of(List.of("Nefarious Ravens")), published);
    }

    @Test
    void sendsEmptyAuthoritativeAllianceList() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));

        manager.onMenuOpened(7, "Sequoia: Diplomacy");
        manager.onContainerContents(7, diplomacyContents());

        assertEquals(List.of(List.of()), published);
    }

    @Test
    void removesCaseInsensitiveDuplicates() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));

        manager.onMenuOpened(7, "Sequoia: Diplomacy");
        manager.onContainerContents(
                7,
                diplomacyContents(
                        alliedGuild("Avicia", "AVO"),
                        alliedGuild("avicia", "AVO"),
                        alliedGuild("Nefarious Ravens", "NRA")));

        assertEquals(List.of(List.of("Avicia", "Nefarious Ravens")), published);
    }

    @Test
    void sendsOnlyOncePerMenuOpening() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));
        List<ItemStack> contents = diplomacyContents(alliedGuild("Avicia", "AVO"));

        manager.onMenuOpened(3, "Sequoia: Diplomacy");
        manager.onContainerContents(3, contents);
        manager.onContainerContents(3, contents);

        assertEquals(List.of(List.of("Avicia")), published);

        manager.onMenuOpened(3, "Sequoia: Diplomacy");
        manager.onContainerContents(3, contents);

        assertEquals(List.of(List.of("Avicia"), List.of("Avicia")), published);
    }

    @Test
    void doesNotSendIncompleteOrMalformedContents() {
        Object session = new Object();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(() -> session, snapshotPublisher(published));

        manager.onMenuOpened(5, "Sequoia: Diplomacy");
        manager.onContainerContents(5, List.of(ItemStack.EMPTY));
        manager.onContainerContents(5, diplomacyContents(namedItem("Loading...")));

        assertEquals(List.of(), published);
    }

    @Test
    void doesNotSendWithoutTheOpeningAuthenticatedSession() {
        AtomicReference<Object> session = new AtomicReference<>();
        List<List<String>> published = new ArrayList<>();
        GuildAllianceSnapshotManager manager =
                new GuildAllianceSnapshotManager(session::get, snapshotPublisher(published));
        List<ItemStack> contents = diplomacyContents(alliedGuild("Avicia", "AVO"));

        manager.onMenuOpened(9, "Sequoia: Diplomacy");
        session.set(new Object());
        manager.onContainerContents(9, contents);

        assertEquals(List.of(), published);

        Object openingSession = new Object();
        session.set(openingSession);
        manager.onMenuOpened(10, "Sequoia: Diplomacy");
        session.set(null);
        manager.onContainerContents(10, contents);
        session.set(new Object());
        manager.onContainerContents(10, contents);

        assertEquals(List.of(), published);
    }

    private static GuildAllianceSnapshotManager.SnapshotPublisher snapshotPublisher(
            List<List<String>> published) {
        return guildNames -> {
            published.add(guildNames);
            return true;
        };
    }

    private static List<ItemStack> diplomacyContents(ItemStack... allies) {
        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int index = 0; index < allies.length; index++) {
            contents.set(index + 2, allies[index]);
        }
        return contents;
    }

    private static ItemStack alliedGuild(String guildName, String tag) {
        return namedItem(guildName + " [" + tag + "]");
    }

    private static ItemStack namedItem(String name) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
