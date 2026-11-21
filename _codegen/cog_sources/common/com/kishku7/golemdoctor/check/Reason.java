package com.kishku7.golemdoctor.check;

/**
 * Why a villager is not producing iron golems. Each label is one to three words, upper case, and is
 * what the client prints in red. {@link #wireId()} is the stable id sent over the network.
 */
public enum Reason {
    NO_BED(0, "NO BED", false),
    NOT_SLEPT(1, "NOT SLEPT", false),
    GOLEM_NEARBY(2, "GOLEM NEARBY", false),
    TOO_FEW_READY(3, "NEED %d READY", true),
    CANNOT_MEET(4, "CANNOT MEET", false),
    NO_SPAWN_SPACE(5, "NO SPAWN SPACE", true);

    private final int wireId;
    private final String label;
    private final boolean group;

    Reason(int wireId, String label, boolean group) {
        this.wireId = wireId;
        this.label = label;
        this.group = group;
    }

    /** True when the blocker is about the group of villagers (or the spawn area), not this one villager. */
    public boolean isGroup() {
        return group;
    }

    public int wireId() {
        return wireId;
    }

    /** The red text for this reason; {@code param} fills a %d (the number of ready villagers needed) when present. */
    public String text(int param) {
        return label.contains("%d") ? String.format(label, param) : label;
    }

    public static Reason fromWire(int id) {
        for (Reason r : values()) {
            if (r.wireId == id) return r;
        }
        return null;
    }
}
