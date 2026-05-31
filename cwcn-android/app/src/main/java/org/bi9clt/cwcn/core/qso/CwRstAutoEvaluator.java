package org.bi9clt.cwcn.core.qso;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;

import java.util.Locale;

public final class CwRstAutoEvaluator {
    private CwRstAutoEvaluator() {
    }

    public static Assessment evaluateSentSuggestion(
            @Nullable RxSessionSnapshot sessionSnapshot,
            @Nullable SpectrumSnapshotData spectrumSnapshot
    ) {
        int readabilityScore = computeReadabilityScore(sessionSnapshot);
        int signalScore = computeSignalScore(sessionSnapshot, spectrumSnapshot);
        boolean hasEvidence = hasSessionEvidence(sessionSnapshot);
        String debugSummary = buildDebugSummary(
                sessionSnapshot,
                spectrumSnapshot,
                readabilityScore,
                signalScore,
                hasEvidence
        );
        int readability = mapReadability(readabilityScore);
        int strength = mapStrength(signalScore);
        if (!hasEvidence || readability == 0 || strength == 0) {
            return Assessment.none(readabilityScore, signalScore, debugSummary);
        }
        int confidence = clampScore(Math.round(readabilityScore * 0.45f + signalScore * 0.55f));
        return new Assessment(
                "" + readability + strength + "9",
                confidence,
                readabilityScore,
                signalScore,
                Source.LIVE_RX_AUDIO,
                debugSummary
        );
    }

    private static int computeReadabilityScore(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        String normalized = trimToEmpty(snapshot.normalizedText());
        String raw = trimToEmpty(snapshot.rawText());
        String preview = trimToEmpty(snapshot.previewRawText());
        String callsign = trimToEmpty(snapshot.primaryCallsignCandidate());

        int score = 0;
        if (!normalized.isEmpty()) {
            score += 52;
        } else if (!raw.isEmpty()) {
            score += 32;
        } else if (!preview.isEmpty()) {
            score += 14;
        }

        if (!callsign.isEmpty()) {
            score += callsign.indexOf('?') >= 0 ? 8 : 18;
        }

        if (containsAny(normalized, "CQ", "DE", "UR", "BK", " K ", " KN")) {
            score += 10;
        }
        if (!normalized.isEmpty() && normalized.indexOf('?') < 0) {
            score += 6;
        }

        score -= Math.min(20, countCharacter(normalized, '?') * 5);
        if (snapshot.inputLevelClipping()) {
            score -= 8;
        }
        if (snapshot.inputLevelHot()) {
            score -= 4;
        }
        if (!snapshot.captureActive()) {
            score -= 4;
        }
        return clampScore(score);
    }

    private static int computeSignalScore(
            @Nullable RxSessionSnapshot sessionSnapshot,
            @Nullable SpectrumSnapshotData spectrumSnapshot
    ) {
        if (spectrumSnapshot == null) {
            return 0;
        }
        double tone = spectrumSnapshot.sqlToneRmsAmplitude();
        if (tone <= 0.0d) {
            return 0;
        }

        double frame = spectrumSnapshot.sqlFrameRmsAmplitude();
        double manual = spectrumSnapshot.sqlManualThreshold();
        double recommended = spectrumSnapshot.sqlRecommendedThreshold();
        double noise = spectrumSnapshot.sqlNoiseFloorEstimate();

        double toneOverThreshold = manual > 0.0d
                ? tone / manual
                : (recommended > 0.0d ? tone / recommended : 0.0d);
        double toneOverFrame = frame > 0.0d ? tone / frame : 0.0d;
        double toneOverNoise = noise > 0.0d ? tone / noise : 0.0d;

        int score = 0;
        if (toneOverThreshold >= 1.8d) {
            score += 44;
        } else if (toneOverThreshold >= 1.2d) {
            score += 34;
        } else if (toneOverThreshold >= 0.85d) {
            score += 24;
        } else if (toneOverThreshold >= 0.60d) {
            score += 14;
        } else {
            score += 6;
        }

        if (toneOverFrame >= 0.70d) {
            score += 28;
        } else if (toneOverFrame >= 0.50d) {
            score += 22;
        } else if (toneOverFrame >= 0.35d) {
            score += 14;
        } else if (toneOverFrame >= 0.20d) {
            score += 8;
        } else {
            score += 4;
        }

        if (toneOverNoise >= 1.6d) {
            score += 18;
        } else if (toneOverNoise >= 1.1d) {
            score += 12;
        } else if (toneOverNoise >= 0.8d) {
            score += 8;
        } else if (toneOverNoise >= 0.5d) {
            score += 4;
        }

        if (spectrumSnapshot.syntheticFallback()) {
            score -= 10;
        }
        if (sessionSnapshot != null && sessionSnapshot.inputLevelClipping()) {
            score -= 8;
        }
        if (sessionSnapshot != null && sessionSnapshot.inputLevelHot()) {
            score -= 4;
        }
        return clampScore(score);
    }

