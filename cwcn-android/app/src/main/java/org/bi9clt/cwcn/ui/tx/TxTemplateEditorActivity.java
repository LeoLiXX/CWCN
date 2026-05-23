package org.bi9clt.cwcn.ui.tx;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.TxTemplateEntry;
import org.bi9clt.cwcn.core.app.TxTemplateStore;
import org.bi9clt.cwcn.databinding.ActivityTxTemplateEditorBinding;

import java.util.List;

public final class TxTemplateEditorActivity extends AppCompatActivity {
    public static final String EXTRA_TEMPLATE_ID =
            "org.bi9clt.cwcn.ui.tx.extra.TEMPLATE_ID";
    public static final String EXTRA_START_FRESH =
            "org.bi9clt.cwcn.ui.tx.extra.START_FRESH";

    private ActivityTxTemplateEditorBinding binding;
    private TxTemplateStore txTemplateStore;
    private TxTemplateEntry sourceEntry;
    private boolean syncingEditor;
    private boolean editorDirty;
    private String actionStatusMessage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxTemplateEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        txTemplateStore = new TxTemplateStore(this);
        setupActions();
        setupEditorWatchers();
        reloadFromStore();
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.resetButton.setOnClickListener(view -> resetEditor());
        binding.saveButton.setOnClickListener(view -> saveTemplate());
    }

    private void setupEditorWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                onEditorChanged();
            }
        };
        binding.templateNameEditText.addTextChangedListener(watcher);
        binding.templateBodyEditText.addTextChangedListener(watcher);
    }

    private void reloadFromStore() {
        String templateId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TEMPLATE_ID);
        boolean startFresh = getIntent() != null && getIntent().getBooleanExtra(EXTRA_START_FRESH, false);
        sourceEntry = startFresh ? null : txTemplateStore.findTemplateById(templateId);
        syncEditorFromSource();
        refreshUi();
    }

    private void syncEditorFromSource() {
        syncingEditor = true;
        binding.templateNameEditText.setText(sourceEntry == null ? "" : sourceEntry.name());
        binding.templateBodyEditText.setText(sourceEntry == null ? "" : sourceEntry.body());
        syncingEditor = false;
        editorDirty = false;
        actionStatusMessage = sourceEntry == null
                ? getString(R.string.tx_template_editor_status_new)
                : getString(R.string.tx_template_editor_status_edit);
        refreshDerivedViews();
    }

    private void onEditorChanged() {
        if (syncingEditor) {
            return;
        }
        editorDirty = true;
        refreshDerivedViews();
    }

    private void refreshUi() {
        boolean editingExisting = sourceEntry != null;
        binding.titleText.setText(editingExisting
                ? R.string.tx_template_editor_title_edit
                : R.string.tx_template_editor_title_new);
        binding.subtitleText.setText(editingExisting
                ? getString(R.string.tx_template_editor_subtitle_edit, sourceEntry.name())
                : getString(R.string.tx_template_editor_subtitle_new));
        binding.editorStatusText.setText(renderEditorStatus());
        refreshDerivedViews();
    }

    private void refreshDerivedViews() {
        List<String> placeholders = txTemplateStore.supportedPlaceholders();
        binding.placeholderSummaryText.setText(
                getString(R.string.tx_template_editor_placeholder_summary, String.join(" ", placeholders))
        );
        String previewBody = normalizeEditorValue(binding.templateBodyEditText.getText());
        binding.templatePreviewText.setText(getString(
                R.string.tx_template_editor_preview,
                previewBody == null ? getString(R.string.tx_template_editor_preview_empty) : previewBody
        ));
    }

    private String renderEditorStatus() {
        if (editorDirty) {
            return getString(R.string.tx_template_editor_status_dirty);
        }
        return actionStatusMessage;
    }

    private void resetEditor() {
        syncEditorFromSource();
        actionStatusMessage = sourceEntry == null
                ? getString(R.string.tx_template_editor_status_reset_new)
                : getString(R.string.tx_template_editor_status_reset_edit);
        refreshUi();
    }

    private void saveTemplate() {
        String name = normalizeEditorValue(binding.templateNameEditText.getText());
        String body = normalizeEditorValue(binding.templateBodyEditText.getText());
        if (name == null) {
            Toast.makeText(this, R.string.tx_template_editor_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (body == null) {
            Toast.makeText(this, R.string.tx_template_editor_body_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isTemplateNameUnique(name, sourceEntry == null ? null : sourceEntry.id())) {
            Toast.makeText(this, R.string.tx_template_editor_name_duplicate, Toast.LENGTH_SHORT).show();
            return;
        }
        TxTemplateEntry saved = txTemplateStore.saveTemplate(
                sourceEntry == null ? null : sourceEntry.id(),
                name,
                body
        );
        sourceEntry = saved;
        syncingEditor = true;
        binding.templateNameEditText.setText(saved.name());
        binding.templateBodyEditText.setText(saved.body());
        syncingEditor = false;
        editorDirty = false;
        actionStatusMessage = getString(R.string.tx_template_editor_status_saved, saved.name());
        refreshUi();
        Toast.makeText(this, actionStatusMessage, Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean isTemplateNameUnique(String candidateName, @Nullable String currentId) {
        List<TxTemplateEntry> templates = txTemplateStore.loadTemplates();
        for (TxTemplateEntry entry : templates) {
            if (entry == null) {
                continue;
            }
            if (currentId != null && currentId.equals(entry.id())) {
                continue;
            }
            if (candidateName.equalsIgnoreCase(entry.name())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private String normalizeEditorValue(@Nullable Editable editable) {
        if (editable == null) {
            return null;
        }
        String trimmed = editable.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
