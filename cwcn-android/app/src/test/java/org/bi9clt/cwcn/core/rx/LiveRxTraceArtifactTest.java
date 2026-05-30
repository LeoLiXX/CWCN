package org.bi9clt.cwcn.core.rx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveRxTraceArtifactTest {
    @Test
    public void analysisKeyIncludesReplayRelevantFields() {
        LiveRxTraceArtifact artifact = new LiveRxTraceArtifact(
                123L,
                "session-a",
                "mic",
                "/tmp/capture.wav",
                "/tmp/trace.log",
                456L,
                8000,
                3600L,
                700,
                55
        );

        assertEquals("session-a|/tmp/capture.wav|700|55|3600", artifact.analysisKey());
        assertTrue(artifact.hasTraceLog());
    }

    @Test
    public void sanitizesOptionalReplaySettingsAndFilePresence() {
        LiveRxTraceArtifact artifact = new LiveRxTraceArtifact(
                123L,
                null,
                null,
                "",
                "",
                -1L,
                -8000,
                -3600L,
                0,
                120
        );

        assertEquals("", artifact.sessionLabel());
        assertEquals("", artifact.sourceLabel());
        assertEquals("", artifact.wavFilePath());
        assertEquals("", artifact.logFilePath());
        assertEquals(0L, artifact.durationMs());
        assertEquals(0, artifact.sampleRateHz());
        assertEquals(0L, artifact.sampleCount());
        assertFalse(artifact.hasPreferredToneFrequency());
        assertTrue(artifact.hasSqlLevel());
        assertEquals(120, artifact.sqlLevel());
        assertFalse(artifact.hasReplayableAudio());
        assertFalse(artifact.hasTraceLog());
    }
}
