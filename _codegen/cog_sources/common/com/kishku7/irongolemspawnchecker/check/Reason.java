package com.kishku7.irongolemspawnchecker.check;

/**
 * Why a villager is not producing iron golems. Each label is one to three words, upper case, and is
 * what the client prints in red. {@link #wireId()} is the stable id sent over the network.
 */
public enum Reason {
    NO_BED(0, "NO BED"),
    NOT_SLEPT(1, "NOT SLEPT"),
    GOLEM_NEARBY(2, "GOLEM NEARBY"),
    TOO_FEW_READY(3, "%d/5 READY"),
    CANNOT_MEET(4, "CANNOT MEET"),
    NO_SPAWN_SPACE(5, "NO SPAWN SPACE");

    private final int wireId;
    private final String label;

    Reason(int wireId, String label) {
        this.wireId = wireId;
        this.label = label;
    }

    public int wireId() {
        return wireId;
    }

    /** The red text for this reason; {@code param} fills a %d (the ready-villager count) when present. */
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
