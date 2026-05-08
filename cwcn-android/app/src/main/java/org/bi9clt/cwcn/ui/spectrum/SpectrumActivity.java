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
import org.bi9clt.cwcn.core.app.SqlLevelStore;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumHistoryStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
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
    private static final long LIVE_SPECTRUM_REFRESH_INTERVAL_MS = 350L;
    private static final int DISPLAY_MAX_FREQUENCY_HZ = 3000;
    private static final int MIN_FOCUS_FREQUENCY_HZ = 100;
    private static final int MAX_FOCUS_FREQUENCY_HZ = 2900;
    private static final int FOCUS_FREQUENCY_STEP_HZ = 5;

    private ActivitySpectrumBinding binding;
    private RxSessionStore rxSessionStore;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private SqlLevelStore sqlLevelStore;
    private SpectrumHistoryStore spectrumHistoryStore;
    private long waterfallSequence;
    private int manualFocusFrequencyHz = -1;
    private int sqlLevel = SqlLevelStore.DEFAULT_SQL_LEVEL;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            mainHandler.postDelayed(this, LIVE_SPECTRUM_REFRESH_INTERVAL_MS);
        }
    };
    private boolean returnToOperateOnNextPause;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySpectrumBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        rxSessionStore = new RxSessionStore(this);
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        sqlLevelStore = new SqlLevelStore(this);
        spectrumHistoryStore = new SpectrumHistoryStore(this);
        sqlLevel = sqlLevelStore.load();
        binding.rulerFrequencyView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        binding.columnarView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        binding.waterfallView.setMaxFrequencyHz(DISPLAY_MAX_FREQUENCY_HZ);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.postDelayed(liveRefreshRunnable, LIVE_SPECTRUM_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(liveRefreshRunnable);
        if (!returnToOperateOnNextPause && !isChangingConfigurations()) {
            OperateActivity.requestSharedOperateRxStop();
        }
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
        returnToOperateOnNextPause = true;
        finish();
    }

    private void refreshUi() {
        RxSessionSnapshot snapshot = rxSessionStore.load();
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
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
        binding.spectrumSqlValueText.setText(String.valueOf(sqlLevel));
        binding.spectrumSummaryText.setText(renderSummary(snapshot, latestSpectrum));
        binding.spectrumDraftText.setText(renderDraftText(overview));
        binding.emptySpectrumStateText.setText(latestSpectrum == null ? "Waiting for live spectrum" : "");
        binding.emptySpectrumStateText.setVisibility(latestSpectrum == null ? View.VISIBLE : View.GONE);
        if (binding.spectrumSqlSeekBar.getProgress() != sqlLevel) {
            binding.spectrumSqlSeekBar.setProgress(sqlLevel);
        }

        applyFocusFrequency(focusFrequencyHz, autoTrackedFrequencyHz);
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
        binding.spectrumSqlSeekBar.setMax(100);
        binding.spectrumSqlSeekBar.setProgress(sqlLevel);
        binding.spectrumSqlValueText.setText(String.valueOf(sqlLevel));
        binding.spectrumSqlSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                sqlLevel = progress;
                binding.spectrumSqlValueText.setText(String.valueOf(sqlLevel));
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
            }
        });
    }

    private void applyFocusFrequency(int focusFrequencyHz, int autoTrackedFrequencyHz) {
        binding.rulerFrequencyView.setCenterFrequencyHz(focusFrequencyHz);
        binding.rulerFrequencyView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
        binding.columnarView.setTouchFrequencyHz(focusFrequencyHz);
        binding.columnarView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
        binding.waterfallView.setTouchFrequencyHz(focusFrequencyHz);
        binding.waterfallView.setAutoTrackedFrequencyHz(autoTrackedFrequencyHz);
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

    private String renderStatusMain(
            @Nullable RxSessionSnapshot snapshot,
            @Nullable RigProfile profile,
            @Nullable SpectrumSnapshotData latestSpectrum
    ) {
        String route = profile == null ? "ROUTE -" : safeCompact(profile.displayName(), 12);
        if (snapshot == null && latestSpectrum == null) {
            return "IDLE  |  " + route;
        }
        int wpm = snapshot == null ? 0 : snapshot.estimatedWpm();
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
        return safeValue(snapshot.phaseDisplayName()) + "  |  " + source + "  |  " + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderSummary(@Nullable RxSessionSnapshot snapshot, @Nullable SpectrumSnapshotData latestSpectrum) {
        if (snapshot == null && latestSpectrum == null) {
            return "PEAK -  |  TRK -  |  FIN -";
        }
        if (latestSpectrum != null) {
            return (latestSpectrum.syntheticFallback() ? "SYNTH  |  " : "LIVE  |  ")
                    + "PEAK " + positiveOrDash(latestSpectrum.peakFrequencyHz()) + "Hz"
                    + "  |  TRK " + positiveOrDash(latestSpectrum.trackedToneHz())
                    + "  |  FIN " + positiveOrDash(latestSpectrum.finalAdoptedToneHz());
        }
        return "PEAK -  |  TRK " + positiveOrDash(snapshot.targetToneFrequencyHz())
                + "  |  FIN " + positiveOrDash(snapshot.effectiveToneFrequencyHz());
    }

    private String renderDraftText(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "";
        }
        return "DRAFT  " + safeValue(draft.remoteCallsignCandidate())
                + "  |  "
                + safeValue(draft.phase().displayName())
                + "  |  RST " + safeValue(draft.rstSentCandidate()) + "/" + safeValue(draft.rstRcvdCandidate());
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
