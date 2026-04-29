package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class LocalAudioDecodeTestSupport {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;

    private LocalAudioDecodeTestSupport() {
    }

    static Path findTestAudioDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (Path probe = current; probe != null; probe = probe.getParent()) {
            Path candidate = probe.resolve("TestAudio");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate TestAudio directory from " + current);
    }

    static List<Path> listConvertedWavFiles() throws IOException {
        Path wavDir = findTestAudioDir().resolve("wav");
        if (!Files.isDirectory(wavDir)) {
            throw new IOException("Converted WAV folder is missing: " + wavDir);
        }
        try (Stream<Path> stream = Files.list(wavDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.US).endsWith(".wav"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }
    }

    static OfflineProbeResult decodeWavFile(Path wavFile) throws IOException {
        return decodeWavFileDetailed(wavFile).probeResult();
    }

    static OfflineDetailedProbeResult decodeWavFileDetailed(Path wavFile) throws IOException {
        WaveDataProbe waveData = readWaveFileForProbe(wavFile);
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<AudioFrame> capturedFrames = new ArrayList<>();
        ArrayList<CwToneEvent> capturedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> capturedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> capturedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();

        short[] samples = waveData.samples();
        int sampleRateHz = waveData.sampleRateHz();
        long sampleOffset = 0L;
        for (int offset = 0; offset < samples.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, samples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(samples, offset, frameSamples, 0, frameLength);
            AudioFrame frame = buildFrameForProbe(frameSamples, sampleRateHz, sampleOffset);
            capturedFrames.add(frame);
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            capturedToneEvents.addAll(toneEvents);
            frameSignalTraces.add(new FrameSignalTrace(frame.capturedAtMs(), signalProcessor.snapshot()));
            drainToneEvents(
                    toneEvents,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            sampleOffset += frameLength;
        }

        long flushTimestampMs = Math.max(
                1L,
                Math.round(samples.length * 1000.0d / sampleRateHz)
        );
        List<CwTimingEvent> timingEvents = timingModel.flushPendingGap(flushTimestampMs);
        capturedTimingEvents.addAll(timingEvents);
        drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, capturedDecodeEvents);
        List<CwDecodeEvent> trailingDecodeEvents = decoder.flushPendingCharacter(flushTimestampMs);
        drainDecodeEvents(trailingDecodeEvents, interpreter, qsoStateMachine, capturedDecodeEvents);

        String fileName = wavFile.getFileName().toString();
        String sourceLabel = fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        OfflineProbeResult probeResult = new OfflineProbeResult(
                sourceLabel,
                sanitize(decoder.snapshot().decodedText()),
                signalProcessor.snapshot(),
                signalProcessor.debugActiveLeaderCompactSummary(),
                timingModel.snapshot(),
                timingModel.debugStrategySummary(),
                decoder.snapshot(),
                interpreter.snapshot()
        );
        return new OfflineDetailedProbeResult(
                probeResult,
                capturedFrames,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                frameSignalTraces,
                flushTimestampMs
        );
    }

    static AudioFrame buildFrameForProbe(short[] samples, int sampleRateHz, long sampleOffset) {
        int peak = 0;
        int clippedSampleCount = 0;
        double sumSquares = 0.0d;
        for (short sample : samples) {
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            if (absolute >= CLIPPING_SAMPLE_THRESHOLD) {
                clippedSampleCount += 1;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = samples.length == 0 ? 0.0d : Math.sqrt(sumSquares / samples.length);
        long capturedAtMs = Math.round(sampleOffset * 1000.0d / sampleRateHz);
        return new AudioFrame(samples, sampleRateHz, 1, peak, rms, clippedSampleCount, capturedAtMs);
    }

    static WaveDataProbe readWaveFileForProbe(Path wavFile) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(wavFile);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             DataInputStream inputStream = new DataInputStream(bufferedInputStream)) {
            byte[] riffHeader = new byte[12];
            inputStream.readFully(riffHeader);
            String riff = new String(riffHeader, 0, 4, StandardCharsets.US_ASCII);
            String wave = new String(riffHeader, 8, 4, StandardCharsets.US_ASCII);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
                throw new IOException("Unsupported WAV header in " + wavFile);
            }

            Integer sampleRateHz = null;
            Integer channelCount = null;
            Integer bitsPerSample = null;
            byte[] dataBytes = null;

            while (inputStream.available() > 0) {
                byte[] chunkHeader = new byte[8];
                inputStream.readFully(chunkHeader);
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = littleEndianInt(chunkHeader, 4);

                if ("fmt ".equals(chunkId)) {
                    byte[] fmtBytes = new byte[chunkSize];
                    inputStream.readFully(fmtBytes);
                    int audioFormat = littleEndianShort(fmtBytes, 0);
                    channelCount = littleEndianShort(fmtBytes, 2);
                    sampleRateHz = littleEndianInt(fmtBytes, 4);
                    bitsPerSample = littleEndianShort(fmtBytes, 14);
                    if (audioFormat != 1) {
                        throw new IOException("Only PCM WAV is supported for local probe: " + wavFile);
                    }
                } else if ("data".equals(chunkId)) {
                    dataBytes = new byte[chunkSize];
                    inputStream.readFully(dataBytes);
                } else {
                    long skipped = inputStream.skipBytes(chunkSize);
                    if (skipped != chunkSize) {
                        throw new IOException("Failed to skip WAV chunk " + chunkId + " in " + wavFile);
                    }
                }

                if ((chunkSize & 1) != 0) {
                    inputStream.skipBytes(1);
                }
            }

            if (sampleRateHz == null || channelCount == null || bitsPerSample == null || dataBytes == null) {
                throw new IOException("Incomplete WAV structure in " + wavFile);
            }
            if (bitsPerSample != 16) {
                throw new IOException("Only PCM16 WAV is supported for local probe: " + wavFile);
            }

            short[] rawSamples = new short[dataBytes.length / 2];
            for (int index = 0; index < rawSamples.length; index++) {
                rawSamples[index] = (short) littleEndianShort(dataBytes, index * 2);
            }

            short[] monoSamples;
            if (channelCount == 1) {
                monoSamples = rawSamples;
            } else {
                int frameCount = rawSamples.length / channelCount;
                monoSamples = new short[frameCount];
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    int sum = 0;
                    for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                        sum += rawSamples[(frameIndex * channelCount) + channelIndex];
                    }
                    monoSamples[frameIndex] = (short) Math.max(
                            Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(sum / (float) channelCount))
                    );
                }
            }

            return new WaveDataProbe(sampleRateHz, monoSamples);
        }
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    static TrailingWindowRedecodeResult redecodeTrailingWords(
            OfflineDetailedProbeResult detailedProbeResult,
            int trailingWordCount
    ) {
        if (detailedProbeResult == null) {
            throw new IllegalArgumentException("detailedProbeResult == null");
        }
        if (trailingWordCount <= 0) {
            throw new IllegalArgumentException("trailingWordCount must be > 0");
        }

        long windowStartTimestampMs = trailingWordWindowStartTimestampMs(
                detailedProbeResult.decodeEvents(),
                trailingWordCount
        );

        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();

        for (CwToneEvent toneEvent : detailedProbeResult.toneEvents()) {
            if (toneEvent.timestampMs() < windowStartTimestampMs) {
                continue;
            }
            List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
            replayedTimingEvents.addAll(timingEvents);
            drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, replayedDecodeEvents);
        }

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(detailedProbeResult.flushTimestampMs());
        replayedTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, replayedDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(detailedProbeResult.flushTimestampMs()),
                interpreter,
                qsoStateMachine,
                replayedDecodeEvents
        );

        return new TrailingWindowRedecodeResult(
                trailingWordCount,
                windowStartTimestampMs,
                sanitize(decoder.snapshot().decodedText()),
                timingModel.snapshot(),
                decoder.snapshot(),
                interpreter.snapshot(),
                replayedTimingEvents,
                replayedDecodeEvents
        );
    }

    private static void drainToneEvents(
            List<CwToneEvent> toneEvents,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
            capturedTimingEvents.addAll(timingEvents);
            drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, capturedDecodeEvents);
        }
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
            drainDecodeEvents(decodeEvents, interpreter, qsoStateMachine, capturedDecodeEvents);
        }
    }

    private static void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            capturedDecodeEvents.add(decodeEvent);
        }
    }

    private static long trailingWordWindowStartTimestampMs(
            List<CwDecodeEvent> decodeEvents,
            int trailingWordCount
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return 0L;
        }
        int endIndex = decodeEvents.size() - 1;
        while (endIndex >= 0 && decodeEvents.get(endIndex).type() == CwDecodeEvent.Type.WORD_BREAK) {
            endIndex -= 1;
        }
        if (endIndex < 0) {
            return decodeEvents.get(0).timestampMs();
        }

        int remainingWordBreaks = trailingWordCount;
        for (int index = endIndex; index >= 0; index--) {
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            if (decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
                continue;
            }
            remainingWordBreaks -= 1;
            if (remainingWordBreaks == 0) {
                return decodeEvent.timestampMs();
            }
        }
        return decodeEvents.get(0).timestampMs();
    }

    static final class WaveDataProbe {
        private final int sampleRateHz;
        private final short[] samples;

        private WaveDataProbe(int sampleRateHz, short[] samples) {
            this.sampleRateHz = sampleRateHz;
            this.samples = samples;
        }

        int sampleRateHz() {
            return sampleRateHz;
        }

        short[] samples() {
            return samples;
        }
    }

    static final class OfflineProbeResult {
        private final String sourceLabel;
        private final String decodedText;
        private final CwSignalSnapshot signalSnapshot;
        private final String signalProcessorLeaderSummary;
        private final CwTimingSnapshot timingSnapshot;
        private final String timingStrategySummary;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;

        private OfflineProbeResult(
                String sourceLabel,
                String decodedText,
                CwSignalSnapshot signalSnapshot,
                String signalProcessorLeaderSummary,
                CwTimingSnapshot timingSnapshot,
                String timingStrategySummary,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot
        ) {
            this.sourceLabel = sourceLabel;
            this.decodedText = decodedText;
            this.signalSnapshot = signalSnapshot;
            this.signalProcessorLeaderSummary = signalProcessorLeaderSummary;
            this.timingSnapshot = timingSnapshot;
            this.timingStrategySummary = timingStrategySummary;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
        }

        String sourceLabel() {
            return sourceLabel;
        }

        String decodedText() {
            return decodedText;
        }

        CwSignalSnapshot signalSnapshot() {
            return signalSnapshot;
        }

        String signalProcessorLeaderSummary() {
            return signalProcessorLeaderSummary;
        }

        CwTimingSnapshot timingSnapshot() {
            return timingSnapshot;
        }

        String timingStrategySummary() {
            return timingStrategySummary;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }
    }

    static final class OfflineDetailedProbeResult {
        private final OfflineProbeResult probeResult;
        private final List<AudioFrame> frames;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<FrameSignalTrace> frameSignalTraces;
        private final long flushTimestampMs;

        private OfflineDetailedProbeResult(
                OfflineProbeResult probeResult,
                List<AudioFrame> frames,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<FrameSignalTrace> frameSignalTraces,
                long flushTimestampMs
        ) {
            this.probeResult = probeResult;
            this.frames = frames;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.frameSignalTraces = frameSignalTraces;
            this.flushTimestampMs = flushTimestampMs;
        }

        OfflineProbeResult probeResult() {
            return probeResult;
        }

        List<AudioFrame> frames() {
            return frames;
        }

        List<CwToneEvent> toneEvents() {
            return toneEvents;
        }

        List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }

        List<FrameSignalTrace> frameSignalTraces() {
            return frameSignalTraces;
        }

        long flushTimestampMs() {
            return flushTimestampMs;
        }
    }

    static FrontEndDisagreementProfile evaluateFrontEndDisagreementProfile(
            OfflineDetailedProbeResult detailedProbeResult,
            int disagreementThresholdHz
    ) {
        int observedHypothesisFrames = 0;
        int rawTargetDisagreementFrames = 0;
        int effectiveTrackedDisagreementFrames = 0;
        int rawTargetNearRepresentativeFrames = 0;
        int hypothesisNearRepresentativeFrames = 0;
        int effectiveTrackedNearRepresentativeFrames = 0;
        CwSignalSnapshot finalSnapshot = detailedProbeResult.probeResult().signalSnapshot();

        for (FrameSignalTrace frameSignalTrace : detailedProbeResult.frameSignalTraces()) {
            CwSignalSnapshot snapshot = frameSignalTrace.snapshot();
            if (snapshot.toneHypothesisSupportFrames() <= 0 || "NONE".equals(snapshot.toneHypothesisSource())) {
                continue;
            }
            observedHypothesisFrames += 1;
            int hypothesisToneHz = snapshot.toneHypothesisFrequencyHz();
            if (Math.abs(snapshot.targetToneFrequencyHz() - hypothesisToneHz) >= disagreementThresholdHz) {
                rawTargetDisagreementFrames += 1;
            }
            if (Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - hypothesisToneHz) >= disagreementThresholdHz) {
                effectiveTrackedDisagreementFrames += 1;
            }
            if (snapshot.representativeLockedToneFrameCount() > 0) {
                int representativeToneHz = snapshot.representativeLockedToneFrequencyHz();
                if (Math.abs(snapshot.targetToneFrequencyHz() - representativeToneHz) <= 30) {
                    rawTargetNearRepresentativeFrames += 1;
                }
                if (Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - representativeToneHz) <= 30) {
                    effectiveTrackedNearRepresentativeFrames += 1;
                }
                if (Math.abs(hypothesisToneHz - representativeToneHz) <= 30) {
                    hypothesisNearRepresentativeFrames += 1;
                }
            }
        }

        return new FrontEndDisagreementProfile(
                detailedProbeResult.probeResult().sourceLabel(),
                disagreementThresholdHz,
                observedHypothesisFrames,
                rawTargetDisagreementFrames,
                effectiveTrackedDisagreementFrames,
                rawTargetNearRepresentativeFrames,
                effectiveTrackedNearRepresentativeFrames,
                hypothesisNearRepresentativeFrames,
                finalSnapshot.representativeCompetitionObservationCount(),
                finalSnapshot.representativeCompetitionTrackedWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisMaxWinStreak(),
                finalSnapshot.activeCenterCompetitionObservationCount(),
                finalSnapshot.activeCenterCompetitionTrackedWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisMaxWinStreak()
        );
    }

    static ForcedToneReplayResult replayForcedTrackedToneDecode(OfflineDetailedProbeResult detailedProbeResult) {
        return replayForcedSnapshotToneDecode(detailedProbeResult, false);
    }

    static ForcedToneReplayResult replayForcedHypothesisToneDecode(OfflineDetailedProbeResult detailedProbeResult) {
        return replayForcedSnapshotToneDecode(detailedProbeResult, true);
    }

    private static ForcedToneReplayResult replayForcedSnapshotToneDecode(
            OfflineDetailedProbeResult detailedProbeResult,
            boolean hypothesisMode
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwToneEvent> replayedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();
        int lastForcedFrequencyHz = 650;

        for (int index = 0; index < detailedProbeResult.frames().size(); index++) {
            AudioFrame frame = detailedProbeResult.frames().get(index);
            CwSignalSnapshot snapshot = detailedProbeResult.frameSignalTraces().get(index).snapshot();
            int forcedFrequencyHz;
            if (hypothesisMode) {
                if (snapshot.toneHypothesisSupportFrames() > 0 && !"NONE".equals(snapshot.toneHypothesisSource())) {
                    forcedFrequencyHz = snapshot.toneHypothesisFrequencyHz();
                    lastForcedFrequencyHz = forcedFrequencyHz;
                } else {
                    forcedFrequencyHz = lastForcedFrequencyHz;
                }
            } else {
                forcedFrequencyHz = snapshot.targetToneFrequencyHz() > 0
                        ? snapshot.targetToneFrequencyHz()
                        : lastForcedFrequencyHz;
                lastForcedFrequencyHz = forcedFrequencyHz;
            }
            List<CwToneEvent> toneEvents = signalProcessor.processForcedToneForTesting(frame, forcedFrequencyHz);
            replayedToneEvents.addAll(toneEvents);
            drainToneEvents(
                    toneEvents,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    replayedTimingEvents,
                    replayedDecodeEvents
            );
        }

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(detailedProbeResult.flushTimestampMs());
        replayedTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, replayedDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(detailedProbeResult.flushTimestampMs()),
                interpreter,
                qsoStateMachine,
                replayedDecodeEvents
        );

        return new ForcedToneReplayResult(
                detailedProbeResult.probeResult().sourceLabel(),
                hypothesisMode ? "HYP" : "TRK",
                lastForcedFrequencyHz,
                sanitize(decoder.snapshot().decodedText()),
                replayedToneEvents,
                replayedTimingEvents,
                replayedDecodeEvents,
                decoder.snapshot(),
                interpreter.snapshot()
        );
    }

    static final class FrameSignalTrace {
        private final long timestampMs;
        private final CwSignalSnapshot snapshot;

        private FrameSignalTrace(long timestampMs, CwSignalSnapshot snapshot) {
            this.timestampMs = timestampMs;
            this.snapshot = snapshot;
        }

        long timestampMs() {
            return timestampMs;
        }

        CwSignalSnapshot snapshot() {
            return snapshot;
        }
    }

    static final class FrontEndDisagreementProfile {
        private final String sourceLabel;
        private final int disagreementThresholdHz;
        private final int observedHypothesisFrames;
        private final int rawTargetDisagreementFrames;
        private final int effectiveTrackedDisagreementFrames;
        private final int rawTargetNearRepresentativeFrames;
        private final int effectiveTrackedNearRepresentativeFrames;
        private final int hypothesisNearRepresentativeFrames;
        private final int representativeCompetitionObservationCount;
        private final int representativeCompetitionTrackedWinFrames;
        private final int representativeCompetitionHypothesisWinFrames;
        private final int representativeCompetitionHypothesisMaxWinStreak;
        private final int activeCenterCompetitionObservationCount;
        private final int activeCenterCompetitionTrackedWinFrames;
        private final int activeCenterCompetitionHypothesisWinFrames;
        private final int activeCenterCompetitionHypothesisMaxWinStreak;

        private FrontEndDisagreementProfile(
                String sourceLabel,
                int disagreementThresholdHz,
                int observedHypothesisFrames,
                int rawTargetDisagreementFrames,
                int effectiveTrackedDisagreementFrames,
                int rawTargetNearRepresentativeFrames,
                int effectiveTrackedNearRepresentativeFrames,
                int hypothesisNearRepresentativeFrames,
                int representativeCompetitionObservationCount,
                int representativeCompetitionTrackedWinFrames,
                int representativeCompetitionHypothesisWinFrames,
                int representativeCompetitionHypothesisMaxWinStreak,
                int activeCenterCompetitionObservationCount,
                int activeCenterCompetitionTrackedWinFrames,
                int activeCenterCompetitionHypothesisWinFrames,
                int activeCenterCompetitionHypothesisMaxWinStreak
        ) {
            this.sourceLabel = sourceLabel;
            this.disagreementThresholdHz = disagreementThresholdHz;
            this.observedHypothesisFrames = observedHypothesisFrames;
            this.rawTargetDisagreementFrames = rawTargetDisagreementFrames;
            this.effectiveTrackedDisagreementFrames = effectiveTrackedDisagreementFrames;
            this.rawTargetNearRepresentativeFrames = rawTargetNearRepresentativeFrames;
            this.effectiveTrackedNearRepresentativeFrames = effectiveTrackedNearRepresentativeFrames;
            this.hypothesisNearRepresentativeFrames = hypothesisNearRepresentativeFrames;
            this.representativeCompetitionObservationCount = representativeCompetitionObservationCount;
            this.representativeCompetitionTrackedWinFrames = representativeCompetitionTrackedWinFrames;
            this.representativeCompetitionHypothesisWinFrames = representativeCompetitionHypothesisWinFrames;
            this.representativeCompetitionHypothesisMaxWinStreak = representativeCompetitionHypothesisMaxWinStreak;
            this.activeCenterCompetitionObservationCount = activeCenterCompetitionObservationCount;
            this.activeCenterCompetitionTrackedWinFrames = activeCenterCompetitionTrackedWinFrames;
            this.activeCenterCompetitionHypothesisWinFrames = activeCenterCompetitionHypothesisWinFrames;
            this.activeCenterCompetitionHypothesisMaxWinStreak = activeCenterCompetitionHypothesisMaxWinStreak;
        }

        int observedHypothesisFrames() {
            return observedHypothesisFrames;
        }

        int rawTargetDisagreementFrames() {
            return rawTargetDisagreementFrames;
        }

        int effectiveTrackedDisagreementFrames() {
            return effectiveTrackedDisagreementFrames;
        }

        int rawTargetNearRepresentativeFrames() {
            return rawTargetNearRepresentativeFrames;
        }

        int effectiveTrackedNearRepresentativeFrames() {
            return effectiveTrackedNearRepresentativeFrames;
        }

        int hypothesisNearRepresentativeFrames() {
            return hypothesisNearRepresentativeFrames;
        }

        int representativeCompetitionObservationCount() {
            return representativeCompetitionObservationCount;
        }

        int representativeCompetitionTrackedWinFrames() {
            return representativeCompetitionTrackedWinFrames;
        }

        int representativeCompetitionHypothesisWinFrames() {
            return representativeCompetitionHypothesisWinFrames;
        }

        int representativeCompetitionHypothesisMaxWinStreak() {
            return representativeCompetitionHypothesisMaxWinStreak;
        }

        int activeCenterCompetitionObservationCount() {
            return activeCenterCompetitionObservationCount;
        }

        int activeCenterCompetitionTrackedWinFrames() {
            return activeCenterCompetitionTrackedWinFrames;
        }

        int activeCenterCompetitionHypothesisWinFrames() {
            return activeCenterCompetitionHypothesisWinFrames;
        }

        int activeCenterCompetitionHypothesisMaxWinStreak() {
            return activeCenterCompetitionHypothesisMaxWinStreak;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%s threshold=%dHz hypFrames=%d rawDisagree=%d effDisagree=%d rawNearRep=%d effNearRep=%d hypNearRep=%d repComp(obs=%d trk=%d hyp=%d maxHyp=%d) actComp(obs=%d trk=%d hyp=%d maxHyp=%d)",
                    sourceLabel,
                    disagreementThresholdHz,
                    observedHypothesisFrames,
                    rawTargetDisagreementFrames,
                    effectiveTrackedDisagreementFrames,
                    rawTargetNearRepresentativeFrames,
                    effectiveTrackedNearRepresentativeFrames,
                    hypothesisNearRepresentativeFrames,
                    representativeCompetitionObservationCount,
                    representativeCompetitionTrackedWinFrames,
                    representativeCompetitionHypothesisWinFrames,
                    representativeCompetitionHypothesisMaxWinStreak,
                    activeCenterCompetitionObservationCount,
                    activeCenterCompetitionTrackedWinFrames,
                    activeCenterCompetitionHypothesisWinFrames,
                    activeCenterCompetitionHypothesisMaxWinStreak
            );
        }
    }

    static final class TrailingWindowRedecodeResult {
        private final int trailingWordCount;
        private final long windowStartTimestampMs;
        private final String decodedText;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;

        private TrailingWindowRedecodeResult(
                int trailingWordCount,
                long windowStartTimestampMs,
                String decodedText,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents
        ) {
            this.trailingWordCount = trailingWordCount;
            this.windowStartTimestampMs = windowStartTimestampMs;
            this.decodedText = decodedText;
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
        }

        int trailingWordCount() {
            return trailingWordCount;
        }

        long windowStartTimestampMs() {
            return windowStartTimestampMs;
        }

        String decodedText() {
            return decodedText;
        }

        CwTimingSnapshot timingSnapshot() {
            return timingSnapshot;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }

        List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }
    }

    static final class ForcedToneReplayResult {
        private final String sourceLabel;
        private final String mode;
        private final int lastForcedFrequencyHz;
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;

        private ForcedToneReplayResult(
                String sourceLabel,
                String mode,
                int lastForcedFrequencyHz,
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot
        ) {
            this.sourceLabel = sourceLabel;
            this.mode = mode;
            this.lastForcedFrequencyHz = lastForcedFrequencyHz;
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%s %s forcedLast=%dHz text=%s tone=%d timing=%d decode=%d",
                    sourceLabel,
                    mode,
                    lastForcedFrequencyHz,
                    decodedText,
                    toneEvents.size(),
                    timingEvents.size(),
                    decodeEvents.size()
            );
        }

        String decodedText() {
            return decodedText;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }
    }
}
