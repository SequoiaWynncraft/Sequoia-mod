package com.seqwawa.seq.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsGuildRankAccess;
import com.seqwawa.seq.utils.PacketTextNormalizer;

public final class GuildRewardAutomationManager {
    private static final String GUILD_MANAGE_COMMAND = "gu manage";
    private static final String CINFRASCITIZEN = "cinfrascitizen";
    private static final String GUILD_ROOT_TITLE_PREFIX = "sequoia [lv.";
    private static final String NEXT_PAGE_LABEL = "next page";
    private static final int MENU_WAIT_TIMEOUT_TICKS = 80;
    private static final int PAGE_WAIT_TIMEOUT_TICKS = 40;
    private static final int MAX_PAGE_ADVANCES = 12;
    private static final int CLICK_DELAY_TICKS = 8;
    private static final int TARGET_STABILIZE_TICKS = 8;
    private static final int MAX_CLICKS_PER_RUN = 256;
    private static final Pattern REWARD_ACTION_PATTERN = Pattern.compile(
            "(?i)^press\\s+(\\d+)\\s+to\\s+send\\s+([\\d, ]+|an?|one)\\s+(.+?)$");

    private ActiveTask activeTask;

    public CompletableFuture<AutomationResult> sendAllEmeraldsToCinfrascitizen() {
        return start(RewardRequest.emeralds(CINFRASCITIZEN));
    }

    public CompletableFuture<AutomationResult> sendAllEmeraldsToCinfrascitizenInCurrentMenu() {
        return start(RewardRequest.emeralds(CINFRASCITIZEN), false);
    }

    public CompletableFuture<AutomationResult> sendTome(String username) {
        return start(RewardRequest.tome(username));
    }

    public CompletableFuture<AutomationResult> sendAspects(String username, long amount) {
        return start(RewardRequest.aspects(username, amount));
    }

    public CompletableFuture<AutomationResult> start(RewardRequest request) {
        return start(request, true);
    }

