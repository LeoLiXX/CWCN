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
import org.bi9clt.cwcn.databinding.ActivityQsoEditorBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class QsoEditorActivity extends AppCompatActivity {
    private ActivityQsoEditorBinding binding;
    private LocalLogRepository localLogRepository;
    private QsoDraftSnapshot currentDraftSnapshot;
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
        reloadFromRepository();
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
            reloadFromRepository();
            editorStatusMessage = "Reloaded draft from local storage.";
            refreshUi();
        });
        binding.saveDraftButton.setOnClickListener(view -> saveDraft());
        binding.confirmLogButton.setOnClickListener(view -> confirmLog());
        binding.exportAdifButton.setOnClickListener(view -> exportAdif());
    }

    private void reloadFromRepository() {
        currentDraftSnapshot = localLogRepository.loadDraft();
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
        actionStatusMessage = "Confirmed log: " + log.remoteCallsign();
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
        binding.draftMetaText.setText(renderDraftMeta());
        binding.draftEvidenceText.setText(renderDraftEvidence());
        binding.editorStatusText.setText(renderEditorStatus());
        binding.actionStatusText.setText(renderActionStatus());
        binding.confirmedLogSummaryText.setText(renderConfirmedLogSummary(overview));
        binding.confirmedLogListText.setText(renderConfirmedLogList());
        binding.saveDraftButton.setEnabled(editorDirty || hasEditorContent());
        binding.confirmLogButton.setEnabled(hasRemoteCallsign());
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

    private String renderDraftMeta() {
        if (currentDraftSnapshot == null) {
            return "No active draft stored yet.";
        }

        String updatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(currentDraftSnapshot.updatedAtEpochMs()));
        return "Phase: " + currentDraftSnapshot.phase().displayName()
                + "\nReady: " + yesNo(currentDraftSnapshot.readyForDraftConfirmation())
                + "\nManual review: " + yesNo(currentDraftSnapshot.needManualReview())
                + "\nUpdated: " + updatedAt;
    }

    private String renderDraftEvidence() {
        if (currentDraftSnapshot == null) {
            return "No normalized text / hints available yet.";
        }

        String hints = currentDraftSnapshot.hints().isEmpty()
                ? "(none)"
                : String.join(" / ", currentDraftSnapshot.hints());
        String normalizedText = currentDraftSnapshot.normalizedText().isEmpty()
                ? "(none)"
                : currentDraftSnapshot.normalizedText();
        String lastEventSummary = currentDraftSnapshot.lastEvent() == null
                ? "(none)"
                : currentDraftSnapshot.lastEvent().summary();
        return "Normalized: " + normalizedText
                + "\nHints: " + hints
                + "\nLast event: " + lastEventSummary;
    }

    private String renderEditorStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append(editorDirty ? "Editor has unsaved changes." : "Editor is in sync with stored draft.");
        if (!editorStatusMessage.isEmpty()) {
            builder.append("\nLast edit action: ").append(editorStatusMessage);
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
            return "Confirmed logs: 0";
        }
        ConfirmedQsoLog latest = overview.latestConfirmedLog();
        String latestLoggedAt = LogDisplayFormatter.formatUtcDateTime(
                latest.qsoDateUtc(),
                latest.timeOnUtc()
        );
        return "Confirmed logs: " + overview.confirmedLogCount()
                + "\nLatest: " + latest.remoteCallsign()
                + "\nLogged at: " + latestLoggedAt
                + " / "
                + safeValue(latest.rstSent())
                + " / "
                + safeValue(latest.rstRcvd());
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
            lines.add(LogDisplayFormatter.formatUtcDateTime(log.qsoDateUtc(), log.timeOnUtc())
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
