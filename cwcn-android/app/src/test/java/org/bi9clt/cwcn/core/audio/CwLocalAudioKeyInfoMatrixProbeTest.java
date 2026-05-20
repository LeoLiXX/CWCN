package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public final class CwLocalAudioKeyInfoMatrixProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final Pattern RECORDING_SUFFIX_PATTERN = Pattern.compile("\\((\\d+)\\)$");
    private static final Map<String, CasePolicy> CASES = buildCases();

    @Test
    public void printKeyInfoFirstEvaluationForAllLocalAudioCases() throws Exception {
        List<CaseEvaluation> evaluations = new ArrayList<>();
        for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
            evaluations.add(evaluateWavFile(wavFile));
        }

        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (Files.isRegularFile(captureWav)) {
            evaluations.add(0, evaluateFrames("capture.wav", loadNormalizedFrames(captureWav)));
        }

        System.out.println("==== key-info-first live-like matrix ====");
        for (CaseEvaluation evaluation : evaluations) {
            System.out.println(evaluation.renderCompactLine());
        }

        System.out.println();
        System.out.println("==== grouped by practical status ====");
        printBucket("strong", evaluations, PracticalStatus.STRONG);
        printBucket("usable", evaluations, PracticalStatus.USABLE);
        printBucket("marginal", evaluations, PracticalStatus.MARGINAL);
        printBucket("opening-only", evaluations, PracticalStatus.OPENING_ONLY);
        printBucket("poor", evaluations, PracticalStatus.POOR);

        System.out.println();
        System.out.println("==== alternative preference on key info ====");
        printAlternativeBucket("fixed-better", evaluations, AlternativePreference.FIXED_BETTER);
        printAlternativeBucket("live-better", evaluations, AlternativePreference.LIVE_BETTER);
        printAlternativeBucket("neutral", evaluations, AlternativePreference.NEUTRAL);

        System.out.println();
        System.out.println("==== per-case detail ====");
        for (CaseEvaluation evaluation : evaluations) {
            System.out.println(evaluation.renderDetailBlock());
        }

        assertTrue("Expected at least one key-info evaluation", !evaluations.isEmpty());
    }

    private static CaseEvaluation evaluateWavFile(Path wavFile) throws Exception {
        String alias = caseAlias(wavFile.getFileName().toString());
        return evaluateFrames(alias, loadNormalizedFrames(wavFile));
    }

    private static List<AudioFrame> loadNormalizedFrames(Path wavFile) throws Exception {
        return LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
    }

    private static CaseEvaluation evaluateFrames(String alias, List<AudioFrame> frames) {
        CasePolicy policy = CASES.get(alias);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult liveDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        alias + "-live",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fixedDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        alias + "-fixed",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        String liveText = sanitize(liveDetailed.probeResult().decodedText());
        String fixedText = sanitize(fixedDetailed.probeResult().decodedText());
        double liveFullRecall = policy == null ? -1.0d : charRecall(policy.expectedText, liveText);
        double fixedFullRecall = policy == null ? -1.0d : charRecall(policy.expectedText, fixedText);

        TokenSummary livePrimary = scoreTokens(policy == null ? null : policy.primaryTokens, liveText);
        TokenSummary liveSecondary = scoreTokens(policy == null ? null : policy.secondaryTokens, liveText);
        TokenSummary fixedPrimary = scoreTokens(policy == null ? null : policy.primaryTokens, fixedText);
        TokenSummary fixedSecondary = scoreTokens(policy == null ? null : policy.secondaryTokens, fixedText);

        double livePracticalScore = practicalBlend(livePrimary.coverage, liveSecondary.coverage);
        double fixedPracticalScore = practicalBlend(fixedPrimary.coverage, fixedSecondary.coverage);
        PracticalStatus status = classifyPractical(livePrimary.coverage, liveSecondary.coverage);
        AlternativePreference alternativePreference = classifyAlternative(
                livePrimary.coverage,
                livePracticalScore,
                fixedPrimary.coverage,
                fixedPracticalScore
        );

        return new CaseEvaluation(
                alias,
                policy,
                status,
                alternativePreference,
                liveText,
                fixedText,
                liveFullRecall,
                fixedFullRecall,
                livePrimary,
                liveSecondary,
                fixedPrimary,
                fixedSecondary,
                livePracticalScore,
                fixedPracticalScore
        );
    }

    private static void printBucket(
            String label,
            List<CaseEvaluation> evaluations,
            PracticalStatus targetStatus
    ) {
        List<String> aliases = new ArrayList<>();
        for (CaseEvaluation evaluation : evaluations) {
            if (evaluation.status == targetStatus) {
                aliases.add(evaluation.alias);
            }
        }
        System.out.println(label + ": " + (aliases.isEmpty() ? "(none)" : String.join(", ", aliases)));
    }

    private static void printAlternativeBucket(
            String label,
            List<CaseEvaluation> evaluations,
            AlternativePreference targetPreference
    ) {
        List<String> aliases = new ArrayList<>();
        for (CaseEvaluation evaluation : evaluations) {
            if (evaluation.alternativePreference == targetPreference) {
                aliases.add(evaluation.alias);
            }
        }
        System.out.println(label + ": " + (aliases.isEmpty() ? "(none)" : String.join(", ", aliases)));
    }

    private static PracticalStatus classifyPractical(double primaryScore, double secondaryScore) {
        if (primaryScore >= 0.85d && practicalBlend(primaryScore, secondaryScore) >= 0.80d) {
            return PracticalStatus.STRONG;
        }
        if (primaryScore >= 0.70d) {
            return PracticalStatus.USABLE;
        }
        if (primaryScore >= 0.50d) {
            return PracticalStatus.MARGINAL;
        }
        if (secondaryScore >= 0.60d) {
            return PracticalStatus.OPENING_ONLY;
        }
        return PracticalStatus.POOR;
    }

    private static AlternativePreference classifyAlternative(
            double livePrimaryScore,
            double livePracticalScore,
            double fixedPrimaryScore,
            double fixedPracticalScore
    ) {
        if (fixedPrimaryScore >= (livePrimaryScore + 0.10d)
                && fixedPracticalScore >= (livePracticalScore + 0.10d)) {
            return AlternativePreference.FIXED_BETTER;
        }
        if (livePrimaryScore >= (fixedPrimaryScore + 0.10d)
                && livePracticalScore >= (fixedPracticalScore + 0.10d)) {
            return AlternativePreference.LIVE_BETTER;
        }
        return AlternativePreference.NEUTRAL;
    }

    private static double practicalBlend(double primaryScore, double secondaryScore) {
        if (primaryScore < 0.0d) {
            return -1.0d;
        }
        if (secondaryScore < 0.0d) {
            return primaryScore;
        }
        return primaryScore * 0.85d + secondaryScore * 0.15d;
    }

    private static TokenSummary scoreTokens(List<WeightedToken> tokens, String actualText) {
        if (tokens == null || tokens.isEmpty()) {
            return new TokenSummary(-1.0d, new ArrayList<>());
        }
        double totalWeight = 0.0d;
        double weightedScore = 0.0d;
        ArrayList<TokenMatch> matches = new ArrayList<>();
        for (WeightedToken token : tokens) {
            TokenMatch match = scoreToken(token, actualText);
            matches.add(match);
            totalWeight += token.weight;
            weightedScore += match.score * token.weight;
        }
        return new TokenSummary(weightedScore / totalWeight, matches);
    }

    private static TokenMatch scoreToken(WeightedToken token, String actualText) {
        String expected = canonicalize(token.token);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return new TokenMatch(token.token, token.weight, 1.0d, TokenState.HIT);
        }
        if (actual.isEmpty()) {
            return new TokenMatch(token.token, token.weight, 0.0d, TokenState.MISS);
        }
        if (actual.contains(expected)) {
            return new TokenMatch(token.token, token.weight, 1.0d, TokenState.HIT);
        }

        int slack = Math.max(1, Math.min(3, expected.length() / 3));
        int minWindowLength = Math.max(1, expected.length() - slack);
        int maxWindowLength = Math.min(actual.length(), expected.length() + slack);
        double bestScore = 0.0d;
        for (int start = 0; start < actual.length(); start++) {
            for (int windowLength = minWindowLength;
                 windowLength <= maxWindowLength && (start + windowLength) <= actual.length();
                 windowLength++) {
                String candidate = actual.substring(start, start + windowLength);
                double score = longestCommonSubsequenceLength(expected, candidate)
                        / (double) expected.length();
                if (score > bestScore) {
                    bestScore = score;
                }
            }
        }
        return new TokenMatch(token.token, token.weight, bestScore, classifyTokenState(bestScore));
    }

    private static TokenState classifyTokenState(double score) {
        if (score >= 0.80d) {
            return TokenState.HIT;
        }
        if (score >= 0.55d) {
            return TokenState.PARTIAL;
        }
        return TokenState.MISS;
    }

    private static String caseAlias(String fileName) {
        String trimmed = fileName == null ? "" : fileName.trim();
        String stem = trimmed.toLowerCase(Locale.US).endsWith(".wav")
                ? trimmed.substring(0, trimmed.length() - 4)
                : trimmed;
        if ("capture".equalsIgnoreCase(stem)) {
            return "capture.wav";
        }
        Matcher matcher = RECORDING_SUFFIX_PATTERN.matcher(stem);
        if (matcher.find()) {
            return "recording(" + matcher.group(1) + ")";
        }
        if (containsNonAscii(stem)) {
            return "recording";
        }
        return stem;
    }

    private static boolean containsNonAscii(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 127) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int lcs = longestCommonSubsequenceLength(expected, actual);
        return lcs / (double) expected.length();
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            char leftChar = left.charAt(leftIndex - 1);
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (leftChar == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                } else {
                    current[rightIndex] = Math.max(previous[rightIndex], current[rightIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static Map<String, CasePolicy> buildCases() {
        LinkedHashMap<String, CasePolicy> cases = new LinkedHashMap<>();
        cases.put(
                "capture.wav",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                                + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                                + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.",
                        "three natural turns",
                        "baseline live-like capture; key fields should all survive",
                        tokens(
                                token("DEBI9CXC", 4),
                                token("700", 2),
                                token("24WPM", 2),
                                token("PSEK", 2)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "20260427_222505",
                new CasePolicy(
                        "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K",
                        "known weak similarity/collision sample",
                        "callsign pair matters more than repeated opening rhythm",
                        tokens(
                                token("BI9CLT", 4),
                                token("DEBI9CMS", 4),
                                token("PSEK", 2)
                        ),
                        tokens()
                )
        );
        cases.put(
                "20260427_224524",
                new CasePolicy(
                        "CP CP DE B6 B6 LZ HOT LZ HOT KN",
                        "known noisy short sample",
                        "practical tail is DE / B6 / LZ HOT / KN",
                        tokens(
                                token("DEB6", 4),
                                token("LZHOT", 4),
                                token("KN", 2)
                        ),
                        tokens(
                                token("CPCP", 1)
                        )
                )
        );
        cases.put(
                "recording",
                new CasePolicy(
                        "QRZ? DE BI3TUK KN.",
                        "short QRZ sample",
                        "opening QRZ is secondary; priority is DE / BI3TUK / KN",
                        tokens(
                                token("DE", 1),
                                token("BI3TUK", 4),
                                token("KN", 2)
                        ),
                        tokens(
                                token("QRZ", 1)
                        )
                )
        );
        cases.put(
                "recording(2)",
                new CasePolicy(
                        "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.",
                        "two-turn CQ DX sample",
                        "practical content is the station plus PAGE tail",
                        tokens(
                                token("DEJV3VV", 4),
                                token("PAGEK", 3)
                        ),
                        tokens(
                                token("CQDX", 1)
                        )
                )
        );
        cases.put(
                "recording(3)",
                new CasePolicy(
                        "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.",
                        "similar-callsign exchange",
                        "callsign collision sample; both sides plus report must survive",
                        tokens(
                                token("BI9CMS", 3),
                                token("DEBI9CLT", 4),
                                token("599", 2),
                                token("5NNBK", 2)
                        ),
                        tokens()
                )
        );
        cases.put(
                "recording(4)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K.",
                        "repeated CQ loop",
                        "repeat count matters less than keeping DE / callsign / PSE K stable",
                        tokens(
                                token("DEBI9CLT", 4),
                                token("PSEK", 3)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(5)",
                new CasePolicy(
                        "Q DE BI9",
                        "very short fragment",
                        "fragment case; keep DE BI9 visible even if leading Q is shaky",
                        tokens(
                                token("DEBI9", 4)
                        ),
                        tokens(
                                token("Q", 1)
                        )
                )
        );
        cases.put(
                "recording(6)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K.",
                        "clean CQ reference",
                        "reference clean case; DE / callsign / PSE K should be exact",
                        tokens(
                                token("DEBI9CLT", 4),
                                token("PSEK", 3)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(7)",
                new CasePolicy(
                        "QRZ? DE BI3TUK KN.",
                        "short QRZ reference",
                        "do not sacrifice DE / BI3TUK / KN just to preserve QRZ opening",
                        tokens(
                                token("DE", 1),
                                token("BI3TUK", 4),
                                token("KN", 2)
                        ),
                        tokens(
                                token("QRZ", 1)
                        )
                )
        );
        cases.put(
                "recording(8)",
                new CasePolicy(
                        "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                        "fast noisy long QSO",
                        "long noisy QSO; preserve both callsigns, reports, QTH/NAME, and closing",
                        longQsoPrimaryTokens(),
                        longQsoSecondaryTokens()
                )
        );
        cases.put(
                "recording(9)",
                new CasePolicy(
                        "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                        "long QSO variant",
                        "same practical target as recording(8)",
                        longQsoPrimaryTokens(),
                        longQsoSecondaryTokens()
                )
        );
        cases.put(
                "recording(10)",
                new CasePolicy(
                        "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                        "long QSO variant",
                        "QSB-like long QSO; key info still outranks opening neatness",
                        longQsoPrimaryTokens(),
                        longQsoSecondaryTokens()
                )
        );
        cases.put(
                "recording(11)",
                new CasePolicy(
                        "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                        "long QSO strong variant",
                        "high-WPM long QSO; practical readability matters more than verbatim perfection",
                        longQsoPrimaryTokens(),
                        longQsoSecondaryTokens()
                )
        );
        cases.put(
                "recording(12)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.",
                        "single CQ with 700",
                        "QSB-like CQ; callsign visibility should outweigh tail details like 700 / PSE K",
                        tokens(
                                token("DEBI9CMS", 6),
                                token("BI9CMS", 4),
                                token("700", 1),
                                token("PSEK", 1)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(13)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT IN 600 PSE K.",
                        "single CQ with 600",
                        "single CQ baseline; practical core is DE / callsign / 600 / PSE K",
                        tokens(
                                token("DEBI9CLT", 4),
                                token("600", 2),
                                token("PSEK", 2)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(14)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K.",
                        "single CQ with 800",
                        "QSB-like single CQ; practical core is DE / callsign / 800 / PSE K",
                        tokens(
                                token("DEBI9CXC", 4),
                                token("800", 2),
                                token("PSEK", 2)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(15)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K.",
                        "single CQ with 800 / 24WPM",
                        "practical target is DE / callsign / 800 / 24WPM / PSE K",
                        tokens(
                                token("DEBI9CXC", 4),
                                token("800", 2),
                                token("24WPM", 2),
                                token("PSEK", 2)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        cases.put(
                "recording(16)",
                new CasePolicy(
                        "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.",
                        "single CQ with 700 / 24WPM",
                        "reference single CQ with explicit WPM",
                        tokens(
                                token("DEBI9CXC", 4),
                                token("700", 2),
                                token("24WPM", 2),
                                token("PSEK", 2)
                        ),
                        tokens(
                                token("CQCQCQ", 1)
                        )
                )
        );
        return cases;
    }

    private static List<WeightedToken> longQsoPrimaryTokens() {
        return tokens(
                token("BG1XXX", 3),
                token("JA1ABC", 3),
                token("RST599", 2),
                token("QTHBEIJING", 2),
                token("NAMELEO", 2),
                token("RST579", 2),
                token("QTHTOKYO", 2),
                token("NAMEKEN", 2),
                token("73", 1),
                token("SK", 1)
        );
    }

    private static List<WeightedToken> longQsoSecondaryTokens() {
        return tokens(
                token("CQCQ", 1),
                token("DE", 1),
                token("TNXQSO", 1)
        );
    }

    private static List<WeightedToken> tokens(WeightedToken... tokens) {
        ArrayList<WeightedToken> list = new ArrayList<>();
        if (tokens != null) {
            java.util.Collections.addAll(list, tokens);
        }
        return list;
    }

    private static WeightedToken token(String token, int weight) {
        return new WeightedToken(token, weight);
    }

    private enum PracticalStatus {
        STRONG,
        USABLE,
        MARGINAL,
        OPENING_ONLY,
        POOR
    }

    private enum AlternativePreference {
        FIXED_BETTER,
        LIVE_BETTER,
        NEUTRAL
    }

    private enum TokenState {
        HIT,
        PARTIAL,
        MISS
    }

    private static final class WeightedToken {
        private final String token;
        private final int weight;

        private WeightedToken(String token, int weight) {
            this.token = token;
            this.weight = weight;
        }
    }

    private static final class CasePolicy {
        private final String expectedText;
        private final String note;
        private final String focusNote;
        private final List<WeightedToken> primaryTokens;
        private final List<WeightedToken> secondaryTokens;

        private CasePolicy(
                String expectedText,
                String note,
                String focusNote,
                List<WeightedToken> primaryTokens,
                List<WeightedToken> secondaryTokens
        ) {
            this.expectedText = expectedText;
            this.note = note;
            this.focusNote = focusNote;
            this.primaryTokens = primaryTokens;
            this.secondaryTokens = secondaryTokens;
        }
    }

    private static final class TokenMatch {
        private final String token;
        private final int weight;
        private final double score;
        private final TokenState state;

        private TokenMatch(String token, int weight, double score, TokenState state) {
            this.token = token;
            this.weight = weight;
            this.score = score;
            this.state = state;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "%s[%s %.2f w=%d]",
                    token,
                    state.name().toLowerCase(Locale.US),
                    score,
                    weight
            );
        }
    }

    private static final class TokenSummary {
        private final double coverage;
        private final List<TokenMatch> matches;

        private TokenSummary(double coverage, List<TokenMatch> matches) {
            this.coverage = coverage;
            this.matches = matches;
        }

        private String renderMatches() {
            if (matches == null || matches.isEmpty()) {
                return "(none)";
            }
            ArrayList<String> parts = new ArrayList<>();
            for (TokenMatch match : matches) {
                parts.add(match.render());
            }
            return String.join(", ", parts);
        }
    }

    private static final class CaseEvaluation {
        private final String alias;
        private final CasePolicy policy;
        private final PracticalStatus status;
        private final AlternativePreference alternativePreference;
        private final String liveText;
        private final String fixedText;
        private final double liveFullRecall;
        private final double fixedFullRecall;
        private final TokenSummary livePrimary;
        private final TokenSummary liveSecondary;
        private final TokenSummary fixedPrimary;
        private final TokenSummary fixedSecondary;
        private final double livePracticalScore;
        private final double fixedPracticalScore;

        private CaseEvaluation(
                String alias,
                CasePolicy policy,
                PracticalStatus status,
                AlternativePreference alternativePreference,
                String liveText,
                String fixedText,
                double liveFullRecall,
                double fixedFullRecall,
                TokenSummary livePrimary,
                TokenSummary liveSecondary,
                TokenSummary fixedPrimary,
                TokenSummary fixedSecondary,
                double livePracticalScore,
                double fixedPracticalScore
        ) {
            this.alias = alias;
            this.policy = policy;
            this.status = status;
            this.alternativePreference = alternativePreference;
            this.liveText = liveText;
            this.fixedText = fixedText;
            this.liveFullRecall = liveFullRecall;
            this.fixedFullRecall = fixedFullRecall;
            this.livePrimary = livePrimary;
            this.liveSecondary = liveSecondary;
            this.fixedPrimary = fixedPrimary;
            this.fixedSecondary = fixedSecondary;
            this.livePracticalScore = livePracticalScore;
            this.fixedPracticalScore = fixedPracticalScore;
        }

        private String renderCompactLine() {
            return String.format(
                    Locale.US,
                    "%-16s status=%-12s full=%-5s primary=%-5s secondary=%-5s practical=%-5s alt=%-12s note=%s",
                    alias,
                    status.name(),
                    renderScore(liveFullRecall),
                    renderScore(livePrimary.coverage),
                    renderScore(liveSecondary.coverage),
                    renderScore(livePracticalScore),
                    alternativePreference.name(),
                    policy == null ? "-" : policy.note
            );
        }

        private String renderDetailBlock() {
            StringBuilder builder = new StringBuilder();
            builder.append("---- ").append(alias).append(" [").append(status.name()).append("] ----\n");
            if (policy != null) {
                builder.append("note=").append(policy.note).append('\n');
                builder.append("focus=").append(policy.focusNote).append('\n');
                builder.append("expected=").append(sanitize(policy.expectedText)).append('\n');
            }
            builder.append("live=").append(liveText).append('\n');
            builder.append(String.format(
                    Locale.US,
                    "live-metrics: full=%s primary=%s secondary=%s practical=%s\n",
                    renderScore(liveFullRecall),
                    renderScore(livePrimary.coverage),
                    renderScore(liveSecondary.coverage),
                    renderScore(livePracticalScore)
            ));
            builder.append("live-primary: ").append(livePrimary.renderMatches()).append('\n');
            builder.append("live-secondary: ").append(liveSecondary.renderMatches()).append('\n');
            builder.append("fixed=").append(fixedText).append('\n');
            builder.append(String.format(
                    Locale.US,
                    "fixed-metrics: full=%s primary=%s secondary=%s practical=%s\n",
                    renderScore(fixedFullRecall),
                    renderScore(fixedPrimary.coverage),
                    renderScore(fixedSecondary.coverage),
                    renderScore(fixedPracticalScore)
            ));
            builder.append("fixed-primary: ").append(fixedPrimary.renderMatches()).append('\n');
            builder.append("fixed-secondary: ").append(fixedSecondary.renderMatches()).append('\n');
            builder.append("decision=").append(alternativePreference.name()).append('\n');
            return builder.toString();
        }
    }

    private static String renderScore(double value) {
        if (value < 0.0d) {
            return "-";
        }
        return String.format(Locale.US, "%.3f", value);
    }
}
