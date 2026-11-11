package com.stackmobs.stacking;

import com.stackmobs.config.ModConfig;
import com.stackmobs.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

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

        if (isPlayerManaged(entity))   return false;
        if (!isLocationAllowed(entity, config)) return false;

        // Sheared sheep stay single so each regrows its own wool by eating
        // grass; they rejoin a stack once their wool is back.
        if (entity instanceof Sheep sheep && sheep.isSheared()) return false;

        return true;
    }

    /**
     * Mobs a player has invested in must never be merged: merging discards the
     * secondary entity, which would delete its name, lead, owner, saddle,
     * inventory or trades.
     */
    /**
     * Applies {@code enabledWorlds} (empty = every dimension) and
     * {@code disabledBiomes}. Only new merges are affected; existing stacks
     * keep working wherever they are.
     */
    public static boolean isLocationAllowed(Mob entity, ModConfig config) {
        if (config.enabledWorlds != null && !config.enabledWorlds.isEmpty()) {
            String world = entity.level().dimension().identifier().toString();
            if (config.enabledWorlds.stream().noneMatch(world::equalsIgnoreCase)) return false;
        }
        if (config.disabledBiomes != null && !config.disabledBiomes.isEmpty()) {
            String biome = entity.level().getBiome(entity.blockPosition()).unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("");
            if (config.disabledBiomes.stream().anyMatch(biome::equalsIgnoreCase)) return false;
        }
        return true;
    }

    public static boolean isPlayerManaged(Mob entity) {
        // Name tags and picked-up items both set persistence
        if (entity.isPersistenceRequired())                       return true;
        // Our own "Mob xN" label only exists while size > 1, so a single mob
        // with a custom name was named by a player or a command
        if (entity.hasCustomName() && getStackSize(entity) <= 1) return true;
        if (entity.isLeashed())                                   return true;
        if (entity.isPassenger() || entity.isVehicle())           return true;
        if (entity instanceof OwnableEntity ownable
                && ownable.getOwnerReference() != null)           return true;
        if (entity instanceof InventoryCarrier)                   return true;
        if (entity instanceof Merchant)                           return true;
        return false;
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
        // Variants must match: sheep colour, cow/pig/chicken variant, sound variant…
        for (DataComponentType<?> type : variantComponentTypes()) {
            if (!Objects.equals(a.get(type), b.get(type))) return false;
        }
        return true;
    }

    // ── Variant components ────────────────────────────────────────────────────

    private static List<DataComponentType<?>> variantComponentTypes;

    /**
     * Every data component type except name/custom data. Entities return
     * {@code null} from {@link Mob#get} for types they don't carry, so this
     * covers each mob's variant/colour components without listing them.
     */
    private static List<DataComponentType<?>> variantComponentTypes() {
        if (variantComponentTypes == null) {
            List<DataComponentType<?>> types = new ArrayList<>();
            for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
                if (type == DataComponents.CUSTOM_NAME || type == DataComponents.CUSTOM_DATA) continue;
                types.add(type);
            }
            variantComponentTypes = List.copyOf(types);
        }
        return variantComponentTypes;
    }

    private static <T> void copyComponent(Mob from, Mob to, DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) to.setComponent(type, value);
    }

    // ── Spawning copies ───────────────────────────────────────────────────────

    /**
     * Creates (but does not add to the world) a single mob of the same type as
     * {@code source}, carrying over its variant/colour and sheared state, and
     * placed randomly within {@code spread} blocks of it. The copy starts as a
     * single, unstacked adult; callers adjust age and stack size as needed.
     */
    public static Mob createCopy(Mob source, ServerLevel level, double spread) {
        if (!(source.getType().create(level, EntitySpawnReason.TRIGGERED) instanceof Mob copy)) {
            return null;
        }

        for (DataComponentType<?> type : variantComponentTypes()) {
            copyComponent(source, copy, type);
        }
        if (source instanceof Sheep srcSheep && copy instanceof Sheep copySheep) {
            copySheep.setSheared(srcSheep.isSheared());
        }
        // Breeding cooldown: ours and vanilla's (a positive age on an adult),
        // so split-off mobs can't be fed again before the rest of their group
        ((StackedMob) copy).stackmobs$setBreedCooldownUntil(
            ((StackedMob) source).stackmobs$getBreedCooldownUntil());
        if (source instanceof AgeableMob srcAgeable && copy instanceof AgeableMob copyAgeable
                && srcAgeable.getAge() > 0) {
            copyAgeable.setAge(srcAgeable.getAge());
        }

        RandomSource rng = level.getRandom();
        copy.snapTo(
            source.getX() + (rng.nextDouble() - 0.5) * spread,
            source.getY(),
            source.getZ() + (rng.nextDouble() - 0.5) * spread,
            source.getYRot(), 0f);
        return copy;
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
        applyLabel(entity, clamped, config);
    }

    // ── Stack label (custom name) ─────────────────────────────────────────────

    /**
     * Sets the "Mob xN" label for a stack of {@code size}, or clears it for a
     * single mob — unless the mob carries a name someone else gave it (name
     * tag, command). That name is never overwritten or cleared; the next
     * name-tag refresh splits the renamed mob off its stack instead.
     */
    public static void applyLabel(Mob entity, int size, ModConfig config) {
        if (!(entity instanceof StackedMob sm)) return;
        if (hasForeignName(entity)) return;

        if (size > 1) {
            Component label = buildNameTag(entity, size, config.showHealthBar);
            entity.setCustomName(label);
            entity.setCustomNameVisible(config.showNameTag);
            sm.stackmobs$setLabel(label.getString());
        } else {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            sm.stackmobs$setLabel(null);
        }
    }

    /**
     * {@code true} when the mob's custom name was not set by this mod, i.e. a
     * player named it with a name tag or a command changed it.
     */
    public static boolean hasForeignName(Mob entity) {
        Component name = entity.getCustomName();
        if (name == null || !(entity instanceof StackedMob sm)) return false;

        String text = name.getString();
        String own  = sm.stackmobs$getLabel();
        if (own != null) return !own.equals(text);

        // Stacks saved before labels were tracked (1.1.0 and earlier) have no
        // stored label: accept anything shaped like our own label.
        if (getStackSize(entity) > 1 && looksLikeOwnLabel(entity, text)) {
            sm.stackmobs$setLabel(text);
            return false;
        }
        return true;
    }

    /** Matches "Cow x12", "♥ Cow x12" and "Cow x12 [70%]" for this mob's type. */
    private static boolean looksLikeOwnLabel(Mob entity, String text) {
        String base = entity.getType().getDescription().getString();
        return text.matches("(♥ )?" + Pattern.quote(base) + " x\\d+( \\[\\d+%])?");
    }

    // ── Registry helpers ──────────────────────────────────────────────────────

    public static String getEntityId(Mob entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
