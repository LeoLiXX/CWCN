package org.bi9clt.cwcn.core.rig;

public enum CatProtocolFamily {
    GENERIC("Generic CAT", "Best starting point when the exact radio dialect is still unknown."),
    YAESU_STYLE("Yaesu-style CAT", "Typical FT-series serial CAT framing and command semantics."),
    ICOM_CIV("Icom CI-V", "Icom CI-V style command family; usually needs a radio address later."),
    KENWOOD_STYLE("Kenwood-style CAT", "TS-series style ASCII command family shared by several compatibles."),
    HAMLIB_RIGCTLD("Hamlib rigctld", "Network-facing CAT family for rigs or bridges exposed through rigctld.");

    private final String displayName;
    private final String summary;

    CatProtocolFamily(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
