package org.bi9clt.cwcn.core.log;

public final class ConfirmedLogQuery {
    public enum SortOrder {
        QSO_TIME_DESC,
        QSO_TIME_ASC,
        CALLSIGN_ASC,
        CALLSIGN_DESC
    }

    public enum ConfirmationFilter {
        ALL,
        MANUALLY_CONFIRMED,
        UNCONFIRMED
    }

    private final String callsignFilter;
    private final Boolean reviewOnly;
    private final String fromQsoDateUtc;
    private final String toQsoDateUtc;
    private final ConfirmationFilter confirmationFilter;
    private final SortOrder sortOrder;

    public ConfirmedLogQuery(
            String callsignFilter,
            Boolean reviewOnly,
            String fromQsoDateUtc,
            String toQsoDateUtc,
            ConfirmationFilter confirmationFilter,
            SortOrder sortOrder
    ) {
        this.callsignFilter = callsignFilter;
        this.reviewOnly = reviewOnly;
        this.fromQsoDateUtc = fromQsoDateUtc;
        this.toQsoDateUtc = toQsoDateUtc;
        this.confirmationFilter = confirmationFilter == null ? ConfirmationFilter.ALL : confirmationFilter;
        this.sortOrder = sortOrder == null ? SortOrder.QSO_TIME_DESC : sortOrder;
    }

    public static ConfirmedLogQuery defaultQuery() {
        return new ConfirmedLogQuery(null, null, null, null, ConfirmationFilter.ALL, SortOrder.QSO_TIME_DESC);
    }

    public String callsignFilter() {
        return callsignFilter;
    }

    public Boolean reviewOnly() {
        return reviewOnly;
    }

    public String fromQsoDateUtc() {
        return fromQsoDateUtc;
    }

    public String toQsoDateUtc() {
        return toQsoDateUtc;
    }

    public ConfirmationFilter confirmationFilter() {
        return confirmationFilter;
    }

    public SortOrder sortOrder() {
        return sortOrder;
    }
}
