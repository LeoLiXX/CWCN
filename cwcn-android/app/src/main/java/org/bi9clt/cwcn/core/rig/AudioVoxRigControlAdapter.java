package org.bi9clt.cwcn.core.rig;

import org.bi9clt.cwcn.core.tx.AudioTrackTxAudioOutput;
import org.bi9clt.cwcn.core.tx.CwTxAudioOutput;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxRunner;
import org.bi9clt.cwcn.core.tx.CwTxState;

public final class AudioVoxRigControlAdapter implements RigControlAdapter {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private final CwTxEngine txEngine;
    private final CwTxRunner txRunner;

    private volatile CwTxPlaybackSnapshot lastSnapshot;
    private volatile int wpm;
    private volatile int toneFrequencyHz;

    public AudioVoxRigControlAdapter() {
        this(new AudioTrackTxAudioOutput(), DEFAULT_WPM, DEFAULT_TONE_FREQUENCY_HZ);
    }

    AudioVoxRigControlAdapter(CwTxAudioOutput audioOutput, int wpm, int toneFrequencyHz) {
        this.txEngine = new CwTxEngine();
        this.txRunner = new CwTxRunner(audioOutput);
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
    }

    @Override
    public String id() {
        return "audio-vox-text";
    }

    @Override
    public String displayName() {
        return "Audio VOX Text Adapter";
    }

    @Override
    public String describeCapabilities() {
        return "Generate CW audio locally so a radio or external keyer can be driven through VOX/audio input.";
    }

    @Override
    public String describeAvailability() {
        return "Ready for audio VOX. Connect phone audio to the rig/keyer audio path and enable VOX on the target device.";
    }

    @Override
    public boolean supportsTextToCw() {
        return true;
    }

    @Override
    public boolean supportsPttControl() {
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean supportsConfigurableTextToCwProfile() {
        return true;
    }

    @Override
    public boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
        return true;
    }

    @Override
    public boolean keyDown() {
        return false;
    }

    @Override
    public boolean keyUp() {
        txRunner.stop();
        return true;
    }

    @Override
    public boolean sendText(String text) {
        if (txRunner.isRunning()) {
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, this.wpm, this.toneFrequencyHz);
        if (plan.elements().isEmpty()) {
            return false;
        }
        lastSnapshot = null;
        txRunner.runPlanBlocking(plan, snapshot -> lastSnapshot = snapshot);
        return lastSnapshot != null && lastSnapshot.state() == CwTxState.COMPLETED;
    }

    CwTxPlaybackSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    @Override
    public CwTxPlaybackSnapshot currentTxPlaybackSnapshot() {
        return lastSnapshot;
    }
}
