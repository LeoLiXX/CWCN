package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording3FrontEndDiagnosisProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRecording3FrontEndDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile profile =
                LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(detailed, 40);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effectiveReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);

        System.out.println("==== recording3 live-like diagnosis ====");
        System.out.println(String.format(
                Locale.US,
                "base text=%s chars=%d turns=%d tone=%d/%d/%d hyp=%d conf=%.2f support=%d idle=%d source=%s on=%d off=%d wpm=%d rejects=%s",
                sanitize(detailed.probeResult().decodedText()),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                countTurns(detailed.turnTransitionTraces()),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().signalSnapshot().toneHypothesisFrequencyHz(),
                detailed.probeResult().signalSnapshot().toneHypothesisConfidence(),
                detailed.probeResult().signalSnapshot().toneHypothesisSupportFrames(),
                detailed.probeResult().signalSnapshot().toneHypothesisIdleFrames(),
                detailed.probeResult().signalSnapshot().toneHypothesisSource(),
                detailed.probeResult().signalSnapshot().totalToneOnEvents(),
                detailed.probeResult().signalSnapshot().totalToneOffEvents(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts()
        ));
        System.out.println("profile=" + profile.renderSummary());
        System.out.println("tracked=" + trackedReplay.renderSummary());
        System.out.println("effective=" + effectiveReplay.renderSummary());
        System.out.println("hypothesis=" + hypothesisReplay.renderSummary());

        assertTrue(true);
    }

    private static int countTurns(java.util.List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
