package org.bi9clt.cwcn.core.rig;

import java.util.Locale;

public final class RigProfileFamilies {
    private RigProfileFamilies() {
    }

    public static boolean isYaesuFamily(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String vendor = safeLower(profile.vendorLabel());
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return vendor.contains("yaesu")
                || id.contains("yaesu")
                || model.contains("ft-");
    }

    public static boolean isIcomFamily(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String vendor = safeLower(profile.vendorLabel());
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return vendor.contains("icom")
                || id.contains("icom")
                || model.contains("ic-");
    }

    public static boolean isKenwoodFamily(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String vendor = safeLower(profile.vendorLabel());
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return vendor.contains("kenwood")
                || id.contains("kenwood")
                || model.contains("ts-");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
