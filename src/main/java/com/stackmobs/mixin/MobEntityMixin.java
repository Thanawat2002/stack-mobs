package com.stackmobs.mixin;

import com.stackmobs.stacking.StackedMob;
import com.stackmobs.util.Constants;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects stack-size and virtual-ages fields into every {@link Mob} subclass
 * and implements the {@link StackedMob} interface.
 * Persists via the {@link ValueOutput}/{@link ValueInput} API introduced in MC 26.1.
 */
@Mixin(Mob.class)
public abstract class MobEntityMixin implements StackedMob {

    @Unique private int   stackmobs_stackSize          = 1;
    @Unique private int[] stackmobs_virtualAges         = new int[0];
    @Unique private long  stackmobs_breedCooldownUntil  = 0L;

    // ── StackedMob ────────────────────────────────────────────────────────────

    @Override public int   stackmobs$getStackSize()       { return stackmobs_stackSize; }
    @Override public void  stackmobs$setStackSize(int s)  { stackmobs_stackSize = Math.max(1, s); }

    @Override
    public int[] stackmobs$getVirtualAges() {
        return stackmobs_virtualAges.clone();
    }

    @Override
    public void stackmobs$setVirtualAges(int[] ages) {
        stackmobs_virtualAges = (ages != null) ? ages.clone() : new int[0];
    }

    @Override public long stackmobs$getBreedCooldownUntil()       { return stackmobs_breedCooldownUntil; }
    @Override public void stackmobs$setBreedCooldownUntil(long t)  { stackmobs_breedCooldownUntil = t; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void stackmobs$writeData(ValueOutput output, CallbackInfo ci) {
        output.putInt(Constants.NBT_STACK_SIZE, stackmobs_stackSize);
        if (stackmobs_virtualAges.length > 0) {
            output.putIntArray(Constants.NBT_VIRTUAL_AGES, stackmobs_virtualAges);
        }
        if (stackmobs_breedCooldownUntil > 0) {
            output.putLong(Constants.NBT_BREED_COOLDOWN, stackmobs_breedCooldownUntil);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void stackmobs$readData(ValueInput input, CallbackInfo ci) {
        stackmobs_stackSize         = Math.max(1, input.getIntOr(Constants.NBT_STACK_SIZE, 1));
        stackmobs_virtualAges        = input.getIntArray(Constants.NBT_VIRTUAL_AGES).orElse(new int[0]);
        stackmobs_breedCooldownUntil = input.getLongOr(Constants.NBT_BREED_COOLDOWN, 0L);
    }
}
