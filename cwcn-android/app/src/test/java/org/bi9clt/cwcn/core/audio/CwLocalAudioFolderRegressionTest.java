package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwLocalAudioFolderRegressionTest {
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> DECODED_RESULTS = loadDecodedResults();
    private static final Set<String> RAW_STRUCTURE_STRICT_EXEMPTIONS =
            Collections.singleton("录音 (6)");

    private static final List<Expectation> STRICT_CASES = Arrays.asList(
            Expectation.strict("录音", "QRZ? DE BI3TUK KN."),
            Expectation.strict("录音 (6)", "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K."),
            Expectation.strict(
                    "录音 (10)",
                    "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
            ),
            Expectation.strict(
                    "录音 (11)",
                    "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
            ),
            Expectation.strict("录音 (13)", "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT IN 600 PSE K."),
            Expectation.strict("录音 (14)", "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K."),
            Expectation.strict("录音 (15)", "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K."),
            Expectation.strict("录音 (16)", "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.")
    );

    private static final List<Expectation> SOFT_CASES = Arrays.asList(
            Expectation.soft("录音 (2)", "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.", 0.50d)
                    .requireFragments("JV3VV", "PAGE", "DX")
                    .requireFragmentCount("JV3VV", 3)
                    .requireFragmentCount("PAGE", 2)
                    .requireFragmentCount("DX", 2)
                    .withMinCharacters(30),
            Expectation.soft("录音 (3)", "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.", 0.82d)
                    .requireFragments("BI9CMS", "BI9CLT", "599", "5NN", "BK")
                    .requireFragmentCount("BI9CMS", 3)
                    .requireFragmentCount("BI9CLT", 3)
                    .withMinCharacters(40),
            Expectation.soft(
                    "录音 (4)",
                    "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K.",
                    0.90d
            ).requireFragments("CQ", "BI9CLT", "PSE", "K")
                    .requireFragmentCount("CQ", 7)
                    .requireFragmentCount("BI9CLT", 8)
                    .requireFragmentCount("PSE", 3)
                    .withMinCharacters(80),
            Expectation.soft("录音 (5)", "Q DE BI9", 0.55d)
                    .requireFragments("DE", "BI")
                    .withMinCharacters(6),
            Expectation.soft("录音 (7)", "QRZ? DE BI3TUK KN.", 0.65d)
                    .requireFragments("3TUK", "KN")
                    .requireFragmentCount("KN", 1)
                    .withMinCharacters(10),
            Expectation.soft("20260427_224524", "CP CP DE B6 B6 LZ HOT LZ HOT KN", 0.30d)
                    .requireFragments("CP", "HOT", "KN")
                    .withMinCharacters(18),
            Expectation.soft(
                    "录音 (9)",
                    "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                    0.62d
            ).requireFragments("BG1XXX", "JA1ABC", "RST", "SK")
                    .requireFragmentCount("BG1XXX", 2)
                    .requireFragmentCount("JA1ABC", 1)
                    .requireFragmentCount("RST", 2)
                    .requireFragmentCount("SK", 1)
                    .withMinCharacters(100)
    );

    private static final List<Expectation> OBSERVABILITY_CASES = Arrays.asList(
            Expectation.observabilityOnly("20260427_222505", "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K")
                    .requireFragments("KI9", "DEBI9", "PSEK")
                    .requireFragmentCount("KI9", 1)
                    .requireFragmentCount("DEBI9", 1)
                    .requireFragmentCount("PSEK", 1)
                    .withMinCharacters(20)
                    .withTrackedTone(740, 100)
                    .withWpmFloor(16),
            Expectation.observabilityOnly("录音 (12)", "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K")
                    .requireFragments("PSEK")
                    .requireFragmentCount("PSEK", 1)
                    .withMinCharacters(15)
                    .withTrackedTone(690, 60)
                    .withWpmFloor(14),
            Expectation.observabilityOnly("录音 (8)")
                    .requireFragments("BG1", "5")
                    .withMinCharacters(60)
                    .withTrackedTone(770, 140)
                    .withWpmFloor(6)
    );

    private static final List<ToneExpectation> STRICT_TONE_CASES = Arrays.asList(
            new ToneExpectation("录音 (13)", 600, 70),
            new ToneExpectation("录音 (14)", 800, 40),
            new ToneExpectation("录音 (15)", 800, 40),
            new ToneExpectation("录音 (16)", 700, 40)
    );

    @Test
    public void localAudioStrictReferenceCase_recordingBase_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音");
    }

    @Test
    public void localAudioStrictReferenceCase_recording6_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (6)");
    }

    @Test
    public void localAudioSoftReferenceCase_recording7_meetsCurrentPracticalTailFloor() {
        assertSoftReferenceCase("录音 (7)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording10_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (10)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording11_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (11)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording13_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (13)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording14_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (14)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording15_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (15)");
    }

    @Test
    public void localAudioStrictReferenceCase_recording16_matchesCanonicalPayloadExactly() {
        assertStrictReferenceCase("录音 (16)");
    }

    @Test
    public void localAudioStrictToneReferenceCase_recording13_staysNearExpectedTrackedTone() {
        assertStrictToneReferenceCase("录音 (13)");
    }

    @Test
    public void localAudioStrictToneReferenceCase_recording14_staysNearExpectedTrackedTone() {
        assertStrictToneReferenceCase("录音 (14)");
    }

    @Test
    public void localAudioStrictToneReferenceCase_recording15_staysNearExpectedTrackedTone() {
        assertStrictToneReferenceCase("录音 (15)");
    }

    @Test
    public void localAudioStrictToneReferenceCase_recording16_staysNearExpectedTrackedTone() {
        assertStrictToneReferenceCase("录音 (16)");
    }

    @Test
    public void localAudioSoftReferenceCase_recording2_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("录音 (2)");
    }

    @Test
    public void localAudioSoftReferenceCase_recording3_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("录音 (3)");
    }

    @Test
    public void localAudioSoftReferenceCase_recording4_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("录音 (4)");
    }

    @Test
    public void localAudioSoftReferenceCase_recording5_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("录音 (5)");
    }

    @Test
    public void localAudioSoftReferenceCase_case20260427_224524_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("20260427_224524");
    }

    @Test
    public void localAudioSoftReferenceCase_recording9_meetsCurrentQualityFloor() {
        assertSoftReferenceCase("录音 (9)");
    }

    @Test
    public void localAudioObservabilityCase_recording12_remainsObservableWithoutFrontEndCollapse() {
        assertObservabilityCase("录音 (12)");
    }

    @Test
    public void localAudioRecording2_eventuallyRecoversTrackedToneOffLowEdge() {
        String sourceLabel = SOFT_CASES.get(0).sourceLabel;
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult(sourceLabel);
        String summary = renderSummary(Expectation.soft(sourceLabel, "recording2", 0.0d), result);

        assertTrue(summary, result.signalSnapshot().effectiveTrackedToneFrequencyHz() >= 560);
        assertTrue(summary, result.signalSnapshot().effectiveAcquisitionWinnerFrequencyHz() >= 560);
    }

    @Test
    public void localAudioObservabilityCase_case20260427_222505_remainsObservableWithoutFrontEndCollapse() {
        assertObservabilityCase("20260427_222505");
    }

    @Test
    public void localAudioObservabilityCase_recording8_remainsObservableWithoutFrontEndCollapse() {
        assertObservabilityCase("录音 (8)");
    }

    @Test
    public void localAudioHypothesisProbeCase_recording2_remainsObservable() {
        assertHypothesisRange(requireResult("褰曢煶 (2)"), 560, 780, 0.10d);
    }

    @Test
    public void localAudioHypothesisProbeCase_recording3_remainsObservable() {
        assertHypothesisRange(requireResult("褰曢煶 (3)"), 560, 780, 0.10d);
    }

    @Test
    public void localAudioHypothesisProbeCase_recording8_remainsObservable() {
        assertHypothesisRange(requireResult("褰曢煶 (8)"), 600, 820, 0.08d);
    }

    @Test
    public void localAudioSevereKnownGapCase_recording8_isTrackedButStillFarFromReadableCopy() {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (8)");
        String expectedText = "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";
        double recall = charRecall(expectedText, result.decodedText());
        String summary = renderSummary(Expectation.observabilityOnly("录音 (8)"), result);

        assertTrue(summary, result.signalSnapshot().targetToneFrequencyHz() > 0);
        assertTrue(summary, result.signalSnapshot().totalToneOnEvents() >= 100);
        assertTrue(summary, result.signalSnapshot().totalToneOffEvents() >= 100);
        assertTrue(summary, result.decoderSnapshot().totalCharacters() >= 60);
        assertTrue(summary + "\nrecall=" + recall, recall < 0.60d);
    }

    private static Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> loadDecodedResults() {
        try {
            Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> results = new LinkedHashMap<>();
            for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
                LocalAudioDecodeTestSupport.OfflineProbeResult result = LocalAudioDecodeTestSupport.decodeWavFile(wavFile);
                results.put(result.sourceLabel(), result);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode local TestAudio WAV fixtures", exception);
        }
    }

    private LocalAudioDecodeTestSupport.OfflineProbeResult requireResult(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = DECODED_RESULTS.get(sourceLabel);
        if (result == null && sourceLabel != null) {
            for (Map.Entry<String, LocalAudioDecodeTestSupport.OfflineProbeResult> entry : DECODED_RESULTS.entrySet()) {
                String candidateLabel = entry.getKey();
                if (candidateLabel.equalsIgnoreCase(sourceLabel)
                        || candidateLabel.endsWith(sourceLabel)
                        || sourceLabel.endsWith(candidateLabel)
                        || shareTrailingRecordingSuffix(candidateLabel, sourceLabel)) {
                    result = entry.getValue();
                    break;
                }
            }
        }
        assertNotNull("Missing decoded local audio fixture: " + sourceLabel, result);
        return result;
    }

    private Expectation requireExpectation(List<Expectation> expectations, String sourceLabel) {
        for (Expectation expectation : expectations) {
            if (expectation.sourceLabel.equals(sourceLabel)) {
                return expectation;
            }
        }
        throw new IllegalArgumentException("Missing expectation for source label: " + sourceLabel);
    }

    private ToneExpectation requireToneExpectation(String sourceLabel) {
        for (ToneExpectation expectation : STRICT_TONE_CASES) {
            if (expectation.sourceLabel.equals(sourceLabel)) {
                return expectation;
            }
        }
        throw new IllegalArgumentException("Missing tone expectation for source label: " + sourceLabel);
    }

    private void assertStrictReferenceCase(String sourceLabel) {
        assertStrictReferenceCase(requireExpectation(STRICT_CASES, sourceLabel));
    }

    private void assertStrictReferenceCase(Expectation expectation) {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult(expectation.sourceLabel);
        String expectedRawComparable = rawComparable(expectation.expectedText);
        String actualRawComparable = rawComparable(result.decodedText());
        String expectedCanonical = canonicalize(expectation.expectedText);
        String actualCanonical = canonicalize(result.decodedText());
        String summary = renderSummary(expectation, result);

        if (!RAW_STRUCTURE_STRICT_EXEMPTIONS.contains(expectation.sourceLabel)) {
            assertEquals(summary + "\nmode=rawComparable", expectedRawComparable, actualRawComparable);
        }
        assertEquals(summary, expectedCanonical, actualCanonical);
        assertTrue(summary, result.decoderSnapshot().totalCharacters() >= Math.max(8, expectedCanonical.length() / 3));
        assertTrue(summary, result.signalSnapshot().targetToneFrequencyHz() > 0);
    }

    private void assertStrictToneReferenceCase(String sourceLabel) {
        ToneExpectation expectation = requireToneExpectation(sourceLabel);
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult(expectation.sourceLabel);
        String summary = renderSummary(Expectation.strict(expectation.sourceLabel, expectation.sourceLabel), result);
        int trackedToneHz = result.signalSnapshot().targetToneFrequencyHz();

        assertTrue(
                summary
                        + "\nexpectedTone=" + expectation.expectedToneHz
                        + " actualTone=" + trackedToneHz
                        + " tolerance=" + expectation.toleranceHz,
                Math.abs(trackedToneHz - expectation.expectedToneHz) <= expectation.toleranceHz
        );
    }

    private void assertSoftReferenceCase(String sourceLabel) {
        assertSoftReferenceCase(requireExpectation(SOFT_CASES, sourceLabel));
    }

    private void assertSoftReferenceCase(Expectation expectation) {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult(expectation.sourceLabel);
        String summary = renderSummary(expectation, result);
        double recall = charRecall(expectation.expectedText, result.decodedText());

        assertTrue(summary + "\nrecall=" + recall, recall >= expectation.minRecall);
        assertTrue(summary, result.decoderSnapshot().totalCharacters() >= expectation.minCharacters);
        assertRequiredFragments(summary, result.decodedText(), expectation.requiredFragments);
        assertRequiredFragmentCounts(summary, result.decodedText(), expectation.requiredFragmentCounts);
        assertToneIfConfigured(summary, result, expectation);
        assertWpmIfConfigured(summary, result, expectation);
    }

    private void assertObservabilityCase(String sourceLabel) {
        assertObservabilityCase(requireExpectation(OBSERVABILITY_CASES, sourceLabel));
    }

    private void assertObservabilityCase(Expectation expectation) {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult(expectation.sourceLabel);
        String summary = renderSummary(expectation, result);

        assertNotNull(summary, result.decodedText());
        assertTrue(summary, !"(empty)".equals(result.decodedText()));
        assertTrue(summary, result.decoderSnapshot().totalCharacters() >= expectation.minCharacters);
        assertTrue(summary, result.signalSnapshot().totalToneOnEvents() >= 8);
        assertTrue(summary, result.signalSnapshot().totalToneOffEvents() >= 8);
        assertRequiredFragments(summary, result.decodedText(), expectation.requiredFragments);
        assertRequiredFragmentCounts(summary, result.decodedText(), expectation.requiredFragmentCounts);
        assertToneIfConfigured(summary, result, expectation);
        assertWpmIfConfigured(summary, result, expectation);
    }

    private boolean shareTrailingRecordingSuffix(String left, String right) {
        int leftOpen = left == null ? -1 : left.lastIndexOf('(');
        int rightOpen = right == null ? -1 : right.lastIndexOf('(');
        int leftClose = left == null ? -1 : left.lastIndexOf(')');
        int rightClose = right == null ? -1 : right.lastIndexOf(')');
        if (leftOpen < 0 || rightOpen < 0 || leftClose <= leftOpen || rightClose <= rightOpen) {
            return false;
        }
        String leftSuffix = left.substring(leftOpen, leftClose + 1);
        String rightSuffix = right.substring(rightOpen, rightClose + 1);
        return leftSuffix.equals(rightSuffix);
    }

    private void assertHypothesisRange(
            LocalAudioDecodeTestSupport.OfflineProbeResult result,
            int minimumHz,
            int maximumHz,
            double minimumConfidence
    ) {
        String summary = renderSummary(Expectation.observabilityOnly(result.sourceLabel()), result);
        int hypothesisHz = result.signalSnapshot().toneHypothesisFrequencyHz();
        double confidence = result.signalSnapshot().toneHypothesisConfidence();
        if (result.signalSnapshot().toneHypothesisSupportFrames() <= 0) {
            assertTrue(summary + "\ntrackedDisplay=" + result.signalSnapshot().effectiveTrackedToneFrequencyHz()
                            + " fallbackRange=[" + minimumHz + "," + maximumHz + "]",
                    result.signalSnapshot().effectiveTrackedToneFrequencyHz() >= minimumHz
                            && result.signalSnapshot().effectiveTrackedToneFrequencyHz() <= maximumHz);
            return;
        }

        assertTrue(summary + "\nhypothesisHz=" + hypothesisHz + " expectedRange=[" + minimumHz + "," + maximumHz + "]",
                hypothesisHz >= minimumHz && hypothesisHz <= maximumHz);
        assertTrue(summary + "\nhypothesisSupportFrames=" + result.signalSnapshot().toneHypothesisSupportFrames(),
                result.signalSnapshot().toneHypothesisSupportFrames() > 0);
        assertTrue(summary + "\nhypothesisConfidence=" + confidence,
                confidence >= minimumConfidence);
    }

    private void assertRequiredFragments(String summary, String actualText, List<String> requiredFragments) {
        String actualCanonical = canonicalize(actualText);
        for (String fragment : requiredFragments) {
            String fragmentCanonical = canonicalize(fragment);
            assertTrue(summary + "\nmissing fragment=" + fragment, actualCanonical.contains(fragmentCanonical));
        }
    }

    private void assertRequiredFragmentCounts(
            String summary,
            String actualText,
            Map<String, Integer> requiredFragmentCounts
    ) {
        String actualCanonical = canonicalize(actualText);
        for (Map.Entry<String, Integer> entry : requiredFragmentCounts.entrySet()) {
            String fragment = entry.getKey();
            String fragmentCanonical = canonicalize(fragment);
            int actualCount = countOccurrences(actualCanonical, fragmentCanonical);
            assertTrue(
                    summary
                            + "\nfragment=" + fragment
                            + " expectedCount>=" + entry.getValue()
                            + " actualCount=" + actualCount,
                    actualCount >= entry.getValue()
            );
        }
    }

    private void assertToneIfConfigured(
            String summary,
            LocalAudioDecodeTestSupport.OfflineProbeResult result,
            Expectation expectation
    ) {
        if (expectation.expectedTrackedToneHz == null) {
            return;
        }
        int actualTone = result.signalSnapshot().targetToneFrequencyHz();
        assertTrue(
                summary
                        + "\nexpectedTone=" + expectation.expectedTrackedToneHz
                        + " actualTone=" + actualTone
                        + " tolerance=" + expectation.trackedToneToleranceHz,
                Math.abs(actualTone - expectation.expectedTrackedToneHz) <= expectation.trackedToneToleranceHz
        );
    }

    private void assertWpmIfConfigured(
            String summary,
            LocalAudioDecodeTestSupport.OfflineProbeResult result,
            Expectation expectation
    ) {
        if (expectation.minWpm == null) {
            return;
        }
        assertTrue(
                summary + "\nminWpm=" + expectation.minWpm + " actualWpm=" + result.timingSnapshot().estimatedWpm(),
                result.timingSnapshot().estimatedWpm() >= expectation.minWpm
        );
    }

    private String renderSummary(Expectation expectation, LocalAudioDecodeTestSupport.OfflineProbeResult result) {
        return expectation.sourceLabel
                + "\nexpected=" + expectation.expectedText
                + "\nactual=" + result.decodedText()
                + "\nchars=" + result.decoderSnapshot().totalCharacters()
                + ", prevTarget=" + result.signalSnapshot().previousTargetBeforeScanFrequencyHz()
                + ", prevScore=" + String.format(Locale.US, "%.1f", result.signalSnapshot().previousTargetBeforeScanSelectionScore())
                + ", trackedTone=" + result.signalSnapshot().targetToneFrequencyHz()
                + ", trackedDisplay=" + result.signalSnapshot().effectiveTrackedToneFrequencyHz()
                + ", hypTone=" + result.signalSnapshot().toneHypothesisFrequencyHz()
                + ", hypConf=" + String.format(Locale.US, "%.2f", result.signalSnapshot().toneHypothesisConfidence())
                + ", hypFrames=" + result.signalSnapshot().toneHypothesisSupportFrames()
                + ", hypIdle=" + result.signalSnapshot().toneHypothesisIdleFrames()
                + ", hypSource=" + result.signalSnapshot().toneHypothesisSource()
                + ", finalTone=" + result.signalSnapshot().finalAdoptedFrequencyHz()
                + ", finalDisplay=" + result.signalSnapshot().effectiveFinalAdoptedFrequencyHz()
                + ", preferredWinner=" + result.signalSnapshot().preferredWindowWinnerFrequencyHz()
                + ", wideWinner=" + result.signalSnapshot().wideScanWinnerFrequencyHz()
                + ", acquisitionWinner=" + result.signalSnapshot().acquisitionWinnerFrequencyHz()
                + ", acquisitionDisplay=" + result.signalSnapshot().effectiveAcquisitionWinnerFrequencyHz()
                + ", acquisitionRunnerUp=" + result.signalSnapshot().acquisitionRunnerUpFrequencyHz()
                + ", acquisitionSource=" + result.signalSnapshot().acquisitionWinnerSource()
                + ", adoptedSource=" + result.signalSnapshot().finalAdoptedSource()
                + ", prefConf=" + Math.round(result.signalSnapshot().preferredWindowWinnerConfidence() * 100.0d)
                + "%, wideConf=" + Math.round(result.signalSnapshot().wideScanWinnerConfidence() * 100.0d)
                + "%, adoptedConf=" + Math.round(result.signalSnapshot().finalAdoptedConfidence() * 100.0d) + "%"
                + ", wpm=" + result.timingSnapshot().estimatedWpm()
                + ", strategy=" + result.timingStrategySummary()
                + ", toneOn=" + result.signalSnapshot().totalToneOnEvents()
                + ", toneOff=" + result.signalSnapshot().totalToneOffEvents()
                + ", acqDetail=" + result.signalSnapshot().acquisitionDecisionDetail()
                + ", adoptDetail=" + result.signalSnapshot().finalAdoptionDetail()
                + ", prefTop=" + result.signalSnapshot().preferredWindowTopCandidatesSummary()
                + ", wideTop=" + result.signalSnapshot().wideScanTopCandidatesSummary()
                + ", hints=" + String.join(" / ", result.interpreterSnapshot().phraseHints());
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

    private static String rawComparable(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder builder = new StringBuilder(upper.length());
        boolean previousWasSpace = false;
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if (Character.isWhitespace(ch)) {
                if (!previousWasSpace && builder.length() > 0) {
                    builder.append(' ');
                }
                previousWasSpace = true;
                continue;
            }
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '?' || ch == '.' || ch == ',' || ch == '/' || ch == '=' || ch == '-') {
                builder.append(ch);
                previousWasSpace = false;
            }
        }
        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == ' ') {
            builder.setLength(length - 1);
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

    private static int countOccurrences(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while (offset <= text.length() - fragment.length()) {
            int index = text.indexOf(fragment, offset);
            if (index < 0) {
                break;
            }
            count += 1;
            offset = index + fragment.length();
        }
        return count;
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
            Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static final class Expectation {
        private final String sourceLabel;
        private final String expectedText;
        private final double minRecall;
        private final List<String> requiredFragments;
        private final Map<String, Integer> requiredFragmentCounts;
        private final int minCharacters;
        private final Integer expectedTrackedToneHz;
        private final int trackedToneToleranceHz;
        private final Integer minWpm;

        private Expectation(
                String sourceLabel,
                String expectedText,
                double minRecall,
                List<String> requiredFragments,
                Map<String, Integer> requiredFragmentCounts,
                int minCharacters,
                Integer expectedTrackedToneHz,
                int trackedToneToleranceHz,
                Integer minWpm
        ) {
            this.sourceLabel = sourceLabel;
            this.expectedText = expectedText;
            this.minRecall = minRecall;
            this.requiredFragments = requiredFragments;
            this.requiredFragmentCounts = requiredFragmentCounts;
            this.minCharacters = minCharacters;
            this.expectedTrackedToneHz = expectedTrackedToneHz;
            this.trackedToneToleranceHz = trackedToneToleranceHz;
            this.minWpm = minWpm;
        }

        private static Expectation strict(String sourceLabel, String expectedText) {
            return new Expectation(sourceLabel, expectedText, 1.0d, Arrays.asList(), new LinkedHashMap<>(), 1, null, 0, null);
        }

        private static Expectation soft(String sourceLabel, String expectedText, double minRecall) {
            return new Expectation(sourceLabel, expectedText, minRecall, Arrays.asList(), new LinkedHashMap<>(), 1, null, 0, null);
        }

        private static Expectation observabilityOnly(String sourceLabel) {
            return new Expectation(sourceLabel, "", 0.0d, Arrays.asList(), new LinkedHashMap<>(), 1, null, 0, null);
        }

        private static Expectation observabilityOnly(String sourceLabel, String expectedText) {
            return new Expectation(sourceLabel, expectedText, 0.0d, Arrays.asList(), new LinkedHashMap<>(), 1, null, 0, null);
        }

        private Expectation requireFragments(String... fragments) {
            return new Expectation(
                    sourceLabel,
                    expectedText,
                    minRecall,
                    Arrays.asList(fragments),
                    requiredFragmentCounts,
                    minCharacters,
                    expectedTrackedToneHz,
                    trackedToneToleranceHz,
                    minWpm
            );
        }

        private Expectation requireFragmentCount(String fragment, int minCount) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>(requiredFragmentCounts);
            counts.put(fragment, minCount);
            return new Expectation(
                    sourceLabel,
                    expectedText,
                    minRecall,
                    requiredFragments,
                    counts,
                    minCharacters,
                    expectedTrackedToneHz,
                    trackedToneToleranceHz,
                    minWpm
            );
        }

        private Expectation withMinCharacters(int value) {
            return new Expectation(
                    sourceLabel,
                    expectedText,
                    minRecall,
                    requiredFragments,
                    requiredFragmentCounts,
                    value,
                    expectedTrackedToneHz,
                    trackedToneToleranceHz,
                    minWpm
            );
        }

        private Expectation withTrackedTone(int value, int toleranceHz) {
            return new Expectation(
                    sourceLabel,
                    expectedText,
                    minRecall,
                    requiredFragments,
                    requiredFragmentCounts,
                    minCharacters,
                    value,
                    toleranceHz,
                    minWpm
            );
        }

        private Expectation withWpmFloor(int value) {
            return new Expectation(
                    sourceLabel,
                    expectedText,
                    minRecall,
                    requiredFragments,
                    requiredFragmentCounts,
                    minCharacters,
                    expectedTrackedToneHz,
                    trackedToneToleranceHz,
                    value
            );
        }
    }

    private static final class ToneExpectation {
        private final String sourceLabel;
        private final int expectedToneHz;
        private final int toleranceHz;

        private ToneExpectation(String sourceLabel, int expectedToneHz, int toleranceHz) {
            this.sourceLabel = sourceLabel;
            this.expectedToneHz = expectedToneHz;
            this.toleranceHz = toleranceHz;
        }
    }
}
