package org.bi9clt.cwcn.core.tx;

import org.bi9clt.cwcn.core.rig.RigControlAdapter;

public final class RigKeyingTxBackend implements CwTxBackend {
    private final RigControlAdapter adapter;
    private final CwTxRunner runner;

    public RigKeyingTxBackend(RigControlAdapter adapter) {
        this.adapter = adapter;
        this.runner = new CwTxRunner(new RigKeyingAudioOutput(adapter));
    }

    public RigControlAdapter rigAdapter() {
        return adapter;
    }

    @Override
    public String id() {
        return "rig-key:" + adapter.id();
    }

    @Override
    public String displayName() {
        return adapter.displayName() + " (Keyed CW)";
    }

    @Override
    public String describeRoute() {
        return adapter.describeCapabilities() + " Keyed CW plan playback is driven through keyDown/keyUp timing.";
    }

    @Override
    public String describeAvailability() {
        return adapter.describeAvailability();
    }

    @Override
    public boolean isReady() {
        return adapter.isReady() && adapter.supportsPttControl();
    }

    @Override
    public boolean supportsLivePlanProfile() {
        return adapter.supportsConfigurableTextToCwProfile();
    }

    @Override
    public boolean usesWpm() {
        return adapter.usesWpmForTextToCwProfile();
    }

    @Override
    public boolean usesToneFrequency() {
        return adapter.usesToneFrequencyForTextToCwProfile();
    }

    @Override
    public boolean supportsProgressSnapshots() {
        return true;
    }

    @Override
    public boolean isRunning() {
        return runner.isRunning();
    }

    @Override
    public boolean start(CwTxPlan plan, CwTxRunner.Listener listener) {
        if (plan == null || listener == null || !isReady() || isRunning()) {
            return false;
        }
        adapter.configureTextToCwProfile(plan.wpm(), plan.toneFrequencyHz());
        return runner.start(plan, snapshot -> listener.onSnapshot(rewriteSnapshot(snapshot)));
    }

    @Override
    public void stop() {
        runner.stop();
        adapter.keyUp();
    }

    private CwTxPlaybackSnapshot rewriteSnapshot(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        String status = snapshot.statusMessage();
        if (snapshot.state() == CwTxState.PLAYING) {
            status = "正在通过独立键控线按节奏发射 CW";
        } else if (snapshot.state() == CwTxState.COMPLETED) {
            status = "独立键控线发射完成";
        } else if (snapshot.state() == CwTxState.STOPPED) {
            status = "独立键控线发射已停止";
        }
        return new CwTxPlaybackSnapshot(
                snapshot.state(),
                snapshot.normalizedText(),
                snapshot.morsePreview(),
                snapshot.completedElementCount(),
                snapshot.totalElementCount(),
                snapshot.elapsedMs(),
                snapshot.totalDurationMs(),
                snapshot.currentElementLabel(),
                snapshot.currentTextIndex(),
                snapshot.toneActive(),
                status
        );
    }

    private static final class RigKeyingAudioOutput implements CwTxAudioOutput {
        private final RigControlAdapter adapter;

        private RigKeyingAudioOutput(RigControlAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
            if (durationMs <= 0) {
                return;
            }
            if (!adapter.keyDown()) {
                throw new IllegalStateException("Rig adapter rejected keyDown.");
            }
            sleepQuietly(durationMs);
        }

        @Override
        public void playSilence(int durationMs) throws InterruptedException {
            if (!adapter.keyUp()) {
                throw new IllegalStateException("Rig adapter rejected keyUp.");
            }
            if (durationMs > 0) {
                sleepQuietly(durationMs);
            }
        }

        @Override
        public void finish() {
            adapter.keyUp();
        }

        @Override
        public void stop() {
            adapter.keyUp();
        }

        private void sleepQuietly(int durationMs) throws InterruptedException {
            int remainingMs = durationMs;
            while (remainingMs > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Rig keying interrupted");
                }
                int sliceMs = Math.min(remainingMs, 25);
                Thread.sleep(sliceMs);
                remainingMs -= sliceMs;
            }
        }
    }
}
