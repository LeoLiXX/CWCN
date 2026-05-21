package org.bi9clt.cwcn.core.log;

public final class LogbookExportRequest {
    public enum ConfirmationScope {
        ALL,
        UNCONFIRMED,
        CONFIRMED
    }

    public enum TimeRangeScope {
        ALL,
        TODAY,
        THIS_MONTH
    }

    private final ConfirmationScope confirmationScope;
    private final TimeRangeScope timeRangeScope;

    public LogbookExportRequest(
            ConfirmationScope confirmationScope,
            TimeRangeScope timeRangeScope
    ) {
        this.confirmationScope = confirmationScope == null ? ConfirmationScope.ALL : confirmationScope;
        this.timeRangeScope = timeRangeScope == null ? TimeRangeScope.ALL : timeRangeScope;
    }

    public ConfirmationScope confirmationScope() {
        return confirmationScope;
    }

    public TimeRangeScope timeRangeScope() {
        return timeRangeScope;
    }
}
