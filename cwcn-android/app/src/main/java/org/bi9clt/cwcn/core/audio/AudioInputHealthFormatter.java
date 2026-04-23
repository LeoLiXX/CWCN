package org.bi9clt.cwcn.core.audio;

public final class AudioInputHealthFormatter {
    private AudioInputHealthFormatter() {
    }

    public static String summaryLabel(AudioInputHealthSnapshot snapshot) {
        if (snapshot == null || snapshot.totalFrames() <= 0) {
            return "No microphone input frames yet";
        }
        if (snapshot.recentClippingFrameRatio() >= 0.10d) {
            return "Recent input is clipping";
        }
        if (snapshot.recentQuietFrameRatio() >= 0.60d) {
            return "Recent input is mostly too quiet";
        }
        if (snapshot.recentHotFrameRatio() >= 0.50d) {
            return "Recent input is running hot";
        }
        if (snapshot.recentUsableFrameRatio() >= 0.50d) {
            return "Recent input level looks usable";
        }
        return "Recent input level is mixed";
    }

    public static String coachHint(AudioInputHealthSnapshot snapshot) {
        if (snapshot == null || snapshot.totalFrames() <= 0) {
            return "Start the microphone source and watch the recent input window before tuning front-end thresholds.";
        }
        if (snapshot.recentClippingFrameRatio() >= 0.10d) {
            return "Back off microphone gain or monitor volume first; clipped audio will poison the CW front end before decoder tuning matters.";
        }
        if (snapshot.recentQuietFrameRatio() >= 0.60d) {
            return "Raise mic drive or move the monitor tone closer; the front end is probably being asked to lock onto an input that is still too weak.";
        }
        if (snapshot.recentHotFrameRatio() >= 0.50d) {
            return "Input level is strong but close to overload, so trim gain a little before blaming wrong-tone or timing behavior.";
        }
        if (snapshot.recentUsableFrameRatio() >= 0.50d) {
            return "Input level looks healthy enough that front-end lock and tracked-tone behavior are the next things to judge.";
        }
        return "Keep comparing input level and front-end lock together; neither is clearly broken, but the mic feed is not comfortably stable yet.";
    }

    public static String compactWindowSummary(AudioInputHealthSnapshot snapshot) {
        if (snapshot == null || snapshot.recentHistoryFrameCount() <= 0) {
            return "window not started";
        }
        return "usable "
                + Math.round(snapshot.recentUsableFrameRatio() * 100.0d)
                + "%, quiet "
                + Math.round(snapshot.recentQuietFrameRatio() * 100.0d)
                + "%, hot "
                + Math.round(snapshot.recentHotFrameRatio() * 100.0d)
                + "%, clip "
                + Math.round(snapshot.recentClippingFrameRatio() * 100.0d)
                + "%";
    }

    public static String stateHistory(AudioInputHealthSnapshot snapshot) {
        if (snapshot == null || snapshot.recentHistoryFrameCount() <= 0) {
            return "(empty)";
        }
        return new String(snapshot.recentStateHistory());
    }
}
