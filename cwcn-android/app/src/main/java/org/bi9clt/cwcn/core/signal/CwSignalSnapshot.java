package org.bi9clt.cwcn.core.signal;

public final class CwSignalSnapshot {
    private final int recentHistoryFrameCount;
    private final char[] recentFrontEndStateHistory;
    private final int[] recentTrackingOffsetHistoryHz;
    private final boolean toneActive;
    private final boolean targetToneLocked;
    private final int preferredToneFrequencyHz;
    private final int targetToneFrequencyHz;
    private final int acquisitionReferenceFrequencyHz;
    private final int toneHypothesisFrequencyHz;
    private final double toneHypothesisConfidence;
    private final int toneHypothesisSupportFrames;
    private final int toneHypothesisIdleFrames;
    private final String toneHypothesisSource;
    private final boolean hypothesisGuardExperimentEnabled;
    private final boolean hypothesisGuardEligible;
    private final boolean hypothesisGuardApplied;
    private final int hypothesisGuardAppliedFrequencyHz;
    private final int hypothesisGuardApplyCount;
    private final int hypothesisGuardHistorySpanHz;
    private final String hypothesisGuardDecision;
    private final int representativeLockedToneFrequencyHz;
    private final int representativeLockedToneFrameCount;
    private final int activeWindowObservationCount;
    private final int activeAcquisitionCenterFrequencyHz;
    private final int activeAcquisitionCenterHitCount;
    private final int activeHypothesisObservationCount;
    private final int activeHypothesisCenterFrequencyHz;
    private final int activeHypothesisCenterHitCount;
    private final int representativeCompetitionObservationCount;
    private final int representativeCompetitionTrackedWinFrames;
    private final int representativeCompetitionHypothesisWinFrames;
    private final int representativeCompetitionTieFrames;
    private final int representativeCompetitionHypothesisCurrentWinStreak;
    private final int representativeCompetitionHypothesisMaxWinStreak;
    private final int activeCenterCompetitionObservationCount;
    private final int activeCenterCompetitionTrackedWinFrames;
    private final int activeCenterCompetitionHypothesisWinFrames;
    private final int activeCenterCompetitionTieFrames;
    private final int activeCenterCompetitionHypothesisCurrentWinStreak;
    private final int activeCenterCompetitionHypothesisMaxWinStreak;
    private final int currentThreshold;
    private final int releaseThreshold;
    private final int noiseFloorEstimate;
    private final int signalFloorEstimate;
    private final double lastRmsAmplitude;
    private final double lastToneRmsAmplitude;
    private final double lastWidebandResidualRmsAmplitude;
    private final double toneDominanceRatio;
    private final double narrowbandIsolationRatio;
    private final double peakToneRmsAmplitude;
    private final double peakNarrowbandIsolationRatio;
    private final int processedFrameCount;
    private final int lockedFrameCount;
    private final int toneActiveFrameCount;
    private final int toneActiveUnlockedFrameCount;
    private final int consecutiveLockedFrames;
    private final int maxConsecutiveLockedFrames;
    private final int consecutiveToneActiveUnlockedFrames;
    private final int maxConsecutiveToneActiveUnlockedFrames;
    private final int pendingRetuneCandidateFrequencyHz;
    private final int pendingRetuneCandidateStableScans;
    private final boolean lockedRetuneGuardHolding;
    private final int lockedRetuneGuardCandidateFrequencyHz;
    private final int lockedRetuneGuardDriftHz;
    private final int lockedRetuneGuardObservedScans;
    private final int lockedRetuneGuardRequiredScans;
    private final int lockedRetuneGuardRemainingScans;
    private final String lockedRetuneGuardBand;
    private final int preferredWindowWinnerFrequencyHz;
    private final int wideScanWinnerFrequencyHz;
    private final int acquisitionWinnerFrequencyHz;
    private final int finalAdoptedFrequencyHz;
    private final double preferredWindowWinnerToneRms;
    private final double wideScanWinnerToneRms;
    private final double acquisitionWinnerToneRms;
    private final double finalAdoptedToneRms;
    private final double preferredWindowWinnerSelectionScore;
    private final double wideScanWinnerSelectionScore;
    private final double acquisitionWinnerSelectionScore;
    private final double finalAdoptedSelectionScore;
    private final double preferredWindowWinnerConfidence;
    private final double wideScanWinnerConfidence;
    private final double acquisitionWinnerConfidence;
    private final double finalAdoptedConfidence;
    private final int preferredWindowRunnerUpFrequencyHz;
    private final int wideScanRunnerUpFrequencyHz;
    private final int acquisitionRunnerUpFrequencyHz;
    private final double preferredWindowRunnerUpSelectionScore;
    private final double wideScanRunnerUpSelectionScore;
    private final double acquisitionRunnerUpSelectionScore;
    private final int previousTargetBeforeScanFrequencyHz;
    private final double previousTargetBeforeScanToneRms;
    private final double previousTargetBeforeScanSelectionScore;
    private final boolean previousTargetBeforeScanLocked;
    private final boolean preferredWindowWinnerLocked;
    private final boolean wideScanWinnerLocked;
    private final boolean acquisitionWinnerLocked;
    private final boolean finalAdoptedLocked;
    private final String acquisitionWinnerSource;
    private final String finalAdoptedSource;
    private final String acquisitionDecisionDetail;
    private final String finalAdoptionDetail;
    private final String preferredWindowTopCandidatesSummary;
    private final String wideScanTopCandidatesSummary;
    private final int totalToneOnEvents;
    private final int totalToneOffEvents;
    private final int frameGapResetCount;
    private final long lastFrameGapMs;
    private final long lastFrameGapResetThresholdMs;
    private final long worstFrameGapMs;
    private final long lastFrameGapResetAtMs;
    private final CwToneEvent lastEvent;

