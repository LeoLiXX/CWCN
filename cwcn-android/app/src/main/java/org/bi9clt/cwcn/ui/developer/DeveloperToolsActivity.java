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
        binding.versionText.setText("开发工具 " + BuildConfig.VERSION_NAME);
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
                ? "开发者模式已开启。\n当前优先保留 RX 实验台、TX 开发控制台和电台实验台，其他杂项不再放进正式路径。"
                : "开发者模式已关闭。\n只有在需要底层收发排查、链路实验或协议验证时才建议开启。");
        binding.toggleDeveloperModeButton.setText(enabled
                ? "关闭开发者模式"
                : "开启开发者模式");
        int visibility = enabled ? View.VISIBLE : View.GONE;
        binding.toolsPanel.setVisibility(visibility);
        binding.toolsHintText.setText(enabled
                ? "RX 实验台聚焦接收链路与解码问题，TX 控制台用于发射验证，电台实验台用于 CAT / 键控实验。"
                : "开发工具会在开启开发者模式后显示。");
    }
}
