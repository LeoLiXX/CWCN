package org.bi9clt.cwcn.ui.developer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.R;
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

public final class DeveloperToolsActivity extends AppCompatActivity {
    public static final String EXTRA_TRACE_WAV_FILE_PATH =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_WAV_FILE_PATH";
    public static final String EXTRA_TRACE_PREFERRED_TONE_FREQUENCY_HZ =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_PREFERRED_TONE_FREQUENCY_HZ";
    public static final String EXTRA_TRACE_SQL_LEVEL =
            "org.bi9clt.cwcn.ui.developer.extra.TRACE_SQL_LEVEL";

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
        binding.versionText.setText(getString(R.string.developer_tools_version, BuildConfig.VERSION_NAME));
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
                ? getString(R.string.developer_tools_mode_on)
                : getString(R.string.developer_tools_mode_off));
        binding.toggleDeveloperModeButton.setText(enabled
                ? getString(R.string.developer_tools_toggle_off)
                : getString(R.string.developer_tools_toggle_on));
        int visibility = enabled ? View.VISIBLE : View.GONE;
        binding.toolsPanel.setVisibility(visibility);
        binding.toolsHintText.setText(enabled
                ? getString(R.string.developer_tools_tools_hint_on)
                : getString(R.string.developer_tools_tools_hint_off));
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
        if (latestTrace.hasSqlLevel()) {
            intent.putExtra(EXTRA_TRACE_SQL_LEVEL, latestTrace.sqlLevel());
        }
        startActivity(intent);
    }

    private String renderLatestTraceStatus(@Nullable LiveRxTraceArtifact artifact) {
        if (artifact == null || !artifact.hasReplayableAudio()) {
            return getString(R.string.developer_tools_latest_trace_empty);
        }
        File wavFile = new File(artifact.wavFilePath());
        File logFile = artifact.hasTraceLog() ? new File(artifact.logFilePath()) : null;
        String toneSummary = artifact.hasPreferredToneFrequency()
                ? artifact.preferredToneFrequencyHz() + " Hz"
                : getString(R.string.developer_tools_not_recorded);
        String sqlSummary = artifact.hasSqlLevel()
                ? String.valueOf(artifact.sqlLevel())
                : getString(R.string.developer_tools_not_recorded);
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.developer_tools_trace_latest, artifact.sessionLabel()));
        builder.append('\n').append(getString(R.string.developer_tools_trace_source, artifact.sourceLabel()));
        builder.append('\n').append(getString(
                R.string.developer_tools_trace_audio,
                artifact.durationMs(),
                artifact.sampleRateHz(),
                artifact.sampleCount()
        ));
        builder.append('\n').append(getString(
                R.string.developer_tools_trace_settings,
                toneSummary,
                sqlSummary
        ));
        builder.append('\n').append(latestTraceAnalysisSummary.isEmpty()
                ? getString(R.string.developer_tools_trace_analysis_waiting)
                : latestTraceAnalysisSummary);
        builder.append('\n').append(getString(R.string.developer_tools_trace_wav_path, wavFile.getAbsolutePath()));
        if (logFile != null && logFile.exists()) {
            builder.append('\n').append(getString(R.string.developer_tools_trace_log_path, logFile.getAbsolutePath()));
        }
        return builder.toString();
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
        latestTraceAnalysisSummary = getString(R.string.developer_tools_trace_analysis_pending);
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
                    artifact.sqlLevel(),
                    DEFAULT_TRACE_ANALYSIS_SEED_WPM
            );
            RxDeveloperStartupToneHintAnalyzer.Result startupToneHint =
                    new RxDeveloperStartupToneHintAnalyzer().analyze(
                            loadedWav.frames(),
                            artifact.preferredToneFrequencyHz()
                    );
            summary = formatTraceAnalysisSummary(analysisResult, startupToneHint);
        } catch (Exception exception) {
            summary = getString(
                    R.string.developer_tools_trace_analysis_failed,
                    safeAnalysisErrorMessage(exception)
            );
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
            return getString(R.string.developer_tools_trace_analysis_no_result);
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
        return getString(
                R.string.developer_tools_trace_analysis_summary,
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
            return getString(R.string.developer_tools_trace_hint_not_run);
        }
        if (!startupToneHint.accepted()) {
            return getString(
                    R.string.developer_tools_trace_hint_none,
                    startupToneHint.decisionCode()
            );
        }
        return getString(
                R.string.developer_tools_trace_hint_fixed,
                startupToneHint.suggestedToneHz(),
                startupToneHint.clusterSummary(),
                startupToneHint.supportFrames()
        );
    }

    private String bestDecodedPreview(RxReplayAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return getString(R.string.developer_tools_preview_empty);
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
        return getString(R.string.developer_tools_preview_empty);
    }

    private String trimPreview(String text) {
        if (text == null) {
            return getString(R.string.developer_tools_preview_empty);
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return getString(R.string.developer_tools_preview_empty);
        }
        if (compact.length() <= TRACE_ANALYSIS_PREVIEW_MAX_CHARS) {
            return compact;
        }
        return compact.substring(0, TRACE_ANALYSIS_PREVIEW_MAX_CHARS - 1) + "…";
    }

    private String safeAnalysisErrorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return getString(R.string.developer_tools_unknown_error);
        }
        return exception.getMessage().trim();
    }
}
