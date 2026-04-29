package org.bi9clt.cwcn.core.audio;

import android.os.SystemClock;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario.PartTimingProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class SyntheticFixtureRxAudioSource implements RxAudioSource {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int CHANNEL_COUNT = 1;
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PCM_16_MAX = 32767;
    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;
    private static final Map<Character, String> MORSE_MAP = buildMorseMap();

    private volatile Callback callback;
    private volatile State state = State.IDLE;
    private volatile boolean running;
    private volatile CwFixtureScenario scenario = CwFixtureLibrary.defaultScenario();

    private Thread workerThread;

    @Override
    public String id() {
        return "synthetic-fixture";
    }

    @Override
    public String displayName() {
        return "Synthetic Fixture";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public synchronized void setScenario(CwFixtureScenario scenario) {
        if (scenario != null) {
            this.scenario = scenario;
        }
    }

    public CwFixtureScenario scenario() {
        return scenario;
    }

    @Override
    public synchronized void start() {
        if (state == State.RUNNING || state == State.STARTING) {
            return;
        }

        running = true;
        updateState(State.STARTING, "Preparing fixture replay: " + scenario.displayName());
        workerThread = new Thread(this::replayLoop, "cwcn-fixture-source");
        workerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPING) {
            return;
        }
        running = false;
        updateState(State.STOPPING, "Stopping fixture replay.");
        joinWorkerThread();
        if (state != State.ERROR) {
            updateState(State.IDLE, "Fixture replay stopped.");
        }
    }

    @Override
    public synchronized void release() {
        stop();
    }

    private void replayLoop() {
        CwFixtureScenario activeScenario = scenario;
        try {
            short[] waveform = renderWaveform(activeScenario);
            updateState(
                    State.RUNNING,
                    String.format(
                            Locale.US,
                            "Replaying fixture %s (%d samples, %d WPM).",
                            activeScenario.displayName(),
                            waveform.length,
                            activeScenario.wpm()
                    )
            );

            long startedAtMs = SystemClock.elapsedRealtime();
            for (int offset = 0; running && offset < waveform.length; offset += FRAME_SIZE_SAMPLES) {
                int frameLength = Math.min(FRAME_SIZE_SAMPLES, waveform.length - offset);
                short[] samples = Arrays.copyOfRange(waveform, offset, offset + frameLength);
                Callback currentCallback = callback;
                if (currentCallback != null) {
                    currentCallback.onAudioFrame(buildFrame(samples, startedAtMs, offset));
                }
                long frameDurationMs = Math.max(1L, Math.round(frameLength * 1000.0d / SAMPLE_RATE_HZ));
                SystemClock.sleep(frameDurationMs);
            }
        } catch (IllegalArgumentException exception) {
            setErrorState("Fixture replay failed: " + exception.getMessage(), exception);
            return;
        } finally {
            running = false;
            clearWorkerThreadReference();
        }

        if (state != State.ERROR) {
            updateState(State.IDLE, "Fixture replay completed.");
        }
    }

    private AudioFrame buildFrame(short[] samples, long startedAtMs, int sampleOffset) {
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
        long capturedAtMs = startedAtMs + Math.round(sampleOffset * 1000.0d / SAMPLE_RATE_HZ);
        return new AudioFrame(samples, SAMPLE_RATE_HZ, CHANNEL_COUNT, peak, rms, clippedSampleCount, capturedAtMs);
    }

    private short[] renderWaveform(CwFixtureScenario scenario) {
        List<Segment> segments = buildSegments(scenario);
        List<CwFixtureScenario.ContinuousInterfererProfile> interferers = buildContinuousInterferers(scenario);
        List<KeyedInterfererRenderState> keyedInterferers = buildKeyedInterfererRenderStates(scenario);
        int totalSamples = 0;
        for (Segment segment : segments) {
            totalSamples += segment.sampleCount;
        }
        short[] waveform = new short[totalSamples];
        Random random = new Random(scenario.id().hashCode());
        double tonePhase = 0.0d;
        double[] interfererPhases = new double[interferers.size()];
        int absoluteIndex = 0;
        for (Segment segment : segments) {
            for (int i = 0; i < segment.sampleCount; i++) {
                double toneFrequencyHz = instantaneousToneFrequencyHz(
                        scenario,
                        segment,
                        i,
                        absoluteIndex,
                        totalSamples
                );
                double tonePhaseStep = 2.0d * Math.PI * toneFrequencyHz / SAMPLE_RATE_HZ;
                double toneComponent = 0.0d;
                if (segment.toneOn) {
                    double qsbScale = qsbScale(scenario, absoluteIndex);
                    double edgeScale = toneEdgeScale(scenario, segment.sampleCount, i);
                    toneComponent = Math.sin(tonePhase)
                            * scenario.toneAmplitude()
                            * edgeScale
                            * qsbScale;
                }
                double interfererComponent = 0.0d;
                for (int interfererIndex = 0; interfererIndex < interferers.size(); interfererIndex++) {
                    CwFixtureScenario.ContinuousInterfererProfile interferer = interferers.get(interfererIndex);
                    double interfererFrequencyHz = instantaneousInterfererFrequencyHz(
                            interferer,
                            absoluteIndex,
                            totalSamples
                    );
                    double interfererPhaseStep = 2.0d * Math.PI * interfererFrequencyHz / SAMPLE_RATE_HZ;
                    if (interfererPhaseStep <= 0.0d) {
                        continue;
                    }
                    interfererComponent += Math.sin(interfererPhases[interfererIndex])
                            * interferer.toneAmplitude()
                            * interfererActivityScale(interferer, absoluteIndex);
                    interfererPhases[interfererIndex] += interfererPhaseStep;
                }
                double noiseComponent = (random.nextDouble() * 2.0d - 1.0d) * scenario.noiseAmplitude();
                double keyedInterfererComponent = 0.0d;
                for (KeyedInterfererRenderState keyedInterferer : keyedInterferers) {
                    keyedInterfererComponent += keyedInterferer.nextSampleComponent(absoluteIndex, totalSamples);
                }
                waveform[absoluteIndex] = (short) clampToPcm16(Math.round(
                        toneComponent + interfererComponent + keyedInterfererComponent + noiseComponent
                ));
                tonePhase += tonePhaseStep;
                absoluteIndex += 1;
            }
        }
        return waveform;
    }

    List<AudioFrame> renderFramesForTesting(CwFixtureScenario scenario) {
        short[] waveform = renderWaveform(scenario);
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long startedAtMs = 0L;
        for (int offset = 0; offset < waveform.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, waveform.length - offset);
            short[] samples = Arrays.copyOfRange(waveform, offset, offset + frameLength);
            frames.add(buildFrame(samples, startedAtMs, offset));
        }
        return frames;
    }

    RenderedFixtureFrames renderFramesWithTruthForTesting(CwFixtureScenario scenario) {
        short[] waveform = renderWaveform(scenario);
        ArrayList<AudioFrame> frames = new ArrayList<>();
        ArrayList<FrameToneTruth> truths = new ArrayList<>();
        long startedAtMs = 0L;
        for (int offset = 0; offset < waveform.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, waveform.length - offset);
            short[] samples = Arrays.copyOfRange(waveform, offset, offset + frameLength);
            frames.add(buildFrame(samples, startedAtMs, offset));
        }
        buildFrameTruthsForTesting(scenario, waveform.length, truths);
        return new RenderedFixtureFrames(frames, truths);
    }

    private void buildFrameTruthsForTesting(
            CwFixtureScenario scenario,
            int totalSamples,
            List<FrameToneTruth> truths
    ) {
        List<Segment> segments = buildSegments(scenario);
        int frameIndex = 0;
        int frameSampleCount = Math.min(FRAME_SIZE_SAMPLES, Math.max(0, totalSamples));
        int frameConsumedSamples = 0;
        int frameActiveSamples = 0;
        double frameToneFrequencySumHz = 0.0d;
        int absoluteIndex = 0;

        for (Segment segment : segments) {
            for (int segmentSampleIndex = 0; segmentSampleIndex < segment.sampleCount; segmentSampleIndex++) {
                if (segment.toneOn) {
                    frameActiveSamples += 1;
                    frameToneFrequencySumHz += instantaneousToneFrequencyHz(
                            scenario,
                            segment,
                            segmentSampleIndex,
                            absoluteIndex,
                            totalSamples
                    );
                }
                absoluteIndex += 1;
                frameConsumedSamples += 1;
                if (frameConsumedSamples >= frameSampleCount) {
                    truths.add(buildFrameToneTruth(
                            frameIndex,
                            absoluteIndex - frameConsumedSamples,
                            frameSampleCount,
                            frameActiveSamples,
                            frameToneFrequencySumHz
                    ));
                    frameIndex += 1;
                    frameConsumedSamples = 0;
                    frameActiveSamples = 0;
                    frameToneFrequencySumHz = 0.0d;
                    frameSampleCount = Math.min(FRAME_SIZE_SAMPLES, Math.max(0, totalSamples - absoluteIndex));
                }
            }
        }

        if (frameConsumedSamples > 0) {
            truths.add(buildFrameToneTruth(
                    frameIndex,
                    absoluteIndex - frameConsumedSamples,
                    frameConsumedSamples,
                    frameActiveSamples,
                    frameToneFrequencySumHz
            ));
        }
    }

    private FrameToneTruth buildFrameToneTruth(
            int frameIndex,
            int frameSampleOffset,
            int frameSampleCount,
            int activeSampleCount,
            double toneFrequencySumHz
    ) {
        double expectedToneFrequencyHz = activeSampleCount <= 0
                ? 0.0d
                : toneFrequencySumHz / activeSampleCount;
        long frameStartTimestampMs = Math.round(frameSampleOffset * 1000.0d / SAMPLE_RATE_HZ);
        return new FrameToneTruth(
                frameIndex,
                frameStartTimestampMs,
                frameSampleCount,
                activeSampleCount,
                expectedToneFrequencyHz
        );
    }

    private List<Segment> buildSegments(CwFixtureScenario scenario) {
        List<String> messageParts = scenario.messageParts();
        if (messageParts == null || messageParts.isEmpty()) {
            throw new IllegalArgumentException("Scenario text is empty.");
        }

        List<Segment> segments = new ArrayList<>();
        TimingProfileState timingProfileState = new TimingProfileState(scenario.id().hashCode());
        appendGap(
                segments,
                adjustGapSamples(
                        scenario,
                        PartTimingProfile.defaultProfile(),
                        timingProfileState,
                        scenario.leadInMs()
                )
        );
        for (int partIndex = 0; partIndex < messageParts.size(); partIndex++) {
            PartTimingProfile partTimingProfile = scenario.timingProfileForPart(partIndex);
            appendMessageSegments(
                    segments,
                    messageParts.get(partIndex),
                    scenario,
                    partTimingProfile,
                    timingProfileState
            );
            if (partIndex < messageParts.size() - 1) {
                appendGap(
                        segments,
                        adjustGapSamples(
                                scenario,
                                partTimingProfile,
                                timingProfileState,
                                scenario.interMessageGapMs()
                        )
                );
            }
        }

        appendGap(
                segments,
                adjustGapSamples(
                        scenario,
                        PartTimingProfile.defaultProfile(),
                        timingProfileState,
                        scenario.tailMs()
                )
        );
        return segments;
    }

    private List<KeyedInterfererRenderState> buildKeyedInterfererRenderStates(CwFixtureScenario scenario) {
        ArrayList<KeyedInterfererRenderState> states = new ArrayList<>();
        for (CwFixtureScenario.KeyedInterfererProfile interferer : scenario.keyedInterferers()) {
            List<Segment> segments = buildKeyedInterfererSegments(interferer);
            if (!segments.isEmpty()) {
                states.add(new KeyedInterfererRenderState(interferer, segments));
            }
        }
        return states;
    }

    private List<Segment> buildKeyedInterfererSegments(CwFixtureScenario.KeyedInterfererProfile interferer) {
        ArrayList<Segment> segments = new ArrayList<>();
        if (interferer == null || !interferer.hasDecodableMessage()) {
            return segments;
        }
        appendGap(segments, durationToSamples(interferer.startOffsetMs()));
        List<String> parts = interferer.messageParts();
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            appendKeyedInterfererMessageSegments(segments, parts.get(partIndex), interferer);
            if (partIndex < parts.size() - 1) {
                appendGap(segments, durationToSamples(interferer.interMessageGapMs()));
            }
        }
        return segments;
    }

    private void appendKeyedInterfererMessageSegments(
            List<Segment> segments,
            String message,
            CwFixtureScenario.KeyedInterfererProfile interferer
    ) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String[] words = message.toUpperCase(Locale.US).trim().split("\\s+");
        int dotSamples = durationToSamples(1200.0d / Math.max(1.0d, interferer.wpm()));
        int emittedCharacterCount = 0;
        int totalDecodableCharacters = countDecodableCharacters(words);
        if (totalDecodableCharacters <= 0) {
            return;
        }

        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            if (word.isEmpty()) {
                continue;
            }
            for (int charIndex = 0; charIndex < word.length(); charIndex++) {
                char current = word.charAt(charIndex);
                String morse = MORSE_MAP.get(current);
                if (morse == null) {
                    continue;
                }
                for (int symbolIndex = 0; symbolIndex < morse.length(); symbolIndex++) {
                    char symbol = morse.charAt(symbolIndex);
                    segments.add(new Segment(
                            true,
                            symbol == '-' ? dotSamples * 3 : dotSamples,
                            true,
                            interferer.toneFrequencyHz(),
                            interferer.toneFrequencyHz()
                    ));
                    if (symbolIndex < morse.length() - 1) {
                        appendGap(segments, dotSamples);
                    }
                }

                emittedCharacterCount += 1;
                if (emittedCharacterCount >= totalDecodableCharacters) {
                    continue;
                }
                if (hasLaterDecodableCharacter(word, charIndex + 1)) {
                    appendGap(segments, dotSamples * 3);
                } else if (hasLaterDecodableWord(words, wordIndex + 1)) {
                    appendGap(segments, dotSamples * (isHandoffWord(nextDecodableWord(words, wordIndex + 1)) ? 8 : 7));
                }
            }
        }
    }

    private void appendMessageSegments(
            List<Segment> segments,
            String message,
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile,
            TimingProfileState timingProfileState
    ) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String[] words = message.toUpperCase(Locale.US).trim().split("\\s+");
        int totalDecodableCharacters = countDecodableCharacters(words);
        if (totalDecodableCharacters <= 0) {
            return;
        }

        int emittedCharacterCount = 0;
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            if (word.isEmpty()) {
                continue;
            }

            for (int charIndex = 0; charIndex < word.length(); charIndex++) {
                char current = word.charAt(charIndex);
                String morse = MORSE_MAP.get(current);
                if (morse == null) {
                    continue;
                }

                double progress = interpolationProgress(emittedCharacterCount, totalDecodableCharacters);
                double dotMs = currentDotMs(scenario, partTimingProfile, progress);

                for (int symbolIndex = 0; symbolIndex < morse.length(); symbolIndex++) {
                    char symbol = morse.charAt(symbolIndex);
                    int toneSamples = adjustToneSamples(
                            scenario,
                            partTimingProfile,
                            timingProfileState,
                            symbol == '-' ? dotMs * 3.0d : dotMs,
                            symbol == '.'
                    );
                    segments.add(new Segment(
                            true,
                            toneSamples,
                            partTimingProfile != null && partTimingProfile.hasToneOverride(),
                            effectiveToneFrequencyHz(scenario, partTimingProfile),
                            effectiveEndToneFrequencyHz(scenario, partTimingProfile)
                    ));
                    if (symbolIndex < morse.length() - 1) {
                        appendGap(
                                segments,
                                adjustGapSamples(scenario, partTimingProfile, timingProfileState, dotMs)
                        );
                    }
                }

                emittedCharacterCount += 1;
                if (emittedCharacterCount >= totalDecodableCharacters) {
                    continue;
                }

                if (hasLaterDecodableCharacter(word, charIndex + 1)) {
                    appendGap(
                            segments,
                            adjustGapSamples(
                                    scenario,
                                    partTimingProfile,
                                    timingProfileState,
                                    dotMs * 3.0d * partTimingProfile.letterGapScale()
                            )
                    );
                } else if (hasLaterDecodableWord(words, wordIndex + 1)) {
                    double wordGapScale = partTimingProfile.wordGapScale();
                    if (isHandoffWord(nextDecodableWord(words, wordIndex + 1))) {
                        wordGapScale *= partTimingProfile.handoffGapScale();
                    }
                    appendGap(
                            segments,
                            adjustGapSamples(
                                    scenario,
                                    partTimingProfile,
                                    timingProfileState,
                                    dotMs * 7.0d * wordGapScale
                            )
                    );
                }

                if (shouldInsertExtraPause(partTimingProfile, emittedCharacterCount)) {
                    double pauseDotMs = currentDotMs(
                            scenario,
                            partTimingProfile,
                            interpolationProgress(emittedCharacterCount, totalDecodableCharacters)
                    );
                    appendGap(
                            segments,
                            adjustGapSamples(
                                    scenario,
                                    partTimingProfile,
                                    timingProfileState,
                                    pauseDotMs * partTimingProfile.extraPauseDotUnits()
                            )
                    );
                }
            }
        }
    }

    private int countDecodableCharacters(String[] words) {
        int count = 0;
        for (String word : words) {
            for (int index = 0; index < word.length(); index++) {
                if (MORSE_MAP.containsKey(word.charAt(index))) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private boolean hasLaterDecodableCharacter(String word, int startIndex) {
        for (int index = startIndex; index < word.length(); index++) {
            if (MORSE_MAP.containsKey(word.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLaterDecodableWord(String[] words, int startIndex) {
        return nextDecodableWord(words, startIndex) != null;
    }

    private String nextDecodableWord(String[] words, int startIndex) {
        for (int index = startIndex; index < words.length; index++) {
            if (wordHasDecodableCharacter(words[index])) {
                return words[index];
            }
        }
        return null;
    }

    private boolean wordHasDecodableCharacter(String word) {
        return hasLaterDecodableCharacter(word, 0);
    }

    private boolean isHandoffWord(String word) {
        return "K".equals(word) || "KN".equals(word) || "BK".equals(word);
    }

    private double interpolationProgress(int emittedCharacterCount, int totalDecodableCharacters) {
        if (totalDecodableCharacters <= 1) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, emittedCharacterCount / (double) (totalDecodableCharacters - 1)));
    }

    private double currentDotMs(
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile,
            double progress
    ) {
        double currentWpmScale = interpolate(partTimingProfile.wpmScale(), partTimingProfile.endWpmScale(), progress);
        return 1200.0d / Math.max(1.0d, scenario.wpm() * currentWpmScale);
    }

    private double interpolate(double start, double end, double progress) {
        return start + ((end - start) * progress);
    }

    private boolean shouldInsertExtraPause(PartTimingProfile partTimingProfile, int emittedCharacterCount) {
        if (partTimingProfile.extraPauseDotUnits() <= 0.0d || emittedCharacterCount <= 0) {
            return false;
        }
        if (partTimingProfile.extraPauseEveryCharacters() > 0
                && (emittedCharacterCount % partTimingProfile.extraPauseEveryCharacters()) == 0) {
            return true;
        }
        for (Integer pauseOffset : partTimingProfile.extraPauseCharacterOffsets()) {
            if (pauseOffset != null && pauseOffset == emittedCharacterCount) {
                return true;
            }
        }
        return false;
    }

    private void appendGap(List<Segment> segments, int sampleCount) {
        if (sampleCount <= 0) {
            return;
        }
        if (!segments.isEmpty()) {
            Segment last = segments.get(segments.size() - 1);
            if (!last.toneOn) {
                last.sampleCount += sampleCount;
                return;
            }
        }
        segments.add(new Segment(false, sampleCount, false, 0.0d, 0.0d));
    }

    private int adjustToneSamples(
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile,
            TimingProfileState timingProfileState,
            double baseDurationMs,
            boolean isDotTone
    ) {
        double scaledDurationMs = baseDurationMs;
        double dotSwingDepth = partTimingProfile.effectiveDotSwingDepth(scenario.dotSwingDepth());
        if (isDotTone && dotSwingDepth > 0.0d) {
            scaledDurationMs *= dotSwingScale(timingProfileState, dotSwingDepth);
        }
        double jitterDepth = partTimingProfile.effectiveTimingJitterDepth(scenario.timingJitterDepth());
        if (isDotTone) {
            jitterDepth += partTimingProfile.dotJitterBoost();
        }
        scaledDurationMs *= jitterScale(jitterDepth, timingProfileState.nextToneJitterUnit());
        return durationToSamples(scaledDurationMs);
    }

    private int adjustGapSamples(
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile,
            TimingProfileState timingProfileState,
            double baseDurationMs
    ) {
        double jitterDepth = partTimingProfile.effectiveTimingJitterDepth(scenario.timingJitterDepth());
        return durationToSamples(baseDurationMs * jitterScale(jitterDepth, timingProfileState.nextGapJitterUnit()));
    }

    private double dotSwingScale(TimingProfileState timingProfileState, double dotSwingDepth) {
        double boundedDepth = Math.max(0.0d, Math.min(0.45d, dotSwingDepth));
        boolean longDot = (timingProfileState.nextDotToneIndex() % 2) == 1;
        return longDot ? (1.0d + boundedDepth) : Math.max(0.55d, 1.0d - boundedDepth);
    }

    private double jitterScale(double jitterDepth, double jitterUnit) {
        double boundedDepth = Math.max(0.0d, Math.min(0.45d, jitterDepth));
        return Math.max(0.45d, 1.0d + (jitterUnit * boundedDepth));
    }

    private double deterministicJitterUnit(int seed, int eventIndex) {
        int mixed = seed ^ (eventIndex * 0x45d9f3b);
        mixed ^= (mixed >>> 16);
        mixed *= 0x45d9f3b;
        mixed ^= (mixed >>> 16);
        int bucket = Math.floorMod(mixed, 2001);
        return (bucket - 1000) / 1000.0d;
    }

    private int durationToSamples(double durationMs) {
        return Math.max(1, (int) Math.round(durationMs * SAMPLE_RATE_HZ / 1000.0d));
    }

    private double qsbScale(CwFixtureScenario scenario, int sampleIndex) {
        if (scenario.qsbDepth() <= 0.0d || scenario.qsbCycleMs() <= 0) {
            return 1.0d;
        }
        double radians = 2.0d * Math.PI * sampleIndex * 1000.0d / (SAMPLE_RATE_HZ * scenario.qsbCycleMs());
        double fade = (Math.sin(radians) + 1.0d) * 0.5d;
        return 1.0d - (scenario.qsbDepth() * fade);
    }

    private double effectiveToneFrequencyHz(
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile
    ) {
        if (partTimingProfile != null && partTimingProfile.toneFrequencyOverrideHz() != null) {
            return partTimingProfile.toneFrequencyOverrideHz();
        }
        return scenario.toneFrequencyHz();
    }

    private double effectiveEndToneFrequencyHz(
            CwFixtureScenario scenario,
            PartTimingProfile partTimingProfile
    ) {
        if (partTimingProfile != null && partTimingProfile.endToneFrequencyOverrideHz() != null) {
            return partTimingProfile.endToneFrequencyOverrideHz();
        }
        return scenario.toneFrequencyHz();
    }

    private double instantaneousToneFrequencyHz(
            CwFixtureScenario scenario,
            Segment segment,
            int segmentSampleIndex,
            int sampleIndex,
            int totalSamples
    ) {
        if (segment != null && segment.hasToneFrequencyOverride()) {
            if (Math.abs(segment.endToneFrequencyHz - segment.startToneFrequencyHz) < 0.0001d || segment.sampleCount <= 1) {
                return segment.startToneFrequencyHz;
            }
            double segmentProgress = Math.max(
                    0.0d,
                    Math.min(1.0d, segmentSampleIndex / (double) (segment.sampleCount - 1))
            );
            return Math.max(
                    1.0d,
                    segment.startToneFrequencyHz
                            + ((segment.endToneFrequencyHz - segment.startToneFrequencyHz) * segmentProgress)
            );
        }
        if (Math.abs(scenario.toneDriftHz()) < 0.0001d || totalSamples <= 1) {
            return scenario.toneFrequencyHz();
        }
        double progress = Math.max(0.0d, Math.min(1.0d, sampleIndex / (double) (totalSamples - 1)));
        return Math.max(1.0d, scenario.toneFrequencyHz() + (scenario.toneDriftHz() * progress));
    }

    private double instantaneousInterfererFrequencyHz(
            CwFixtureScenario scenario,
            int sampleIndex,
            int totalSamples
    ) {
        if (scenario.interfererToneFrequencyHz() <= 0) {
            return 0.0d;
        }
        return instantaneousInterfererFrequencyHz(
                new CwFixtureScenario.ContinuousInterfererProfile(
                        scenario.interfererToneFrequencyHz(),
                        scenario.interfererToneAmplitude(),
                        scenario.interfererToneDriftHz()
                ),
                sampleIndex,
                totalSamples
        );
    }

    private double instantaneousInterfererFrequencyHz(
            CwFixtureScenario.ContinuousInterfererProfile interferer,
            int sampleIndex,
            int totalSamples
    ) {
        if (interferer == null || interferer.toneFrequencyHz() <= 0) {
            return 0.0d;
        }
        if (Math.abs(interferer.toneDriftHz()) < 0.0001d || totalSamples <= 1) {
            return interferer.toneFrequencyHz();
        }
        double progress = Math.max(0.0d, Math.min(1.0d, sampleIndex / (double) (totalSamples - 1)));
        return Math.max(1.0d, interferer.toneFrequencyHz() + (interferer.toneDriftHz() * progress));
    }

    private double instantaneousKeyedInterfererFrequencyHz(
            CwFixtureScenario.KeyedInterfererProfile interferer,
            int sampleIndex,
            int totalSamples
    ) {
        if (interferer == null || interferer.toneFrequencyHz() <= 0) {
            return 0.0d;
        }
        if (Math.abs(interferer.toneDriftHz()) < 0.0001d || totalSamples <= 1) {
            return interferer.toneFrequencyHz();
        }
        double progress = Math.max(0.0d, Math.min(1.0d, sampleIndex / (double) (totalSamples - 1)));
        return Math.max(1.0d, interferer.toneFrequencyHz() + (interferer.toneDriftHz() * progress));
    }

    private List<CwFixtureScenario.ContinuousInterfererProfile> buildContinuousInterferers(CwFixtureScenario scenario) {
        ArrayList<CwFixtureScenario.ContinuousInterfererProfile> interferers = new ArrayList<>();
        if (scenario.interfererToneAmplitude() > 0 && scenario.interfererToneFrequencyHz() > 0) {
            interferers.add(new CwFixtureScenario.ContinuousInterfererProfile(
                    scenario.interfererToneFrequencyHz(),
                    scenario.interfererToneAmplitude(),
                    scenario.interfererToneDriftHz()
            ));
        }
        interferers.addAll(scenario.additionalInterferers());
        return interferers;
    }

    private double interfererActivityScale(
            CwFixtureScenario.ContinuousInterfererProfile interferer,
            int absoluteSampleIndex
    ) {
        if (interferer == null || !interferer.isBursting()) {
            return 1.0d;
        }
        double elapsedMs = (absoluteSampleIndex * 1000.0d / SAMPLE_RATE_HZ) + interferer.burstOffsetMs();
        elapsedMs += burstWobbleOffsetMs(interferer, elapsedMs);
        double cycleMs = interferer.burstOnMs() + interferer.burstOffMs();
        if (cycleMs <= 0.0d) {
            return 1.0d;
        }
        double cyclePositionMs = elapsedMs % cycleMs;
        if (cyclePositionMs < 0.0d) {
            cyclePositionMs += cycleMs;
        }
        if (cyclePositionMs >= interferer.burstOnMs()) {
            return 0.0d;
        }

        double rampMs = Math.min(6.0d, interferer.burstOnMs() / 4.0d);
        if (rampMs <= 0.5d) {
            return 1.0d;
        }
        double attackScale = Math.min(1.0d, cyclePositionMs / rampMs);
        double releaseScale = Math.min(1.0d, (interferer.burstOnMs() - cyclePositionMs) / rampMs);
        return smoothRamp(Math.min(attackScale, releaseScale));
    }

    private double burstWobbleOffsetMs(
            CwFixtureScenario.ContinuousInterfererProfile interferer,
            double elapsedMs
    ) {
        if (interferer == null || !interferer.hasBurstWobble()) {
            return 0.0d;
        }
        double cycleMs = interferer.burstOnMs() + interferer.burstOffMs();
        if (cycleMs <= 0.0d) {
            return 0.0d;
        }
        double primaryRadians = (2.0d * Math.PI * elapsedMs) / interferer.burstWobbleCycleMs();
        double phaseOffset = (2.0d * Math.PI * interferer.burstOffsetMs()) / Math.max(1.0d, cycleMs);
        double normalizedWobble = (
                Math.sin(primaryRadians + phaseOffset)
                        + (0.55d * Math.sin((primaryRadians * 1.89d) + (phaseOffset * 0.7d)))
                        + (0.30d * Math.sin((primaryRadians * 0.63d) + (phaseOffset * 1.6d)))
        ) / 1.85d;
        return normalizedWobble * cycleMs * interferer.burstWobbleDepth();
    }

    private double toneEdgeScale(CwFixtureScenario scenario, int segmentSampleCount, int segmentSampleIndex) {
        return toneEdgeScale(
                scenario.riseRampMs(),
                scenario.fallRampMs(),
                segmentSampleCount,
                segmentSampleIndex
        );
    }

    private double toneEdgeScale(
            int riseRampMs,
            int fallRampMs,
            int segmentSampleCount,
            int segmentSampleIndex
    ) {
        int riseRampSamples = durationToSamples(riseRampMs);
        int fallRampSamples = durationToSamples(fallRampMs);
        double scale = 1.0d;
        if (riseRampSamples > 1) {
            double riseProgress = Math.min(1.0d, segmentSampleIndex / (double) riseRampSamples);
            scale = Math.min(scale, smoothRamp(riseProgress));
        }
        if (fallRampSamples > 1) {
            int tailSamples = Math.max(0, segmentSampleCount - 1 - segmentSampleIndex);
            double fallProgress = Math.min(1.0d, tailSamples / (double) fallRampSamples);
            scale = Math.min(scale, smoothRamp(fallProgress));
        }
        return Math.max(0.0d, Math.min(1.0d, scale));
    }

    private double smoothRamp(double progress) {
        double clamped = Math.max(0.0d, Math.min(1.0d, progress));
        return 0.5d - (0.5d * Math.cos(Math.PI * clamped));
    }

    private int clampToPcm16(long value) {
        return (int) Math.max(-PCM_16_MAX, Math.min(PCM_16_MAX, value));
    }

    private void updateState(State newState, String detail) {
        state = newState;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(newState, detail);
        }
    }

    private void setErrorState(String message, Throwable throwable) {
        state = State.ERROR;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(State.ERROR, message);
            currentCallback.onError(message, throwable);
        }
    }

    private synchronized void joinWorkerThread() {
        if (workerThread == null) {
            return;
        }
        try {
            workerThread.join(500);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            workerThread = null;
        }
    }

    private synchronized void clearWorkerThreadReference() {
        workerThread = null;
    }

    private static Map<Character, String> buildMorseMap() {
        HashMap<Character, String> map = new HashMap<>();
        map.put('A', ".-");
        map.put('B', "-...");
        map.put('C', "-.-.");
        map.put('D', "-..");
        map.put('E', ".");
        map.put('F', "..-.");
        map.put('G', "--.");
        map.put('H', "....");
        map.put('I', "..");
        map.put('J', ".---");
        map.put('K', "-.-");
        map.put('L', ".-..");
        map.put('M', "--");
        map.put('N', "-.");
        map.put('O', "---");
        map.put('P', ".--.");
        map.put('Q', "--.-");
        map.put('R', ".-.");
        map.put('S', "...");
        map.put('T', "-");
        map.put('U', "..-");
        map.put('V', "...-");
        map.put('W', ".--");
        map.put('X', "-..-");
        map.put('Y', "-.--");
        map.put('Z', "--..");
        map.put('0', "-----");
        map.put('1', ".----");
        map.put('2', "..---");
        map.put('3', "...--");
        map.put('4', "....-");
        map.put('5', ".....");
        map.put('6', "-....");
        map.put('7', "--...");
        map.put('8', "---..");
        map.put('9', "----.");
        map.put('?', "..--..");
        map.put('/', "-..-.");
        map.put('.', ".-.-.-");
        map.put(',', "--..--");
        map.put('=', "-...-");
        return map;
    }

    private static final class Segment {
        private final boolean toneOn;
        private int sampleCount;
        private final boolean useToneFrequencyOverride;
        private final double startToneFrequencyHz;
        private final double endToneFrequencyHz;

        private Segment(
                boolean toneOn,
                int sampleCount,
                boolean useToneFrequencyOverride,
                double startToneFrequencyHz,
                double endToneFrequencyHz
        ) {
            this.toneOn = toneOn;
            this.sampleCount = sampleCount;
            this.useToneFrequencyOverride = useToneFrequencyOverride;
            this.startToneFrequencyHz = startToneFrequencyHz;
            this.endToneFrequencyHz = endToneFrequencyHz;
        }

        private boolean hasToneFrequencyOverride() {
            return toneOn && useToneFrequencyOverride && startToneFrequencyHz > 0.0d;
        }
    }

    static final class RenderedFixtureFrames {
        private final List<AudioFrame> frames;
        private final List<FrameToneTruth> toneTruths;

        private RenderedFixtureFrames(List<AudioFrame> frames, List<FrameToneTruth> toneTruths) {
            this.frames = frames;
            this.toneTruths = toneTruths;
        }

        List<AudioFrame> frames() {
            return frames;
        }

        List<FrameToneTruth> toneTruths() {
            return toneTruths;
        }
    }

    static final class FrameToneTruth {
        private final int frameIndex;
        private final long frameStartTimestampMs;
        private final int sampleCount;
        private final int activeSampleCount;
        private final double expectedToneFrequencyHz;

        private FrameToneTruth(
                int frameIndex,
                long frameStartTimestampMs,
                int sampleCount,
                int activeSampleCount,
                double expectedToneFrequencyHz
        ) {
            this.frameIndex = frameIndex;
            this.frameStartTimestampMs = frameStartTimestampMs;
            this.sampleCount = sampleCount;
            this.activeSampleCount = activeSampleCount;
            this.expectedToneFrequencyHz = expectedToneFrequencyHz;
        }

        int frameIndex() {
            return frameIndex;
        }

        long frameStartTimestampMs() {
            return frameStartTimestampMs;
        }

        int sampleCount() {
            return sampleCount;
        }

        int activeSampleCount() {
            return activeSampleCount;
        }

        double activeSampleRatio() {
            if (sampleCount <= 0) {
                return 0.0d;
            }
            return Math.max(0.0d, Math.min(1.0d, activeSampleCount / (double) sampleCount));
        }

        boolean toneActive() {
            return activeSampleCount > 0 && expectedToneFrequencyHz > 0.0d;
        }

        double expectedToneFrequencyHz() {
            return expectedToneFrequencyHz;
        }
    }

    private final class KeyedInterfererRenderState {
        private final CwFixtureScenario.KeyedInterfererProfile profile;
        private final List<Segment> segments;
        private int segmentIndex;
        private int segmentSampleIndex;
        private double phase;

        private KeyedInterfererRenderState(
                CwFixtureScenario.KeyedInterfererProfile profile,
                List<Segment> segments
        ) {
            this.profile = profile;
            this.segments = segments;
        }

        private double nextSampleComponent(int absoluteSampleIndex, int totalSamples) {
            if (segmentIndex >= segments.size()) {
                return 0.0d;
            }
            Segment segment = segments.get(segmentIndex);
            double component = 0.0d;
            if (segment.toneOn) {
                double frequencyHz = instantaneousFrequencyHz(absoluteSampleIndex, totalSamples);
                double phaseStep = 2.0d * Math.PI * frequencyHz / SAMPLE_RATE_HZ;
                double edgeScale = toneEdgeScale(
                        profile.riseRampMs(),
                        profile.fallRampMs(),
                        segment.sampleCount,
                        segmentSampleIndex
                );
                component = Math.sin(phase) * profile.toneAmplitude() * edgeScale;
                phase += phaseStep;
            }
            advance();
            return component;
        }

        private double instantaneousFrequencyHz(int absoluteSampleIndex, int totalSamples) {
            if (Math.abs(profile.toneDriftHz()) < 0.0001d || totalSamples <= 1) {
                return profile.toneFrequencyHz();
            }
            double progress = Math.max(0.0d, Math.min(1.0d, absoluteSampleIndex / (double) (totalSamples - 1)));
            return Math.max(1.0d, profile.toneFrequencyHz() + (profile.toneDriftHz() * progress));
        }

        private void advance() {
            segmentSampleIndex += 1;
            while (segmentIndex < segments.size() && segmentSampleIndex >= segments.get(segmentIndex).sampleCount) {
                segmentIndex += 1;
                segmentSampleIndex = 0;
            }
        }
    }

    private final class TimingProfileState {
        private final int seed;
        private int toneEventIndex;
        private int gapEventIndex;
        private int dotToneIndex;

        private TimingProfileState(int seed) {
            this.seed = seed;
        }

        private double nextToneJitterUnit() {
            double unit = deterministicJitterUnit(seed ^ 0x51a7, toneEventIndex);
            toneEventIndex += 1;
            return unit;
        }

        private double nextGapJitterUnit() {
            double unit = deterministicJitterUnit(seed ^ 0x2f31, gapEventIndex);
            gapEventIndex += 1;
            return unit;
        }

        private int nextDotToneIndex() {
            int current = dotToneIndex;
            dotToneIndex += 1;
            return current;
        }
    }
}
