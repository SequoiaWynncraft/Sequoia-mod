package org.sequoia.seq.config;

import com.google.gson.*;
import lombok.Getter;
import org.sequoia.seq.client.SeqClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ConfigManager {
    private static final Path CONFIG_PATH = Path.of("config", "sequoia.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Setting<?>> settings = new ArrayList<>();

    public ConfigManager() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }

    public void register(Setting<?> setting) {
        settings.add(setting);
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            for (Setting<?> setting : settings) {
                String key = setting.getCategory() + "." + setting.getName();
                root.add(key, setting.serialize());
            }
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            SeqClient.LOGGER.error("Failed to save config", e);
        }
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        try (Reader reader = new InputStreamReader(
                new FileInputStream(CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (Setting<?> setting : settings) {
                String key = setting.getCategory() + "." + setting.getName();
                JsonElement element = root.get(key);
                if (element != null) {
                    setting.deserialize(element);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            SeqClient.LOGGER.error("Failed to load config", e);
        }
    }

}
