package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluator;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwCrowdedBandCoverageTest {
    @Test
    public void weakAdjacentClusterCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_weak_adjacent_cluster_cq_700hz", 450, 0.55d, 22, false);
    }

    @Test
    public void noisyBurstyAdjacentClusterCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_noisy_bursty_adjacent_cluster_cq_700hz", 700, 0.20d, 18, true);
    }

    @Test
    public void cochannelUnderlayProxyCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_cochannel_underlay_proxy_cq_700hz", 700, 0.50d, 20, false);
    }

    @Test
    public void humNoiseAdjacentClusterCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_hum_noise_adjacent_cluster_cq_700hz", 700, 0.45d, 20, false);
    }

    @Test
    public void sameToneDualSequenceCurrentlyExposesCochannelBranchAmbiguity() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_same_tone_dual_sequence_target_priority_700hz", 700);
        String summary = renderSummary(bundle);

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertNotEquals(summary, "WRONG", bundle.result.frontEndQualityCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 10000.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 40);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 40);
        assertTrue(summary, bundle.signalSnapshot.maxConsecutiveLockedFrames() >= 8);
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.70d);
        assertTrue(summary, bundle.timingSnapshot.estimatedWpm() <= 12);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 4);
    }

    @Test
    public void sameToneDualSequenceTargetDominantStillNeedsBranchRecovery() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_same_tone_dual_sequence_target_dominant_700hz", 700);
        String summary = renderSummary(bundle);

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertEquals(summary, "GOOD", bundle.result.frontEndQualityCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 10000.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 20);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 20);
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.70d);
        assertTrue(summary, bundle.timingSnapshot.estimatedWpm() <= 20);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 4);
    }

    @Test
    public void sameToneDualSequenceInterfererDominantNowExposesForeignFingerprintPressure() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_same_tone_dual_sequence_interferer_dominant_700hz", 700);
        String summary = renderSummary(bundle);
        String decodedText = sanitize(bundle.decoderSnapshot.decodedText());
        int targetFingerprint = countSubstring(decodedText, "BI9CLT") + countSubstring(decodedText, "CQ");
        int interfererFingerprint = countSubstring(decodedText, "JA1ABC") + countSubstring(decodedText, "VVV");

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 10000.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 20);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 20);
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.70d);
        assertTrue(summary, interfererFingerprint >= 1 || bundle.result.textTokenRecall() <= 0.20d);
        assertTrue(summary, interfererFingerprint >= targetFingerprint || bundle.result.textTokenRecall() <= 0.30d);
    }

    @Test
    public void clearDifferentToneReplyEventuallyHandsOffToSecondOperatorTone() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_two_tone_reply_clear_handoff_680_760hz", 680);
        String summary = renderSummary(bundle);
        String decodedText = sanitize(bundle.decoderSnapshot.decodedText());
        int firstOperatorFingerprint = countSubstring(decodedText, "BI9CLT")
                + countSubstring(decodedText, "CQ")
                + countSubstring(decodedText, "PSE");
        int secondOperatorFingerprint = countSubstring(decodedText, "JA1ABC")
                + countSubstring(decodedText, "TNX")
                + countSubstring(decodedText, "CALL")
                + countSubstring(decodedText, "BK");

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertNotEquals(summary, "SEARCH_FALLBACK", bundle.signalSnapshot.finalAdoptedSource());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 9000.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 16);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 16);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.60d);
        assertTrue(summary, bundle.signalSnapshot.maxConsecutiveLockedFrames() >= 10);
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 760) <= 30);
        assertTrue(summary, firstOperatorFingerprint >= 1 || bundle.result.textTokenRecall() >= 0.18d);
        assertTrue(summary, secondOperatorFingerprint >= 1 || bundle.signalSnapshot.targetToneFrequencyHz() >= 730);
    }

    @Test
    public void shortGapDifferentToneReplyStaysNearActiveOperatorPairWithoutWildThrash() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_two_tone_reply_short_gap_680_760hz", 680);
        String summary = renderSummary(bundle);
        String decodedText = sanitize(bundle.decoderSnapshot.decodedText());
        int firstOperatorFingerprint = countSubstring(decodedText, "BI9CLT")
                + countSubstring(decodedText, "CQ")
                + countSubstring(decodedText, "PSE");
        int secondOperatorFingerprint = countSubstring(decodedText, "JA1ABC")
                + countSubstring(decodedText, "TNX")
                + countSubstring(decodedText, "CALL")
                + countSubstring(decodedText, "BK")
                + countSubstring(decodedText, "GM");
        int distanceToEitherToneHz = Math.min(
                Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 680),
                Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 760)
        );

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 9000.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 16);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 16);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.52d);
        assertTrue(summary, bundle.signalSnapshot.maxConsecutiveLockedFrames() >= 8);
        assertTrue(summary, distanceToEitherToneHz <= 35);
        assertTrue(summary, firstOperatorFingerprint >= 1 || secondOperatorFingerprint >= 1);
        assertTrue(
                summary,
                secondOperatorFingerprint >= 1
                        || bundle.signalSnapshot.targetToneFrequencyHz() >= 730
                        || bundle.decoderSnapshot.totalCharacters() >= 6
        );
    }

    @Test
    public void leftAdjacentOccupancyCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_left_adjacent_occupancy_cq_700hz", 700, 0.50d, 22, false);
    }

    @Test
    public void rightAdjacentOccupancyCqRemainsBasicallyDiscernible() {
        assertDiscernible("user_right_adjacent_occupancy_cq_700hz", 700, 0.50d, 22, false);
    }

    @Test
    public void asymmetricAdjacentOccupancyRemainsObservableWithoutFullCollapse() {
        OfflineEvalBundle leftBundle = evaluateOfflineBundle("user_left_adjacent_occupancy_cq_700hz", 700);
        OfflineEvalBundle rightBundle = evaluateOfflineBundle("user_right_adjacent_occupancy_cq_700hz", 700);

        String summary = "left\n" + renderSummary(leftBundle) + "\nright\n" + renderSummary(rightBundle);
        double recallGap = Math.abs(
                leftBundle.result.textTokenRecall() - rightBundle.result.textTokenRecall()
        );
        int trackedGap = Math.abs(
                leftBundle.signalSnapshot.targetToneFrequencyHz()
                        - rightBundle.signalSnapshot.targetToneFrequencyHz()
        );

        assertTrue(summary, leftBundle.result.textTokenRecall() >= 0.45d);
        assertTrue(summary, rightBundle.result.textTokenRecall() >= 0.45d);
        assertTrue(summary, recallGap <= 0.30d);
        assertTrue(summary, leftBundle.signalSnapshot.targetToneFrequencyHz() <= 710);
        assertTrue(summary, rightBundle.signalSnapshot.targetToneFrequencyHz() >= 730);
        assertTrue(summary, trackedGap <= 140);
    }

    @Test
    public void leftAdjacentOccupancyLongQsoRemainsUsableWithoutSearchCollapse() {
        assertLongQsoDiscernible("user_left_adjacent_occupancy_long_qso_700hz", 700, 0.48d, 80, 0.58d, 0.44d, 0.22d);
    }

    @Test
    public void rightAdjacentOccupancyLongQsoRemainsUsableWithoutSearchCollapse() {
        assertLongQsoDiscernible("user_right_adjacent_occupancy_long_qso_700hz", 700, 0.48d, 80, 0.58d, 0.44d, 0.22d);
    }

    @Test
    public void asymmetricAdjacentOccupancyLongQsoPrefersStableLockOverSearchThrash() {
        OfflineEvalBundle leftBundle = evaluateOfflineBundle("user_left_adjacent_occupancy_long_qso_700hz", 700);
        OfflineEvalBundle rightBundle = evaluateOfflineBundle("user_right_adjacent_occupancy_long_qso_700hz", 700);

        String summary = "left\n" + renderSummary(leftBundle) + "\nright\n" + renderSummary(rightBundle);

        assertNotEquals(summary, "SEARCH_FALLBACK", leftBundle.signalSnapshot.finalAdoptedSource());
        assertNotEquals(summary, "SEARCH_FALLBACK", rightBundle.signalSnapshot.finalAdoptedSource());
        assertTrue(summary, leftBundle.signalSnapshot.lockedFrameRatio() >= 0.72d);
        assertTrue(summary, rightBundle.signalSnapshot.lockedFrameRatio() >= 0.72d);
        assertTrue(summary, leftBundle.signalSnapshot.maxConsecutiveLockedFrames() >= 18);
        assertTrue(summary, rightBundle.signalSnapshot.maxConsecutiveLockedFrames() >= 18);
        assertTrue(summary, leftBundle.signalSnapshot.recentLockedFrameRatio() >= 0.45d);
        assertTrue(summary, rightBundle.signalSnapshot.recentLockedFrameRatio() >= 0.45d);
    }

    private void assertDiscernible(
            String scenarioId,
            int preferredToneHz,
            double minRecall,
            int minCharacters,
            boolean allowSignalBucket
    ) {
        OfflineEvalBundle bundle = evaluateOfflineBundle(scenarioId, preferredToneHz);
        String summary = renderSummary(bundle);
        String decodedText = sanitize(bundle.decoderSnapshot.decodedText());

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        if (!allowSignalBucket) {
            assertNotEquals(summary, "SIG", bundle.result.likelyBottleneckCode());
        }
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 10);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 10);
        assertTrue(summary, bundle.result.textTokenRecall() >= minRecall);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= minCharacters);
        assertTrue(summary, decodedText.contains("CQ"));
        assertTrue(summary, containsCallsignCore(decodedText));
    }

    private void assertLongQsoDiscernible(
            String scenarioId,
            int preferredToneHz,
            double minRecall,
            int minCharacters,
            double minFirstHalfRecall,
            double minSecondHalfRecall,
            double maxSecondHalfDrop
    ) {
        OfflineEvalBundle bundle = evaluateOfflineBundle(scenarioId, preferredToneHz);
        String summary = renderSummary(bundle);
        String decodedText = sanitize(bundle.decoderSnapshot.decodedText());
        double firstHalfRecall = tokenWindowRecall(
                bundle.scenario.expectedNormalizedText(),
                decodedText,
                0.0d,
                0.5d
        );
        double secondHalfRecall = tokenWindowRecall(
                bundle.scenario.expectedNormalizedText(),
                decodedText,
                0.5d,
                1.0d
        );

        assertNotNull(summary, bundle.result);
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", bundle.result.likelyBottleneckCode());
        assertNotEquals(summary, "SEARCH_FALLBACK", bundle.signalSnapshot.finalAdoptedSource());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 80);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 80);
        assertTrue(summary, bundle.signalSnapshot.lockedFrameRatio() >= 0.72d);
        assertTrue(summary, bundle.signalSnapshot.maxConsecutiveLockedFrames() >= 18);
        assertTrue(summary, bundle.signalSnapshot.recentLockedFrameRatio() >= 0.45d);
        assertTrue(summary, bundle.result.textTokenRecall() >= minRecall);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= minCharacters);
        assertTrue(summary + "\nfirstHalfRecall=" + firstHalfRecall, firstHalfRecall >= minFirstHalfRecall);
        assertTrue(summary + "\nsecondHalfRecall=" + secondHalfRecall, secondHalfRecall >= minSecondHalfRecall);
        assertTrue(
                summary + "\nfirstHalfRecall=" + firstHalfRecall + "\nsecondHalfRecall=" + secondHalfRecall,
                secondHalfRecall >= firstHalfRecall - maxSecondHalfDrop
        );
        assertTrue(summary, countSubstring(decodedText, "BG1XXX") >= 3);
        assertTrue(summary, countSubstring(decodedText, "JA1ABC") >= 2);
        assertTrue(summary, decodedText.contains("TOKYO"));
        assertTrue(summary, decodedText.contains("73"));
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId, int preferredToneHz) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailedProbeResult =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        preferredToneHz,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        return new OfflineEvalBundle(
                scenario,
                CwFixtureEvaluator.evaluate(
                        scenario,
                        detailedProbeResult.probeResult().interpreterSnapshot(),
                        detailedProbeResult.probeResult().qsoDraftSnapshot(),
                        detailedProbeResult.probeResult().signalSnapshot(),
                        true
                ),
                detailedProbeResult.probeResult().signalSnapshot(),
                detailedProbeResult.probeResult().timingSnapshot(),
                detailedProbeResult.probeResult().decoderSnapshot()
        );
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderSummary(OfflineEvalBundle bundle) {
        return bundle.scenario.id()
                + "\n" + bundle.result.renderSummary()
                + "\ntracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + " AQ=" + bundle.signalSnapshot.acquisitionWinnerFrequencyHz()
                + " AD=" + bundle.signalSnapshot.finalAdoptedFrequencyHz()
                + " ADsrc=" + bundle.signalSnapshot.finalAdoptedSource()
                + " lockCov=" + bundle.signalSnapshot.lockedFrameRatio()
                + " peakToneRms=" + bundle.signalSnapshot.peakToneRmsAmplitude()
                + "\nwpm=" + bundle.timingSnapshot.estimatedWpm()
                + " chars=" + bundle.decoderSnapshot.totalCharacters()
                + " recall=" + bundle.result.textTokenRecall()
                + " text=" + sanitize(bundle.decoderSnapshot.decodedText());
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace('\u25A1', '?');
    }

    private boolean containsCallsignCore(String decodedText) {
        if (decodedText == null || decodedText.isEmpty()) {
            return false;
        }
        return decodedText.contains("BI9CLT")
                || decodedText.contains("I9CLT")
                || decodedText.contains("9CLT")
                || decodedText.contains("CLT");
    }

    private int countSubstring(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int searchFrom = 0;
        while (searchFrom >= 0 && searchFrom < text.length()) {
            int foundAt = text.indexOf(fragment, searchFrom);
            if (foundAt < 0) {
                break;
            }
            count += 1;
            searchFrom = foundAt + fragment.length();
        }
        return count;
    }

    private double tokenWindowRecall(String expectedText, String actualText, double startFraction, double endFraction) {
        List<String> expectedTokens = normalizedTokenList(expectedText);
        List<String> actualTokens = normalizedTokenList(actualText);
        if (expectedTokens.isEmpty()) {
            return actualTokens.isEmpty() ? 1.0d : 0.0d;
        }

        int startIndex = Math.max(0, Math.min(expectedTokens.size(), (int) Math.floor(expectedTokens.size() * startFraction)));
        int endIndex = Math.max(startIndex, Math.min(expectedTokens.size(), (int) Math.ceil(expectedTokens.size() * endFraction)));
        List<String> window = expectedTokens.subList(startIndex, endIndex);
        if (window.isEmpty()) {
            return 1.0d;
        }
        int lcs = tokenLcsLength(window, actualTokens);
        return lcs / (double) window.size();
    }

    private List<String> normalizedTokenList(String text) {
        if (text == null || text.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String upper = text.toUpperCase(java.util.Locale.US).replace('\u25A1', '?');
        StringBuilder normalized = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                normalized.append(ch);
            } else {
                normalized.append(' ');
            }
        }
        String trimmed = normalized.toString().trim();
        if (trimmed.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList(trimmed.split("\\s+"));
    }

    private int tokenLcsLength(List<String> expectedTokens, List<String> actualTokens) {
        int[] previous = new int[actualTokens.size() + 1];
        int[] current = new int[actualTokens.size() + 1];
        for (int expectedIndex = 1; expectedIndex <= expectedTokens.size(); expectedIndex++) {
            String expectedToken = expectedTokens.get(expectedIndex - 1);
            for (int actualIndex = 1; actualIndex <= actualTokens.size(); actualIndex++) {
                if (expectedToken.equals(actualTokens.get(actualIndex - 1))) {
                    current[actualIndex] = previous[actualIndex - 1] + 1;
                } else {
                    current[actualIndex] = Math.max(previous[actualIndex], current[actualIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[actualTokens.size()];
    }

    private static final class OfflineEvalBundle {
        private final CwFixtureScenario scenario;
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;

        private OfflineEvalBundle(
                CwFixtureScenario scenario,
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot
        ) {
            this.scenario = scenario;
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
        }
    }
}
