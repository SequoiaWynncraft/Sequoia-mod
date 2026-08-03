package com.seqwawa.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.managers.BombShareManager;
import com.seqwawa.seq.managers.GuildRewardAutomationManager;
import com.seqwawa.seq.managers.LeaderboardBadgeService;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.PartyListing;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.map.GatheringClusterCache;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.network.auth.AuthException;
import com.seqwawa.seq.ui.IngredientGuideScreen;
import com.seqwawa.seq.ui.WorldMapScreen;
import com.seqwawa.seq.ui.PartyFinderScreen;
import com.seqwawa.seq.utils.MinecraftUsername;
import com.seqwawa.seq.utils.PlayerNameCache;

public class SeqCommand {

        private static final List<String> ROLE_SUGGESTIONS = List.of("dps", "healer", "tank");
        private static final List<String> TREASURY_AMOUNT_SUGGESTIONS = List.of(
                        "50",
                        "50le",
                        "50s",
                        "2stx5le",
                        "2stx5le+1stx5le+4stx4le");

        public static void register() {
                ClientCommandRegistrationCallback.EVENT.register(SeqCommand::registerCommands);
        }

        static void registerCommands(
                        CommandDispatcher<FabricClientCommandSource> dispatcher,
                        CommandBuildContext registryAccess) {
                var root = ClientCommandManager.literal("seq")
                                .executes(ctx -> {
                                        SeqClient.openMainScreen();
                                        return 1;
                                });

                ConnectionCommandRegistrar.register(root);
                RewardCommandRegistrar.registerRewards(root);
                TreasuryCommandRegistrar.register(root);
                RewardCommandRegistrar.registerBadges(root);
                MapCommandRegistrar.register(root);
                PartyCommandRegistrar.register(root);

                dispatcher.register(root);
                RewardCommandRegistrar.registerStandaloneAliases(dispatcher);
        }

        static <S> LiteralArgumentBuilder<S> buildTreasuryCommand(TreasuryCommandExecutor<S> executor) {
                return TreasuryCommandRegistrar.buildCommand(executor);
        }

        static int runTreasuryOut(
                        CommandContext<FabricClientCommandSource> ctx, TreasuryCommandArguments arguments) {
                String activeUsername = SeqClient.mc == null || SeqClient.mc.getUser() == null
                                ? null
                                : SeqClient.mc.getUser().getName();
                TreasuryOutManager manager = SeqClient.getTreasuryOutManager();
                if (manager == null) {
                        sendFeedback(ctx.getSource(), "Treasury OUT is unavailable until SeqMod finishes loading.");
                        return 0;
                }
                boolean submitted = manager.submit(
                                activeUsername,
                                ConnectionManager.isTreasuryOutConnected(),
                                arguments.amount(),
                                arguments.payouter(),
                                arguments.reason(),
                                message -> sendFeedback(ctx.getSource(), message));
                return submitted ? 1 : 0;
        }

        static int openPartyScreen() {
                SeqClient.mc.execute(() -> SeqClient.mc.setScreen(new PartyFinderScreen(SeqClient.mc.screen)));
                return 1;
        }

        static int openWorldMapScreen(CommandContext<FabricClientCommandSource> ctx) {
                SeqClient.mc.execute(() -> SeqClient.mc.setScreen(new WorldMapScreen(SeqClient.mc.screen)));
                return 1;
        }

        static int openIngredientGuideScreen(CommandContext<FabricClientCommandSource> ctx) {
                SeqClient.mc.execute(() -> SeqClient.mc.setScreen(new IngredientGuideScreen(SeqClient.mc.screen)));
                return 1;
        }

