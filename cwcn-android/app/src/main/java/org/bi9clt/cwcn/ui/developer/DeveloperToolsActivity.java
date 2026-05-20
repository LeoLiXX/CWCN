package org.bi9clt.cwcn.ui.developer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.core.audio.WavReplayFrameLoader;
import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.rx.LiveRxTraceArtifact;
import org.bi9clt.cwcn.core.rx.RxDeveloperStartupToneHintAnalyzer;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisResult;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisRunner;
import org.bi9clt.cwcn.core.rx.LiveRxTraceStore;
import org.bi9clt.cwcn.databinding.ActivityDeveloperToolsBinding;
import org.bi9clt.cwcn.ui.debug.InputDebugActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;
import org.bi9clt.cwcn.ui.tx.TxActivity;

import java.io.File;
import java.util.Locale;

public final class DeveloperToolsActivity extends AppCompatActivity {
    public static final String EXTRA_TRACE_WAV_FILE_PATH =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_WAV_FILE_PATH";
    public static final String EXTRA_TRACE_PREFERRED_TONE_FREQUENCY_HZ =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_PREFERRED_TONE_FREQUENCY_HZ";
    public static final String EXTRA_TRACE_SQL_PERCENT =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_SQL_PERCENT";

    private static final int DEFAULT_TRACE_ANALYSIS_SEED_WPM = 18;
    private static final int TRACE_ANALYSIS_PREVIEW_MAX_CHARS = 96;

