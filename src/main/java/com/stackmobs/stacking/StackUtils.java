package com.stackmobs.stacking;

import com.stackmobs.config.ModConfig;
import com.stackmobs.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless utility helpers for stacking logic.
 */
public final class StackUtils {

    private StackUtils() {}

    // ── Entity eligibility ────────────────────────────────────────────────────

    public static boolean isStackable(Mob entity, ModConfig config) {
        if (!config.enabled)           return false;
        if (entity == null)            return false;
        if (!entity.isAlive())         return false;
        if (entity.isRemoved())        return false;
        if (isBoss(entity))            return false;

        String id = getEntityId(entity);
        if (config.isBlacklisted(id))  return false;
        if (!config.isWhitelisted(id)) return false;

        return true;
    }

    public static boolean isBoss(Mob entity) {
        return entity instanceof WitherBoss
            || entity instanceof EnderDragon
            || entity instanceof ElderGuardian
            || entity instanceof Warden;
    }

    public static boolean sameType(Mob a, Mob b) {
        if (a.getType() != b.getType()) return false;
        // Baby and adult must never share the same stack
        if (a.isBaby() != b.isBaby()) return false;
        // Sheared and un-sheared sheep must not share the same stack
        if (a instanceof net.minecraft.world.entity.animal.sheep.Sheep sheepA
                && b instanceof net.minecraft.world.entity.animal.sheep.Sheep sheepB) {
            if (sheepA.isSheared() != sheepB.isSheared()) return false;
        }
        return true;
    }

    // ── Spatial helpers ───────────────────────────────────────────────────────

    public static AABB getSearchBox(Mob entity, double radius) {
        return entity.getBoundingBox().inflate(radius);
    }

    public static List<Mob> findMergeCandidates(Mob primary, Level level, ModConfig config) {
        double radius   = config.stackRadius;
        int    maxStack = config.maxStackSize;
        int    minStack = Math.max(2, config.minStackSize);
        AABB   box      = getSearchBox(primary, radius);

        int primarySize = getStackSize(primary);

        long gameTime = level.getGameTime();

        // Gather all nearby mobs of the same type that pass basic eligibility
        List<Mob> nearby = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(Mob.class), box, candidate -> {
            if (candidate == primary)                            return false;
            if (!isStackable(candidate, config))                 return false;
            if (!sameType(primary, candidate))                   return false;
            if (candidate.distanceToSqr(primary) > radius * radius) return false;
            // Never merge mobs with different breed-cooldown states:
            // a fed (cooldown active) mob must not merge with an unfed one.
            boolean primaryCooldown   = ((StackedMob) primary).stackmobs$isBreedCooldownActive(gameTime);
            boolean candidateCooldown = ((StackedMob) candidate).stackmobs$isBreedCooldownActive(gameTime);
            return primaryCooldown == candidateCooldown;
        }, nearby);

        // minStackSize threshold: primary + nearby must reach the minimum count
        // before any merging is allowed. A stack that is already size > 1 counts
        // as having already passed the threshold.
        int totalNearby = 1 + nearby.size(); // 1 = primary itself
        if (primarySize == 1 && totalNearby < minStack) {
            return List.of(); // not enough mobs to start a stack
        }

        // Filter to those that still fit within maxStackSize
        List<Mob> results = new ArrayList<>();
        for (Mob candidate : nearby) {
            int candidateSize = getStackSize(candidate);
            if (primarySize + candidateSize <= maxStack) {
                results.add(candidate);
            }
        }

        return results;
    }

    // ── Name-tag formatting ───────────────────────────────────────────────────

    public static Component buildNameTag(Mob entity, int stackSize, boolean showHealth) {
        String baseName = entity.getType().getDescription().getString();

        // Fed mobs (breed cooldown active) get a pink heart prefix and distinct colour
        boolean isFed = (entity instanceof StackedMob sm)
                && sm.stackmobs$isBreedCooldownActive(entity.level().getGameTime());

        ChatFormatting color  = isFed ? ChatFormatting.LIGHT_PURPLE : getStackColor(stackSize);
        String         prefix = isFed ? "\u2665 " : ""; // ♥

        String label;
        if (showHealth) {
            float pct = (entity.getHealth() / entity.getMaxHealth()) * 100f;
            label = prefix + String.format(Constants.NAMETAG_FORMAT_HEALTH, baseName, stackSize, pct);
        } else {
            label = prefix + String.format(Constants.NAMETAG_FORMAT, baseName, stackSize);
        }

        return Component.literal(label).withStyle(color);
    }

    public static ChatFormatting getStackColor(int size) {
        if (size >= 100) return ChatFormatting.RED;
        if (size >= 50)  return ChatFormatting.GOLD;
        if (size >= 10)  return ChatFormatting.YELLOW;
        return ChatFormatting.WHITE;
    }

    // ── Stack-size helpers ────────────────────────────────────────────────────

    public static int getStackSize(Mob entity) {
        if (entity instanceof StackedMob sm) return sm.stackmobs$getStackSize();
        return 1;
    }

    public static void setStackSize(Mob entity, int stackSize, ModConfig config) {
        if (!(entity instanceof StackedMob sm)) return;

        int clamped = Math.max(1, stackSize);
        sm.stackmobs$setStackSize(clamped);

        if (clamped > 1) {
            entity.setCustomName(buildNameTag(entity, clamped, config.showHealthBar));
            entity.setCustomNameVisible(config.showNameTag);
        } else {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }

    // ── Registry helpers ──────────────────────────────────────────────────────

    public static String getEntityId(Mob entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
