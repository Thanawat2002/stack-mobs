package com.stackmobs.config;

import com.stackmobs.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-data configuration object for Stack Mobs.
 *
 * <p>Serialised to / deserialised from {@code config/stackmobs.json}
 * by {@link ConfigManager}. All fields have sensible defaults so a
 * missing config file results in a fully functional mod.
 */
public class ModConfig {

    // ── Global ───────────────────────────────────────────────────────────────
    /** Master switch. When {@code false} no stacking logic runs. */
    public boolean enabled         = Constants.DEFAULT_ENABLED;

    // ── Stacking behaviour ────────────────────────────────────────────────────
    /** Block radius in which identical mobs are merged. */
    public int     stackRadius     = Constants.DEFAULT_STACK_RADIUS;

    /** Maximum number of entities that may form a single stack. */
    public int     maxStackSize    = Constants.DEFAULT_MAX_STACK;

    /**
     * Minimum number of identical mobs that must be within {@link #stackRadius}
     * before merging begins. Must be ≥ 2.
     * <p>
     * Example: {@code 3} means stacking only starts when at least 3 of the same
     * mob type are close together — 2 mobs alone will never merge.
     */
    public int     minStackSize    = Constants.DEFAULT_MIN_STACK_SIZE;

    /**
     * Server ticks between merge-scan cycles.
     * Lower = more responsive merging, higher CPU cost.
     * Default 40 ticks ≈ 2 seconds.
     */
    public int     mergeDelay      = Constants.DEFAULT_MERGE_DELAY;

    // ── Visual ────────────────────────────────────────────────────────────────
    /** Show "MobName xN" floating name tag above stacked mobs. */
    public boolean showNameTag     = Constants.DEFAULT_SHOW_NAMETAG;

    /** Append a percentage health bar to the name tag. */
    public boolean showHealthBar   = Constants.DEFAULT_SHOW_HEALTH_BAR;

    /**
     * The floating name tag is shown while at least one player is within this
     * many blocks of the stack (then everyone who can see the mob sees it).
     * Set to {@code 0} to always show (vanilla behaviour).
     */
    public int nameTagViewDistance = Constants.DEFAULT_NAMETAG_VIEW_DISTANCE;

    // ── Entity filter ─────────────────────────────────────────────────────────
    /**
     * Master switch for the blacklist feature.
     * When {@code false} the {@link #blacklist} list is ignored entirely
     * (hard-coded boss blacklist in {@link Constants#BOSS_BLACKLIST} still applies).
     */
    public boolean useBlacklist = Constants.DEFAULT_USE_BLACKLIST;

    /**
     * Entity-type identifiers that must never be stacked (in addition to the
     * hard-coded boss blacklist in {@link Constants#BOSS_BLACKLIST}).
     * Only used when {@link #useBlacklist} is {@code true}.
     * Example: {@code "minecraft:creeper"}.
     */
    public List<String> blacklist  = new ArrayList<>(List.of(
        "minecraft:wither",
        "minecraft:ender_dragon"
    ));

    /**
     * Master switch for the whitelist feature.
     * When {@code true}, ONLY entities listed in {@link #whitelist} may be stacked.
     * When {@code false}, the {@link #whitelist} list is ignored and all
     * non-blacklisted entities are allowed.
     */
    public boolean useWhitelist = Constants.DEFAULT_USE_WHITELIST;

    /**
     * Entity-type identifiers that are exclusively allowed to stack.
     * Only used when {@link #useWhitelist} is {@code true}.
     * Example: {@code ["minecraft:zombie", "minecraft:skeleton"]}.
     */
    public List<String> whitelist  = new ArrayList<>(List.of(
        "minecraft:chicken",
        "minecraft:cow",
        "minecraft:pig",
        "minecraft:sheep"
    ));

    // ── World / biome restrictions ────────────────────────────────────────────
    /**
     * Dimension identifiers where stacking is active.
     * Empty = stacking is active in all dimensions.
     * Example: {@code ["minecraft:overworld"]}.
     */
    public List<String> enabledWorlds  = new ArrayList<>();

    /**
     * Biome identifiers where stacking is disabled.
     * Empty = no biome restrictions.
     */
    public List<String> disabledBiomes = new ArrayList<>();

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Clamps out-of-range values and replaces missing ({@code null}) lists so
     * a hand-edited file can't break stacking.
     *
     * @return a description of every value that was corrected (empty if none)
     */
    public List<String> validate() {
        List<String> fixes = new ArrayList<>();
        if (stackRadius < 1) {
            fixes.add("stackRadius " + stackRadius + " < 1, using 1");
            stackRadius = 1;
        }
        if (maxStackSize < 2) {
            fixes.add("maxStackSize " + maxStackSize + " < 2, using 2");
            maxStackSize = 2;
        }
        if (minStackSize < 2) {
            fixes.add("minStackSize " + minStackSize + " < 2, using 2");
            minStackSize = 2;
        }
        if (minStackSize > maxStackSize) {
            fixes.add("minStackSize " + minStackSize + " > maxStackSize, using " + maxStackSize);
            minStackSize = maxStackSize;
        }
        if (mergeDelay < 1) {
            fixes.add("mergeDelay " + mergeDelay + " < 1, using 1");
            mergeDelay = 1;
        }
        if (nameTagViewDistance < 0) {
            fixes.add("nameTagViewDistance " + nameTagViewDistance + " < 0, using 0");
            nameTagViewDistance = 0;
        }
        if (blacklist == null)      { blacklist = new ArrayList<>();      fixes.add("blacklist missing, using []"); }
        if (whitelist == null)      { whitelist = new ArrayList<>();      fixes.add("whitelist missing, using []"); }
        if (enabledWorlds == null)  { enabledWorlds = new ArrayList<>();  fixes.add("enabledWorlds missing, using []"); }
        if (disabledBiomes == null) { disabledBiomes = new ArrayList<>(); fixes.add("disabledBiomes missing, using []"); }
        return fixes;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the given entity-type id should never be stacked.
     * <p>
     * Hard-coded bosses ({@link Constants#BOSS_BLACKLIST}) are always blocked.
     * The config {@link #blacklist} is only consulted when {@link #useBlacklist}
     * is {@code true}.
     *
     * @param entityId namespaced entity id, e.g. {@code "minecraft:zombie"}
     * @return {@code true} if the entity should never be stacked
     */
    public boolean isBlacklisted(String entityId) {
        // Hard-coded bosses are always blocked regardless of useBlacklist flag
        for (String boss : Constants.BOSS_BLACKLIST) {
            if (boss.equalsIgnoreCase(entityId)) return true;
        }
        // Config blacklist only applies when the feature is enabled
        if (!useBlacklist) return false;
        return blacklist.stream().anyMatch(id -> id.equalsIgnoreCase(entityId));
    }

    /**
     * Returns {@code true} when the given entity-type id is allowed through
     * the whitelist check.
     * <p>
     * When {@link #useWhitelist} is {@code false} this always returns {@code true}.
     * When {@link #useWhitelist} is {@code true}, the entity must appear in
     * {@link #whitelist} to be allowed.
     *
     * @param entityId namespaced entity id
     * @return {@code true} if the entity passes the whitelist gate
     */
    public boolean isWhitelisted(String entityId) {
        if (!useWhitelist) return true;
        return whitelist.stream().anyMatch(id -> id.equalsIgnoreCase(entityId));
    }
}
