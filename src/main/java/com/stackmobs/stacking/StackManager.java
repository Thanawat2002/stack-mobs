package com.stackmobs.stacking;

import com.stackmobs.StackMobsMod;
import com.stackmobs.config.ModConfig;
import com.stackmobs.mixin.LivingEntityDropAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core engine of the Stack Mobs mod.
 * Handles merging, splitting, per-member death dispatch, and baby growth.
 */
public class StackManager {

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static StackManager instance;

    public static StackManager getInstance() { return instance; }

    // ── State ─────────────────────────────────────────────────────────────────
    private ModConfig config;
    private final Set<UUID>           processing       = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MobStack> activeStacks     = new ConcurrentHashMap<>();
    private int tickCounter     = 0;
    private int nameTagCounter  = 0;
    private static final int NAMETAG_UPDATE_INTERVAL = 10; // ticks

    // ── Constructor ───────────────────────────────────────────────────────────

    public StackManager(ModConfig config) {
        this.config = config;
        instance    = this;
        StackMobsMod.LOGGER.info("[StackMobs] StackManager initialised.");
    }

    // ── Tick entry-point ──────────────────────────────────────────────────────

    public void onServerTick(MinecraftServer server) {
        // Existing stacks keep working while stacking is disabled; only new
        // merges stop. Otherwise babies in a stack would freeze and then all
        // grow up at once.

        // Virtual-age processing runs every tick so babies grow at the
        // correct vanilla rate (1 tick = 1 age unit).
        for (ServerLevel level : server.getAllLevels()) {
            processVirtualAges(level);
        }

        // Name-tag text + visibility update (every NAMETAG_UPDATE_INTERVAL ticks)
        nameTagCounter++;
        if (nameTagCounter >= NAMETAG_UPDATE_INTERVAL) {
            nameTagCounter = 0;
            refreshNameTags();
            if (config.showNameTag && config.nameTagViewDistance > 0) {
                for (ServerLevel level : server.getAllLevels()) {
                    updateNameTagVisibility(level);
                }
            }
        }

        if (!config.enabled) return;

        tickCounter++;
        if (tickCounter < config.mergeDelay) return;
        tickCounter = 0;

        for (ServerLevel level : server.getAllLevels()) {
            scanWorld(level);
        }
    }

    // ── Virtual-age processing ────────────────────────────────────────────────

    /**
     * Increments the stored age timer for every virtual baby in each stack,
     * and spawns an adult when a timer reaches zero.
     *
     * <p>This runs every server tick so the growth rate matches vanilla
     * (one age unit per tick, same as {@code AgeableMob.aiStep()}).
     */
    private void processVirtualAges(ServerLevel level) {
        // Work on a snapshot to avoid ConcurrentModificationException
        List<MobStack> snapshot = new ArrayList<>(activeStacks.values());

        for (MobStack stack : snapshot) {
            if (!stack.isValid()) continue;
            Mob primary = stack.getPrimary();
            if (!primary.isBaby()) continue;
            if (!(primary.level() instanceof ServerLevel primaryLevel)
                    || primaryLevel != level) continue;

            int[] ages = ((StackedMob) primary).stackmobs$getVirtualAges();
            if (ages.length == 0) continue;

            // Increment every virtual age; collect those that have grown up
            List<Integer> remaining = new ArrayList<>(ages.length);
            boolean anyGrown = false;

            for (int age : ages) {
                int next = age + 1; // count up toward 0
                if (next >= 0) {
                    // Never grow more babies than the stack still holds
                    if (StackUtils.getStackSize(primary) <= 1) continue;
                    spawnAdult(level, primary);
                    StackUtils.setStackSize(primary,
                        StackUtils.getStackSize(primary) - 1, config);
                    anyGrown = true;
                } else {
                    remaining.add(next);
                }
            }

            // Persist the updated ages
            int[] updated = remaining.stream().mapToInt(Integer::intValue).toArray();
            ((StackedMob) primary).stackmobs$setVirtualAges(updated);

            if (anyGrown) {
                int size = StackUtils.getStackSize(primary);
                if (size > 1) registerStack(primary, level);
                else          activeStacks.remove(primary.getUUID());
            } else {
                // Still update so we don't need to re-read each tick
                ((StackedMob) primary).stackmobs$setVirtualAges(updated);
            }
        }
    }

