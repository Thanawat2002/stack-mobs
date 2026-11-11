package com.stackmobs.stacking;

/**
 * Interface injected into every {@link net.minecraft.entity.mob.MobEntity} instance
 * by {@code MobEntityMixin}. Provides a stable, cast-based API to read and write
 * stack data on any mob entity without reflection.
 *
 * <p>Usage:
 * <pre>{@code
 * if (entity instanceof StackedMob sm) {
 *     int size = sm.stackmobs$getStackSize();
 *     sm.stackmobs$setStackSize(size + 1);
 * }
 * }</pre>
 */
public interface StackedMob {

    /**
     * Returns the current stack size (minimum 1).
     *
     * @return number of entities represented by this entity
     */
    int stackmobs$getStackSize();

    /**
     * Sets the stack size and updates the entity's displayed name tag accordingly.
     *
     * @param size new stack size; clamped to [1, maxStackSize]
     */
    void stackmobs$setStackSize(int size);

    /**
     * Convenience check – returns {@code true} when stack size is greater than 1.
     *
     * @return {@code true} if this entity represents multiple mobs
     */
    default boolean stackmobs$isStacked() {
        return stackmobs$getStackSize() > 1;
    }

    /**
     * Returns the individual age timers for each <em>virtual</em> baby in the
     * stack (i.e. every baby except the primary entity itself). Each value is
     * negative and counts up toward 0 at the same rate as a vanilla baby's age.
     * When a value reaches 0, that virtual baby grows into an adult.
     *
     * @return a copy of the virtual-ages array; empty for non-baby or size-1 stacks
     */
    int[] stackmobs$getVirtualAges();

    /**
     * Replaces the virtual-ages array. The array length must equal
     * {@code stackSize - 1} for baby stacks; otherwise it should be empty.
     *
     * @param ages new array of age values (all should be &lt; 0)
     */
    void stackmobs$setVirtualAges(int[] ages);

    /**
     * Returns the game-time tick at which this entity's breed cooldown expires.
     * {@code 0} means no cooldown is active.
     */
    long stackmobs$getBreedCooldownUntil();

    /**
     * Sets the game-time tick at which this entity's breed cooldown expires.
     *
     * @param tick absolute game tick; use {@code 0} to clear the cooldown
     */
    void stackmobs$setBreedCooldownUntil(long tick);

    /**
     * Returns {@code true} if this entity is currently in a breed cooldown.
     *
     * @param gameTime current level game time (from {@code level.getGameTime()})
     */
    default boolean stackmobs$isBreedCooldownActive(long gameTime) {
        return gameTime < stackmobs$getBreedCooldownUntil();
    }

    /**
     * Text of the stack label this mod last set as the custom name, or
     * {@code null} if it set none. A custom name that differs was set by
     * someone else (name tag, command) and must not be overwritten.
     */
    String stackmobs$getLabel();

    void stackmobs$setLabel(String label);
}
