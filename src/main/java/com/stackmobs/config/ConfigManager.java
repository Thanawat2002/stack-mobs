package com.stackmobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stackmobs.StackMobsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles reading and writing of {@link ModConfig} from/to disk.
 *
 * <p>Config file location: {@code <game_dir>/config/stackmobs.json}.
 *
 * <p>If the file is absent on first load, a default config is written
 * automatically so users have a documented template to customise.
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "stackmobs.json";

    private final Gson   gson;
    private final Path   configPath;
    private       ModConfig config;

    public ConfigManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

        this.configPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CONFIG_FILE);

        this.config = new ModConfig();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads the configuration from disk.
     * Creates a default config file if none exists.
     */
    public void load() {
        if (!Files.exists(configPath)) {
            StackMobsMod.LOGGER.info("[StackMobs] No config found – writing defaults to {}", configPath);
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ModConfig loaded = gson.fromJson(reader, ModConfig.class);
            if (loaded != null) {
                config = loaded;
                StackMobsMod.LOGGER.info("[StackMobs] Configuration loaded from {}", configPath);
            } else {
                StackMobsMod.LOGGER.warn("[StackMobs] Config file is empty – using defaults.");
            }
        } catch (IOException e) {
            StackMobsMod.LOGGER.error("[StackMobs] Failed to read config: {}", e.getMessage());
        }
    }

    /**
     * Saves the current in-memory configuration to disk.
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
            StackMobsMod.LOGGER.info("[StackMobs] Configuration saved to {}", configPath);
        } catch (IOException e) {
            StackMobsMod.LOGGER.error("[StackMobs] Failed to save config: {}", e.getMessage());
        }
    }

    /**
     * Reloads the config from disk and returns a result message suitable for
     * displaying to a command sender.
     *
     * @return human-readable result string
     */
    public String reload() {
        try {
            load();
            return "Configuration reloaded successfully.";
        } catch (Exception e) {
            return "Reload failed: " + e.getMessage();
        }
    }

    /** @return the currently active configuration object */
    public ModConfig getConfig() { return config; }
}
