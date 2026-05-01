package org.bi9clt.cwcn.ui.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.databinding.ActivityHomeBinding;
import org.bi9clt.cwcn.ui.operate.OperateActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;

public final class HomeActivity extends AppCompatActivity {
    private ActivityHomeBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
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
        binding.openSettingsButton.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void refreshUi() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        binding.draftSummaryText.setText(renderDraftSummary(overview));
        binding.rigSummaryText.setText(renderRigSummary());
        binding.navigationNoteText.setText(renderNavigationNote());
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
            return "Rig path: none pinned\nOpen Connect Radio to choose the primary rig family for future operating screens.";
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigProfileConfigurationFormatter.renderCompactSummary(profile, settings)
                + "\nNext: Open Connect Radio to tune transport-specific defaults and hints.";
    }

    private String renderNavigationNote() {
        return "Rig Setup is the formal rig entry. Operate is the main user path, and advanced/developer tools now live under Settings.";
    }
}
