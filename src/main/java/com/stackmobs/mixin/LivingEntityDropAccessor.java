package com.stackmobs.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin that exposes the {@code protected} method
 * {@link LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)} so it can be
 * called from {@link com.stackmobs.stacking.StackManager} without reflection.
 *
 * <p>Calling {@code dropAllDeathLoot} triggers the full vanilla drop pipeline:
 * equipment drops, loot-table drops, and experience orbs – identical to a
 * natural entity death, but without actually removing the entity.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDropAccessor {

    /**
     * Invokes {@link LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)}.
     *
     * @param level  the server level in which drops are spawned
     * @param source damage source for loot-context conditions (e.g. killed_by_player)
     */
    @Invoker("dropAllDeathLoot")
    void invokeDropAllDeathLoot(ServerLevel level, DamageSource source);
}
