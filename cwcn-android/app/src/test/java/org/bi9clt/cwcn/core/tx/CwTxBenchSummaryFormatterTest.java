package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxBenchSummaryFormatterTest {
    @Test
    public void usbPermissionMissingSummaryIsActionable() {
        String summary = CwTxBenchSummaryFormatter.format(
                "rig-text:usb-serial-keyer",
                "USB Serial Keyer Adapter",
                false,
                false,
                "USB permission missing.",
                "usb-serial-no-permission",
                null,
                null,
                "Press Request USB Permission."
        );

        assertTrue(summary.contains("blocked by missing USB permission"));
        assertTrue(summary.contains("Next: Press Request USB Permission."));
    }

    @Test
    public void completedUsbReadySummaryKeepsRecentOutcome() {
        String summary = CwTxBenchSummaryFormatter.format(
                "rig-text:usb-serial-keyer",
                "USB Serial Keyer Adapter",
                true,
                false,
                "Ready.",
                "usb-serial-ready",
                "19:27:40 - Locked target missing (usb-serial-target-missing). Next: Reconnect the locked USB keyer.",
                CwTxState.COMPLETED,
                "Load DIT Test or VVV Test."
        );

        assertTrue(summary.contains("Last TX completed."));
        assertTrue(summary.contains("ready for a short bench transmission"));
        assertTrue(summary.contains("Recent issue: 19:27:40 - Locked target missing"));
    }

    @Test
    public void mockUsbBackendUsesUsbBenchSummaryPath() {
        String summary = CwTxBenchSummaryFormatter.format(
                "rig-text:usb-serial-keyer-mock",
                "Mock USB Serial Keyer Adapter",
                false,
                false,
                "Mock mode: target device is attached, but Android USB permission has not been granted yet.",
                "usb-serial-no-permission",
                null,
                null,
                "Press Request USB Permission."
        );

        assertTrue(summary.contains("blocked by missing USB permission"));
    }

    @Test
    public void localSidetoneReadySummaryMentionsDryRun() {
        String summary = CwTxBenchSummaryFormatter.format(
                "local-sidetone",
                "Local Sidetone",
                true,
                false,
                "Ready.",
                null,
                null,
                null,
                "Press Start TX for a dry run."
        );

        assertTrue(summary.contains("dry-run TX check"));
        assertTrue(summary.contains("Next: Press Start TX for a dry run."));
    }
}