    public CwSignalSnapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int representativeLockedToneFrequencyHz,
            int representativeLockedToneFrameCount,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            int processedFrameCount,
            int lockedFrameCount,
            int toneActiveFrameCount,
            int toneActiveUnlockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int consecutiveToneActiveUnlockedFrames,
            int maxConsecutiveToneActiveUnlockedFrames,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans,
            int preferredWindowWinnerFrequencyHz,
            int wideScanWinnerFrequencyHz,
            int acquisitionWinnerFrequencyHz,
            int finalAdoptedFrequencyHz,
            double preferredWindowWinnerToneRms,
            double wideScanWinnerToneRms,
            double acquisitionWinnerToneRms,
            double finalAdoptedToneRms,
            double preferredWindowWinnerSelectionScore,
            double wideScanWinnerSelectionScore,
            double acquisitionWinnerSelectionScore,
            double finalAdoptedSelectionScore,
            boolean preferredWindowWinnerLocked,
            boolean wideScanWinnerLocked,
            boolean acquisitionWinnerLocked,
            boolean finalAdoptedLocked,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long lastFrameGapMs,
            long lastFrameGapResetThresholdMs,
            long worstFrameGapMs,
            long lastFrameGapResetAtMs,
            CwToneEvent lastEvent
    ) {
        this(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                0.0d,
                0,
                0,
                "NONE",
                false,
                false,
                false,
                0,
                0,
                0,
                "DISABLED",
                representativeLockedToneFrequencyHz,
                representativeLockedToneFrameCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                currentThreshold,
                releaseThreshold,
                noiseFloorEstimate,
                signalFloorEstimate,
                lastRmsAmplitude,
                lastToneRmsAmplitude,
                lastWidebandResidualRmsAmplitude,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                processedFrameCount,
                lockedFrameCount,
                toneActiveFrameCount,
                toneActiveUnlockedFrameCount,
                consecutiveLockedFrames,
                maxConsecutiveLockedFrames,
                consecutiveToneActiveUnlockedFrames,
                maxConsecutiveToneActiveUnlockedFrames,
                pendingRetuneCandidateFrequencyHz,
                pendingRetuneCandidateStableScans,
                false,
                0,
                0,
                0,
                0,
                0,
                "NONE",
                preferredWindowWinnerFrequencyHz,
                wideScanWinnerFrequencyHz,
                acquisitionWinnerFrequencyHz,
                finalAdoptedFrequencyHz,
                preferredWindowWinnerToneRms,
                wideScanWinnerToneRms,
                acquisitionWinnerToneRms,
                finalAdoptedToneRms,
                preferredWindowWinnerSelectionScore,
                wideScanWinnerSelectionScore,
                acquisitionWinnerSelectionScore,
                finalAdoptedSelectionScore,
                preferredWindowWinnerLocked,
                wideScanWinnerLocked,
                acquisitionWinnerLocked,
                finalAdoptedLocked,
                acquisitionWinnerSource,
                finalAdoptedSource,
                totalToneOnEvents,
                totalToneOffEvents,
                frameGapResetCount,
                lastFrameGapMs,
                lastFrameGapResetThresholdMs,
                worstFrameGapMs,
                lastFrameGapResetAtMs,
                lastEvent
        );
    }

    public CwSignalSnapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int acquisitionReferenceFrequencyHz,
            int toneHypothesisFrequencyHz,
            double toneHypothesisConfidence,
            int toneHypothesisSupportFrames,
            int toneHypothesisIdleFrames,
            String toneHypothesisSource,
            boolean hypothesisGuardExperimentEnabled,
            boolean hypothesisGuardEligible,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedFrequencyHz,
            int hypothesisGuardApplyCount,
            int hypothesisGuardHistorySpanHz,
            String hypothesisGuardDecision,
            int representativeLockedToneFrequencyHz,
            int representativeLockedToneFrameCount,
            int activeWindowObservationCount,
            int activeAcquisitionCenterFrequencyHz,
            int activeAcquisitionCenterHitCount,
            int activeHypothesisObservationCount,
            int activeHypothesisCenterFrequencyHz,
            int activeHypothesisCenterHitCount,
            int representativeCompetitionObservationCount,
            int representativeCompetitionTrackedWinFrames,
            int representativeCompetitionHypothesisWinFrames,
            int representativeCompetitionTieFrames,
            int representativeCompetitionHypothesisCurrentWinStreak,
            int representativeCompetitionHypothesisMaxWinStreak,
            int activeCenterCompetitionObservationCount,
            int activeCenterCompetitionTrackedWinFrames,
            int activeCenterCompetitionHypothesisWinFrames,
            int activeCenterCompetitionTieFrames,
            int activeCenterCompetitionHypothesisCurrentWinStreak,
            int activeCenterCompetitionHypothesisMaxWinStreak,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            int processedFrameCount,
            int lockedFrameCount,
            int toneActiveFrameCount,
            int toneActiveUnlockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int consecutiveToneActiveUnlockedFrames,
            int maxConsecutiveToneActiveUnlockedFrames,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans,
            boolean lockedRetuneGuardHolding,
            int lockedRetuneGuardCandidateFrequencyHz,
            int lockedRetuneGuardDriftHz,
            int lockedRetuneGuardObservedScans,
            int lockedRetuneGuardRequiredScans,
            int lockedRetuneGuardRemainingScans,
            String lockedRetuneGuardBand,
            int preferredWindowWinnerFrequencyHz,
            int wideScanWinnerFrequencyHz,
            int acquisitionWinnerFrequencyHz,
            int finalAdoptedFrequencyHz,
            double preferredWindowWinnerToneRms,
            double wideScanWinnerToneRms,
            double acquisitionWinnerToneRms,
            double finalAdoptedToneRms,
            double preferredWindowWinnerSelectionScore,
            double wideScanWinnerSelectionScore,
            double acquisitionWinnerSelectionScore,
            double finalAdoptedSelectionScore,
            double preferredWindowWinnerConfidence,
            double wideScanWinnerConfidence,
            double acquisitionWinnerConfidence,
            double finalAdoptedConfidence,
            int preferredWindowRunnerUpFrequencyHz,
            int wideScanRunnerUpFrequencyHz,
            int acquisitionRunnerUpFrequencyHz,
            double preferredWindowRunnerUpSelectionScore,
            double wideScanRunnerUpSelectionScore,
            double acquisitionRunnerUpSelectionScore,
            int previousTargetBeforeScanFrequencyHz,
            double previousTargetBeforeScanToneRms,
            double previousTargetBeforeScanSelectionScore,
            boolean previousTargetBeforeScanLocked,
            boolean preferredWindowWinnerLocked,
            boolean wideScanWinnerLocked,
            boolean acquisitionWinnerLocked,
            boolean finalAdoptedLocked,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            String acquisitionDecisionDetail,
            String finalAdoptionDetail,
            String preferredWindowTopCandidatesSummary,
            String wideScanTopCandidatesSummary,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long lastFrameGapMs,
            long lastFrameGapResetThresholdMs,
            long worstFrameGapMs,
            long lastFrameGapResetAtMs,
            CwToneEvent lastEvent,
            boolean legacyDefaultsMarker
    ) {
        this(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                0.0d,
                0,
                0,
                "NONE",
                false,
                false,
                false,
                0,
                0,
                0,
                "DISABLED",
                representativeLockedToneFrequencyHz,
                representativeLockedToneFrameCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                currentThreshold,
                releaseThreshold,
                noiseFloorEstimate,
                signalFloorEstimate,
                lastRmsAmplitude,
                lastToneRmsAmplitude,
                lastWidebandResidualRmsAmplitude,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                processedFrameCount,
                lockedFrameCount,
                toneActiveFrameCount,
                toneActiveUnlockedFrameCount,
                consecutiveLockedFrames,
                maxConsecutiveLockedFrames,
                consecutiveToneActiveUnlockedFrames,
                maxConsecutiveToneActiveUnlockedFrames,
                pendingRetuneCandidateFrequencyHz,
                pendingRetuneCandidateStableScans,
                false,
                0,
                0,
                0,
                0,
                0,
                "NONE",
                preferredWindowWinnerFrequencyHz,
                wideScanWinnerFrequencyHz,
                acquisitionWinnerFrequencyHz,
                finalAdoptedFrequencyHz,
                preferredWindowWinnerToneRms,
                wideScanWinnerToneRms,
                acquisitionWinnerToneRms,
                finalAdoptedToneRms,
                preferredWindowWinnerSelectionScore,
                wideScanWinnerSelectionScore,
                acquisitionWinnerSelectionScore,
                finalAdoptedSelectionScore,
                preferredWindowWinnerLocked,
                wideScanWinnerLocked,
                acquisitionWinnerLocked,
                finalAdoptedLocked,
                acquisitionWinnerSource,
                finalAdoptedSource,
                totalToneOnEvents,
                totalToneOffEvents,
                frameGapResetCount,
                lastFrameGapMs,
                lastFrameGapResetThresholdMs,
                worstFrameGapMs,
                lastFrameGapResetAtMs,
                lastEvent
        );
    }

    public CwSignalSnapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int acquisitionReferenceFrequencyHz,
            int toneHypothesisFrequencyHz,
            double toneHypothesisConfidence,
            int toneHypothesisSupportFrames,
            int toneHypothesisIdleFrames,
            String toneHypothesisSource,
            boolean hypothesisGuardExperimentEnabled,
            boolean hypothesisGuardEligible,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedFrequencyHz,
            int hypothesisGuardApplyCount,
            int hypothesisGuardHistorySpanHz,
            String hypothesisGuardDecision,
            int representativeLockedToneFrequencyHz,
            int representativeLockedToneFrameCount,
            int activeWindowObservationCount,
            int activeAcquisitionCenterFrequencyHz,
            int activeAcquisitionCenterHitCount,
            int activeHypothesisObservationCount,
            int activeHypothesisCenterFrequencyHz,
            int activeHypothesisCenterHitCount,
            int representativeCompetitionObservationCount,
            int representativeCompetitionTrackedWinFrames,
            int representativeCompetitionHypothesisWinFrames,
            int representativeCompetitionTieFrames,
            int representativeCompetitionHypothesisCurrentWinStreak,
            int representativeCompetitionHypothesisMaxWinStreak,
            int activeCenterCompetitionObservationCount,
            int activeCenterCompetitionTrackedWinFrames,
            int activeCenterCompetitionHypothesisWinFrames,
            int activeCenterCompetitionTieFrames,
            int activeCenterCompetitionHypothesisCurrentWinStreak,
            int activeCenterCompetitionHypothesisMaxWinStreak,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            int processedFrameCount,
            int lockedFrameCount,
            int toneActiveFrameCount,
            int toneActiveUnlockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int consecutiveToneActiveUnlockedFrames,
            int maxConsecutiveToneActiveUnlockedFrames,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans,
            boolean lockedRetuneGuardHolding,
            int lockedRetuneGuardCandidateFrequencyHz,
            int lockedRetuneGuardDriftHz,
            int lockedRetuneGuardObservedScans,
            int lockedRetuneGuardRequiredScans,
            int lockedRetuneGuardRemainingScans,
            String lockedRetuneGuardBand,
            int preferredWindowWinnerFrequencyHz,
            int wideScanWinnerFrequencyHz,
            int acquisitionWinnerFrequencyHz,
            int finalAdoptedFrequencyHz,
            double preferredWindowWinnerToneRms,
            double wideScanWinnerToneRms,
            double acquisitionWinnerToneRms,
            double finalAdoptedToneRms,
            double preferredWindowWinnerSelectionScore,
            double wideScanWinnerSelectionScore,
            double acquisitionWinnerSelectionScore,
            double finalAdoptedSelectionScore,
            boolean preferredWindowWinnerLocked,
            boolean wideScanWinnerLocked,
            boolean acquisitionWinnerLocked,
            boolean finalAdoptedLocked,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long lastFrameGapMs,
            long lastFrameGapResetThresholdMs,
            long worstFrameGapMs,
            long lastFrameGapResetAtMs,
            CwToneEvent lastEvent
    ) {
        this(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                acquisitionReferenceFrequencyHz,
                toneHypothesisFrequencyHz,
                toneHypothesisConfidence,
                toneHypothesisSupportFrames,
                toneHypothesisIdleFrames,
                toneHypothesisSource,
                hypothesisGuardExperimentEnabled,
                hypothesisGuardEligible,
                hypothesisGuardApplied,
                hypothesisGuardAppliedFrequencyHz,
                hypothesisGuardApplyCount,
                hypothesisGuardHistorySpanHz,
                hypothesisGuardDecision,
                representativeLockedToneFrequencyHz,
                representativeLockedToneFrameCount,
                activeWindowObservationCount,
                activeAcquisitionCenterFrequencyHz,
                activeAcquisitionCenterHitCount,
                activeHypothesisObservationCount,
                activeHypothesisCenterFrequencyHz,
                activeHypothesisCenterHitCount,
                representativeCompetitionObservationCount,
                representativeCompetitionTrackedWinFrames,
                representativeCompetitionHypothesisWinFrames,
                representativeCompetitionTieFrames,
                representativeCompetitionHypothesisCurrentWinStreak,
                representativeCompetitionHypothesisMaxWinStreak,
                activeCenterCompetitionObservationCount,
                activeCenterCompetitionTrackedWinFrames,
                activeCenterCompetitionHypothesisWinFrames,
                activeCenterCompetitionTieFrames,
                activeCenterCompetitionHypothesisCurrentWinStreak,
                activeCenterCompetitionHypothesisMaxWinStreak,
                currentThreshold,
                releaseThreshold,
                noiseFloorEstimate,
                signalFloorEstimate,
                lastRmsAmplitude,
                lastToneRmsAmplitude,
                lastWidebandResidualRmsAmplitude,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                processedFrameCount,
                lockedFrameCount,
                toneActiveFrameCount,
                toneActiveUnlockedFrameCount,
                consecutiveLockedFrames,
                maxConsecutiveLockedFrames,
                consecutiveToneActiveUnlockedFrames,
                maxConsecutiveToneActiveUnlockedFrames,
                pendingRetuneCandidateFrequencyHz,
                pendingRetuneCandidateStableScans,
                lockedRetuneGuardHolding,
                lockedRetuneGuardCandidateFrequencyHz,
                lockedRetuneGuardDriftHz,
                lockedRetuneGuardObservedScans,
                lockedRetuneGuardRequiredScans,
                lockedRetuneGuardRemainingScans,
                lockedRetuneGuardBand,
                preferredWindowWinnerFrequencyHz,
                wideScanWinnerFrequencyHz,
                acquisitionWinnerFrequencyHz,
                finalAdoptedFrequencyHz,
                preferredWindowWinnerToneRms,
                wideScanWinnerToneRms,
                acquisitionWinnerToneRms,
                finalAdoptedToneRms,
                preferredWindowWinnerSelectionScore,
                wideScanWinnerSelectionScore,
                acquisitionWinnerSelectionScore,
                finalAdoptedSelectionScore,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0,
                0,
                0,
                0.0d,
                0.0d,
                0.0d,
                0,
                0.0d,
                0.0d,
                false,
                preferredWindowWinnerLocked,
                wideScanWinnerLocked,
                acquisitionWinnerLocked,
                finalAdoptedLocked,
                acquisitionWinnerSource,
                finalAdoptedSource,
                "NONE",
                "NONE",
                "NONE",
                "NONE",
                totalToneOnEvents,
                totalToneOffEvents,
                frameGapResetCount,
                lastFrameGapMs,
                lastFrameGapResetThresholdMs,
                worstFrameGapMs,
                lastFrameGapResetAtMs,
                lastEvent
        );
    }

    public CwSignalSnapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int acquisitionReferenceFrequencyHz,
            int toneHypothesisFrequencyHz,
            double toneHypothesisConfidence,
            int toneHypothesisSupportFrames,
            int toneHypothesisIdleFrames,
            String toneHypothesisSource,
            boolean hypothesisGuardExperimentEnabled,
            boolean hypothesisGuardEligible,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedFrequencyHz,
            int hypothesisGuardApplyCount,
            int hypothesisGuardHistorySpanHz,
            String hypothesisGuardDecision,
            int representativeLockedToneFrequencyHz,
            int representativeLockedToneFrameCount,
            int activeWindowObservationCount,
            int activeAcquisitionCenterFrequencyHz,
            int activeAcquisitionCenterHitCount,
            int activeHypothesisObservationCount,
            int activeHypothesisCenterFrequencyHz,
            int activeHypothesisCenterHitCount,
            int representativeCompetitionObservationCount,
            int representativeCompetitionTrackedWinFrames,
            int representativeCompetitionHypothesisWinFrames,
            int representativeCompetitionTieFrames,
            int representativeCompetitionHypothesisCurrentWinStreak,
            int representativeCompetitionHypothesisMaxWinStreak,
            int activeCenterCompetitionObservationCount,
            int activeCenterCompetitionTrackedWinFrames,
            int activeCenterCompetitionHypothesisWinFrames,
            int activeCenterCompetitionTieFrames,
            int activeCenterCompetitionHypothesisCurrentWinStreak,
            int activeCenterCompetitionHypothesisMaxWinStreak,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            int processedFrameCount,
            int lockedFrameCount,
            int toneActiveFrameCount,
            int toneActiveUnlockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int consecutiveToneActiveUnlockedFrames,
            int maxConsecutiveToneActiveUnlockedFrames,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans,
            boolean lockedRetuneGuardHolding,
            int lockedRetuneGuardCandidateFrequencyHz,
            int lockedRetuneGuardDriftHz,
            int lockedRetuneGuardObservedScans,
            int lockedRetuneGuardRequiredScans,
            int lockedRetuneGuardRemainingScans,
            String lockedRetuneGuardBand,
            int preferredWindowWinnerFrequencyHz,
            int wideScanWinnerFrequencyHz,
            int acquisitionWinnerFrequencyHz,
            int finalAdoptedFrequencyHz,
            double preferredWindowWinnerToneRms,
            double wideScanWinnerToneRms,
            double acquisitionWinnerToneRms,
            double finalAdoptedToneRms,
            double preferredWindowWinnerSelectionScore,
            double wideScanWinnerSelectionScore,
            double acquisitionWinnerSelectionScore,
            double finalAdoptedSelectionScore,
            double preferredWindowWinnerConfidence,
            double wideScanWinnerConfidence,
            double acquisitionWinnerConfidence,
            double finalAdoptedConfidence,
            int preferredWindowRunnerUpFrequencyHz,
            int wideScanRunnerUpFrequencyHz,
            int acquisitionRunnerUpFrequencyHz,
            double preferredWindowRunnerUpSelectionScore,
            double wideScanRunnerUpSelectionScore,
            double acquisitionRunnerUpSelectionScore,
            int previousTargetBeforeScanFrequencyHz,
            double previousTargetBeforeScanToneRms,
            double previousTargetBeforeScanSelectionScore,
            boolean previousTargetBeforeScanLocked,
            boolean preferredWindowWinnerLocked,
            boolean wideScanWinnerLocked,
            boolean acquisitionWinnerLocked,
            boolean finalAdoptedLocked,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            String acquisitionDecisionDetail,
            String finalAdoptionDetail,
            String preferredWindowTopCandidatesSummary,
            String wideScanTopCandidatesSummary,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long lastFrameGapMs,
            long lastFrameGapResetThresholdMs,
            long worstFrameGapMs,
            long lastFrameGapResetAtMs,
            CwToneEvent lastEvent
    ) {
        this.recentHistoryFrameCount = recentHistoryFrameCount;
        this.recentFrontEndStateHistory = recentFrontEndStateHistory == null
                ? new char[0]
                : recentFrontEndStateHistory.clone();
        this.recentTrackingOffsetHistoryHz = recentTrackingOffsetHistoryHz == null
                ? new int[0]
                : recentTrackingOffsetHistoryHz.clone();
        this.toneActive = toneActive;
        this.targetToneLocked = targetToneLocked;
        this.preferredToneFrequencyHz = preferredToneFrequencyHz;
        this.targetToneFrequencyHz = targetToneFrequencyHz;
        this.acquisitionReferenceFrequencyHz = acquisitionReferenceFrequencyHz;
        this.toneHypothesisFrequencyHz = toneHypothesisFrequencyHz;
        this.toneHypothesisConfidence = toneHypothesisConfidence;
        this.toneHypothesisSupportFrames = toneHypothesisSupportFrames;
        this.toneHypothesisIdleFrames = toneHypothesisIdleFrames;
        this.toneHypothesisSource = toneHypothesisSource == null ? "NONE" : toneHypothesisSource;
        this.hypothesisGuardExperimentEnabled = hypothesisGuardExperimentEnabled;
        this.hypothesisGuardEligible = hypothesisGuardEligible;
        this.hypothesisGuardApplied = hypothesisGuardApplied;
        this.hypothesisGuardAppliedFrequencyHz = hypothesisGuardAppliedFrequencyHz;
        this.hypothesisGuardApplyCount = hypothesisGuardApplyCount;
        this.hypothesisGuardHistorySpanHz = hypothesisGuardHistorySpanHz;
        this.hypothesisGuardDecision = hypothesisGuardDecision == null ? "NONE" : hypothesisGuardDecision;
        this.representativeLockedToneFrequencyHz = representativeLockedToneFrequencyHz;
        this.representativeLockedToneFrameCount = representativeLockedToneFrameCount;
        this.activeWindowObservationCount = activeWindowObservationCount;
        this.activeAcquisitionCenterFrequencyHz = activeAcquisitionCenterFrequencyHz;
        this.activeAcquisitionCenterHitCount = activeAcquisitionCenterHitCount;
        this.activeHypothesisObservationCount = activeHypothesisObservationCount;
        this.activeHypothesisCenterFrequencyHz = activeHypothesisCenterFrequencyHz;
        this.activeHypothesisCenterHitCount = activeHypothesisCenterHitCount;
        this.representativeCompetitionObservationCount = representativeCompetitionObservationCount;
        this.representativeCompetitionTrackedWinFrames = representativeCompetitionTrackedWinFrames;
        this.representativeCompetitionHypothesisWinFrames = representativeCompetitionHypothesisWinFrames;
        this.representativeCompetitionTieFrames = representativeCompetitionTieFrames;
        this.representativeCompetitionHypothesisCurrentWinStreak = representativeCompetitionHypothesisCurrentWinStreak;
        this.representativeCompetitionHypothesisMaxWinStreak = representativeCompetitionHypothesisMaxWinStreak;
        this.activeCenterCompetitionObservationCount = activeCenterCompetitionObservationCount;
        this.activeCenterCompetitionTrackedWinFrames = activeCenterCompetitionTrackedWinFrames;
        this.activeCenterCompetitionHypothesisWinFrames = activeCenterCompetitionHypothesisWinFrames;
        this.activeCenterCompetitionTieFrames = activeCenterCompetitionTieFrames;
        this.activeCenterCompetitionHypothesisCurrentWinStreak = activeCenterCompetitionHypothesisCurrentWinStreak;
        this.activeCenterCompetitionHypothesisMaxWinStreak = activeCenterCompetitionHypothesisMaxWinStreak;
        this.currentThreshold = currentThreshold;
        this.releaseThreshold = releaseThreshold;
        this.noiseFloorEstimate = noiseFloorEstimate;
        this.signalFloorEstimate = signalFloorEstimate;
        this.lastRmsAmplitude = lastRmsAmplitude;
        this.lastToneRmsAmplitude = lastToneRmsAmplitude;
        this.lastWidebandResidualRmsAmplitude = lastWidebandResidualRmsAmplitude;
        this.toneDominanceRatio = toneDominanceRatio;
        this.narrowbandIsolationRatio = narrowbandIsolationRatio;
        this.peakToneRmsAmplitude = peakToneRmsAmplitude;
        this.peakNarrowbandIsolationRatio = peakNarrowbandIsolationRatio;
        this.processedFrameCount = processedFrameCount;
        this.lockedFrameCount = lockedFrameCount;
        this.toneActiveFrameCount = toneActiveFrameCount;
        this.toneActiveUnlockedFrameCount = toneActiveUnlockedFrameCount;
        this.consecutiveLockedFrames = consecutiveLockedFrames;
        this.maxConsecutiveLockedFrames = maxConsecutiveLockedFrames;
        this.consecutiveToneActiveUnlockedFrames = consecutiveToneActiveUnlockedFrames;
        this.maxConsecutiveToneActiveUnlockedFrames = maxConsecutiveToneActiveUnlockedFrames;
        this.pendingRetuneCandidateFrequencyHz = pendingRetuneCandidateFrequencyHz;
        this.pendingRetuneCandidateStableScans = pendingRetuneCandidateStableScans;
        this.lockedRetuneGuardHolding = lockedRetuneGuardHolding;
        this.lockedRetuneGuardCandidateFrequencyHz = lockedRetuneGuardCandidateFrequencyHz;
        this.lockedRetuneGuardDriftHz = lockedRetuneGuardDriftHz;
        this.lockedRetuneGuardObservedScans = lockedRetuneGuardObservedScans;
        this.lockedRetuneGuardRequiredScans = lockedRetuneGuardRequiredScans;
        this.lockedRetuneGuardRemainingScans = lockedRetuneGuardRemainingScans;
        this.lockedRetuneGuardBand = lockedRetuneGuardBand == null ? "NONE" : lockedRetuneGuardBand;
        this.preferredWindowWinnerFrequencyHz = preferredWindowWinnerFrequencyHz;
        this.wideScanWinnerFrequencyHz = wideScanWinnerFrequencyHz;
        this.acquisitionWinnerFrequencyHz = acquisitionWinnerFrequencyHz;
        this.finalAdoptedFrequencyHz = finalAdoptedFrequencyHz;
        this.preferredWindowWinnerToneRms = preferredWindowWinnerToneRms;
        this.wideScanWinnerToneRms = wideScanWinnerToneRms;
        this.acquisitionWinnerToneRms = acquisitionWinnerToneRms;
        this.finalAdoptedToneRms = finalAdoptedToneRms;
        this.preferredWindowWinnerSelectionScore = preferredWindowWinnerSelectionScore;
        this.wideScanWinnerSelectionScore = wideScanWinnerSelectionScore;
        this.acquisitionWinnerSelectionScore = acquisitionWinnerSelectionScore;
        this.finalAdoptedSelectionScore = finalAdoptedSelectionScore;
        this.preferredWindowWinnerConfidence = preferredWindowWinnerConfidence;
        this.wideScanWinnerConfidence = wideScanWinnerConfidence;
        this.acquisitionWinnerConfidence = acquisitionWinnerConfidence;
        this.finalAdoptedConfidence = finalAdoptedConfidence;
        this.preferredWindowRunnerUpFrequencyHz = preferredWindowRunnerUpFrequencyHz;
        this.wideScanRunnerUpFrequencyHz = wideScanRunnerUpFrequencyHz;
        this.acquisitionRunnerUpFrequencyHz = acquisitionRunnerUpFrequencyHz;
        this.preferredWindowRunnerUpSelectionScore = preferredWindowRunnerUpSelectionScore;
        this.wideScanRunnerUpSelectionScore = wideScanRunnerUpSelectionScore;
        this.acquisitionRunnerUpSelectionScore = acquisitionRunnerUpSelectionScore;
        this.previousTargetBeforeScanFrequencyHz = previousTargetBeforeScanFrequencyHz;
        this.previousTargetBeforeScanToneRms = previousTargetBeforeScanToneRms;
        this.previousTargetBeforeScanSelectionScore = previousTargetBeforeScanSelectionScore;
        this.previousTargetBeforeScanLocked = previousTargetBeforeScanLocked;
        this.preferredWindowWinnerLocked = preferredWindowWinnerLocked;
        this.wideScanWinnerLocked = wideScanWinnerLocked;
        this.acquisitionWinnerLocked = acquisitionWinnerLocked;
        this.finalAdoptedLocked = finalAdoptedLocked;
        this.acquisitionWinnerSource = acquisitionWinnerSource == null ? "NONE" : acquisitionWinnerSource;
        this.finalAdoptedSource = finalAdoptedSource == null ? "NONE" : finalAdoptedSource;
        this.acquisitionDecisionDetail = acquisitionDecisionDetail == null ? "NONE" : acquisitionDecisionDetail;
        this.finalAdoptionDetail = finalAdoptionDetail == null ? "NONE" : finalAdoptionDetail;
        this.preferredWindowTopCandidatesSummary = preferredWindowTopCandidatesSummary == null
                ? "NONE"
                : preferredWindowTopCandidatesSummary;
        this.wideScanTopCandidatesSummary = wideScanTopCandidatesSummary == null
                ? "NONE"
                : wideScanTopCandidatesSummary;
        this.totalToneOnEvents = totalToneOnEvents;
        this.totalToneOffEvents = totalToneOffEvents;
        this.frameGapResetCount = frameGapResetCount;
        this.lastFrameGapMs = lastFrameGapMs;
        this.lastFrameGapResetThresholdMs = lastFrameGapResetThresholdMs;
        this.worstFrameGapMs = worstFrameGapMs;
        this.lastFrameGapResetAtMs = lastFrameGapResetAtMs;
        this.lastEvent = lastEvent;
    }

    public int recentHistoryFrameCount() {
        return recentHistoryFrameCount;
    }

    public char[] recentFrontEndStateHistory() {
        return recentFrontEndStateHistory.clone();
    }

    public int[] recentTrackingOffsetHistoryHz() {
        return recentTrackingOffsetHistoryHz.clone();
    }

    public boolean toneActive() {
        return toneActive;
    }

    public boolean targetToneLocked() {
        return targetToneLocked;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int targetToneFrequencyHz() {
        return targetToneFrequencyHz;
    }

    public int acquisitionReferenceFrequencyHz() {
        return acquisitionReferenceFrequencyHz;
    }

    public int toneHypothesisFrequencyHz() {
        return toneHypothesisFrequencyHz;
    }

    public double toneHypothesisConfidence() {
        return toneHypothesisConfidence;
    }

    public int toneHypothesisSupportFrames() {
        return toneHypothesisSupportFrames;
    }

    public int toneHypothesisIdleFrames() {
        return toneHypothesisIdleFrames;
    }

    public String toneHypothesisSource() {
        return toneHypothesisSource;
    }

    public boolean hypothesisGuardExperimentEnabled() {
        return hypothesisGuardExperimentEnabled;
    }

    public boolean hypothesisGuardEligible() {
        return hypothesisGuardEligible;
    }

    public boolean hypothesisGuardApplied() {
        return hypothesisGuardApplied;
    }

    public int hypothesisGuardAppliedFrequencyHz() {
        return hypothesisGuardAppliedFrequencyHz;
    }

    public int hypothesisGuardApplyCount() {
        return hypothesisGuardApplyCount;
    }

    public int hypothesisGuardHistorySpanHz() {
        return hypothesisGuardHistorySpanHz;
    }

    public String hypothesisGuardDecision() {
        return hypothesisGuardDecision;
    }

    public int representativeLockedToneFrequencyHz() {
        return representativeLockedToneFrequencyHz;
    }

    public int representativeLockedToneFrameCount() {
        return representativeLockedToneFrameCount;
    }

    public int activeWindowObservationCount() {
        return activeWindowObservationCount;
    }

    public int activeAcquisitionCenterFrequencyHz() {
        return activeAcquisitionCenterFrequencyHz;
    }

    public int activeAcquisitionCenterHitCount() {
        return activeAcquisitionCenterHitCount;
    }

    public int activeHypothesisObservationCount() {
        return activeHypothesisObservationCount;
    }

    public int activeHypothesisCenterFrequencyHz() {
        return activeHypothesisCenterFrequencyHz;
    }

    public int activeHypothesisCenterHitCount() {
        return activeHypothesisCenterHitCount;
    }

    public int representativeCompetitionObservationCount() {
        return representativeCompetitionObservationCount;
    }

    public int representativeCompetitionTrackedWinFrames() {
        return representativeCompetitionTrackedWinFrames;
    }

    public int representativeCompetitionHypothesisWinFrames() {
        return representativeCompetitionHypothesisWinFrames;
    }

    public int representativeCompetitionTieFrames() {
        return representativeCompetitionTieFrames;
    }

    public int representativeCompetitionHypothesisCurrentWinStreak() {
        return representativeCompetitionHypothesisCurrentWinStreak;
    }

    public int representativeCompetitionHypothesisMaxWinStreak() {
        return representativeCompetitionHypothesisMaxWinStreak;
    }

    public int activeCenterCompetitionObservationCount() {
        return activeCenterCompetitionObservationCount;
    }

    public int activeCenterCompetitionTrackedWinFrames() {
        return activeCenterCompetitionTrackedWinFrames;
    }

    public int activeCenterCompetitionHypothesisWinFrames() {
        return activeCenterCompetitionHypothesisWinFrames;
    }

    public int activeCenterCompetitionTieFrames() {
        return activeCenterCompetitionTieFrames;
    }

    public int activeCenterCompetitionHypothesisCurrentWinStreak() {
        return activeCenterCompetitionHypothesisCurrentWinStreak;
    }

    public int activeCenterCompetitionHypothesisMaxWinStreak() {
        return activeCenterCompetitionHypothesisMaxWinStreak;
    }

    public int effectiveTrackedToneFrequencyHz() {
        if (representativeLockedToneFrameCount > 0) {
            return representativeLockedToneFrequencyHz;
        }
        return targetToneFrequencyHz;
    }

    public int currentThreshold() {
        return currentThreshold;
    }

    public int releaseThreshold() {
        return releaseThreshold;
    }

    public int noiseFloorEstimate() {
        return noiseFloorEstimate;
    }

    public int signalFloorEstimate() {
        return signalFloorEstimate;
    }

    public double lastRmsAmplitude() {
        return lastRmsAmplitude;
    }

    public double lastToneRmsAmplitude() {
        return lastToneRmsAmplitude;
    }

    public double lastWidebandResidualRmsAmplitude() {
        return lastWidebandResidualRmsAmplitude;
    }

    public double toneDominanceRatio() {
        return toneDominanceRatio;
    }

    public double narrowbandIsolationRatio() {
        return narrowbandIsolationRatio;
    }

    public double peakToneRmsAmplitude() {
        return peakToneRmsAmplitude;
    }

    public double peakNarrowbandIsolationRatio() {
        return peakNarrowbandIsolationRatio;
    }

    public int processedFrameCount() {
        return processedFrameCount;
    }

    public int lockedFrameCount() {
        return lockedFrameCount;
    }

    public int toneActiveFrameCount() {
        return toneActiveFrameCount;
    }

    public int toneActiveUnlockedFrameCount() {
        return toneActiveUnlockedFrameCount;
    }

    public int consecutiveLockedFrames() {
        return consecutiveLockedFrames;
    }

    public int maxConsecutiveLockedFrames() {
        return maxConsecutiveLockedFrames;
    }

    public int consecutiveToneActiveUnlockedFrames() {
        return consecutiveToneActiveUnlockedFrames;
    }

    public int maxConsecutiveToneActiveUnlockedFrames() {
        return maxConsecutiveToneActiveUnlockedFrames;
    }

    public int pendingRetuneCandidateFrequencyHz() {
        return pendingRetuneCandidateFrequencyHz;
    }

    public int pendingRetuneCandidateStableScans() {
        return pendingRetuneCandidateStableScans;
    }

    public boolean lockedRetuneGuardHolding() {
        return lockedRetuneGuardHolding;
    }

    public int lockedRetuneGuardCandidateFrequencyHz() {
        return lockedRetuneGuardCandidateFrequencyHz;
    }

    public int lockedRetuneGuardDriftHz() {
        return lockedRetuneGuardDriftHz;
    }

    public int lockedRetuneGuardObservedScans() {
        return lockedRetuneGuardObservedScans;
    }

    public int lockedRetuneGuardRequiredScans() {
        return lockedRetuneGuardRequiredScans;
    }

    public int lockedRetuneGuardRemainingScans() {
        return lockedRetuneGuardRemainingScans;
    }

    public String lockedRetuneGuardBand() {
        return lockedRetuneGuardBand;
    }

    public int preferredWindowWinnerFrequencyHz() {
        return preferredWindowWinnerFrequencyHz;
    }

    public int wideScanWinnerFrequencyHz() {
        return wideScanWinnerFrequencyHz;
    }

    public int acquisitionWinnerFrequencyHz() {
        return acquisitionWinnerFrequencyHz;
    }

    public int effectiveAcquisitionWinnerFrequencyHz() {
        return preferEffectiveTrackedDisplayTone(
                acquisitionWinnerFrequencyHz,
                acquisitionWinnerLocked,
                acquisitionWinnerSelectionScore
        );
    }

    public int finalAdoptedFrequencyHz() {
        return finalAdoptedFrequencyHz;
    }

    public int effectiveFinalAdoptedFrequencyHz() {
        if ("SEARCH_FALLBACK".equals(finalAdoptedSource)) {
            return preferEffectiveTrackedDisplayTone(
                    finalAdoptedFrequencyHz,
                    finalAdoptedLocked,
                    finalAdoptedSelectionScore
            );
        }
        return finalAdoptedFrequencyHz;
    }

    public double preferredWindowWinnerToneRms() {
        return preferredWindowWinnerToneRms;
    }

    public double wideScanWinnerToneRms() {
        return wideScanWinnerToneRms;
    }

    public double acquisitionWinnerToneRms() {
        return acquisitionWinnerToneRms;
    }

    public double finalAdoptedToneRms() {
        return finalAdoptedToneRms;
    }

    public double preferredWindowWinnerSelectionScore() {
        return preferredWindowWinnerSelectionScore;
    }

    public double wideScanWinnerSelectionScore() {
        return wideScanWinnerSelectionScore;
    }

    public double acquisitionWinnerSelectionScore() {
        return acquisitionWinnerSelectionScore;
    }

    public double finalAdoptedSelectionScore() {
        return finalAdoptedSelectionScore;
    }

    public double preferredWindowWinnerConfidence() {
        return preferredWindowWinnerConfidence;
    }

    public double wideScanWinnerConfidence() {
        return wideScanWinnerConfidence;
    }

    public double acquisitionWinnerConfidence() {
        return acquisitionWinnerConfidence;
    }

    public double finalAdoptedConfidence() {
        return finalAdoptedConfidence;
    }

    public int preferredWindowRunnerUpFrequencyHz() {
        return preferredWindowRunnerUpFrequencyHz;
    }

    public int wideScanRunnerUpFrequencyHz() {
        return wideScanRunnerUpFrequencyHz;
    }

    public int acquisitionRunnerUpFrequencyHz() {
        return acquisitionRunnerUpFrequencyHz;
    }

    public double preferredWindowRunnerUpSelectionScore() {
        return preferredWindowRunnerUpSelectionScore;
    }

    public double wideScanRunnerUpSelectionScore() {
        return wideScanRunnerUpSelectionScore;
    }

    public double acquisitionRunnerUpSelectionScore() {
        return acquisitionRunnerUpSelectionScore;
    }

    public int previousTargetBeforeScanFrequencyHz() {
        return previousTargetBeforeScanFrequencyHz;
    }

    public double previousTargetBeforeScanToneRms() {
        return previousTargetBeforeScanToneRms;
    }

    public double previousTargetBeforeScanSelectionScore() {
        return previousTargetBeforeScanSelectionScore;
    }

    public boolean previousTargetBeforeScanLocked() {
        return previousTargetBeforeScanLocked;
    }

    public boolean preferredWindowWinnerLocked() {
        return preferredWindowWinnerLocked;
    }

    public boolean wideScanWinnerLocked() {
        return wideScanWinnerLocked;
    }

    public boolean acquisitionWinnerLocked() {
        return acquisitionWinnerLocked;
    }

    public boolean finalAdoptedLocked() {
        return finalAdoptedLocked;
    }

    public String acquisitionWinnerSource() {
        return acquisitionWinnerSource;
    }

    public String finalAdoptedSource() {
        return finalAdoptedSource;
    }

    public String acquisitionDecisionDetail() {
        return acquisitionDecisionDetail;
    }

    public String finalAdoptionDetail() {
        return finalAdoptionDetail;
    }

    public String hypothesisCompetitionSummary() {
        return "rep(obs=" + representativeCompetitionObservationCount
                + ", trk=" + representativeCompetitionTrackedWinFrames
                + ", hyp=" + representativeCompetitionHypothesisWinFrames
                + ", tie=" + representativeCompetitionTieFrames
                + ", maxHyp=" + representativeCompetitionHypothesisMaxWinStreak
                + ") act(obs=" + activeCenterCompetitionObservationCount
                + ", trk=" + activeCenterCompetitionTrackedWinFrames
                + ", hyp=" + activeCenterCompetitionHypothesisWinFrames
                + ", tie=" + activeCenterCompetitionTieFrames
                + ", maxHyp=" + activeCenterCompetitionHypothesisMaxWinStreak
                + ")";
    }

    public String preferredWindowTopCandidatesSummary() {
        return preferredWindowTopCandidatesSummary;
    }

    public String wideScanTopCandidatesSummary() {
        return wideScanTopCandidatesSummary;
    }

    public double lockedFrameRatio() {
        if (processedFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, lockedFrameCount / (double) processedFrameCount));
    }

    public double toneActiveUnlockedFrameRatio() {
        if (toneActiveFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, toneActiveUnlockedFrameCount / (double) toneActiveFrameCount));
    }

    public int recentLockedFrameCount() {
        return countRecentStateCodes('L', 'l');
    }

    public int recentActiveLockedFrameCount() {
        return countRecentStateCodes('L');
    }

    public int recentQuietLockedFrameCount() {
        return countRecentStateCodes('l');
    }

    public int recentActiveUnlockedFrameCount() {
        return countRecentStateCodes('u');
    }

    public int recentSearchFrameCount() {
        return countRecentStateCodes('.');
    }

    public double recentLockedFrameRatio() {
        return ratioWithinRecentHistory(recentLockedFrameCount());
    }

    public double recentActiveUnlockedFrameRatio() {
        return ratioWithinRecentHistory(recentActiveUnlockedFrameCount());
    }

    public double recentSearchFrameRatio() {
        return ratioWithinRecentHistory(recentSearchFrameCount());
    }

    public int recentNearTargetLockedFrameCount() {
        return countRecentLockedFramesWithinOffset(15, true);
    }

    public int recentFarOffTargetLockedFrameCount() {
        return countRecentLockedFramesWithinOffset(45, false);
    }

    public double recentNearTargetLockedFrameRatio() {
        return ratioWithinRecentLockedFrames(recentNearTargetLockedFrameCount());
    }

    public double recentFarOffTargetLockedFrameRatio() {
        return ratioWithinRecentLockedFrames(recentFarOffTargetLockedFrameCount());
    }

    public int totalToneOnEvents() {
        return totalToneOnEvents;
    }

    public int totalToneOffEvents() {
        return totalToneOffEvents;
    }

    public int frameGapResetCount() {
        return frameGapResetCount;
    }

    public long lastFrameGapMs() {
        return lastFrameGapMs;
    }

    public long lastFrameGapResetThresholdMs() {
        return lastFrameGapResetThresholdMs;
    }

    public long worstFrameGapMs() {
        return worstFrameGapMs;
    }

    public long lastFrameGapResetAtMs() {
        return lastFrameGapResetAtMs;
    }

    public CwToneEvent lastEvent() {
        return lastEvent;
    }

    private int countRecentStateCodes(char... acceptedCodes) {
        if (acceptedCodes == null || acceptedCodes.length == 0 || recentHistoryFrameCount <= 0) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(recentHistoryFrameCount, recentFrontEndStateHistory.length);
        for (int index = 0; index < limit; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            for (char acceptedCode : acceptedCodes) {
                if (stateCode == acceptedCode) {
                    count += 1;
                    break;
                }
            }
        }
        return count;
    }

    private int countRecentLockedFramesWithinOffset(int thresholdHz, boolean withinThreshold) {
        if (recentHistoryFrameCount <= 0 || thresholdHz < 0) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            if (stateCode != 'L' && stateCode != 'l') {
                continue;
            }
            int absoluteOffsetHz = Math.abs(recentTrackingOffsetHistoryHz[index]);
            if (withinThreshold ? absoluteOffsetHz <= thresholdHz : absoluteOffsetHz >= thresholdHz) {
                count += 1;
            }
        }
        return count;
    }

    private double ratioWithinRecentHistory(int count) {
        if (recentHistoryFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, count / (double) recentHistoryFrameCount));
    }

    private double ratioWithinRecentLockedFrames(int count) {
        int recentLockedFrameCount = recentLockedFrameCount();
        if (recentLockedFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, count / (double) recentLockedFrameCount));
    }

    private int preferEffectiveTrackedDisplayTone(
            int rawFrequencyHz,
            boolean rawLocked,
            double rawSelectionScore
    ) {
        int effectiveTrackedToneHz = effectiveTrackedToneFrequencyHz();
        if (rawLocked
                || representativeLockedToneFrameCount <= 0
                || effectiveTrackedToneHz <= 0
                || Math.abs(rawFrequencyHz - effectiveTrackedToneHz) < 40) {
            return rawFrequencyHz;
        }
        if (rawFrequencyHz <= 0 || rawSelectionScore <= 0.0d) {
            return effectiveTrackedToneHz;
        }
        return rawFrequencyHz;
    }
}
