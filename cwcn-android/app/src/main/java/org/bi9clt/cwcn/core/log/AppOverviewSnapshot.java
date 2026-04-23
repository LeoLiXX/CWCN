package org.bi9clt.cwcn.core.log;

import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;

public final class AppOverviewSnapshot {
    private final QsoDraftSnapshot activeDraft;
    private final int confirmedLogCount;
    private final int manualReviewLogCount;
    private final ConfirmedQsoLog latestConfirmedLog;

    public AppOverviewSnapshot(
            QsoDraftSnapshot activeDraft,
            int confirmedLogCount,
            int manualReviewLogCount,
            ConfirmedQsoLog latestConfirmedLog
    ) {
        this.activeDraft = activeDraft;
        this.confirmedLogCount = confirmedLogCount;
        this.manualReviewLogCount = manualReviewLogCount;
        this.latestConfirmedLog = latestConfirmedLog;
    }

    public QsoDraftSnapshot activeDraft() {
        return activeDraft;
    }

    public int confirmedLogCount() {
        return confirmedLogCount;
    }

    public int manualReviewLogCount() {
        return manualReviewLogCount;
    }

    public ConfirmedQsoLog latestConfirmedLog() {
        return latestConfirmedLog;
    }
}
