package com.kishku7.golemdoctor.check;

import java.util.List;

/**
 * Everything the server tells the client about one villager. Pure data (no Minecraft types) so it can
 * be shared by every loader and unit-tested.
 *
 * @param entityId the villager's entity id this answers
 * @param reasons  every blocker found, highest priority first; empty means "should spawn"
 * @param village  the village the villager belongs to
 */
public record CheckResult(int entityId, List<Entry> reasons, Village village) {

    /** One blocker; {@code param} is only meaningful for {@link Reason#TOO_FEW_READY}. */
    public record Entry(Reason reason, int param) {
        public String text() {
            return reason.text(param);
        }
    }

    /**
     * The village a villager belongs to, grouped from nearby beds, job sites and bells.
     *
     * @param known  false when no village points were found near the villager
     * @param rank   this villager's number in the village, 1..total (ordered by UUID)
     * @param total  villagers counted in the village
     * @param chunksX village width in chunks
     * @param chunksZ village depth in chunks
     */
    public record Village(boolean known, int rank, int total, int chunksX, int chunksZ,
                          int minX, int minZ, int maxX, int maxZ) {
        public static Village unknown() {
            return new Village(false, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
