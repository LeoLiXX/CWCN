package org.bi9clt.cwcn.core.audio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public final class LocalFileRxAudioSource implements RxAudioSource {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;
    private static final long CODEC_DEQUEUE_TIMEOUT_US = 10_000L;
    private static final int BYTES_PER_PCM16_SAMPLE = 2;

    private final Context appContext;

    private volatile Callback callback;
    private volatile State state = State.IDLE;
    private volatile boolean running;

    private Uri selectedFileUri;
    private String selectedFileLabel = "";
    private String selectedFileMetadataSummary = "";
    private String lastReplayFormatSummary = "";
    private Thread workerThread;

    public LocalFileRxAudioSource(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String id() {
        return "local-file-replay";
    }

    @Override
    public String displayName() {
        return "本地文件回放";
    }

    @Override
    public boolean isAvailable() {
        return selectedFileUri != null;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public synchronized void setSelectedFile(@Nullable Uri fileUri, @Nullable String fileLabel) {
        selectedFileUri = fileUri;
        selectedFileLabel = fileLabel == null ? "" : fileLabel.trim();
        selectedFileMetadataSummary = buildSelectedFileMetadataSummary(fileUri, selectedFileLabel);
        lastReplayFormatSummary = "";
    }

    @Nullable
    public synchronized Uri selectedFileUri() {
        return selectedFileUri;
    }

    public synchronized String selectedFileLabel() {
        if (!selectedFileLabel.isEmpty()) {
            return selectedFileLabel;
        }
        if (selectedFileUri == null) {
            return "";
        }
        return selectedFileUri.toString();
    }

    public synchronized String selectionSummary() {
        if (selectedFileUri == null) {
            return "还没有选择本地音频文件。推荐格式：WAV PCM 单声道。M4A/AAC 可作为兼容输入。";
        }
        StringBuilder builder = new StringBuilder(selectedFileMetadataSummary);
        if (builder.length() == 0) {
            builder.append("已选文件：").append(selectedFileLabel())
                    .append("\nUri: ").append(selectedFileUri);
        }
        if (!lastReplayFormatSummary.isEmpty()) {
            builder.append("\n上次回放格式：").append(lastReplayFormatSummary);
        }
        builder.append("\n优先：WAV PCM。兼容：通过 Android 解码器读取 M4A/AAC。");
        return builder.toString();
    }

    @Override
    public synchronized void start() {
        if (state == State.RUNNING || state == State.STARTING) {
            return;
        }
        if (selectedFileUri == null) {
            setErrorState("还没有选择本地音频文件。", null);
            return;
        }
        running = true;
        updateState(State.STARTING, "正在准备本地文件回放：" + selectedFileLabel());
        workerThread = new Thread(this::replayLoop, "cwcn-local-file-source");
        workerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPING) {
            return;
        }
        running = false;
        updateState(State.STOPPING, "正在停止本地文件回放。");
        joinWorkerThread();
        if (state != State.ERROR) {
            updateState(State.IDLE, "本地文件回放已停止。");
        }
    }

    @Override
    public synchronized void release() {
        stop();
    }

    private void replayLoop() {
        Uri activeUri;
        String activeLabel;
        synchronized (this) {
            activeUri = selectedFileUri;
            activeLabel = selectedFileLabel();
        }
        if (activeUri == null) {
            setErrorState("还没有选择本地音频文件。", null);
            return;
        }

        try {
            updateState(State.RUNNING, "正在回放本地文件：" + activeLabel);
            long startedAtMs = SystemClock.elapsedRealtime();
            FrameEmitter emitter = new FrameEmitter(startedAtMs);
            boolean decoded = decodeWithMediaCodec(activeUri, emitter);
            if (!decoded) {
                decodeWavePcm16(activeUri, emitter);
            }
            emitter.flushPending();
        } catch (IOException | IllegalArgumentException exception) {
            if (!running) {
                updateState(State.IDLE, "本地文件回放已停止。");
                return;
            }
            setErrorState("本地文件回放失败：" + exception.getMessage(), exception);
            return;
        } finally {
            running = false;
            clearWorkerThreadReference();
        }

        if (state != State.ERROR) {
            updateState(State.IDLE, "本地文件回放已完成。");
        }
    }

    private boolean decodeWithMediaCodec(Uri fileUri, FrameEmitter emitter) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor descriptor = null;
        MediaCodec codec = null;
        try {
            descriptor = appContext.getContentResolver().openAssetFileDescriptor(fileUri, "r");
            if (descriptor == null) {
                return false;
            }
            extractor.setDataSource(
                    descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(),
                    descriptor.getLength()
            );
            int trackIndex = findAudioTrack(extractor);
            if (trackIndex < 0) {
                return false;
            }

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType == null || mimeType.trim().isEmpty()) {
                return false;
            }
            extractor.selectTrack(trackIndex);
            codec = MediaCodec.createDecoderByType(mimeType);
            codec.configure(format, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean extractorDone = false;
            boolean outputDone = false;
            while (running && !outputDone) {
                if (!extractorDone) {
                    int inputBufferIndex = codec.dequeueInputBuffer(CODEC_DEQUEUE_TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                        if (inputBuffer == null) {
                            throw new IOException("解码器输入缓冲区不可用。");
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            extractorDone = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs < 0L ? 0L : presentationTimeUs,
                                    0
                            );
                            extractor.advance();
                        }
                    }
                }

                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    emitter.updateFormat(
                            outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                            outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                                    ? outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                    : 2
                    );
                    continue;
                }
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER
                        || outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    continue;
                }
                if (outputBufferIndex < 0) {
                    continue;
                }

                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    ByteBuffer copy = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN);
                    emitter.consumeDecoderOutput(copy);
                }
                codec.releaseOutputBuffer(outputBufferIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
            }
            return true;
        } catch (RuntimeException runtimeException) {
            return false;
        } finally {
            closeQuietly(codec);
            closeQuietly(extractor);
            closeQuietly(descriptor);
        }
    }

    private void decodeWavePcm16(Uri fileUri, FrameEmitter emitter) throws IOException {
        InputStream inputStream = null;
        DataInputStream dataInputStream = null;
        try {
            inputStream = appContext.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                throw new IOException("无法打开所选文件。");
            }
            dataInputStream = new DataInputStream(inputStream);
            WaveFormat waveFormat = readWaveFormat(dataInputStream);
            emitter.updateFormat(waveFormat.sampleRateHz, waveFormat.channelCount, 2);

            byte[] buffer = new byte[Math.max(2048, FRAME_SIZE_SAMPLES * waveFormat.channelCount * BYTES_PER_PCM16_SAMPLE)];
            int remainingBytes = waveFormat.dataSizeBytes;
            while (running && remainingBytes > 0) {
                int toRead = Math.min(buffer.length, remainingBytes);
                int read = dataInputStream.read(buffer, 0, toRead);
                if (read <= 0) {
                    break;
                }
                emitter.consumePcm16(buffer, read);
                remainingBytes -= read;
            }
        } finally {
            closeQuietly(dataInputStream);
            closeQuietly(inputStream);
        }
    }

    private WaveFormat readWaveFormat(DataInputStream inputStream) throws IOException {
        byte[] riffHeader = new byte[12];
        inputStream.readFully(riffHeader);
        if (!asciiEquals(riffHeader, 0, "RIFF") || !asciiEquals(riffHeader, 8, "WAVE")) {
            throw new IOException("所选 WAV 文件头无效。");
        }

        Integer sampleRateHz = null;
        Integer channelCount = null;
        Integer bitsPerSample = null;
        Integer audioFormat = null;
        Integer dataSizeBytes = null;

        while (true) {
            byte[] chunkHeader = new byte[8];
            inputStream.readFully(chunkHeader);
            String chunkId = new String(chunkHeader, 0, 4);
            int chunkSize = littleEndianInt(chunkHeader, 4);
            if ("fmt ".equals(chunkId)) {
                byte[] fmtBytes = new byte[chunkSize];
                inputStream.readFully(fmtBytes);
                audioFormat = littleEndianShort(fmtBytes, 0);
                channelCount = littleEndianShort(fmtBytes, 2);
                sampleRateHz = littleEndianInt(fmtBytes, 4);
                bitsPerSample = chunkSize >= 16 ? littleEndianShort(fmtBytes, 14) : 0;
            } else if ("data".equals(chunkId)) {
                dataSizeBytes = chunkSize;
                break;
            } else {
                skipFully(inputStream, chunkSize);
            }
            if ((chunkSize & 1) != 0) {
                skipFully(inputStream, 1);
            }
        }

        if (sampleRateHz == null || channelCount == null || bitsPerSample == null
                || audioFormat == null || dataSizeBytes == null) {
            throw new IOException("所选 WAV 文件缺少必要的格式区块。");
        }
        if (audioFormat != 1 || bitsPerSample != 16) {
            throw new IOException("手动兜底模式只支持 PCM16 WAV。");
        }
        return new WaveFormat(sampleRateHz, channelCount, dataSizeBytes);
    }

    private int findAudioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType != null && mimeType.startsWith("audio/")) {
                return index;
            }
        }
        return -1;
    }

    private void updateState(State newState, String detail) {
        state = newState;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(newState, detail);
        }
    }

    private void setErrorState(String message, @Nullable Throwable throwable) {
        state = State.ERROR;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(State.ERROR, message);
            currentCallback.onError(message, throwable);
        }
    }

    private void joinWorkerThread() {
        if (workerThread == null) {
            return;
        }
        try {
            workerThread.join(500L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            workerThread = null;
        }
    }

    private void clearWorkerThreadReference() {
        synchronized (this) {
            workerThread = null;
        }
    }

    private void closeQuietly(@Nullable MediaCodec codec) {
        if (codec == null) {
            return;
        }
        try {
            codec.stop();
        } catch (RuntimeException ignored) {
        }
        try {
            codec.release();
        } catch (RuntimeException ignored) {
        }
    }

    private void closeQuietly(@Nullable MediaExtractor extractor) {
        if (extractor == null) {
            return;
        }
        try {
            extractor.release();
        } catch (RuntimeException ignored) {
        }
    }

    private void closeQuietly(@Nullable Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private boolean asciiEquals(byte[] bytes, int offset, String text) {
        if (bytes == null || text == null || offset < 0 || offset + text.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if ((char) bytes[offset + index] != text.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private void skipFully(DataInputStream inputStream, int byteCount) throws IOException {
        int remaining = byteCount;
        while (remaining > 0) {
            int skipped = inputStream.skipBytes(remaining);
            if (skipped <= 0) {
                throw new IOException("跳过 WAV 区块时遇到意外文件结尾。");
            }
            remaining -= skipped;
        }
    }

    public String resolveDisplayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        ContentResolver resolver = appContext.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
    }

    private synchronized void rememberReplayFormat(int sampleRateHz, int channelCount, int pcmEncoding) {
        lastReplayFormatSummary = sampleRateHz + " Hz, "
                + channelCount + " 声道, "
                + pcmEncodingLabel(pcmEncoding);
    }

    private String buildSelectedFileMetadataSummary(@Nullable Uri fileUri, @Nullable String fileLabel) {
        if (fileUri == null) {
            return "";
        }
        String resolvedLabel = (fileLabel == null || fileLabel.trim().isEmpty())
                ? resolveDisplayName(fileUri)
                : fileLabel.trim();
        String mimeType = safeMimeType(fileUri);
        long fileSizeBytes = safeFileSizeBytes(fileUri);
        ProbedAudioMetadata metadata = probeAudioMetadata(fileUri);

        StringBuilder builder = new StringBuilder();
        builder.append("已选文件：").append(resolvedLabel)
                .append("\nMIME：").append(mimeType);
        if (fileSizeBytes > 0L) {
            builder.append("\n大小：").append(formatByteSize(fileSizeBytes));
        }
        builder.append("\nUri: ").append(fileUri);
        if (metadata != null) {
            builder.append("\n探测：")
                    .append(metadata.summaryLabel());
        } else {
            builder.append("\n探测：暂时无法获取元数据");
        }
        builder.append("\n建议：")
                .append(isLikelyWave(mimeType, resolvedLabel)
                        ? "WAV 基线路径"
                        : "兼容路径；如需稳定复现，优先使用 WAV");
        return builder.toString();
    }

    private String safeMimeType(Uri fileUri) {
        if (fileUri == null) {
            return "未知";
        }
        String mimeType = appContext.getContentResolver().getType(fileUri);
        if (mimeType == null || mimeType.trim().isEmpty()) {
            return "未知";
        }
        return mimeType.trim();
    }

    private long safeFileSizeBytes(Uri fileUri) {
        if (fileUri == null) {
            return -1L;
        }
        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(
                    fileUri,
                    new String[]{OpenableColumns.SIZE},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    @Nullable
    private ProbedAudioMetadata probeAudioMetadata(Uri fileUri) {
        if (fileUri == null) {
            return null;
        }
        ProbedAudioMetadata extractorMetadata = probeWithMediaExtractor(fileUri);
        if (extractorMetadata != null) {
            return extractorMetadata;
        }
        try {
            ContentResolver resolver = appContext.getContentResolver();
            InputStream inputStream = resolver.openInputStream(fileUri);
            if (inputStream == null) {
                return null;
            }
            DataInputStream dataInputStream = new DataInputStream(inputStream);
            try {
                WaveFormat waveFormat = readWaveFormat(dataInputStream);
                return new ProbedAudioMetadata(
                        "audio/wav",
                        waveFormat.sampleRateHz,
                        waveFormat.channelCount,
                        "PCM16 WAV",
                        "WAV 解析器"
                );
            } finally {
                closeQuietly(dataInputStream);
                closeQuietly(inputStream);
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private ProbedAudioMetadata probeWithMediaExtractor(Uri fileUri) {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = appContext.getContentResolver().openAssetFileDescriptor(fileUri, "r");
            if (descriptor == null) {
                return null;
            }
            extractor.setDataSource(
                    descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(),
                    descriptor.getLength()
            );
            int trackIndex = findAudioTrack(extractor);
            if (trackIndex < 0) {
                return null;
            }
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mimeType = format.containsKey(MediaFormat.KEY_MIME)
                    ? format.getString(MediaFormat.KEY_MIME)
                    : "未知";
            int sampleRateHz = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    : 0;
            int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 0;
            String codecLabel = mimeType == null ? "未知" : mimeType;
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                codecLabel += ", " + pcmEncodingLabel(format.getInteger(MediaFormat.KEY_PCM_ENCODING));
            }
            return new ProbedAudioMetadata(
                    mimeType == null ? "未知" : mimeType,
                    sampleRateHz,
                    channelCount,
                    codecLabel,
                    "MediaExtractor"
            );
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            closeQuietly(extractor);
            closeQuietly(descriptor);
        }
    }

    private boolean isLikelyWave(String mimeType, String label) {
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.US);
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.US);
        return normalizedMime.contains("wav")
                || normalizedLabel.endsWith(".wav")
                || normalizedLabel.endsWith(".wave");
    }

    private String formatByteSize(long sizeBytes) {
        if (sizeBytes < 1024L) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0d);
        }
        return String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0d * 1024.0d));
    }

    private String pcmEncodingLabel(int pcmEncoding) {
        switch (pcmEncoding) {
            case 2:
                return "PCM16";
            case 3:
                return "PCM8";
            case 4:
                return "PCM 浮点";
            default:
                return "PCM(" + pcmEncoding + ")";
        }
    }

    private final class FrameEmitter {
        private final long startedAtMs;
        private final short[] pendingSamples = new short[FRAME_SIZE_SAMPLES];
        private int pendingSampleCount;
        private long emittedSampleCount;
        private int sampleRateHz = 16000;
        private int channelCount = 1;
        private int pcmEncoding = 2;

        private FrameEmitter(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }

        private void updateFormat(int sampleRateHz, int channelCount, int pcmEncoding) {
            this.sampleRateHz = Math.max(1, sampleRateHz);
            this.channelCount = Math.max(1, channelCount);
            this.pcmEncoding = pcmEncoding;
            rememberReplayFormat(this.sampleRateHz, this.channelCount, this.pcmEncoding);
        }

        private void consumeDecoderOutput(ByteBuffer byteBuffer) throws IOException {
            if (pcmEncoding == 4) {
                consumeFloatPcm(byteBuffer);
                return;
            }
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            consumePcm16(bytes, bytes.length);
        }

        private void consumeFloatPcm(ByteBuffer byteBuffer) throws IOException {
            int frameChannelCount = Math.max(1, channelCount);
            while (running && byteBuffer.remaining() >= frameChannelCount * 4) {
                float mixed = 0.0f;
                for (int channelIndex = 0; channelIndex < frameChannelCount; channelIndex++) {
                    mixed += byteBuffer.getFloat();
                }
                mixed /= frameChannelCount;
                short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed * 32767.0f)));
                appendMonoSample(sample);
            }
        }

        private void consumePcm16(byte[] bytes, int length) throws IOException {
            if (length <= 0) {
                return;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN);
            int frameChannelCount = Math.max(1, channelCount);
            while (running && byteBuffer.remaining() >= frameChannelCount * BYTES_PER_PCM16_SAMPLE) {
                int mixed = 0;
                for (int channelIndex = 0; channelIndex < frameChannelCount; channelIndex++) {
                    mixed += byteBuffer.getShort();
                }
                appendMonoSample((short) (mixed / frameChannelCount));
            }
        }

        private void appendMonoSample(short sample) throws IOException {
            pendingSamples[pendingSampleCount] = sample;
            pendingSampleCount += 1;
            if (pendingSampleCount >= pendingSamples.length) {
                emitFrame(pendingSampleCount);
                pendingSampleCount = 0;
            }
        }

        private void flushPending() throws IOException {
            if (pendingSampleCount > 0) {
                emitFrame(pendingSampleCount);
                pendingSampleCount = 0;
            }
        }

        private void emitFrame(int sampleCount) throws IOException {
            short[] samples = new short[sampleCount];
            System.arraycopy(pendingSamples, 0, samples, 0, sampleCount);
            int peak = 0;
            int clippedSampleCount = 0;
            double sumSquares = 0.0d;
            for (short sample : samples) {
                int absolute = Math.abs((int) sample);
                if (absolute > peak) {
                    peak = absolute;
                }
                if (absolute >= CLIPPING_SAMPLE_THRESHOLD) {
                    clippedSampleCount += 1;
                }
                sumSquares += (double) sample * sample;
            }
            double rms = sampleCount == 0 ? 0.0d : Math.sqrt(sumSquares / sampleCount);
            long capturedAtMs = startedAtMs + Math.round(emittedSampleCount * 1000.0d / Math.max(1, sampleRateHz));
            emittedSampleCount += sampleCount;
            Callback currentCallback = callback;
            if (currentCallback != null) {
                currentCallback.onAudioFrame(new AudioFrame(
                        samples,
                        sampleRateHz,
                        1,
                        peak,
                        rms,
                        clippedSampleCount,
                        capturedAtMs
                ));
            }
            long frameDurationMs = Math.max(1L, Math.round(sampleCount * 1000.0d / Math.max(1, sampleRateHz)));
            SystemClock.sleep(frameDurationMs);
            if (!running) {
                throw new IOException("回放已中断。");
            }
        }
    }

    private static final class WaveFormat {
        private final int sampleRateHz;
        private final int channelCount;
        private final int dataSizeBytes;

        private WaveFormat(int sampleRateHz, int channelCount, int dataSizeBytes) {
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.dataSizeBytes = dataSizeBytes;
        }
    }

    private static final class ProbedAudioMetadata {
        private final String mimeType;
        private final int sampleRateHz;
        private final int channelCount;
        private final String codecLabel;
        private final String sourceLabel;

        private ProbedAudioMetadata(
                String mimeType,
                int sampleRateHz,
                int channelCount,
                String codecLabel,
                String sourceLabel
        ) {
            this.mimeType = mimeType == null ? "未知" : mimeType;
            this.sampleRateHz = Math.max(0, sampleRateHz);
            this.channelCount = Math.max(0, channelCount);
            this.codecLabel = codecLabel == null ? "未知" : codecLabel;
            this.sourceLabel = sourceLabel == null ? "未知" : sourceLabel;
        }

        private String summaryLabel() {
            StringBuilder builder = new StringBuilder();
            builder.append(codecLabel);
            if (sampleRateHz > 0) {
                builder.append(", ").append(sampleRateHz).append(" Hz");
            }
            if (channelCount > 0) {
                builder.append(", ").append(channelCount).append(" 声道");
            }
            builder.append(" [").append(sourceLabel).append("]");
            return builder.toString();
        }
    }
}