    private ActivityDeveloperToolsBinding binding;
    private DeveloperModeStore developerModeStore;
    private LiveRxTraceStore liveRxTraceStore;
    private String latestTraceAnalysisKey = "";
    private String latestTraceAnalysisSummary = "";
    private long latestTraceAnalysisGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeveloperToolsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        developerModeStore = new DeveloperModeStore(this);
        liveRxTraceStore = new LiveRxTraceStore(this);
        binding.versionText.setText("开发工具 " + BuildConfig.VERSION_NAME);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.openRxDebugButton.setOnClickListener(view ->
                startActivity(new Intent(this, InputDebugActivity.class)));
        binding.openLatestTraceReplayButton.setOnClickListener(view -> openLatestTraceReplay());
        binding.openTxConsoleButton.setOnClickListener(view ->
                startActivity(new Intent(this, TxActivity.class)));
        binding.openRigBenchButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, RigSetupActivity.class);
            intent.putExtra(RigSetupActivity.EXTRA_OPEN_DEVELOPER_LABS, true);
            startActivity(intent);
        });
    }

    private void refreshUi() {
        boolean enabled = developerModeStore.isEnabled();
        binding.developerModeStatusText.setText(enabled
                ? "开发者模式已开启。\n当前优先保留 RX 实验台、TX 开发控制台和电台实验台，其他杂项不再放进正式路径。"
                : "开发者模式已关闭。\n只有在需要底层收发排查、链路实验或协议验证时才建议开启。");
        binding.toggleDeveloperModeButton.setText(enabled
                ? "关闭开发者模式"
                : "开启开发者模式");
        int visibility = enabled ? View.VISIBLE : View.GONE;
        binding.toolsPanel.setVisibility(visibility);
        binding.toolsHintText.setText(enabled
                ? "RX 实验台聚焦接收链路与解码问题，现场 trace 回放用于复盘 Operate 真机输入，TX 控制台用于发射验证，电台实验台用于 CAT / 键控实验。"
                : "开发工具会在开启开发者模式后显示。");
        LiveRxTraceArtifact latestTrace = liveRxTraceStore == null ? null : liveRxTraceStore.loadLatest();
        ensureLatestTraceAnalysis(latestTrace);
        binding.latestTraceStatusText.setText(renderLatestTraceStatus(latestTrace));
        binding.openLatestTraceReplayButton.setEnabled(enabled && latestTrace != null && latestTrace.hasReplayableAudio());
    }

    private void openLatestTraceReplay() {
        LiveRxTraceArtifact latestTrace = liveRxTraceStore == null ? null : liveRxTraceStore.loadLatest();
        if (latestTrace == null || !latestTrace.hasReplayableAudio()) {
            refreshUi();
            return;
        }
        Intent intent = new Intent(this, InputDebugActivity.class);
        intent.putExtra(EXTRA_TRACE_WAV_FILE_PATH, latestTrace.wavFilePath());
        if (latestTrace.hasPreferredToneFrequency()) {
            intent.putExtra(EXTRA_TRACE_PREFERRED_TONE_FREQUENCY_HZ, latestTrace.preferredToneFrequencyHz());
        }
        if (latestTrace.hasSqlPercent()) {
            intent.putExtra(EXTRA_TRACE_SQL_PERCENT, latestTrace.sqlPercent());
        }
        startActivity(intent);
    }

    private String renderLatestTraceStatus(@Nullable LiveRxTraceArtifact artifact) {
        if (artifact == null || !artifact.hasReplayableAudio()) {
            return "最近现场 trace：暂无。\n先在 Operate 真机路径下复现一次，系统会自动记录最近一份 WAV 和时间线。";
        }
        File wavFile = new File(artifact.wavFilePath());
        File logFile = artifact.hasTraceLog() ? new File(artifact.logFilePath()) : null;
        return String.format(
                Locale.US,
                "最近现场 trace：%s\n来源：%s\n时长：%d ms | %d Hz | %d samples\n现场设置：Tone %s | SQL %s\n%s\nWAV：%s%s",
                artifact.sessionLabel(),
                artifact.sourceLabel(),
                artifact.durationMs(),
                artifact.sampleRateHz(),
                artifact.sampleCount(),
                artifact.hasPreferredToneFrequency()
                        ? artifact.preferredToneFrequencyHz() + " Hz"
                        : "未记录",
                artifact.hasSqlPercent()
                        ? artifact.sqlPercent() + "%"
                        : "未记录",
                latestTraceAnalysisSummary.isEmpty()
                        ? "离线分析：等待执行。"
                        : latestTraceAnalysisSummary,
                wavFile.getAbsolutePath(),
                (logFile != null && logFile.exists()) ? "\nLOG：" + logFile.getAbsolutePath() : ""
        );
    }

    private void ensureLatestTraceAnalysis(@Nullable LiveRxTraceArtifact artifact) {
        if (artifact == null || !artifact.hasReplayableAudio()) {
            latestTraceAnalysisKey = "";
            latestTraceAnalysisSummary = "";
            latestTraceAnalysisGeneration += 1L;
            return;
        }
        String analysisKey = buildLatestTraceAnalysisKey(artifact);
        if (analysisKey.isEmpty()) {
            latestTraceAnalysisKey = "";
            latestTraceAnalysisSummary = "";
            latestTraceAnalysisGeneration += 1L;
            return;
        }
        if (analysisKey.equals(latestTraceAnalysisKey) && !latestTraceAnalysisSummary.isEmpty()) {
            return;
        }
        latestTraceAnalysisKey = analysisKey;
        latestTraceAnalysisSummary = "离线分析：处理中…";
        long generation = ++latestTraceAnalysisGeneration;
        LiveRxTraceArtifact traceArtifact = artifact;
        new Thread(() -> runLatestTraceAnalysis(traceArtifact, analysisKey, generation), "cwcn-latest-trace-analysis").start();
    }

    private void runLatestTraceAnalysis(
            LiveRxTraceArtifact artifact,
            String analysisKey,
            long generation
    ) {
        String summary;
        try {
            File wavFile = new File(artifact.wavFilePath());
            WavReplayFrameLoader.LoadedWav loadedWav = new WavReplayFrameLoader().load(wavFile);
            RxReplayAnalysisResult analysisResult = new RxReplayAnalysisRunner().analyze(
                    loadedWav.frames(),
                    artifact.preferredToneFrequencyHz(),
                    artifact.sqlPercent(),
                    DEFAULT_TRACE_ANALYSIS_SEED_WPM
            );
            RxDeveloperStartupToneHintAnalyzer.Result startupToneHint =
                    new RxDeveloperStartupToneHintAnalyzer().analyze(
                            loadedWav.frames(),
                            artifact.preferredToneFrequencyHz()
                    );
            summary = formatTraceAnalysisSummary(analysisResult, startupToneHint);
        } catch (Exception exception) {
            summary = "离线分析：失败 - " + safeAnalysisErrorMessage(exception);
        }
        final String finalSummary = summary;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (generation != latestTraceAnalysisGeneration || !analysisKey.equals(latestTraceAnalysisKey)) {
                return;
            }
            latestTraceAnalysisSummary = finalSummary;
            refreshUi();
        });
    }

    private String buildLatestTraceAnalysisKey(@Nullable LiveRxTraceArtifact artifact) {
        if (artifact == null || !artifact.hasReplayableAudio()) {
            return "";
        }
        return artifact.analysisKey();
    }

    private String formatTraceAnalysisSummary(
            RxReplayAnalysisResult analysisResult,
            @Nullable RxDeveloperStartupToneHintAnalyzer.Result startupToneHint
    ) {
        if (analysisResult == null) {
            return "离线分析：未得到结果。";
        }
        String preview = trimPreview(bestDecodedPreview(analysisResult));
        int finalToneHz = analysisResult.signalSnapshot() == null
                ? 0
                : analysisResult.signalSnapshot().effectiveTrackedToneFrequencyHz();
        double wpm = analysisResult.timingSnapshot() == null
                ? 0.0d
                : analysisResult.timingSnapshot().estimatedWpmPrecise() > 0.0d
                ? analysisResult.timingSnapshot().estimatedWpmPrecise()
                : analysisResult.timingSnapshot().estimatedWpm();
        return String.format(
                Locale.US,
                "离线分析（共享 replay）：%d frames | %d tone | %d decode | %d turns | %d repairs | 锁定 %s | WPM %.1f\n%s\n预览：%s",
                analysisResult.processedFrameCount(),
                analysisResult.toneEventCount(),
                analysisResult.decodeEventCount(),
                analysisResult.turnCount(),
                analysisResult.tailRepairCount(),
                finalToneHz > 0 ? finalToneHz + " Hz" : "-",
                wpm,
                formatStartupToneHint(startupToneHint),
                preview
        );
    }

    private String formatStartupToneHint(
            @Nullable RxDeveloperStartupToneHintAnalyzer.Result startupToneHint
    ) {
        if (startupToneHint == null) {
            return "开发提示：startup raw spectrum hint 未执行。";
        }
        if (!startupToneHint.accepted()) {
            return "开发提示：暂无保守 startup 固定 Tone 建议"
                    + "（仅基于 raw spectrum，非 shared replay 结论；"
                    + startupToneHint.decisionCode()
                    + "）";
        }
        return String.format(
                Locale.US,
                "开发提示：可试固定 Tone %d Hz（仅基于 startup raw spectrum，非 shared replay 结论；%s，%d frames）",
                startupToneHint.suggestedToneHz(),
                startupToneHint.clusterSummary(),
                startupToneHint.supportFrames()
        );
    }

    private String bestDecodedPreview(RxReplayAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "(empty)";
        }
        String decodedText = analysisResult.decodedText();
        if (decodedText != null && !decodedText.trim().isEmpty()) {
            return decodedText.trim();
        }
        if (analysisResult.decoderSnapshot() != null) {
            String fallback = analysisResult.decoderSnapshot().decodedText();
            if (fallback != null && !fallback.trim().isEmpty()) {
                return fallback.trim();
            }
        }
        return "(empty)";
    }

    private String trimPreview(String text) {
        if (text == null) {
            return "(empty)";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return "(empty)";
        }
        if (compact.length() <= TRACE_ANALYSIS_PREVIEW_MAX_CHARS) {
            return compact;
        }
        return compact.substring(0, TRACE_ANALYSIS_PREVIEW_MAX_CHARS - 1) + "…";
    }

    private String safeAnalysisErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return "unknown error";
        }
        return exception.getMessage().trim();
    }
}
