package com.stackmobs;

import com.stackmobs.commands.StackCommand;
import com.stackmobs.config.ConfigManager;
import com.stackmobs.events.EntityDeathHandler;
import com.stackmobs.events.EntitySpawnHandler;
import com.stackmobs.events.ServerTickHandler;
import com.stackmobs.stacking.StackManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary mod initialiser for <b>Stack Mobs</b>.
 * Loaded via the {@code "main"} entrypoint in {@code fabric.mod.json}.
 */
public class StackMobsMod implements ModInitializer {

    public static final String MOD_ID = "stackmobs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static StackMobsMod instance;

    public static StackMobsMod getInstance() { return instance; }

    private ConfigManager configManager;
    private StackManager  stackManager;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[StackMobs] Starting initialisation…");

        // 1. Configuration
        configManager = new ConfigManager();
        configManager.load();

        // 2. Stack engine
        stackManager = new StackManager(configManager.getConfig());

        // 3. Events
        ServerEntityEvents.ENTITY_LOAD.register(
            new EntitySpawnHandler(stackManager, configManager));

        ServerLivingEntityEvents.AFTER_DEATH.register(
            new EntityDeathHandler(stackManager, configManager));

        ServerTickEvents.END_SERVER_TICK.register(
            new ServerTickHandler(stackManager, configManager));

        // 4. Commands
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                StackCommand.register(dispatcher, registryAccess, stackManager, configManager)
        );

        LOGGER.info("[StackMobs] Ready. Stacking={} radius={} max={}",
            configManager.getConfig().enabled ? "ON" : "OFF",
            configManager.getConfig().stackRadius,
            configManager.getConfig().maxStackSize);
    }

    public ConfigManager getConfigManager() { return configManager; }
    public StackManager  getStackManager()  { return stackManager; }
}
