package com.kishku7.golemdoctor.check;

import com.kishku7.golemdoctor.check.CheckResult.Village;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Minecraft has no village object, so one is built here: beds, job sites and bells (the "village"
 * point-of-interest tag) within {@link #SCAN_RADIUS} of the villager are chained together when
 * {@link #LINK_BLOCKS} or less apart, starting from the point nearest the villager. The villagers
 * standing inside that cluster (plus a margin) are the village villagers.
 *
 * Only points in loaded chunks are visible, so a village that reaches into unloaded chunks reads
 * smaller than it is.
 */
public final class VillageScan {
    private VillageScan() {}

    static final int SCAN_RADIUS = 96;
    static final int LINK_BLOCKS = 24;
    static final int MARGIN_BLOCKS = 16;
    /** How far from the bell villagers that share it are looked for. */
    static final int BELL_RADIUS = 128;

    public static Village scan(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        PoiManager poi = level.getPoiManager();
        List<BlockPos> points = new ArrayList<>(poi.getInRange(
                type -> type.is(PoiTypeTags.VILLAGE), origin, SCAN_RADIUS, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).map(BlockPos::immutable).toList());
        if (points.isEmpty()) {
            return Village.unknown();
        }

        BlockPos seed = points.stream().min(Comparator.comparingDouble(p -> p.distSqr(origin))).orElseThrow();
        List<BlockPos> cluster = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        points.remove(seed);
        long linkSqr = (long) LINK_BLOCKS * LINK_BLOCKS;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            cluster.add(current);
            for (int i = points.size() - 1; i >= 0; i--) {
                if (points.get(i).distSqr(current) <= linkSqr) {
                    queue.add(points.remove(i));
                }
            }
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cluster) {
            minX = Math.min(minX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxZ = Math.max(maxZ, p.getZ());
        }

        AABB box = new AABB(minX - MARGIN_BLOCKS, level.getMinY(), minZ - MARGIN_BLOCKS,
                maxX + MARGIN_BLOCKS + 1, level.getMaxY(), maxZ + MARGIN_BLOCKS + 1);
        List<Villager> members = new ArrayList<>(level.getEntitiesOfClass(Villager.class, box, Villager::isAlive));
        // Villagers remember the bell they gather at (MEETING_POINT). Sharing one is the closest thing the
        // game has to "these villagers belong together", so it wins over the position-based guess.
        Optional<GlobalPos> bell = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (bell.isPresent()) {
            BlockPos bp = bell.get().pos();
            AABB wide = new AABB(bp).inflate(BELL_RADIUS, level.getHeight(), BELL_RADIUS);
            List<Villager> byBell = level.getEntitiesOfClass(Villager.class, wide, v -> v.isAlive() &&
                    bell.equals(v.getBrain().getMemory(MemoryModuleType.MEETING_POINT)));
            if (!byBell.isEmpty()) {
                members = new ArrayList<>(byBell);
            }
        }
        members.sort(Comparator.comparing(v -> v.getUUID()));
        int rank = 0;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId() == villager.getId()) {
                rank = i + 1;
                break;
            }
        }

        int chunksX = (maxX >> 4) - (minX >> 4) + 1;
        int chunksZ = (maxZ >> 4) - (minZ >> 4) + 1;
        return new Village(true, rank, members.size(), chunksX, chunksZ, minX, minZ, maxX, maxZ);
    }
}