        static int runStatus(CommandContext<FabricClientCommandSource> ctx) {
                boolean connected = ConnectionManager.isConnected();
                String token = SeqClient.getConfigManager().getToken();
                boolean hasToken = token != null && !token.isBlank();
                boolean tokenExpired = hasToken && SeqClient.getAuthService().isTokenExpired();
                String uptime = ConnectionManager.getInstance().getUptimeString();
                AuthException authError = SeqClient.getAuthService().getLastError();

                StringBuilder message = new StringBuilder();
                message.append("Connection: ").append(connected ? "connected" : "disconnected");
                message.append(" | Session: ").append(formatSessionStatus(hasToken, tokenExpired));
                message.append(" | Server: ").append(formatServerScope(WynncraftServerPolicy.currentScope()));

                if (connected && uptime != null) {
                        message.append(" | Uptime: ").append(uptime);
                }
                if (ConnectionManager.isAutoConnectSuppressedByManualDisconnect()) {
                        message.append(" | Auto-connect paused");
                }
                if (authError != null) {
                        message.append(" | Auth issue: ").append(authError.getMessage());
                }

                sendFeedback(ctx.getSource(), message.toString());
                return 1;
        }

        private static String formatSessionStatus(boolean hasToken, boolean tokenExpired) {
                if (!hasToken) {
                        return "not ready";
                }
                return tokenExpired ? "expired" : "ready";
        }

        private static String formatServerScope(WynncraftServerPolicy.Scope scope) {
                return switch (scope) {
                        case MAIN -> "Wynncraft";
                        case UNKNOWN -> "checking";
                        case BLOCKED -> "unsupported";
                };
        }

        static int runBadgeStatus(CommandContext<FabricClientCommandSource> ctx) {
                String rendererStatus = SeqClient.getSeqBadgeNametagRenderer() == null
                                ? "disabled"
                                : SeqClient.getSeqBadgeNametagRenderer().status();
                sendFeedback(
                                ctx.getSource(),
                                LeaderboardBadgeService.getInstance().status() + " | renderer=" + rendererStatus);
                return 1;
        }

        static int runBadgeRefresh(CommandContext<FabricClientCommandSource> ctx) {
                LeaderboardBadgeService.getInstance()
                                .refreshAsync()
                                .thenAccept(message -> sendFeedback(ctx.getSource(), message));
                sendFeedback(ctx.getSource(), "Refreshing leaderboard badges...");
                return 1;
        }

        static int runMapParams(CommandContext<FabricClientCommandSource> ctx) {
                WorldMapSettings settings = WorldMapSettings.getInstance();
                sendFeedback(
                                ctx.getSource(),
                                "Map clustering: " + settings.describe()
                                                + " | cached analyses="
                                                + GatheringClusterCache.getInstance().size());
                return 1;
        }

        static int runMapClusterEps(CommandContext<FabricClientCommandSource> ctx) {
                int epsBlocks = IntegerArgumentType.getInteger(ctx, "blocks");
                WorldMapSettings.getInstance().setClusterEps(epsBlocks);
                GatheringClusterCache.getInstance().clear();
                sendFeedback(
                                ctx.getSource(),
                                "Map cluster eps set to " + epsBlocks + " blocks. Cluster cache cleared.");
                return 1;
        }

        static int runMapClusterMinSamples(CommandContext<FabricClientCommandSource> ctx) {
                int minSamples = IntegerArgumentType.getInteger(ctx, "count");
                WorldMapSettings.getInstance().setClusterMinSamples(minSamples);
                GatheringClusterCache.getInstance().clear();
                sendFeedback(
                                ctx.getSource(),
                                "Map cluster minSamples set to " + minSamples + ". Cluster cache cleared.");
                return 1;
        }

        static int runMapClusterReset(CommandContext<FabricClientCommandSource> ctx) {
                WorldMapSettings.getInstance().resetClusterParams();
                GatheringClusterCache.getInstance().clear();
                sendFeedback(
                                ctx.getSource(),
                                "Map clustering reset to " + WorldMapSettings.getInstance().describe()
                                                + ". Cluster cache cleared.");
                return 1;
        }

        static int runMapDebugToggle(CommandContext<FabricClientCommandSource> ctx) {
                boolean enabled = WorldMapSettings.getInstance().toggleDebugInfo();
                GatheringMapImageService imageService = GatheringMapImageService.getInstance();
                sendFeedback(
                                ctx.getSource(),
                                "Map debug " + (enabled ? "enabled" : "disabled")
                                                + " | source="
                                                + displayMapImageSource(imageService.imageSource())
                                                + " | status="
                                                + imageService.hqStatus()
                                                + " | url="
                                                + imageService.hqMapUrl());
                return 1;
        }

