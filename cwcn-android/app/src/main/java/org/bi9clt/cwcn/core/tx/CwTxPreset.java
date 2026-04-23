package org.bi9clt.cwcn.core.tx;

public enum CwTxPreset {
    BENCH_DIT("Bench DIT", "E E E"),
    BENCH_PATTERN("Bench VVV", "VVV VVV DE {MYCALL}"),
    GENERAL_CQ("CQ General", "CQ CQ CQ DE {MYCALL} {MYCALL} K"),
    CQ_DX("CQ DX", "CQ DX CQ DX DE {MYCALL} K"),
    TEST_CALL("Test Call", "VVV VVV TEST DE {MYCALL} K"),
    REPORT_599("Send 599", "UR RST 599 599 BK"),
    AGN_PSE("AGN / PSE", "AGN AGN PSE BK"),
    TU_73("TU 73", "TU TU 73 EE");

    private final String displayName;
    private final String template;

    CwTxPreset(String displayName, String template) {
        this.displayName = displayName;
        this.template = template;
    }

    public String displayName() {
        return displayName;
    }

    public String template() {
        return template;
    }

    public String render(String stationCallsign) {
        String rendered = template.replace("{MYCALL}", normalizePlaceholderValue(stationCallsign));
        return rendered.trim().replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return displayName;
    }

    private String normalizePlaceholderValue(String value) {
        if (value == null) {
            return "MYCALL";
        }
        String trimmed = value.trim().toUpperCase();
        return trimmed.isEmpty() ? "MYCALL" : trimmed;
    }
}
