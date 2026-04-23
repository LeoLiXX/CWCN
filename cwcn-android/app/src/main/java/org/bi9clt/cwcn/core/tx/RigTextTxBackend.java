package org.bi9clt.cwcn.core.tx;

import org.bi9clt.cwcn.core.rig.RigControlAdapter;

public final class RigTextTxBackend implements CwTxBackend {
    private final RigControlAdapter adapter;

    private volatile boolean running;

    public RigTextTxBackend(RigControlAdapter adapter) {
        this.adapter = adapter;
    }

    public RigControlAdapter rigAdapter() {
        return adapter;
    }

    @Override
    public String id() {
        return "rig-text:" + adapter.id();
    }

    @Override
    public String displayName() {
        return adapter.displayName();
    }

    @Override
    public String describeRoute() {
        return adapter.describeCapabilities();
    }

    @Override
    public String describeAvailability() {
        return adapter.describeAvailability();
    }

    @Override
    public boolean isReady() {
        return adapter.isReady();
    }

    @Override
    public boolean supportsLivePlanProfile() {
        return adapter.supportsConfigurableTextToCwProfile();
    }

    @Override
    public boolean supportsProgressSnapshots() {
        return false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean start(CwTxPlan plan, CwTxRunner.Listener listener) {
        if (plan == null || listener == null || running || !isReady()) {
            return false;
        }
        adapter.configureTextToCwProfile(plan.wpm(), plan.toneFrequencyHz());
        running = true;
        listener.onSnapshot(new CwTxPlaybackSnapshot(
                CwTxState.PLAYING,
                plan.normalizedText(),
                plan.morsePreview(),
                0,
                plan.elements().size(),
                0,
                plan.totalDurationMs(),
                "",
                false,
                "Sending text to rig adapter: " + adapter.displayName()
        ));
        boolean sent = false;
        try {
            sent = adapter.sendText(plan.normalizedText());
        } catch (RuntimeException exception) {
            listener.onSnapshot(new CwTxPlaybackSnapshot(
                    CwTxState.ERROR,
                    plan.normalizedText(),
                    plan.morsePreview(),
                    0,
                    plan.elements().size(),
                    0,
                    plan.totalDurationMs(),
                    "",
                    false,
                    "Rig adapter error: " + exception.getMessage()
            ));
            return true;
        } finally {
            running = false;
        }

        listener.onSnapshot(new CwTxPlaybackSnapshot(
                sent ? CwTxState.COMPLETED : CwTxState.ERROR,
                plan.normalizedText(),
                plan.morsePreview(),
                sent ? plan.elements().size() : 0,
                plan.elements().size(),
                sent ? plan.totalDurationMs() : 0,
                plan.totalDurationMs(),
                "",
                false,
                sent
                        ? "Rig adapter accepted text TX request."
                        : "Rig adapter rejected the text TX request."
        ));
        return true;
    }

    @Override
    public void stop() {
        if (running) {
            adapter.keyUp();
        }
        running = false;
    }
}
