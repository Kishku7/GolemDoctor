package com.kishku7.golemdoctor.check;

/**
 * Why a villager is not producing iron golems. {@link #text(int)} is what the client prints in red.
 * {@link #wireId()} is the stable id sent over the network. The ready-villager count is not a Reason:
 * the client works it out per method (scare, gossip) from the counts in the reply.
 */
public enum Reason {
    NO_BED(0, "Has no bed", Scope.VILLAGER),
    /** param = whole minutes since the villager last slept, or -1 when it has never slept. */
    NOT_SLEPT(1, "Not Slept in %d minutes", Scope.VILLAGER),
    /** param = whole seconds since the villager last saw a golem. */
    GOLEM_NEARBY(2, "Saw Golem %d seconds ago", Scope.VILLAGER),
    CANT_GOSSIP(4, "Can't Gossip", Scope.GOSSIP),
    NO_SPAWN_SPACE(5, "No Spawn Space for Golem", Scope.SHARED),
    CANT_REACH_BELL(6, "Can't Reach Bell", Scope.GOSSIP);

    /** Which part of the popup a blocker belongs to. */
    public enum Scope {
        /** About this one villager. */
        VILLAGER,
        /** Only blocks the gossip method (villagers must walk to each other or to the bell). */
        GOSSIP,
        /** Blocks both methods (the spawn area). */
        SHARED
    }

    private final int wireId;
    private final String label;
    private final Scope scope;

    Reason(int wireId, String label, Scope scope) {
        this.wireId = wireId;
        this.label = label;
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    /** True when the blocker is about the group or the spawn area, not this one villager. */
    public boolean isGroup() {
        return scope != Scope.VILLAGER;
    }

    public int wireId() {
        return wireId;
    }

    /** The red text for this reason; {@code param} fills the number (minutes or seconds) when present. */
    public String text(int param) {
        switch (this) {
            case NOT_SLEPT:
                if (param < 0) return "Has Not Slept";
                return "Not Slept in " + param + (param == 1 ? " minute" : " minutes");
            case GOLEM_NEARBY:
                return "Saw Golem " + param + (param == 1 ? " second ago" : " seconds ago");
            default:
                return label;
        }
    }

    public static Reason fromWire(int id) {
        for (Reason r : values()) {
            if (r.wireId == id) return r;
        }
        return null;
    }
}
