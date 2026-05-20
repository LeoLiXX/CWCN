package org.bi9clt.cwcn.core.rx;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public final class LiveRxTraceRecorder {
    private static final long FRAME_LOG_INTERVAL_MS = 240L;
    private static final int MAX_TRACE_DIRECTORIES = 6;

    private final Context appContext;
    private final LiveRxTraceStore traceStore;

    private File sessionDirectory;
    private File wavFile;
    private File logFile;
    private RandomAccessFile wavOutput;
    private BufferedWriter logWriter;
    private String sessionLabel = "";
    private String sourceLabel = "";
    private long startedAtEpochMs;
    private long startedAtElapsedMs;
    private long lastFrameLogElapsedMs = Long.MIN_VALUE;
    private long lastObservedTimestampMs;
    private long writtenSampleCount;
    private int sampleRateHz;
    private int preferredToneFrequencyHz = -1;
    private int sqlPercent = -1;
    private boolean active;

    public LiveRxTraceRecorder(Context context, LiveRxTraceStore traceStore) {
        this.appContext = context.getApplicationContext();
        this.traceStore = traceStore;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void startSession(String sourceLabel, int preferredToneFrequencyHz, int sqlPercent) {
        finishSession("restart");
        startedAtEpochMs = System.currentTimeMillis();
        startedAtElapsedMs = SystemClock.elapsedRealtime();
        this.sourceLabel = safeText(sourceLabel);
        this.preferredToneFrequencyHz = preferredToneFrequencyHz > 0 ? preferredToneFrequencyHz : -1;
        this.sqlPercent = sqlPercent < 0 ? -1 : Math.min(100, sqlPercent);
        sessionLabel = buildSessionLabel(this.sourceLabel, startedAtEpochMs);
        sessionDirectory = new File(resolveBaseDirectory(), sessionLabel);
        if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) {
            resetSessionState();
            return;
        }
        wavFile = new File(sessionDirectory, "capture.wav");
        logFile = new File(sessionDirectory, "trace.log");
        try {
            logWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true),
                    StandardCharsets.UTF_8
            ));
            logLine(0L, "SESSION",
                    "start source=" + sanitizeValue(this.sourceLabel)
                            + " preferredToneHz=" + preferredToneFrequencyHz
                            + " sqlPercent=" + sqlPercent);
            active = true;
        } catch (IOException exception) {
            closeQuietly(logWriter);
            resetSessionState();
        }
    }

    public synchronized void recordState(String state, @Nullable String detail, long timestampMs) {
        if (!active) {
            return;
        }
        logLine(elapsedMs(timestampMs), "STATE",
                sanitizeValue(state) + " detail=" + sanitizeValue(detail));
    }

    public synchronized void recordFrame(
            @Nullable AudioFrame frame,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            int displayWpm,
            @Nullable String frontEndSummary
    ) {
        if (!active || frame == null) {
            return;
        }
        ensureWaveOutput(frame.sampleRateHz());
        appendWaveSamples(frame);
        lastObservedTimestampMs = Math.max(lastObservedTimestampMs, frame.capturedAtMs());

        long frameElapsedMs = elapsedMs(frame.capturedAtMs());
        if ((frameElapsedMs - lastFrameLogElapsedMs) < FRAME_LOG_INTERVAL_MS) {
            return;
        }
        lastFrameLogElapsedMs = frameElapsedMs;
        double rawWpm = timingSnapshot == null
                ? 0.0d
                : timingSnapshot.estimatedWpmPrecise() > 0.0d
                ? timingSnapshot.estimatedWpmPrecise()
                : timingSnapshot.estimatedWpm();
        logLine(frameElapsedMs, "FRAME",
                "peak=" + frame.peakAmplitude()
                        + " rms=" + formatDouble(frame.rmsAmplitude())
                        + " clipRatio=" + formatDouble(frame.clippedSampleRatio())
                        + " dspWpm=" + displayWpm
                        + " rawWpm=" + formatDouble(rawWpm)
                        + " lock=" + percent(signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio())
                        + " near=" + percent(signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio())
                        + " unl=" + percent(signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio())
                        + " target=" + positiveOrDash(signalSnapshot == null ? 0 : signalSnapshot.targetToneFrequencyHz())
                        + " eff=" + positiveOrDash(signalSnapshot == null ? 0 : signalSnapshot.effectiveTrackedToneFrequencyHz())
                        + " inHot=" + percent(inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentHotFrameRatio())
                        + " inClip=" + percent(inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentClippingFrameRatio())
                        + " fe=" + sanitizeValue(frontEndSummary));
    }

    public synchronized void recordToneEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot
    ) {
        if (!active || toneEvent == null) {
            return;
        }
        lastObservedTimestampMs = Math.max(lastObservedTimestampMs, toneEvent.timestampMs());
        logLine(elapsedMs(toneEvent.timestampMs()), "TONE",
                "type=" + toneEvent.type().name()
                        + " dur=" + toneEvent.toneDurationMs()
                        + " peak=" + toneEvent.peakAmplitude()
                        + " rms=" + formatDouble(toneEvent.rmsAmplitude())
                        + " target=" + positiveOrDash(signalSnapshot == null ? 0 : signalSnapshot.targetToneFrequencyHz())
                        + " eff=" + positiveOrDash(signalSnapshot == null ? 0 : signalSnapshot.effectiveTrackedToneFrequencyHz()));
    }

    public synchronized void recordTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (!active || timingEvent == null) {
            return;
        }
        lastObservedTimestampMs = Math.max(lastObservedTimestampMs, timingEvent.timestampMs());
        double rawWpm = timingSnapshot == null
                ? 0.0d
                : timingSnapshot.estimatedWpmPrecise() > 0.0d
                ? timingSnapshot.estimatedWpmPrecise()
                : timingSnapshot.estimatedWpm();
        logLine(elapsedMs(timingEvent.timestampMs()), "TIMING",
                "kind=" + timingEvent.kind().name()
                        + " cls=" + timingEvent.classification().name()
                        + " dur=" + timingEvent.durationMs()
                        + " dot=" + timingEvent.dotEstimateMs()
                        + " intra=" + timingEvent.intraGapEstimateMs()
                        + " rawWpm=" + formatDouble(rawWpm));
    }

    public synchronized void recordDecodeEvent(
            @Nullable CwDecodeEvent decodeEvent,
            int displayWpm,
            @Nullable String frontEndSummary
    ) {
        if (!active || decodeEvent == null) {
            return;
        }
        lastObservedTimestampMs = Math.max(lastObservedTimestampMs, decodeEvent.timestampMs());
        logLine(elapsedMs(decodeEvent.timestampMs()), "DECODE",
                "type=" + decodeEvent.type().name()
                        + " emit=" + sanitizeValue(decodeEvent.emittedValue())
                        + " seq=" + sanitizeValue(decodeEvent.sourceSequence())
                        + " unknown=" + decodeEvent.unknownCharacter()
                        + " dspWpm=" + displayWpm
                        + " text=" + sanitizeValue(decodeEvent.outputText())
                        + " fe=" + sanitizeValue(frontEndSummary));
    }

    public synchronized void recordMarker(
            String label,
            @Nullable String detail,
            long timestampMs
    ) {
        if (!active) {
            return;
        }
        lastObservedTimestampMs = Math.max(lastObservedTimestampMs, timestampMs);
        logLine(elapsedMs(timestampMs), "MARK",
                sanitizeValue(label) + " detail=" + sanitizeValue(detail));
    }

    public synchronized void finishSession(String reason) {
        if (!active && sessionDirectory == null) {
            return;
        }
        long finishTimestampMs = lastObservedTimestampMs > 0L
                ? lastObservedTimestampMs
                : SystemClock.elapsedRealtime();
        if (active) {
            logLine(elapsedMs(finishTimestampMs), "SESSION",
                    "stop reason=" + sanitizeValue(reason)
                            + " samples=" + writtenSampleCount
                            + " sampleRateHz=" + sampleRateHz);
        }
        patchWaveHeader();
        closeQuietly(wavOutput);
        closeQuietly(logWriter);

        if (wavFile != null && wavFile.exists() && writtenSampleCount > 0L) {
            long durationMs = sampleRateHz <= 0
                    ? 0L
                    : Math.round(writtenSampleCount * 1000.0d / sampleRateHz);
            traceStore.saveLatest(new LiveRxTraceArtifact(
                    startedAtEpochMs,
                    sessionLabel,
                    sourceLabel,
                    wavFile.getAbsolutePath(),
                    logFile == null ? "" : logFile.getAbsolutePath(),
                    durationMs,
                    sampleRateHz,
                    writtenSampleCount,
                    preferredToneFrequencyHz,
                    sqlPercent
            ));
            pruneOldTraceDirectories();
        }
        resetSessionState();
    }

    private File resolveBaseDirectory() {
        File externalDir = appContext.getExternalFilesDir("rx-traces");
        if (externalDir != null) {
            return externalDir;
        }
        return new File(appContext.getFilesDir(), "rx-traces");
    }

    private void ensureWaveOutput(int frameSampleRateHz) {
        if (wavOutput != null) {
            return;
        }
        sampleRateHz = Math.max(1, frameSampleRateHz);
        try {
            wavOutput = new RandomAccessFile(wavFile, "rw");
            writeWaveHeaderPlaceholder(wavOutput, sampleRateHz);
        } catch (IOException exception) {
            closeQuietly(wavOutput);
            wavOutput = null;
        }
    }

    private void appendWaveSamples(AudioFrame frame) {
        if (wavOutput == null || frame == null) {
            return;
        }
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0) {
            return;
        }
        try {
            for (short sample : samples) {
                writeLittleEndianShort(wavOutput, sample);
            }
            writtenSampleCount += samples.length;
        } catch (IOException exception) {
            closeQuietly(wavOutput);
            wavOutput = null;
        }
    }

    private void patchWaveHeader() {
        if (wavFile == null || !wavFile.exists() || sampleRateHz <= 0) {
            return;
        }
        RandomAccessFile raf = wavOutput;
        try {
            if (raf == null) {
                raf = new RandomAccessFile(wavFile, "rw");
            }
            long dataSizeBytes = writtenSampleCount * 2L;
            raf.seek(4L);
            writeLittleEndianInt(raf, (int) Math.min(Integer.MAX_VALUE, 36L + dataSizeBytes));
            raf.seek(40L);
            writeLittleEndianInt(raf, (int) Math.min(Integer.MAX_VALUE, dataSizeBytes));
        } catch (IOException ignored) {
        } finally {
            if (wavOutput == null) {
                closeQuietly(raf);
            }
        }
    }

    private void logLine(long elapsedMs, String kind, String detail) {
        if (logWriter == null) {
            return;
        }
        try {
            logWriter.write(String.format(
                    Locale.US,
                    "%08d | %s | %s",
                    Math.max(0L, elapsedMs),
                    kind,
                    detail == null ? "" : detail
            ));
            logWriter.newLine();
            logWriter.flush();
        } catch (IOException ignored) {
        }
    }

    private long elapsedMs(long timestampMs) {
        long base = startedAtElapsedMs > 0L ? startedAtElapsedMs : SystemClock.elapsedRealtime();
        return Math.max(0L, timestampMs - base);
    }

    private String buildSessionLabel(String sourceLabel, long epochMs) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date(epochMs));
        return "trace-" + timestamp + "-" + sanitizeFileComponent(sourceLabel);
    }

    private void pruneOldTraceDirectories() {
        File baseDirectory = resolveBaseDirectory();
        File[] directories = baseDirectory.listFiles(File::isDirectory);
        if (directories == null || directories.length <= MAX_TRACE_DIRECTORIES) {
            return;
        }
        Arrays.sort(directories, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = MAX_TRACE_DIRECTORIES; index < directories.length; index++) {
            deleteRecursively(directories[index]);
        }
    }

    private void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void resetSessionState() {
        sessionDirectory = null;
        wavFile = null;
        logFile = null;
        wavOutput = null;
        logWriter = null;
        sessionLabel = "";
        sourceLabel = "";
        startedAtEpochMs = 0L;
        startedAtElapsedMs = 0L;
        lastFrameLogElapsedMs = Long.MIN_VALUE;
        lastObservedTimestampMs = 0L;
        writtenSampleCount = 0L;
        sampleRateHz = 0;
        preferredToneFrequencyHz = -1;
        sqlPercent = -1;
        active = false;
    }

    private void writeWaveHeaderPlaceholder(RandomAccessFile output, int sampleRateHz) throws IOException {
        output.setLength(0L);
        output.seek(0L);
        output.writeBytes("RIFF");
        writeLittleEndianInt(output, 36);
        output.writeBytes("WAVE");
        output.writeBytes("fmt ");
        writeLittleEndianInt(output, 16);
        writeLittleEndianShort(output, 1);
        writeLittleEndianShort(output, 1);
        writeLittleEndianInt(output, sampleRateHz);
        writeLittleEndianInt(output, sampleRateHz * 2);
        writeLittleEndianShort(output, 2);
        writeLittleEndianShort(output, 16);
        output.writeBytes("data");
        writeLittleEndianInt(output, 0);
    }

    private void writeLittleEndianInt(RandomAccessFile output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private void writeLittleEndianShort(RandomAccessFile output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private String sanitizeValue(@Nullable String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private String sanitizeFileComponent(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "rx";
        }
        return value.trim()
                .replaceAll("[^a-zA-Z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "")
                .toLowerCase(Locale.US);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String percent(double ratio) {
        return String.format(Locale.US, "%.0f%%", Math.max(0.0d, ratio) * 100.0d);
    }

    private String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private void closeQuietly(@Nullable RandomAccessFile file) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly(@Nullable BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
