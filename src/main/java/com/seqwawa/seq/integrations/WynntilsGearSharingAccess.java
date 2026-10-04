package com.seqwawa.seq.integrations;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/** Updates saved sharing options without requiring Wynntils at runtime. */
public final class WynntilsGearSharingAccess {
    private static final WynntilsGearSharingAccess INSTANCE = new WynntilsGearSharingAccess(
            () -> FabricLoader.getInstance().isModLoaded("wynntils"),
            () -> Class.forName("com.wynntils.core.components.Services").getField("Hades").get(null),
            error -> LogUtils.getLogger().warn("[Wynntils] Gearsharing integration is unavailable", error));

    private final BooleanSupplier modLoaded;
    private final ServiceResolver serviceResolver;
    private final Consumer<Throwable> logFailure;
    private boolean failureLogged;

    WynntilsGearSharingAccess(
            BooleanSupplier modLoaded, ServiceResolver serviceResolver, Consumer<Throwable> logFailure) {
        this.modLoaded = modLoaded;
        this.serviceResolver = serviceResolver;
        this.logFailure = logFailure;
    }

    public static Result disableArmorAndAccessorySharing() {
        return INSTANCE.disable();
    }

    Result disable() {
        if (!modLoaded.getAsBoolean()) {
            return Result.UNAVAILABLE;
        }
        try {
            Object service = serviceResolver.resolve();
            Object globalOptions = storedValue(service, "gearShareOptions");
            Object characterOptions = storedValue(service, "characterGearShareOptions");
            if (!(characterOptions instanceof Map<?, ?> profiles)) {
                throw new IllegalStateException("Wynntils character gearsharing options are not a map");
            }
            Method save = service.getClass().getMethod("saveGearShareOptions");
            requireReturnType(save, void.class);

            // Resolve all methods and read all profiles before changing any options.
            List<SlotChange> changes = new ArrayList<>();
            collectChanges(globalOptions, changes);
            for (Object options : profiles.values()) {
                collectChanges(options, changes);
            }
            if (changes.isEmpty()) {
                return Result.UNCHANGED;
            }
            for (SlotChange change : changes) {
                change.setter().invoke(change.options(), change.slot(), false);
            }
            save.invoke(service);
            return Result.CHANGED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (!failureLogged) {
                failureLogged = true;
                logFailure.accept(error);
            }
            return Result.UNAVAILABLE;
        }
    }

    private static Object storedValue(Object service, String fieldName) throws ReflectiveOperationException {
        Field field = service.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object storage = field.get(service);
        return storage.getClass().getMethod("get").invoke(storage);
    }

    private static void collectChanges(Object options, List<SlotChange> changes) throws ReflectiveOperationException {
        collectSlotChanges(options, "InventoryArmor", "shouldShareArmor", "setShareArmor", changes);
        collectSlotChanges(options, "InventoryAccessory", "shouldShareAccessory", "setShareAccessory", changes);
    }

    private static void collectSlotChanges(
            Object options, String slotType, String getterName, String setterName, List<SlotChange> changes)
            throws ReflectiveOperationException {
        Class<?> slots = Class.forName("com.wynntils.models.inventory.type." + slotType,
                true, options.getClass().getClassLoader());
        if (!slots.isEnum()) {
            throw new IllegalStateException("Wynntils gearsharing slot type is not an enum");
        }
        Method shouldShare = options.getClass().getMethod(getterName, slots);
        Method setter = options.getClass().getMethod(setterName, slots, boolean.class);
        requireReturnType(shouldShare, boolean.class);
        requireReturnType(setter, void.class);
        for (Object slot : slots.getEnumConstants()) {
            if ((Boolean) shouldShare.invoke(options, slot)) {
                changes.add(new SlotChange(options, setter, slot));
            }
        }
    }

    private static void requireReturnType(Method method, Class<?> expected) {
        if (method.getReturnType() != expected) {
            throw new IllegalStateException("Unexpected Wynntils API return type: " + method);
        }
    }

    private record SlotChange(Object options, Method setter, Object slot) {}

    public enum Result {
        CHANGED,
        UNCHANGED,
        UNAVAILABLE
    }

    @FunctionalInterface
    interface ServiceResolver {
        Object resolve() throws ReflectiveOperationException;
    }
}
