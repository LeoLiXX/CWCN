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
        if (backend.id() != null && backend.id().startsWith("rig-text:usb-serial-keyer")) {
            return buildUsbKeyerChecklist(plan);
        }
        if ("rig-text:hamlib-rigctld".equals(backend.id())) {
            return buildHamlibRigctldChecklist(plan);
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
        builder.append("\nSafety recovery: if the line looks stuck, use Release Key Line first, then Refresh USB Devices.");
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

    private static String buildHamlibRigctldChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hamlib rigctld checklist: confirm Rig Setup is pinned to a network CAT profile, set host/port, and verify rigctld accepts send_morse commands before longer traffic.");
        builder.append("\nRecovery hint: if TX fails immediately, first check network reachability and whether rigctld is already bound to the expected radio.");
        builder.append("\nYaesu note: for FT-710 and nearby FT-series rigs, this is currently the preferred formal test path in CWCN while native Android Yaesu CAT is still pending.");
        if (plan != null) {
            if (plan.wpm() > 35) {
                builder.append("\nWPM warning: start below 35 WPM until the rigctld KEYSPD behavior is confirmed on your target rig.");
            }
            if (plan.toneFrequencyHz() < 400 || plan.toneFrequencyHz() > 900) {
                builder.append("\nPitch note: many radios behave more predictably when CWPITCH starts in the midrange.");
            }
            if (plan.totalDurationMs() > 20000) {
                builder.append("\nLength warning: keep the first network CAT tests short so command/rig behavior is easier to isolate.");
            }
        }
        return builder.toString();
    }
}
