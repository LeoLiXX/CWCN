package org.bi9clt.cwcn.core.tx;

import androidx.annotation.Nullable;

import java.util.List;

public final class CwTxRunner {
    public interface Listener {
        void onSnapshot(CwTxPlaybackSnapshot snapshot);
    }

    private final CwTxAudioOutput audioOutput;

    private volatile boolean stopRequested;
    private volatile Thread workerThread;

    public CwTxRunner(CwTxAudioOutput audioOutput) {
        this.audioOutput = audioOutput;
    }

    public synchronized boolean start(CwTxPlan plan, Listener listener) {
        if (plan == null || listener == null || isRunning()) {
            return false;
        }
        stopRequested = false;
        Thread thread = new Thread(() -> runPlanBlocking(plan, listener), "cwcn-tx-runner");
        workerThread = thread;
        thread.start();
        return true;
    }

    public synchronized boolean isRunning() {
        return workerThread != null && workerThread.isAlive();
    }

    public void stop() {
        stopRequested = true;
        audioOutput.stop();
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void runPlanBlocking(CwTxPlan plan, Listener listener) {
        Thread currentThread = Thread.currentThread();
        if (workerThread == null) {
            workerThread = currentThread;
        }
        int elapsedMs = 0;
        int completedElementCount = 0;
        List<CwTxElement> elements = plan.elements();
        try {
            emitSnapshot(listener, buildSnapshot(
                    CwTxState.PLAYING,
                    plan,
                    completedElementCount,
                    elapsedMs,
                    elements.isEmpty() ? null : elements.get(0),
                    "TX started"
            ));
            for (CwTxElement element : elements) {
                if (stopRequested) {
                    emitSnapshot(listener, buildSnapshot(
                            CwTxState.STOPPED,
                            plan,
                            completedElementCount,
                            elapsedMs,
                            null,
                            "TX stopped"
                    ));
                    return;
                }
                playElement(plan, element);
                completedElementCount += 1;
                elapsedMs += element.durationMs();
                emitSnapshot(listener, buildSnapshot(
                        CwTxState.PLAYING,
                        plan,
                        completedElementCount,
                        elapsedMs,
                        nextElement(elements, completedElementCount),
                        "TX running"
                ));
            }
            audioOutput.finish();
            emitSnapshot(listener, buildSnapshot(
                    CwTxState.COMPLETED,
                    plan,
                    completedElementCount,
                    elapsedMs,
                    null,
                    "TX completed"
            ));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            emitSnapshot(listener, buildSnapshot(
                    stopRequested ? CwTxState.STOPPED : CwTxState.ERROR,
                    plan,
                    completedElementCount,
                    elapsedMs,
                    null,
                    stopRequested ? "TX stopped" : "TX interrupted"
            ));
        } catch (RuntimeException exception) {
            emitSnapshot(listener, buildSnapshot(
                    CwTxState.ERROR,
                    plan,
                    completedElementCount,
                    elapsedMs,
                    null,
                    "TX error: " + exception.getMessage()
            ));
        } finally {
            audioOutput.stop();
            if (workerThread == currentThread) {
                workerThread = null;
            }
            stopRequested = false;
        }
    }

    private void playElement(CwTxPlan plan, CwTxElement element) throws InterruptedException {
        if (element.kind() == CwTxElement.Kind.KEY_DOWN) {
            audioOutput.playTone(plan.toneFrequencyHz(), element.durationMs());
            return;
        }
        audioOutput.playSilence(element.durationMs());
    }

    private CwTxElement nextElement(List<CwTxElement> elements, int completedElementCount) {
        if (completedElementCount < 0 || completedElementCount >= elements.size()) {
            return null;
        }
        return elements.get(completedElementCount);
    }

    private CwTxPlaybackSnapshot buildSnapshot(
            CwTxState state,
            CwTxPlan plan,
            int completedElementCount,
            int elapsedMs,
            @Nullable CwTxElement currentElement,
            String statusMessage
    ) {
        return new CwTxPlaybackSnapshot(
                state,
                plan.normalizedText(),
                plan.morsePreview(),
                completedElementCount,
                plan.elements().size(),
                elapsedMs,
                plan.totalDurationMs(),
                currentElement == null ? "" : currentElement.sourceSymbol(),
                currentElement == null ? -1 : currentElement.sourceTextIndex(),
                currentElement != null && currentElement.kind() == CwTxElement.Kind.KEY_DOWN,
                statusMessage
        );
    }

    private void emitSnapshot(Listener listener, CwTxPlaybackSnapshot snapshot) {
        listener.onSnapshot(snapshot);
    }
}
