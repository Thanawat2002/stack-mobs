package com.stackmobs.events;

import com.stackmobs.StackMobsMod;
import com.stackmobs.config.ConfigManager;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Cleans up stack registry entries when an entity fully dies
 * (i.e. the last stack member reaches 0 HP).
 */
public class EntityDeathHandler implements ServerLivingEntityEvents.AfterDeath {

    private final StackManager  stackManager;
    private final ConfigManager configManager;

    public EntityDeathHandler(StackManager stackManager, ConfigManager configManager) {
        this.stackManager  = stackManager;
        this.configManager = configManager;
    }

    @Override
    public void afterDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof Mob mob)) return;

        stackManager.removeStack(mob.getUUID());

        int remainingSize = StackUtils.getStackSize(mob);
        if (remainingSize > 1) {
            StackMobsMod.LOGGER.warn(
                "[StackMobs] Entity {} died with stack size {}; missed death-intercept.",
                StackUtils.getEntityId(mob), remainingSize
            );
        }
    }
}
