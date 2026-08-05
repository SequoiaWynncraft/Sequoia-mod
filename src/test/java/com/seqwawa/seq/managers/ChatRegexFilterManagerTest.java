package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
                        "guild_bank"),
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
            "󏿼󐀆 Torment changed the ally tax of Path to the Grootslangs\n󏿼󐀆 to 67%",
            "󏿼󐀆 Torment set Efficient Resources bonus to level 3 on Lake\n󏿼󐀆 Rieke",
            "󏿼󐀆 Torment set Efficient Resources upgrade to level 3 on Lake\n󏿼󐀆 Rieke",
            "󏿼󐀆 Torment changed the style of Wood Sprite Hideaway to \n󏿼󐀆 fastest",
            "󏿼󐀆 Torment changed the style of Wood Sprite Hideaway to \n󏿼󐀆 cheapest",
            "󏿼󏿿󏿾 Envy changed the borders of Harnort Compound to close",
            "󏿼󐀆 Envy changed the borders of Harnort Compound to open",
            "󏿼󏿿󏿾 Yearnm changed the global borders to close",
            "󏿼󐀆 Envy set the guild headquarters to Harnort Compound",
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
        assertFalse(manager.shouldFilter("Torment: I changed the ally tax of our route to 67%"));
        assertFalse(manager.shouldFilter("Torment: I changed the style of my house to fastest"));
        assertFalse(manager.shouldFilter("Envy: I changed the borders of my build to open"));
        assertFalse(manager.shouldFilter("Yearnm: I changed the global borders to close"));
        assertFalse(manager.shouldFilter("Envy: I set the guild headquarters to Harnort Compound"));
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
        assertTrue(manager.shouldFilter(
                "󏿼󐀆 Territory Lake Rieke production has stabilised"));
        assertTrue(manager.shouldFilter(
                "󏿼󐀆 Territory Karoc Quarry production has stabilized"));
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
    void gazEffectRequiresOnlyGeneralEasterEggSettingAndCanonicalUsername() {
        boolean[] easterEggsEnabled = {false};
        AtomicInteger deathMessages = new AtomicInteger();
        ChatRegexFilterManager manager =
                new ChatRegexFilterManager(() -> easterEggsEnabled[0], deathMessages::incrementAndGet);
        Component providedMessage = guildMessage("GaztheCat", "star check your dms");

        assertTrue(manager.shouldAllowIncoming(providedMessage));
        assertEquals(0, deathMessages.get());

        easterEggsEnabled[0] = true;
        assertTrue(manager.shouldAllowIncoming(providedMessage));
        assertTrue(manager.shouldAllowIncoming(guildMessage("GAZTHECAT", "check your DM")));
        assertTrue(manager.shouldAllowIncoming(guildMessage("SomeoneElse", "star check your dms")));
        assertTrue(manager.shouldAllowIncoming(guildMessage("GaztheCat", "star check your messages")));
        assertEquals(2, deathMessages.get());
    }

    @Test
    void gazEffectResolvesNicknameFromMessageMetadata() {
        AtomicInteger deathMessages = new AtomicInteger();
        ChatRegexFilterManager manager =
                new ChatRegexFilterManager(() -> true, deathMessages::incrementAndGet);

        Component message = Component.empty()
                .append(Component.literal("󏿼󏿿󏿾 "))
                .append(Component.literal("󏿿󏿿󏿿󏿿󏿿󏿿󏿠󐀂 "))
                .append(Component.literal("Rallying Fervor")
                        .withStyle(Style.EMPTY.withInsertion("GaztheCat")))
                .append(Component.literal(": star check your dms"));

        assertTrue(manager.shouldAllowIncoming(message));
        assertTrue(manager.shouldAllowIncoming(Component.literal(
                "󏿼󏿿󏿾 Rallying Fervor: star check your dms")));
        assertEquals(1, deathMessages.get());
    }

    @Test
    void gazEffectAcceptsPunctuationAndIgnBeforeOrAfterReminder() {
        AtomicInteger deathMessages = new AtomicInteger();
        ChatRegexFilterManager manager =
                new ChatRegexFilterManager(() -> true, deathMessages::incrementAndGet);

        List<String> reminders = List.of(
                "Cela41, check dm.",
                "check your dm, Cela41!",
                "Cela41... check, your d.m.s!!!",
                "check-dms / Cela41");

        for (String reminder : reminders) {
            assertTrue(manager.shouldAllowIncoming(guildMessage("GaztheCat", reminder)), reminder);
        }

        assertEquals(reminders.size(), deathMessages.get());
    }

    private static Component guildMessage(String canonicalUsername, String content) {
        return Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("Rallying Fervor")
                        .withStyle(Style.EMPTY.withInsertion(canonicalUsername)))
                .append(Component.literal(": " + content));
    }
}
