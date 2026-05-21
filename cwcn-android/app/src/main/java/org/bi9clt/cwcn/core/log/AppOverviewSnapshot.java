package org.bi9clt.cwcn.core.log;

import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;

public final class AppOverviewSnapshot {
    private final QsoDraftSnapshot activeDraft;
    private final int confirmedLogCount;
    private final int reviewQueueCount;
    private final int manuallyConfirmedCount;
    private final ConfirmedQsoLog latestConfirmedLog;

    public AppOverviewSnapshot(
            QsoDraftSnapshot activeDraft,
            int confirmedLogCount,
            int reviewQueueCount,
            int manuallyConfirmedCount,
            ConfirmedQsoLog latestConfirmedLog
    ) {
        this.activeDraft = activeDraft;
        this.confirmedLogCount = confirmedLogCount;
        this.reviewQueueCount = reviewQueueCount;
        this.manuallyConfirmedCount = manuallyConfirmedCount;
        this.latestConfirmedLog = latestConfirmedLog;
    }

    public QsoDraftSnapshot activeDraft() {
        return activeDraft;
    }

    public int confirmedLogCount() {
        return confirmedLogCount;
    }

    public int reviewQueueCount() {
        return reviewQueueCount;
    }

    public int manuallyConfirmedCount() {
        return manuallyConfirmedCount;
    }

    public int manualReviewLogCount() {
        return reviewQueueCount;
    }

    public ConfirmedQsoLog latestConfirmedLog() {
        return latestConfirmedLog;
    }
}