    private CompletableFuture<AutomationResult> start(RewardRequest request, boolean openGuildManage) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<AutomationResult> future = new CompletableFuture<>();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> startOnClientThread(request, future, openGuildManage));
        return future;
    }

    private void startOnClientThread(
            RewardRequest request, CompletableFuture<AutomationResult> future, boolean openGuildManage) {
        if (activeTask != null) {
            completeRejected(future, "Another guild reward command is already running.");
            return;
        }
        if (!WynntilsGuildRankAccess.isChiefOrOwner()) {
            completeRejected(future, "Guild rewards can only be sent by chiefs or owners.");
            return;
        }
        if (request.amount() <= 0) {
            completeRejected(future, "Reward amount must be greater than zero.");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            completeRejected(future, "Could not open guild manage menu: no player connection is available.");
            return;
        }
        if (minecraft.gameMode == null) {
            completeRejected(future, "Could not send guild reward: no game mode is available.");
            return;
        }

        AbstractContainerMenu currentMenu = currentMenu();
        boolean currentMenuLooksUsable = currentMenu != null
                && (findTargetSlot(currentMenu, request.targetUsername()) != null
                        || findGuildRootSlot(currentMenu) != null
                        || findNextPageSlot(currentMenu) != null
                        || GuildStorageTracker.extractCurrentEmeralds(currentMenu).isPresent());
        activeTask = new ActiveTask(
                request,
                future,
                currentMenu == null ? -1 : currentMenu.containerId,
                currentMenuLooksUsable,
                openGuildManage ? State.WAIT_FOR_MENU : State.FIND_TARGET);
        if (openGuildManage) {
            minecraft.player.connection.sendCommand(GUILD_MANAGE_COMMAND);
            notifyPlayer("Opening guild manage menu for " + request.targetUsername() + "...");
        }
        SeqClient.LOGGER.info("[GuildReward] Started automation type={} target={} amount={}",
                request.type(),
                request.targetUsername(),
                request.amount());
    }

    private void completeRejected(CompletableFuture<AutomationResult> future, String message) {
        notifyPlayer(message);
        future.complete(AutomationResult.failure(message));
    }

    public void tick() {
        if (activeTask == null) {
            return;
        }
        activeTask.tick();
        if (activeTask.done()) {
            activeTask = null;
        }
    }

    public boolean isRunning() {
        return activeTask != null;
    }

    static List<RewardAction> parseRewardActions(List<String> loreLines) {
        List<RewardAction> actions = new ArrayList<>();
        if (loreLines == null) {
            return actions;
        }

        for (String rawLine : loreLines) {
            String line = PacketTextNormalizer.normalizeForParsing(rawLine);
            Matcher matcher = REWARD_ACTION_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            int keyNumber = parseInt(matcher.group(1));
            long amount = parseAmount(matcher.group(2));
            RewardType type = RewardType.fromLoreLabel(matcher.group(3));
            if (keyNumber <= 0 || amount <= 0 || type == null) {
                continue;
            }
            actions.add(new RewardAction(type, keyNumber - 1, amount));
        }
        return actions;
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("a".equals(normalized) || "an".equals(normalized) || "one".equals(normalized)) {
            return 1;
        }
        try {
            return Long.parseLong(normalized.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Slot findTargetSlot(AbstractContainerMenu menu, String targetUsername) {
        if (menu == null || targetUsername == null || targetUsername.isBlank()) {
            return null;
        }
        String normalizedTarget = targetUsername.trim().toLowerCase(Locale.ROOT);
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            if (slot.getItem().getHoverName().getString().toLowerCase(Locale.ROOT).contains(normalizedTarget)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findNextPageSlot(AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            if (stackTextLines(slot.getItem()).stream()
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .anyMatch(line -> line.contains(NEXT_PAGE_LABEL))) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findGuildRootSlot(AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            String title = PacketTextNormalizer.normalizeForParsing(slot.getItem().getHoverName().getString())
                    .toLowerCase(Locale.ROOT);
            if (title.startsWith(GUILD_ROOT_TITLE_PREFIX)) {
                return slot;
            }
        }
        return null;
    }

    private static List<String> stackTextLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lines;
        }
        lines.add(PacketTextNormalizer.normalizeForParsing(stack.getHoverName().getString()));
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                lines.add(PacketTextNormalizer.normalizeForParsing(line.getString()));
            }
        }
        return lines;
    }

    private static Optional<RewardAction> findRewardAction(ItemStack stack, RewardType type) {
        return parseRewardActions(stackTextLines(stack)).stream()
                .filter(action -> action.type() == type)
                .findFirst();
    }

    private static AbstractContainerMenu currentMenu() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return null;
        }
        return containerScreen.getMenu();
    }

    private static void clickSlot(AbstractContainerMenu menu, Slot slot, int button, ClickType clickType) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, button, clickType, minecraft.player);
    }

    private static void notifyPlayer(String message) {
        NotificationAccessor.notifyPlayer(message);
    }

    public enum RewardType {
        EMERALDS("emeralds"),
        ASPECT("aspects"),
        TOME("guild tome");

        private final String displayName;

        RewardType(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        static RewardType fromLoreLabel(String rawLabel) {
            if (rawLabel == null) {
                return null;
            }
            String label = rawLabel.trim().toLowerCase(Locale.ROOT);
            if (label.startsWith("emerald")) {
                return EMERALDS;
            }
            if (label.startsWith("aspect")) {
                return ASPECT;
            }
            if (label.contains("tome")) {
                return TOME;
            }
            return null;
        }
    }

    public record RewardRequest(RewardType type, String targetUsername, long amount) {
        static RewardRequest emeralds(String targetUsername) {
            return new RewardRequest(RewardType.EMERALDS, targetUsername, Long.MAX_VALUE);
        }

        static RewardRequest tome(String targetUsername) {
            return new RewardRequest(RewardType.TOME, targetUsername, 1);
        }

        static RewardRequest aspects(String targetUsername, long amount) {
            return new RewardRequest(RewardType.ASPECT, targetUsername, amount);
        }
    }

    public record AutomationResult(boolean success, String message) {
        static AutomationResult success(String message) {
            return new AutomationResult(true, message);
        }

        static AutomationResult failure(String message) {
            return new AutomationResult(false, message);
        }
    }

    record RewardAction(RewardType type, int hotbarButton, long amountPerClick) {}

    private final class ActiveTask {
        private final RewardRequest request;
        private final CompletableFuture<AutomationResult> future;
        private final int initialContainerId;
        private final boolean initialMenuAllowed;
        private State state = State.WAIT_FOR_MENU;
        private int ticksInState;
        private int pageAdvances;
        private int clickDelay;
        private int clicksSent;
        private long remainingAmount;
        private boolean done;

        private ActiveTask(
                RewardRequest request,
                CompletableFuture<AutomationResult> future,
                int initialContainerId,
                boolean initialMenuAllowed,
                State initialState) {
            this.request = request;
            this.future = future;
            this.initialContainerId = initialContainerId;
            this.initialMenuAllowed = initialMenuAllowed;
            this.state = initialState;
            this.remainingAmount = request.amount();
        }

        private void tick() {
            if (done) {
                return;
            }
            ticksInState++;
            switch (state) {
                case WAIT_FOR_MENU -> tickWaitForMenu();
                case ENTER_GUILD_MEMBERS -> tickEnterGuildMembers();
                case WAIT_AFTER_GUILD_SELECT -> tickWaitAfterGuildSelect();
                case FIND_TARGET -> tickFindTarget();
                case WAIT_AFTER_TARGET_FOUND -> tickWaitAfterTargetFound();
                case WAIT_AFTER_PAGE -> tickWaitAfterPage();
                case CLICK_REWARD -> tickClickReward();
            }
        }

        private void tickWaitForMenu() {
            AbstractContainerMenu menu = currentMenu();
            if (menu != null && (initialMenuAllowed || menu.containerId != initialContainerId)) {
                transition(State.ENTER_GUILD_MEMBERS);
                return;
            }
            if (ticksInState >= MENU_WAIT_TIMEOUT_TICKS) {
                fail("Timed out waiting for /gu manage to open.");
            }
        }

        private void tickEnterGuildMembers() {
            AbstractContainerMenu menu = currentMenu();
            if (menu == null) {
                fail("Guild manage menu closed before selecting Sequoia.");
                return;
            }
            if (findTargetSlot(menu, request.targetUsername()) != null || findNextPageSlot(menu) != null) {
                transition(State.FIND_TARGET);
                return;
            }

            Slot guildSlot = findGuildRootSlot(menu);
            if (guildSlot == null) {
                fail("Could not find the Sequoia guild entry in /gu manage.");
                return;
            }

            clickSlot(menu, guildSlot, 0, ClickType.PICKUP);
            SeqClient.LOGGER.info("[GuildReward] Selected Sequoia guild entry at slot {}", guildSlot.index);
            transition(State.WAIT_AFTER_GUILD_SELECT);
        }

        private void tickWaitAfterGuildSelect() {
            if (ticksInState >= CLICK_DELAY_TICKS) {
                transition(State.FIND_TARGET);
            }
        }

        private void tickFindTarget() {
            AbstractContainerMenu menu = currentMenu();
            if (menu == null) {
                fail("Guild manage menu closed before " + request.targetUsername() + " was found.");
                return;
            }

            Slot targetSlot = findTargetSlot(menu, request.targetUsername());
            if (targetSlot != null) {
                transition(State.WAIT_AFTER_TARGET_FOUND);
                return;
            }

            Slot nextPageSlot = findNextPageSlot(menu);
            if (nextPageSlot == null) {
                fail("Could not find " + request.targetUsername() + " in the guild manage menu.");
                return;
            }
            if (pageAdvances >= MAX_PAGE_ADVANCES) {
                fail("Stopped after checking too many guild manage pages.");
                return;
            }

            clickSlot(menu, nextPageSlot, 0, ClickType.PICKUP);
            pageAdvances++;
            SeqClient.LOGGER.info("[GuildReward] Advanced to next page while searching for {}", request.targetUsername());
            transition(State.WAIT_AFTER_PAGE);
        }

        private void tickWaitAfterTargetFound() {
            AbstractContainerMenu menu = currentMenu();
            if (menu == null) {
                fail("Guild manage menu closed before rewards were sent.");
                return;
            }

            Slot targetSlot = findTargetSlot(menu, request.targetUsername());
            if (targetSlot == null) {
                transition(State.FIND_TARGET);
                return;
            }

            if (findRewardAction(targetSlot.getItem(), request.type()).isEmpty()) {
                if (ticksInState >= MENU_WAIT_TIMEOUT_TICKS) {
                    fail("Timed out waiting for " + request.targetUsername() + "'s reward actions to load.");
                }
                return;
            }

            if (ticksInState >= TARGET_STABILIZE_TICKS) {
                transition(State.CLICK_REWARD);
            }
        }

        private void tickWaitAfterPage() {
            if (ticksInState >= CLICK_DELAY_TICKS) {
                transition(State.FIND_TARGET);
                return;
            }
            if (ticksInState >= PAGE_WAIT_TIMEOUT_TICKS) {
                fail("Timed out waiting for the next guild manage page.");
            }
        }

        private void tickClickReward() {
            if (clickDelay > 0) {
                clickDelay--;
                return;
            }

            AbstractContainerMenu menu = currentMenu();
            if (menu == null) {
                fail("Guild manage menu closed before rewards were sent.");
                return;
            }

            Slot targetSlot = findTargetSlot(menu, request.targetUsername());
            if (targetSlot == null) {
                transition(State.FIND_TARGET);
                return;
            }

            Optional<RewardAction> action = findRewardAction(targetSlot.getItem(), request.type());
            if (action.isEmpty()) {
                fail("Could not find the " + request.type().displayName() + " action for " + request.targetUsername() + ".");
                return;
            }

            long amountToSend = amountToSend(menu, action.get());
            if (amountToSend <= 0) {
                succeed(successMessage());
                return;
            }
            if (clicksSent >= MAX_CLICKS_PER_RUN) {
                fail("Stopped after sending too many reward clicks.");
                return;
            }

            clickSlot(menu, targetSlot, action.get().hotbarButton(), ClickType.SWAP);
            clicksSent++;
            if (request.type() == RewardType.EMERALDS && remainingAmount != Long.MAX_VALUE) {
                remainingAmount = Math.max(0, remainingAmount - action.get().amountPerClick());
            } else if (request.type() != RewardType.EMERALDS) {
                remainingAmount = Math.max(0, remainingAmount - action.get().amountPerClick());
            }
            clickDelay = CLICK_DELAY_TICKS;
            SeqClient.LOGGER.info(
                    "[GuildReward] Sent click {} type={} target={} hotbarButton={} amountPerClick={}",
                    clicksSent,
                    request.type(),
                    request.targetUsername(),
                    action.get().hotbarButton(),
                    action.get().amountPerClick());
        }

        private long amountToSend(AbstractContainerMenu menu, RewardAction action) {
            if (request.type() == RewardType.EMERALDS) {
                OptionalLong currentEmeralds = GuildStorageTracker.extractCurrentEmeralds(menu);
                if (currentEmeralds.isPresent()) {
                    if (currentEmeralds.getAsLong() <= 0) {
                        return 0;
                    }
                    if (remainingAmount == Long.MAX_VALUE) {
                        remainingAmount = currentEmeralds.getAsLong();
                    }
                }
                return remainingAmount == Long.MAX_VALUE ? action.amountPerClick() : remainingAmount;
            }
            return remainingAmount;
        }

        private String successMessage() {
            return switch (request.type()) {
                case EMERALDS -> "Finished sending emeralds to " + request.targetUsername() + ".";
                case ASPECT -> "Finished sending " + request.amount() + " aspect(s) to " + request.targetUsername() + ".";
                case TOME -> "Finished sending 1 guild tome to " + request.targetUsername() + ".";
            };
        }

        private void transition(State nextState) {
            state = nextState;
            ticksInState = 0;
        }

        private void succeed(String message) {
            done = true;
            notifyPlayer(message);
            future.complete(AutomationResult.success(message));
        }

        private void fail(String message) {
            done = true;
            notifyPlayer(message);
            future.complete(AutomationResult.failure(message));
            SeqClient.LOGGER.warn("[GuildReward] {}", message);
        }

        private boolean done() {
            return done;
        }
    }

    private enum State {
        WAIT_FOR_MENU,
        ENTER_GUILD_MEMBERS,
        WAIT_AFTER_GUILD_SELECT,
        FIND_TARGET,
        WAIT_AFTER_TARGET_FOUND,
        WAIT_AFTER_PAGE,
        CLICK_REWARD
    }
}