    /**
     * Rebuilds each stack's label when its text would change: the health %
     * after non-lethal hits, and the ♥ prefix when a breed cooldown expires.
     * Only sends an update when the text actually differs.
     *
     * <p>A stack whose primary was renamed, saddled, leashed or otherwise taken
     * over by a player is split instead: that mob leaves the stack with its
     * name and equipment, so a member's death can't drop its saddle.
     */
    private void refreshNameTags() {
        // Snapshot: splitting registers a new stack while we iterate
        for (MobStack stack : new ArrayList<>(activeStacks.values())) {
            if (!stack.isValid()) continue;
            Mob mob = stack.getPrimary();
            int size = StackUtils.getStackSize(mob);
            if (size < 2) continue;

            if (StackUtils.hasForeignName(mob) || StackUtils.isPlayerManaged(mob)) {
                splitOffPrimary(mob);
                continue;
            }

            Component expected = StackUtils.buildNameTag(mob, size, config.showHealthBar);
            if (!expected.equals(mob.getCustomName())) {
                StackUtils.applyLabel(mob, size, config);
            }
        }
    }

    /**
     * Splits the primary off its stack. It stays where it is as a single mob,
     * keeping its name, saddle and lead (which also stop it re-stacking); the
     * rest of the stack continues as a new, unnamed stack right next to it.
     */
    public void splitOffPrimary(Mob named) {
        if (!(named.level() instanceof ServerLevel level)) return;
        int size = StackUtils.getStackSize(named);
        if (size <= 1) return;

        int[] ages = ((StackedMob) named).stackmobs$getVirtualAges();
        Mob rest = StackUtils.createCopy(named, level, 0.5);
        if (rest == null) return;

        // The rest's primary takes the first virtual baby's age
        int[] restAges = ages;
        if (named.isBaby() && rest instanceof AgeableMob ageableRest) {
            ageableRest.setAge(ages.length > 0 ? Math.min(ages[0], -1) : -24000);
            restAges = ages.length > 0 ? Arrays.copyOfRange(ages, 1, ages.length) : ages;
        }
        StackUtils.setStackSize(rest, size - 1, config);
        ((StackedMob) rest).stackmobs$setVirtualAges(restAges);
        level.addFreshEntity(rest);
        syncRegistry(rest, level);

        ((StackedMob) named).stackmobs$setVirtualAges(new int[0]);
        StackUtils.setStackSize(named, 1, config); // keeps the player's name
        syncRegistry(named, level);

        StackMobsMod.LOGGER.debug("[StackMobs] {} split off its stack (renamed or player-managed); {} remain stacked.",
            StackUtils.getEntityId(named), size - 1);
    }

    /**
     * Shows a stack's label while any player is within
     * {@link ModConfig#nameTagViewDistance} blocks. Name-tag visibility is a
     * property of the entity, not of each viewer, so once one player is close
     * every player who can see the mob sees the label.
     */
    private void updateNameTagVisibility(ServerLevel level) {
        if (!config.showNameTag) return;
        double maxDistSq = (double) config.nameTagViewDistance * config.nameTagViewDistance;

        for (MobStack stack : activeStacks.values()) {
            if (!stack.isValid()) continue;
            Mob mob = stack.getPrimary();
            if (!(mob.level() instanceof ServerLevel mobLevel) || mobLevel != level) continue;
            if (StackUtils.getStackSize(mob) < 2) continue;

            // Show name tag only when at least one player is close enough
            boolean anyClose = !level.getPlayers(
                p -> p.distanceToSqr(mob) <= maxDistSq
            ).isEmpty();

            mob.setCustomNameVisible(anyClose);
        }
    }

