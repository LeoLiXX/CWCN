package org.bi9clt.cwcn.ui.qso;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.ConfirmedLogQuery;
import org.bi9clt.cwcn.core.log.LogDisplayFormatter;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatter;
import org.bi9clt.cwcn.databinding.ActivityQsoLogbookBinding;

import java.util.List;
import java.util.Locale;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public final class QsoLogbookActivity extends AppCompatActivity {
    private static final SimpleDateFormat QSO_DATE_FILTER_FORMAT = createQsoDateFilterFormat();

    private enum SortOption {
        NEWEST_FIRST("Newest first", ConfirmedLogQuery.SortOrder.CONFIRMED_AT_DESC),
        OLDEST_FIRST("Oldest first", ConfirmedLogQuery.SortOrder.CONFIRMED_AT_ASC),
        CALLSIGN_A_Z("Callsign A-Z", ConfirmedLogQuery.SortOrder.CALLSIGN_ASC),
        CALLSIGN_Z_A("Callsign Z-A", ConfirmedLogQuery.SortOrder.CALLSIGN_DESC);

        private final String label;
        private final ConfirmedLogQuery.SortOrder sortOrder;

        SortOption(String label, ConfirmedLogQuery.SortOrder sortOrder) {
            this.label = label;
            this.sortOrder = sortOrder;
        }

        public ConfirmedLogQuery.SortOrder sortOrder() {
            return sortOrder;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private ActivityQsoLogbookBinding binding;
    private LocalLogRepository localLogRepository;
    private List<ConfirmedQsoLog> confirmedLogs;
    private int selectedLogIndex = -1;
    private String actionStatusMessage = "";
    private String activeCallsignFilter;
    private boolean activeReviewOnly;
    private String activeFromQsoDateUtc;
    private String activeToQsoDateUtc;
    private SortOption activeSortOption = SortOption.NEWEST_FIRST;
    private int totalConfirmedLogCount;
    private int reviewQueueCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQsoLogbookBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        setupFilterControls();
        setupActions();
        reloadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadLogs();
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.reloadButton.setOnClickListener(view -> {
            actionStatusMessage = "Reloaded confirmed logs.";
            reloadLogs();
        });
        binding.applyFilterButton.setOnClickListener(view -> applyFiltersFromUi());
        binding.clearFilterButton.setOnClickListener(view -> clearFilters());
        binding.loadIntoEditorButton.setOnClickListener(view -> loadSelectedLogIntoEditor());
        binding.toggleReviewButton.setOnClickListener(view -> toggleSelectedLogReviewFlag());
        binding.deleteLogButton.setOnClickListener(view -> deleteSelectedLog());
    }

    private void setupFilterControls() {
        ArrayAdapter<SortOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SortOption.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.sortOrderSpinner.setAdapter(adapter);
        binding.sortOrderSpinner.setSelection(0);
    }

    private void reloadLogs() {
        confirmedLogs = localLogRepository.queryConfirmedLogs(buildQuery());
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        totalConfirmedLogCount = overview.confirmedLogCount();
        reviewQueueCount = overview.manualReviewLogCount();
        if (confirmedLogs.isEmpty()) {
            selectedLogIndex = -1;
        } else if (selectedLogIndex < 0 || selectedLogIndex >= confirmedLogs.size()) {
            selectedLogIndex = 0;
        }
        rebuildLogButtons();
        refreshUi();
    }

    private void refreshUi() {
        binding.logSummaryText.setText(renderLogSummary());
        binding.selectedLogText.setText(renderSelectedLogText());
        binding.selectedLogRawText.setText(renderSelectedLogRawText());
        binding.selectedLogReviewText.setText(renderSelectedLogReviewText());
        binding.actionStatusText.setText(renderActionStatus());
        boolean hasSelection = selectedLogIndex >= 0 && selectedLogIndex < confirmedLogs.size();
        binding.loadIntoEditorButton.setEnabled(hasSelection);
        binding.toggleReviewButton.setEnabled(hasSelection);
        binding.deleteLogButton.setEnabled(hasSelection);
        binding.loadIntoEditorButton.setText(selectedLog() != null && selectedLog().needManualReview()
                ? "Review In Editor"
                : "Re-edit");
        binding.toggleReviewButton.setText(selectedLog() != null && selectedLog().needManualReview()
                ? "Clear Review Flag"
                : "Mark Review");
    }

    private void rebuildLogButtons() {
        binding.logButtonContainer.removeAllViews();
        if (confirmedLogs.isEmpty()) {
            AppCompatButton button = createLogButton("(no confirmed logs)");
            button.setEnabled(false);
            binding.logButtonContainer.addView(button);
            return;
        }

        for (int i = confirmedLogs.size() - 1; i >= 0; i--) {
            int index = i;
            ConfirmedQsoLog log = confirmedLogs.get(i);
            AppCompatButton button = createLogButton(buildButtonLabel(log, i == selectedLogIndex));
            button.setOnClickListener(view -> {
                selectedLogIndex = index;
                actionStatusMessage = "Selected log: " + safeValue(log.remoteCallsign());
                rebuildLogButtons();
                refreshUi();
            });
            binding.logButtonContainer.addView(button);
        }
    }

    private AppCompatButton createLogButton(String text) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(text);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.bottomMargin = dpToPx(8);
        button.setLayoutParams(layoutParams);
        return button;
    }

    private void loadSelectedLogIntoEditor() {
        if (selectedLogIndex < 0 || selectedLogIndex >= confirmedLogs.size()) {
            Toast.makeText(this, "Select a log first.", Toast.LENGTH_SHORT).show();
            return;
        }

        ConfirmedQsoLog log = confirmedLogs.get(selectedLogIndex);
        actionStatusMessage = "Opened log in editor: " + safeValue(log.remoteCallsign());
        Toast.makeText(this, "Opened selected log in editor.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, QsoEditorActivity.class);
        intent.putExtra(QsoEditorActivity.EXTRA_CONFIRMED_LOG_ID, log.id());
        startActivity(intent);
    }

    private void toggleSelectedLogReviewFlag() {
        ConfirmedQsoLog selectedLog = selectedLog();
        if (selectedLog == null) {
            Toast.makeText(this, "Select a log first.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean updatedReviewFlag = !selectedLog.needManualReview();
        boolean updated = localLogRepository.updateConfirmedLog(
                selectedLog.id(),
                selectedLog.withNeedManualReview(updatedReviewFlag)
        );
        if (!updated) {
            Toast.makeText(this, "Unable to update selected log.", Toast.LENGTH_SHORT).show();
            return;
        }

        actionStatusMessage = (updatedReviewFlag ? "Marked" : "Cleared")
                + " manual review for "
                + safeValue(selectedLog.remoteCallsign());
        reloadLogs();
    }

    private void deleteSelectedLog() {
        ConfirmedQsoLog selectedLog = selectedLog();
        if (selectedLog == null) {
            Toast.makeText(this, "Select a log first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String callsign = safeValue(selectedLog.remoteCallsign());
        new AlertDialog.Builder(this)
                .setTitle("Delete confirmed log?")
                .setMessage("Delete log for " + callsign + "?\nThis cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> performDeleteSelectedLog(callsign))
                .show();
    }

    private void performDeleteSelectedLog(String callsign) {
        ConfirmedQsoLog selectedLog = selectedLog();
        boolean deleted = selectedLog != null && localLogRepository.deleteConfirmedLog(selectedLog.id());
        if (!deleted) {
            Toast.makeText(this, "Unable to delete selected log.", Toast.LENGTH_SHORT).show();
            return;
        }

        actionStatusMessage = "Deleted log: " + callsign;
        Toast.makeText(this, "Selected log deleted.", Toast.LENGTH_SHORT).show();
        if (confirmedLogs != null && selectedLogIndex >= confirmedLogs.size() - 1) {
            selectedLogIndex = Math.max(0, confirmedLogs.size() - 2);
        }
        reloadLogs();
    }

    private String renderLogSummary() {
        if (confirmedLogs.isEmpty()) {
            return "Showing: 0 of " + totalConfirmedLogCount
                    + "\nReview queue: " + reviewQueueCount
                    + "\nFilter: " + renderFilterSummary();
        }
        return "Showing: " + confirmedLogs.size() + " of " + totalConfirmedLogCount
                + "\nReview queue: " + reviewQueueCount
                + "\nSelected: " + safeValue(selectedLog() == null ? null : selectedLog().remoteCallsign())
                + "\nFilter: " + renderFilterSummary();
    }

    private String renderSelectedLogText() {
        ConfirmedQsoLog selectedLog = selectedLog();
        if (selectedLog == null) {
            return "No confirmed log selected.";
        }
        String loggedAt = LogDisplayFormatter.formatUtcDateTime(
                selectedLog.qsoDateUtc(),
                selectedLog.timeOnUtc()
        );
        return "Call: " + safeValue(selectedLog.remoteCallsign())
                + "\nStation: " + safeValue(selectedLog.stationCallsign())
                + "\nLogged at: " + loggedAt
                + "\nRST: " + safeValue(selectedLog.rstSent()) + " / " + safeValue(selectedLog.rstRcvd())
                + "\nName: " + safeValue(selectedLog.name())
                + "\nQTH: " + safeValue(selectedLog.qth())
                + "\nPhase: " + safeValue(selectedLog.phase())
                + "\nManual review: " + (selectedLog.needManualReview() ? "yes" : "no")
                + "\nConfirmed at: " + LogDisplayFormatter.formatEpochMillis(selectedLog.confirmedAtEpochMs());
    }

    private String renderSelectedLogRawText() {
        ConfirmedQsoLog selectedLog = selectedLog();
        if (selectedLog == null) {
            return "No normalized text available.";
        }
        return "Normalized text: " + safeValue(selectedLog.normalizedText());
    }

    private String renderSelectedLogReviewText() {
        ConfirmedQsoLog selectedLog = selectedLog();
        return QsoWorkflowSummaryFormatter.renderConfirmedLogReviewSummary(selectedLog)
                + "\nNext step: " + QsoWorkflowSummaryFormatter.renderConfirmedLogNextStep(selectedLog);
    }

    private String renderActionStatus() {
        if (actionStatusMessage.isEmpty()) {
            return "No logbook action yet.";
        }
        return actionStatusMessage;
    }

    private void applyFiltersFromUi() {
        String fromDate = normalizeDateFilter(binding.fromDateFilterEditText.getText());
        if (binding.fromDateFilterEditText.getText() != null && fromDate == null
                && !binding.fromDateFilterEditText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "From date must be YYYYMMDD.", Toast.LENGTH_SHORT).show();
            return;
        }

        String toDate = normalizeDateFilter(binding.toDateFilterEditText.getText());
        if (binding.toDateFilterEditText.getText() != null && toDate == null
                && !binding.toDateFilterEditText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "To date must be YYYYMMDD.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fromDate != null && toDate != null && fromDate.compareTo(toDate) > 0) {
            Toast.makeText(this, "From date must be earlier than or equal to To date.", Toast.LENGTH_SHORT).show();
            return;
        }

        activeCallsignFilter = normalizeFilter(binding.callsignFilterEditText.getText());
        activeReviewOnly = binding.reviewOnlyCheckBox.isChecked();
        activeFromQsoDateUtc = fromDate;
        activeToQsoDateUtc = toDate;
        Object selectedItem = binding.sortOrderSpinner.getSelectedItem();
        if (selectedItem instanceof SortOption) {
            activeSortOption = (SortOption) selectedItem;
        }
        actionStatusMessage = "Applied log filters.";
        selectedLogIndex = -1;
        reloadLogs();
    }

    private void clearFilters() {
        activeCallsignFilter = null;
        activeReviewOnly = false;
        activeFromQsoDateUtc = null;
        activeToQsoDateUtc = null;
        activeSortOption = SortOption.NEWEST_FIRST;
        binding.callsignFilterEditText.setText("");
        binding.fromDateFilterEditText.setText("");
        binding.toDateFilterEditText.setText("");
        binding.reviewOnlyCheckBox.setChecked(false);
        binding.sortOrderSpinner.setSelection(0);
        actionStatusMessage = "Cleared log filters.";
        selectedLogIndex = -1;
        reloadLogs();
    }

    private ConfirmedLogQuery buildQuery() {
        return new ConfirmedLogQuery(
                activeCallsignFilter,
                activeReviewOnly ? Boolean.TRUE : null,
                activeFromQsoDateUtc,
                activeToQsoDateUtc,
                activeSortOption.sortOrder()
        );
    }

    private String renderFilterSummary() {
        String callsign = activeCallsignFilter == null ? "(none)" : activeCallsignFilter.toUpperCase(Locale.US);
        String review = activeReviewOnly ? "review-only" : "all";
        String from = activeFromQsoDateUtc == null ? "(none)" : activeFromQsoDateUtc;
        String to = activeToQsoDateUtc == null ? "(none)" : activeToQsoDateUtc;
        return "callsign=" + callsign
                + ", review=" + review
                + ", from=" + from
                + ", to=" + to
                + ", sort=" + activeSortOption;
    }

    private ConfirmedQsoLog selectedLog() {
        if (confirmedLogs == null || selectedLogIndex < 0 || selectedLogIndex >= confirmedLogs.size()) {
            return null;
        }
        return confirmedLogs.get(selectedLogIndex);
    }

    private String buildButtonLabel(ConfirmedQsoLog log, boolean selected) {
        StringBuilder builder = new StringBuilder();
        if (selected) {
            builder.append("[Selected] ");
        }
        if (log.needManualReview()) {
            builder.append("[Review] ");
        }
        builder.append(LogDisplayFormatter.formatUtcDateTime(log.qsoDateUtc(), log.timeOnUtc()))
                .append("  ")
                .append(safeValue(log.remoteCallsign()));
        return builder.toString();
    }

    private String safeValue(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String normalizeFilter(Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private String normalizeDateFilter(Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }

        String digitsOnly = raw.replaceAll("[^0-9]", "");
        if (digitsOnly.length() != 8) {
            return null;
        }

        synchronized (QSO_DATE_FILTER_FORMAT) {
            try {
                QSO_DATE_FILTER_FORMAT.parse(digitsOnly);
                return digitsOnly;
            } catch (ParseException exception) {
                return null;
            }
        }
    }

    private static SimpleDateFormat createQsoDateFilterFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setLenient(false);
        return format;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
