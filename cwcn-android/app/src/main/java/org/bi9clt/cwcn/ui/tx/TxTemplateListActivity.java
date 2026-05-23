package org.bi9clt.cwcn.ui.tx;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.TxTemplateEntry;
import org.bi9clt.cwcn.core.app.TxTemplateStore;
import org.bi9clt.cwcn.databinding.ActivityTxTemplateListBinding;
import org.bi9clt.cwcn.ui.navigation.FormalBottomNavStyler;
import org.bi9clt.cwcn.ui.operate.OperateActivity;
import org.bi9clt.cwcn.ui.qso.QsoLogbookActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;
import org.bi9clt.cwcn.ui.spectrum.SpectrumActivity;

import java.util.Collections;
import java.util.List;

public final class TxTemplateListActivity extends AppCompatActivity implements TxTemplateListAdapter.Callbacks {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;

    private ActivityTxTemplateListBinding binding;
    private TxTemplateStore txTemplateStore;
    private TxTemplateListAdapter adapter;
    private List<TxTemplateEntry> displayedTemplates = Collections.emptyList();
    private String statusMessage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxTemplateListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        txTemplateStore = new TxTemplateStore(this);
        adapter = new TxTemplateListAdapter(this);
        setupRecyclerView();
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupRecyclerView() {
        binding.templateRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.templateRecyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.newButton.setOnClickListener(view -> openEditor(null));
        binding.bottomNavView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_nav_operate) {
                startActivity(new Intent(this, OperateActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_spectrum) {
                startActivity(new Intent(this, SpectrumActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_logbook) {
                startActivity(new Intent(this, QsoLogbookActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_settings) {
                return true;
            }
            return false;
        });
    }

    private void refreshUi() {
        displayedTemplates = txTemplateStore.loadTemplates();
        adapter.submit(displayedTemplates);
        binding.subtitleText.setText(getString(R.string.tx_template_list_subtitle, displayedTemplates.size()));
        binding.summaryText.setText(renderSummary());
        binding.statusText.setText(statusMessage);
        binding.statusText.setVisibility(statusMessage == null || statusMessage.trim().isEmpty()
                ? View.GONE
                : View.VISIBLE);
        binding.emptyStateText.setVisibility(displayedTemplates.isEmpty() ? View.VISIBLE : View.GONE);
        FormalBottomNavStyler.apply(binding.bottomNavView, FormalBottomNavStyler.Page.SETTINGS);
    }

    private String renderSummary() {
        return getString(
                R.string.tx_template_list_summary,
                String.join(" ", txTemplateStore.supportedPlaceholders())
        );
    }

    private void openEditor(@Nullable String templateId) {
        Intent intent = new Intent(this, TxTemplateEditorActivity.class);
        if (templateId != null) {
            intent.putExtra(TxTemplateEditorActivity.EXTRA_TEMPLATE_ID, templateId);
        } else {
            intent.putExtra(TxTemplateEditorActivity.EXTRA_START_FRESH, true);
        }
        startActivity(intent);
    }

    @Override
    public void onTemplateClicked(TxTemplateEntry entry) {
        openEditor(entry == null ? null : entry.id());
    }

    @Override
    public void onTemplateLongPressed(View anchorView, TxTemplateEntry entry) {
        if (entry == null) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add(0, MENU_EDIT, 0, getString(R.string.tx_template_menu_edit));
        popupMenu.getMenu().add(0, MENU_DELETE, 1, getString(R.string.tx_template_menu_delete));
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_EDIT) {
                openEditor(entry.id());
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                confirmDelete(entry);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void confirmDelete(TxTemplateEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tx_template_delete_title)
                .setMessage(getString(R.string.tx_template_delete_message, entry.name()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tx_template_menu_delete, (dialog, which) -> deleteTemplate(entry))
                .show();
    }

    private void deleteTemplate(TxTemplateEntry entry) {
        boolean deleted = txTemplateStore.deleteTemplate(entry.id());
        if (!deleted) {
            Toast.makeText(this, R.string.tx_template_keep_one, Toast.LENGTH_SHORT).show();
            return;
        }
        statusMessage = getString(R.string.tx_template_status_deleted, entry.name());
        refreshUi();
    }
}
