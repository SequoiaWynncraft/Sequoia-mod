package com.seqwawa.seq.managers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.network.ConnectionManager;

public class BombShareManager implements NotificationAccessor {

    private static final List<String> SUGGESTED_TOKENS = List.of("dxp", "prof", "profxp", "profspeed", "loot", "lc");
    private static final List<BombShareType> DISPLAY_ORDER = List.of(
            BombShareType.COMBAT_XP,
            BombShareType.PROFESSION_XP,
            BombShareType.PROFESSION_SPEED,
            BombShareType.LOOT,
            BombShareType.LOOT_CHEST);
    private static final Pattern WORLD_NUMBER_PATTERN = Pattern.compile("(?i)^([a-z]+)(\\d+)$");

    private final BombStateProvider bombStateProvider;
    private final ConcurrentLinkedDeque<String> pendingPromptIds = new ConcurrentLinkedDeque<>();

    public BombShareManager() {
        this(BombStateProviders.create());
    }

    BombShareManager(BombStateProvider bombStateProvider) {
        this.bombStateProvider = Objects.requireNonNull(bombStateProvider, "bombStateProvider");

        ConnectionManager.onBombSharePrompt(this::handlePrompt);
        ConnectionManager.onBombShareResult(this::handleResult);
    }

    public int requestBombShare(String rawSelectors) {
        ResolvedRequest request = resolveRequest(rawSelectors);
        if (request == null) {
            notify("Unknown bomb type. Try /seq bomb dxp, prof, profxp, profspeed, loot, or lc.");
            return 0;
        }
        if (!ConnectionManager.isConnected()) {
            notify("Not connected. Use /seq connect first.");
            return 0;
        }

        boolean sent = ConnectionManager.getInstance()
                .sendBombShareRequest(request.canonicalKey(), request.requestedTypes());
        if (!sent) {
            notify("Could not send bomb share request right now.");
            return 0;
        }

        notify("Requesting shared " + request.displayLabel() + " bomb worlds...");
        return 1;
    }

