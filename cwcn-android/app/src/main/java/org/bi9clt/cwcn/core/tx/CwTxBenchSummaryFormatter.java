package org.bi9clt.cwcn.core.tx;

public final class CwTxBenchSummaryFormatter {
    private CwTxBenchSummaryFormatter() {
    }

    public static String format(
            String backendId,
            String backendDisplayName,
            boolean backendReady,
            boolean backendRunning,
            String backendAvailability,
            String usbDiagnosticCode,
            String recentUsbIssue,
            CwTxState lastState,
            String nextAction
    ) {
        StringBuilder builder = new StringBuilder();
        appendRecentOutcome(builder, lastState);
        builder.append(buildCoreSummary(
                backendId,
                backendDisplayName,
                backendReady,
                backendRunning,
                backendAvailability,
                usbDiagnosticCode
        ));
        appendRecentUsbIssue(builder, usbDiagnosticCode, recentUsbIssue);
        String normalizedNextAction = normalize(nextAction);
        if (!normalizedNextAction.isEmpty()) {
            builder.append("\nNext: ").append(normalizedNextAction);
        }
        return builder.toString();
    }

    private static void appendRecentOutcome(StringBuilder builder, CwTxState lastState) {
        if (lastState == null) {
            return;
        }
        if (lastState == CwTxState.ERROR) {
            builder.append("Last TX ended with an error. ");
            return;
        }
        if (lastState == CwTxState.COMPLETED) {
            builder.append("Last TX completed. ");
            return;
        }
        if (lastState == CwTxState.STOPPED) {
            builder.append("Last TX was stopped. ");
        }
    }

    private static String buildCoreSummary(
            String backendId,
            String backendDisplayName,
            boolean backendReady,
            boolean backendRunning,
            String backendAvailability,
            String usbDiagnosticCode
    ) {
        String normalizedName = normalize(backendDisplayName);
        String normalizedAvailability = normalize(backendAvailability);
        String normalizedBackendId = normalize(backendId);
        if (normalizedBackendId.isEmpty()) {
            return "Select a TX backend first.";
        }
        if (backendRunning) {
            return normalizedName.isEmpty()
                    ? "TX is active. Watch hardware behavior and the bench log."
                    : normalizedName + " is actively transmitting. Watch hardware behavior and the bench log.";
        }
        if ("local-sidetone".equals(normalizedBackendId)) {
            return backendReady
                    ? "Local sidetone route is ready for a dry-run TX check."
                    : "Local sidetone route is not ready. " + fallback(normalizedAvailability, "Review local audio output.");
        }
        if ("rig-text:audio-vox-text".equals(normalizedBackendId)) {
            return backendReady
                    ? "Audio VOX route is ready for a short conservative bench transmission."
                    : "Audio VOX route is not ready. " + fallback(normalizedAvailability, "Review the audio path and VOX setup.");
        }
        if (normalizedBackendId.startsWith("rig-text:usb-serial-keyer")) {
            return buildUsbSummary(backendReady, normalizedAvailability, usbDiagnosticCode);
        }
        if (backendReady) {
            return fallback(normalizedName, "Selected TX backend") + " is ready for bench validation.";
        }
        return fallback(normalizedName, "Selected TX backend") + " is not ready. "
                + fallback(normalizedAvailability, "Review route availability.");
    }

    private static String buildUsbSummary(
            boolean backendReady,
            String backendAvailability,
            String usbDiagnosticCode
    ) {
        if ("usb-serial-target-missing".equals(usbDiagnosticCode)) {
            return "USB keyer route is blocked because the locked target is missing.";
        }
        if ("usb-serial-no-device".equals(usbDiagnosticCode)) {
            return "USB keyer route is blocked because no USB device is attached.";
        }
        if ("usb-serial-no-cdc".equals(usbDiagnosticCode)) {
            return "USB keyer route is blocked because no CDC/ACM keyer device is available.";
        }
        if ("usb-serial-no-permission".equals(usbDiagnosticCode)) {
            return "USB keyer route is blocked by missing USB permission.";
        }
        if ("usb-serial-open-failed".equals(usbDiagnosticCode)) {
            return "USB keyer route found a target, but opening the device failed.";
        }
        if ("usb-serial-claim-failed".equals(usbDiagnosticCode)) {
            return "USB keyer route opened the device, but control interface claim failed.";
        }
        if ("usb-serial-no-control-interface".equals(usbDiagnosticCode)) {
            return "USB keyer route found a device without the expected control interface.";
        }
        if ("usb-serial-ready".equals(usbDiagnosticCode) || backendReady) {
            return "USB keyer route is ready for a short bench transmission.";
        }
        return "USB keyer route is not ready. " + fallback(backendAvailability, "Review the USB route state.");
    }

    private static void appendRecentUsbIssue(
            StringBuilder builder,
            String usbDiagnosticCode,
            String recentUsbIssue
    ) {
        String normalizedRecentIssue = normalize(recentUsbIssue);
        if (normalizedRecentIssue.isEmpty()) {
            return;
        }
        if (!"usb-serial-ready".equals(normalize(usbDiagnosticCode))) {
            return;
        }
        builder.append("\nRecent issue: ").append(normalizedRecentIssue);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
