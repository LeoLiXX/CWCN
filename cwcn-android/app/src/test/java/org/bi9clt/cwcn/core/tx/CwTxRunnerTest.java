package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class CwTxRunnerTest {
    @Test
    public void runPlanBlockingCompletesAndEmitsProgressSnapshots() {
        FakeAudioOutput audioOutput = new FakeAudioOutput();
        CwTxRunner runner = new CwTxRunner(audioOutput);
        CwTxPlan plan = new CwTxEngine().buildPlan("EE", 20, 700);
        List<CwTxPlaybackSnapshot> snapshots = new ArrayList<>();

        runner.runPlanBlocking(plan, snapshots::add);

        assertFalse(snapshots.isEmpty());
        assertEquals(CwTxState.COMPLETED, snapshots.get(snapshots.size() - 1).state());
        assertEquals(plan.totalDurationMs(), snapshots.get(snapshots.size() - 1).elapsedMs());
        assertTrue(audioOutput.events.contains("tone:700:60"));
        assertTrue(audioOutput.events.contains("silence:180"));
    }

    @Test
    public void stopDuringPlaybackEndsInStoppedState() throws Exception {
        BlockingFakeAudioOutput audioOutput = new BlockingFakeAudioOutput();
        CwTxRunner runner = new CwTxRunner(audioOutput);
        CwTxPlan plan = new CwTxEngine().buildPlan("TTTT", 5, 650);
        List<CwTxPlaybackSnapshot> snapshots = new ArrayList<>();

        Thread thread = new Thread(() -> runner.runPlanBlocking(plan, snapshots::add));
        thread.start();
        audioOutput.awaitFirstTone();
        runner.stop();
        thread.join(2000);

        assertFalse(thread.isAlive());
        assertFalse(snapshots.isEmpty());
        assertEquals(CwTxState.STOPPED, snapshots.get(snapshots.size() - 1).state());
    }

    private static final class FakeAudioOutput implements CwTxAudioOutput {
        private final List<String> events = new ArrayList<>();

        @Override
        public void playTone(int frequencyHz, int durationMs) {
            events.add("tone:" + frequencyHz + ":" + durationMs);
        }

        @Override
        public void playSilence(int durationMs) {
            events.add("silence:" + durationMs);
        }

        @Override
        public void stop() {
            events.add("stop");
        }
    }

    private static final class BlockingFakeAudioOutput implements CwTxAudioOutput {
        private boolean firstToneStarted;

        @Override
        public synchronized void playTone(int frequencyHz, int durationMs) throws InterruptedException {
            firstToneStarted = true;
            notifyAll();
            Thread.sleep(durationMs * 10L);
        }

        @Override
        public void playSilence(int durationMs) throws InterruptedException {
            Thread.sleep(durationMs * 10L);
        }

        @Override
        public void stop() {
        }

        public synchronized void awaitFirstTone() throws InterruptedException {
            long deadline = System.currentTimeMillis() + 1000L;
            while (!firstToneStarted && System.currentTimeMillis() < deadline) {
                wait(25L);
            }
        }
    }
}
