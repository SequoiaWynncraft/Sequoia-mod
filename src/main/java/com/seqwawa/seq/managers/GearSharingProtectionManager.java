package com.seqwawa.seq.managers;

import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.integrations.WynntilsGearSharingAccess;
import java.util.function.Supplier;

/** Silently disables armor/accessory sharing immediately on world change. */
public final class GearSharingProtectionManager {
    private final Supplier<WynntilsGearSharingAccess.Result> disableSharing;

    public GearSharingProtectionManager() {
        this(WynntilsGearSharingAccess::disableArmorAndAccessorySharing);
    }

    GearSharingProtectionManager(Supplier<WynntilsGearSharingAccess.Result> disableSharing) {
        this.disableSharing = disableSharing;
    }

    public static Setting.BooleanSetting createSetting() {
        Setting.BooleanSetting setting = new Setting.BooleanSetting("auto_disable_wynntils_gearsharing", "wynntils", true);
        setting.setPresentation(
                "Automatically disable Wynntils armor/accessory sharing",
                "Disable armor and accessory sharing on each world change. Held-item sharing is preserved.",
                "Gearsharing");
        return setting;
    }

    public void onWorldChange(boolean enabled) {
        if (enabled) {
            disableSharing.get();
        }
    }
}
