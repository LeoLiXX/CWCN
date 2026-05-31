package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwLocalAudioLiveLikeRegressionTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> DECODED_RESULTS =
            loadDecodedResults();

    @Test
    public void localAudioLiveLikeRecording2_keepsTwoTurnsReadable() {
        String expectedText = "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.";
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (2)");
        String summary = renderSummary("录音 (2)", expectedText, result);
        String actualCanonical = canonicalize(result.decodedText());
        double recall = charRecall(expectedText, result.decodedText());

        assertTrue(summary + "\nrecall=" + recall, recall >= 0.90d);
        assertTrue(summary, actualCanonical.startsWith(canonicalize("CQ DX CQ DX DE JV3VV JV3VV PAGE K")));
        assertTrue(summary, countOccurrences(actualCanonical, "DEJV3VV") >= 2);
        assertTrue(summary, countOccurrences(actualCanonical, "JV3VV") >= 4);
        assertTrue(summary, countOccurrences(actualCanonical, "PAGE") >= 2);
        assertTrue(summary, countOccurrences(actualCanonical, "CQDX") >= 3);
    }

    @Test
    public void localAudioLiveLikeRecording16_keepsExactRawCopy() {
        String expectedText = "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (16)");
        String summary = renderSummary("录音 (16)", expectedText, result);

        assertEquals(summary, sanitize(expectedText), sanitize(result.decodedText()));
    }

    @Test
    public void localAudioLiveLikeRecording7_keepsCallsignTailReadable() {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (7)");
        String actualCanonical = canonicalize(result.decodedText());
        String summary = renderSummary("录音 (7) key tail", "BI3TUK KN", result);

        assertTrue(summary, actualCanonical.contains(canonicalize("BI3TUK")));
        assertTrue(summary, actualCanonical.contains(canonicalize("KN")));
        assertTrue(summary, !actualCanonical.contains(canonicalize("6I3TUK")));
    }

    @Test
    public void localAudioLiveLikeRecording7_keepsDeAndCallsignTailTogether() {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (7)");
        String actualCanonical = canonicalize(result.decodedText());
        String summary = renderSummary("录音 (7) DE tail", "DE BI3TUK KN", result);

        assertTrue(summary,
                actualCanonical.contains(canonicalize("DEBI3TUK"))
                        || actualCanonical.contains(canonicalize("DIBI3TUK")));
        assertTrue(summary, actualCanonical.contains(canonicalize("KN")));
        assertTrue(summary, !actualCanonical.contains(canonicalize("BIBI3TUK")));
    }

    @Test
    public void localAudioLiveLikeRecording2_secondTurnOpeningKeepsCallupVisible() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TurnTransitionTrace turn2Start = null;
        LocalAudioDecodeTestSupport.TurnTransitionTrace turn2End = null;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : detailed.turnTransitionTraces()) {
            if (trace == null) {
                continue;
            }
            if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START
                    && trace.turnIndex() == 2) {
                turn2Start = trace;
            } else if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END
                    && trace.turnIndex() == 2) {
                turn2End = trace;
                break;
            }
        }

        assertNotNull("Missing turn 2 start for recording2", turn2Start);
        long turn2StartMs = turn2Start.timestampMs();
        long turn2WindowEndMs = Math.min(
                turn2End == null ? detailed.flushTimestampMs() : turn2End.timestampMs(),
                turn2StartMs + 6000L
        );
        String rawBeforeTurn2 = textAtOrBefore(detailed.rawDecodeEvents(), Math.max(0L, turn2StartMs - 1L));
        String rawTurn2Opening = sliceNewText(
                rawBeforeTurn2,
                textAtOrBefore(detailed.rawDecodeEvents(), turn2WindowEndMs)
        );
        String canonicalOpening = canonicalize(rawTurn2Opening);
        String summary = renderSummary("录音 (2) turn2 opening", "CQ DX CQ DX DE", detailed.probeResult())
                + "\nrawTurn2Opening=" + rawTurn2Opening
                + "\nturn2StartMs=" + turn2StartMs
                + "\nturn2WindowEndMs=" + turn2WindowEndMs;

        assertTrue(summary, canonicalOpening.startsWith("C"));
        assertTrue(summary, canonicalOpening.contains(canonicalize("CQ DX DE")));
        assertTrue(summary, countOccurrences(canonicalOpening, "CQDX") >= 1);
        assertTrue(summary, !canonicalOpening.contains("DNT"));
        assertTrue(summary, !canonicalOpening.contains("CTK"));
    }

    @Test
    public void localAudioLiveLikeRecording8_keepsReadableLongQsoCopy() {
        String expectedText =
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";
        LocalAudioDecodeTestSupport.OfflineProbeResult result = requireResult("录音 (8)");
        String summary = renderSummary("录音 (8)", expectedText, result);
        String actualCanonical = canonicalize(result.decodedText());
        double recall = charRecall(expectedText, result.decodedText());

        assertTrue(summary + "\nrecall=" + recall, recall >= 0.75d);
        assertTrue(summary, result.decoderSnapshot().totalCharacters() >= 120);
        assertTrue(summary, countOccurrences(actualCanonical, "BG1XXX") >= 2);
        assertTrue(summary, countOccurrences(actualCanonical, "JA1ABC") >= 1);
        assertTrue(summary, countOccurrences(actualCanonical, "RST") >= 2);
        assertTrue(summary, countOccurrences(actualCanonical, "SK") >= 1);
    }

    @Test
    public void localAudioLiveLikeRecording12_currentBaseGateRemainsBestOverall() throws Exception {
        String expectedText = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult base =
                decodeRecording12Variant(frames, false, false, false, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult auth =
                decodeRecording12Variant(frames, true, false, false, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult onset =
                decodeRecording12Variant(frames, false, true, false, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult merge =
                decodeRecording12Variant(frames, false, false, true, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult hold =
                decodeRecording12Variant(frames, false, false, false, true);

        double baseRecall = charRecall(expectedText, base.probeResult().decodedText());
        double authRecall = charRecall(expectedText, auth.probeResult().decodedText());
        double onsetRecall = charRecall(expectedText, onset.probeResult().decodedText());
        double mergeRecall = charRecall(expectedText, merge.probeResult().decodedText());
        double holdRecall = charRecall(expectedText, hold.probeResult().decodedText());

        String summary = "录音 (12) current gate baseline"
                + "\nBASE  recall=" + String.format(Locale.US, "%.3f", baseRecall)
                + " trust=" + firstTrustedTimestampMs(base)
                + " text=" + base.probeResult().decodedText()
                + "\nAUTH  recall=" + String.format(Locale.US, "%.3f", authRecall)
                + " trust=" + firstTrustedTimestampMs(auth)
                + " text=" + auth.probeResult().decodedText()
                + "\nONSET recall=" + String.format(Locale.US, "%.3f", onsetRecall)
                + " trust=" + firstTrustedTimestampMs(onset)
                + " text=" + onset.probeResult().decodedText()
                + "\nMERGE recall=" + String.format(Locale.US, "%.3f", mergeRecall)
                + " trust=" + firstTrustedTimestampMs(merge)
                + " text=" + merge.probeResult().decodedText()
                + "\nHOLD  recall=" + String.format(Locale.US, "%.3f", holdRecall)
                + " trust=" + firstTrustedTimestampMs(hold)
                + " text=" + hold.probeResult().decodedText();

        assertTrue(summary, baseRecall >= authRecall);
        assertTrue(summary, baseRecall >= onsetRecall);
        assertTrue(summary, baseRecall > mergeRecall);
        assertTrue(summary, baseRecall >= holdRecall);
    }

    @Test
    public void localAudioLiveLikeRecording12_manualFixed700Window30KeepsPracticalCopy() throws Exception {
        String expectedText = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fixed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
                        "recording12-fixed-win30-regression",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        30,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        String decodedText = fixed.probeResult().decodedText();
        String actualCanonical = canonicalize(decodedText);
        double recall = charRecall(expectedText, decodedText);
        String summary = "录音 (12) manual fixed 700 ±30"
                + "\nexpected=" + expectedText
                + "\nactual=" + decodedText
                + "\nrecall=" + recall
                + "\ntrackedTone=" + fixed.probeResult().signalSnapshot().targetToneFrequencyHz()
                + ", acquisitionTone=" + fixed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz()
                + ", finalTone=" + fixed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz()
                + ", wpm=" + fixed.probeResult().timingSnapshot().estimatedWpm()
                + ", chars=" + fixed.probeResult().decoderSnapshot().totalCharacters();

        assertTrue(summary, recall >= 0.78d);
        assertTrue(summary,
                actualCanonical.contains(canonicalize("BI9CMS"))
                        || actualCanonical.contains(canonicalize("9CMS")));
        assertTrue(summary, actualCanonical.contains(canonicalize("700")));
        assertTrue(summary, actualCanonical.contains(canonicalize("PSEK")));
        assertTrue(summary, Math.abs(fixed.probeResult().signalSnapshot().targetToneFrequencyHz() - 700) <= 30);
    }

    private static Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> loadDecodedResults() {
        try {
            Map<String, LocalAudioDecodeTestSupport.OfflineProbeResult> results = new LinkedHashMap<>();
            for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
                String fileName = wavFile.getFileName().toString();
                if (!fileName.endsWith("(2).wav")
                        && !fileName.endsWith("(7).wav")
                        && !fileName.endsWith("(8).wav")
                        && !fileName.endsWith("(16).wav")) {
                    continue;
                }
                LocalAudioDecodeTestSupport.OfflineProbeResult result =
                        LocalAudioDecodeTestSupport.decodeWavFileLiveLike(
                                wavFile,
                                PREFERRED_TONE_HZ,
                                SEED_WPM,
                                SQL_PERCENT,
                                false,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                results.put(result.sourceLabel(), result);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode live-like local audio fixtures", exception);
        }
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decodeRecording12Variant(
            List<AudioFrame> frames,
            boolean authority,
            boolean onset,
            boolean merge,
            boolean hold
    ) {
        return LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                String.format(
                        Locale.US,
                        "recording12-regression-a%s-o%s-m%s-h%s",
                        authority ? 1 : 0,
                        onset ? 1 : 0,
                        merge ? 1 : 0,
                        hold ? 1 : 0
                ),
                frames,
                PREFERRED_TONE_HZ,
                SEED_WPM,
                SQL_PERCENT,
                false,
                authority,
                onset,
                merge,
                hold,
                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
        );
    }

    private static long firstTrustedTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : result.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static LocalAudioDecodeTestSupport.OfflineProbeResult requireResult(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineProbeResult result = DECODED_RESULTS.get(sourceLabel);
        assertNotNull("Missing live-like decoded local audio fixture: " + sourceLabel, result);
        return result;
    }

    private static String renderSummary(
            String sourceLabel,
            String expectedText,
            LocalAudioDecodeTestSupport.OfflineProbeResult result
    ) {
        return sourceLabel
                + "\nexpected=" + expectedText
                + "\nactual=" + result.decodedText()
                + "\ntrackedTone=" + result.signalSnapshot().targetToneFrequencyHz()
                + ", acquisitionTone=" + result.signalSnapshot().effectiveAcquisitionWinnerFrequencyHz()
                + ", finalTone=" + result.signalSnapshot().effectiveFinalAdoptedFrequencyHz()
                + ", wpm=" + result.timingSnapshot().estimatedWpm()
                + ", chars=" + result.decoderSnapshot().totalCharacters();
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String textAtOrBefore(
            java.util.List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents,
            long timestampMs
    ) {
        String latestText = "";
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String sliceNewText(String previousText, String currentText) {
        String safePrevious = previousText == null ? "" : previousText;
        String safeCurrent = currentText == null ? "" : currentText;
        if (safePrevious.length() >= safeCurrent.length()) {
            return "";
        }
        return safeCurrent.substring(safePrevious.length());
    }
}
