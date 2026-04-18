package com.stackmobs.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stackmobs.StackMobsMod;
import com.stackmobs.config.ConfigManager;
import com.stackmobs.stacking.MobStack;
import com.stackmobs.stacking.StackManager;
import com.stackmobs.stacking.StackUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers and handles all {@code /stackmob} sub-commands.
 */
public final class StackCommand {

    private StackCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                StackManager stackManager,
                                ConfigManager configManager) {

        dispatcher.register(
            Commands.literal("stackmob")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))

                .then(Commands.literal("reload")
                    .executes(ctx -> executeReload(ctx, configManager, stackManager)))

                .then(Commands.literal("toggle")
                    .executes(ctx -> executeToggle(ctx, configManager)))

                .then(Commands.literal("info")
                    .executes(ctx -> executeInfo(ctx, configManager, stackManager)))

                .then(Commands.literal("unstack")
                    .executes(ctx -> executeUnstack(ctx, stackManager, 32))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> executeUnstack(ctx, stackManager,
                            IntegerArgumentType.getInteger(ctx, "radius")))))

                .then(Commands.literal("killall")
                    .executes(ctx -> executeKillAll(ctx, stackManager)))

                .then(Commands.literal("debug")
                    .executes(ctx -> executeDebug(ctx, stackManager)))
        );
    }

    // ── /stackmob reload ─────────────────────────────────────────────────────

    private static int executeReload(CommandContext<CommandSourceStack> ctx,
                                     ConfigManager configManager,
                                     StackManager  stackManager) {
        String result = configManager.reload();
        stackManager.updateConfig(configManager.getConfig());
        sendSuccess(ctx, result);
        StackMobsMod.LOGGER.info("[StackMobs] Config reloaded by {}.", ctx.getSource().getTextName());
        return 1;
    }

    // ── /stackmob toggle ─────────────────────────────────────────────────────

    private static int executeToggle(CommandContext<CommandSourceStack> ctx,
                                     ConfigManager configManager) {
        boolean newState = !configManager.getConfig().enabled;
        configManager.getConfig().enabled = newState;
        configManager.save();

        String msg = newState
            ? "§aStack Mobs stacking is now ENABLED."
            : "§cStack Mobs stacking is now DISABLED.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    // ── /stackmob info ────────────────────────────────────────────────────────

    private static int executeInfo(CommandContext<CommandSourceStack> ctx,
                                   ConfigManager configManager,
                                   StackManager  stackManager) {
        var cfg   = configManager.getConfig();
        int stacks = stackManager.getActiveStacks().size();
        int saved  = stackManager.getTotalEntitiesSaved();

        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== Stack Mobs Info ===").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> line("Enabled",         String.valueOf(cfg.enabled)), false);
        src.sendSuccess(() -> line("Stack Radius",    cfg.stackRadius + " blocks"), false);
        src.sendSuccess(() -> line("Max Stack Size",  String.valueOf(cfg.maxStackSize)), false);
        src.sendSuccess(() -> line("Merge Delay",     cfg.mergeDelay + " ticks"), false);
        src.sendSuccess(() -> line("Show Name Tag",   String.valueOf(cfg.showNameTag)), false);
        src.sendSuccess(() -> line("Active Stacks",   String.valueOf(stacks)), false);
        src.sendSuccess(() -> line("Entities Saved",  String.valueOf(saved)), false);
        return 1;
    }

    // ── /stackmob unstack [radius] ────────────────────────────────────────────

    private static int executeUnstack(CommandContext<CommandSourceStack> ctx,
                                      StackManager stackManager,
                                      int          radius) {
        CommandSourceStack src = ctx.getSource();

        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel  level  = src.getLevel();

            AABB box = player.getBoundingBox().inflate(radius);
            List<Mob> stacked = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Mob.class), box,
                e -> StackUtils.getStackSize(e) > 1, stacked);

            if (stacked.isEmpty()) {
                sendSuccess(ctx, "No stacked mobs found within " + radius + " blocks.");
                return 0;
            }

            stacked.forEach(stackManager::unstack);
            sendSuccess(ctx, "Unstacked " + stacked.size() + " mob(s) within " + radius + " blocks.");
            return stacked.size();

        } catch (Exception e) {
            src.sendFailure(Component.literal("§c[StackMobs] Must be a player to use this command."));
            return 0;
        }
    }

    // ── /stackmob killall ─────────────────────────────────────────────────────

    private static int executeKillAll(CommandContext<CommandSourceStack> ctx,
                                      StackManager stackManager) {
        int killed = 0;

        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            List<Mob> stacked = new ArrayList<>();
            net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
            net.minecraft.world.phys.AABB borderBox = new net.minecraft.world.phys.AABB(
                border.getMinX(), level.getMinY(), border.getMinZ(),
                border.getMaxX(), level.getMaxY(), border.getMaxZ());
            level.getEntities(EntityTypeTest.forClass(Mob.class),
                borderBox,
                e -> StackUtils.getStackSize(e) > 1, stacked);

            for (Mob mob : stacked) {
                mob.kill(level);
                killed++;
            }
        }

        stackManager.clearRegistry();

        final int count = killed;
        if (count == 0) {
            sendSuccess(ctx, "No stacked mobs found.");
            return 0;
        }
        sendSuccess(ctx, "Killed " + count + " stacked mob(s) across all worlds.");
        return count;
    }

    // ── /stackmob debug ───────────────────────────────────────────────────────

    private static int executeDebug(CommandContext<CommandSourceStack> ctx,
                                    StackManager stackManager) {
        CommandSourceStack src    = ctx.getSource();
        Map<java.util.UUID, MobStack> stacks = stackManager.getActiveStacks();

        src.sendSuccess(() -> Component.literal("=== Stack Mobs Debug ===").withStyle(ChatFormatting.AQUA), false);

        if (stacks.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  No active stacks registered."), false);
            return 0;
        }

        stacks.values().stream().filter(MobStack::isValid).forEach(stack -> {
            String line = String.format("  [%s] size=%d world=%s",
                stack.getPrimaryUUID().toString().substring(0, 8),
                stack.getStackSize(), stack.getWorldKey());
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        });

        src.sendSuccess(() ->
            Component.literal("Total: " + stacks.size() + " stacks | " +
                              stackManager.getTotalEntitiesSaved() + " entities saved")
                .withStyle(ChatFormatting.YELLOW), false);
        return stacks.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void sendSuccess(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[StackMobs]§r " + msg), false);
    }

    private static Component line(String key, String value) {
        return Component.literal("  " + key + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
