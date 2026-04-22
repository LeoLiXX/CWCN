package org.bi9clt.cwcn.core.log;

public final class ConfirmedLogQuery {
    public enum SortOrder {
        CONFIRMED_AT_DESC,
        CONFIRMED_AT_ASC,
        CALLSIGN_ASC,
        CALLSIGN_DESC
    }

    private final String callsignFilter;
    private final Boolean reviewOnly;
    private final String fromQsoDateUtc;
    private final String toQsoDateUtc;
    private final SortOrder sortOrder;

    public ConfirmedLogQuery(String callsignFilter, Boolean reviewOnly, SortOrder sortOrder) {
        this(callsignFilter, reviewOnly, null, null, sortOrder);
    }

    public ConfirmedLogQuery(
            String callsignFilter,
            Boolean reviewOnly,
            String fromQsoDateUtc,
            String toQsoDateUtc,
            SortOrder sortOrder
    ) {
        this.callsignFilter = callsignFilter;
        this.reviewOnly = reviewOnly;
        this.fromQsoDateUtc = fromQsoDateUtc;
        this.toQsoDateUtc = toQsoDateUtc;
        this.sortOrder = sortOrder;
    }

    public static ConfirmedLogQuery defaultQuery() {
        return new ConfirmedLogQuery(null, null, null, null, SortOrder.CONFIRMED_AT_DESC);
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

    public SortOrder sortOrder() {
        return sortOrder;
    }
}
