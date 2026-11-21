package com.kishku7.golemdoctor.net;

import com.kishku7.golemdoctor.check.CheckResult;
import com.kishku7.golemdoctor.check.CheckResult.Entry;
import com.kishku7.golemdoctor.check.CheckResult.Village;
import com.kishku7.golemdoctor.check.GolemCheck;
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
        ctx.getSource().sendSuccess(() -> Component.literal("GROUP: " + result.nearby()
                + " in range, " + result.ready() + " ready" + (result.panicking() ? " (scared now)" : "")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(result.methodText(true)
                + (result.ready() >= CheckResult.SCARE_NEEDED ? " (ok)" : " (short)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(result.methodText(false)
                + (result.ready() >= CheckResult.GOSSIP_NEEDED ? " (ok)" : " (short)")), false);
        for (Entry e : result.reasons()) {
            ctx.getSource().sendSuccess(() -> Component.literal((e.reason().isGroup() ? "BLOCKED (group): " : "BLOCKED (villager): ") + e.text()), false);
        }
        if (result.scareOk()) {
            ctx.getSource().sendSuccess(() -> Component.literal("SHOULD SPAWN (scare)"), false);
        }
        if (result.gossipOk()) {
            ctx.getSource().sendSuccess(() -> Component.literal("SHOULD SPAWN (gossip)"), false);
        }
        return result.reasons().size();
    }
}
