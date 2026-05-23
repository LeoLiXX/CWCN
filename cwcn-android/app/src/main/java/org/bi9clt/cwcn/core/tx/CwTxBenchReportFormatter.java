package org.bi9clt.cwcn.core.tx;

public final class CwTxBenchReportFormatter {
    private CwTxBenchReportFormatter() {
    }

    public static String format(
            String benchSummary,
            String recentUsbIssue,
            String backendSummary,
            String planSummary,
            String usbSummary,
            String txStatus,
            String txProgress,
            String benchLog
    ) {
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "联调摘要", benchSummary);
        appendOptionalSection(builder, "最近 USB 问题", recentUsbIssue);
        appendSection(builder, "发射路径", backendSummary);
        appendSection(builder, "发射计划", planSummary);
        appendSection(builder, "USB 链路", usbSummary);
        appendSection(builder, "发射状态", txStatus);
        appendSection(builder, "发射进度", txProgress);
        appendSection(builder, "联调日志", benchLog);
        return builder.toString();
    }

    private static void appendOptionalSection(StringBuilder builder, String title, String body) {
        String normalizedBody = normalize(body, "");
        if (normalizedBody.isEmpty()) {
            return;
        }
        appendSection(builder, title, normalizedBody);
    }

    private static void appendSection(StringBuilder builder, String title, String body) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("== ").append(normalize(title, "区块")).append(" ==\n");
        builder.append(normalize(body, "（空）"));
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.replace("\r\n", "\n").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
