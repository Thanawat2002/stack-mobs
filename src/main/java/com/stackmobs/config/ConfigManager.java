package com.stackmobs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
        String error = tryLoad();
        if (error != null) {
            StackMobsMod.LOGGER.error("[StackMobs] {} Keeping the current settings.", error);
        }
    }

    /**
     * Reads and validates the config file. On failure (unreadable file or
     * malformed JSON) the current settings are kept and the file is left
     * untouched, so a typo can't crash the server or wipe the user's config.
     *
     * @return {@code null} on success, otherwise a description of the problem
     */
    private String tryLoad() {
        if (!Files.exists(configPath)) {
            StackMobsMod.LOGGER.info("[StackMobs] No config found – writing defaults to {}", configPath);
            save();
            return null;
        }

        ModConfig loaded;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            loaded = gson.fromJson(reader, ModConfig.class);
        } catch (IOException e) {
            return "Failed to read " + configPath + ": " + e.getMessage();
        } catch (JsonParseException e) {
            return "Invalid JSON in " + configPath + ": " + e.getMessage();
        }

        if (loaded == null) {
            StackMobsMod.LOGGER.warn("[StackMobs] Config file is empty – using defaults.");
            loaded = new ModConfig();
        }
        for (String fix : loaded.validate()) {
            StackMobsMod.LOGGER.warn("[StackMobs] Config: {}", fix);
        }
        config = loaded;
        StackMobsMod.LOGGER.info("[StackMobs] Configuration loaded from {}", configPath);
        return null;
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
     * Reloads the config from disk. On failure the current settings stay active.
     *
     * @return {@code null} on success, otherwise a human-readable error
     */
    public String reload() {
        return tryLoad();
    }

    /** @return the currently active configuration object */
    public ModConfig getConfig() { return config; }
}
