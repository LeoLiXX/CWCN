package org.bi9clt.cwcn.ui.spectrum;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.app.RxInputSettingsStore;
import org.bi9clt.cwcn.core.app.SqlLevelStore;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumHistoryStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
import org.bi9clt.cwcn.core.spectrum.SqlThresholdAdvisor;
import org.bi9clt.cwcn.databinding.ActivitySpectrumBinding;
import org.bi9clt.cwcn.ui.navigation.FormalBottomNavStyler;
import org.bi9clt.cwcn.ui.operate.OperateActivity;
import org.bi9clt.cwcn.ui.qso.QsoLogbookActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SpectrumActivity extends AppCompatActivity {
    private static final long LIVE_SPECTRUM_REFRESH_INTERVAL_MS = 120L;
    private static final int DISPLAY_MAX_FREQUENCY_HZ = 3000;
    private static final int MIN_FOCUS_FREQUENCY_HZ = 100;
    private static final int MAX_FOCUS_FREQUENCY_HZ = 2900;
    private static final int FOCUS_FREQUENCY_STEP_HZ = 5;
    private ActivitySpectrumBinding binding;
    private RxSessionStore rxSessionStore;
    private RigSelectionStore rigSelectionStore;
    private DeveloperModeStore developerModeStore;
    private RxInputSettingsStore rxInputSettingsStore;
    private SqlLevelStore sqlLevelStore;
    private SpectrumHistoryStore spectrumHistoryStore;
    private long waterfallSequence;
    private int manualFocusFrequencyHz = -1;
    private int sqlLevel = SqlLevelStore.DEFAULT_SQL_LEVEL;
    private int sqlDisplayMax = SqlLevelStore.DEFAULT_SQL_DISPLAY_MAX;
    private boolean suppressSqlSeekbarCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            mainHandler.postDelayed(this, LIVE_SPECTRUM_REFRESH_INTERVAL_MS);
        }
    };
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySpectrumBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        rxSessionStore = new RxSessionStore(this);
        rigSelectionStore = new RigSelectionStore(this);
        developerModeStore = new DeveloperModeStore(this);
        rxInputSettingsStore = new RxInputSettingsStore(this);
        sqlLevelStore = new SqlLevelStore(this);
        spectrumHistoryStore = new SpectrumHistoryStore(this);
        sqlLevel = sqlLevelStore.load();
        sqlDisplayMax = sqlLevelStore.loadDisplayMax(sqlLevel);
        binding.rulerFrequencyView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        binding.columnarView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        binding.waterfallView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OperateActivity.requestSharedOperateRxResume();
        refreshUi();
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.postDelayed(liveRefreshRunnable, LIVE_SPECTRUM_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(liveRefreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(liveRefreshRunnable);
        super.onDestroy();
    }

    private void setupActions() {
        binding.backToOperateButton.setOnClickListener(view -> returnToOperate());
        binding.bottomNavView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_nav_operate) {
                returnToOperate();
                return true;
            }
            if (itemId == R.id.menu_nav_spectrum) {
                return true;
            }
            if (itemId == R.id.menu_nav_logbook) {
                startActivity(new Intent(this, QsoLogbookActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        setupSqlControls();
        setupManualToneDragging(binding.columnarView);
        setupManualToneDragging(binding.waterfallView);
    }

    private void returnToOperate() {
        finish();
    }

    private void refreshUi() {
        RxSessionSnapshot snapshot = rxSessionStore.load();
        RigProfile profile = rigSelectionStore.selectedProfile();
        List<SpectrumSnapshotData> spectrumHistory = loadSpectrumHistory(snapshot);
        SpectrumSnapshotData latestSpectrum = latestSpectrum(spectrumHistory);
        int[] waveData = buildWaveData(latestSpectrum);
        int focusFrequencyHz = resolveFocusFrequency(snapshot, latestSpectrum);
        int autoTrackedFrequencyHz = latestSpectrum == null
                ? resolveAutoTrackedFrequency(snapshot)
                : positiveOrFallback(latestSpectrum.finalAdoptedToneHz(), latestSpectrum.trackedToneHz());

        binding.spectrumStatusMainText.setText(renderStatusMain(snapshot, profile, latestSpectrum));
        binding.spectrumStatusDetailText.setText(renderStatusDetail(snapshot, spectrumHistory, latestSpectrum));
        binding.spectrumSqlValueText.setText(renderSqlValue(latestSpectrum));
        binding.spectrumSqlDetailText.setText(renderSqlDetail(latestSpectrum));
        binding.spectrumSqlHintText.setText(renderSqlHint(latestSpectrum));
        String summaryText = renderSummary(snapshot, latestSpectrum);
        binding.spectrumSummaryText.setText(summaryText);
        binding.spectrumSummaryText.setVisibility(hasMeaningfulText(summaryText) ? View.VISIBLE : View.GONE);
        binding.spectrumDraftText.setText("");
        binding.spectrumDraftText.setVisibility(View.GONE);
        binding.emptySpectrumStateText.setText(latestSpectrum == null ? "Waiting for live spectrum" : "");
        binding.emptySpectrumStateText.setVisibility(latestSpectrum == null ? View.VISIBLE : View.GONE);
        syncSqlSeekBarRange();
        if (binding.spectrumSqlSeekBar.getProgress() != sqlLevel) {
            suppressSqlSeekbarCallback = true;
            binding.spectrumSqlSeekBar.setProgress(sqlLevel);
            suppressSqlSeekbarCallback = false;
        }

        applyFocusFrequency(focusFrequencyHz, autoTrackedFrequencyHz);
        binding.columnarView.setSqlReferenceLevel(resolveSqlReferenceWaveLevel(latestSpectrum), sqlLevel);
        binding.columnarView.setSqlRecommendedLevel(resolveRecommendedSqlWaveLevel(latestSpectrum));
        applySqlMeter(latestSpectrum);
        binding.columnarView.setWaveData(waveData);
        binding.waterfallView.setWaveData(waveData, waterfallSequence++);

        FormalBottomNavStyler.apply(binding.bottomNavView, FormalBottomNavStyler.Page.SPECTRUM);
    }

    private void setupManualToneDragging(View targetView) {
        targetView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN
                    && action != MotionEvent.ACTION_MOVE
                    && action != MotionEvent.ACTION_UP) {
                return false;
            }
            if (view.getWidth() <= 0) {
                return false;
            }
            float clampedX = Math.max(0f, Math.min(event.getX(), view.getWidth()));
            int frequencyHz = normalizeDraggedFrequencyHz(
                    Math.round((clampedX / view.getWidth()) * DISPLAY_MAX_FREQUENCY_HZ)
            );
            if (frequencyHz != manualFocusFrequencyHz) {
                manualFocusFrequencyHz = frequencyHz;
                applyFocusFrequency(manualFocusFrequencyHz, resolveAutoTrackedFrequency(rxSessionStore.load()));
                persistPreferredToneFrequency(frequencyHz);
            } else {
                applyFocusFrequency(manualFocusFrequencyHz, resolveAutoTrackedFrequency(rxSessionStore.load()));
            }
            if (action == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return true;
        });
    }

    private void setupSqlControls() {
        syncSqlSeekBarRange();
        binding.spectrumSqlSeekBar.setProgress(sqlLevel);
        binding.spectrumSqlValueText.setText(renderSqlValue(null));
        binding.spectrumSqlSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || suppressSqlSeekbarCallback) {
                    return;
                }
                sqlLevel = clampSqlThreshold(progress);
                binding.spectrumSqlValueText.setText(renderSqlValue(latestSpectrum(loadSpectrumHistory(rxSessionStore.load()))));
                if (sqlLevelStore != null) {
                    sqlLevelStore.save(sqlLevel);
                }
                OperateActivity.requestSharedOperateSqlUpdate(sqlLevel);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateSqlDisplayMaxAfterRelease(seekBar.getProgress());
            }
        });
    }

    private void syncSqlSeekBarRange() {
        int normalizedDisplayMax = SqlLevelStore.ensureDisplayMaxCoversLevel(sqlDisplayMax, sqlLevel);
        if (normalizedDisplayMax != sqlDisplayMax) {
            sqlDisplayMax = normalizedDisplayMax;
            if (sqlLevelStore != null) {
                sqlLevelStore.saveDisplayMax(sqlDisplayMax);
            }
        }
        if (binding.spectrumSqlSeekBar.getMax() != sqlDisplayMax) {
            binding.spectrumSqlSeekBar.setMax(sqlDisplayMax);
        }
    }

    private void updateSqlDisplayMaxAfterRelease(int releasedProgress) {
        int releasedSqlLevel = clampSqlThreshold(releasedProgress);
        int adjustedDisplayMax = SqlLevelStore.adjustDisplayMaxForReleasedLevel(sqlDisplayMax, releasedSqlLevel);
        if (adjustedDisplayMax == sqlDisplayMax) {
            return;
        }
        sqlDisplayMax = adjustedDisplayMax;
        if (sqlLevelStore != null) {
            sqlLevelStore.saveDisplayMax(sqlDisplayMax);
        }
        syncSqlSeekBarRange();
        suppressSqlSeekbarCallback = true;
        binding.spectrumSqlSeekBar.setProgress(sqlLevel);
        suppressSqlSeekbarCallback = false;
    }

    private void applyFocusFrequency(int focusFrequencyHz, int autoTrackedFrequencyHz) {
        boolean fixedToneMode = rxInputSettingsStore != null
                && rxInputSettingsStore.rxToneMode() == RxInputSettingsStore.RxToneMode.FIXED_TONE;
        int trackingHalfWindowHz = fixedToneMode && rxInputSettingsStore != null
                ? rxInputSettingsStore.fixedToneLearningWindowHz()
                : 0;
        binding.rulerFrequencyView.setCenterFrequencyHz(focusFrequencyHz);
        binding.rulerFrequencyView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
        binding.columnarView.setTouchFrequencyHz(focusFrequencyHz);
        binding.columnarView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
        binding.columnarView.setTrackingWindowHz(focusFrequencyHz, trackingHalfWindowHz);
        binding.waterfallView.setTouchFrequencyHz(focusFrequencyHz);
        binding.waterfallView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
        binding.waterfallView.setTrackingWindowHz(focusFrequencyHz, trackingHalfWindowHz);
    }

    private int resolveFocusFrequency(
            @Nullable RxSessionSnapshot snapshot,
            @Nullable SpectrumSnapshotData latestSpectrum
    ) {
        if (manualFocusFrequencyHz > 0) {
            return manualFocusFrequencyHz;
        }
        int resolved = latestSpectrum == null
                ? resolveBestTone(snapshot)
                : positiveOrFallback(latestSpectrum.finalAdoptedToneHz(), latestSpectrum.trackedToneHz());
        return normalizeDraggedFrequencyHz(resolved);
    }

    @Nullable
    private int[] buildWaveData(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null || latestSpectrum.frequenciesHz().length == 0 || latestSpectrum.magnitudes().length == 0) {
            return null;
        }
        int[] frequencies = latestSpectrum.frequenciesHz();
        float[] magnitudes = latestSpectrum.magnitudes();
        int[] waveData = new int[frequencies.length];
        float floor = latestSpectrum.noiseFloorMagnitude();
        float ceiling = Math.max(latestSpectrum.peakMagnitude(), floor + 1f);
        for (int index = 0; index < frequencies.length; index++) {
            float normalized = (magnitudes[index] - floor) / Math.max(1f, ceiling - floor);
            int value = Math.round(Math.max(0f, Math.min(1f, normalized)) * 255f);
            waveData[index] = value;
        }
        return waveData;
    }

    private int resolveSqlReferenceWaveLevel(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null) {
            return -1;
        }
        float[] magnitudes = latestSpectrum.magnitudes();
        if (magnitudes.length == 0) {
            return -1;
        }

        float floor = latestSpectrum.noiseFloorMagnitude();
        float ceiling = Math.max(latestSpectrum.peakMagnitude(), floor + 1f);
        float sqlReferenceMagnitude = resolveThresholdMagnitude(
                latestSpectrum.sqlAttackThreshold(),
                latestSpectrum.sqlNoiseFloorEstimate(),
                latestSpectrum.sqlSignalFloorEstimate(),
                latestSpectrum.sqlToneRmsAmplitude(),
                latestSpectrum.peakMagnitude(),
                floor,
                ceiling
        );
        float normalized = (sqlReferenceMagnitude - floor) / Math.max(1f, ceiling - floor);
        return Math.round(Math.max(0f, Math.min(1f, normalized)) * 255f);
    }

    private int resolveRecommendedSqlWaveLevel(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null || latestSpectrum.sqlRecommendedThreshold() <= 0) {
            return -1;
        }
        float[] magnitudes = latestSpectrum.magnitudes();
        if (magnitudes.length == 0) {
            return -1;
        }
        float floor = latestSpectrum.noiseFloorMagnitude();
        float ceiling = Math.max(latestSpectrum.peakMagnitude(), floor + 1f);
        float sqlReferenceMagnitude = resolveThresholdMagnitude(
                latestSpectrum.sqlRecommendedThreshold(),
                latestSpectrum.sqlNoiseFloorEstimate(),
                latestSpectrum.sqlSignalFloorEstimate(),
                latestSpectrum.sqlToneRmsAmplitude(),
                latestSpectrum.peakMagnitude(),
                floor,
                ceiling
        );
        float normalized = (sqlReferenceMagnitude - floor) / Math.max(1f, ceiling - floor);
        return Math.round(Math.max(0f, Math.min(1f, normalized)) * 255f);
    }

    private float resolveThresholdMagnitude(
            int threshold,
            int sqlNoiseFloorEstimate,
            int sqlSignalFloorEstimate,
            float sqlToneRmsAmplitude,
            float peakMagnitude,
            float floor,
            float ceiling
    ) {
        if (threshold <= 0) {
            return floor;
        }

        float thresholdRatio = 0f;
        if (sqlSignalFloorEstimate > sqlNoiseFloorEstimate) {
            thresholdRatio = (threshold - sqlNoiseFloorEstimate)
                    / (float) Math.max(1, sqlSignalFloorEstimate - sqlNoiseFloorEstimate);
        } else if (sqlToneRmsAmplitude > 0f) {
            thresholdRatio = threshold / Math.max(1f, sqlToneRmsAmplitude);
        }
        thresholdRatio = Math.max(0f, Math.min(1.35f, thresholdRatio));
        float mappedMagnitude = floor + ((ceiling - floor) * thresholdRatio);

        if (sqlToneRmsAmplitude > 0f && peakMagnitude > 0f) {
            float tonePeakRatio = threshold / Math.max(1f, sqlToneRmsAmplitude);
            float peakMappedMagnitude = peakMagnitude * Math.max(0f, Math.min(1.2f, tonePeakRatio));
            mappedMagnitude = Math.max(mappedMagnitude, floor + ((peakMappedMagnitude - floor) * 0.5f));
        }

        return Math.max(floor, Math.min(ceiling, mappedMagnitude));
    }

    private void applySqlMeter(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null) {
            binding.spectrumSqlMeterView.setLevels(0f, 0f, 0f, 0f, 0f, 0f);
            return;
        }
        SqlThresholdAdvisor.Recommendation recommendation = SqlThresholdAdvisor.recommend(latestSpectrum);
        binding.spectrumSqlMeterView.setLevels(
                latestSpectrum.sqlFrameRmsAmplitude(),
                latestSpectrum.sqlToneRmsAmplitude(),
                latestSpectrum.sqlNoiseFloorEstimate(),
                latestSpectrum.sqlAttackThreshold(),
                latestSpectrum.sqlReleaseThreshold(),
                latestSpectrum.sqlRecommendedThreshold() > 0
                        ? latestSpectrum.sqlRecommendedThreshold()
                        : (recommendation.available() ? recommendation.recommendedThresholdLevel() : 0f)
        );
    }

    private String renderSqlValue(@Nullable SpectrumSnapshotData latestSpectrum) {
        return "SQL " + sqlLevel;
    }

    private String renderSqlDetail(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null || latestSpectrum.sqlAttackThreshold() <= 0) {
            return "MAN -  |  IN -  |  REC -";
        }
        int threshold = latestSpectrum.sqlAttackThreshold();
        int current = Math.round(latestSpectrum.sqlFrameRmsAmplitude());
        int recommendation = latestSpectrum.sqlRecommendedThreshold();
        StringBuilder builder = new StringBuilder();
        builder.append("MAN ").append(threshold)
                .append("  |  IN ").append(current);
        if (recommendation > 0) {
            builder.append("  |  REC ").append(recommendation);
        } else {
            builder.append("  |  REC -");
        }
        return builder.toString();
    }

    private String renderSqlHint(@Nullable SpectrumSnapshotData latestSpectrum) {
        if (latestSpectrum == null || latestSpectrum.sqlAttackThreshold() <= 0) {
            return "MAN 是手动门限，IN 是当前输入，REC 是推荐门限。";
        }
        float frameLevel = latestSpectrum.sqlFrameRmsAmplitude();
        float toneLevel = latestSpectrum.sqlToneRmsAmplitude();
        int threshold = latestSpectrum.sqlAttackThreshold();
        int noise = latestSpectrum.sqlNoiseFloorEstimate();
        int delta = Math.round(frameLevel) - threshold;
        int toneDelta = Math.round(toneLevel) - threshold;
        String recommendationSuffix = latestSpectrum.sqlRecommendedThreshold() > 0
                ? buildSqlRecommendationHintSuffix(latestSpectrum)
                : "";
        if (frameLevel < threshold) {
            if ((threshold - frameLevel) <= Math.max(4f, threshold * 0.08f)) {
                return "MAN 是手动门限，IN 刚好在线下方，适合等有效 CW 过线。" + recommendationSuffix;
            }
            return "MAN 是手动门限，IN 比它低 " + Math.abs(delta) + "；背景噪声应被压住。" + recommendationSuffix;
        }
        if (toneLevel > threshold) {
            return "MAN 是手动门限，Tone 比它高 " + Math.abs(toneDelta) + "；应允许有效 CW 通过。"
                    + recommendationSuffix;
        }
        if (noise >= threshold) {
            return "MAN 是手动门限；当前噪声已经贴近或越过它，建议继续上调门限。" + recommendationSuffix;
        }
        return "MAN 是手动门限，IN 比它高 " + Math.abs(delta) + "；若不是 CW，可继续提高门限。"
                + recommendationSuffix;
    }

    private int clampSqlThreshold(int threshold) {
        return Math.max(SqlLevelStore.MIN_SQL_LEVEL, Math.min(SqlLevelStore.MAX_SQL_LEVEL, threshold));
    }

    private String buildSqlRecommendationHintSuffix(SpectrumSnapshotData snapshotData) {
        StringBuilder builder = new StringBuilder();
        builder.append(" 推荐线 ").append(snapshotData.sqlRecommendedThreshold())
                .append("，噪声 ").append(snapshotData.sqlNoiseFloorEstimate());
        SqlThresholdAdvisor.Recommendation recommendation = SqlThresholdAdvisor.recommend(snapshotData);
        if (recommendation.limitedBySafetyFloor()) {
            builder.append("，当前受系统下限保护。");
            return builder.toString();
        }
        if (recommendation.limitedByToneHeadroom()) {
            builder.append("，已给弱 CW 留出过线空间。");
            return builder.toString();
        }
        builder.append("，当前按略高于噪声估计。");
        return builder.toString();
    }

    private String renderStatusMain(
            @Nullable RxSessionSnapshot snapshot,
            @Nullable RigProfile profile,
            @Nullable SpectrumSnapshotData latestSpectrum
    ) {
        String route = profile == null ? "ROUTE -" : safeCompact(profile.displayName(), 12);
        if (snapshot == null && latestSpectrum == null) {
            return "IDLE  |  " + route;
        }
        int wpm = resolveDisplayWpm(snapshot);
        int tone = latestSpectrum != null
                ? positiveOrFallback(latestSpectrum.finalAdoptedToneHz(), latestSpectrum.trackedToneHz())
                : resolveBestTone(snapshot);
        return "LIVE  |  " + positiveOrDash(wpm) + "WPM  |  " + positiveOrDash(tone) + "Hz  |  " + route;
    }

    private String renderStatusDetail(
            @Nullable RxSessionSnapshot snapshot,
            List<SpectrumSnapshotData> spectrumHistory,
            @Nullable SpectrumSnapshotData latestSpectrum
    ) {
        String source = latestSpectrum == null
                ? "NO DATA"
                : latestSpectrum.syntheticFallback()
                ? "SYNTH"
                : "WF " + spectrumHistory.size();
        if (snapshot == null) {
            return source;
        }
        return (snapshot.captureActive() ? "RAW 接收中" : "RAW 保持中")
                + "  |  "
                + source
                + "  |  "
                + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderSummary(@Nullable RxSessionSnapshot snapshot, @Nullable SpectrumSnapshotData latestSpectrum) {
        String baseSummary;
        if (snapshot == null && latestSpectrum == null) {
            baseSummary = "PEAK -  |  TRK -  |  FIN -";
        } else if (latestSpectrum != null) {
            baseSummary = (latestSpectrum.syntheticFallback() ? "SYNTH  |  " : "LIVE  |  ")
                    + "PEAK " + positiveOrDash(latestSpectrum.peakFrequencyHz()) + "Hz"
                    + "  |  TRK " + positiveOrDash(latestSpectrum.trackedToneHz())
                    + "  |  FIN " + positiveOrDash(latestSpectrum.finalAdoptedToneHz());
        } else {
            baseSummary = "PEAK -  |  TRK " + positiveOrDash(snapshot.targetToneFrequencyHz())
                    + "  |  FIN " + positiveOrDash(snapshot.effectiveToneFrequencyHz());
        }
        String developerFrontEndSummary = renderDeveloperFrontEndSummary(snapshot);
        if (!hasMeaningfulText(developerFrontEndSummary)) {
            return baseSummary;
        }
        return baseSummary + "\nFRONT  |  " + developerFrontEndSummary;
    }

    private String renderDeveloperFrontEndSummary(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null
                || developerModeStore == null
                || !developerModeStore.isEnabled()
                || !snapshot.hasDeveloperFrontEndSummary()) {
            return "";
        }
        return snapshot.developerFrontEndSummary();
    }

    private List<SpectrumSnapshotData> loadSpectrumHistory(@Nullable RxSessionSnapshot snapshot) {
        List<SpectrumSnapshotData> history = spectrumHistoryStore.loadHistory();
        if (!history.isEmpty()) {
            return history;
        }
        SpectrumSnapshotData fallback = buildFallbackSpectrumSnapshot(snapshot);
        if (fallback == null) {
            return Collections.emptyList();
        }
        ArrayList<SpectrumSnapshotData> syntheticHistory = new ArrayList<>();
        syntheticHistory.add(fallback);
        return syntheticHistory;
    }

    @Nullable
    private SpectrumSnapshotData buildFallbackSpectrumSnapshot(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        int preferredTone = positiveOrFallback(snapshot.preferredToneFrequencyHz(), 650);
        int trackedTone = positiveOrFallback(snapshot.targetToneFrequencyHz(), preferredTone);
        int effectiveTone = positiveOrFallback(snapshot.effectiveToneFrequencyHz(), trackedTone);
        int hypothesisTone = (preferredTone + trackedTone) / 2;
        int minHz = 0;
        int maxHz = DISPLAY_MAX_FREQUENCY_HZ;
        int points = (maxHz / 5) + 1;
        int[] frequenciesHz = new int[points];
        float[] magnitudes = new float[points];
        float peakMagnitude = 0f;
        int peakFrequencyHz = effectiveTone;

        for (int index = 0; index < points; index++) {
            int frequencyHz = minHz + (index * 5);
            frequenciesHz[index] = frequencyHz;
            float base = 10f;
            float preferredBump = gaussian(frequencyHz, preferredTone, 105f) * 26f;
            float trackedBump = gaussian(frequencyHz, trackedTone, 70f) * 38f;
            float effectiveBump = gaussian(frequencyHz, effectiveTone, 48f) * 54f;
            float wideShape = gaussian(frequencyHz, hypothesisTone, 220f) * 11f;
            float ripple = (float) (3.0 * Math.sin(index * 0.27));
            float magnitude = Math.max(2f, base + preferredBump + trackedBump + effectiveBump + wideShape + ripple);
            magnitudes[index] = magnitude;
            if (magnitude > peakMagnitude) {
                peakMagnitude = magnitude;
                peakFrequencyHz = frequencyHz;
            }
        }

        return new SpectrumSnapshotData(
                snapshot.updatedAtEpochMs(),
                frequenciesHz,
                magnitudes,
                peakFrequencyHz,
                peakMagnitude,
                14f,
                preferredTone,
                trackedTone,
                hypothesisTone,
                preferredTone,
                hypothesisTone,
                trackedTone,
                effectiveTone,
                "TRACK",
                "EFFECTIVE",
                true,
                false,
                hypothesisTone,
                "UI SYNTH",
                true
        );
    }

    @Nullable
    private SpectrumSnapshotData latestSpectrum(List<SpectrumSnapshotData> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    private boolean hasRealSpectrumHistory(List<SpectrumSnapshotData> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (SpectrumSnapshotData item : history) {
            if (item != null && !item.syntheticFallback()) {
                return true;
            }
        }
        return false;
    }

    private float gaussian(int x, int center, float spread) {
        if (spread <= 0f) {
            return 0f;
        }
        float delta = (x - center) / spread;
        return (float) Math.exp(-0.5f * delta * delta);
    }

    private int resolveBestTone(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            RigProfileSettings settings = rigSelectionStore == null
                    ? null
                    : rigSelectionStore.loadSettings(rigSelectionStore.selectedProfile());
            return settings == null ? 0 : settings.defaultToneFrequencyHz();
        }
        if (snapshot.effectiveToneFrequencyHz() > 0) {
            return snapshot.effectiveToneFrequencyHz();
        }
        if (snapshot.targetToneFrequencyHz() > 0) {
            return snapshot.targetToneFrequencyHz();
        }
        return snapshot.preferredToneFrequencyHz();
    }

    private int resolveAutoTrackedFrequency(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return -1;
        }
        if (snapshot.effectiveToneFrequencyHz() > 0) {
            return snapshot.effectiveToneFrequencyHz();
        }
        if (snapshot.targetToneFrequencyHz() > 0) {
            return snapshot.targetToneFrequencyHz();
        }
        return -1;
    }

    private int normalizeDraggedFrequencyHz(int frequencyHz) {
        int clamped = Math.max(MIN_FOCUS_FREQUENCY_HZ, Math.min(MAX_FOCUS_FREQUENCY_HZ, frequencyHz));
        return Math.round(clamped / (float) FOCUS_FREQUENCY_STEP_HZ) * FOCUS_FREQUENCY_STEP_HZ;
    }

    private void persistPreferredToneFrequency(int frequencyHz) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        if (existing.defaultToneFrequencyHz() == frequencyHz) {
            OperateActivity.requestSharedOperatePreferredToneUpdate(frequencyHz);
            return;
        }
        RigProfileSettings updated = new RigProfileSettings(
                existing.defaultWpm(),
                frequencyHz,
                existing.usbKeyLine(),
                existing.usbPreferredDeviceName(),
                existing.serialCatProtocolFamily(),
                existing.serialCatBaudRate(),
                existing.serialCatPortHint(),
                existing.serialCatKeyLine(),
                existing.serialCatKeyingPortHint(),
                existing.serialCatKeyingPolarity(),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                existing.serialCatCivAddressHex(),
                existing.networkCatProtocolFamily(),
                existing.networkHost(),
                existing.networkPort(),
                existing.bluetoothDeviceHint()
        );
        rigSelectionStore.saveSettings(profile, updated);
        OperateActivity.requestSharedOperatePreferredToneUpdate(frequencyHz);
    }

    private int positiveOrFallback(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String safeCompact(String value, int maxLength) {
        String safe = safeValue(value);
        if ("-".equals(safe) || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private boolean hasMeaningfulText(@Nullable String value) {
        return value != null && !value.trim().isEmpty() && !"-".equals(value.trim());
    }

    private int resolveDisplayWpm(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.stableEstimatedWpm() > 0 ? snapshot.stableEstimatedWpm() : snapshot.estimatedWpm();
    }

    private String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private String renderAge(long updatedAtEpochMs) {
        if (updatedAtEpochMs <= 0L) {
            return "-";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - updatedAtEpochMs);
        long ageSeconds = ageMs / 1000L;
        if (ageSeconds < 60L) {
            return ageSeconds + "s";
        }
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) {
            return ageMinutes + "m";
        }
        long ageHours = ageMinutes / 60L;
        return String.format(Locale.US, "%dh", ageHours);
    }
}
