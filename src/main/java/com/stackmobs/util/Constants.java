package com.stackmobs.util;

/**
 * Global constants used throughout the Stack Mobs mod.
 */
public final class Constants {

    private Constants() {}

    // ── NBT keys ────────────────────────────────────────────────────────────
    /** NBT key that stores the number of entities in the stack. */
    public static final String NBT_STACK_SIZE     = "StackMobs_Size";
    /** NBT key that marks the entity as a primary (merged) stack entity. */
    public static final String NBT_IS_PRIMARY     = "StackMobs_IsPrimary";
    /** NBT key for an int[] storing individual ages of each virtual baby in a stack. */
    public static final String NBT_VIRTUAL_AGES   = "StackMobs_VirtualAges";
    /** NBT key for the game-time tick when the breed cooldown expires. */
    public static final String NBT_BREED_COOLDOWN = "StackMobs_BreedCooldown";
    /** NBT key for the text of the "Mob xN" label this mod last set. */
    public static final String NBT_LABEL          = "StackMobs_Label";

    // ── Config defaults ──────────────────────────────────────────────────────
    public static final boolean DEFAULT_ENABLED          = true;
    public static final int     DEFAULT_STACK_RADIUS     = 8;
    public static final int     DEFAULT_MAX_STACK        = 1000;
    public static final int     DEFAULT_MIN_STACK_SIZE   = 10;
    public static final boolean DEFAULT_SHOW_NAMETAG     = true;
    public static final int     DEFAULT_MERGE_DELAY      = 100;   // ticks (~5 seconds)
    public static final boolean DEFAULT_SHOW_HEALTH_BAR      = false;
    public static final int     DEFAULT_NAMETAG_VIEW_DISTANCE = 8;  // blocks
    public static final boolean DEFAULT_USE_BLACKLIST    = true;
    public static final boolean DEFAULT_USE_WHITELIST    = true;

    // ── Hardcoded blacklist (bosses / special entities) ──────────────────────
    /** Entity-type IDs that must never be stacked, regardless of config. */
    public static final String[] BOSS_BLACKLIST = {
        "minecraft:wither",
        "minecraft:ender_dragon",
        "minecraft:elder_guardian",
        "minecraft:warden"
    };

    // ── Formatting ───────────────────────────────────────────────────────────
    public static final String NAMETAG_FORMAT        = "%s x%d";
    public static final String NAMETAG_FORMAT_HEALTH = "%s x%d [%.0f%%]";

    // ── Particles ────────────────────────────────────────────────────────────
    public static final int MERGE_PARTICLE_COUNT = 10;
    public static final int SPLIT_PARTICLE_COUNT = 6;

    // ── Permissions ──────────────────────────────────────────────────────────
    public static final int PERMISSION_LEVEL_ADMIN  = 2;
    public static final int PERMISSION_LEVEL_PLAYER = 0;
}
