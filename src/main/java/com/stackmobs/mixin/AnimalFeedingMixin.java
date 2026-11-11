package com.stackmobs.mixin;

import com.stackmobs.StackMobsMod;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackedMob;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Handles feeding interactions for stacked animals.
 *
 * <h3>Adult animals (breeding)</h3>
 * <p>When food is limited, only some of the stack eat — those that ate
 * split off into their own entity (with a 5-minute breed cooldown) while
 * the unfed remainder stays as a separate stack with no cooldown.
 * Each baby requires 2 food (1 per virtual parent), mirroring vanilla.
 *
 * <h3>Baby animals (growth)</h3>
 * <p>Vanilla feeds the primary with the first item; each further item feeds
 * one virtual baby, applying vanilla's speed-up to that baby's own age.
 */
@Mixin(Animal.class)
public abstract class AnimalFeedingMixin {

    // ── Shadow declarations ───────────────────────────────────────────────────

    @Shadow public abstract boolean isFood(ItemStack stack);
    @Shadow public abstract boolean canFallInLove();
    @Shadow public abstract boolean isInLove();
    @Shadow public abstract void setInLove(Player player);

    /** 6 000 ticks = 5 minutes, same as vanilla BreedGoal cooldown. */
    @Unique private static final long BREED_COOLDOWN_TICKS = 6_000L;

    // ── HEAD injection (adults) ───────────────────────────────────────────────

    @Inject(
        method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stackmobs$onFeedHead(Player player,
                                       InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {

        Animal self = (Animal) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        // Existing stacks keep stack-aware feeding while stacking is disabled
        StackManager manager = StackManager.getInstance();
        if (manager == null) return;

        int stackSize = StackUtils.getStackSize(self);
        if (stackSize < 2) return;

        ItemStack heldItem = player.getItemInHand(hand);
        if (!isFood(heldItem)) return;
        if (self.isBaby()) return; // handled in RETURN injection

        // ── Breed cooldown check ──────────────────────────────────────────────
        long gameTime = serverLevel.getGameTime();
        if (((StackedMob) self).stackmobs$isBreedCooldownActive(gameTime)) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        if (!canFallInLove() || isInLove()) return;

        // ── Calculate fed / unfed split ───────────────────────────────────────
        // Each baby costs 2 food (1 per virtual parent).
        int babies    = Math.min(stackSize / 2, heldItem.getCount() / 2);
        if (babies <= 0) return;

        int fedCount   = babies * 2;       // number of mobs that ate
        int unfedCount = stackSize - fedCount; // number of mobs that did NOT eat

        // ── Consume food ──────────────────────────────────────────────────────
        if (!player.getAbilities().instabuild) {
            heldItem.shrink(fedCount); // 1 food per fed mob
        }

        // ── Split unfed mobs into their own entity ────────────────────────────
        if (unfedCount > 0) {
            stackmobs$spawnUnfedGroup(serverLevel, self, unfedCount, manager);
        }

        // ── Primary entity = fed group (gets cooldown + breeds) ───────────────
        StackUtils.setStackSize(self, fedCount, manager.getConfig());
        ((StackedMob) self).stackmobs$setBreedCooldownUntil(gameTime + BREED_COOLDOWN_TICKS);

        // Hearts appear immediately, breeding deferred one tick so animation shows
        setInLove(player);

        final int babiesToSpawn = babies;
        serverLevel.getServer().execute(() -> {
            if (!self.isAlive()) return;
            for (int i = 0; i < babiesToSpawn; i++) {
                self.spawnChildFromBreeding(serverLevel, self);
                // finalizeSpawnChildFromBreeding resets love internally;
                // re-set for subsequent iterations.
                if (i < babiesToSpawn - 1) setInLove(player);
            }
        });

        StackMobsMod.LOGGER.debug(
            "[StackMobs] {} (size {}): fed={} unfed={} babies={} food consumed={}",
            StackUtils.getEntityId(self), stackSize,
            fedCount, unfedCount, babiesToSpawn, fedCount);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * Spawns a separate entity representing the mobs in the stack that were
     * NOT fed. The new entity has no breed cooldown and is positioned just
     * beside the primary entity.
     */
    @Unique
    private static void stackmobs$spawnUnfedGroup(ServerLevel level,
                                                   Animal primary,
                                                   int unfedCount,
                                                   StackManager manager) {
        try {
            // Keeps colour/variant and sheared state, so feeding a sheared
            // stack can't produce freshly woolly sheep
            Mob unfed = StackUtils.createCopy(primary, level, 2.0);
            if (unfed == null) return;

            StackUtils.setStackSize(unfed, unfedCount, manager.getConfig());
            level.addFreshEntity(unfed);
            manager.registerStack(unfed, level);

            StackMobsMod.LOGGER.debug(
                "[StackMobs] Spawned unfed group of {} {}.",
                unfedCount, StackUtils.getEntityId(unfed));
        } catch (Exception e) {
            StackMobsMod.LOGGER.warn("[StackMobs] Failed to spawn unfed group: {}", e.getMessage());
        }
    }

    // ── RETURN injection (babies) ─────────────────────────────────────────────

    @Inject(
        method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At("RETURN")
    )
    private void stackmobs$onFeedTail(Player player,
                                       InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {

        InteractionResult result = cir.getReturnValue();
        if (result != InteractionResult.SUCCESS && result != InteractionResult.CONSUME) return;

        Animal self = (Animal) (Object) this;
        if (!(self.level() instanceof ServerLevel)) return;
        if (!self.isBaby()) return;

        StackManager manager = StackManager.getInstance();
        if (manager == null) return;

        int stackSize = StackUtils.getStackSize(self);
        if (stackSize < 2) return;

        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.isEmpty() || !isFood(heldItem)) return;

        // Vanilla already fed the primary. Feed the virtual babies one item
        // each, applying vanilla's speed-up (10% of the remaining time).
        int[] ages = ((StackedMob) self).stackmobs$getVirtualAges();
        boolean creative = player.getAbilities().instabuild;
        int fed = creative ? ages.length : Math.min(ages.length, heldItem.getCount());
        if (fed <= 0) return;

        for (int i = 0; i < fed; i++) {
            int speedUpTicks = AgeableMob.getSpeedUpSecondsWhenFeeding(-ages[i]) * 20;
            // Reaching 0 grows the baby up on the next tick (processVirtualAges)
            ages[i] = Math.min(0, ages[i] + speedUpTicks);
        }
        ((StackedMob) self).stackmobs$setVirtualAges(ages);
        if (!creative) heldItem.shrink(fed);

        StackMobsMod.LOGGER.debug(
            "[StackMobs] Baby {} (size {}): fed {} virtual babies.",
            StackUtils.getEntityId(self), stackSize, fed);
    }
}
