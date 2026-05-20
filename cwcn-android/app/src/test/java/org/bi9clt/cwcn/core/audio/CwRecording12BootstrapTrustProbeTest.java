package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12BootstrapTrustProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int MAX_TIMING_LINES = 40;
    private static final int MAX_STABLE_LINES = 48;
    private static final int MAX_BOOTSTRAP_LINES = 48;

    @Test
    public void printRecording12TrustBootstrapTimeline() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-bootstrap-trust",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult softDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithSoftStableBootstrapCandidate(
                        "recording12-bootstrap-trust-soft",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        long turnStartMs = firstTurnStartMs(detailed.turnTransitionTraces());
        LocalAudioDecodeTestSupport.TimingStateTrace modelTrustTrace =
                firstTrustedTraceAtOrAfter(detailed.timingStateTraces(), turnStartMs);
        long modelTrustMs = modelTrustTrace == null ? -1L : modelTrustTrace.timestampMs();
        DebugSnapshot trustDebug = modelTrustTrace == null ? null : modelTrustTrace.debugSnapshot();
        long anchorTrustMs = firstAnchorTrustTimestampMs(detailed, turnStartMs);
        long softTurnStartMs = firstTurnStartMs(softDetailed.turnTransitionTraces());
        LocalAudioDecodeTestSupport.TimingStateTrace softModelTrustTrace =
                firstTrustedTraceAtOrAfter(softDetailed.timingStateTraces(), softTurnStartMs);
        long softModelTrustMs = softModelTrustTrace == null ? -1L : softModelTrustTrace.timestampMs();
        DebugSnapshot softTrustDebug =
                softModelTrustTrace == null ? null : softModelTrustTrace.debugSnapshot();
        long softAnchorTrustMs = firstAnchorTrustTimestampMs(softDetailed, softTurnStartMs);
        SoftBootstrapSimulation baselineSimulation =
                simulateSoftMixedBootstrap(detailed, turnStartMs, false);
        SoftBootstrapSimulation softSimulation =
                simulateSoftMixedBootstrap(detailed, turnStartMs, true);

        System.out.println("==== recording12 bootstrap trust timeline ====");
        System.out.println("final=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println("final-soft=" + sanitize(softDetailed.probeResult().decodedText()));
        System.out.println(String.format(
                Locale.US,
                "turnStart=%d modelTrust=%d anchorTrust=%d reason=%s trustedDot=%.1f rawWpm=%.1f stableWpm=%.1f",
                turnStartMs,
                modelTrustMs,
                anchorTrustMs,
                trustDebug == null ? "none" : safe(trustDebug.lastTrustedUpdateReason()),
                trustDebug == null ? 0.0d : trustDebug.trustedDotEstimateMs(),
                modelTrustTrace == null || modelTrustTrace.rawSnapshot() == null
                        ? 0.0d
                        : renderWpm(modelTrustTrace.rawSnapshot()),
                modelTrustTrace == null || modelTrustTrace.stabilizedSnapshot() == null
                        ? 0.0d
                        : renderWpm(modelTrustTrace.stabilizedSnapshot())
        ));
        System.out.println(String.format(
                Locale.US,
                "soft turnStart=%d modelTrust=%d anchorTrust=%d reason=%s trustedDot=%.1f rawWpm=%.1f stableWpm=%.1f",
                softTurnStartMs,
                softModelTrustMs,
                softAnchorTrustMs,
                softTrustDebug == null ? "none" : safe(softTrustDebug.lastTrustedUpdateReason()),
                softTrustDebug == null ? 0.0d : softTrustDebug.trustedDotEstimateMs(),
                softModelTrustTrace == null || softModelTrustTrace.rawSnapshot() == null
                        ? 0.0d
                        : renderWpm(softModelTrustTrace.rawSnapshot()),
                softModelTrustTrace == null || softModelTrustTrace.stabilizedSnapshot() == null
                        ? 0.0d
                        : renderWpm(softModelTrustTrace.stabilizedSnapshot())
        ));
        System.out.println("preTrust raw="
                + sanitize(textAtOrBefore(detailed.rawDecodeEvents(), modelTrustMs)));
        System.out.println("preTrust stable="
                + sanitize(textAtOrBefore(detailed.stableAcceptedDecodeEvents(), modelTrustMs)));
        System.out.println("stableRejects=" + detailed.stableRejectCounts());
        System.out.println("bootstrapBoundaryRejects=" + detailed.bootstrapBoundaryRejectCounts());
        System.out.println("bootstrapCadenceRejects=" + detailed.bootstrapCadenceRejectCounts());
        System.out.println("sim baseline=" + baselineSimulation.describe());
        System.out.println("sim soft-known-front-end-learning=" + softSimulation.describe());

        System.out.println("-- timing-state changes until trust --");
        printTimingStateChangesUntilTrust(
                detailed.timingStateTraces(),
                turnStartMs,
                modelTrustMs,
                MAX_TIMING_LINES
        );
        System.out.println("-- stable decisions until trust --");
        printStableDecisionsUntilTrust(
                detailed.stableDecisionTraces(),
                turnStartMs,
                modelTrustMs,
                MAX_STABLE_LINES
        );
        System.out.println("-- boundary bootstrap decisions until trust --");
        printBootstrapDecisionsUntilTrust(
                detailed.bootstrapBoundaryDecisionTraces(),
                turnStartMs,
                modelTrustMs,
                MAX_BOOTSTRAP_LINES
        );
        System.out.println("-- cadence bootstrap decisions until trust --");
        printBootstrapDecisionsUntilTrust(
                detailed.bootstrapCadenceDecisionTraces(),
                turnStartMs,
                modelTrustMs,
                MAX_BOOTSTRAP_LINES
        );

        assertTrue("softModelTrustMs=" + softModelTrustMs, softModelTrustMs > 0L);
        if (modelTrustMs > 0L) {
            assertTrue(
                    "baselineModelTrustMs=" + modelTrustMs + " softModelTrustMs=" + softModelTrustMs,
                    softModelTrustMs <= modelTrustMs
            );
        }
        if (anchorTrustMs > 0L && softAnchorTrustMs > 0L) {
            assertTrue(
                    "baselineAnchorTrustMs=" + anchorTrustMs + " softAnchorTrustMs=" + softAnchorTrustMs,
                    softAnchorTrustMs <= anchorTrustMs
            );
        }
    }

    private static long firstTurnStartMs(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                return trace.timestampMs();
            }
        }
        return 0L;
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTraceAtOrAfter(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs || trace.debugSnapshot() == null) {
                continue;
            }
            if (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace;
            }
        }
        return null;
    }

    private static long firstAnchorTrustTimestampMs(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs
    ) {
        long stableTrustMs = firstStableTrustTimestampMs(detailed.stableDecisionTraces(), windowStartMs);
        long boundaryTrustMs = firstBootstrapTrustTimestampMs(
                detailed.bootstrapBoundaryDecisionTraces(),
                windowStartMs
        );
        long cadenceTrustMs = firstBootstrapTrustTimestampMs(
                detailed.bootstrapCadenceDecisionTraces(),
                windowStartMs
        );
        long earliest = minPositive(stableTrustMs, minPositive(boundaryTrustMs, cadenceTrustMs));
        return earliest < 0L ? -1L : earliest;
    }

    private static long firstStableTrustTimestampMs(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long windowStartMs
    ) {
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.trustedTimingEstablished()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstBootstrapTrustTimestampMs(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowStartMs
    ) {
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.trustedTimingEstablished()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static SoftBootstrapSimulation simulateSoftMixedBootstrap(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            boolean includeSoftFrontEndLearning
    ) {
        ArrayList<SoftBootstrapCandidate> candidates = new ArrayList<>();
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : detailed.bootstrapBoundaryDecisionTraces()) {
            if (trace == null || trace.timestampMs() < windowStartMs || !"pass".equals(trace.decision())) {
                continue;
            }
            candidates.add(SoftBootstrapCandidate.boundary(
                    trace.timestampMs(),
                    trace.candidateDotEstimateMs(),
                    trace.classification()
            ));
        }
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : detailed.stableDecisionTraces()) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            boolean pass = "pass".equals(trace.decision());
            boolean softRescue = includeSoftFrontEndLearning && shouldUseSoftStableCandidate(trace);
            if (!pass && !softRescue) {
                continue;
            }
            candidates.add(SoftBootstrapCandidate.stable(
                    trace.timestampMs(),
                    rawWpmToDotEstimateMs(trace.rawWpm()),
                    pass ? "pass" : "soft-front-end-learning",
                    sanitize(trace.emittedValue())
            ));
        }
        candidates.sort(Comparator
                .comparingLong(SoftBootstrapCandidate::timestampMs)
                .thenComparingInt(SoftBootstrapCandidate::sortOrder));

        PendingDots boundaryPending = new PendingDots(3);
        PendingDots stablePending = new PendingDots(3);
        for (SoftBootstrapCandidate candidate : candidates) {
            if (candidate == null || candidate.dotEstimateMs() <= 0L) {
                continue;
            }
            boundaryPending.expireIfStale(candidate.timestampMs(), 1800L);
            stablePending.expireIfStale(candidate.timestampMs(), 1800L);
            if (candidate.boundary()) {
                boundaryPending.append(candidate.dotEstimateMs(), candidate.timestampMs(), candidate.label());
            } else {
                stablePending.append(candidate.dotEstimateMs(), candidate.timestampMs(), candidate.label());
            }
            SoftBootstrapPair pair = findBestSoftPair(boundaryPending, stablePending);
            if (pair != null) {
                return new SoftBootstrapSimulation(
                        candidate.timestampMs(),
                        pair.mixedDotEstimateMs(),
                        pair.boundarySummary(),
                        pair.stableSummary()
                );
            }
        }
        return SoftBootstrapSimulation.none();
    }

    private static boolean shouldUseSoftStableCandidate(
            LocalAudioDecodeTestSupport.StableDecisionTrace trace
    ) {
        return trace != null
                && "front-end-learning".equals(trace.decision())
                && !trace.unknownCharacter()
                && trace.recentLockedFrameRatio() >= 0.75d
                && trace.recentNearTargetLockedFrameRatio() >= 0.95d
                && trace.recentActiveUnlockedFrameRatio() <= 0.02d
                && trace.recentHotFrameRatio() <= 0.46d
                && trace.recentClippingFrameRatio() <= 0.01d;
    }

    private static long rawWpmToDotEstimateMs(double rawWpm) {
        if (rawWpm <= 0.0d) {
            return 0L;
        }
        return Math.max(1L, Math.round(1200.0d / rawWpm));
    }

    private static SoftBootstrapPair findBestSoftPair(PendingDots boundaryPending, PendingDots stablePending) {
        if (boundaryPending.count() <= 0 || stablePending.count() <= 0) {
            return null;
        }
        SoftBootstrapPair bestPair = null;
        for (int boundaryIndex = 0; boundaryIndex < boundaryPending.count(); boundaryIndex++) {
            SoftDot boundary = boundaryPending.dotAt(boundaryIndex);
            if (boundary == null || boundary.dotEstimateMs() <= 0L) {
                continue;
            }
            for (int stableIndex = 0; stableIndex < stablePending.count(); stableIndex++) {
                SoftDot stable = stablePending.dotAt(stableIndex);
                if (stable == null || stable.dotEstimateMs() <= 0L) {
                    continue;
                }
                double spreadWpm = softSpreadWpm(boundary.dotEstimateMs(), stable.dotEstimateMs());
                double mixedDotEstimateMs = (boundary.dotEstimateMs() + stable.dotEstimateMs()) / 2.0d;
                if (spreadWpm > 4.0d || mixedDotEstimateMs < 48.0d) {
                    continue;
                }
                SoftBootstrapPair candidatePair = new SoftBootstrapPair(
                        mixedDotEstimateMs,
                        spreadWpm,
                        boundaryIndex + stableIndex,
                        boundary.summary(),
                        stable.summary()
                );
                if (bestPair == null || candidatePair.isBetterThan(bestPair)) {
                    bestPair = candidatePair;
                }
            }
        }
        return bestPair;
    }

    private static double softSpreadWpm(long leftDotEstimateMs, long rightDotEstimateMs) {
        if (leftDotEstimateMs <= 0L || rightDotEstimateMs <= 0L) {
            return Double.POSITIVE_INFINITY;
        }
        double leftWpm = 1200.0d / Math.max(1L, leftDotEstimateMs);
        double rightWpm = 1200.0d / Math.max(1L, rightDotEstimateMs);
        return Math.abs(leftWpm - rightWpm);
    }

    private static void printTimingStateChangesUntilTrust(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartMs,
            long trustMs,
            int maxLines
    ) {
        int printed = 0;
        String lastKey = null;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartMs) {
                continue;
            }
            if (trustMs >= 0L && timestampMs > trustMs) {
                break;
            }
            DebugSnapshot debug = trace.debugSnapshot();
            String key = safe(debug.lastTrustedUpdateReason())
                    + "|"
                    + Math.round(debug.trustedDotEstimateMs())
                    + "|"
                    + Math.round(debug.pendingFastTrustedDotEstimateMs())
                    + "|"
                    + debug.pendingFastTrustedEvidenceCount()
                    + "|"
                    + safe(debug.lastObservationSummary());
            if (key.equals(lastKey)) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d trusted=%.1f pending=%.1fx%d reason=%s obs=%s raw=%s stable=%s",
                    timestampMs,
                    debug.trustedDotEstimateMs(),
                    debug.pendingFastTrustedDotEstimateMs(),
                    debug.pendingFastTrustedEvidenceCount(),
                    safe(debug.lastTrustedUpdateReason()),
                    safe(debug.lastObservationSummary()),
                    renderSnapshot(trace.rawSnapshot()),
                    renderSnapshot(trace.stabilizedSnapshot())
            ));
            lastKey = key;
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printStableDecisionsUntilTrust(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long windowStartMs,
            long trustMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trustMs >= 0L && trace.timestampMs() > trustMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d emit=%s seq=%s decision=%s trusted=%s lock=%s lockR=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f rawWpm=%.1f",
                    trace.timestampMs(),
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    safe(trace.decision()),
                    yesNo(trace.trustedTimingEstablished()),
                    yesNo(trace.targetToneLocked()),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printBootstrapDecisionsUntilTrust(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowStartMs,
            long trustMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trustMs >= 0L && trace.timestampMs() > trustMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d cand=%d decision=%s trusted=%s lock=%s lockR=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.decision()),
                    yesNo(trace.trustedTimingEstablished()),
                    yesNo(trace.targetToneLocked()),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static String textAtOrBefore(List<CwDecodeEvent> events, long timestampMs) {
        String latest = "";
        if (events == null) {
            return latest;
        }
        for (CwDecodeEvent event : events) {
            if (event == null) {
                continue;
            }
            if (timestampMs >= 0L && event.timestampMs() > timestampMs) {
                break;
            }
            latest = event.outputText();
        }
        return latest;
    }

    private static double renderWpm(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0d;
        }
        if (snapshot.estimatedWpmPrecise() > 0.0d) {
            return snapshot.estimatedWpmPrecise();
        }
        return Math.max(0, snapshot.estimatedWpm());
    }

    private static String renderSnapshot(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        return String.format(
                Locale.US,
                "%.1fwpm/%dms",
                renderWpm(snapshot),
                snapshot.dotEstimateMs()
        );
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static long minPositive(long first, long second) {
        if (first < 0L) {
            return second;
        }
        if (second < 0L) {
            return first;
        }
        return Math.min(first, second);
    }

    private static final class SoftBootstrapSimulation {
        private final long timestampMs;
        private final double dotEstimateMs;
        private final String boundarySummary;
        private final String stableSummary;

        private SoftBootstrapSimulation(
                long timestampMs,
                double dotEstimateMs,
                String boundarySummary,
                String stableSummary
        ) {
            this.timestampMs = timestampMs;
            this.dotEstimateMs = dotEstimateMs;
            this.boundarySummary = boundarySummary;
            this.stableSummary = stableSummary;
        }

        private static SoftBootstrapSimulation none() {
            return new SoftBootstrapSimulation(-1L, 0.0d, "none", "none");
        }

        private String describe() {
            if (timestampMs < 0L) {
                return "none";
            }
            return String.format(
                    Locale.US,
                    "@%d dot=%.1f boundary=%s stable=%s",
                    timestampMs,
                    dotEstimateMs,
                    boundarySummary,
                    stableSummary
            );
        }
    }

    private static final class SoftBootstrapPair {
        private final double mixedDotEstimateMs;
        private final double spreadWpm;
        private final int recencyScore;
        private final String boundarySummary;
        private final String stableSummary;

        private SoftBootstrapPair(
                double mixedDotEstimateMs,
                double spreadWpm,
                int recencyScore,
                String boundarySummary,
                String stableSummary
        ) {
            this.mixedDotEstimateMs = mixedDotEstimateMs;
            this.spreadWpm = spreadWpm;
            this.recencyScore = recencyScore;
            this.boundarySummary = boundarySummary;
            this.stableSummary = stableSummary;
        }

        private boolean isBetterThan(SoftBootstrapPair other) {
            if (other == null) {
                return true;
            }
            if (recencyScore != other.recencyScore) {
                return recencyScore > other.recencyScore;
            }
            if (Double.compare(spreadWpm, other.spreadWpm) != 0) {
                return spreadWpm < other.spreadWpm;
            }
            return mixedDotEstimateMs > other.mixedDotEstimateMs;
        }

        private double mixedDotEstimateMs() {
            return mixedDotEstimateMs;
        }

        private String boundarySummary() {
            return boundarySummary;
        }

        private String stableSummary() {
            return stableSummary;
        }
    }

    private static final class PendingDots {
        private final ArrayList<SoftDot> dots = new ArrayList<>();

        private PendingDots(int capacity) {
        }

        private void expireIfStale(long timestampMs, long windowMs) {
            dots.removeIf(dot -> dot != null && timestampMs > 0L && (timestampMs - dot.timestampMs()) >= windowMs);
        }

        private void append(long dotEstimateMs, long timestampMs, String label) {
            dots.add(new SoftDot(dotEstimateMs, timestampMs, label));
            while (dots.size() > 3) {
                dots.remove(0);
            }
        }

        private int count() {
            return dots.size();
        }

        private SoftDot dotAt(int index) {
            if (index < 0 || index >= dots.size()) {
                return null;
            }
            return dots.get(index);
        }
    }

    private static final class SoftDot {
        private final long dotEstimateMs;
        private final long timestampMs;
        private final String label;

        private SoftDot(long dotEstimateMs, long timestampMs, String label) {
            this.dotEstimateMs = dotEstimateMs;
            this.timestampMs = timestampMs;
            this.label = label == null ? "?" : label;
        }

        private long dotEstimateMs() {
            return dotEstimateMs;
        }

        private long timestampMs() {
            return timestampMs;
        }

        private String summary() {
            return label + "@" + timestampMs + "/" + dotEstimateMs + "ms";
        }
    }

    private static final class SoftBootstrapCandidate {
        private final long timestampMs;
        private final long dotEstimateMs;
        private final boolean boundary;
        private final String label;

        private SoftBootstrapCandidate(
                long timestampMs,
                long dotEstimateMs,
                boolean boundary,
                String label
        ) {
            this.timestampMs = timestampMs;
            this.dotEstimateMs = dotEstimateMs;
            this.boundary = boundary;
            this.label = label == null ? "?" : label;
        }

        private static SoftBootstrapCandidate boundary(long timestampMs, long dotEstimateMs, String label) {
            return new SoftBootstrapCandidate(timestampMs, dotEstimateMs, true, "B:" + safe(label));
        }

        private static SoftBootstrapCandidate stable(long timestampMs, long dotEstimateMs, String label, String emit) {
            return new SoftBootstrapCandidate(
                    timestampMs,
                    dotEstimateMs,
                    false,
                    "S:" + safe(label) + ":" + sanitize(emit)
            );
        }

        private long timestampMs() {
            return timestampMs;
        }

        private long dotEstimateMs() {
            return dotEstimateMs;
        }

        private boolean boundary() {
            return boundary;
        }

        private int sortOrder() {
            return boundary ? 0 : 1;
        }

        private String label() {
            return label;
        }
    }
}
