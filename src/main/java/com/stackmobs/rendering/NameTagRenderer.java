package com.stackmobs.rendering;

import com.stackmobs.config.ModConfig;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/**
 * Server-side utility for building and applying name-tag text to stacked mobs.
 *
 * <p>Name tags are set via {@link Mob#setCustomName(Component)} and
 * {@link Mob#setCustomNameVisible(boolean)}, which are server-authoritative
 * and automatically synchronised to all clients.
 */
public final class NameTagRenderer {

    private NameTagRenderer() {}

    /**
     * Applies (or removes) the stack name tag on {@code entity}.
     */
    public static void applyNameTag(Mob entity, int stackSize, ModConfig config) {
        if (!config.showNameTag || stackSize <= 1) {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            return;
        }

        Component tag = StackUtils.buildNameTag(entity, stackSize, config.showHealthBar);
        entity.setCustomName(tag);
        entity.setCustomNameVisible(true);
    }

    /**
     * Refreshes the name tag using the entity's current stack size.
     */
    public static void refresh(Mob entity, ModConfig config) {
        int size = StackUtils.getStackSize(entity);
        applyNameTag(entity, size, config);
    }

    /**
     * Builds a preview {@link Component} without applying it to any entity.
     */
    public static Component buildPreview(String baseName,
                                         int    stackSize,
                                         float  healthPct,
                                         boolean showHealth) {
        ChatFormatting color = StackUtils.getStackColor(stackSize);

        String label = showHealth
            ? String.format("%s x%d [%.0f%%]", baseName, stackSize, healthPct)
            : String.format("%s x%d", baseName, stackSize);

        return Component.literal(label).withStyle(color);
    }
}
