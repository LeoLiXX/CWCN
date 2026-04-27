package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxBenchReportFormatterTest {
    @Test
    public void formatBuildsReadableSectionedReport() {
        String report = CwTxBenchReportFormatter.format(
                "USB keyer route is ready.",
                "19:27:40 - Locked target missing (usb-serial-target-missing). Next: Reconnect the locked USB keyer.",
                "Backend: USB Serial Keyer",
                "WPM: 12",
                "Diagnostic stage: Ready",
                "State: completed",
                "Progress: 100%",
                "[09:00:01] TX  Playback completed."
        );

        assertTrue(report.contains("== Bench Summary =="));
        assertTrue(report.contains("== Recent USB Issue =="));
        assertTrue(report.contains("== Backend =="));
        assertTrue(report.contains("== USB Route =="));
        assertTrue(report.contains("== Bench Log =="));
        assertTrue(report.contains("Playback completed."));
        assertTrue(report.contains("Locked target missing"));
    }

    @Test
    public void formatFallsBackForEmptySections() {
        String report = CwTxBenchReportFormatter.format("", null, null, " ", "", "", "", "");

        assertEquals(
                "== Bench Summary ==\n(none)\n\n== Backend ==\n(none)\n\n== Plan ==\n(none)\n\n== USB Route ==\n(none)\n\n== Playback Status ==\n(none)\n\n== Playback Progress ==\n(none)\n\n== Bench Log ==\n(none)",
                report
        );
    }
}
