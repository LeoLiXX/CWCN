package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

public final class CwFrontEndHealthClassifier {
    private CwFrontEndHealthClassifier() {
    }

    public static String qualityCode(CwSignalSnapshot snapshot) {
        if (!hasFrontEndHistory(snapshot)) {
            return "NA";
        }
        if (suggestsWrongToneLock(snapshot)) {
            return "WRONG";
        }
        if ((snapshot.targetToneLocked() || suggestsCleanRelease(snapshot))
                && snapshot.peakNarrowbandIsolationRatio() >= 0.55d
                && snapshot.lockedFrameRatio() >= 0.25d
                && snapshot.maxConsecutiveLockedFrames() >= 4) {
            return "GOOD";
        }
        if (!snapshot.targetToneLocked() && suggestsEarlierHealthyLock(snapshot)) {
            return "DROP";
        }
        if (suggestsSignalLoss(snapshot)) {
            return "MISS";
        }
        return "WEAK";
    }

    public static String qualityLabel(CwSignalSnapshot snapshot) {
        switch (qualityCode(snapshot)) {
            case "WRONG":
                return "Strong lock on off-target tone";
            case "GOOD":
                return suggestsCleanRelease(snapshot)
                        ? "Healthy lock with clean release"
                        : "Healthy lock retained";
            case "DROP":
                return "Earlier lock, later drop";
            case "MISS":
                return "No convincing lock formed";
            case "WEAK":
                return "Partial / unstable acquisition";
            default:
                return "No front-end history available";
        }
    }

    public static String bottleneckCode(CwSignalSnapshot snapshot) {
        String qualityCode = qualityCode(snapshot);
        switch (qualityCode) {
            case "GOOD":
                return "OK";
            case "WRONG":
                return "TRK";
            case "DROP":
            case "MISS":
            case "WEAK":
                return "SIG";
            default:
                return "NA";
        }
    }

    public static String bottleneckLabel(CwSignalSnapshot snapshot) {
        switch (bottleneckCode(snapshot)) {
            case "OK":
                return "Front-end looks healthy";
            case "TRK":
                return "Wrong-tone acquisition / tracking";
            case "SIG":
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

    public static boolean suggestsWrongToneLock(CwSignalSnapshot snapshot) {
        return snapshot != null
                && snapshot.targetToneLocked()
                && Math.abs(trackingErrorHz(snapshot)) >= 45
                && snapshot.peakNarrowbandIsolationRatio() >= 0.60d
                && snapshot.lockedFrameRatio() >= 0.30d
                && snapshot.maxConsecutiveLockedFrames() >= 4;
    }

    public static boolean suggestsCleanRelease(CwSignalSnapshot snapshot) {
        return snapshot != null
                && !snapshot.targetToneLocked()
                && snapshot.lastEvent() != null
                && snapshot.lastEvent().type() == CwToneEvent.Type.TONE_OFF
                && suggestsEarlierHealthyLock(snapshot)
                && snapshot.toneActiveUnlockedFrameRatio() <= 0.16d
                && snapshot.maxConsecutiveToneActiveUnlockedFrames() <= 1;
    }

    public static boolean suggestsEarlierHealthyLock(CwSignalSnapshot snapshot) {
        return snapshot != null
                && snapshot.peakNarrowbandIsolationRatio() >= 0.55d
                && snapshot.lockedFrameRatio() >= 0.15d
                && snapshot.maxConsecutiveLockedFrames() >= 3;
    }

    public static boolean suggestsSignalLoss(CwSignalSnapshot snapshot) {
        return snapshot != null
                && snapshot.peakToneRmsAmplitude() > 0.0d
                && snapshot.peakNarrowbandIsolationRatio() < 0.35d
                && snapshot.lockedFrameRatio() < 0.08d
                && snapshot.maxConsecutiveLockedFrames() < 2;
    }

    public static boolean hasFrontEndHistory(CwSignalSnapshot snapshot) {
        return snapshot != null
                && (snapshot.peakToneRmsAmplitude() > 0.0d
                || snapshot.peakNarrowbandIsolationRatio() > 0.0d
                || snapshot.lockedFrameRatio() > 0.0d
                || snapshot.maxConsecutiveLockedFrames() > 0);
    }

    public static int trackingErrorHz(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.targetToneFrequencyHz() - snapshot.preferredToneFrequencyHz();
    }
}
