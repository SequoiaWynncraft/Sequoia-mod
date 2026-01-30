package op.legends.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import op.legends.seq.client.SeqClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConnectionManager {

    private static final String WS_URL = "ws://localhost:8081/ws";
    private static final Path TOKEN_FILE = Path.of(System.getProperty("user.home"), ".seq_token");
    private static final Gson GSON = new Gson();

    private static ConnectionManager instance;
    private WebSocketClient client;
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
        loadToken();
    }

    public void connect() {
        if (client != null && client.isOpen()) {
            sendChat("Already connected");
            return;
        }

        sendChat("Connecting...");

        try {
            client = new WebSocketClient(new URI(WS_URL)) {
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
                }

                @Override
                public void onError(Exception ex) {
                    SeqClient.LOGGER.error("WebSocket error", ex);
                    sendChat("Connection error: " + ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            SeqClient.LOGGER.error("Failed to connect", e);
            sendChat("Failed to connect: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (client == null || !client.isOpen()) {
            sendChat("Not connected");
            return;
        }
        client.close();
        authenticated = false;
        sendChat("Disconnected");
    }

    private void requestAuth() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "auth_request");
        msg.addProperty("minecraft_uuid", player.getUUID().toString());
        msg.addProperty("minecraft_username", player.getName().getString());
        client.send(GSON.toJson(msg));
    }

    private void authenticate() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "authenticate");
        msg.addProperty("token", token);
        client.send(GSON.toJson(msg));
    }

    public void requestConnectedUsers(Consumer<List<String>> callback) {
        if (client == null || !client.isOpen()) {
            callback.accept(List.of());
            return;
        }
        this.connectedUsersCallback = callback;
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "get_connected");
        client.send(GSON.toJson(msg));
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "auth_challenge" -> {
                    String url = json.get("url").getAsString();
                    String code = json.get("code").getAsString();
                    sendClickableLink("Click here to authenticate", url);
                    sendChat("Your code: " + code);
                }
                case "auth_success" -> {
                    token = json.get("token").getAsString();
                    String discordUser = json.get("discord_username").getAsString();
                    saveToken();
                    authenticated = true;
                    sendChat("Successfully linked to Discord: " + discordUser);
                }
                case "authenticated" -> {
                    String discordUser = json.get("discord_username").getAsString();
                    authenticated = true;
                    sendChat("Connected as " + discordUser);
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
                    sendChat("Error: " + error);
                    if (error.contains("expired") || error.contains("Invalid")) {
                        token = null;
                        deleteToken();
                    }
                    if (error.contains("guild")) {
                        client.close();
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
        return client != null && client.isOpen();
    }

    private void sendChat(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("[Seq] " + message), false);
            }
        });
    }

    private void sendClickableLink(String text, String url) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                try {
                    URI uri = new URI(url);
                    MutableComponent link = Component.literal("[Seq] " + text)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent.OpenUrl(uri))
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true));
                    Minecraft.getInstance().player.displayClientMessage(link, false);
                } catch (URISyntaxException e) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("[Seq] " + text + ": " + url), false);
                }
            }
        });
    }
}
