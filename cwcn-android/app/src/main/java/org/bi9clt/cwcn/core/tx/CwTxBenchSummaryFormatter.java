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
            builder.append("\n下一步：").append(normalizedNextAction);
        }
        return builder.toString();
    }

    private static void appendRecentOutcome(StringBuilder builder, CwTxState lastState) {
        if (lastState == null) {
            return;
        }
        if (lastState == CwTxState.ERROR) {
            builder.append("上一次 TX 以错误结束。");
            builder.append("\n");
            return;
        }
        if (lastState == CwTxState.COMPLETED) {
            builder.append("上一次 TX 已完成。");
            builder.append("\n");
            return;
        }
        if (lastState == CwTxState.STOPPED) {
            builder.append("上一次 TX 已停止。");
        }
        if (builder.length() > 0) {
            builder.append("\n");
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
            return "请先选择一条 TX 路径。";
        }
        if (backendRunning) {
            return normalizedName.isEmpty()
                    ? "TX 正在进行中。请观察硬件行为和发射验证日志。"
                    : normalizedName + " 正在发射中。请观察硬件行为和发射验证日志。";
        }
        if ("local-sidetone".equals(normalizedBackendId)) {
            return backendReady
                    ? "本地侧音链路已就绪，可用于脱机 TX 演练。"
                    : "本地侧音链路还没准备好。"
                    + fallback(normalizedAvailability, "请检查本地音频输出。");
        }
        if ("rig-text:audio-vox-text".equals(normalizedBackendId)) {
            return backendReady
                    ? "音频 VOX 链路已就绪，适合先做一段保守的短发射验证。"
                    : "音频 VOX 链路还没准备好。"
                    + fallback(normalizedAvailability, "请检查音频链路和 VOX 设置。");
        }
        if (normalizedBackendId.startsWith("rig-text:usb-serial-keyer")) {
            return buildUsbSummary(backendReady, normalizedAvailability, usbDiagnosticCode);
        }
        if (backendReady) {
            return fallback(normalizedName, "当前 TX 路径") + " 已就绪，可进入发射验证。";
        }
        return fallback(normalizedName, "当前 TX 路径") + " 还没准备好。"
                + fallback(normalizedAvailability, "请检查链路可用性。");
    }

    private static String buildUsbSummary(
            boolean backendReady,
            String backendAvailability,
            String usbDiagnosticCode
    ) {
        if ("usb-serial-target-missing".equals(usbDiagnosticCode)) {
            return "USB 键控链路不可用：锁定的目标设备当前不存在。";
        }
        if ("usb-serial-no-device".equals(usbDiagnosticCode)) {
            return "USB 键控链路不可用：当前没有接入 USB 设备。";
        }
        if ("usb-serial-no-cdc".equals(usbDiagnosticCode)) {
            return "USB 键控链路不可用：没有检测到可用的 CDC/ACM 键控设备。";
        }
        if ("usb-serial-no-permission".equals(usbDiagnosticCode)) {
            return "USB 键控链路不可用：缺少 USB 权限。";
        }
        if ("usb-serial-open-failed".equals(usbDiagnosticCode)) {
            return "USB 键控链路找到了目标设备，但打开设备失败。";
        }
        if ("usb-serial-claim-failed".equals(usbDiagnosticCode)) {
            return "USB 键控链路已打开设备，但声明控制接口失败。";
        }
        if ("usb-serial-no-control-interface".equals(usbDiagnosticCode)) {
            return "USB 键控链路找到了设备，但缺少预期的控制接口。";
        }
        if ("usb-serial-ready".equals(usbDiagnosticCode) || backendReady) {
            return "USB 键控链路已就绪，适合先做一段短发射验证。";
        }
        return "USB 键控链路还没准备好。"
                + fallback(backendAvailability, "请检查 USB 链路状态。");
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
        builder.append("\n最近问题：").append(normalizedRecentIssue);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
