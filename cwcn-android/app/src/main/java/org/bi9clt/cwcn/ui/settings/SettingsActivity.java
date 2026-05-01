package org.bi9clt.cwcn.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.databinding.ActivitySettingsBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;

public final class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private DeveloperModeStore developerModeStore;
    private RigSelectionStore rigSelectionStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        developerModeStore = new DeveloperModeStore(this);
        rigSelectionStore = new RigSelectionStore(this);
        binding.versionText.setText("Settings " + BuildConfig.VERSION_NAME);
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        binding.stationProfileText.setText(renderStationProfileText());
        binding.rigSettingsText.setText(renderRigSettingsText());
        binding.developerModeStatusText.setText(renderDeveloperModeStatus(developerModeEnabled));
        binding.toggleDeveloperModeButton.setText(developerModeEnabled
                ? "Disable Developer Mode"
                : "Enable Developer Mode");
        binding.developerToolsPanel.setVisibility(developerModeEnabled ? View.VISIBLE : View.GONE);
        binding.developerToolsNoteText.setText(developerModeEnabled
                ? "Developer mode is on. RX Debug, TX Console, and rig bench tools stay here instead of the normal user path."
                : "Developer mode is off. Normal users should not need these tools during everyday operation.");
    }

    private String renderStationProfileText() {
        return "This page is the formal home for configuration."
                + "\nUpcoming buckets:"
                + "\n- My callsign / station identity"
                + "\n- CW templates and quick replies"
                + "\n- operating preferences"
                + "\n- export / log behavior";
    }

    private String renderRigSettingsText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "No rig path is pinned yet."
                    + "\nOpen Connect Radio to choose the main transport and save the preferred rig family.";
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigProfileConfigurationFormatter.renderCompactSummary(profile, settings)
                + "\nConnect Radio remains the main place for transport-specific setup.";
    }

    private String renderDeveloperModeStatus(boolean enabled) {
        return enabled
                ? "Developer mode is enabled."
                + "\nEngineering-only tools are available below, but they stay out of Home and the normal operating path."
                : "Developer mode is disabled."
                + "\nHome and Operate stay focused on normal radio use, while engineering tools remain hidden here.";
    }
}