        static int runMapClusterCacheStatus(CommandContext<FabricClientCommandSource> ctx) {
                sendFeedback(
                                ctx.getSource(),
                                "Map cluster cache entries: " + GatheringClusterCache.getInstance().size());
                return 1;
        }

        static int runMapClusterCacheClear(CommandContext<FabricClientCommandSource> ctx) {
                GatheringClusterCache.getInstance().clear();
                sendFeedback(ctx.getSource(), "Map cluster cache cleared.");
                return 1;
        }

        static int runMapImageCacheStatus(CommandContext<FabricClientCommandSource> ctx) {
                sendFeedback(ctx.getSource(), GatheringMapImageService.getInstance().cacheStatus());
                return 1;
        }

        static int runMapImageCacheClear(CommandContext<FabricClientCommandSource> ctx) {
                sendFeedback(ctx.getSource(), GatheringMapImageService.getInstance().clearCache());
                return 1;
        }

        static CompletableFuture<Suggestions> suggestBombSelectors(
                        CommandContext<FabricClientCommandSource> ctx,
                        SuggestionsBuilder builder) {
                for (String suggestion : BombShareManager.suggestionsFor(builder.getRemaining())) {
                        builder.suggest(suggestion);
                }
                return builder.buildFuture();
        }

        static int openPartyCreateScreen() {
                SeqClient.mc.execute(() -> SeqClient.mc.setScreen(new PartyFinderScreen(SeqClient.mc.screen, true)));
                return 1;
        }

        static int runPartyList(CommandContext<FabricClientCommandSource> ctx) {
                FabricClientCommandSource source = ctx.getSource();
                PartyFinderManager manager = SeqClient.getPartyFinderManager();
                manager.refreshListingsForCommand().whenComplete((result, error) -> {
                        if (error != null) {
                                sendFeedback(source, "Unexpected error while loading party listings.");
                                return;
                        }
                        if (!result.success()) {
                                sendFeedback(source, result.message());
                                return;
                        }

                        List<Listing> listings = result.data();
                        if (listings == null || listings.isEmpty()) {
                                sendFeedback(source, "No Sequoia party listings found.");
                                return;
                        }

                        sendFeedback(source, "Party listings (" + listings.size() + "):");
                        Listing currentListing = manager.getCurrentListing();
                        for (Listing listing : listings) {
                                boolean isCurrent = currentListing != null && currentListing.id() == listing.id();
                                sendFeedback(source, formatListingSummary(listing, isCurrent));
                        }
                });
                return 1;
        }

        static int runQueuedAspectReward(CommandContext<FabricClientCommandSource> ctx) {
                long amount = LongArgumentType.getLong(ctx, "amount");
                return runQueuedGuildReward(ctx, GuildRewardAutomationManager.RewardType.ASPECT, amount);
        }

        static int runDirectAspectReward(CommandContext<FabricClientCommandSource> ctx) {
                long amount = LongArgumentType.getLong(ctx, "amount");
                String username = StringArgumentType.getString(ctx, "username");
                if (!isValidMinecraftUsername(username)) {
                        sendFeedback(ctx.getSource(), "IGN must be a Minecraft username: 3-16 letters, numbers, or underscores.");
                        return 0;
                }
                SeqClient.getGuildRewardAutomationManager().sendAspects(username, amount);
                return 1;
        }

        static int runDirectTomeReward(CommandContext<FabricClientCommandSource> ctx, String username) {
                if (!isValidMinecraftUsername(username)) {
                        sendFeedback(ctx.getSource(), "IGN must be a Minecraft username: 3-16 letters, numbers, or underscores.");
                        return 0;
                }
                SeqClient.getGuildRewardAutomationManager().sendTome(username);
                return 1;
        }

        static int runRewardQueueRequest(
                        CommandContext<FabricClientCommandSource> ctx,
                        String type,
                        String reason) {
                FabricClientCommandSource source = ctx.getSource();
                ApiClient.getInstance().createRewardQueueRequest(type, reason).whenComplete((ignored, error) -> {
                        if (error != null) {
                                sendFeedback(
                                                source,
                                                "Could not submit " + rewardRequestLabel(type) + " request: "
                                                                + describeRewardQueueRequestFailure(error, type));
                                return;
                        }

                        sendFeedback(source, rewardRequestLabel(type) + " request submitted.");
                });
                return 1;
        }

