package com.stackmobs.events;

import com.stackmobs.config.ConfigManager;
import com.stackmobs.stacking.StackManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Drives the periodic mob-merge scan by delegating to
 * {@link StackManager#onServerTick(MinecraftServer)} every server tick.
 */
public class ServerTickHandler implements ServerTickEvents.EndTick {

    private final StackManager  stackManager;
    private final ConfigManager configManager;

    public ServerTickHandler(StackManager stackManager, ConfigManager configManager) {
        this.stackManager  = stackManager;
        this.configManager = configManager;
    }

    @Override
    public void onEndTick(MinecraftServer server) {
        // StackManager decides what still runs while stacking is disabled
        stackManager.onServerTick(server);
    }
}
