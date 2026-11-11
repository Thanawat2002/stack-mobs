package com.stackmobs.mixin;

import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import com.stackmobs.stacking.StackedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spreads incoming damage across the virtual mobs in a stack.
 *
 * <p>Injected at HEAD of {@code actuallyHurt} (post-armor, post-enchantment
 * damage) so every hit — regardless of size — correctly kills as many virtual
 * members as the damage warrants.
 *
 * <h3>Formula</h3>
 * <pre>
 *   overflow        = damage - primaryCurrentHP          (≥ 0 only if primary dies)
 *   additionalKills = floor(overflow / maxHP)
 *   totalKills      = 1 + additionalKills
 *   survivorHP      = maxHP - (overflow mod maxHP)
 * </pre>
 *
 * <h3>Examples (mob maxHP = 10)</h3>
 * <pre>
 *   Hit 7,  HP 10/10 → 7 < 10: no kill, vanilla reduces HP to 3/10
 *   Hit 8,  HP  3/10 → overflow 5:  kills 1, next at  5/10
 *   Hit 15, HP 10/10 → overflow 5:  kills 1, next at  5/10
 *   Hit 25, HP 10/10 → overflow 15: kills 2, next at  5/10
 *   Hit 100,HP 10/10 → kills entire stack (vanilla handles entity death)
 * </pre>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(
        method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;" +
                 "Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stackmobs$spreadDamage(ServerLevel level,
                                         DamageSource source,
                                         float amount,
                                         CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Mob mob))   return;
        if (!(self instanceof StackedMob sm)) return;

        int stackSize = sm.stackmobs$getStackSize();
        if (stackSize <= 1) return; // single mob — vanilla handles everything

        // Runs even when stacking is disabled: otherwise one kill would wipe
        // out an entire existing stack with only a single mob's drops.
        StackManager manager = StackManager.getInstance();
        if (manager == null) return;

        float currentHealth = self.getHealth();
        float maxHealth     = self.getMaxHealth();

        // ── Hit doesn't kill the primary mob ─────────────────────────────────
        // Let vanilla reduce HP normally; no virtual mob dies.
        if (amount < currentHealth) return;

        // ── Calculate how many virtual mobs die ───────────────────────────────
        float overflow      = amount - currentHealth;
        int additionalKills = (int) Math.floor(overflow / maxHealth);
        int totalKills      = Math.min(1 + additionalKills, stackSize);

        // ── Entire stack wiped ────────────────────────────────────────────────
        // Issue drops for all virtual members; let vanilla kill the primary entity.
        if (totalKills >= stackSize) {
            for (int i = 0; i < stackSize - 1; i++) {
                manager.onStackMemberKilled(mob, level, source);
            }
            sm.stackmobs$setStackSize(1);
            self.setCustomName(null);
            self.setCustomNameVisible(false);
            return; // do NOT cancel → entity dies naturally
        }

        // ── Partial kill: some mobs survive ──────────────────────────────────
        for (int i = 0; i < totalKills; i++) {
            manager.onStackMemberKilled(mob, level, source);
        }

        int   newSize        = stackSize - totalKills;
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

        self.setHealth(survivorHealth);
        ci.cancel(); // prevent vanilla from zeroing out health
    }
}
