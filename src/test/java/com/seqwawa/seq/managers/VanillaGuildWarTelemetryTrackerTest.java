package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaGuildWarTelemetryTrackerTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetDetector() {
        MinecraftCharacterClassDetector.getInstance().reset();
    }

    @Test
    void nativeClassAndBossBarPublishWarBeforeTower() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        String selectionTitle = new String(Character.toChars(0xCFFD5)) + '\uE01F';
        detector.observeCharacterSelection(selectionTitle, characterCard("- Class: Warrior"));

        MinecraftWarTowerTracker towerTracker = new MinecraftWarTowerTracker();
        towerTracker.add(
                UUID.fromString("10000000-0000-0000-0000-000000000009"),
                Component.literal("[SEQ] Detlas Tower - ❤ 300000 (25%) - ⚔ 1200-1800 (2.5x)"),
                0.75f);

        CapturingPublisher publisher = new CapturingPublisher();
        LiveWarTelemetryTracker telemetry = new LiveWarTelemetryTracker(
                new DetectorPlayerContext(detector), publisher, () -> true, () -> 100_000L);
        VanillaGuildWarTelemetryTracker tracker =
                new VanillaGuildWarTelemetryTracker(detector, towerTracker, telemetry);

        tracker.tickCurrentState();

        assertEquals(List.of("status:WAR:Detlas", "tower:Detlas"), publisher.events);
        assertEquals(new WarTowerUpdate("Detlas", 0.75f, 400_000L, 3_750L), publisher.towerUpdates.getFirst());
    }

    @Test
    void runtimeIdentityChangeClearsClassTowerAndTelemetryState() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        String selectionTitle = new String(Character.toChars(0xCFFD5)) + '\uE01F';
        detector.observeCharacterSelection(selectionTitle, characterCard("- Class: Archer"));
        MinecraftWarTowerTracker towerTracker = new MinecraftWarTowerTracker();
        towerTracker.add(
                UUID.fromString("10000000-0000-0000-0000-000000000010"),
                Component.literal("[SEQ] Ragni Tower - ❤ 100 (0%) - ⚔ 1-2 (1x)"),
                1.0f);
        CapturingPublisher publisher = new CapturingPublisher();
        LiveWarTelemetryTracker telemetry = new LiveWarTelemetryTracker(
                new DetectorPlayerContext(detector), publisher, () -> true, () -> 200_000L);
        VanillaGuildWarTelemetryTracker tracker =
                new VanillaGuildWarTelemetryTracker(detector, towerTracker, telemetry);

        tracker.observeRuntimeIdentity(1, 2, 3);
        tracker.tickCurrentState();
        tracker.observeRuntimeIdentity(1, 4, 5);

        assertNull(detector.currentClass());
        assertNull(towerTracker.latestSnapshot());
        tracker.tickCurrentState();
        assertEquals("status:REMOVE:null", publisher.events.getLast());
    }

    private static ItemStack characterCard(String classLore) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(classLore))));
        return stack;
    }

    private record DetectorPlayerContext(MinecraftCharacterClassDetector detector)
            implements LiveWarTelemetryTracker.PlayerContext {
        @Override
        public boolean warModeActive() {
            return false;
        }

        @Override
        public WynnClassType localClassType() {
            return detector.currentClass();
        }

        @Override
        public LiveWarTelemetryTracker.WorldPosition worldPosition() {
            return new LiveWarTelemetryTracker.WorldPosition(0, 0);
        }
    }

    private static final class CapturingPublisher implements LiveWarTelemetryTracker.Publisher {
        private final ArrayList<String> events = new ArrayList<>();
        private final ArrayList<WarTowerUpdate> towerUpdates = new ArrayList<>();

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public boolean publishWarStatus(WarStatusUpdate update) {
            events.add("status:" + update.status() + ":" + update.territory());
            return true;
        }

        @Override
        public boolean publishWarTowerUpdate(WarTowerUpdate update) {
            towerUpdates.add(update);
            events.add("tower:" + update.territory());
            return true;
        }
    }
}
