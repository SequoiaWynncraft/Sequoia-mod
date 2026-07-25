package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

/**
 * Filters incoming chat against independently configurable built-in patterns.
 */
public final class ChatRegexFilterManager {
    private static final String SETTINGS_CATEGORY = "chat_filters";
    private static final String GAZ_USERNAME = "gazthecat";
    private static final Pattern ECONOMY_ACTIVITY_PATTERN = Pattern.compile(
            "^(?:"
                    + "[^:]{1,64} changed \\d+ (?:upgrades|bonuses) on .+"
                    + "|[^:]{1,64} changed (?:the global tax|the tax of .+) to \\d+%"
                    + "|[^:]{1,64} set .+ bonus to level \\d+ on .+"
                    + "|[^:]{1,64} removed .+ bonus from .+"
                    + "|[^:]{1,64} applied the loadout(?:\\s+|:).+ on .+"
                    + "|Territory .+ production has stabil(?:ised|ized)"
                    + ")$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ECONOMY_RESOURCE_ALERT_PATTERN = Pattern.compile(
            "^(?:Copied to clipboard: )?Territory .+ "
                    + "(?:is using|is producing) more resources than it can store!$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GUILD_BANK_PATTERN = Pattern.compile(
            "^[^:]{1,64} (?:deposited \\d+x .+ to|withdrew \\d+x .+ from) "
                    + "the Guild Bank \\([^)]+\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_YOUR_DMS_PATTERN =
            Pattern.compile("\\bcheck\\s+your\\s+dms?\\b", Pattern.CASE_INSENSITIVE);

    private final Setting.BooleanSetting enabledSetting =
            new Setting.BooleanSetting("enable_regex_filters", SETTINGS_CATEGORY, false);
    private final Setting.BooleanSetting economyAlertsOnlySetting;
    private final List<BuiltInFilter> builtInFilters;
    private final Runnable gazDeathMessageEffect;
    private boolean hooksRegistered;

    public ChatRegexFilterManager() {
        this(ChatRegexFilterManager::easterEggsEnabled, GazDeathMessageEffect::showForLocalPlayer);
    }

    ChatRegexFilterManager(BooleanSupplier easterEggsEnabled) {
        this(easterEggsEnabled, () -> {});
    }

    ChatRegexFilterManager(BooleanSupplier easterEggsEnabled, Runnable gazDeathMessageEffect) {
        Objects.requireNonNull(easterEggsEnabled);
        this.gazDeathMessageEffect = Objects.requireNonNull(gazDeathMessageEffect);
        Setting.BooleanSetting gazDeathSetting =
                new Setting.BooleanSetting("gaz_death_message", SETTINGS_CATEGORY, false);
        gazDeathSetting.setVisibilityCondition(easterEggsEnabled);
        Setting.BooleanSetting economySetting =
                new Setting.BooleanSetting("economy", SETTINGS_CATEGORY, false);
        economyAlertsOnlySetting =
                new Setting.BooleanSetting("economy_resource_alerts_only", SETTINGS_CATEGORY, false);
        economyAlertsOnlySetting.setVisibilityCondition(economySetting::getValue);
        builtInFilters = List.of(
                new BuiltInFilter(
                        "economy",
                        economySetting,
                        (message, normalized) -> matchesEconomy(normalized),
                        () -> true),
                new BuiltInFilter(
                        "guild_bank",
                        new Setting.BooleanSetting("guild_bank", SETTINGS_CATEGORY, false),
                        (message, normalized) -> GUILD_BANK_PATTERN.matcher(normalized).matches(),
                        () -> true),
                new BuiltInFilter(
                        "gaz_death_message",
                        gazDeathSetting,
                        ChatRegexFilterManager::matchesGazDmReminder,
                        easterEggsEnabled));
    }

    public List<Setting<?>> settings() {
        List<Setting<?>> settings = new ArrayList<>(2 + builtInFilters.size());
        settings.add(enabledSetting);
        for (BuiltInFilter filter : builtInFilters) {
            settings.add(filter.enabledSetting());
            if (filter.id().equals("economy")) {
                settings.add(economyAlertsOnlySetting);
            }
        }
        return List.copyOf(settings);
    }

    public Setting.BooleanSetting enabledSetting() {
        return enabledSetting;
    }

    public List<BuiltInFilter> builtInFilters() {
        return builtInFilters;
    }

    Setting.BooleanSetting economyAlertsOnlySetting() {
        return economyAlertsOnlySetting;
    }

    public void registerIncomingHooks() {
        if (hooksRegistered) {
            return;
        }
        hooksRegistered = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> overlay || !shouldFilter(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) ->
                        !shouldFilter(message));
    }

    public boolean shouldFilter(String message) {
        return shouldFilter(Component.literal(message == null ? "" : message));
    }

    public boolean shouldFilter(Component message) {
        if (!enabledSetting.getValue()) {
            return false;
        }
        String normalizedMessage =
                PacketTextNormalizer.normalizeForParsing(message == null ? null : message.getString());
        if (normalizedMessage.isEmpty()) {
            return false;
        }
        for (BuiltInFilter filter : builtInFilters) {
            if (filter.matches(message, normalizedMessage)) {
                if (filter.id().equals("gaz_death_message")) {
                    gazDeathMessageEffect.run();
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGazDmReminder(Component message, String normalizedMessage) {
        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);
        return parsed != null
                && GAZ_USERNAME.equals(parsed.username().toLowerCase(Locale.ROOT))
                && CHECK_YOUR_DMS_PATTERN.matcher(parsed.message()).find();
    }

    private boolean matchesEconomy(String normalizedMessage) {
        if (ECONOMY_RESOURCE_ALERT_PATTERN.matcher(normalizedMessage).matches()) {
            return true;
        }
        return !economyAlertsOnlySetting.getValue()
                && ECONOMY_ACTIVITY_PATTERN.matcher(normalizedMessage).matches();
    }

    private static boolean easterEggsEnabled() {
        return SeqClient.getEasterEggsSetting() != null
                && SeqClient.getEasterEggsSetting().getValue();
    }

    public static final class BuiltInFilter {
        private final String id;
        private final Setting.BooleanSetting enabledSetting;
        private final BiPredicate<Component, String> matcher;
        private final BooleanSupplier available;

        private BuiltInFilter(
                String id,
                Setting.BooleanSetting enabledSetting,
                BiPredicate<Component, String> matcher,
                BooleanSupplier available) {
            this.id = id;
            this.enabledSetting = enabledSetting;
            this.matcher = matcher;
            this.available = available;
        }

        public String id() {
            return id;
        }

        public Setting.BooleanSetting enabledSetting() {
            return enabledSetting;
        }

        private boolean matches(Component message, String normalizedMessage) {
            return available.getAsBoolean()
                    && enabledSetting.getValue()
                    && matcher.test(message, normalizedMessage);
        }
    }
}
