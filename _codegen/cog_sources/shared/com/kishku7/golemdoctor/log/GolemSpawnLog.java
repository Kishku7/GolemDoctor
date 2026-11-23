package com.kishku7.golemdoctor.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * Counts iron golems as they are added to the world and reports the rate, so a player can see how fast
 * a farm really is. Only freshly added golems count (the server's addFreshEntity), never golems loaded back
 * from disk, and golems a player built from pumpkins are left out.
 *
 * <p>History is kept for an hour in memory only. Reports go to whoever asked: a player in chat or the
 * console. Everything runs on the server thread.
 */
public final class GolemSpawnLog {
    private GolemSpawnLog() {}

    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 128;

    private static final long REPORT_MS = 60_000L;
    private static final long KEEP_MS = 3_600_000L;
    private static final double INGOTS_PER_GOLEM = 4.0;
    private static final int HOT_CHUNKS = 3;

    private record Spawn(long timeMs, ServerLevel level, int chunkX, int chunkZ, double x, double z) {}

    /** One reporter: a player (uuid) or the console (uuid null). */
    private static final class Sub {
        final UUID player;
        long windowStart;
        long total;
        long minutes;

        Sub(UUID player, long now) {
            this.player = player;
            this.windowStart = now;
        }
    }

    private static final ArrayDeque<Spawn> HISTORY = new ArrayDeque<>();
    private static final Map<String, Sub> SUBS = new LinkedHashMap<>();
    private static final Map<String, Integer> RADIUS = new HashMap<>();
    private static long trackingSince = System.currentTimeMillis();

    /** Source key: the player's uuid, or "console". */
    public static String key(ServerPlayer player) {
        return player == null ? "console" : player.getUUID().toString();
    }

    public static void serverStarted() {
        HISTORY.clear();
        SUBS.clear();
        trackingSince = System.currentTimeMillis();
    }

    public static void serverStopped() {
        HISTORY.clear();
        SUBS.clear();
        RADIUS.clear();
    }

    public static void forget(UUID player) {
        SUBS.remove(player.toString());
        RADIUS.remove(player.toString());
    }

    /** Called from the mixin for every iron golem the server adds fresh. */
    public static void record(ServerLevel level, IronGolem golem) {
        long now = System.currentTimeMillis();
        HISTORY.addLast(new Spawn(now, level, golem.getBlockX() >> 4, golem.getBlockZ() >> 4, golem.getX(), golem.getZ()));
        trim(now);
    }

    private static void trim(long now) {
        while (!HISTORY.isEmpty() && now - HISTORY.peekFirst().timeMs() > KEEP_MS) {
            HISTORY.pollFirst();
        }
    }

    public static int radiusFor(String key) {
        return RADIUS.getOrDefault(key, DEFAULT_RADIUS);
    }

    public static void setRadius(String key, int chunks) {
        RADIUS.put(key, Math.max(0, Math.min(MAX_RADIUS, chunks)));
    }

    public static boolean isOn(String key) {
        return SUBS.containsKey(key);
    }

    public static void start(ServerPlayer player) {
        SUBS.put(key(player), new Sub(player == null ? null : player.getUUID(), System.currentTimeMillis()));
    }

    public static boolean stop(String key) {
        return SUBS.remove(key) != null;
    }

    // ---- once-a-minute reports ----

    /** Server tick hook: send each reporter their line once its minute is up. */
    public static void tick(MinecraftServer server) {
        if (SUBS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> drop = new ArrayList<>();
        for (Map.Entry<String, Sub> e : SUBS.entrySet()) {
            Sub sub = e.getValue();
            if (now - sub.windowStart < REPORT_MS) {
                continue;
            }
            ServerPlayer player = null;
            if (sub.player != null) {
                player = server.getPlayerList().getPlayer(sub.player);
                if (player == null) {
                    drop.add(e.getKey());
                    continue;
                }
            }
            long from = sub.windowStart;
            long to = from + REPORT_MS;
            sub.windowStart = to;
            sub.minutes++;
            int radius = radiusFor(e.getKey());
            int server60 = count(from, to, null, 0, 0, 0);
            sub.total += server60;
            String line = "[GolemLog] last minute: " + server60 + " golems on the server";
            int near60 = -1;
            if (player != null) {
                near60 = count(from, to, player.level(), player.getBlockX() >> 4, player.getBlockZ() >> 4, radius);
                line += ", " + near60 + " within " + radius + " chunks of you";
            }
            send(server, player, line);
            double avg = (double) sub.total / sub.minutes;
            send(server, player, String.format(Locale.ROOT,
                    "[GolemLog] average since start (%d min): %.1f/min on the server, about %d iron an hour (estimate, %d per golem)",
                    sub.minutes, avg, Math.round(avg * 60.0 * INGOTS_PER_GOLEM), Math.round(INGOTS_PER_GOLEM)));
            if (sub.windowStart + REPORT_MS <= now) {
                sub.windowStart = now;
            }
        }
        for (String k : drop) {
            SUBS.remove(k);
        }
    }

    private static void send(MinecraftServer server, ServerPlayer player, String text) {
        Component c = Component.literal(text);
        if (player == null) {
            server.sendSystemMessage(c);
        } else {
            player.sendSystemMessage(c);
        }
    }

    // ---- one-shot summary ----

    /** Lines for /golemlog now. player may be null (console): no near-you figures then. */
    public static List<String> summary(ServerPlayer player, int radius) {
        long now = System.currentTimeMillis();
        trim(now);
        double upMin = Math.max(1.0, (now - trackingSince) / 60_000.0);
        List<String> out = new ArrayList<>();
        out.add("[GolemLog] server up " + fmtMin(upMin) + " since the mod started counting");
        for (int mins : new int[] {15, 60}) {
            long from = now - mins * 60_000L;
            double span = Math.min(mins, upMin);
            int all = count(from, now, null, 0, 0, 0);
            String s = "last " + mins + " min: " + all + " on the server (" + perMin(all, span) + "/min)";
            if (player != null) {
                int near = count(from, now, player.level(), player.getBlockX() >> 4, player.getBlockZ() >> 4, radius);
                s += ", " + near + " within " + radius + " chunks (" + perMin(near, span) + "/min, about "
                        + Math.round(near / span * 60.0 * INGOTS_PER_GOLEM) + " iron an hour)";
            } else {
                s += ", about " + Math.round(all / span * 60.0 * INGOTS_PER_GOLEM) + " iron an hour";
            }
            out.add("[GolemLog] " + s);
        }
        out.add("[GolemLog] iron figures are estimates: " + Math.round(INGOTS_PER_GOLEM) + " ingots per golem");
        hot(out, now, player, radius);
        lastSpawn(out, now, player);
        return out;
    }

    private static void hot(List<String> out, long now, ServerPlayer player, int radius) {
        Map<Long, int[]> by = new HashMap<>();
        long from = now - 15 * 60_000L;
        for (Spawn s : HISTORY) {
            if (s.timeMs() < from) {
                continue;
            }
            if (player != null && !within(s, player.level(), player.getBlockX() >> 4, player.getBlockZ() >> 4, radius)) {
                continue;
            }
            long k = ((long) s.chunkX() << 32) ^ (s.chunkZ() & 0xffffffffL);
            by.computeIfAbsent(k, x -> new int[] {s.chunkX(), s.chunkZ(), 0})[2]++;
        }
        if (by.isEmpty()) {
            out.add("[GolemLog] busiest chunks (15 min): none" + (player != null ? " near you" : ""));
            return;
        }
        List<int[]> rows = new ArrayList<>(by.values());
        rows.sort((a, b) -> Integer.compare(b[2], a[2]));
        StringBuilder sb = new StringBuilder("[GolemLog] busiest chunks (15 min)" + (player != null ? " near you" : "") + ":");
        for (int i = 0; i < Math.min(HOT_CHUNKS, rows.size()); i++) {
            int[] r = rows.get(i);
            sb.append(i == 0 ? " " : " | ").append(r[2]).append(" at x ").append(r[0] * 16).append(" z ").append(r[1] * 16)
                    .append(" (chunk ").append(r[0]).append(",").append(r[1]).append(")");
        }
        out.add(sb.toString());
    }

    private static void lastSpawn(List<String> out, long now, ServerPlayer player) {
        Spawn last = HISTORY.peekLast();
        if (last == null) {
            out.add("[GolemLog] no golem has spawned in the last hour");
            return;
        }
        String s = "[GolemLog] last spawn " + Math.max(0, (now - last.timeMs()) / 1000) + " s ago at x "
                + Math.round(last.x()) + " z " + Math.round(last.z());
        if (player != null && last.level() == player.level()) {
            double dx = last.x() - player.getX();
            double dz = last.z() - player.getZ();
            s += ", " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks from you";
        }
        out.add(s);
    }

    // ---- per-tower breakdown ----

    private static final double TOWER_JOIN_BLOCKS = 24.0;

    private static final class Cluster {
        final ServerLevel level;
        double sx;
        double sz;
        final List<Long> times = new ArrayList<>();

        Cluster(ServerLevel level) {
            this.level = level;
        }

        void add(Spawn s) {
            sx += s.x();
            sz += s.z();
            times.add(s.timeMs());
        }

        double cx() {
            return sx / times.size();
        }

        double cz() {
            return sz / times.size();
        }
    }

    /**
     * Lines for /golemlog towers: spawns in the last mins minutes grouped by where they happen (within 24
     * blocks of a group's centre is the same tower), one row per tower with count, rate and the gap between
     * its spawns. player may be null (console).
     */
    public static List<String> towers(ServerPlayer player, int mins) {
        long now = System.currentTimeMillis();
        trim(now);
        long from = now - mins * 60_000L;
        double upMin = Math.max(1.0, (now - trackingSince) / 60_000.0);
        double span = Math.min(mins, upMin);
        List<Cluster> clusters = new ArrayList<>();
        for (Spawn s : HISTORY) {
            if (s.timeMs() < from) {
                continue;
            }
            Cluster best = null;
            double bestD = TOWER_JOIN_BLOCKS;
            for (Cluster c : clusters) {
                if (c.level != s.level()) {
                    continue;
                }
                double dx = c.cx() - s.x();
                double dz = c.cz() - s.z();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= bestD) {
                    best = c;
                    bestD = d;
                }
            }
            if (best == null) {
                best = new Cluster(s.level());
                clusters.add(best);
            }
            best.add(s);
        }
        List<String> out = new ArrayList<>();
        out.add("[GolemLog] towers, last " + mins + " min (spawns within " + Math.round(TOWER_JOIN_BLOCKS)
                + " blocks of each other count as one tower; span " + fmtMin(span) + ")");
        if (clusters.isEmpty()) {
            out.add("[GolemLog] no golem has spawned in that time");
            return out;
        }
        clusters.sort((a, b) -> Integer.compare(b.times.size(), a.times.size()));
        int rank = 1;
        for (Cluster c : clusters) {
            int n = c.times.size();
            StringBuilder sb = new StringBuilder("[GolemLog] #" + rank++ + " x " + Math.round(c.cx()) + " z "
                    + Math.round(c.cz()) + ": " + n + " golems, " + perMin(n, span) + "/min");
            if (n >= 2) {
                long total = c.times.get(n - 1) - c.times.get(0);
                long min = Long.MAX_VALUE;
                long max = 0;
                for (int i = 1; i < n; i++) {
                    long g = c.times.get(i) - c.times.get(i - 1);
                    min = Math.min(min, g);
                    max = Math.max(max, g);
                }
                sb.append(String.format(Locale.ROOT, ", gap avg %.0f s (min %d, max %d)",
                        total / 1000.0 / (n - 1), min / 1000, max / 1000));
            }
            sb.append(", last ").append(Math.max(0, (now - c.times.get(n - 1)) / 1000)).append(" s ago");
            if (player != null && c.level == player.level()) {
                double dx = c.cx() - player.getX();
                double dz = c.cz() - player.getZ();
                sb.append(", ").append(Math.round(Math.sqrt(dx * dx + dz * dz))).append(" blocks from you");
            }
            out.add(sb.toString());
        }
        out.add("[GolemLog] " + clusters.size() + " towers, " + clusters.stream().mapToInt(c -> c.times.size()).sum()
                + " golems in total");
        return out;
    }

    // ---- counting ----

    private static boolean within(Spawn s, Object level, int pcx, int pcz, int radius) {
        return s.level() == level && Math.abs(s.chunkX() - pcx) <= radius && Math.abs(s.chunkZ() - pcz) <= radius;
    }

    /** Spawns with from <= time < to. With a level, only those within radius chunks of (pcx, pcz) in that level. */
    private static int count(long from, long to, Object level, int pcx, int pcz, int radius) {
        int n = 0;
        Iterator<Spawn> it = HISTORY.iterator();
        while (it.hasNext()) {
            Spawn s = it.next();
            if (s.timeMs() < from || s.timeMs() >= to) {
                continue;
            }
            if (level != null && !within(s, level, pcx, pcz, radius)) {
                continue;
            }
            n++;
        }
        return n;
    }

    private static String perMin(int n, double minutes) {
        return String.format(Locale.ROOT, "%.1f", n / minutes);
    }

    private static String fmtMin(double min) {
        return min < 1.0 ? "under a minute" : String.format(Locale.ROOT, "%.0f min", min);
    }
}
