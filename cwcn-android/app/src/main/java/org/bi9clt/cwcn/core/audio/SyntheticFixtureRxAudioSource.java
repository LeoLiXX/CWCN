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
        double sumSquares = 0.0d;
        for (short sample : samples) {
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = samples.length == 0 ? 0.0d : Math.sqrt(sumSquares / samples.length);
        long capturedAtMs = startedAtMs + Math.round(sampleOffset * 1000.0d / SAMPLE_RATE_HZ);
        return new AudioFrame(samples, SAMPLE_RATE_HZ, CHANNEL_COUNT, peak, rms, capturedAtMs);
    }

    private short[] renderWaveform(CwFixtureScenario scenario) {
        List<Segment> segments = buildSegments(scenario);
        List<CwFixtureScenario.ContinuousInterfererProfile> interferers = buildContinuousInterferers(scenario);
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
                double toneFrequencyHz = instantaneousToneFrequencyHz(scenario, absoluteIndex, totalSamples);
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
                waveform[absoluteIndex] = (short) clampToPcm16(Math.round(
                        toneComponent + interfererComponent + noiseComponent
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
                    segments.add(new Segment(true, toneSamples));
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
        return partTimingProfile.extraPauseEveryCharacters() > 0
                && partTimingProfile.extraPauseDotUnits() > 0.0d
                && emittedCharacterCount > 0
                && (emittedCharacterCount % partTimingProfile.extraPauseEveryCharacters()) == 0;
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
        segments.add(new Segment(false, sampleCount));
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

    private double instantaneousToneFrequencyHz(CwFixtureScenario scenario, int sampleIndex, int totalSamples) {
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

    private double toneEdgeScale(CwFixtureScenario scenario, int segmentSampleCount, int segmentSampleIndex) {
        int riseRampSamples = durationToSamples(scenario.riseRampMs());
        int fallRampSamples = durationToSamples(scenario.fallRampMs());
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

        private Segment(boolean toneOn, int sampleCount) {
            this.toneOn = toneOn;
            this.sampleCount = sampleCount;
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
