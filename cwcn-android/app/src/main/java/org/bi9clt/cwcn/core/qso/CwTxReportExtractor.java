package org.bi9clt.cwcn.core.qso;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.adif.CwAdifExporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwTxReportExtractor {
    private CwTxReportExtractor() {
    }

    @Nullable
    public static String extractSentRst(@Nullable String transmittedText) {
        List<String> tokens = tokenize(transmittedText);
        if (tokens.isEmpty()) {
            return null;
        }

        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("RST".equals(token)) {
                String directReport = reportTokenAt(tokens, index + 1);
                if (directReport != null) {
                    return directReport;
                }
                continue;
            }
            if ("UR".equals(token) || "R".equals(token)) {
                String directReport = reportTokenAt(tokens, index + 1);
                if (directReport != null) {
                    return directReport;
                }
                if ("RST".equals(tokenAt(tokens, index + 1))) {
                    String labeledReport = reportTokenAt(tokens, index + 2);
                    if (labeledReport != null) {
                        return labeledReport;
                    }
                }
            }
        }

        for (String token : tokens) {
            String fallbackReport = normalizeReportToken(token);
            if (fallbackReport != null) {
                return fallbackReport;
            }
        }
        return null;
    }

    @Nullable
    private static String reportTokenAt(List<String> tokens, int index) {
        return normalizeReportToken(tokenAt(tokens, index));
    }

    @Nullable
    private static String tokenAt(List<String> tokens, int index) {
        if (tokens == null || index < 0 || index >= tokens.size()) {
            return null;
        }
        return tokens.get(index);
    }

    @Nullable
    private static String normalizeReportToken(@Nullable String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String normalized = CwAdifExporter.normalizeRstValue(rawToken);
        if (normalized == null || normalized.length() != 3) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (!Character.isDigit(normalized.charAt(index))) {
                return null;
            }
        }
        char readability = normalized.charAt(0);
        char strength = normalized.charAt(1);
        char tone = normalized.charAt(2);
        if (readability < '1' || readability > '5') {
            return null;
        }
        if (strength < '1' || strength > '9') {
            return null;
        }
        if (tone < '1' || tone > '9') {
            return null;
        }
        return normalized;
    }

    private static List<String> tokenize(@Nullable String transmittedText) {
        ArrayList<String> tokens = new ArrayList<>();
        if (transmittedText == null) {
            return tokens;
        }
        String normalized = transmittedText.toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9?/]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return tokens;
        }
        String[] rawTokens = normalized.split("\\s+");
        for (String rawToken : rawTokens) {
            if (rawToken != null && !rawToken.isEmpty()) {
                tokens.add(rawToken);
            }
        }
        return tokens;
    }
}
