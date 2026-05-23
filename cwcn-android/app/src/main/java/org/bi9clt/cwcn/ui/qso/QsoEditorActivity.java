package org.bi9clt.cwcn.ui.qso;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LogDisplayFormatter;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftFactory;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.rig.RigFrequencyResolver;
import org.bi9clt.cwcn.databinding.ActivityQsoEditorBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class QsoEditorActivity extends AppCompatActivity {
    public static final String EXTRA_CONFIRMED_LOG_ID =
            "org.bi9clt.cwcn.ui.qso.extra.CONFIRMED_LOG_ID";
    public static final String EXTRA_START_FRESH =
            "org.bi9clt.cwcn.ui.qso.extra.START_FRESH";
    public static final String EXTRA_SEED_COMMENT =
            "org.bi9clt.cwcn.ui.qso.extra.SEED_COMMENT";

    private static final String LOCAL_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private ActivityQsoEditorBinding binding;
    private LocalLogRepository localLogRepository;
    private StationProfileStore stationProfileStore;
    private RigFrequencyResolver rigFrequencyResolver;
    private QsoDraftSnapshot currentDraftSnapshot;
    private ConfirmedQsoLog currentConfirmedLog;
    private long currentConfirmedLogId;
    private boolean syncingEditor;
    private boolean editorDirty;
    private String seedComment;
    private String actionStatusMessage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQsoEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        stationProfileStore = new StationProfileStore(this);
        rigFrequencyResolver = new RigFrequencyResolver(this);
        setupActions();
        setupEditorWatchers();
        reloadFromRepository(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadFromRepository(false);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.openLogbookButton.setOnClickListener(view ->
                startActivity(new Intent(this, QsoLogbookActivity.class)));
        binding.resetButton.setOnClickListener(view -> resetEditor());
        binding.saveButton.setOnClickListener(view -> saveRecord());
    }

    private void setupEditorWatchers() {
        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                onEditorChanged();
            }
        };
        binding.qsoTimeEditText.addTextChangedListener(watcher);
        binding.remoteCallsignEditText.addTextChangedListener(watcher);
        binding.stationCallsignEditText.addTextChangedListener(watcher);
        binding.frequencyEditText.addTextChangedListener(watcher);
        binding.rstSentEditText.addTextChangedListener(watcher);
        binding.rstRcvdEditText.addTextChangedListener(watcher);
        binding.nameEditText.addTextChangedListener(watcher);
        binding.qthEditText.addTextChangedListener(watcher);
        binding.remoteGridEditText.addTextChangedListener(watcher);
        binding.stationGridEditText.addTextChangedListener(watcher);
        binding.commentEditText.addTextChangedListener(watcher);
        binding.manualConfirmedCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onEditorChanged();
            }
        });
    }

    private void onEditorChanged() {
        if (syncingEditor) {
            return;
        }
        editorDirty = true;
        refreshDerivedViews();
        refreshStatusText();
    }

    private void reloadFromRepository(boolean discardUnsavedChanges) {
        if (editorDirty && !discardUnsavedChanges) {
            refreshUi();
            return;
        }

        currentConfirmedLogId = resolveConfirmedLogIdExtra();
        boolean startFresh = shouldStartFresh();
        seedComment = normalizeIntentText(
                getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SEED_COMMENT)
        );
        currentConfirmedLog = currentConfirmedLogId > 0L
                ? localLogRepository.loadConfirmedLogById(currentConfirmedLogId)
                : null;
        if (currentConfirmedLog != null) {
            currentDraftSnapshot = QsoDraftFactory.createDraftFromConfirmedLog(
                    currentConfirmedLog,
                    currentConfirmedLog.qsoTimeUtcEpochMs() > 0L
                            ? currentConfirmedLog.qsoTimeUtcEpochMs()
                            : System.currentTimeMillis(),
                    "loaded from confirmed log"
            );
        } else if (!startFresh) {
            currentDraftSnapshot = localLogRepository.loadDraft();
        } else {
            currentDraftSnapshot = null;
        }

        syncEditorFromSource();
        actionStatusMessage = "";
        refreshUi();
        tryPrefillFrequencyAsync();
    }

    private void resetEditor() {
        if (isEditingConfirmedLog()) {
            if (currentConfirmedLog == null) {
                Toast.makeText(this, R.string.qso_editor_toast_log_missing, Toast.LENGTH_SHORT).show();
                return;
            }
            syncEditorFromSource();
            actionStatusMessage = getString(R.string.qso_editor_action_status_restored_saved);
            refreshUi();
            return;
        }

        currentDraftSnapshot = localLogRepository.loadDraft();
        syncEditorFromSource();
        actionStatusMessage = currentDraftSnapshot == null
                ? getString(R.string.qso_editor_action_status_cleared_new)
                : getString(R.string.qso_editor_action_status_restored_draft);
        refreshUi();
    }

    private void saveRecord() {
        String validationError = validateEditor();
        if (validationError != null) {
            Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show();
            return;
        }

        ConfirmedQsoLog record = buildRecordFromEditor();
        if (record == null) {
            Toast.makeText(this, R.string.qso_editor_toast_build_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEditingConfirmedLog()) {
            boolean updated = localLogRepository.updateConfirmedLog(currentConfirmedLogId, record);
            if (!updated) {
                Toast.makeText(this, R.string.qso_editor_toast_update_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            currentConfirmedLog = localLogRepository.loadConfirmedLogById(currentConfirmedLogId);
            currentDraftSnapshot = currentConfirmedLog == null
                    ? null
                    : QsoDraftFactory.createDraftFromConfirmedLog(
                            currentConfirmedLog,
                            currentConfirmedLog.qsoTimeUtcEpochMs(),
                            "updated confirmed log"
                    );
            actionStatusMessage = getString(
                    R.string.qso_editor_action_status_updated,
                    safeValue(record.remoteCallsign())
            );
        } else {
            ConfirmedQsoLog saved = localLogRepository.saveConfirmedLog(record);
            if (saved == null) {
                Toast.makeText(this, R.string.qso_editor_toast_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            localLogRepository.clearDraft();
            currentConfirmedLogId = saved.id();
            currentConfirmedLog = saved;
            currentDraftSnapshot = QsoDraftFactory.createDraftFromConfirmedLog(
                    saved,
                    saved.qsoTimeUtcEpochMs(),
                    "saved from qso editor"
            );
            actionStatusMessage = getString(
                    R.string.qso_editor_action_status_saved,
                    safeValue(saved.remoteCallsign())
            );
        }

        syncEditorFromSource();
        refreshUi();
    }

    private void syncEditorFromSource() {
        syncingEditor = true;
        long qsoTimeUtcEpochMs = resolveSeedQsoTimeUtcEpochMs();
        binding.qsoTimeEditText.setText(formatLocalTime(qsoTimeUtcEpochMs));
        binding.remoteCallsignEditText.setText(valueOrEmpty(resolveRemoteCallsign()));
        binding.stationCallsignEditText.setText(valueOrEmpty(resolveStationCallsign()));
        binding.frequencyEditText.setText(formatFrequencyValue(resolveFrequencyHz()));
        binding.rstSentEditText.setText(valueOrEmpty(resolveRstSent()));
        binding.rstRcvdEditText.setText(valueOrEmpty(resolveRstRcvd()));
        binding.nameEditText.setText(valueOrEmpty(resolveName()));
        binding.qthEditText.setText(valueOrEmpty(resolveQth()));
        binding.remoteGridEditText.setText(valueOrEmpty(resolveRemoteGrid()));
        binding.stationGridEditText.setText(valueOrEmpty(resolveStationGrid()));
        binding.commentEditText.setText(valueOrEmpty(resolveComment()));
        binding.manualConfirmedCheckBox.setChecked(resolveManualConfirmed());
        syncingEditor = false;
        editorDirty = false;
        refreshDerivedViews();
        refreshStatusText();
    }

    private void tryPrefillFrequencyAsync() {
        if (isEditingConfirmedLog()) {
            return;
        }
        if (parseFrequencyHz(binding.frequencyEditText.getText()) > 0L) {
            return;
        }
        new Thread(() -> {
            long detectedFrequencyHz = rigFrequencyResolver == null
                    ? 0L
                    : rigFrequencyResolver.readCurrentFrequencyHz();
            if (detectedFrequencyHz <= 0L) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (parseFrequencyHz(binding.frequencyEditText.getText()) > 0L) {
                    return;
                }
                syncingEditor = true;
                binding.frequencyEditText.setText(String.valueOf(detectedFrequencyHz));
                syncingEditor = false;
                actionStatusMessage = getString(R.string.qso_editor_action_status_frequency_loaded);
                refreshDerivedViews();
                refreshStatusText();
            });
        }, "cwcn-qso-frequency-prefill").start();
    }

    private void refreshUi() {
        binding.titleText.setText(isEditingConfirmedLog()
                ? R.string.qso_editor_title_edit
                : R.string.qso_editor_title_new);
        binding.subtitleText.setText(isEditingConfirmedLog()
                ? R.string.qso_editor_subtitle_edit
                : R.string.qso_editor_subtitle_new);
        binding.editorModeChip.setText(isEditingConfirmedLog()
                ? R.string.qso_editor_mode_edit
                : R.string.qso_editor_mode_new);
        binding.saveButton.setText(isEditingConfirmedLog()
                ? R.string.qso_editor_action_update
                : R.string.qso_editor_action_save);
        binding.resetButton.setText(isEditingConfirmedLog()
                ? R.string.qso_editor_action_revert
                : R.string.qso_editor_action_reset);
        binding.seedSummaryText.setText(renderSeedSummary());
        refreshDerivedViews();
        refreshStatusText();
    }

    private void refreshDerivedViews() {
        Long parsedQsoTime = parseLocalTimeMillis(binding.qsoTimeEditText.getText());
        binding.qsoTimeHintText.setText(renderQsoTimeHint(parsedQsoTime));

        long frequencyHz = parseFrequencyHz(binding.frequencyEditText.getText());
        String bandLabel = LogDisplayFormatter.formatBand(frequencyHz);
        String frequencyLabel = LogDisplayFormatter.formatFrequency(frequencyHz);
        binding.bandSummaryText.setText(getString(
                R.string.qso_editor_band_summary,
                bandLabel,
                frequencyLabel
        ));

        Double distanceKm = resolveDistanceKm(
                normalizedEditorValue(binding.stationGridEditText.getText()),
                normalizedEditorValue(binding.remoteGridEditText.getText())
        );
        binding.distanceSummaryText.setText(getString(
                R.string.qso_editor_distance_summary,
                LogDisplayFormatter.formatDistanceKm(distanceKm)
        ));
    }

    private void refreshStatusText() {
        StringBuilder statusBuilder = new StringBuilder();
        statusBuilder.append(getString(editorDirty
                ? R.string.qso_editor_status_unsaved
                : R.string.qso_editor_status_loaded));
        if (isEditingConfirmedLog()) {
            statusBuilder.append("  ")
                    .append(getString(R.string.qso_editor_status_record_id, currentConfirmedLogId));
        } else if (currentDraftSnapshot != null) {
            statusBuilder.append("  ").append(getString(R.string.qso_editor_status_draft_seed));
        } else {
            statusBuilder.append("  ").append(getString(R.string.qso_editor_status_manual_entry));
        }
        binding.editorStatusText.setText(statusBuilder.toString());
        binding.actionStatusText.setText(actionStatusMessage.isEmpty()
                ? getString(R.string.qso_editor_status_utc_note)
                : actionStatusMessage);
    }

    @Nullable
    private String validateEditor() {
        if (normalizedEditorValue(binding.remoteCallsignEditText.getText()) == null) {
            return getString(R.string.qso_editor_error_remote_callsign_required);
        }
        if (normalizedEditorValue(binding.stationCallsignEditText.getText()) == null) {
            return getString(R.string.qso_editor_error_station_callsign_required);
        }
        if (parseLocalTimeMillis(binding.qsoTimeEditText.getText()) == null) {
            return getString(R.string.qso_editor_error_time_invalid);
        }
        return null;
    }

    @Nullable
    private ConfirmedQsoLog buildRecordFromEditor() {
        Long qsoTimeUtcEpochMs = parseLocalTimeMillis(binding.qsoTimeEditText.getText());
        if (qsoTimeUtcEpochMs == null) {
            return null;
        }
        long confirmedAtEpochMs = isEditingConfirmedLog() && currentConfirmedLog != null
                ? currentConfirmedLog.confirmedAtEpochMs()
                : System.currentTimeMillis();
        String remoteGrid = normalizedEditorValue(binding.remoteGridEditText.getText());
        String stationGrid = normalizedEditorValue(binding.stationGridEditText.getText());
        String remoteCallsign = normalizedEditorValue(binding.remoteCallsignEditText.getText());
        String comment = buildComment(
                normalizedEditorValue(binding.commentEditText.getText()),
                stationGrid,
                remoteGrid
        );
        return new ConfirmedQsoLog(
                isEditingConfirmedLog() && currentConfirmedLog != null ? currentConfirmedLog.id() : 0L,
                remoteCallsign,
                normalizedEditorValue(binding.stationCallsignEditText.getText()),
                qsoTimeUtcEpochMs,
                parseFrequencyHz(binding.frequencyEditText.getText()),
                normalizedEditorValue(binding.rstSentEditText.getText()),
                normalizedEditorValue(binding.rstRcvdEditText.getText()),
                remoteGrid,
                stationGrid,
                normalizedEditorValue(binding.nameEditText.getText()),
                normalizedEditorValue(binding.qthEditText.getText()),
                comment,
                binding.manualConfirmedCheckBox.isChecked(),
                "CW",
                resolvePhaseForSave(),
                currentDraftSnapshot == null ? "" : safeText(currentDraftSnapshot.normalizedText()),
                remoteCallsign != null && remoteCallsign.contains("?"),
                confirmedAtEpochMs
        );
    }

    private String renderSeedSummary() {
        if (isEditingConfirmedLog() && currentConfirmedLog != null) {
            return getString(
                    R.string.qso_editor_seed_summary_editing,
                    safeValue(currentConfirmedLog.remoteCallsign())
            );
        }
        if (currentDraftSnapshot == null) {
            return getString(R.string.qso_editor_seed_summary_empty);
        }
        return getString(R.string.qso_editor_seed_summary_seeded);
    }

    private String renderQsoTimeHint(@Nullable Long qsoTimeUtcEpochMs) {
        if (qsoTimeUtcEpochMs == null) {
            return getString(R.string.qso_editor_time_hint_empty);
        }
        return getString(
                R.string.qso_editor_time_hint_value,
                CwTimeUtcFormatter.format(qsoTimeUtcEpochMs)
        );
    }

    @Nullable
    private Long parseLocalTimeMillis(@Nullable Editable editable) {
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(LOCAL_TIME_PATTERN, Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getDefault());
        try {
            Date parsed = format.parse(raw);
            return parsed == null ? null : parsed.getTime();
        } catch (ParseException exception) {
            return null;
        }
    }

    private String formatLocalTime(long epochMillis) {
        if (epochMillis <= 0L) {
            epochMillis = System.currentTimeMillis();
        }
        SimpleDateFormat format = new SimpleDateFormat(LOCAL_TIME_PATTERN, Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMillis));
    }

    private long parseFrequencyHz(@Nullable Editable editable) {
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String formatFrequencyValue(long frequencyHz) {
        return frequencyHz > 0L ? String.valueOf(frequencyHz) : "";
    }

    private long resolveSeedQsoTimeUtcEpochMs() {
        if (currentConfirmedLog != null && currentConfirmedLog.qsoTimeUtcEpochMs() > 0L) {
            return currentConfirmedLog.qsoTimeUtcEpochMs();
        }
        if (currentDraftSnapshot != null && currentDraftSnapshot.updatedAtEpochMs() > 0L) {
            return currentDraftSnapshot.updatedAtEpochMs();
        }
        return System.currentTimeMillis();
    }

    private String resolveRemoteCallsign() {
        if (currentConfirmedLog != null && currentConfirmedLog.remoteCallsign() != null) {
            return currentConfirmedLog.remoteCallsign();
        }
        return currentDraftSnapshot == null ? null : currentDraftSnapshot.remoteCallsignCandidate();
    }

    private String resolveStationCallsign() {
        if (currentConfirmedLog != null && currentConfirmedLog.stationCallsign() != null) {
            return currentConfirmedLog.stationCallsign();
        }
        if (currentDraftSnapshot != null && currentDraftSnapshot.stationCallsignUsed() != null) {
            return currentDraftSnapshot.stationCallsignUsed();
        }
        return stationProfileStore == null ? null : stationProfileStore.stationCallsign();
    }

    private long resolveFrequencyHz() {
        return currentConfirmedLog == null ? 0L : currentConfirmedLog.frequencyHz();
    }

    private String resolveRstSent() {
        if (currentConfirmedLog != null && currentConfirmedLog.rstSent() != null) {
            return currentConfirmedLog.rstSent();
        }
        return currentDraftSnapshot == null ? null : currentDraftSnapshot.rstSentCandidate();
    }

    private String resolveRstRcvd() {
        if (currentConfirmedLog != null && currentConfirmedLog.rstRcvd() != null) {
            return currentConfirmedLog.rstRcvd();
        }
        return currentDraftSnapshot == null ? null : currentDraftSnapshot.rstRcvdCandidate();
    }

    private String resolveName() {
        if (currentConfirmedLog != null && currentConfirmedLog.name() != null) {
            return currentConfirmedLog.name();
        }
        return currentDraftSnapshot == null ? null : currentDraftSnapshot.nameCandidate();
    }

    private String resolveQth() {
        if (currentConfirmedLog != null && currentConfirmedLog.qth() != null) {
            return currentConfirmedLog.qth();
        }
        return currentDraftSnapshot == null ? null : currentDraftSnapshot.qthCandidate();
    }

    private String resolveRemoteGrid() {
        return currentConfirmedLog == null ? null : currentConfirmedLog.remoteGrid();
    }

    private String resolveStationGrid() {
        if (currentConfirmedLog != null && currentConfirmedLog.stationGrid() != null) {
            return currentConfirmedLog.stationGrid();
        }
        return stationProfileStore == null ? null : stationProfileStore.maidenheadGrid();
    }

    private String resolveComment() {
        if (currentConfirmedLog != null && currentConfirmedLog.comment() != null) {
            return currentConfirmedLog.comment();
        }
        return seedComment;
    }

    private boolean resolveManualConfirmed() {
        return currentConfirmedLog != null && currentConfirmedLog.manualConfirmed();
    }

    @Nullable
    private String resolvePhaseForSave() {
        if (currentConfirmedLog != null && currentConfirmedLog.phase() != null) {
            return currentConfirmedLog.phase();
        }
        if (currentDraftSnapshot == null || currentDraftSnapshot.phase() == null) {
            return null;
        }
        return currentDraftSnapshot.phase().displayName();
    }

    private String buildComment(String editorComment, String stationGrid, String remoteGrid) {
        Double distanceKm = resolveDistanceKm(stationGrid, remoteGrid);
        if (editorComment != null && !editorComment.isEmpty()) {
            return editorComment;
        }
        if (distanceKm != null && distanceKm > 0.0d) {
            return getString(R.string.qso_editor_comment_distance_seed, distanceKm);
        }
        return getString(R.string.qso_editor_comment_default);
    }

    @Nullable
    private Double resolveDistanceKm(String stationGrid, String remoteGrid) {
        ConfirmedQsoLog probe = new ConfirmedQsoLog(
                0L,
                "N0CALL",
                "N0CALL",
                System.currentTimeMillis(),
                0L,
                null,
                null,
                remoteGrid,
                stationGrid,
                null,
                null,
                null,
                false,
                "CW",
                null,
                "",
                false,
                0L
        );
        return probe.distanceKm();
    }

    private boolean isEditingConfirmedLog() {
        return currentConfirmedLog != null && currentConfirmedLogId > 0L;
    }

    private long resolveConfirmedLogIdExtra() {
        Intent intent = getIntent();
        if (intent == null) {
            return -1L;
        }
        return intent.getLongExtra(EXTRA_CONFIRMED_LOG_ID, -1L);
    }

    private boolean shouldStartFresh() {
        Intent intent = getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_START_FRESH, false);
    }

    @Nullable
    private String normalizedEditorValue(@Nullable Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Nullable
    private String normalizeIntentText(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeValue(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private static final class CwTimeUtcFormatter {
        private CwTimeUtcFormatter() {
        }

        private static String format(long epochMillis) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(new Date(epochMillis));
        }
    }
}
