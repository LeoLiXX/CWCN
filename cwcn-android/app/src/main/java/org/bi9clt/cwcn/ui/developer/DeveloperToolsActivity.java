package org.bi9clt.cwcn.ui.developer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.databinding.ActivityDeveloperToolsBinding;
import org.bi9clt.cwcn.ui.debug.InputDebugActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;
import org.bi9clt.cwcn.ui.tx.TxActivity;

public final class DeveloperToolsActivity extends AppCompatActivity {
    private ActivityDeveloperToolsBinding binding;
    private DeveloperModeStore developerModeStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeveloperToolsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        developerModeStore = new DeveloperModeStore(this);
        binding.versionText.setText("Developer Tools " + BuildConfig.VERSION_NAME);
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
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.openRxDebugButton.setOnClickListener(view ->
                startActivity(new Intent(this, InputDebugActivity.class)));
        binding.openTxConsoleButton.setOnClickListener(view ->
                startActivity(new Intent(this, TxActivity.class)));
        binding.openRigBenchButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, RigSetupActivity.class);
            intent.putExtra(RigSetupActivity.EXTRA_OPEN_DEVELOPER_LABS, true);
            startActivity(intent);
        });
    }

    private void refreshUi() {
        boolean enabled = developerModeStore.isEnabled();
        binding.developerModeStatusText.setText(enabled
                ? "Developer mode is enabled.\nRX Debug, TX Console, and rig bench tools are available from this screen."
                : "Developer mode is disabled.\nEnable it only when you need protocol probes, bench TX, or low-level RX inspection.");
        binding.toggleDeveloperModeButton.setText(enabled
                ? "Disable Developer Mode"
                : "Enable Developer Mode");
        int visibility = enabled ? View.VISIBLE : View.GONE;
        binding.toolsPanel.setVisibility(visibility);
        binding.toolsHintText.setText(enabled
                ? "Use RX Debug for decode analysis, TX Console for send-side benching, and Rig Bench for CAT/keying experiments."
                : "Developer tools stay hidden until developer mode is enabled.");
    }
}
