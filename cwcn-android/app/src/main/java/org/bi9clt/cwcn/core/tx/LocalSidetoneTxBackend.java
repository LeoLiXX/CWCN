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
        return "本地侧音";
    }

    @Override
    public String describeRoute() {
        return "通过手机音频链路在本地播放生成的 CW，适合脱离电台的发射演练。";
    }

    @Override
    public String describeAvailability() {
        return "已准备好进行本地侧音播放。";
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
