package org.bi9clt.cwcn.ui.operate;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatter;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.databinding.ActivityOperateBinding;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;

public final class OperateActivity extends AppCompatActivity {
    private static final long LIVE_RX_REFRESH_INTERVAL_MS = 700L;

    private ActivityOperateBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private RxSessionStore rxSessionStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            mainHandler.postDelayed(this, LIVE_RX_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOperateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        rxSessionStore = new RxSessionStore(this);
        binding.versionText.setText("Operate " + BuildConfig.VERSION_NAME);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.postDelayed(liveRefreshRunnable, LIVE_RX_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(liveRefreshRunnable);
        super.onPause();
    }

    private void setupActions() {
        binding.backHomeButton.setOnClickListener(view -> finish());
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.openSettingsButton.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void refreshUi() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        RxSessionSnapshot rxSessionSnapshot = rxSessionStore.load();
        binding.liveRxStatusText.setText(renderLiveRxStatus(rxSessionSnapshot));
        binding.liveRxTextText.setText(renderLiveRxText(rxSessionSnapshot));
        binding.operatingStatusText.setText(renderOperatingStatus(overview));
        binding.rigStatusText.setText(renderRigStatus());
        binding.nextActionText.setText(renderNextAction(overview));
    }

    private String renderLiveRxStatus(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "No shared RX session yet.\nCurrent code still produces live RX mainly from Debug. This panel is the first bridge toward a formal operating RX screen.";
        }
        return "Source: " + safeValue(snapshot.sourceLabel())
                + "\nCapture: " + safeValue(snapshot.captureState())
                + " / active=" + yesNo(snapshot.captureActive())
                + "\nTone: pref=" + snapshot.preferredToneFrequencyHz()
                + "Hz, tracked=" + snapshot.targetToneFrequencyHz()
                + "Hz, effective=" + snapshot.effectiveToneFrequencyHz()
                + "Hz"
                + "\nWPM: " + snapshot.estimatedWpm()
                + "\nPhase: " + safeValue(snapshot.phaseDisplayName())
                + "\nRemote: " + safeValue(snapshot.remoteCallsign())
                + "\nReady: " + yesNo(snapshot.readyForDraftConfirmation())
                + " / Review: " + yesNo(snapshot.needManualReview())
                + "\nUpdated: " + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderLiveRxText(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "RAW: -\nNormalized: -";
        }
        return "RAW: " + safeValue(snapshot.rawText())
                + "\nNormalized: " + safeValue(snapshot.normalizedText());
    }

    private String renderOperatingStatus(AppOverviewSnapshot overview) {
        if (overview == null || overview.activeDraft() == null) {
            return "No active operating draft yet.\nStart with Connect Radio, then begin a live RX session from the normal operating path.";
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
            return "1. Rig path is pinned.\n2. Bring up a live RX session and watch the shared RAW/Normalized panel here.\n3. Use Settings when you need configuration or advanced tools.";
        }
        return "Rig path exists and RX has draft context.\nUse this page to track live RAW/Normalized text and draft readiness, then visit Settings for configuration or advanced tools.";
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String renderAge(long updatedAtEpochMs) {
        if (updatedAtEpochMs <= 0L) {
            return "-";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - updatedAtEpochMs);
        long ageSeconds = ageMs / 1000L;
        if (ageSeconds < 60L) {
            return ageSeconds + "s ago";
        }
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) {
            return ageMinutes + "m ago";
        }
        long ageHours = ageMinutes / 60L;
        return ageHours + "h ago";
    }
}
