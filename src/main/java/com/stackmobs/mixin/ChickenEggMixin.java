package com.stackmobs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;

/**
 * Makes a chicken stack lay eggs for every chicken in it.
 *
 * <p>A stack is a single entity with a single egg timer, so without this a
 * {@code Chicken x1000} would lay as many eggs as one chicken. Each time the
 * timer fires, the egg loot table is rolled once per stacked chicken, keeping
 * egg output equal to that many vanilla chickens. Dropped eggs merge into item
 * stacks, so this adds few entities.
 */
@Mixin(Chicken.class)
public abstract class ChickenEggMixin {

    @WrapOperation(
        method = "aiStep()V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/entity/animal/chicken/Chicken;" +
                          "dropFromGiftLootTable(Lnet/minecraft/server/level/ServerLevel;" +
                          "Lnet/minecraft/resources/ResourceKey;" +
                          "Ljava/util/function/BiConsumer;)Z")
    )
    private boolean stackmobs$layEggPerChicken(Chicken self,
                                               ServerLevel level,
                                               ResourceKey<LootTable> lootTable,
                                               BiConsumer<ServerLevel, ItemStack> dropper,
                                               Operation<Boolean> original) {
        int stackSize = StackUtils.getStackSize(self);
        boolean dropped = false;
        for (int i = 0; i < stackSize; i++) {
            dropped |= original.call(self, level, lootTable, dropper);
        }
        return dropped;
    }
}
