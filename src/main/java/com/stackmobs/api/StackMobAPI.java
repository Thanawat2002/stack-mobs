package com.stackmobs.api;

import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Public developer API for interacting with Stack Mobs from other mods.
 * All mutating calls are server-side only and are ignored on the client.
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

    /** Returns how many mobs {@code entity} represents (1 for non-stacked mobs). */
    int  getStackSize(LivingEntity entity);

    /** Sets the stack size (minimum 1) and updates the name tag and registry. */
    void setStackSize(LivingEntity entity, int amount);

    /**
     * Merges {@code source} into {@code target}. Does nothing unless both are
     * the same mob type and variant (same rules as automatic stacking) and the
     * combined size fits within {@code maxStackSize}.
     */
    void merge(LivingEntity source, LivingEntity target);

    /**
     * Splits {@code amount} members off the stack as individual mobs, keeping
     * the rest stacked. {@code amount} is clamped to stack size - 1.
     */
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
            if (!(entity.level() instanceof ServerLevel level)) return;
            StackManager manager = StackManager.getInstance();
            if (manager == null) return;
            StackUtils.setStackSize(mob, amount, manager.getConfig());
            manager.syncRegistry(mob, level);
        }

        @Override
        public void merge(LivingEntity source, LivingEntity target) {
            if (!(source instanceof Mob srcMob)) return;
            if (!(target instanceof Mob tgtMob)) return;
            if (srcMob == tgtMob || !srcMob.isAlive() || !tgtMob.isAlive()) return;
            if (!(target.level() instanceof ServerLevel level) || source.level() != level) return;

            StackManager manager = StackManager.getInstance();
            if (manager == null) return;
            if (!StackUtils.sameType(tgtMob, srcMob)) return;
            int combined = StackUtils.getStackSize(tgtMob) + StackUtils.getStackSize(srcMob);
            if (combined > manager.getConfig().maxStackSize) return; // would lose mobs
            manager.merge(level, tgtMob, srcMob);
        }

        @Override
        public void split(LivingEntity entity, int amount) {
            if (!(entity instanceof Mob mob)) return;
            StackManager manager = StackManager.getInstance();
            if (manager == null) return;
            manager.split(mob, amount);
        }
    }
}
