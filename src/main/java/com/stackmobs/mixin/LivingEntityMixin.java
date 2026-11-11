package com.stackmobs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import com.stackmobs.stacking.StackedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spreads incoming damage across the virtual mobs in a stack.
 *
 * <p>Wraps the {@code setHealth} call inside {@code actuallyHurt}, which
 * vanilla makes only after armour, enchantments, Resistance and absorption
 * have reduced the damage. The new health it is about to set tells us the
 * real damage dealt, so armoured mobs lose exactly as many members as a
 * vanilla mob would lose health.
 *
 * <h3>Formula</h3>
 * <pre>
 *   overflow        = -(new health)                   (≥ 0 only if primary dies)
 *   additionalKills = floor(overflow / maxHP)
 *   totalKills      = 1 + additionalKills
 *   survivorHP      = maxHP - (overflow mod maxHP)
 * </pre>
 *
 * <h3>Examples (mob maxHP = 10, damage after armour)</h3>
 * <pre>
 *   Hit 7,  HP 10/10 → 7 < 10: no kill, HP 3/10
 *   Hit 8,  HP  3/10 → overflow 5:  kills 1, next at  5/10
 *   Hit 15, HP 10/10 → overflow 5:  kills 1, next at  5/10
 *   Hit 25, HP 10/10 → overflow 15: kills 2, next at  5/10
 *   Hit 100,HP 10/10 → kills entire stack (vanilla handles entity death)
 * </pre>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    // ── Removal damage (/kill, /stackmob killall, falling into the void) ──────

    @Inject(
        method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;" +
                 "Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At("HEAD")
    )
    private void stackmobs$removeStackOnKill(ServerLevel level,
                                             DamageSource source,
                                             float amount,
                                             CallbackInfo ci) {
        // Remove the whole stack with only the primary's vanilla drops. Running
        // the loot table once per member would flood the world with items and
        // lag the server (999 loot rolls for a 1000-stack), or drop them into
        // the void where nobody can collect them anyway.
        if (!source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Mob mob) || !(self instanceof StackedMob sm)) return;
        if (sm.stackmobs$getStackSize() <= 1) return;

        StackManager manager = StackManager.getInstance();
        if (manager == null) return;

        sm.stackmobs$setStackSize(1);
        self.setCustomName(null);
        self.setCustomNameVisible(false);
        manager.syncRegistry(mob, level);
        // vanilla applies the damage and kills the primary
    }

    // ── Damage spill ──────────────────────────────────────────────────────────

    @WrapOperation(
        method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;" +
                 "Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V")
    )
    private void stackmobs$spreadDamage(LivingEntity self,
                                        float newHealth,
                                        Operation<Void> setHealth,
                                        @Local(argsOnly = true) ServerLevel level,
                                        @Local(argsOnly = true) DamageSource source) {
        // Hit doesn't kill the primary mob: vanilla reduces HP normally
        if (newHealth > 0
                || !(self instanceof Mob mob)
                || !(self instanceof StackedMob sm)
                || sm.stackmobs$getStackSize() <= 1) {
            setHealth.call(self, newHealth);
            return;
        }

        // Runs even when stacking is disabled: otherwise one kill would wipe
        // out an entire existing stack with only a single mob's drops.
        StackManager manager = StackManager.getInstance();
        if (manager == null) {
            setHealth.call(self, newHealth);
            return;
        }

        int   stackSize = sm.stackmobs$getStackSize();
        float maxHealth = self.getMaxHealth();

        // ── Calculate how many virtual mobs die ───────────────────────────────
        float overflow      = -newHealth;
        int additionalKills = (int) Math.floor(overflow / maxHealth);
        int totalKills      = Math.min(1 + additionalKills, stackSize);

        // ── Entire stack wiped ────────────────────────────────────────────────
        // Issue drops for all virtual members; vanilla kills the primary entity.
        if (totalKills >= stackSize) {
            for (int i = 0; i < stackSize - 1; i++) {
                manager.onStackMemberKilled(mob, level, source);
            }
            sm.stackmobs$setStackSize(1);
            self.setCustomName(null);
            self.setCustomNameVisible(false);
            setHealth.call(self, newHealth);
            return;
        }

        // ── Partial kill: some mobs survive ──────────────────────────────────
        for (int i = 0; i < totalKills; i++) {
            manager.onStackMemberKilled(mob, level, source);
        }

        int   newSize         = stackSize - totalKills;
        float damageRemainder = overflow % maxHealth;
        float survivorHealth  = Math.max(0.5f, maxHealth - damageRemainder);

        sm.stackmobs$setStackSize(newSize);

        if (newSize > 1) {
            self.setCustomName(StackUtils.buildNameTag(mob, newSize,
                manager.getConfig().showHealthBar));
            self.setCustomNameVisible(true);
        } else {
            self.setCustomName(null);
            self.setCustomNameVisible(false);
        }
        manager.syncRegistry(mob, level);

        // The next member steps in with the leftover damage applied
        setHealth.call(self, survivorHealth);
    }
}
