package org.bi9clt.cwcn.core.tx;

public final class LocalSidetoneTxBackend implements CwTxBackend {
    private final CwTxRunner txRunner;

    public LocalSidetoneTxBackend(CwTxAudioOutput audioOutput) {
        this.txRunner = new CwTxRunner(audioOutput);
    }

    @Override
    public String id() {
        return "local-sidetone";
    }

    @Override
    public String displayName() {
        return "Local Sidetone";
    }

    @Override
    public String describeRoute() {
        return "Play generated CW locally through the phone audio path for dry-run TX testing.";
    }

    @Override
    public String describeAvailability() {
        return "Ready for local sidetone playback.";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean supportsLivePlanProfile() {
        return true;
    }

    @Override
    public boolean supportsProgressSnapshots() {
        return true;
    }

    @Override
    public boolean isRunning() {
        return txRunner.isRunning();
    }

    @Override
    public boolean start(CwTxPlan plan, CwTxRunner.Listener listener) {
        return txRunner.start(plan, listener);
    }

    @Override
    public void stop() {
        txRunner.stop();
    }
}
