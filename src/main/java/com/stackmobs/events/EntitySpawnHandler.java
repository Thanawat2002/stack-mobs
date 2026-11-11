package com.stackmobs.events;

import com.stackmobs.config.ConfigManager;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Listens for entities loading into a {@link ServerLevel} and restores
 * stacked name tags for mobs that were saved with a stack size &gt; 1.
 */
public class EntitySpawnHandler implements ServerEntityEvents.Load {

    private final StackManager  stackManager;
    private final ConfigManager configManager;

    public EntitySpawnHandler(StackManager stackManager, ConfigManager configManager) {
        this.stackManager  = stackManager;
        this.configManager = configManager;
    }

    @Override
    public void onLoad(Entity entity, ServerLevel level) {
        // Register every existing stack, even when stacking is disabled or the
        // mob no longer passes the filters: the registry drives baby growth and
        // name tags, which existing stacks need regardless.
        if (!(entity instanceof Mob mob))              return;

        int stackSize = StackUtils.getStackSize(mob);
        if (stackSize > 1) {
            StackUtils.setStackSize(mob, stackSize, configManager.getConfig());
            stackManager.registerStack(mob, level);
        }
    }
}
