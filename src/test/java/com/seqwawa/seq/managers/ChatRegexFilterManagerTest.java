package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class ChatRegexFilterManagerTest {
    @Test
    void defaultsToDisabled() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);

        assertFalse(manager.enabledSetting().getValue());
        assertFalse(manager.builtInFilters().getFirst().enabledSetting().getValue());
        assertFalse(manager.shouldFilter(guildMessage("GaztheCat", "check your dms")));
        assertEquals(
                List.of(
                        "enable_regex_filters",
                        "economy",
                        "economy_resource_alerts_only",
                        "guild_bank",
                        "gaz_death_message"),
                manager.settings().stream().map(setting -> setting.getName()).toList());
    }

    @Test
    void economyFilterCoversTerritoryManagementSpam() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        manager.builtInFilters().getFirst().enabledSetting().setValue(true);

        String[] messages = {
            "󏿼󐀆 Torment changed 2 upgrades on Alder Understory",
            "󏿼󐀆 Kablob changed 3 bonuses on Citadel's Shadow",
            "󏿼󏿿󏿾 Cal_and_Ben changed the global tax to 70%",
            "󏿼󏿿󏿾 know_your_limits changed the tax of Espren to 69%",
            "󏿼󐀆 Torment set Efficient Resources bonus to level 3 on Lake\n󏿼󐀆 Rieke",
            "󏿼󏿿󏿾 Territory Citadel's Shadow is using more resources than it\n󏿼󐀆 can store!",
            "󏿼󐀆 Territory Lake Rieke is using more resources than it can\n󏿼󐀆 store!",
            "󏿼󐀆 Torment removed Larger Resource Storage bonus from \n󏿼󐀆 Citadel's Shadow",
            "󏿼󐀆 Kablob set Larger Resource Storage bonus to level 1 on \n󏿼󐀆 Citadel's Shadow",
            "󏿼󐀆 Sorrow applied the loadout ragebait on Void Valley",
            "󏿼󐀆 Charlvtte applied the loadout :void: on Royal Gate§b,\n"
                    + "󏿼󐀆 §3Wellspring of Eternity§b, §3Fort Torann§b, §3Royal Dam§b,\n"
                    + "󏿼󐀆 §3Xima Valley§b, §3Forts in Fall§b, §3The Frog Bog§b,\n"
                    + "󏿼󐀆 §3Citadel's Shadow§b, §3Void Valley§b, §3Toxic Caves§b, "
                    + "and §3Final Step",
            "󏿼󐀆 Territory Lake Rieke production has stabilised",
            "󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿿󏿂󐀆"
                    + "§aCopied to clipboard: §fTerritory Royal Gate is producing more resources than it\n"
                    + " can store!",
            "&b&{fr:cp}󏿼󏿿󏿾&{fr:d} Territory &3Forts in Fall&b is producing more resources than\n"
                    + "&{fr:cp}󏿼󐀆&{fr:d} it can store!"
        };

        for (String message : messages) {
            assertTrue(manager.shouldFilter(message), message);
        }
    }

    @Test
    void economyFilterDoesNotHideNearbyChatMessages() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        manager.builtInFilters().getFirst().enabledSetting().setValue(true);

        assertFalse(manager.shouldFilter("Torment: I changed 2 upgrades on my build"));
        assertFalse(manager.shouldFilter("Cal_and_Ben: I changed the global tax to 70%"));
        assertFalse(manager.shouldFilter("Sorrow: I applied the loadout ragebait on Void Valley"));
        assertFalse(manager.shouldFilter("Territory Lake Rieke is under attack!"));
        assertFalse(manager.shouldFilter("Territory Lake Rieke production increased"));
    }

    @Test
    void economyCanFilterOnlyResourceStorageAlerts() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        Setting.BooleanSetting economySetting =
                manager.builtInFilters().getFirst().enabledSetting();

        assertFalse(manager.economyAlertsOnlySetting().isVisible());

        economySetting.setValue(true);
        manager.economyAlertsOnlySetting().setValue(true);

        assertTrue(manager.economyAlertsOnlySetting().isVisible());
        assertTrue(manager.shouldFilter(
                "󏿼󐀆 Territory Lake Rieke is using more resources than it can\n󏿼󐀆 store!"));
        assertTrue(manager.shouldFilter(
                "󏿼󐀆 Territory Royal Gate is producing more resources than it can store!"));
        assertFalse(manager.shouldFilter(
                "󏿼󐀆 Sorrow applied the loadout ragebait on Void Valley"));
        assertFalse(manager.shouldFilter(
                "󏿼󏿿󏿾 Cal_and_Ben changed the global tax to 70%"));
        assertFalse(manager.shouldFilter(
                "󏿼󐀆 Territory Lake Rieke production has stabilised"));
    }

    @Test
    void guildBankFilterCoversDepositAndWithdrawalSpam() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        manager.builtInFilters().stream()
                .filter(filter -> filter.id().equals("guild_bank"))
                .findFirst()
                .orElseThrow()
                .enabledSetting()
                .setValue(true);

        String[] messages = {
            "󏿼󏿿󏿾 NAGISX deposited 3x Cinnabar Gem to the Guild Bank (\n󏿼󐀆 Everyone)",
            "󏿼󐀆 NAGISX deposited 3x Mahseer Oil to the Guild Bank (\n󏿼󐀆 Everyone)",
            "󏿼󐀆 I hate Prof withdrew 3x Cinnabar Gem from the Guild Bank (\n󏿼󐀆 Everyone)",
            "&b&{fr:cp}󏿼󐀆&{fr:d} &3&o&<1>I hate Prof&b withdrew "
                    + "&e1x Remnant of the Ruined&b from the\n"
                    + "&{fr:cp}󏿼󐀆&{fr:d} Guild Bank (&3Everyone&b)",
            "&b&{fr:cp}󏿼󐀆&{fr:d} &3&o&<1>I hate Prof&b withdrew "
                    + "&e1x Lunar Charm&b from the Guild Bank (&3\n"
                    + "&b&{fr:cp}󏿼󐀆&{fr:d} &3Everyone&b)"
        };

        for (String message : messages) {
            assertTrue(manager.shouldFilter(message), message);
        }
    }

    @Test
    void guildBankFilterDoesNotHidePlayerDiscussion() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        manager.builtInFilters().stream()
                .filter(filter -> filter.id().equals("guild_bank"))
                .findFirst()
                .orElseThrow()
                .enabledSetting()
                .setValue(true);

        assertFalse(manager.shouldFilter("NAGISX: I deposited 3x Cinnabar Gem"));
        assertFalse(manager.shouldFilter("The Guild Bank is open to everyone"));
    }

    @Test
    void gazFilterRequiresEasterEggsAndCanonicalUsername() {
        boolean[] easterEggsEnabled = {false};
        ChatRegexFilterManager manager =
                new ChatRegexFilterManager(() -> easterEggsEnabled[0]);
        manager.enabledSetting().setValue(true);
        ChatRegexFilterManager.BuiltInFilter gazFilter = manager.builtInFilters().stream()
                .filter(filter -> filter.id().equals("gaz_death_message"))
                .findFirst()
                .orElseThrow();
        gazFilter.enabledSetting().setValue(true);
        Component providedMessage = guildMessage("GaztheCat", "star check your dms");

        assertFalse(gazFilter.enabledSetting().isVisible());
        assertFalse(manager.shouldFilter(providedMessage));

        easterEggsEnabled[0] = true;
        assertTrue(gazFilter.enabledSetting().isVisible());
        assertTrue(manager.shouldFilter(providedMessage));
        assertTrue(manager.shouldFilter(guildMessage("GAZTHECAT", "check your DM")));
        assertFalse(manager.shouldFilter(guildMessage("SomeoneElse", "star check your dms")));
        assertFalse(manager.shouldFilter(guildMessage("GaztheCat", "star check your messages")));
    }

    @Test
    void gazFilterResolvesNicknameFromMessageMetadata() {
        ChatRegexFilterManager manager = new ChatRegexFilterManager(() -> true);
        manager.enabledSetting().setValue(true);
        manager.builtInFilters().stream()
                .filter(filter -> filter.id().equals("gaz_death_message"))
                .findFirst()
                .orElseThrow()
                .enabledSetting()
                .setValue(true);

        Component message = Component.empty()
                .append(Component.literal("󏿼󏿿󏿾 "))
                .append(Component.literal("󏿿󏿿󏿿󏿿󏿿󏿿󏿠󐀂 "))
                .append(Component.literal("Rallying Fervor")
                        .withStyle(Style.EMPTY.withInsertion("GaztheCat")))
                .append(Component.literal(": star check your dms"));

        assertTrue(manager.shouldFilter(message));
        assertFalse(manager.shouldFilter(Component.literal(
                "󏿼󏿿󏿾 Rallying Fervor: star check your dms")));
    }

    private static Component guildMessage(String canonicalUsername, String content) {
        return Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("Rallying Fervor")
                        .withStyle(Style.EMPTY.withInsertion(canonicalUsername)))
                .append(Component.literal(": " + content));
    }
}
