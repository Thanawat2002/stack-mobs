package com.stackmobs.mixin;

import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import com.stackmobs.stacking.StackedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Intercepts {@link AgeableMob#setAge(int)} to handle the moment a
 * <em>primary</em> baby entity in a stack would normally grow into an adult.
 *
 * <p>Instead of letting vanilla convert the primary entity to an adult
 * (which would make the entire stack adult at once), this mixin:
 * <ol>
 *   <li>Spawns one fresh adult mob near the primary.</li>
 *   <li>Decrements the stack size by one.</li>
 *   <li>If virtual-baby ages remain, promotes the earliest one to be the
 *       primary's new vanilla age so the primary stays a baby.</li>
 *   <li>If no virtual babies remain, allows vanilla to finish the grow-up
 *       normally (the last baby becomes the primary adult).</li>
 * </ol>
 */
@Mixin(AgeableMob.class)
public abstract class AgeableMobMixin {

    /** Direct access to the underlying age field to avoid mixin re-entry. */
    @Shadow protected int age;

    /**
     * Flag to prevent recursive triggering when we set the age ourselves
     * after intercepting vanilla's grow-up.
     */
    @Unique
    private boolean stackmobs_skipGrowUpCheck = false;

    @Inject(method = "setAge(I)V", at = @At("HEAD"), cancellable = true)
    private void stackmobs$interceptGrowUp(int newAge, CallbackInfo ci) {
        // Skip if we set the age ourselves after a previous intercept
        if (stackmobs_skipGrowUpCheck) {
            stackmobs_skipGrowUpCheck = false;
            return;
        }

        // Only intercept the baby → adult transition (age goes from negative to 0+)
        if (newAge < 0) return;

        AgeableMob self = (AgeableMob) (Object) this;

        // Guard: only intercept if the entity is currently a baby
        if (!self.isBaby()) return;

        // Guard: must be a Mob on the server
        if (!(self instanceof Mob mob)) return;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        // Guard: mod must be active
        StackManager manager = StackManager.getInstance();
        if (manager == null || !manager.getConfig().enabled) return;

        // Guard: only handle stacked entities
        int stackSize = StackUtils.getStackSize(mob);
        if (stackSize < 2) return;

        int[] virtualAges = ((StackedMob) mob).stackmobs$getVirtualAges();

        // ── One virtual baby exists or more ──────────────────────────────────
        // Spawn an adult for the baby that just grew up
        manager.spawnAdult(serverLevel, mob);
        StackUtils.setStackSize(mob, stackSize - 1, manager.getConfig());

        if (virtualAges.length > 0) {
            // Promote the first virtual age as the new primary age
            // and remove it from the virtual-ages array.
            int nextAge = virtualAges[0];
            int[] remaining = Arrays.copyOfRange(virtualAges, 1, virtualAges.length);
            ((StackedMob) mob).stackmobs$setVirtualAges(remaining);

            // Set the age directly on the field to avoid re-triggering this mixin,
            // then cancel vanilla's setAge(0) so the entity stays a baby.
            stackmobs_skipGrowUpCheck = true;
            this.age = nextAge;
            ci.cancel();
        }
        // else: no more virtual babies → let vanilla proceed (primary grows up last)
    }
}
