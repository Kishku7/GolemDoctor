package com.kishku7.irongolemspawnchecker.net;

import com.kishku7.irongolemspawnchecker.check.CheckResult;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Entry;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Village;
import com.kishku7.irongolemspawnchecker.check.GolemCheck;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * /golemcheck &lt;villager&gt; prints the same answer the popup shows, as chat lines. It exists so the
 * logic can be checked without a client and so a server owner can ask about any villager by selector.
 * Read-only.
 */
public final class CheckCommand {
    private CheckCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golemcheck")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .executes(CheckCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "villager");
        if (!(entity instanceof Villager villager)) {
            ctx.getSource().sendFailure(Component.literal("Not a villager."));
            return 0;
        }
        CheckResult result = GolemCheck.evaluate(ctx.getSource().getLevel(), villager);
        Village v = result.village();
        StringBuilder head = new StringBuilder("Villager ");
        if (v.known()) {
            head.append(v.rank()).append(" of ").append(v.total())
                    .append(" | village ").append(v.chunksX()).append("x").append(v.chunksZ()).append(" chunks")
                    .append(" | x ").append(v.minX()).append(" to ").append(v.maxX())
                    .append(" z ").append(v.minZ()).append(" to ").append(v.maxZ());
        } else {
            head.append("(no village found)");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(head.toString()), false);
        if (result.reasons().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("SHOULD SPAWN"), false);
        } else {
            for (Entry e : result.reasons()) {
                ctx.getSource().sendSuccess(() -> Component.literal("BLOCKED: " + e.text()), false);
            }
        }
        return result.reasons().size();
    }
}