    public int sharePrompt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            notify("Missing bomb share request id.");
            return 0;
        }

        Optional<ConnectionManager.BombSharePromptMessage> prompt =
                ConnectionManager.getInstance().getPendingBombSharePrompt(requestId);
        if (prompt.isEmpty()) {
            removePendingPromptId(requestId);
            notify("That bomb share request is no longer available.");
            return 0;
        }

        if (prompt.get().expiresAt() != null && Instant.now().isAfter(prompt.get().expiresAt())) {
            ConnectionManager.getInstance().removePendingBombSharePrompt(requestId);
            removePendingPromptId(requestId);
            notify("That bomb share request expired.");
            return 0;
        }

        List<String> worlds = resolveActiveWorlds(prompt.get().requestedTypes());
        if (worlds.isEmpty()) {
            notify("No active " + buildDisplayLabel(prompt.get().requestedTypes()) + " bombs to share right now.");
            return 0;
        }

        boolean sent = ConnectionManager.getInstance().sendBombShareSubmit(requestId, worlds);
        if (!sent) {
            notify("Could not share bomb worlds right now.");
            return 0;
        }

        ConnectionManager.getInstance().removePendingBombSharePrompt(requestId);
        removePendingPromptId(requestId);
        notify("Shared " + buildDisplayLabel(prompt.get().requestedTypes()) + " bomb worlds.");
        return 1;
    }

    public int tryHotkeyShareLatestPendingPrompt() {
        String latestRequestId = latestPendingPromptId();
        if (latestRequestId == null) {
            return 0;
        }
        return sharePrompt(latestRequestId);
    }

    public int muteRequests() {
        Setting.BooleanSetting setting = SeqClient.getReceiveBombShareRequestsSetting();
        if (setting != null) {
            setting.setValue(false);
            SeqClient.getConfigManager().save();
        }
        notify("Bomb share requests disabled.");
        return 1;
    }

    private void handlePrompt(ConnectionManager.BombSharePromptMessage prompt) {
        if (!shouldShowPrompt(prompt)) {
            return;
        }
        rememberPendingPrompt(prompt.requestId());

        if (SeqClient.isBombShareHotkeyDown()) {
            if (sharePrompt(prompt.requestId()) > 0) {
                return;
            }
        }

        SeqClient.mc.execute(() -> {
            var player = SeqClient.mc.player;
            if (player == null) {
                return;
            }

            boolean firstPrompt = !SeqClient.getConfigManager().isBombSharePromptSeen();
            SeqClient.getConfigManager().setBombSharePromptSeen(true);

            MutableComponent promptMessage = NotificationAccessor.prefixComponent()
                    .append(Component.literal("Share " + buildDisplayLabel(prompt.requestedTypes()) + " bombs with "
                                    + prompt.requesterUsername() + "?")
                            .withStyle(ChatFormatting.GRAY));

            MutableComponent actionMessage = Component.empty()
                    .append(NotificationAccessor.wynnPill(
                            "Share",
                            ChatFormatting.GREEN,
                            ChatFormatting.WHITE,
                            new ClickEvent.RunCommand("/seq bomb _share " + prompt.requestId())));
            if (firstPrompt) {
                actionMessage = actionMessage
                        .append(Component.literal(" "))
                        .append(NotificationAccessor.wynnPill(
                                "Don't ask again",
                                ChatFormatting.RED,
                                ChatFormatting.WHITE,
                                new ClickEvent.RunCommand("/seq bomb _mute-requests")));
            }

            player.displayClientMessage(promptMessage, false);
            player.displayClientMessage(actionMessage, false);
        });
    }

    private void handleResult(ConnectionManager.BombShareResultMessage result) {
        removePendingPromptId(result.requestId());
        String displayLabel = buildDisplayLabel(result.requestedTypes());
        if (result.worlds().isEmpty()) {
            notify("No active " + displayLabel + " bomb worlds were shared.");
            return;
        }
        notify("Shared " + displayLabel + " bomb worlds: " + String.join(", ", result.worlds()));
    }

    private boolean shouldShowPrompt(ConnectionManager.BombSharePromptMessage prompt) {
        Setting.BooleanSetting setting = SeqClient.getReceiveBombShareRequestsSetting();
        if (setting != null && !setting.getValue()) {
            return false;
        }
        if (prompt == null || prompt.requestId() == null || prompt.requestId().isBlank()) {
            return false;
        }
        if (prompt.expiresAt() != null && Instant.now().isAfter(prompt.expiresAt())) {
            return false;
        }
        return !resolveActiveWorlds(prompt.requestedTypes()).isEmpty();
    }

    private void rememberPendingPrompt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        removePendingPromptId(requestId);
        pendingPromptIds.addLast(requestId);
    }

    private void removePendingPromptId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        pendingPromptIds.removeIf(requestId::equals);
    }

    private String latestPendingPromptId() {
        while (true) {
            String requestId = pendingPromptIds.peekLast();
            if (requestId == null) {
                return null;
            }
            if (ConnectionManager.getInstance().hasPendingBombSharePrompt(requestId)) {
                return requestId;
            }
            pendingPromptIds.pollLast();
        }
    }

    private List<String> resolveActiveWorlds(Collection<BombShareType> requestedTypes) {
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return List.of();
        }

        Set<String> worlds = new TreeSet<>(BombShareManager::compareWorldNames);
        for (BombStateProvider.ActiveBomb bomb : bombStateProvider.activeBombs()) {
            if (bomb == null || bomb.type() == null || bomb.world() == null || bomb.world().isBlank()) {
                continue;
            }
            if (!requestedTypes.contains(bomb.type())) {
                continue;
            }
            worlds.add(bomb.world().trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(worlds);
    }

    static ResolvedRequest resolveRequest(String rawSelectors) {
        if (rawSelectors == null || rawSelectors.isBlank()) {
            return null;
        }

        EnumSet<BombShareType> requestedTypes = EnumSet.noneOf(BombShareType.class);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String rawToken : rawSelectors.trim().split("\\s+")) {
            String token = rawToken == null ? "" : rawToken.trim().toLowerCase(Locale.ROOT);
            if (token.isBlank()) {
                continue;
            }
            tokens.add(token);
            expandToken(token, requestedTypes);
        }

        if (requestedTypes.isEmpty()) {
            return null;
        }

        return new ResolvedRequest(
                canonicalKeyFor(requestedTypes),
                List.copyOf(orderedTypes(requestedTypes)),
                buildDisplayLabel(requestedTypes),
                List.copyOf(tokens));
    }

    private static void expandToken(String token, Set<BombShareType> sink) {
        switch (token) {
            case "dxp", "combat", "combatxp" -> sink.add(BombShareType.COMBAT_XP);
            case "profxp", "pxp" -> sink.add(BombShareType.PROFESSION_XP);
            case "profspeed", "pspeed", "speed" -> sink.add(BombShareType.PROFESSION_SPEED);
            case "prof", "profs" -> {
                sink.add(BombShareType.PROFESSION_XP);
                sink.add(BombShareType.PROFESSION_SPEED);
            }
            case "loot" -> sink.add(BombShareType.LOOT);
            case "lootchest", "chest", "lc" -> sink.add(BombShareType.LOOT_CHEST);
            default -> {
            }
        }
    }

    public static List<String> suggestionsFor(String rawInput) {
        String remaining = rawInput == null ? "" : rawInput.toLowerCase(Locale.ROOT);
        String[] parts = remaining.split("\\s+");
        LinkedHashSet<String> used = new LinkedHashSet<>();
        boolean endsWithSpace = remaining.endsWith(" ");
        int completeCount = endsWithSpace ? parts.length : Math.max(0, parts.length - 1);
        for (int index = 0; index < completeCount; index++) {
            String part = parts[index].trim();
            if (!part.isEmpty()) {
                used.add(part);
            }
        }

        String currentPrefix = endsWithSpace || parts.length == 0 ? "" : parts[parts.length - 1].trim();
        List<String> suggestions = new ArrayList<>();
        for (String token : SUGGESTED_TOKENS) {
            if (used.contains(token)) {
                continue;
            }
            if (!currentPrefix.isEmpty() && !token.startsWith(currentPrefix)) {
                continue;
            }
            suggestions.add(token);
        }
        return suggestions;
    }

    static String canonicalKeyFor(Collection<BombShareType> requestedTypes) {
        return orderedTypes(requestedTypes).stream()
                .map(Enum::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + "+" + right)
                .orElse("");
    }

    static String buildDisplayLabel(Collection<BombShareType> requestedTypes) {
        List<BombShareType> orderedTypes = orderedTypes(requestedTypes);
        if (orderedTypes.isEmpty()) {
            return "bomb";
        }
        if (orderedTypes.size() == 2
                && orderedTypes.contains(BombShareType.PROFESSION_XP)
                && orderedTypes.contains(BombShareType.PROFESSION_SPEED)) {
            return "profession";
        }
        if (orderedTypes.size() == 1) {
            return orderedTypes.get(0).displayName();
        }

        List<String> labels = orderedTypes.stream().map(BombShareType::displayName).toList();
        return String.join(" + ", labels);
    }

    private static List<BombShareType> orderedTypes(Collection<BombShareType> requestedTypes) {
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return List.of();
        }

        List<BombShareType> ordered = new ArrayList<>();
        for (BombShareType type : DISPLAY_ORDER) {
            if (requestedTypes.contains(type)) {
                ordered.add(type);
            }
        }
        return ordered;
    }

    static int compareWorldNames(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        Matcher leftMatcher = WORLD_NUMBER_PATTERN.matcher(left);
        Matcher rightMatcher = WORLD_NUMBER_PATTERN.matcher(right);
        if (leftMatcher.matches() && rightMatcher.matches()
                && leftMatcher.group(1).equalsIgnoreCase(rightMatcher.group(1))) {
            int numberCompare =
                    Integer.compare(Integer.parseInt(leftMatcher.group(2)), Integer.parseInt(rightMatcher.group(2)));
            if (numberCompare != 0) {
                return numberCompare;
            }
        }

        return String.CASE_INSENSITIVE_ORDER.compare(left, right);
    }

    public record ResolvedRequest(
            String canonicalKey, List<BombShareType> requestedTypes, String displayLabel, List<String> rawTokens) {}

    public interface BombStateProvider {
        Collection<ActiveBomb> activeBombs();

        record ActiveBomb(BombShareType type, String world) {}
    }

    private static final class BombStateProviders {
        private BombStateProviders() {}

        private static BombStateProvider create() {
            try {
                return (BombStateProvider) Class.forName("com.seqwawa.seq.managers.WynntilsBombStateProvider")
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable throwable) {
                SeqClient.LOGGER.warn(
                        "[BombShare] Wynntils bomb provider unavailable; share prompts will be ignored. Cause: {}",
                        throwable.toString());
                return Collections::emptyList;
            }
        }
    }
}
