package org.bi9clt.cwcn.core.rx;

public final class LiveRxToneEventStabilizerStats {
    private final int delayedToneOnCount;
    private final int confirmedToneOnCount;
    private final int droppedToneOnFragmentCount;
    private final int delayedToneOffCount;
    private final int droppedIsolatedToneOffCount;
    private final int bridgedToneOffCount;

    public LiveRxToneEventStabilizerStats(
            int delayedToneOnCount,
            int confirmedToneOnCount,
            int droppedToneOnFragmentCount,
            int delayedToneOffCount,
            int droppedIsolatedToneOffCount,
            int bridgedToneOffCount
    ) {
        this.delayedToneOnCount = Math.max(0, delayedToneOnCount);
        this.confirmedToneOnCount = Math.max(0, confirmedToneOnCount);
        this.droppedToneOnFragmentCount = Math.max(0, droppedToneOnFragmentCount);
        this.delayedToneOffCount = Math.max(0, delayedToneOffCount);
        this.droppedIsolatedToneOffCount = Math.max(0, droppedIsolatedToneOffCount);
        this.bridgedToneOffCount = Math.max(0, bridgedToneOffCount);
    }

    public int delayedToneOnCount() {
        return delayedToneOnCount;
    }

    public int confirmedToneOnCount() {
        return confirmedToneOnCount;
    }

    public int droppedToneOnFragmentCount() {
        return droppedToneOnFragmentCount;
    }

    public int delayedToneOffCount() {
        return delayedToneOffCount;
    }

    public int droppedIsolatedToneOffCount() {
        return droppedIsolatedToneOffCount;
    }

    public int bridgedToneOffCount() {
        return bridgedToneOffCount;
    }

    public String compactSummary() {
        return "gate on "
                + delayedToneOnCount
                + "/"
                + confirmedToneOnCount
                + "/"
                + droppedToneOnFragmentCount
                + " off "
                + delayedToneOffCount
                + "/"
                + droppedIsolatedToneOffCount
                + "/"
                + bridgedToneOffCount;
    }
}