        private static String rewardRequestLabel(String type) {
                return "tome".equals(type) ? "Tome" : "Aspects";
        }

        private static String describeRewardQueueRequestFailure(Throwable error, String type) {
                Throwable cause = unwrapCompletionException(error);
                if (cause instanceof ApiClient.ApiException apiException && apiException.getStatusCode() == 409) {
                        return "You already have a pending "
                                        + rewardRequestLabel(type).toLowerCase(Locale.ROOT)
                                        + " request in the queue.";
                }
                return describeApiFailure(error, "Backend request failed.");
        }

        private static String describeApiFailure(Throwable error, String fallback) {
                Throwable cause = unwrapCompletionException(error);
                if (cause instanceof ApiClient.ApiException apiException) {
                        String message = readApiMessage(apiException.getResponseBody());
                        if (message != null && !message.isBlank()) {
                                return message;
                        }
                }
                String message = cause == null ? null : cause.getMessage();
                return message == null || message.isBlank() ? fallback : message;
        }

        private static Throwable unwrapCompletionException(Throwable error) {
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                }
                return cause;
        }

        private static String readApiMessage(String responseBody) {
                if (responseBody == null || responseBody.isBlank()) {
                        return null;
                }
                try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        if (json.has("message") && json.get("message").isJsonPrimitive()) {
                                return json.get("message").getAsString();
                        }
                        if (json.has("error") && json.get("error").isJsonPrimitive()) {
                                return json.get("error").getAsString();
                        }
                } catch (IllegalStateException | JsonSyntaxException ignored) {
                        return null;
                }
                return null;
        }

        static int runQueuedGuildReward(
                        CommandContext<FabricClientCommandSource> ctx,
                        GuildRewardAutomationManager.RewardType rewardType,
                        long amount) {
                FabricClientCommandSource source = ctx.getSource();
                String type = rewardType == GuildRewardAutomationManager.RewardType.TOME ? "tome" : "aspect";
                ApiClient.getInstance().getFirstRewardQueueEntry(type).whenComplete((response, error) -> {
                        if (error != null) {
                                sendFeedback(source, "Could not load " + type + " reward queue.");
                                return;
                        }
                        if (response == null || response.entry() == null) {
                                sendFeedback(source, "No pending " + type + " reward queue entry.");
                                return;
                        }

                        ApiClient.RewardQueueEntry entry = response.entry();
                        String username = entry.minecraftUsername();
                        if (!isValidMinecraftUsername(username)) {
                                sendFeedback(source, "Queued " + type + " entry has an invalid IGN.");
                                return;
                        }

                        CompletableFuture<GuildRewardAutomationManager.AutomationResult> automation =
                                        rewardType == GuildRewardAutomationManager.RewardType.TOME
                                                        ? SeqClient.getGuildRewardAutomationManager().sendTome(username)
                                                        : SeqClient.getGuildRewardAutomationManager()
                                                                        .sendAspects(username, amount);
                        automation.whenComplete((result, automationError) -> {
                                if (automationError != null || result == null || !result.success()) {
                                        return;
                                }
                                ApiClient.getInstance().completeRewardQueueEntry(entry.requestId())
                                                .whenComplete((ignored, claimError) -> {
                                                        if (claimError != null) {
                                                                sendFeedback(
                                                                                source,
                                                                                "Reward sent, but queue completion failed for request #"
                                                                                                + entry.requestId()
                                                                                                + ".");
                                                        } else {
                                                                sendFeedback(
                                                                                source,
                                                                                "Completed reward queue request #"
                                                                                                + entry.requestId()
                                                                                                + ".");
                                                        }
                                                });
                        });
                });
                return 1;
        }

        static int runPartyStatus(CommandContext<FabricClientCommandSource> ctx) {
                FabricClientCommandSource source = ctx.getSource();
                PartyFinderManager manager = SeqClient.getPartyFinderManager();
                manager.refreshListingsForCommand().whenComplete((result, error) -> {
                        if (error != null) {
                                sendFeedback(source, "Unexpected error while loading party status.");
                                return;
                        }
                        if (!result.success()) {
                                sendFeedback(source, result.message());
                                return;
                        }

                        Listing currentListing = manager.getCurrentListing();
                        if (currentListing == null) {
                                sendFeedback(source, "You are not currently in a Sequoia party.");
                                return;
                        }

                        sendFeedback(source, "Current party: " + formatListingSummary(currentListing, true));
                        sendFeedback(
                                        source,
                                        manager.isPartyLeader()
                                                        ? "You are the party leader."
                                                        : "You are a party member.");
                });
                return 1;
        }

        static int runPartyCreate(CommandContext<FabricClientCommandSource> ctx) {
                List<String> activities = parseActivitiesInput(
                                StringArgumentType.getString(ctx, "activities"));
                if (activities.isEmpty()) {
                        sendFeedback(ctx.getSource(), "Provide at least one activity.");
                        return 0;
                }
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().createPartyFromCommand(activities));
        }

        static int runPartyUpdate(CommandContext<FabricClientCommandSource> ctx) {
                List<String> activities = parseActivitiesInput(
                                StringArgumentType.getString(ctx, "activities"));
                if (activities.isEmpty()) {
                        sendFeedback(ctx.getSource(), "Provide at least one activity.");
                        return 0;
                }
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().updatePartyFromCommand(activities));
        }

        static int runPartyJoin(
                        CommandContext<FabricClientCommandSource> ctx,
                        PartyRole role,
                        String inviteToken) {
                long listingId = LongArgumentType.getLong(ctx, "listingId");
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().joinPartyFromCommand(listingId, role, inviteToken));
        }

        static int runPartyJoinWithRole(CommandContext<FabricClientCommandSource> ctx) {
                PartyRole role = parseRole(StringArgumentType.getString(ctx, "role"));
                if (role == null) {
                        sendFeedback(ctx.getSource(), "Role must be one of: DPS, Healer, Tank.");
                        return 0;
                }
                return runPartyJoin(ctx, role, null);
        }

        static int runPartyJoinWithToken(CommandContext<FabricClientCommandSource> ctx) {
                return runPartyJoin(
                                ctx,
                                PartyRole.DPS,
                                StringArgumentType.getString(ctx, "inviteToken"));
        }

        static int runPartyJoinWithRoleAndToken(CommandContext<FabricClientCommandSource> ctx) {
                PartyRole role = parseRole(StringArgumentType.getString(ctx, "role"));
                if (role == null) {
                        sendFeedback(ctx.getSource(), "Role must be one of: DPS, Healer, Tank.");
                        return 0;
                }
                return runPartyJoin(ctx, role, StringArgumentType.getString(ctx, "inviteToken"));
        }

        static int runPartyDeny(CommandContext<FabricClientCommandSource> ctx) {
                long listingId = LongArgumentType.getLong(ctx, "listingId");
                sendFeedback(ctx.getSource(), "Dismissed party invite for #" + listingId + ".");
                return 1;
        }

        static int runIgnoreBridgeUser(CommandContext<FabricClientCommandSource> ctx) {
                String username = StringArgumentType.getString(ctx, "username");
                if (!ConfigManager.isValidBridgeUsername(username)) {
                        sendFeedback(
                                        ctx.getSource(),
                                        "IGN must be a Minecraft username: 3-16 letters, numbers, or underscores.");
                        return 0;
                }

                if (SeqClient.getConfigManager().addIgnoredBridgeUser(username)) {
                        sendFeedback(ctx.getSource(), "Ignoring Discord bridge messages from " + username.trim() + ".");
                } else {
                        sendFeedback(
                                        ctx.getSource(),
                                        "Already ignoring Discord bridge messages from " + username.trim() + ".");
                }
                return 1;
        }

        static int runUnignoreBridgeUser(CommandContext<FabricClientCommandSource> ctx) {
                String username = StringArgumentType.getString(ctx, "username");
                if (!ConfigManager.isValidBridgeUsername(username)) {
                        sendFeedback(
                                        ctx.getSource(),
                                        "IGN must be a Minecraft username: 3-16 letters, numbers, or underscores.");
                        return 0;
                }

                if (SeqClient.getConfigManager().removeIgnoredBridgeUser(username)) {
                        sendFeedback(
                                        ctx.getSource(),
                                        "Discord bridge messages from " + username.trim() + " are visible again.");
                } else {
                        sendFeedback(
                                        ctx.getSource(),
                                        "Discord bridge messages from " + username.trim() + " were not ignored.");
                }
                return 1;
        }

        static int runIgnoredBridgeUsersList(CommandContext<FabricClientCommandSource> ctx) {
                List<String> ignoredUsers = SeqClient.getConfigManager().ignoredBridgeUsers();
                if (ignoredUsers.isEmpty()) {
                        sendFeedback(ctx.getSource(), "No Discord bridge users are ignored.");
                        return 1;
                }

                sendFeedback(ctx.getSource(), "Ignored Discord bridge users: " + String.join(", ", ignoredUsers));
                return 1;
        }

        static int runPartyInvite(CommandContext<FabricClientCommandSource> ctx) {
                String username = StringArgumentType.getString(ctx, "username");
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().createInviteFromCommand(username));
        }

        static int runPartyReserve(CommandContext<FabricClientCommandSource> ctx) {
                int count = IntegerArgumentType.getInteger(ctx, "count");
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().setReservedSlotTargetFromCommand(count));
        }

        static int runPartyRole(CommandContext<FabricClientCommandSource> ctx) {
                PartyRole role = parseRole(StringArgumentType.getString(ctx, "role"));
                if (role == null) {
                        sendFeedback(ctx.getSource(), "Role must be one of: DPS, Healer, Tank.");
                        return 0;
                }
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().changeRoleFromCommand(role));
        }

        static int runPartyKick(CommandContext<FabricClientCommandSource> ctx) {
                String username = StringArgumentType.getString(ctx, "username");
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().kickMemberFromCommand(username));
        }

        static int runPartyPromote(CommandContext<FabricClientCommandSource> ctx) {
                String username = StringArgumentType.getString(ctx, "username");
                return relayCommandResult(
                                ctx,
                                SeqClient.getPartyFinderManager().promoteMemberFromCommand(username));
        }

        static <T> int relayCommandResult(
                        CommandContext<FabricClientCommandSource> ctx,
                        CompletableFuture<PartyFinderManager.CommandResult<T>> future) {
                FabricClientCommandSource source = ctx.getSource();
                future.whenComplete((result, error) -> {
                        if (error != null) {
                                sendFeedback(source, "Unexpected command failure.");
                                return;
                        }
                        if (result != null && result.message() != null && !result.message().isBlank()) {
                                sendFeedback(source, result.message());
                        }
                });
                return 1;
        }

        static CompletableFuture<Suggestions> suggestRoles(
                        CommandContext<FabricClientCommandSource> ctx,
                        SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggest(ROLE_SUGGESTIONS, builder);
        }

        static <S> CompletableFuture<Suggestions> suggestTreasuryAmounts(
                        CommandContext<S> ctx,
                        SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggest(TREASURY_AMOUNT_SUGGESTIONS, builder);
        }

        static CompletableFuture<Suggestions> suggestActivities(
                        CommandContext<FabricClientCommandSource> ctx,
                        SuggestionsBuilder builder) {
                String remaining = builder.getRemaining();
                int lastCommaIndex = remaining.lastIndexOf(',');
                int segmentStart = lastCommaIndex >= 0 ? lastCommaIndex + 1 : 0;
                while (segmentStart < remaining.length() && Character.isWhitespace(remaining.charAt(segmentStart))) {
                        segmentStart++;
                }

                String segment = remaining.substring(segmentStart);
                if (!segment.isEmpty() && (segment.charAt(0) == '"' || segment.charAt(0) == '\'')) {
                        segment = segment.substring(1);
                }

                SuggestionsBuilder segmentBuilder = builder.createOffset(builder.getStart() + segmentStart);
                String loweredSegment = segment.toLowerCase(Locale.ROOT);
                List<String> matches = PartyListing.activityCommandAliases()
                                .stream()
                                .filter(alias -> alias.toLowerCase(Locale.ROOT).startsWith(loweredSegment))
                                .toList();
                return SharedSuggestionProvider.suggest(matches, segmentBuilder);
        }

        static CompletableFuture<Suggestions> suggestIgnoredBridgeUsers(
                        CommandContext<FabricClientCommandSource> ctx,
                        SuggestionsBuilder builder) {
                return SharedSuggestionProvider.suggest(SeqClient.getConfigManager().ignoredBridgeUsers(), builder);
        }

        private static List<String> parseActivitiesInput(String rawActivities) {
                List<String> activities = new ArrayList<>();
                if (rawActivities == null || rawActivities.isBlank()) {
                        return activities;
                }

                StringBuilder current = new StringBuilder();
                char activeQuote = 0;
                for (int i = 0; i < rawActivities.length(); i++) {
                        char ch = rawActivities.charAt(i);
                        if ((ch == '"' || ch == '\'') && (activeQuote == 0 || activeQuote == ch)) {
                                activeQuote = activeQuote == 0 ? ch : 0;
                                continue;
                        }
                        if (ch == ',' && activeQuote == 0) {
                                addActivityToken(activities, current.toString());
                                current.setLength(0);
                                continue;
                        }
                        current.append(ch);
                }
                addActivityToken(activities, current.toString());
                return activities;
        }

        private static void addActivityToken(List<String> activities, String rawToken) {
                if (rawToken == null) {
                        return;
                }
                String normalized = rawToken.trim();
                if (normalized.length() >= 2
                                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                                                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
                        normalized = normalized.substring(1, normalized.length() - 1).trim();
                }
                if (!normalized.isEmpty()) {
                        activities.add(normalized);
                }
        }

        private static PartyRole parseRole(String rawRole) {
                        if (rawRole == null) {
                                return null;
                        }
                        return switch (rawRole.trim().toLowerCase(Locale.ROOT)) {
                                case "dps" -> PartyRole.DPS;
                                case "healer" -> PartyRole.HEALER;
                                case "tank" -> PartyRole.TANK;
                                default -> null;
                        };
        }

        private static boolean isValidMinecraftUsername(String username) {
                return MinecraftUsername.normalize(username) != null;
        }

        private static String formatListingSummary(Listing listing, boolean isCurrent) {
                String prefix = isCurrent ? "* " : "• ";
                String activities = listing.resolvedActivities()
                                .stream()
                                .map(Activity::name)
                                .map(PartyListing::backendNameToDisplayName)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse("Unknown Activity");
                String leaderName = PlayerNameCache.resolve(listing.leaderUUID());
                boolean leaderResolvable = leaderName != null
                                && !leaderName.isBlank()
                                && !"Loading...".equalsIgnoreCase(leaderName)
                                && !"Unknown".equalsIgnoreCase(leaderName);
                String leaderSegment = leaderResolvable ? " | Leader: " + leaderName : "";

                return prefix + "#" + listing.id()
                                + " | "
                                + activities
                                + " | "
                                + formatEnumLabel(listing.mode().name())
                                + " | "
                                + listing.occupiedSlotCount()
                                + "/"
                                + listing.maxPartySize()
                                + " | "
                                + formatEnumLabel(listing.status().name())
                                + leaderSegment;
        }

        private static String formatEnumLabel(String raw) {
                if (raw == null || raw.isBlank()) {
                        return "";
                }
                String lower = raw.toLowerCase(Locale.ROOT);
                return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        private static String displayMapImageSource(GatheringMapImageService.Source source) {
                return switch (source) {
                        case NONE -> "none";
                        case FALLBACK -> "fallback";
                        case CACHED_HQ -> "cached HQ";
                };
        }

        static void sendFeedback(FabricClientCommandSource source, String message) {
                SeqClient.mc.execute(() -> source.sendFeedback(NotificationAccessor.prefixed(message)));
        }

        record TreasuryCommandArguments(String amount, String payouter, String reason) {}

        @FunctionalInterface
        interface TreasuryCommandExecutor<S> {
                int execute(CommandContext<S> context, TreasuryCommandArguments arguments);
        }

}
