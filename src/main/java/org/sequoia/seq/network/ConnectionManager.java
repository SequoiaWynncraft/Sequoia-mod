package org.sequoia.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.sequoia.seq.model.WynnClassType;
import org.sequoia.seq.utils.WynnClassCache;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ConnectionManager extends WebSocketClient implements NotificationAccessor {

    private static final Gson GSON = new Gson();
    private static final long RECONNECT_BASE_MS = 1_000;
    private static final long RECONNECT_CAP_MS = 60_000;

    private static ConnectionManager instance;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "seq-reconnect");
        t.setDaemon(true);
        return t;
    });

    @Getter
    private boolean authenticated = false;
    @Getter
    private Instant connectedSince;
    private Consumer<List<String>> connectedUsersCallback;

    // Reconnect state
    private static boolean autoReconnect = true;
    private static int reconnectAttempt = 0;
    private static ScheduledFuture<?> reconnectTask;

    // Callbacks for new message types
    private static Consumer<DiscordChatMessage> discordChatHandler;
    private static Consumer<PartyFinderUpdateMessage> partyFinderUpdateHandler;
    private static Consumer<PartyFinderInviteMessage> partyFinderInviteHandler;

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    private ConnectionManager() {
        super(URI.create(BuildConfig.WS_URL));
    }

    // ── Connect / Disconnect ──

    @Override
    public void connect() {
        SeqClient.LOGGER.info(
                "[WebSocket] connect() called open={} authenticated={} autoReconnect={} url={}",
                isOpen(),
                authenticated,
                autoReconnect,
                BuildConfig.WS_URL);
        if (isOpen()) {
            notify("Already connected/connecting");
            return;
        }

        notify("Connecting to " + BuildConfig.ENVIRONMENT + "...");
        try {
            super.connect();
        } catch (Exception e) {
            SeqClient.LOGGER.error("Failed to connect", e);
            notify("Failed to connect: " + e.getMessage());
            instance = null;
            scheduleReconnect();
        }
    }

    public void disconnect() {
        SeqClient.LOGGER.info(
                "[WebSocket] disconnect() called open={} authenticated={} autoReconnect={}",
                isOpen(),
                authenticated,
                autoReconnect);
        autoReconnect = false;
        cancelReconnect();
        if (!isOpen()) {
            notify("Not connected");
            return;
        }
        close();
        authenticated = false;
        connectedSince = null;
        notify("Disconnected");
    }

    // ── WebSocket lifecycle ──

    @Override
    public void onOpen(ServerHandshake handshake) {
        SeqClient.LOGGER.info(
                "[WebSocket] onOpen url={} status={} message='{}'",
                BuildConfig.WS_URL,
                handshake != null ? handshake.getHttpStatus() : -1,
                handshake != null ? handshake.getHttpStatusMessage() : "null");
        reconnectAttempt = 0;
        String token = SeqClient.getConfigManager().getToken();
        if (token != null) {
            SeqClient.LOGGER.info("[WebSocket] Authenticating with stored token (length={})", token.length());
            sendAuthenticate(token);
        } else {
            SeqClient.LOGGER.info("[WebSocket] No stored token found; requesting auth challenge");
            requestAuth();
        }
    }

    @Override
    public void onMessage(String message) {
        SeqClient.LOGGER.debug("[WebSocket] onMessage raw={} chars", message != null ? message.length() : -1);
        handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        SeqClient.LOGGER.info(
                "[WebSocket] onClose code={} reason='{}' remote={} autoReconnect={}",
                code,
                reason,
                remote,
                autoReconnect);
        authenticated = false;
        connectedSince = null;
        instance = null;
        if (autoReconnect && remote) {
            SeqClient.LOGGER.info("[WebSocket] Scheduling reconnect after remote close");
            scheduleReconnect();
        } else {
            SeqClient.LOGGER.info("[WebSocket] Reconnect not scheduled (autoReconnect={} remote={})", autoReconnect,
                    remote);
        }
    }

    @Override
    public void onError(Exception ex) {
        SeqClient.LOGGER.error(
                "[WebSocket] onError open={} authenticated={} message={}",
                isOpen(),
                authenticated,
                ex != null ? ex.getMessage() : "null",
                ex);
        notify("Connection error: " + ex.getMessage());
    }

    // ── Auto-reconnect ──

    private static void scheduleReconnect() {
        cancelReconnect();
        long delay = Math.min(RECONNECT_BASE_MS * (1L << reconnectAttempt), RECONNECT_CAP_MS);
        reconnectAttempt++;
        SeqClient.LOGGER.info("[WebSocket] Reconnecting in {}ms (attempt {})", delay, reconnectAttempt);
        reconnectTask = scheduler.schedule(() -> {
            instance = null;
            try {
                SeqClient.LOGGER.info("[WebSocket] Running scheduled reconnect attempt {}", reconnectAttempt);
                getInstance().connect();
            } catch (Exception e) {
                SeqClient.LOGGER.error("[WebSocket] Reconnect failed", e);
                scheduleReconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static void cancelReconnect() {
        if (reconnectTask != null) {
            SeqClient.LOGGER.debug("[WebSocket] Cancelling pending reconnect task");
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    // ── Outgoing messages ──

    private void send(String type, JsonObject payload) {
        if (payload == null)
            payload = new JsonObject();
        payload.addProperty("type", type);
        SeqClient.LOGGER.debug("[WebSocket] send type={} payload={}", type, truncate(payload.toString(), 512));
        send(GSON.toJson(payload));
    }

    private void requestAuth() {
        var player = Minecraft.getInstance().player;
        if (player == null)
            return;

        JsonObject msg = new JsonObject();
        msg.addProperty("minecraft_uuid", player.getUUID().toString());
        msg.addProperty("minecraft_username", player.getName().getString());
        send("auth_request", msg);
    }

    private void sendAuthenticate(String token) {
        JsonObject msg = new JsonObject();
        msg.addProperty("token", token);
        send("authenticate", msg);
    }

    public void requestConnectedUsers(Consumer<List<String>> callback) {
        SeqClient.LOGGER.info("[WebSocket] requestConnectedUsers open={} authenticated={}", isOpen(), authenticated);
        if (!isOpen()) {
            callback.accept(List.of());
            return;
        }
        this.connectedUsersCallback = callback;
        send("get_connected", null);
    }

    public void sendGuildChat(String username, String message, String avatarUrl) {
        if (!isOpen()) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: socket not open");
            return;
        }
        if (!authenticated) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: not authenticated");
            return;
        }

        SeqClient.LOGGER.info("[ConnectionManager] Sending guild_chat username='{}' message='{}'", username, message);
        JsonObject msg = new JsonObject();
        msg.addProperty("username", username);
        msg.addProperty("message", message);
        if (avatarUrl != null)
            msg.addProperty("avatar_url", avatarUrl);
        send("guild_chat", msg);
    }

    public void sendRaidAnnouncement(List<String> usernames, String raidType,
            int aspectCount, int emeraldCount,
            double experienceCount, int srCount) {
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendRaidAnnouncement dropped open={} authenticated={}",
                    isOpen(),
                    authenticated);
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Sending guild_raid_announcement type={} usernames={}", raidType,
                usernames.size());
        JsonObject msg = new JsonObject();
        JsonArray names = new JsonArray();
        usernames.forEach(names::add);
        msg.add("usernames", names);
        msg.addProperty("raid_type", raidType);
        msg.addProperty("aspect_count", aspectCount);
        msg.addProperty("emerald_count", emeraldCount);
        msg.addProperty("experience_count", experienceCount);
        msg.addProperty("sr_count", srCount);
        send("guild_raid_announcement", msg);
    }

    public void sendPartyClassUpdate(WynnClassType classType) {
        if (!authenticated || !isOpen() || classType == null) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartyClassUpdate dropped open={} authenticated={} classType={}",
                    isOpen(),
                    authenticated,
                    classType);
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Sending party_class_update classType={}", classType);
        JsonObject msg = new JsonObject();
        msg.addProperty("class_type", classType.name());
        send("party_class_update", msg);
    }

    public void sendLocalPartyClassUpdate() {
        WynnClassType classType = WynnClassCache.resolveLocalClassType();
        if (classType == null) {
            return;
        }
        sendPartyClassUpdate(classType);
    }

    // ── Incoming message handler ──

    private void handleMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            if (json == null || !json.has("type")) {
                SeqClient.LOGGER.warn("[WebSocket] Dropping message without type: {}", truncate(message, 512));
                return;
            }
            String type = json.get("type").getAsString();
            SeqClient.LOGGER.info("[WebSocket] Received message type={} payload={}", type, truncate(message, 512));

            switch (type) {
                case "auth_challenge" -> {
                    String url = json.get("url").getAsString();
                    String code = json.get("code").getAsString();
                    notifyClickable("Click here to authenticate", url);
                    notify("Your code: " + code);
                }
                case "auth_success" -> {
                    String token = json.get("token").getAsString();
                    String discordUser = json.get("discord_username").getAsString();
                    SeqClient.getConfigManager().setToken(token);
                    authenticated = true;
                    connectedSince = Instant.now();
                    autoReconnect = true;
                    notify("Successfully linked to Discord: " + discordUser);
                    sendLocalPartyClassUpdate();
                }
                case "authenticated" -> {
                    String discordUser = json.get("discord_username").getAsString();
                    authenticated = true;
                    connectedSince = Instant.now();
                    autoReconnect = true;
                    notify("Connected as " + discordUser);
                    sendLocalPartyClassUpdate();
                }
                case "connected_users" -> {
                    List<String> users = new ArrayList<>();
                    json.getAsJsonArray("users").forEach(el -> users.add(el.getAsString()));
                    SeqClient.LOGGER.info(
                            "[WebSocket] connected_users received count={} callbackPresent={}",
                            users.size(),
                            connectedUsersCallback != null);
                    if (connectedUsersCallback != null) {
                        connectedUsersCallback.accept(users);
                        connectedUsersCallback = null;
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] connected_users had no callback listener");
                    }
                }
                case "discord_chat" -> {
                    if (discordChatHandler != null) {
                        String username = json.get("username").getAsString();
                        String msg = json.get("message").getAsString();
                        SeqClient.LOGGER.info("[WebSocket] Dispatching discord_chat from {}", username);
                        discordChatHandler.accept(new DiscordChatMessage(username, msg));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received discord_chat but handler is not registered");
                    }
                }
                case "party_finder_update" -> {
                    if (partyFinderUpdateHandler != null) {
                        String action = json.get("action").getAsString();
                        JsonObject listingJson = json.getAsJsonObject("listing");
                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_update action={} hasListing={}",
                                action,
                                listingJson != null);
                        partyFinderUpdateHandler.accept(new PartyFinderUpdateMessage(action, listingJson));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received party_finder_update but handler is not registered");
                    }
                }
                case "party_finder_invite" -> {
                    if (partyFinderInviteHandler != null) {
                        long listingId = json.get("listing_id").getAsLong();
                        String inviterUUID = json.get("inviter_uuid").getAsString();
                        String inviteToken = json.get("invite_token").getAsString();

                        JsonObject listingJson = null;
                        if (json.has("listing") && json.get("listing").isJsonObject()) {
                            listingJson = json.getAsJsonObject("listing");
                        }

                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_invite listingId={} inviterUUID={} tokenPresent={} hasListing={}",
                                listingId,
                                inviterUUID,
                                inviteToken != null && !inviteToken.isBlank(),
                                listingJson != null);

                        partyFinderInviteHandler.accept(
                                new PartyFinderInviteMessage(
                                        listingId,
                                        inviterUUID,
                                        inviteToken,
                                        listingJson));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received party_finder_invite but handler is not registered");
                    }
                }
                case "error" -> {
                    String error = json.get("message").getAsString();
                    SeqClient.LOGGER.warn("[WebSocket] Backend error message={}", error);
                    notify("Error: " + error);
                    if (error.contains("expired") || error.contains("Invalid")) {
                        SeqClient.getConfigManager().clearToken();
                    }
                    if (error.contains("guild")) {
                        SeqClient.LOGGER
                                .warn("[WebSocket] Guild error detected; disabling auto-reconnect and closing socket");
                        autoReconnect = false;
                        close();
                    }
                }
                default -> SeqClient.LOGGER.warn("[WebSocket] Unhandled incoming message type={}", type);
            }
        } catch (Exception e) {
            SeqClient.LOGGER.error("[WebSocket] Failed to handle message payload={}", truncate(message, 512), e);
        }
    }

    // ── Handler registration ──

    public static void onDiscordChat(Consumer<DiscordChatMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering discord_chat handler present={}", handler != null);
        discordChatHandler = handler;
    }

    public static void onPartyFinderUpdate(Consumer<PartyFinderUpdateMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering party_finder_update handler present={}", handler != null);
        partyFinderUpdateHandler = handler;
    }

    public static void onPartyFinderInvite(Consumer<PartyFinderInviteMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering party_finder_invite handler present={}", handler != null);
        partyFinderInviteHandler = handler;
    }

    private static String truncate(String input, int maxLength) {
        if (input == null) {
            return "null";
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength) + "...";
    }

    // ── Utility ──

    public static boolean isConnected() {
        return instance != null && instance.isOpen() && instance.authenticated;
    }

    public String getEnvironment() {
        return BuildConfig.ENVIRONMENT;
    }

    public String getUptimeString() {
        if (connectedSince == null)
            return null;
        java.time.Duration dur = java.time.Duration.between(connectedSince, Instant.now());
        long hours = dur.toHours();
        long mins = dur.toMinutesPart();
        long secs = dur.toSecondsPart();
        if (hours > 0)
            return hours + "h " + mins + "m";
        if (mins > 0)
            return mins + "m " + secs + "s";
        return secs + "s";
    }

    // ── Message records ──

    public record DiscordChatMessage(String username, String message) {
    }

    public record PartyFinderUpdateMessage(String action, JsonObject listingJson) {
    }

    public record PartyFinderInviteMessage(
            long listingId,
            String inviterUUID,
            String inviteToken,
            JsonObject listingJson) {
    }
}
