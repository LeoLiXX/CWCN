package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.rig.RigControlAdapter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RigTextTxBackendTest {
    @Test
    public void backendDoesNotStartWhenAdapterNotReady() {
        RigTextTxBackend backend = new RigTextTxBackend(new FakeRigControlAdapter(false, true));
        CwTxPlan plan = new CwTxEngine().buildPlan("CQ TEST", 18, 650);
        List<CwTxPlaybackSnapshot> snapshots = new ArrayList<>();

        boolean started = backend.start(plan, snapshots::add);

        assertFalse(started);
        assertTrue(snapshots.isEmpty());
    }

    @Test
    public void backendCompletesWhenReadyAdapterAcceptsText() {
        RigTextTxBackend backend = new RigTextTxBackend(new FakeRigControlAdapter(true, true));
        CwTxPlan plan = new CwTxEngine().buildPlan("CQ TEST", 18, 650);
        List<CwTxPlaybackSnapshot> snapshots = new ArrayList<>();

        boolean started = backend.start(plan, snapshots::add);

        assertTrue(started);
        waitFor(() -> hasLastState(snapshots, CwTxState.COMPLETED));
        assertEquals(CwTxState.COMPLETED, snapshots.get(snapshots.size() - 1).state());
        assertEquals(plan.totalDurationMs(), snapshots.get(snapshots.size() - 1).elapsedMs());
    }

    @Test
    public void backendReportsErrorWhenAdapterRejectsText() {
        RigTextTxBackend backend = new RigTextTxBackend(new FakeRigControlAdapter(true, false));
        CwTxPlan plan = new CwTxEngine().buildPlan("CQ TEST", 18, 650);
        List<CwTxPlaybackSnapshot> snapshots = new ArrayList<>();

        boolean started = backend.start(plan, snapshots::add);

        assertTrue(started);
        waitFor(() -> hasLastState(snapshots, CwTxState.ERROR));
        assertEquals(CwTxState.ERROR, snapshots.get(snapshots.size() - 1).state());
        assertTrue(snapshots.get(snapshots.size() - 1).statusMessage().contains("Rig adapter rejected"));
        assertTrue(snapshots.get(snapshots.size() - 1).statusMessage().contains("Ready"));
    }

    @Test
    public void backendExposesWhenRouteUsesWpmButNotTone() {
        RigTextTxBackend backend = new RigTextTxBackend(new FakeRigControlAdapter(true, true, true, false));

        assertTrue(backend.usesWpm());
        assertFalse(backend.usesToneFrequency());
        assertTrue(backend.supportsProgressSnapshots());
    }

    private void waitFor(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 1000L;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(condition.getAsBoolean());
    }

    private boolean hasLastState(List<CwTxPlaybackSnapshot> snapshots, CwTxState state) {
        return !snapshots.isEmpty() && snapshots.get(snapshots.size() - 1).state() == state;
    }

    private static final class FakeRigControlAdapter implements RigControlAdapter {
        private final boolean ready;
        private final boolean sendTextResult;
        private final boolean usesWpm;
        private final boolean usesToneFrequency;

        private FakeRigControlAdapter(boolean ready, boolean sendTextResult) {
            this(ready, sendTextResult, true, true);
        }

        private FakeRigControlAdapter(
                boolean ready,
                boolean sendTextResult,
                boolean usesWpm,
                boolean usesToneFrequency
        ) {
            this.ready = ready;
            this.sendTextResult = sendTextResult;
            this.usesWpm = usesWpm;
            this.usesToneFrequency = usesToneFrequency;
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String displayName() {
            return "Fake Rig";
        }

        @Override
        public String describeCapabilities() {
            return "Fake text-to-CW adapter";
        }

        @Override
        public String describeAvailability() {
            return ready ? "Ready" : "Not ready";
        }

        @Override
        public boolean supportsTextToCw() {
            return true;
        }

        @Override
        public boolean supportsPttControl() {
            return true;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public boolean keyDown() {
            return false;
        }

        @Override
        public boolean keyUp() {
            return true;
        }

        @Override
        public boolean sendText(String text) {
            return sendTextResult;
        }

        @Override
        public boolean usesWpmForTextToCwProfile() {
            return usesWpm;
        }

        @Override
        public boolean usesToneFrequencyForTextToCwProfile() {
            return usesToneFrequency;
        }
    }
}