    private static boolean hasSessionEvidence(@Nullable RxSessionSnapshot snapshot) {
        return snapshot != null
                && (!trimToEmpty(snapshot.normalizedText()).isEmpty()
                || !trimToEmpty(snapshot.rawText()).isEmpty()
                || !trimToEmpty(snapshot.previewRawText()).isEmpty()
                || !trimToEmpty(snapshot.primaryCallsignCandidate()).isEmpty());
    }

    private static int mapReadability(int readabilityScore) {
        if (readabilityScore >= 70) {
            return 5;
        }
        if (readabilityScore >= 35) {
            return 4;
        }
        return 0;
    }

    private static int mapStrength(int signalScore) {
        if (signalScore >= 75) {
            return 9;
        }
        if (signalScore >= 50) {
            return 7;
        }
        if (signalScore >= 25) {
            return 5;
        }
        if (signalScore >= 12) {
            return 4;
        }
        return 0;
    }

    private static String buildDebugSummary(
            @Nullable RxSessionSnapshot sessionSnapshot,
            @Nullable SpectrumSnapshotData spectrumSnapshot,
            int readabilityScore,
            int signalScore,
            boolean hasEvidence
    ) {
        String normalized = sessionSnapshot == null ? "" : trimToEmpty(sessionSnapshot.normalizedText());
        String callsign = sessionSnapshot == null ? "" : trimToEmpty(sessionSnapshot.primaryCallsignCandidate());
        String tone = spectrumSnapshot == null
                ? "-"
                : String.format(Locale.US, "%.1f", spectrumSnapshot.sqlToneRmsAmplitude());
        String frame = spectrumSnapshot == null
                ? "-"
                : String.format(Locale.US, "%.1f", spectrumSnapshot.sqlFrameRmsAmplitude());
        return "evidence=" + hasEvidence
                + " read=" + readabilityScore
                + " sig=" + signalScore
                + " capture=" + (sessionSnapshot != null && sessionSnapshot.captureActive())
                + " clip=" + (sessionSnapshot != null && sessionSnapshot.inputLevelClipping())
                + " hot=" + (sessionSnapshot != null && sessionSnapshot.inputLevelHot())
                + " tone=" + tone
                + " frame=" + frame
                + " norm=" + normalized
                + " call=" + callsign;
    }

    private static boolean containsAny(String text, String... fragments) {
        if (text == null || text.isEmpty() || fragments == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (fragment != null && !fragment.isEmpty() && text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static int countCharacter(String text, char target) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                count += 1;
            }
        }
        return count;
    }

    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public enum Source {
        NONE,
        LIVE_RX_AUDIO
    }

    public static final class Assessment {
        private final String suggestedRstSent;
        private final int confidence;
        private final int readabilityScore;
        private final int signalScore;
        private final Source source;
        private final String debugSummary;

        private Assessment(
                @Nullable String suggestedRstSent,
                int confidence,
                int readabilityScore,
                int signalScore,
                Source source,
                String debugSummary
        ) {
            this.suggestedRstSent = suggestedRstSent;
            this.confidence = confidence;
            this.readabilityScore = readabilityScore;
            this.signalScore = signalScore;
            this.source = source == null ? Source.NONE : source;
            this.debugSummary = debugSummary == null ? "" : debugSummary;
        }

        public static Assessment none(int readabilityScore, int signalScore, String debugSummary) {
            return new Assessment(null, 0, readabilityScore, signalScore, Source.NONE, debugSummary);
        }

        @Nullable
        public String suggestedRstSent() {
            return suggestedRstSent;
        }

        public boolean hasSuggestion() {
            return suggestedRstSent != null && !suggestedRstSent.trim().isEmpty();
        }

        public int confidence() {
            return confidence;
        }

        public int readabilityScore() {
            return readabilityScore;
        }

        public int signalScore() {
            return signalScore;
        }

        public Source source() {
            return source;
        }

        public String debugSummary() {
            return debugSummary;
        }
    }
}