    /**
     * Spawns one adult entity of the same type as {@code babyEntity} near it.
     * Used when a virtual baby in a stack grows up.
     */
    public void spawnAdult(ServerLevel level, Mob babyEntity) {
        try {
            Mob adult = StackUtils.createCopy(babyEntity, level, 1.5);
            if (adult == null) return;

            // Ensure the spawned entity is an adult (age 0)
            if (adult instanceof AgeableMob ageableMob) {
                ageableMob.setAge(0);
            }

            StackUtils.setStackSize(adult, 1, config);
            level.addFreshEntity(adult);

            StackMobsMod.LOGGER.debug("[StackMobs] Virtual baby {} grew up → adult spawned.",
                StackUtils.getEntityId(babyEntity));
        } catch (Exception e) {
            StackMobsMod.LOGGER.warn("[StackMobs] Failed to spawn adult from baby stack: {}",
                e.getMessage());
        }
    }

    // ── Merge logic ───────────────────────────────────────────────────────────

    private void scanWorld(ServerLevel level) {
        List<Mob> mobs = new ArrayList<>();
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        net.minecraft.world.phys.AABB borderBox = new net.minecraft.world.phys.AABB(
            border.getMinX(), level.getMinY(), border.getMinZ(),
            border.getMaxX(), level.getMaxY(), border.getMaxZ());
        level.getEntities(EntityTypeTest.forClass(Mob.class),
            borderBox,
            mob -> StackUtils.isStackable(mob, config),
            mobs);

        Set<UUID> merged = new HashSet<>();

        for (Mob primary : mobs) {
            UUID primaryUUID = primary.getUUID();
            if (merged.contains(primaryUUID))     continue;
            if (processing.contains(primaryUUID)) continue;

            List<Mob> candidates = StackUtils.findMergeCandidates(primary, level, config);

            for (Mob secondary : candidates) {
                UUID secondaryUUID = secondary.getUUID();
                if (merged.contains(secondaryUUID))     continue;
                if (processing.contains(secondaryUUID)) continue;

                merge(level, primary, secondary);
                merged.add(secondaryUUID);

                if (StackUtils.getStackSize(primary) >= config.maxStackSize) break;
            }
        }
    }

    public void merge(ServerLevel level, Mob primary, Mob secondary) {
        processing.add(primary.getUUID());
        processing.add(secondary.getUUID());

        try {
            // ── Baby stacks: transfer secondary's age so it grows up independently
            if (primary.isBaby() && secondary.isBaby()
                    && primary instanceof AgeableMob aPrimary
                    && secondary instanceof AgeableMob aSecondary) {

                int[] primaryVA   = ((StackedMob) primary).stackmobs$getVirtualAges();
                int[] secondaryVA = ((StackedMob) secondary).stackmobs$getVirtualAges();
                int   secAge      = aSecondary.getAge();

                // Combined: primary's existing virtual ages
                //         + secondary's virtual ages
                //         + secondary's own age (it becomes a new virtual baby)
                int[] combined = new int[primaryVA.length + secondaryVA.length + 1];
                System.arraycopy(primaryVA,   0, combined, 0,                  primaryVA.length);
                System.arraycopy(secondaryVA, 0, combined, primaryVA.length,   secondaryVA.length);
                combined[combined.length - 1] = secAge;

                ((StackedMob) primary).stackmobs$setVirtualAges(combined);
            }

            int combinedSize = Math.min(
                StackUtils.getStackSize(primary) + StackUtils.getStackSize(secondary),
                config.maxStackSize);

            StackUtils.setStackSize(primary, combinedSize, config);
            secondary.discard();

            registerStack(primary, level);
            spawnMergeParticles(level, primary);

            StackMobsMod.LOGGER.debug("[StackMobs] Merged {} + {} → size {}",
                StackUtils.getEntityId(primary),
                StackUtils.getEntityId(secondary),
                combinedSize);
        } finally {
            processing.remove(primary.getUUID());
            processing.remove(secondary.getUUID());
        }
    }

    // ── Unstack (split) ───────────────────────────────────────────────────────

    /** Splits the whole stack into individual mobs. */
    public void unstack(Mob entity) {
        split(entity, StackUtils.getStackSize(entity) - 1);
    }

