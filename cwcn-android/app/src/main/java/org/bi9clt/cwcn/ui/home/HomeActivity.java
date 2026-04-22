package org.bi9clt.cwcn.ui.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.log.LogDisplayFormatter;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.databinding.ActivityHomeBinding;
import org.bi9clt.cwcn.ui.debug.InputDebugActivity;
import org.bi9clt.cwcn.ui.qso.QsoEditorActivity;
import org.bi9clt.cwcn.ui.qso.QsoLogbookActivity;

public final class HomeActivity extends AppCompatActivity {
    private ActivityHomeBinding binding;
    private LocalLogRepository localLogRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        binding.versionText.setText("Home " + BuildConfig.VERSION_NAME);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupActions() {
        binding.openQsoEditorButton.setOnClickListener(view ->
                startActivity(new Intent(this, QsoEditorActivity.class)));
        binding.openLogbookButton.setOnClickListener(view ->
                startActivity(new Intent(this, QsoLogbookActivity.class)));
        binding.openDebugButton.setOnClickListener(view ->
                startActivity(new Intent(this, InputDebugActivity.class)));
    }

    private void refreshUi() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        binding.draftSummaryText.setText(renderDraftSummary(overview));
        binding.logSummaryText.setText(renderLogSummary(overview));
    }

    private String renderDraftSummary(AppOverviewSnapshot overview) {
        if (overview == null) {
            return "Active draft: none\nOpen QSO Editor to start or continue a draft.";
        }
        QsoDraftSnapshot draft = overview.activeDraft();
        if (draft == null) {
            return "Active draft: none\nOpen QSO Editor to start or continue a draft.";
        }

        String callsign = safeValue(draft.remoteCallsignCandidate());
        String rst = safeValue(draft.rstSentCandidate()) + "/" + safeValue(draft.rstRcvdCandidate());
        return "Active draft: "
                + callsign
                + "\nPhase: " + draft.phase().displayName()
                + "\nRST: " + rst
                + "\nReady: " + yesNo(draft.readyForDraftConfirmation())
                + "\nReview needed: " + yesNo(draft.needManualReview());
    }

    private String renderLogSummary(AppOverviewSnapshot overview) {
        if (overview == null || overview.confirmedLogCount() == 0) {
            return "Confirmed logs: 0\nLogbook is empty.";
        }

        ConfirmedQsoLog latest = overview.latestConfirmedLog();
        return "Confirmed logs: " + overview.confirmedLogCount()
                + "\nLatest: " + safeValue(latest.remoteCallsign())
                + "\nLogged at: " + LogDisplayFormatter.formatUtcDateTime(latest.qsoDateUtc(), latest.timeOnUtc())
                + "\nReview flag: " + yesNo(latest.needManualReview());
    }

    private String safeValue(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
