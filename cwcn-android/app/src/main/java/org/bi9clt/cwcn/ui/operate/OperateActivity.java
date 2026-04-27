package org.bi9clt.cwcn.ui.operate;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.databinding.ActivityOperateBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;

public final class OperateActivity extends AppCompatActivity {
    private ActivityOperateBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private DeveloperModeStore developerModeStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOperateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        developerModeStore = new DeveloperModeStore(this);
        binding.versionText.setText("Operate " + BuildConfig.VERSION_NAME);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupActions() {
        binding.backHomeButton.setOnClickListener(view ->
                finish());
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
    }

    private void refreshUi() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        boolean developerModeEnabled = developerModeStore.isEnabled();
        binding.operatingStatusText.setText(renderOperatingStatus(overview));
        binding.rigStatusText.setText(renderRigStatus());
        binding.nextActionText.setText(renderNextAction(overview));
        binding.developerSupportPanel.setVisibility(developerModeEnabled ? View.VISIBLE : View.GONE);
        binding.developerSupportText.setText(developerModeEnabled
                ? "Developer mode is enabled. Bench tools are separated into Developer Tools so this screen can stay focused on normal use."
                : "Developer mode is disabled. This screen stays focused on the normal user path.");
    }

    private String renderOperatingStatus(AppOverviewSnapshot overview) {
        if (overview == null || overview.activeDraft() == null) {
            return "No active operating draft yet.\nStart with Connect Radio, then use RX Debug to verify tone lock and decode stability.";
        }
        QsoDraftSnapshot draft = overview.activeDraft();
        return "Active draft: "
                + safeValue(draft.remoteCallsignCandidate())
                + "\nPhase: " + draft.phase().displayName()
                + "\nReady: " + yesNo(draft.readyForDraftConfirmation())
                + "\nReview needed: " + yesNo(draft.needManualReview())
                + "\nNext: " + QsoWorkflowSummaryFormatter.renderDraftNextStep(draft, false);
    }

    private String renderRigStatus() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "No rig path is pinned yet.\nUse Connect Radio first so operating screens know which connection path to prefer.";
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigProfileConfigurationFormatter.renderCompactSummary(profile, settings)
                + "\nReady for user flow: saved rig path is available.";
    }

    private String renderNextAction(AppOverviewSnapshot overview) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "1. Connect Radio.\n2. Save your preferred rig path.\n3. Return here and continue with RX validation.";
        }
        if (overview == null || overview.activeDraft() == null) {
            return "1. Rig path is pinned.\n2. Open Debug Tools in developer mode when you need deep RX inspection.\n3. Return here to keep checking operating readiness.";
        }
        return "Rig path exists and RX has draft context.\nKeep testing tone acquisition, spacing, and decode stability before bringing later workflow features back.";
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