    /**
     * Splits {@code amount} members off the stack as individual mobs (clamped
     * to stack size - 1); the rest stays as the stack.
     */
    public void split(Mob entity, int amount) {
        if (!(entity.level() instanceof ServerLevel level)) return;

        int stackSize = StackUtils.getStackSize(entity);
        amount = Math.min(amount, stackSize - 1);
        if (amount <= 0) return;

        // Split-off babies take the last virtual ages; the stack keeps the rest
        int[] virtualAges = ((StackedMob) entity).stackmobs$getVirtualAges();
        int keep = Math.max(0, virtualAges.length - amount);
        int[] splitAges = Arrays.copyOfRange(virtualAges, keep, virtualAges.length);
        ((StackedMob) entity).stackmobs$setVirtualAges(Arrays.copyOf(virtualAges, keep));
        StackUtils.setStackSize(entity, stackSize - amount, config);
        syncRegistry(entity, level);

        for (int i = 0; i < amount; i++) {
            try {
                Mob clone = StackUtils.createCopy(entity, level, 2.0);
                if (clone == null) continue;

                // If source was a baby, clone should also be a baby
                if (entity.isBaby() && clone instanceof AgeableMob ageableClone) {
                    int age = (i < splitAges.length) ? splitAges[i] : -24000;
                    ageableClone.setAge(Math.min(age, -1));
                }

                StackUtils.setStackSize(clone, 1, config);
                level.addFreshEntity(clone);
            } catch (Exception e) {
                StackMobsMod.LOGGER.warn("[StackMobs] Failed to spawn unstack clone: {}",
                    e.getMessage());
            }
        }

        spawnSplitParticles(level, entity);
    }

    // ── Death handling ────────────────────────────────────────────────────────

    public void onStackMemberKilled(Mob entity, ServerLevel level, DamageSource source) {
        try {
            ((LivingEntityDropAccessor) entity).invokeDropAllDeathLoot(level, source);
        } catch (Exception e) {
            StackMobsMod.LOGGER.warn("[StackMobs] Could not invoke dropAllDeathLoot for {}: {}",
                StackUtils.getEntityId(entity), e.getMessage());
        }
        // Registry is updated by the caller once the new stack size is known
    }

    /** Re-registers or drops {@code entity} to match its current stack size. */
    public void syncRegistry(Mob entity, Level level) {
        if (StackUtils.getStackSize(entity) > 1) registerStack(entity, level);
        else                                     activeStacks.remove(entity.getUUID());
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    public void registerStack(Mob entity, Level level) {
        String worldKey = level.dimension().identifier().toString();
        int    size     = StackUtils.getStackSize(entity);
        activeStacks.put(entity.getUUID(), new MobStack(entity, size, worldKey));
    }

    public void removeStack(UUID uuid)                          { activeStacks.remove(uuid); }
    public Map<UUID, MobStack> getActiveStacks()               { return Collections.unmodifiableMap(activeStacks); }
    public ModConfig getConfig()                               { return config; }
    public void updateConfig(ModConfig cfg)                    { this.config = cfg; }
    public void clearRegistry()                                { activeStacks.clear(); }

    /** Stacks whose primary is loaded and alive (unloaded chunks excluded). */
    public int getActiveStackCount() {
        return (int) activeStacks.values().stream().filter(MobStack::isValid).count();
    }

    public int getTotalEntitiesSaved() {
        // Live size from the entity, not the registry snapshot
        return activeStacks.values().stream()
            .filter(MobStack::isValid)
            .mapToInt(s -> StackUtils.getStackSize(s.getPrimary()) - 1)
            .sum();
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private void spawnMergeParticles(ServerLevel level, Mob entity) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            entity.getX(), entity.getY() + 1.0, entity.getZ(),
            10, 0.4, 0.4, 0.4, 0.1);
    }

    private void spawnSplitParticles(ServerLevel level, Mob entity) {
        level.sendParticles(ParticleTypes.CLOUD,
            entity.getX(), entity.getY() + 1.0, entity.getZ(),
            6, 0.5, 0.5, 0.5, 0.05);
    }
}
