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

    public static boolean isXieguFamily(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String vendor = safeLower(profile.vendorLabel());
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return vendor.contains("xiegu")
                || id.contains("xiegu")
                || model.contains("x6100")
                || model.contains("x6200")
                || model.contains("g90");
    }

    public static boolean isXieguPortableUsbFamily(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return id.contains("x6100")
                || id.contains("x6200")
                || model.contains("x6100")
                || model.contains("x6200");
    }

    public static boolean isXieguG90Line(RigProfile profile) {
        if (profile == null) {
            return false;
        }
        String id = safeLower(profile.id());
        String model = safeLower(profile.modelLabel());
        return id.contains("g90")
                || model.contains("g90");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
