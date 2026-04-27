package org.bi9clt.cwcn.ui.home;

import android.content.Intent;
import android.os.Bundle;

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
import org.bi9clt.cwcn.databinding.ActivityHomeBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
import org.bi9clt.cwcn.ui.operate.OperateActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;

public final class HomeActivity extends AppCompatActivity {
    private ActivityHomeBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private DeveloperModeStore developerModeStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        developerModeStore = new DeveloperModeStore(this);
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
        binding.openOperateButton.setOnClickListener(view ->
                startActivity(new Intent(this, OperateActivity.class)));
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        binding.draftSummaryText.setText(renderDraftSummary(overview));
        binding.rigSummaryText.setText(renderRigSummary());
        binding.developerModeStatusText.setText(renderDeveloperModeStatus(developerModeEnabled));
        binding.toggleDeveloperModeButton.setText(developerModeEnabled
                ? "Disable Developer Mode"
                : "Enable Developer Mode");
        binding.developerToolsPanel.setVisibility(developerModeEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.developerToolsNoteText.setText(developerModeEnabled
                ? "Developer mode is on. Developer Tools now collects RX Debug, TX Console, and rig bench entrances in one place."
                : "Developer mode is off. Turn it on only when you need bench TX, raw debug views, or protocol-level rig testing.");
        binding.navigationNoteText.setText(renderNavigationNote(developerModeEnabled));
    }

    private String renderDraftSummary(AppOverviewSnapshot overview) {
        if (overview == null) {
            return "Active draft: none\nFinish rig setup first, then validate RX decoding from the operating path.";
        }
        QsoDraftSnapshot draft = overview.activeDraft();
        if (draft == null) {
            return "Active draft: none\nFinish rig setup first, then validate RX decoding from the operating path.";
        }

        String callsign = safeValue(draft.remoteCallsignCandidate());
        String rst = safeValue(draft.rstSentCandidate()) + "/" + safeValue(draft.rstRcvdCandidate());
        return "Active draft: "
                + callsign
                + "\nPhase: " + draft.phase().displayName()
                + "\nRST: " + rst
                + "\nReady: " + yesNo(draft.readyForDraftConfirmation())
                + "\nReview needed: " + yesNo(draft.needManualReview())
                + "\nNext: " + QsoWorkflowSummaryFormatter.renderDraftNextStep(draft, false);
    }

    private String safeValue(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String renderRigSummary() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "Rig path: none pinned\nOpen Rig Setup to choose the primary rig family for future operating screens.";
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigProfileConfigurationFormatter.renderCompactSummary(profile, settings)
                + "\nNext: Open Rig Setup to tune transport-specific defaults and hints.";
    }

    private String renderDeveloperModeStatus(boolean enabled) {
        return enabled
                ? "Developer mode is enabled.\nEngineering tools such as TX Console, protocol probes, and raw debug entry points are visible."
                : "Developer mode is disabled.\nHome stays focused on normal operating flow, while engineering tools are folded away.";
    }

    private String renderNavigationNote(boolean developerModeEnabled) {
        return developerModeEnabled
                ? "Rig Setup is the formal rig entry. Operate stays focused on the daily path, and Developer Tools holds the engineering entrances."
                : "Rig Setup is the formal rig entry. Operate stays focused on the daily path, and engineering tools are hidden behind developer mode.";
    }
}
