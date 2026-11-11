package com.stackmobs.mixin;

import com.stackmobs.StackMobsMod;
import com.stackmobs.config.ModConfig;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import com.stackmobs.stacking.StackedMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Batch-shearing: one shear action clips as many sheep as the shears have
 * durability for (up to the entire stack).
 *
 * <ul>
 *   <li>sheepToShear = min(stackSize, shears durability)</li>
 *   <li>Wool drops (1-3 per sheep) are spawned for every clipped sheep.</li>
 *   <li>Shears lose 1 durability per clipped sheep (vanilla already handles the
 *       first one; we apply the remainder manually).</li>
 *   <li>Every sheared sheep becomes its own entity (the primary plus spawned
 *       copies) so each regrows wool by eating grass, as in vanilla. Sheared
 *       sheep never stack; they rejoin a stack once their wool is back.</li>
 *   <li>The un-sheared remainder is spawned as a separate stack.</li>
 * </ul>
 */
@Mixin(Sheep.class)
public class SheepShearingMixin {

    /** Stack size captured at the start of shear(), before vanilla modifies anything. */
    @Unique private int stackmobs_stackSizeAtShear = 1;

    /** How many sheep will be clipped this action (bounded by shears durability). */
    @Unique private int stackmobs_sheepToShear = 1;

    // ── HEAD: capture state before vanilla shears ─────────────────────────────

    @Inject(
        method = "shear(Lnet/minecraft/server/level/ServerLevel;" +
                 "Lnet/minecraft/sounds/SoundSource;" +
                 "Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD")
    )
    private void stackmobs$captureShearState(ServerLevel level,
                                             SoundSource soundSource,
                                             ItemStack shears,
                                             CallbackInfo ci) {
        Sheep self = (Sheep) (Object) this;
        if (!(self instanceof StackedMob sm)) return;

        int stackSize  = sm.stackmobs$getStackSize();
        // Remaining uses = how many more 1-durability hits the shears can take
        int durability = shears.getMaxDamage() - shears.getDamageValue();

        stackmobs_stackSizeAtShear = stackSize;
        stackmobs_sheepToShear     = Math.min(stackSize, Math.max(1, durability));
    }

    // ── RETURN: drop extra wool, damage shears, split stack ───────────────────

    @Inject(
        method = "shear(Lnet/minecraft/server/level/ServerLevel;" +
                 "Lnet/minecraft/sounds/SoundSource;" +
                 "Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("RETURN")
    )
    private void stackmobs$batchShear(ServerLevel level,
                                      SoundSource soundSource,
                                      ItemStack shears,
                                      CallbackInfo ci) {
        Sheep self = (Sheep) (Object) this;

        if (!self.isSheared()) return;             // shearing didn't actually happen
        if (!(self instanceof StackedMob sm)) return;

        // Vanilla has already sheared the primary; the stack must be split
        // even while stacking is disabled or the whole stack counts as sheared.
        StackManager manager = StackManager.getInstance();
        if (manager == null) return;

        ModConfig config    = manager.getConfig();
        int stackSize       = stackmobs_stackSizeAtShear;
        int sheepToShear    = stackmobs_sheepToShear;

        if (stackSize <= 1) return; // nothing extra to do for a single sheep

        // ── Extra wool drops and shear durability ─────────────────────────────
        // Vanilla already handled 1 sheep (dropped loot, damaged shears by 1).
        // We handle the remaining (sheepToShear - 1) sheep manually.
        int extraShears = sheepToShear - 1;

        if (extraShears > 0) {
            Item woolItem = getWoolItem(self.getColor());
            Random rng    = new Random();

            for (int i = 0; i < extraShears; i++) {
                int woolCount = 1 + rng.nextInt(3); // 1–3 wool per sheep, matching vanilla range
                ItemEntity drop = new ItemEntity(
                    level,
                    self.getX(), self.getY() + 0.5, self.getZ(),
                    new ItemStack(woolItem, woolCount));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }

            // Damage shears (vanilla already spent 1 durability on the first sheep)
            int newDamage = shears.getDamageValue() + extraShears;
            if (!shears.isEmpty()) {
                if (newDamage >= shears.getMaxDamage()) {
                    shears.shrink(1); // shears break
                } else {
                    shears.setDamageValue(newDamage);
                }
            }
        }

        // ── Sheared sheep split into individual entities ──────────────────────
        // A stack can only hold one sheared flag, so a sheared stack would
        // regrow every sheep's wool the moment the primary eats grass. Each
        // sheared sheep is therefore its own entity; sheared sheep never stack
        // (see StackUtils.isStackable) and rejoin a stack once wool regrows.
        StackUtils.setStackSize(self, 1, config);
        manager.removeStack(self.getUUID());

        for (int i = 1; i < sheepToShear; i++) {
            try {
                Mob sheared = StackUtils.createCopy(self, level, 1.5); // inherits sheared=true
                if (sheared == null) continue;
                StackUtils.setStackSize(sheared, 1, config);
                level.addFreshEntity(sheared);
            } catch (Exception e) {
                StackMobsMod.LOGGER.warn(
                    "[StackMobs] Failed to spawn sheared sheep: {}", e.getMessage());
            }
        }

        // ── Spawn the un-sheared remainder as a separate entity ───────────────
        int remaining = stackSize - sheepToShear;
        if (remaining > 0) {
            try {
                if (!(StackUtils.createCopy(self, level, 1.0) instanceof Sheep unsheared)) return;
                unsheared.setSheared(false); // copy inherits the primary's new sheared state

                level.addFreshEntity(unsheared);
                StackUtils.setStackSize(unsheared, remaining, config);

                if (remaining > 1) {
                    manager.registerStack(unsheared, level);
                }
            } catch (Exception e) {
                StackMobsMod.LOGGER.warn(
                    "[StackMobs] Failed to spawn un-sheared sheep remainder: {}",
                    e.getMessage());
            }
        }
    }

    // ── Helper: wool item for a given DyeColor ────────────────────────────────

    private static Item getWoolItem(DyeColor color) {
        // 26.3+: per-colour wool items live in a ColorCollection keyed by DyeColor
        return Items.WOOL.pick(color);
    }
}
