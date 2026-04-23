package org.bi9clt.cwcn.ui.qso;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.adif.CwAdifFileWriter;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LogDisplayFormatter;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftFactory;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatter;
import org.bi9clt.cwcn.databinding.ActivityQsoEditorBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class QsoEditorActivity extends AppCompatActivity {
    public static final String EXTRA_CONFIRMED_LOG_ID =
            "org.bi9clt.cwcn.ui.qso.extra.CONFIRMED_LOG_ID";

    private ActivityQsoEditorBinding binding;
    private LocalLogRepository localLogRepository;
    private QsoDraftSnapshot currentDraftSnapshot;
    private ConfirmedQsoLog currentConfirmedLog;
    private long currentConfirmedLogId;
    private String actionStatusMessage = "";
    private String editorStatusMessage = "";
    private boolean syncingEditor;
    private boolean editorDirty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQsoEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        setupEditorWatchers();
        setupActions();
        reloadFromRepository();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadFromRepository(false);
    }

    private void setupEditorWatchers() {
        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditor) {
                    editorDirty = true;
                    binding.editorStatusText.setText(renderEditorStatus());
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(watcher);
        binding.remoteCallsignEditText.addTextChangedListener(watcher);
        binding.rstSentEditText.addTextChangedListener(watcher);
        binding.rstRcvdEditText.addTextChangedListener(watcher);
        binding.nameEditText.addTextChangedListener(watcher);
        binding.qthEditText.addTextChangedListener(watcher);
    }

    private void setupActions() {
        binding.backToDebugButton.setOnClickListener(view -> finish());
        binding.openLogbookButton.setOnClickListener(view ->
                startActivity(new Intent(this, QsoLogbookActivity.class)));
        binding.reloadDraftButton.setOnClickListener(view -> {
            reloadFromRepository(true);
            editorStatusMessage = isEditingConfirmedLog()
                    ? "Reloaded confirmed log from local storage."
                    : "Reloaded draft from local storage.";
            refreshUi();
        });
        binding.clearDraftButton.setOnClickListener(view -> clearEditorContext());
        binding.saveDraftButton.setOnClickListener(view -> saveDraft());
        binding.confirmLogButton.setOnClickListener(view -> {
            if (isEditingConfirmedLog()) {
                updateConfirmedLog();
            } else {
                confirmLog();
            }
        });
        binding.exportAdifButton.setOnClickListener(view -> exportAdif());
    }

    private void reloadFromRepository() {
        reloadFromRepository(true);
    }

    private void reloadFromRepository(boolean discardUnsavedChanges) {
        if (editorDirty && !discardUnsavedChanges) {
            refreshUi();
            return;
        }

        currentConfirmedLogId = resolveConfirmedLogIdExtra();
        currentConfirmedLog = currentConfirmedLogId > 0L
                ? localLogRepository.loadConfirmedLogById(currentConfirmedLogId)
                : null;
        if (currentConfirmedLog != null) {
            currentDraftSnapshot = QsoDraftFactory.createDraftFromConfirmedLog(
                    currentConfirmedLog,
                    System.currentTimeMillis(),
                    "loaded from confirmed log"
            );
        } else {
            currentDraftSnapshot = localLogRepository.loadDraft();
        }
        syncEditorFromSnapshot();
        refreshUi();
    }

    private void saveDraft() {
        QsoDraftSnapshot snapshot = buildSnapshotFromEditor();
        if (!hasDraftContent(snapshot)) {
            Toast.makeText(this, "No draft data to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        localLogRepository.saveDraft(snapshot);
        currentDraftSnapshot = snapshot;
        editorDirty = false;
        editorStatusMessage = "Saved draft from QSO editor.";
        actionStatusMessage = "Draft saved.";
        refreshUi();
    }

    private void clearEditorContext() {
        if (isEditingConfirmedLog()) {
            if (currentConfirmedLog == null) {
                Toast.makeText(this, "Confirmed log is no longer available.", Toast.LENGTH_SHORT).show();
                return;
            }
            currentDraftSnapshot = QsoDraftFactory.createDraftFromConfirmedLog(
                    currentConfirmedLog,
                    System.currentTimeMillis(),
                    "reset to confirmed log"
            );
            actionStatusMessage = "Reset editor back to confirmed log.";
            editorStatusMessage = "Discarded unsaved edits and restored the selected log.";
            syncEditorFromSnapshot();
            refreshUi();
            return;
        }

        if (!hasEditorContent() && currentDraftSnapshot == null) {
            Toast.makeText(this, "No active draft to clear.", Toast.LENGTH_SHORT).show();
            return;
        }

        localLogRepository.clearDraft();
        currentDraftSnapshot = null;
        actionStatusMessage = "Cleared active draft.";
        editorStatusMessage = "Cleared stored draft and reset editor.";
        syncEditorFromSnapshot();
        refreshUi();
    }

    private void updateConfirmedLog() {
        if (currentConfirmedLog == null || currentConfirmedLogId <= 0L) {
            Toast.makeText(this, "Confirmed log is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }

        QsoDraftSnapshot snapshot = buildSnapshotFromEditor();
        if (snapshot.remoteCallsignCandidate() == null || snapshot.remoteCallsignCandidate().isEmpty()) {
            Toast.makeText(this, "Remote callsign is required before saving.", Toast.LENGTH_SHORT).show();
            return;
        }

        ConfirmedQsoLog updatedLog = currentConfirmedLog.withDraftEdits(snapshot);
        boolean updated = localLogRepository.updateConfirmedLog(currentConfirmedLogId, updatedLog);
        if (!updated) {
            Toast.makeText(this, "Unable to update confirmed log.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentConfirmedLog = localLogRepository.loadConfirmedLogById(currentConfirmedLogId);
        currentDraftSnapshot = currentConfirmedLog == null
                ? null
                : QsoDraftFactory.createDraftFromConfirmedLog(
                        currentConfirmedLog,
                        System.currentTimeMillis(),
                        "updated confirmed log"
                );
        editorDirty = false;
        editorStatusMessage = "Saved editor changes back into the confirmed log.";
        actionStatusMessage = "Updated confirmed log: "
                + safeValue(updatedLog.remoteCallsign())
                + (updatedLog.needManualReview() ? " (review flag kept)" : "");
        syncEditorFromSnapshot();
        refreshUi();
    }

    private void confirmLog() {
        QsoDraftSnapshot snapshot = buildSnapshotFromEditor();
        if (snapshot.remoteCallsignCandidate() == null || snapshot.remoteCallsignCandidate().isEmpty()) {
            Toast.makeText(this, "Remote callsign is required before confirming.", Toast.LENGTH_SHORT).show();
            return;
        }

        ConfirmedQsoLog log = localLogRepository.confirmDraft(snapshot, System.currentTimeMillis());
        localLogRepository.clearDraft();
        currentDraftSnapshot = null;
        editorDirty = false;
        editorStatusMessage = "Confirmed log and cleared active draft.";
        actionStatusMessage = "Confirmed log: "
                + log.remoteCallsign()
                + (log.needManualReview() ? " (review flag kept)" : "");
        syncEditorFromSnapshot();
        refreshUi();
    }

    private void exportAdif() {
        List<ConfirmedQsoLog> logs = localLogRepository.loadConfirmedLogs();
        if (logs.isEmpty()) {
            Toast.makeText(this, "No confirmed logs available for export.", Toast.LENGTH_SHORT).show();
            return;
        }

        File targetFile;
        try {
            targetFile = CwAdifFileWriter.export(this, logs, BuildConfig.VERSION_NAME);
        } catch (IOException exception) {
            actionStatusMessage = "ADIF export failed: " + exception.getMessage();
            Toast.makeText(this, "ADIF export failed.", Toast.LENGTH_SHORT).show();
            refreshUi();
            return;
        }

        actionStatusMessage = "Exported " + logs.size()
                + " log(s) to "
                + targetFile.getAbsolutePath();
        refreshUi();
    }

    private void refreshUi() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        QsoDraftSnapshot draftForDisplay = buildDisplaySnapshot();
        binding.draftMetaText.setText(renderDraftMeta(draftForDisplay, overview));
        binding.draftEvidenceText.setText(renderDraftEvidence(draftForDisplay));
        binding.draftReviewText.setText(renderDraftReview(draftForDisplay));
        binding.fieldOriginText.setText(QsoWorkflowSummaryFormatter.renderDraftFieldOriginSummary(draftForDisplay));
        binding.editorStatusText.setText(renderEditorStatus());
        binding.actionStatusText.setText(renderActionStatus());
        binding.confirmedLogSummaryText.setText(renderConfirmedLogSummary(overview));
        binding.confirmedLogListText.setText(renderConfirmedLogList());
        binding.reloadDraftButton.setText(isEditingConfirmedLog() ? "Reload Log" : "Reload Draft");
        binding.saveDraftButton.setText(isEditingConfirmedLog() ? "Save Draft Copy" : "Save Draft");
        binding.clearDraftButton.setText(isEditingConfirmedLog() ? "Reset To Log" : "Clear Draft");
        binding.saveDraftButton.setEnabled(editorDirty || hasEditorContent());
        binding.confirmLogButton.setEnabled(hasRemoteCallsign());
        binding.clearDraftButton.setEnabled(editorDirty || currentDraftSnapshot != null || hasEditorContent());
        binding.confirmLogButton.setText(resolveConfirmButtonText(draftForDisplay));
        binding.exportAdifButton.setEnabled(overview.confirmedLogCount() > 0);
    }

    private void syncEditorFromSnapshot() {
        syncingEditor = true;
        binding.stationCallsignEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.stationCallsignUsed())));
        binding.remoteCallsignEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.remoteCallsignCandidate())));
        binding.rstSentEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.rstSentCandidate())));
        binding.rstRcvdEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.rstRcvdCandidate())));
        binding.nameEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.nameCandidate())));
        binding.qthEditText.setText(valueOrEmpty(currentValue(currentDraftSnapshot == null ? null : currentDraftSnapshot.qthCandidate())));
        syncingEditor = false;
        editorDirty = false;
    }

    private QsoDraftSnapshot buildSnapshotFromEditor() {
        return QsoDraftFactory.createManualDraft(
                currentDraftSnapshot,
                normalizedEditorValue(binding.stationCallsignEditText.getText()),
                normalizedEditorValue(binding.remoteCallsignEditText.getText()),
                normalizedEditorValue(binding.rstSentEditText.getText()),
                normalizedEditorValue(binding.rstRcvdEditText.getText()),
                normalizedEditorValue(binding.nameEditText.getText()),
                normalizedEditorValue(binding.qthEditText.getText()),
                System.currentTimeMillis(),
                "edited in QSO editor"
        );
    }

    private QsoDraftSnapshot buildDisplaySnapshot() {
        if (!editorDirty) {
            return currentDraftSnapshot;
        }
        if (!hasEditorContent() && currentDraftSnapshot == null) {
            return null;
        }
        return buildSnapshotFromEditor();
    }

    private String renderDraftMeta(QsoDraftSnapshot snapshot, AppOverviewSnapshot overview) {
        if (snapshot == null) {
            return "No active draft stored yet.";
        }

        String updatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(snapshot.updatedAtEpochMs()));
        return "Phase: " + snapshot.phase().displayName()
                + "\nReady: " + yesNo(snapshot.readyForDraftConfirmation())
                + "\nManual review: " + yesNo(snapshot.needManualReview())
                + "\nReview queue: " + (overview == null ? 0 : overview.manualReviewLogCount())
                + "\nSource: " + resolveDraftSourceLabel()
                + "\nUpdated: " + updatedAt;
    }

    private String renderDraftEvidence(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            return "No normalized text / hints available yet.";
        }

        String hints = snapshot.hints().isEmpty()
                ? "(none)"
                : String.join(" / ", snapshot.hints());
        String normalizedText = snapshot.normalizedText().isEmpty()
                ? "(none)"
                : snapshot.normalizedText();
        String lastEventSummary = snapshot.lastEvent() == null
                ? "(none)"
                : snapshot.lastEvent().summary();
        return "Normalized: " + normalizedText
                + "\nHints: " + hints
                + "\nLast event: " + lastEventSummary;
    }

    private String renderDraftReview(QsoDraftSnapshot snapshot) {
        return QsoWorkflowSummaryFormatter.renderDraftReviewSummary(snapshot)
                + "\nNext step: " + QsoWorkflowSummaryFormatter.renderDraftNextStep(snapshot, editorDirty);
    }

    private String renderEditorStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append(editorDirty ? "Editor has unsaved changes." : "Editor is in sync with stored draft.");
        if (!editorStatusMessage.isEmpty()) {
            builder.append("\nLast edit action: ").append(editorStatusMessage);
        }
        if (isEditingConfirmedLog()) {
            builder.append("\nMode: editing confirmed log #").append(currentConfirmedLogId);
        }
        return builder.toString();
    }

    private String renderActionStatus() {
        if (actionStatusMessage.isEmpty()) {
            return "No save/confirm/export action yet.";
        }
        return actionStatusMessage;
    }

    private String renderConfirmedLogSummary(AppOverviewSnapshot overview) {
        if (overview == null || overview.confirmedLogCount() == 0) {
            return "Confirmed logs: 0\nReview queue: 0";
        }
        ConfirmedQsoLog latest = overview.latestConfirmedLog();
        String latestLoggedAt = LogDisplayFormatter.formatUtcDateTime(
                latest.qsoDateUtc(),
                latest.timeOnUtc()
        );
        return "Confirmed logs: " + overview.confirmedLogCount()
                + "\nReview queue: " + overview.manualReviewLogCount()
                + "\nLatest: " + latest.remoteCallsign()
                + "\nLogged at: " + latestLoggedAt
                + " / "
                + safeValue(latest.rstSent())
                + " / "
                + safeValue(latest.rstRcvd())
                + (isEditingConfirmedLog() && currentConfirmedLog != null
                ? "\nEditing: #" + currentConfirmedLog.id() + " " + safeValue(currentConfirmedLog.remoteCallsign())
                : "");
    }

    private String renderConfirmedLogList() {
        List<ConfirmedQsoLog> logs = localLogRepository.loadConfirmedLogs();
        if (logs.isEmpty()) {
            return "No confirmed logs yet.";
        }

        ArrayList<String> lines = new ArrayList<>();
        int start = Math.max(0, logs.size() - 10);
        for (int i = logs.size() - 1; i >= start; i--) {
            ConfirmedQsoLog log = logs.get(i);
            String prefix = log.needManualReview() ? "[Review] " : "";
            lines.add(prefix + LogDisplayFormatter.formatUtcDateTime(log.qsoDateUtc(), log.timeOnUtc())
                    + "  "
                    + safeValue(log.remoteCallsign())
                    + "  "
                    + safeValue(log.rstSent())
                    + "/"
                    + safeValue(log.rstRcvd()));
        }
        return String.join("\n", lines);
    }

    private boolean hasEditorContent() {
        return normalizedEditorValue(binding.stationCallsignEditText.getText()) != null
                || normalizedEditorValue(binding.remoteCallsignEditText.getText()) != null
                || normalizedEditorValue(binding.rstSentEditText.getText()) != null
                || normalizedEditorValue(binding.rstRcvdEditText.getText()) != null
                || normalizedEditorValue(binding.nameEditText.getText()) != null
                || normalizedEditorValue(binding.qthEditText.getText()) != null;
    }

    private boolean hasRemoteCallsign() {
        return normalizedEditorValue(binding.remoteCallsignEditText.getText()) != null;
    }

    private String resolveConfirmButtonText(QsoDraftSnapshot snapshot) {
        if (isEditingConfirmedLog()) {
            if (snapshot != null && snapshot.needManualReview()) {
                return "Save To Log (Keep Review)";
            }
            return "Save To Log";
        }
        if (snapshot != null && snapshot.needManualReview()) {
            return "Confirm With Review Flag";
        }
        return "Confirm Log";
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

    private String resolveDraftSourceLabel() {
        if (editorDirty) {
            return "unsaved editor preview";
        }
        if (isEditingConfirmedLog()) {
            return "confirmed log #" + currentConfirmedLogId;
        }
        return "stored active draft";
    }

    private boolean hasDraftContent(QsoDraftSnapshot snapshot) {
        return snapshot.stationCallsignUsed() != null
                || snapshot.remoteCallsignCandidate() != null
                || snapshot.rstSentCandidate() != null
                || snapshot.rstRcvdCandidate() != null
                || snapshot.nameCandidate() != null
                || snapshot.qthCandidate() != null;
    }

    private String normalizedEditorValue(Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private String currentValue(String value) {
        return value;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeValue(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
