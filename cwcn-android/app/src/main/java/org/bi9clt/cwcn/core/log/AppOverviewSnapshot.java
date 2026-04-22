package org.bi9clt.cwcn.core.log;

import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;

public final class AppOverviewSnapshot {
    private final QsoDraftSnapshot activeDraft;
    private final int confirmedLogCount;
    private final ConfirmedQsoLog latestConfirmedLog;

    public AppOverviewSnapshot(QsoDraftSnapshot activeDraft, int confirmedLogCount, ConfirmedQsoLog latestConfirmedLog) {
        this.activeDraft = activeDraft;
        this.confirmedLogCount = confirmedLogCount;
        this.latestConfirmedLog = latestConfirmedLog;
    }

    public QsoDraftSnapshot activeDraft() {
        return activeDraft;
    }

    public int confirmedLogCount() {
        return confirmedLogCount;
    }

    public ConfirmedQsoLog latestConfirmedLog() {
        return latestConfirmedLog;
    }
}
