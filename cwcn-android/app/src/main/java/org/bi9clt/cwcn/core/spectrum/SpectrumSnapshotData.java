package org.bi9clt.cwcn.core.spectrum;

import org.bi9clt.cwcn.ui.debug.AudioSpectrumSnapshot;

public final class SpectrumSnapshotData {
    private final long capturedAtEpochMs;
    private final int[] frequenciesHz;
    private final float[] magnitudes;
    private final int peakFrequencyHz;
    private final float peakMagnitude;
    private final float noiseFloorMagnitude;
    private final int preferredToneHz;
    private final int trackedToneHz;
    private final int hypothesisToneHz;
    private final int preferredWindowWinnerToneHz;
    private final int wideScanWinnerToneHz;
    private final int acquisitionWinnerToneHz;
    private final int finalAdoptedToneHz;
    private final String acquisitionWinnerSource;
    private final String finalAdoptedSource;
    private final boolean hypothesisGuardEnabled;
    private final boolean hypothesisGuardApplied;
    private final int hypothesisGuardAppliedToneHz;
    private final String hypothesisGuardDecision;
    private final boolean syntheticFallback;

    public SpectrumSnapshotData(
            long capturedAtEpochMs,
            int[] frequenciesHz,
            float[] magnitudes,
            int peakFrequencyHz,
            float peakMagnitude,
            float noiseFloorMagnitude,
            int preferredToneHz,
            int trackedToneHz,
            int hypothesisToneHz,
            int preferredWindowWinnerToneHz,
            int wideScanWinnerToneHz,
            int acquisitionWinnerToneHz,
            int finalAdoptedToneHz,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            boolean hypothesisGuardEnabled,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedToneHz,
            String hypothesisGuardDecision,
            boolean syntheticFallback
    ) {
        this.capturedAtEpochMs = capturedAtEpochMs;
        this.frequenciesHz = frequenciesHz == null ? new int[0] : frequenciesHz;
        this.magnitudes = magnitudes == null ? new float[0] : magnitudes;
        this.peakFrequencyHz = peakFrequencyHz;
        this.peakMagnitude = peakMagnitude;
        this.noiseFloorMagnitude = noiseFloorMagnitude;
        this.preferredToneHz = preferredToneHz;
        this.trackedToneHz = trackedToneHz;
        this.hypothesisToneHz = hypothesisToneHz;
        this.preferredWindowWinnerToneHz = preferredWindowWinnerToneHz;
        this.wideScanWinnerToneHz = wideScanWinnerToneHz;
        this.acquisitionWinnerToneHz = acquisitionWinnerToneHz;
        this.finalAdoptedToneHz = finalAdoptedToneHz;
        this.acquisitionWinnerSource = acquisitionWinnerSource == null ? "NONE" : acquisitionWinnerSource;
        this.finalAdoptedSource = finalAdoptedSource == null ? "NONE" : finalAdoptedSource;
        this.hypothesisGuardEnabled = hypothesisGuardEnabled;
        this.hypothesisGuardApplied = hypothesisGuardApplied;
        this.hypothesisGuardAppliedToneHz = hypothesisGuardAppliedToneHz;
        this.hypothesisGuardDecision = hypothesisGuardDecision == null ? "NONE" : hypothesisGuardDecision;
        this.syntheticFallback = syntheticFallback;
    }

    public static SpectrumSnapshotData fromAudioSnapshot(AudioSpectrumSnapshot snapshot, long capturedAtEpochMs) {
        if (snapshot == null) {
            return null;
        }
        return new SpectrumSnapshotData(
                capturedAtEpochMs,
                snapshot.frequenciesHz(),
                snapshot.magnitudes(),
                snapshot.peakFrequencyHz(),
                snapshot.peakMagnitude(),
                snapshot.noiseFloorMagnitude(),
                snapshot.preferredToneHz(),
                snapshot.trackedToneHz(),
                snapshot.hypothesisToneHz(),
                snapshot.preferredWindowWinnerToneHz(),
                snapshot.wideScanWinnerToneHz(),
                snapshot.acquisitionWinnerToneHz(),
                snapshot.finalAdoptedToneHz(),
                snapshot.acquisitionWinnerSource(),
                snapshot.finalAdoptedSource(),
                snapshot.hypothesisGuardEnabled(),
                snapshot.hypothesisGuardApplied(),
                snapshot.hypothesisGuardAppliedToneHz(),
                snapshot.hypothesisGuardDecision(),
                false
        );
    }

    public long capturedAtEpochMs() {
        return capturedAtEpochMs;
    }

    public int[] frequenciesHz() {
        return frequenciesHz;
    }

    public float[] magnitudes() {
        return magnitudes;
    }

    public int peakFrequencyHz() {
        return peakFrequencyHz;
    }

    public float peakMagnitude() {
        return peakMagnitude;
    }

    public float noiseFloorMagnitude() {
        return noiseFloorMagnitude;
    }

    public int preferredToneHz() {
        return preferredToneHz;
    }

    public int trackedToneHz() {
        return trackedToneHz;
    }

    public int hypothesisToneHz() {
        return hypothesisToneHz;
    }

    public int preferredWindowWinnerToneHz() {
        return preferredWindowWinnerToneHz;
    }

    public int wideScanWinnerToneHz() {
        return wideScanWinnerToneHz;
    }

    public int acquisitionWinnerToneHz() {
        return acquisitionWinnerToneHz;
    }

    public int finalAdoptedToneHz() {
        return finalAdoptedToneHz;
    }

    public String acquisitionWinnerSource() {
        return acquisitionWinnerSource;
    }

    public String finalAdoptedSource() {
        return finalAdoptedSource;
    }

    public boolean hypothesisGuardEnabled() {
        return hypothesisGuardEnabled;
    }

    public boolean hypothesisGuardApplied() {
        return hypothesisGuardApplied;
    }

    public int hypothesisGuardAppliedToneHz() {
        return hypothesisGuardAppliedToneHz;
    }

    public String hypothesisGuardDecision() {
        return hypothesisGuardDecision;
    }

    public boolean syntheticFallback() {
        return syntheticFallback;
    }

    public AudioSpectrumSnapshot toAudioSpectrumSnapshot() {
        return new AudioSpectrumSnapshot(
                frequenciesHz,
                magnitudes,
                peakFrequencyHz,
                peakMagnitude,
                noiseFloorMagnitude,
                preferredToneHz,
                trackedToneHz,
                hypothesisToneHz,
                preferredWindowWinnerToneHz,
                wideScanWinnerToneHz,
                acquisitionWinnerToneHz,
                finalAdoptedToneHz,
                acquisitionWinnerSource,
                finalAdoptedSource,
                hypothesisGuardEnabled,
                hypothesisGuardApplied,
                hypothesisGuardAppliedToneHz,
                hypothesisGuardDecision
        );
    }
}
