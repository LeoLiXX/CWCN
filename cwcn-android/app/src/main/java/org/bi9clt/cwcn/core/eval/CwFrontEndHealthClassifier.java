package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

public final class CwFrontEndHealthClassifier {
    public static final String QUALITY_NA = "NA";
    public static final String QUALITY_WRONG = "WRONG";
    public static final String QUALITY_GOOD = "GOOD";
    public static final String QUALITY_DROP = "DROP";
    public static final String QUALITY_MISS = "MISS";
    public static final String QUALITY_WEAK = "WEAK";

    public static final String BOTTLENECK_NA = "NA";
    public static final String BOTTLENECK_OK = "OK";
    public static final String BOTTLENECK_TRK = "TRK";
    public static final String BOTTLENECK_SIG = "SIG";

    private CwFrontEndHealthClassifier() {
    }

    public static String qualityCode(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return QUALITY_NA;
        }
        return qualityCode(
                snapshot.targetToneLocked(),
                snapshot.lastEvent() != null && snapshot.lastEvent().type() == CwToneEvent.Type.TONE_OFF,
                snapshot.peakToneRmsAmplitude(),
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames(),
                snapshot.toneActiveUnlockedFrameRatio(),
                snapshot.maxConsecutiveToneActiveUnlockedFrames(),
                trackingErrorHz(snapshot)
        );
    }

    public static String qualityLabel(CwSignalSnapshot snapshot) {
        return qualityLabel(qualityCode(snapshot), suggestsCleanRelease(snapshot));
    }

    public static String bottleneckCode(CwSignalSnapshot snapshot) {
        return bottleneckCode(qualityCode(snapshot));
    }

    public static String bottleneckLabel(CwSignalSnapshot snapshot) {
        return bottleneckLabel(bottleneckCode(snapshot));
    }

    public static String qualityCode(
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            int trackingErrorHz
    ) {
        if (!hasFrontEndHistory(
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        )) {
            return QUALITY_NA;
        }
        if (suggestsWrongToneLock(
                finalToneLocked,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                trackingErrorHz
        )) {
            return QUALITY_WRONG;
        }
        if ((finalToneLocked || suggestsCleanRelease(
                finalToneLocked,
                endedOnToneOffEvent,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames
        ))
                && peakNarrowbandIsolationRatio >= 0.55d
                && lockedFrameRatio >= 0.25d
                && maxConsecutiveLockedFrames >= 4) {
            return QUALITY_GOOD;
        }
        if (!finalToneLocked && suggestsEarlierHealthyLock(
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        )) {
            return QUALITY_DROP;
        }
        if (suggestsSignalLoss(
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        )) {
            return QUALITY_MISS;
        }
        return QUALITY_WEAK;
    }

    public static String qualityLabel(String qualityCode, boolean cleanRelease) {
        switch (qualityCode) {
            case QUALITY_WRONG:
                return "Strong lock on off-target tone";
            case QUALITY_GOOD:
                return cleanRelease
                        ? "Healthy lock with clean release"
                        : "Healthy lock retained";
            case QUALITY_DROP:
                return "Earlier lock, later drop";
            case QUALITY_MISS:
                return "No convincing lock formed";
            case QUALITY_WEAK:
                return "Partial / unstable acquisition";
            default:
                return "No front-end history available";
        }
    }

    public static String bottleneckCode(String qualityCode) {
        switch (qualityCode) {
            case QUALITY_GOOD:
                return BOTTLENECK_OK;
            case QUALITY_WRONG:
                return BOTTLENECK_TRK;
            case QUALITY_DROP:
            case QUALITY_MISS:
            case QUALITY_WEAK:
                return BOTTLENECK_SIG;
            default:
                return BOTTLENECK_NA;
        }
    }

    public static String bottleneckLabel(String bottleneckCode) {
        switch (bottleneckCode) {
            case BOTTLENECK_OK:
                return "Front-end looks healthy";
            case BOTTLENECK_TRK:
                return "Wrong-tone acquisition / tracking";
            case BOTTLENECK_SIG:
                return "Front-end signal/timing acquisition";
            default:
                return "No diagnosis yet";
        }
    }

    public static String reason(CwSignalSnapshot snapshot) {
        if (!hasFrontEndHistory(snapshot)) {
            return "No reliable narrow-band tone has stood out yet.";
        }
        if (suggestsWrongToneLock(snapshot)) {
            return "The front end is locked confidently, but the tracked tone is sitting far enough away from the preferred pitch that this looks like wrong-tone acquisition.";
        }
        if (suggestsCleanRelease(snapshot)) {
            return "Active tone windows stayed comfortably locked, and the latest unlocked state looks like a normal tone-off tail.";
        }
        if (!snapshot.targetToneLocked() && suggestsEarlierHealthyLock(snapshot)) {
            return "The front end had a healthy lock earlier in this run, but is not locked on the latest frame.";
        }
        if (suggestsSignalLoss(snapshot)) {
            return "Front-end history never formed a convincing narrow-band lock, so this still looks like acquisition loss.";
        }
        if (snapshot.targetToneLocked()) {
            return "A lock exists, but tone separation from noise or interference is still not very comfortable.";
        }
        return "A candidate tone is present, but lock confidence is still below the stable range.";
    }

    public static String recentTrendLabel(CwSignalSnapshot snapshot) {
        if (snapshot == null || snapshot.recentHistoryFrameCount() <= 0) {
            return "No recent window yet";
        }
        if (suggestsWrongToneLock(snapshot)
                || snapshot.recentFarOffTargetLockedFrameRatio() >= 0.35d) {
            return "Recent window is spending meaningful lock time off target";
        }
        if (snapshot.recentActiveUnlockedFrameRatio() >= 0.25d) {
            return "Recent window is showing repeated active unlock pressure";
        }
        if (snapshot.recentLockedFrameRatio() >= 0.60d
                && snapshot.recentNearTargetLockedFrameRatio() >= 0.70d) {
            return "Recent window is mostly locked near target";
        }
        if (snapshot.recentSearchFrameRatio() >= 0.60d) {
            return "Recent window is mostly idle/search";
        }
        if (snapshot.recentLockedFrameRatio() >= snapshot.recentSearchFrameRatio()) {
            return "Recent window is mixed but lock still outweighs search";
        }
        return "Recent window is mixed with limited stable lock";
    }

    public static String liveCheckHint(CwSignalSnapshot snapshot) {
        if (!hasFrontEndHistory(snapshot)) {
            return "Start input and compare tracked tone against the preferred target before judging downstream decode quality.";
        }
        if (suggestsWrongToneLock(snapshot)) {
            return "Verify preferred tone versus actual monitor pitch first; wrong-tone acquisition is the main risk right now.";
        }
        if (snapshot.recentFarOffTargetLockedFrameRatio() >= 0.35d) {
            return "Watch offset history closely; recent lock is drifting far enough from target to question frequency alignment before decoder behavior.";
        }
        if (snapshot.recentActiveUnlockedFrameRatio() >= 0.25d) {
            return "Watch active unlock bursts before tuning decoder/interpreter rules; the front end is still losing lock during live tone windows.";
        }
        if (snapshot.recentSearchFrameRatio() >= 0.60d) {
            return "The front end is spending most recent frames searching, so tone strength/noise conditions are still the first thing to improve.";
        }
        if (suggestsCleanRelease(snapshot)) {
            return "The latest state looks like a clean tone-off tail, so compare the next active segment rather than overreacting to the final unlocked frame.";
        }
        if (snapshot.recentLockedFrameRatio() >= 0.60d) {
            return "Recent lock looks stable enough that timing/decoder/interpreter behavior is now worth inspecting.";
        }
        return "Keep comparing recent lock, search, and offset behavior together; the front end is not failing outright, but it is not comfortably stable yet.";
    }

    public static boolean suggestsWrongToneLock(CwSignalSnapshot snapshot) {
        return snapshot != null
                && suggestsWrongToneLock(
                snapshot.targetToneLocked(),
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames(),
                trackingErrorHz(snapshot)
        );
    }

    public static boolean suggestsCleanRelease(CwSignalSnapshot snapshot) {
        return snapshot != null
                && suggestsCleanRelease(
                snapshot.targetToneLocked(),
                snapshot.lastEvent() != null && snapshot.lastEvent().type() == CwToneEvent.Type.TONE_OFF,
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames(),
                snapshot.toneActiveUnlockedFrameRatio(),
                snapshot.maxConsecutiveToneActiveUnlockedFrames()
        );
    }

    public static boolean suggestsEarlierHealthyLock(CwSignalSnapshot snapshot) {
        return snapshot != null
                && suggestsEarlierHealthyLock(
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames()
        );
    }

    public static boolean suggestsSignalLoss(CwSignalSnapshot snapshot) {
        return snapshot != null
                && suggestsSignalLoss(
                snapshot.peakToneRmsAmplitude(),
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames()
        );
    }

    public static boolean hasFrontEndHistory(CwSignalSnapshot snapshot) {
        return snapshot != null
                && hasFrontEndHistory(
                snapshot.peakToneRmsAmplitude(),
                snapshot.peakNarrowbandIsolationRatio(),
                snapshot.lockedFrameRatio(),
                snapshot.maxConsecutiveLockedFrames()
        );
    }

    public static boolean suggestsWrongToneLock(
            boolean finalToneLocked,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            int trackingErrorHz
    ) {
        return finalToneLocked
                && Math.abs(trackingErrorHz) >= 45
                && peakNarrowbandIsolationRatio >= 0.60d
                && lockedFrameRatio >= 0.30d
                && maxConsecutiveLockedFrames >= 4;
    }

    public static boolean suggestsCleanRelease(
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames
    ) {
        return !finalToneLocked
                && endedOnToneOffEvent
                && suggestsEarlierHealthyLock(
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        )
                && toneActiveUnlockedFrameRatio <= 0.16d
                && maxConsecutiveToneActiveUnlockedFrames <= 1;
    }

    public static boolean suggestsEarlierHealthyLock(
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames
    ) {
        return peakNarrowbandIsolationRatio >= 0.55d
                && lockedFrameRatio >= 0.15d
                && maxConsecutiveLockedFrames >= 3;
    }

    public static boolean suggestsSignalLoss(
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames
    ) {
        return peakToneRmsAmplitude > 0.0d
                && peakNarrowbandIsolationRatio < 0.35d
                && lockedFrameRatio < 0.08d
                && maxConsecutiveLockedFrames < 2;
    }

    public static boolean hasFrontEndHistory(
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames
    ) {
        return peakToneRmsAmplitude > 0.0d
                || peakNarrowbandIsolationRatio > 0.0d
                || lockedFrameRatio > 0.0d
                || maxConsecutiveLockedFrames > 0;
    }

    public static int trackingErrorHz(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.targetToneFrequencyHz() - snapshot.preferredToneFrequencyHz();
    }
}
