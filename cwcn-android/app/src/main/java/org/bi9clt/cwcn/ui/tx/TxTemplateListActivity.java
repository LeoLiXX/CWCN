package org.bi9clt.cwcn.ui.tx;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.bi9clt.cwcn.BuildConfig;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TxTemplateListActivity extends AppCompatActivity implements TxTemplateListAdapter.Callbacks {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;

    private ActivityTxTemplateListBinding binding;
    private TxTemplateStore txTemplateStore;
    private TxTemplateListAdapter adapter;
    private List<TxTemplateEntry> displayedTemplates = Collections.emptyList();
    private String statusMessage = "";
    private ActivityResultLauncher<String[]> importDocumentLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxTemplateListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        importDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleImportDocument
        );
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
        binding.importButton.setOnClickListener(view -> launchImportDocumentPicker());
        binding.exportButton.setOnClickListener(view -> exportTemplates());
        binding.newButton.setOnClickListener(view -> openEditor(null));
        binding.bottomNavView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_nav_operate) {
                Intent intent = new Intent(this, OperateActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
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

    private void launchImportDocumentPicker() {
        if (importDocumentLauncher == null) {
            return;
        }
        importDocumentLauncher.launch(new String[]{"application/json", "text/plain"});
    }

    private void handleImportDocument(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        String rawJson;
        try {
            rawJson = readTextFromUri(uri);
        } catch (IOException exception) {
            Toast.makeText(this, R.string.tx_template_import_failed_read, Toast.LENGTH_SHORT).show();
            return;
        }
        int importableCount = txTemplateStore.countImportableTemplates(rawJson);
        if (importableCount <= 0) {
            Toast.makeText(this, R.string.tx_template_import_failed_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        showImportModeDialog(rawJson, importableCount);
    }

    private void showImportModeDialog(String rawJson, int importableCount) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tx_template_import_dialog_title)
                .setMessage(getString(R.string.tx_template_import_dialog_message, importableCount))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.tx_template_import_mode_append,
                        (dialog, which) -> applyTemplateImport(rawJson, TxTemplateStore.ImportMode.APPEND)
                )
                .setNeutralButton(
                        R.string.tx_template_import_mode_replace,
                        (dialog, which) -> applyTemplateImport(
                                rawJson,
                                TxTemplateStore.ImportMode.REPLACE_SAME_NAME
                        )
                )
                .show();
    }

    private void applyTemplateImport(String rawJson, TxTemplateStore.ImportMode importMode) {
        TxTemplateStore.ImportResult result = txTemplateStore.importTemplates(rawJson, importMode);
        if (result.importedCount() <= 0) {
            Toast.makeText(this, R.string.tx_template_import_failed_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        statusMessage = renderImportStatus(result, importMode);
        refreshUi();
    }

    private String renderImportStatus(
            TxTemplateStore.ImportResult result,
            TxTemplateStore.ImportMode importMode
    ) {
        boolean renamed = result.renamedCount() > 0;
        if (importMode == TxTemplateStore.ImportMode.REPLACE_SAME_NAME) {
            return renamed
                    ? getString(
                    R.string.tx_template_status_imported_replace_renamed,
                    result.importedCount(),
                    result.updatedCount(),
                    result.createdCount(),
                    result.renamedCount()
            )
                    : getString(
                    R.string.tx_template_status_imported_replace,
                    result.importedCount(),
                    result.updatedCount(),
                    result.createdCount()
            );
        }
        return renamed
                ? getString(
                R.string.tx_template_status_imported_append_renamed,
                result.importedCount(),
                result.createdCount(),
                result.renamedCount()
        )
                : getString(
                R.string.tx_template_status_imported_append,
                result.importedCount(),
                result.createdCount()
        );
    }

    private void exportTemplates() {
        try {
            String backupJson = txTemplateStore.exportBackupJson();
            File exportFile = writeExportFile(backupJson);
            Uri shareUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    exportFile
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.tx_template_export_subject));
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.setClipData(ClipData.newUri(
                    getContentResolver(),
                    getString(R.string.tx_template_export_subject),
                    shareUri
            ));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            statusMessage = getString(
                    R.string.tx_template_status_exported,
                    displayedTemplates.size(),
                    exportFile.getName()
            );
            refreshUi();
            startActivity(Intent.createChooser(
                    shareIntent,
                    getString(R.string.tx_template_export_chooser)
            ));
        } catch (IOException exception) {
            Toast.makeText(this, R.string.tx_template_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private File writeExportFile(String backupJson) throws IOException {
        File shareDir = new File(getCacheDir(), "logbook-share");
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            throw new IOException("Failed to create share directory");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
        File exportFile = new File(shareDir, "cwcn-tx-templates-" + timestamp + ".json");
        try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
            outputStream.write(backupJson.getBytes(StandardCharsets.UTF_8));
        }
        return exportFile;
    }

    private String readTextFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Input stream unavailable");
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }
}
