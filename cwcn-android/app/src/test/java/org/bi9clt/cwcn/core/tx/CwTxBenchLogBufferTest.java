package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxBenchLogBufferTest {
    @Test
    public void renderMultilineReturnsEmptyStateWhenNoEntriesExist() {
        CwTxBenchLogBuffer buffer = new CwTxBenchLogBuffer(4);

        assertTrue(buffer.isEmpty());
        assertEquals("No bench events yet.", buffer.renderMultiline());
    }

    @Test
    public void appendFormatsEntriesAndCapsBufferSize() {
        CwTxBenchLogBuffer buffer = new CwTxBenchLogBuffer(2);

        buffer.append("09:00:01", "USB", "Permission requested");
        buffer.append("09:00:02", "TX", "Start requested");
        buffer.append("09:00:03", "USB", "Permission granted");

        assertEquals(2, buffer.size());
        assertEquals(
                "[09:00:02] TX  Start requested\n[09:00:03] USB  Permission granted",
                buffer.renderMultiline()
        );
    }

    @Test
    public void appendNormalizesEmptyAndMultilineValues() {
        CwTxBenchLogBuffer buffer = new CwTxBenchLogBuffer(4);

        buffer.append(null, "", "Permission denied\nCheck OTG");

        assertFalse(buffer.isEmpty());
        assertEquals("[--:--:--] INFO  Permission denied Check OTG", buffer.renderMultiline());
    }
}
