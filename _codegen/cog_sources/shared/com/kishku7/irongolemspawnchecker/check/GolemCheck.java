package com.kishku7.irongolemspawnchecker.check;

import com.kishku7.irongolemspawnchecker.IronGolemSpawnChecker;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Works out why one villager is not producing iron golems, by re-reading the same conditions vanilla
 * checks in Villager.spawnGolemIfNeeded, Villager.wantsToSpawnGolem and SpawnUtil.
 * Read-only: nothing is spawned, moved or changed. Runs on the server thread, on request only.
 */
public final class GolemCheck {
    private GolemCheck() {}

    /** Vanilla asks villagers within this many blocks (box inflate) to agree to a golem. */
    private static final double AGREE_RANGE = 10.0;
    /** Vanilla placement search offsets around the villager. */
    private static final int SPAWN_RANGE_XZ = 8;
    private static final int SPAWN_RANGE_Y = 6;
    /** Stop test-spawning candidates once this many have been tried (a valid one ends it sooner). */
    private static final int MAX_CANDIDATES = 40;

    public static CheckResult evaluate(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        Brain<Villager> brain = villager.getBrain();
        List<Entry> reasons = new ArrayList<>();

        Optional<Long> lastSlept = brain.getMemory(MemoryModuleType.LAST_SLEPT);
        boolean sleptRecently = lastSlept.isPresent()
                && now - lastSlept.get() < IronGolemSpawnChecker.SLEEP_WINDOW_TICKS;
        if (!sleptRecently) {
            if (!brain.hasMemoryValue(MemoryModuleType.HOME)) {
                reasons.add(new Entry(Reason.NO_BED, 0));
            } else {
                reasons.add(new Entry(Reason.NOT_SLEPT, 0));
            }
        }

        if (brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY)) {
            reasons.add(new Entry(Reason.GOLEM_NEARBY, 0));
        }

        AABB box = villager.getBoundingBox().inflate(AGREE_RANGE);
        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, box);
        int ready = 0;
        for (Villager v : nearby) {
            if (v.wantsToSpawnGolem(now)) {
                ready++;
            }
        }
        if (ready < IronGolemSpawnChecker.VILLAGERS_NEEDED) {
            reasons.add(new Entry(Reason.TOO_FEW_READY, ready));
        }

        if (nearby.size() >= 2 && !canGather(villager, nearby)) {
            reasons.add(new Entry(Reason.CANNOT_MEET, 0));
        }

        if (!hasSpawnSpace(level, villager)) {
            reasons.add(new Entry(Reason.NO_SPAWN_SPACE, 0));
        }

        return new CheckResult(villager.getId(), reasons, VillageScan.scan(level, villager));
    }

    /**
     * Gossip (which triggers the spawn) only happens when two villagers walk up to each other, so each
     * of the needed villagers must be able to path to this one. Fewer reachable villagers than
     * min(needed, nearby) means walls, pens or water are keeping them apart.
     */
    private static boolean canGather(Villager villager, List<Villager> nearby) {
        int reachable = 1;
        for (Villager other : nearby) {
            if (other.getId() == villager.getId()) {
                continue;
            }
            Path path = villager.getNavigation().createPath(other.blockPosition(), 2);
            if (path != null && path.canReach()) {
                reachable++;
            }
        }
        return reachable >= Math.min(IronGolemSpawnChecker.VILLAGERS_NEEDED, nearby.size());
    }

    /**
     * Repeats the vanilla placement search for every column in range instead of ten random picks, so
     * the answer does not flicker. True as soon as one test golem passes the vanilla spawn checks.
     */
    @SuppressWarnings("deprecation") // LEGACY_IRON_GOLEM is the strategy vanilla itself still uses
    private static boolean hasSpawnSpace(ServerLevel level, Villager villager) {
        BlockPos start = villager.blockPosition();
        SpawnUtil.Strategy strategy = SpawnUtil.Strategy.LEGACY_IRON_GOLEM;
        int tried = 0;
        boolean skippedUnloaded = false;
        for (int dx = -SPAWN_RANGE_XZ; dx <= SPAWN_RANGE_XZ; dx++) {
            for (int dz = -SPAWN_RANGE_XZ; dz <= SPAWN_RANGE_XZ; dz++) {
                BlockPos.MutableBlockPos search = start.offset(dx, SPAWN_RANGE_Y, dz).mutable();
                if (!level.isLoaded(search)) {
                    skippedUnloaded = true;
                    continue;
                }
                if (!moveToSpawnPosition(level, search, strategy)) {
                    continue;
                }
                if (++tried > MAX_CANDIDATES) {
                    return false;
                }
                IronGolem golem = EntityTypes.IRON_GOLEM.create(level, null, search.immutable(),
                        EntitySpawnReason.MOB_SUMMONED, false, false);
                if (golem == null) {
                    continue;
                }
                boolean ok = golem.checkSpawnRules(level, EntitySpawnReason.MOB_SUMMONED)
                        && golem.checkSpawnObstruction(level);
                golem.discard();
                if (ok) {
                    return true;
                }
            }
        }
        // Columns in unloaded chunks could not be looked at, so "no room" is not proven: stay quiet.
        return skippedUnloaded;
    }

    /** Vanilla SpawnUtil.moveToPossibleSpawnPosition: walk down looking for solid ground. */
    private static boolean moveToSpawnPosition(ServerLevel level, BlockPos.MutableBlockPos search,
                                               SpawnUtil.Strategy strategy) {
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos().set(search);
        BlockState aboveState = level.getBlockState(above);
        for (int y = SPAWN_RANGE_Y; y >= -SPAWN_RANGE_Y; y--) {
            search.move(Direction.DOWN);
            above.setWithOffset(search, Direction.UP);
            BlockState current = level.getBlockState(search);
            if (strategy.canSpawnOn(level, search, current, above, aboveState)) {
                search.move(Direction.UP);
                return true;
            }
            aboveState = current;
        }
        return false;
    }
}
