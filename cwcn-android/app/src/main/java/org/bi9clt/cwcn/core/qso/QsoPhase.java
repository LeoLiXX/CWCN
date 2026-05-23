package org.bi9clt.cwcn.core.qso;

public enum QsoPhase {
    IDLE("idle"),
    CALLING_CQ("calling_cq"),
    AWAITING_REPLY("awaiting_reply"),
    REPLY_DETECTED("reply_detected"),
    REPORT_EXCHANGE("report_exchange"),
    INFO_EXCHANGE("info_exchange"),
    CLOSING("closing"),
    COMPLETED("completed");

    private final String displayName;

    QsoPhase(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String stableId() {
        return displayName;
    }

    public static QsoPhase fromPersistedValue(String rawPhase) {
        if (rawPhase == null || rawPhase.isEmpty()) {
            return IDLE;
        }
        for (QsoPhase phase : values()) {
            if (rawPhase.equals(phase.name()) || rawPhase.equals(phase.stableId())) {
                return phase;
            }
        }
        return IDLE;
    }
}
