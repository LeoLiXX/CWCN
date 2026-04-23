package org.bi9clt.cwcn.core.rig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.tx.CwTxAudioOutput;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class AudioVoxRigControlAdapterTest {
    @Test
    public void sendTextPlaysAudioAndCompletes() {
        RecordingAudioOutput audioOutput = new RecordingAudioOutput();
        AudioVoxRigControlAdapter adapter = new AudioVoxRigControlAdapter(audioOutput, 20, 700);

        boolean sent = adapter.sendText("EE");

        assertTrue(sent);
        assertTrue(audioOutput.events.contains("tone:700:60"));
        assertTrue(audioOutput.events.contains("silence:180"));
    }

    @Test
    public void configuredProfileIsAppliedToFollowingTransmit() {
        RecordingAudioOutput audioOutput = new RecordingAudioOutput();
        AudioVoxRigControlAdapter adapter = new AudioVoxRigControlAdapter(audioOutput, 20, 700);

        adapter.configureTextToCwProfile(10, 900);
        boolean sent = adapter.sendText("E");

        assertTrue(sent);
        assertTrue(audioOutput.events.contains("tone:900:120"));
    }

    @Test
    public void sendTextReturnsFalseForUnsupportedPayload() {
        RecordingAudioOutput audioOutput = new RecordingAudioOutput();
        AudioVoxRigControlAdapter adapter = new AudioVoxRigControlAdapter(audioOutput, 20, 700);

        boolean sent = adapter.sendText("###");

        assertFalse(sent);
        assertTrue(audioOutput.events.isEmpty());
    }

    private static final class RecordingAudioOutput implements CwTxAudioOutput {
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
}
