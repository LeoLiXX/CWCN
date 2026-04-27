package org.bi9clt.cwcn.ui.debug;

public final class AudioSpectrumSnapshot {
    private final int[] frequenciesHz;
    private final float[] magnitudes;
    private final int peakFrequencyHz;
    private final float peakMagnitude;
    private final float noiseFloorMagnitude;
    private final int preferredToneHz;
    private final int trackedToneHz;
    private final int preferredWindowWinnerToneHz;
    private final int wideScanWinnerToneHz;
    private final int acquisitionWinnerToneHz;
    private final int finalAdoptedToneHz;
    private final String acquisitionWinnerSource;
    private final String finalAdoptedSource;

    public AudioSpectrumSnapshot(
            int[] frequenciesHz,
            float[] magnitudes,
            int peakFrequencyHz,
            float peakMagnitude,
            float noiseFloorMagnitude,
            int preferredToneHz,
            int trackedToneHz,
            int preferredWindowWinnerToneHz,
            int wideScanWinnerToneHz,
            int acquisitionWinnerToneHz,
            int finalAdoptedToneHz,
            String acquisitionWinnerSource,
            String finalAdoptedSource
    ) {
        this.frequenciesHz = frequenciesHz;
        this.magnitudes = magnitudes;
        this.peakFrequencyHz = peakFrequencyHz;
        this.peakMagnitude = peakMagnitude;
        this.noiseFloorMagnitude = noiseFloorMagnitude;
        this.preferredToneHz = preferredToneHz;
        this.trackedToneHz = trackedToneHz;
        this.preferredWindowWinnerToneHz = preferredWindowWinnerToneHz;
        this.wideScanWinnerToneHz = wideScanWinnerToneHz;
        this.acquisitionWinnerToneHz = acquisitionWinnerToneHz;
        this.finalAdoptedToneHz = finalAdoptedToneHz;
        this.acquisitionWinnerSource = acquisitionWinnerSource == null ? "NONE" : acquisitionWinnerSource;
        this.finalAdoptedSource = finalAdoptedSource == null ? "NONE" : finalAdoptedSource;
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
}
