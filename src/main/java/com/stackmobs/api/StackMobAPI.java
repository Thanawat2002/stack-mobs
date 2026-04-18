package com.stackmobs.api;

import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Public developer API for interacting with Stack Mobs from other mods.
 *
 * <pre>{@code
 * int size = StackMobAPI.INSTANCE.getStackSize(myZombie);
 * StackMobAPI.INSTANCE.setStackSize(myZombie, 10);
 * StackMobAPI.INSTANCE.merge(zombieA, zombieB);
 * StackMobAPI.INSTANCE.split(bigZombie, 5);
 * }</pre>
 */
public interface StackMobAPI {

    /** Singleton default implementation backed by {@link StackManager}. */
    StackMobAPI INSTANCE = new DefaultImpl();

    int  getStackSize(LivingEntity entity);
    void setStackSize(LivingEntity entity, int amount);
    void merge(LivingEntity source, LivingEntity target);
    void split(LivingEntity entity, int amount);

    // ── Default implementation ────────────────────────────────────────────────

    final class DefaultImpl implements StackMobAPI {

        @Override
        public int getStackSize(LivingEntity entity) {
            if (!(entity instanceof Mob mob)) return 1;
            return StackUtils.getStackSize(mob);
        }

        @Override
        public void setStackSize(LivingEntity entity, int amount) {
            if (!(entity instanceof Mob mob)) return;
            com.stackmobs.StackMobsMod mod = com.stackmobs.StackMobsMod.getInstance();
            if (mod == null) return;
            StackUtils.setStackSize(mob, amount, mod.getConfigManager().getConfig());
        }

        @Override
        public void merge(LivingEntity source, LivingEntity target) {
            if (!(source instanceof Mob srcMob)) return;
            if (!(target instanceof Mob tgtMob)) return;
            if (!(target.level() instanceof ServerLevel level)) return;

            StackManager manager = StackManager.getInstance();
            if (manager == null) return;
            manager.merge(level, tgtMob, srcMob);
        }

        @Override
        public void split(LivingEntity entity, int amount) {
            if (!(entity instanceof Mob mob)) return;
            StackManager manager = StackManager.getInstance();
            if (manager == null) return;
            manager.unstack(mob);
        }
    }
}
