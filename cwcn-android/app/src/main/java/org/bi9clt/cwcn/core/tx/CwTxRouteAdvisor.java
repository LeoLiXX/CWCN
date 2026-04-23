package org.bi9clt.cwcn.core.tx;

public final class CwTxRouteAdvisor {
    private CwTxRouteAdvisor() {
    }

    public static String buildChecklist(CwTxBackend backend, CwTxPlan plan) {
        if (backend == null) {
            return "Select a TX backend first.";
        }
        if ("local-sidetone".equals(backend.id())) {
            return "Local checklist: use this route for dry-run verification, headphones are preferred, and current WPM/tone are applied directly.";
        }
        if ("rig-text:audio-vox-text".equals(backend.id())) {
            return buildAudioVoxChecklist(plan);
        }
        if ("rig-text:usb-serial-keyer".equals(backend.id())) {
            return buildUsbKeyerChecklist(plan);
        }
        return "Rig route checklist: confirm backend readiness first, then verify how this adapter expects keying/PTT/audio to reach the target device.";
    }

    private static String buildAudioVoxChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Audio VOX checklist: connect phone audio to the rig/keyer audio path, enable VOX on the target device, and start with conservative phone volume.");
        if (plan != null) {
            if (plan.toneFrequencyHz() < 500 || plan.toneFrequencyHz() > 900) {
                builder.append("\nTone warning: VOX testing is usually easier around 500-900 Hz.");
            }
            if (plan.wpm() < 10 || plan.wpm() > 28) {
                builder.append("\nWPM warning: initial VOX validation is usually easier around 10-28 WPM.");
            }
            if (plan.totalDurationMs() > 30000) {
                builder.append("\nLength warning: very long over-the-air VOX tests are harder to calibrate; start with a shorter macro.");
            }
        }
        return builder.toString();
    }

    private static String buildUsbKeyerChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("USB RTS/DTR checklist: confirm the USB serial keyer is attached, verify which control line is wired, and keep the first test short.");
        if (plan != null) {
            if (plan.wpm() > 35) {
                builder.append("\nWPM warning: start below 35 WPM until RTS/DTR timing is confirmed on hardware.");
            }
            if (plan.totalDurationMs() > 20000) {
                builder.append("\nLength warning: keep initial hardware-keying tests short so stuck-line issues are easier to recover.");
            }
            builder.append("\nTone note: tone frequency does not affect RTS/DTR keying, only timing does.");
        }
        return builder.toString();
    }
}
