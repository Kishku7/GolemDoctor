package com.kishku7.irongolemspawnchecker.net;

import com.kishku7.irongolemspawnchecker.IronGolemSpawnChecker;
import com.kishku7.irongolemspawnchecker.check.CheckResult;
import com.kishku7.irongolemspawnchecker.check.GolemCheck;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Loader-neutral server side: validates a request and produces the answer. The loader seam only
 * moves the packets. The same code serves a dedicated server, an integrated (singleplayer) server
 * and a LAN host, because all of them handle the request on their own server thread.
 */
public final class CheckServer {
    private CheckServer() {}

    private static final Map<UUID, Long> LAST_REQUEST_TICK = new ConcurrentHashMap<>();

    /** Returns the answer, or null if the request is invalid, out of reach or too frequent. */
    public static CheckResult handle(ServerPlayer player, int entityId) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_REQUEST_TICK.get(player.getUUID());
        if (last != null && now - last < IronGolemSpawnChecker.MIN_REQUEST_GAP_TICKS && now >= last) {
            return null;
        }
        LAST_REQUEST_TICK.put(player.getUUID(), now);

        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof Villager villager) || !villager.isAlive()) {
            return null;
        }
        double reach = IronGolemSpawnChecker.MAX_REACH_BLOCKS;
        if (player.distanceToSqr(villager) > reach * reach) {
            return null;
        }
        return GolemCheck.evaluate(level, villager);
    }

    /** Forget a player on disconnect so the table cannot grow without bound. */
    public static void forget(UUID player) {
        LAST_REQUEST_TICK.remove(player);
    }
}
