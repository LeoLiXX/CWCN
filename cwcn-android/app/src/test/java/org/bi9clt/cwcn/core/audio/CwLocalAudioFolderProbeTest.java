package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwLocalAudioFolderProbeTest {
    @Test
    public void printCurrentDecodeSummaryForAllConvertedLocalAudioFiles() throws Exception {
        List<Path> wavFiles = LocalAudioDecodeTestSupport.listConvertedWavFiles();
        assertTrue("No WAV files found under TestAudio/wav", !wavFiles.isEmpty());

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Current Decode Probe\n\n");
        for (Path wavFile : wavFiles) {
            LocalAudioDecodeTestSupport.OfflineProbeResult result = LocalAudioDecodeTestSupport.decodeWavFile(wavFile);
            markdown.append("## ")
                    .append(result.sourceLabel())
                    .append(".m4a")
                    .append("\n\n")
                    .append("内容大致为\n\n")
                    .append("```text\n")
                    .append(result.decodedText())
                    .append("\n```\n\n")
                    .append("当前算法观测\n\n")
                    .append("```text\n")
                    .append("chars=").append(result.decoderSnapshot().totalCharacters())
                    .append(", prevTarget=").append(result.signalSnapshot().previousTargetBeforeScanFrequencyHz()).append("Hz")
                    .append(", prevScore=").append(String.format(java.util.Locale.US, "%.1f", result.signalSnapshot().previousTargetBeforeScanSelectionScore()))
                    .append(", trackedTone=").append(result.signalSnapshot().targetToneFrequencyHz()).append("Hz")
                    .append(", trackedDisplay=").append(result.signalSnapshot().effectiveTrackedToneFrequencyHz()).append("Hz")
                    .append(", finalTone=").append(result.signalSnapshot().finalAdoptedFrequencyHz()).append("Hz")
                    .append(", finalDisplay=").append(result.signalSnapshot().effectiveFinalAdoptedFrequencyHz()).append("Hz")
                    .append(", preferredWinner=").append(result.signalSnapshot().preferredWindowWinnerFrequencyHz()).append("Hz")
                    .append(", wideWinner=").append(result.signalSnapshot().wideScanWinnerFrequencyHz()).append("Hz")
                    .append(", acquisitionWinner=").append(result.signalSnapshot().acquisitionWinnerFrequencyHz()).append("Hz")
                    .append(", acquisitionDisplay=").append(result.signalSnapshot().effectiveAcquisitionWinnerFrequencyHz()).append("Hz")
                    .append(", acquisitionRunnerUp=").append(result.signalSnapshot().acquisitionRunnerUpFrequencyHz()).append("Hz")
                    .append(", acquisitionSource=").append(result.signalSnapshot().acquisitionWinnerSource())
                    .append(", adoptedSource=").append(result.signalSnapshot().finalAdoptedSource())
                    .append(", prefConf=").append(Math.round(result.signalSnapshot().preferredWindowWinnerConfidence() * 100.0d)).append('%')
                    .append(", wideConf=").append(Math.round(result.signalSnapshot().wideScanWinnerConfidence() * 100.0d)).append('%')
                    .append(", adoptedConf=").append(Math.round(result.signalSnapshot().finalAdoptedConfidence() * 100.0d)).append('%')
                    .append(", acqDetail=").append(result.signalSnapshot().acquisitionDecisionDetail())
                    .append(", adoptDetail=").append(result.signalSnapshot().finalAdoptionDetail())
                    .append(", prefTop=").append(result.signalSnapshot().preferredWindowTopCandidatesSummary())
                    .append(", wideTop=").append(result.signalSnapshot().wideScanTopCandidatesSummary())
                    .append(", activeLeaders=").append(result.signalProcessorLeaderSummary())
                    .append(", wpm=").append(result.timingSnapshot().estimatedWpm())
                    .append(", strategy=").append(result.timingStrategySummary())
                    .append(", toneOn=").append(result.signalSnapshot().totalToneOnEvents())
                    .append(", toneOff=").append(result.signalSnapshot().totalToneOffEvents())
                    .append(", hints=").append(String.join(" / ", result.interpreterSnapshot().phraseHints()))
                    .append("\n```\n\n");
        }

        System.out.println(markdown);
    }
}
