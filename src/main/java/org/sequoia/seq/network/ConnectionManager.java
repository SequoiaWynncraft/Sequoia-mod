package org.sequoia.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConnectionManager extends WebSocketClient implements NotificationAccessor {

    private static final String WS_URL = "ws://45.38.20.147:8081/ws";
    private static final Path TOKEN_FILE = Path.of(System.getProperty("user.home"), ".seq_token");
    private static final Gson GSON = new Gson();

    private static ConnectionManager instance;
    private String token;
    @Getter
    private boolean authenticated = false;
    private Consumer<List<String>> connectedUsersCallback;

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    private ConnectionManager() {
        super(URI.create(WS_URL));
        loadToken();
    }

    @Override
    public void connect() {
        if (getReadyState() != ReadyState.NOT_YET_CONNECTED) { // according to the goons this is only "state" im allowed to connnect otherwise its illegal and entire instance is basically fucked
            notify("Already connected/connecting");
            return;
        }

        notify("Connecting...");
        try {
            super.connect();
        } catch (Exception e) {
            SeqClient.LOGGER.error("Failed to connect", e);
            notify("Failed to connect: " + e.getMessage());
            instance = null;
        }
    }

    public void disconnect() {
        if (!isOpen()) {
            notify("Not connected");
            return;
        }
        close();
        authenticated = false;
        notify("Disconnected");
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        SeqClient.LOGGER.info("WebSocket connected");
        if (token != null) {
            authenticate();
        } else {
            requestAuth();
        }
    }

    @Override
    public void onMessage(String message) {
        handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        SeqClient.LOGGER.info("WebSocket closed: {} - {}", code, reason);
        authenticated = false;
        instance = null;
    }

    @Override
    public void onError(Exception ex) {
        SeqClient.LOGGER.error("WebSocket error", ex);
        notify("Connection error: " + ex.getMessage());
    }

    private void requestAuth() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "auth_request");
        msg.addProperty("minecraft_uuid", player.getUUID().toString());
        msg.addProperty("minecraft_username", player.getName().getString());
        send(GSON.toJson(msg));
    }

    private void authenticate() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "authenticate");
        msg.addProperty("token", token);
        send(GSON.toJson(msg));
    }

    public void requestConnectedUsers(Consumer<List<String>> callback) {
        if (!isOpen()) {
            callback.accept(List.of());
            return;
        }
        this.connectedUsersCallback = callback;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "get_connected");
        send(GSON.toJson(msg));
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "auth_challenge" -> {
                    String url = json.get("url").getAsString();
                    String code = json.get("code").getAsString();
                    notifyClickable("Click here to authenticate", url);
                    notify("Your code: " + code);
                }
                case "auth_success" -> {
                    token = json.get("token").getAsString();
                    String discordUser = json.get("discord_username").getAsString();
                    saveToken();
                    authenticated = true;
                    notify("Successfully linked to Discord: " + discordUser);
                }
                case "authenticated" -> {
                    String discordUser = json.get("discord_username").getAsString();
                    authenticated = true;
                    notify("Connected as " + discordUser);
                }
                case "connected_users" -> {
                    List<String> users = new ArrayList<>();
                    json.getAsJsonArray("users").forEach(el -> users.add(el.getAsString()));
                    if (connectedUsersCallback != null) {
                        connectedUsersCallback.accept(users);
                        connectedUsersCallback = null;
                    }
                }
                case "error" -> {
                    String error = json.get("message").getAsString();
                    notify("Error: " + error);
                    if (error.contains("expired") || error.contains("Invalid")) {
                        token = null;
                        deleteToken();
                    }
                    if (error.contains("guild")) {
                        close();
                    }
                }
            }
        } catch (Exception e) {
            SeqClient.LOGGER.error("Failed to handle message", e);
        }
    }

    private void loadToken() {
        try {
            if (Files.exists(TOKEN_FILE)) {
                token = Files.readString(TOKEN_FILE).trim();
            }
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to load token", e);
        }
    }

    private void saveToken() {
        try {
            Files.writeString(TOKEN_FILE, token);
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to save token", e);
        }
    }

    private void deleteToken() {
        try {
            Files.deleteIfExists(TOKEN_FILE);
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to delete token", e);
        }
    }

    public boolean isConnected() {
        return isOpen();
    }

}
