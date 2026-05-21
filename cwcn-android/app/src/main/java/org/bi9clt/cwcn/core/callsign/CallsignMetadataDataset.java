package org.bi9clt.cwcn.core.callsign;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CallsignMetadataDataset {
    private static final Comparator<PrefixMatch> PREFIX_LENGTH_DESCENDING =
            (left, right) -> Integer.compare(right.prefix.length(), left.prefix.length());

    private final Map<String, EntityRecord> exactMatches;
    private final List<PrefixMatch> prefixMatches;
    private final Map<String, CallsignMetadata> resolvedCache = new HashMap<>();

    private CallsignMetadataDataset(
            Map<String, EntityRecord> exactMatches,
            List<PrefixMatch> prefixMatches
    ) {
        this.exactMatches = exactMatches;
        this.prefixMatches = prefixMatches;
    }

    static CallsignMetadataDataset fromAssetStreams(
            InputStream ctyInputStream,
            @Nullable InputStream countryCnInputStream
    ) throws IOException {
        String ctyText = readText(ctyInputStream);
        String countryCnText = countryCnInputStream == null ? null : readText(countryCnInputStream);
        return fromText(ctyText, countryCnText);
    }

    static CallsignMetadataDataset fromText(
            String ctyText,
            @Nullable String countryCnText
    ) {
        Map<String, String> countryTranslations = parseCountryTranslations(countryCnText);
        Map<String, EntityRecord> exactMatches = new HashMap<>();
        ArrayList<PrefixMatch> prefixMatches = new ArrayList<>();

        if (ctyText != null) {
            String[] records = ctyText.split(";");
            for (String record : records) {
                if (record == null || !record.contains(":")) {
                    continue;
                }
                String[] fields = record.split(":");
                if (fields.length < 9) {
                    continue;
                }
                String countryNameEn = normalizeText(fields[0]);
                EntityRecord entity = new EntityRecord(
                        countryNameEn,
                        countryTranslations.get(countryNameEn),
                        parseInteger(fields[1]),
                        parseInteger(fields[2]),
                        normalizeText(fields[3]),
                        parseDouble(fields[4]),
                        parseDouble(fields[5]),
                        parseDouble(fields[6]),
                        normalizeText(fields[7])
                );
                for (String rawPattern : parseCallsignPatterns(fields[8])) {
                    if (rawPattern.startsWith("=") && rawPattern.length() > 1) {
                        exactMatches.put(rawPattern.substring(1), entity);
                    } else if (!rawPattern.isEmpty()) {
                        prefixMatches.add(new PrefixMatch(rawPattern, entity));
                    }
                }
            }
        }

        prefixMatches.sort(PREFIX_LENGTH_DESCENDING);
        return new CallsignMetadataDataset(
                Collections.unmodifiableMap(exactMatches),
                Collections.unmodifiableList(prefixMatches)
        );
    }

    @Nullable
    CallsignMetadata resolve(@Nullable String callsign) {
        String normalizedCallsign = normalizeCallsign(callsign);
        if (normalizedCallsign == null) {
            return null;
        }
        synchronized (resolvedCache) {
            if (resolvedCache.containsKey(normalizedCallsign)) {
                return resolvedCache.get(normalizedCallsign);
            }
        }

        CallsignMetadata resolved = null;
        EntityRecord exactMatch = exactMatches.get(normalizedCallsign);
        if (exactMatch != null) {
            resolved = exactMatch.toMetadata(normalizedCallsign, "=" + normalizedCallsign);
        } else {
            for (PrefixMatch prefixMatch : prefixMatches) {
                if (normalizedCallsign.startsWith(prefixMatch.prefix)) {
                    resolved = prefixMatch.entity.toMetadata(normalizedCallsign, prefixMatch.prefix);
                    break;
                }
            }
        }

        synchronized (resolvedCache) {
            resolvedCache.put(normalizedCallsign, resolved);
        }
        return resolved;
    }

    private static Map<String, String> parseCountryTranslations(@Nullable String rawText) {
        HashMap<String, String> translations = new HashMap<>();
        if (rawText == null || rawText.isEmpty()) {
            return translations;
        }
        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || !line.contains(":")) {
                continue;
            }
            String[] fields = line.split(":", 2);
            String englishName = normalizeText(fields[0]);
            String chineseName = fields.length > 1 ? normalizeText(fields[1]) : null;
            if (englishName != null && chineseName != null) {
                translations.put(englishName, chineseName);
            }
        }
        return translations;
    }

    private static List<String> parseCallsignPatterns(String rawPatterns) {
        ArrayList<String> patterns = new ArrayList<>();
        if (rawPatterns == null || rawPatterns.isEmpty()) {
            return patterns;
        }
        String flattened = rawPatterns.replace('\r', ' ').replace('\n', ' ');
        String[] tokens = flattened.split(",");
        for (String token : tokens) {
            String normalized = normalizePatternToken(token);
            if (normalized != null) {
                patterns.add(normalized);
            }
        }
        return patterns;
    }

    @Nullable
    private static String normalizePatternToken(@Nullable String token) {
        String normalized = normalizeText(token);
        if (normalized == null) {
            return null;
        }
        int parenthesesIndex = normalized.indexOf('(');
        if (parenthesesIndex >= 0) {
            normalized = normalized.substring(0, parenthesesIndex).trim();
        }
        int bracketIndex = normalized.indexOf('[');
        if (bracketIndex >= 0) {
            normalized = normalized.substring(0, bracketIndex).trim();
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.US);
    }

    @Nullable
    private static String normalizeCallsign(@Nullable String callsign) {
        String normalized = normalizeText(callsign);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace("<", "").replace(">", "");
        return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.US);
    }

    @Nullable
    private static String normalizeText(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseInteger(@Nullable String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parseDouble(@Nullable String value) {
        try {
            return value == null ? 0.0d : Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private static String readText(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private static final class PrefixMatch {
        private final String prefix;
        private final EntityRecord entity;

        private PrefixMatch(String prefix, EntityRecord entity) {
            this.prefix = prefix;
            this.entity = entity;
        }
    }

    private static final class EntityRecord {
        private final String countryNameEn;
        private final String countryNameZhCn;
        private final int cqZone;
        private final int ituZone;
        private final String continent;
        private final double latitude;
        private final double longitude;
        private final double gmtOffsetHours;
        private final String dxccPrefix;

        private EntityRecord(
                String countryNameEn,
                String countryNameZhCn,
                int cqZone,
                int ituZone,
                String continent,
                double latitude,
                double longitude,
                double gmtOffsetHours,
                String dxccPrefix
        ) {
            this.countryNameEn = countryNameEn;
            this.countryNameZhCn = countryNameZhCn;
            this.cqZone = cqZone;
            this.ituZone = ituZone;
            this.continent = continent;
            this.latitude = latitude;
            this.longitude = longitude;
            this.gmtOffsetHours = gmtOffsetHours;
            this.dxccPrefix = dxccPrefix;
        }

        private CallsignMetadata toMetadata(String callsign, String matchedRule) {
            return new CallsignMetadata(
                    callsign,
                    matchedRule,
                    countryNameEn,
                    countryNameZhCn,
                    cqZone,
                    ituZone,
                    continent,
                    latitude,
                    longitude,
                    gmtOffsetHours,
                    dxccPrefix
            );
        }
    }
}
