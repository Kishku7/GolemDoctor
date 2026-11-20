package com.kishku7.golemdoctor;

/** Shared mod constants (loader-agnostic, no net.minecraft imports). */
public final class GolemDoctor {
    private GolemDoctor() {}

    public static final String MOD_ID = "golemdoctor";

    /** Villagers (that qualify) a villager needs within range before vanilla will spawn a golem. */
    public static final int VILLAGERS_NEEDED = 5;

    /** Ticks since a villager last slept within which it still counts for a golem (vanilla: 24000). */
    public static final long SLEEP_WINDOW_TICKS = 24000L;

    /** Client re-asks the server this often (ticks) while the crosshair stays on one villager. */
    public static final int REFRESH_TICKS = 10;

    /** Server drops a player's requests that arrive faster than this many ticks apart. */
    public static final int MIN_REQUEST_GAP_TICKS = 4;

    /** Largest distance (blocks) the server accepts between a requesting player and the villager. */
    public static final double MAX_REACH_BLOCKS = 12.0;
}
