package com.kishku7.golemdoctor.log;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /golemlog: iron golem spawn counts, once a minute or on demand. Read-only, like /golemcheck: it only
 * tells the one who asked.
 *
 * <pre>
 * /golemlog               same as now
 * /golemlog now           rolling 15 and 60 minute figures, busiest chunks, last spawn
 * /golemlog towers [min]  one row per tower: count, rate, gap between spawns (minutes of history, default 15)
 * /golemlog start [n]     report once a minute to me, near-you radius n chunks (default 8)
 * /golemlog stop          stop my reports
 * /golemlog radius n      change my near-you radius (chunks; my own chunk is 0)
 * </pre>
 */
public final class GolemLogCommand {
    private GolemLogCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golemlog")
                .executes(GolemLogCommand::now)
                .then(Commands.literal("now").executes(GolemLogCommand::now))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx, -1))
                        .then(Commands.argument("chunks", chunksArg())
                                .executes(ctx -> start(ctx, chunksValue(ctx)))))
                .then(Commands.literal("towers")
                        .executes(ctx -> towers(ctx, 15))
                        .then(Commands.argument("chunks", chunksArg())
                                .executes(ctx -> towers(ctx, chunksValue(ctx)))))
                .then(Commands.literal("stop").executes(GolemLogCommand::stop))
                .then(Commands.literal("radius")
                        .then(Commands.argument("chunks", chunksArg())
                                .executes(GolemLogCommand::radius))));
    }

    // The integer argument class moved in the 26.4 snapshots (vanilla wraps its own IntegerArgument).
    //[[[cog
    // import sys; sys.path.insert(0, codegen); import compat
    // compat.int_arg(cog, ver)
    //]]]
    //[[[end]]]

    private static int now(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        int radius = GolemSpawnLog.radiusFor(GolemSpawnLog.key(player));
        for (String line : GolemSpawnLog.summary(player, radius)) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** Per-tower rows. The number is minutes of history (1 to 60, default 15). */
    private static int towers(CommandContext<CommandSourceStack> ctx, int minutes) {
        int mins = Math.max(1, Math.min(60, minutes));
        for (String line : GolemSpawnLog.towers(ctx.getSource().getPlayer(), mins)) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int chunks) {
        ServerPlayer player = ctx.getSource().getPlayer();
        String key = GolemSpawnLog.key(player);
        if (chunks >= 0) {
            GolemSpawnLog.setRadius(key, chunks);
        }
        GolemSpawnLog.start(player);
        String where = player == null ? "the console" : "you";
        String near = player == null ? "" : ", near-you radius " + GolemSpawnLog.radiusFor(key) + " chunks";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[GolemLog] reporting to " + where + " once a minute" + near + ". /golemlog stop to end."), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        boolean was = GolemSpawnLog.stop(GolemSpawnLog.key(ctx.getSource().getPlayer()));
        ctx.getSource().sendSuccess(() -> Component.literal(
                was ? "[GolemLog] stopped." : "[GolemLog] you were not receiving reports."), false);
        return was ? 1 : 0;
    }

    private static int radius(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("The console has no position, so no near-you radius."));
            return 0;
        }
        int chunks = chunksValue(ctx);
        GolemSpawnLog.setRadius(GolemSpawnLog.key(player), chunks);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[GolemLog] near-you radius is now " + chunks + " chunks (your own chunk is 0)."), false);
        return 1;
    }
}
