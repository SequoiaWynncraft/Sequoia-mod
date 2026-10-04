package com.seqwawa.seq.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wynntils.models.inventory.type.InventoryAccessory;
import com.wynntils.models.inventory.type.InventoryArmor;
import com.wynntils.services.hades.type.GearShareOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WynntilsGearSharingAccessTest {
    @Test
    void missingModDoesNotResolveAnyWynntilsClasses() {
        var access = new WynntilsGearSharingAccess(() -> false,
                () -> { throw new AssertionError("Must not resolve absent Wynntils"); },
                error -> { throw new AssertionError("Must not log absent Wynntils", error); });
        assertEquals(WynntilsGearSharingAccess.Result.UNAVAILABLE, access.disable());
    }

    @Test
    void disablesGlobalAndAllCharacterOptionsAndSavesOnce() {
        FakeService service = new FakeService();
        GearShareOptions active = new GearShareOptions();
        active.setShareHeldItem(false);
        active.setShareCraftedItems(false);
        GearShareOptions inactive = new GearShareOptions();
        inactive.setShareCraftedNames(false);
        service.characterGearShareOptions.value.put("active", active);
        service.characterGearShareOptions.value.put("inactive", inactive);
        service.characterGearShareEnabled.put("active", true);
        service.characterGearShareEnabled.put("inactive", false);
        var access = access(service);

        assertEquals(WynntilsGearSharingAccess.Result.CHANGED, access.disable());
        for (GearShareOptions options : List.of(service.gearShareOptions.value, active, inactive)) {
            assertSlotsDisabled(options);
        }
        assertTrue(service.gearShareOptions.value.shouldShareHeldItem());
        assertTrue(service.gearShareOptions.value.shareCraftedItems());
        assertTrue(service.gearShareOptions.value.shareCraftedNames());
        assertFalse(active.shouldShareHeldItem());
        assertFalse(active.shareCraftedItems());
        assertTrue(active.shareCraftedNames());
        assertTrue(inactive.shouldShareHeldItem());
        assertTrue(inactive.shareCraftedItems());
        assertFalse(inactive.shareCraftedNames());
        assertEquals(Map.of("active", true, "inactive", false), service.characterGearShareEnabled);
        assertEquals(1, service.saves);

        assertEquals(WynntilsGearSharingAccess.Result.UNCHANGED, access.disable());
        assertEquals(1, service.saves);
    }

    @Test
    void disablesEachSlotIndividuallyAndPreservesHeldItemSharing() {
        FakeService service = new FakeService();
        var access = access(service);
        access.disable();
        for (boolean heldItem : List.of(false, true)) {
            service.gearShareOptions.value.setShareHeldItem(heldItem);
            for (InventoryArmor slot : InventoryArmor.values()) {
                service.gearShareOptions.value.setShareArmor(slot, true);
                assertEquals(WynntilsGearSharingAccess.Result.CHANGED, access.disable(), slot.name());
                assertSlotsDisabled(service.gearShareOptions.value);
                assertEquals(heldItem, service.gearShareOptions.value.shouldShareHeldItem());
            }
            for (InventoryAccessory slot : InventoryAccessory.values()) {
                service.gearShareOptions.value.setShareAccessory(slot, true);
                assertEquals(WynntilsGearSharingAccess.Result.CHANGED, access.disable(), slot.name());
                assertSlotsDisabled(service.gearShareOptions.value);
                assertEquals(heldItem, service.gearShareOptions.value.shouldShareHeldItem());
            }
        }
    }

    @Test
    void readsNewlySavedProfilesOnNextInvocation() {
        FakeService service = new FakeService();
        var access = access(service);
        access.disable();
        GearShareOptions newCharacter = new GearShareOptions();
        service.characterGearShareOptions.value.put("new", newCharacter);
        assertEquals(WynntilsGearSharingAccess.Result.CHANGED, access.disable());
        assertSlotsDisabled(newCharacter);
        assertEquals(2, service.saves);
    }

    @Test
    void validatesEveryProfileBeforeMutatingAnyOptions() {
        FakeService service = new FakeService();
        GearShareOptions validProfile = new GearShareOptions();
        service.characterGearShareOptions.value.put("valid", validProfile);
        service.characterGearShareOptions.value.put("incompatible", new Object());
        List<Throwable> failures = new ArrayList<>();
        var access = new WynntilsGearSharingAccess(() -> true, () -> service, failures::add);
        assertEquals(WynntilsGearSharingAccess.Result.UNAVAILABLE, access.disable());
        assertEquals(WynntilsGearSharingAccess.Result.UNAVAILABLE, access.disable());
        assertTrue(service.gearShareOptions.value.shouldShareArmor(InventoryArmor.HELMET));
        assertTrue(validProfile.shouldShareAccessory(InventoryAccessory.NECKLACE));
        assertEquals(0, service.saves);
        assertEquals(1, failures.size());
    }

    @Test
    void handlesMissingClassesFieldsSaveMethodAndStorageInvocationFailures() {
        for (WynntilsGearSharingAccess.ServiceResolver resolver : List.<WynntilsGearSharingAccess.ServiceResolver>of(
                () -> { throw new ClassNotFoundException("Services"); },
                () -> { throw new NoClassDefFoundError("Services"); },
                Object::new,
                MissingSaveService::new,
                ThrowingStorageService::new)) {
            List<Throwable> failures = new ArrayList<>();
            var access = new WynntilsGearSharingAccess(() -> true, resolver, failures::add);
            assertEquals(WynntilsGearSharingAccess.Result.UNAVAILABLE, access.disable());
            assertEquals(WynntilsGearSharingAccess.Result.UNAVAILABLE, access.disable());
            assertEquals(1, failures.size());
        }
    }

    private static WynntilsGearSharingAccess access(FakeService service) {
        return new WynntilsGearSharingAccess(() -> true, () -> service,
                error -> { throw new AssertionError(error); });
    }

    private static void assertSlotsDisabled(GearShareOptions options) {
        for (InventoryArmor slot : InventoryArmor.values()) {
            assertFalse(options.shouldShareArmor(slot), slot.name());
        }
        for (InventoryAccessory slot : InventoryAccessory.values()) {
            assertFalse(options.shouldShareAccessory(slot), slot.name());
        }
    }

    public static class FakeService {
        private final FakeStorage<GearShareOptions> gearShareOptions = new FakeStorage<>(new GearShareOptions());
        private final FakeStorage<Map<String, Object>> characterGearShareOptions = new FakeStorage<>(new LinkedHashMap<>());
        private final Map<String, Boolean> characterGearShareEnabled = new LinkedHashMap<>();
        int saves;

        public void saveGearShareOptions() {
            saves++;
        }
    }

    public static class FakeStorage<T> {
        final T value;

        FakeStorage(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

    public static class MissingSaveService {
        private final FakeStorage<GearShareOptions> gearShareOptions = new FakeStorage<>(new GearShareOptions());
        private final FakeStorage<Map<String, Object>> characterGearShareOptions = new FakeStorage<>(Map.of());
    }

    public static class ThrowingStorageService {
        private final ThrowingStorage gearShareOptions = new ThrowingStorage();
    }

    public static class ThrowingStorage {
        public Object get() {
            throw new IllegalStateException("Unavailable");
        }
    }
}
