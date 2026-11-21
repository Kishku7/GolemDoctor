package com.kishku7.golemdoctor.check;

import com.kishku7.golemdoctor.GolemDoctor;
import com.kishku7.golemdoctor.check.CheckResult.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntitySpawnReason;
//[[[cog
// import sys; sys.path.insert(0, codegen); import compat
// compat.entity_types_import(cog, ver)
//]]]
import net.minecraft.world.entity.EntityTypes;
//[[[end]]]
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
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
                && now - lastSlept.get() < GolemDoctor.SLEEP_WINDOW_TICKS;
        if (!sleptRecently) {
            // No bed is only a blocker while the villager has not slept: it explains why it cannot.
            if (!brain.hasMemoryValue(MemoryModuleType.HOME)) {
                reasons.add(new Entry(Reason.NO_BED, 0));
            }
            int minutes = lastSlept.isPresent() ? (int) ((now - lastSlept.get()) / 1200L) : -1;
            reasons.add(new Entry(Reason.NOT_SLEPT, minutes));
        }

        if (brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY)) {
            long left = brain.getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
            long ago = Math.max(0L, GolemDoctor.GOLEM_MEMORY_TICKS - left);
            reasons.add(new Entry(Reason.GOLEM_NEARBY, (int) (ago / 20L)));
        }

        AABB box = villager.getBoundingBox().inflate(AGREE_RANGE);
        // Only living villagers: a villager that just died lingers in the world for a moment and must not count.
        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive);
        int ready = 0;
        for (Villager v : nearby) {
            if (v.wantsToSpawnGolem(now)) {
                ready++;
            }
        }
        // The ready count is not a reason here: the client compares it with 3 (scare) and 5 (gossip) itself.
        boolean panicking = brain.isActive(Activity.PANIC);

        if (nearby.size() >= 2 && !canGather(villager, nearby)) {
            reasons.add(new Entry(Reason.CANT_GOSSIP, 0));
        }
        if (!canReachBell(level, villager)) {
            reasons.add(new Entry(Reason.CANT_REACH_BELL, 0));
        }

        if (!hasSpawnSpace(level, villager)) {
            reasons.add(new Entry(Reason.NO_SPAWN_SPACE, 0));
        }

        return new CheckResult(villager.getId(), reasons, VillageScan.scan(level, villager),
                nearby.size(), ready, panicking);
    }

    /**
     * False only when the villager has a bell, the bell is close enough to test, and there is no path to
     * it. Far or unloaded bells are not judged, so the answer never guesses.
     */
    private static boolean canReachBell(ServerLevel level, Villager villager) {
        Optional<GlobalPos> bell = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (bell.isEmpty() || !bell.get().dimension().equals(level.dimension())) {
            return true;
        }
        BlockPos pos = bell.get().pos();
        if (!level.isLoaded(pos)
                || villager.blockPosition().distSqr(pos) > GolemDoctor.BELL_TEST_RANGE * GolemDoctor.BELL_TEST_RANGE) {
            return true;
        }
        Path path = villager.getNavigation().createPath(pos, 2);
        return path != null && path.canReach();
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
        return reachable >= Math.min(GolemDoctor.VILLAGERS_NEEDED, nearby.size());
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
                //[[[cog
                // compat.entity_types_create(cog, ver)
                //]]]
                IronGolem golem = EntityTypes.IRON_GOLEM.create(level, null, search.immutable(),
                //[[[end]]]
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
