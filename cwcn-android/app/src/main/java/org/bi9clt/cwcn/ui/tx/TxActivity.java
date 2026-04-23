package org.bi9clt.cwcn.ui.tx;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.tx.CwTxBackend;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPreset;
import org.bi9clt.cwcn.core.tx.CwTxState;
import org.bi9clt.cwcn.core.tx.LocalSidetoneTxBackend;
import org.bi9clt.cwcn.core.tx.RigTextTxBackend;
import org.bi9clt.cwcn.databinding.ActivityTxBinding;

import java.util.ArrayList;
import java.util.Locale;

public final class TxActivity extends AppCompatActivity {
    private static final String DEFAULT_TX_TEXT = "CQ CQ DE BI9CLT";
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private ActivityTxBinding binding;
    private CwTxEngine txEngine;
    private LocalLogRepository localLogRepository;
    private AudioTrackTxAudioOutput txAudioOutput;
    private ArrayList<TxBackendOption> backendOptions;

    private CwTxPlan currentPlan;
    private CwTxPlaybackSnapshot lastPlaybackSnapshot;
    private boolean syncingFields;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        txEngine = new CwTxEngine();
        localLogRepository = new LocalLogRepository(this);
        txAudioOutput = new AudioTrackTxAudioOutput();
        backendOptions = buildBackendOptions();
        setupBackendSelector();
        setupPresetSelector();
        setupDefaults();
        setupActions();
        rebuildPlanPreview();
    }

    @Override
    protected void onDestroy() {
        stopAllBackends();
        txAudioOutput.stop();
        super.onDestroy();
    }

    private void setupDefaults() {
        String defaultCallsign = resolveDefaultStationCallsign();
        syncingFields = true;
        binding.stationCallsignEditText.setText(defaultCallsign);
        binding.txTextEditText.setText(DEFAULT_TX_TEXT);
        binding.wpmEditText.setText(String.valueOf(DEFAULT_WPM));
        binding.toneFrequencyEditText.setText(String.valueOf(DEFAULT_TONE_FREQUENCY_HZ));
        syncingFields = false;

        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingFields) {
                    rebuildPlanPreview();
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(watcher);
        binding.txTextEditText.addTextChangedListener(watcher);
        binding.wpmEditText.addTextChangedListener(watcher);
        binding.toneFrequencyEditText.addTextChangedListener(watcher);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.rebuildPreviewButton.setOnClickListener(view -> rebuildPlanPreview());
        binding.applyPresetButton.setOnClickListener(view -> applySelectedPreset());
        binding.startTxButton.setOnClickListener(view -> startTx());
        binding.stopTxButton.setOnClickListener(view -> stopTx());
    }

    private void setupBackendSelector() {
        ArrayAdapter<TxBackendOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                backendOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.backendSpinner.setAdapter(adapter);
        binding.backendSpinner.setSelection(0);
        binding.backendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                stopAllBackends();
                lastPlaybackSnapshot = null;
                rebuildPlanPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                rebuildPlanPreview();
            }
        });
    }

    private void setupPresetSelector() {
        ArrayAdapter<CwTxPreset> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CwTxPreset.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.presetSpinner.setAdapter(adapter);
        binding.presetSpinner.setSelection(0);
    }

    private void rebuildPlanPreview() {
        CwTxBackend backend = selectedBackend();
        currentPlan = txEngine.buildPlan(
                binding.txTextEditText.getText() == null ? null : binding.txTextEditText.getText().toString(),
                parsedPositiveInt(binding.wpmEditText.getText(), DEFAULT_WPM),
                parsedPositiveInt(binding.toneFrequencyEditText.getText(), DEFAULT_TONE_FREQUENCY_HZ)
        );
        binding.normalizedText.setText(currentPlan.normalizedText().isEmpty()
                ? "(empty)"
                : currentPlan.normalizedText());
        binding.morsePreviewText.setText(currentPlan.morsePreview().isEmpty()
                ? "(no supported Morse symbols)"
                : currentPlan.morsePreview());
        binding.planSummaryText.setText(renderPlanSummary(currentPlan));
        binding.backendSummaryText.setText(renderBackendSummary(backend));
        binding.routeChecklistText.setText(renderRouteChecklist(backend, currentPlan));
        if (lastPlaybackSnapshot == null || !backend.isRunning()) {
            binding.txStatusText.setText(renderIdleStatus(backend));
            binding.txProgressText.setText("Progress: 0%");
        }
        boolean editableTxProfile = backend.supportsLivePlanProfile() || backend instanceof LocalSidetoneTxBackend;
        binding.toneFrequencyEditText.setEnabled(editableTxProfile);
        binding.toneFrequencyEditText.setAlpha(editableTxProfile ? 1.0f : 0.55f);
        refreshButtons();
    }

    private void startTx() {
        CwTxBackend backend = selectedBackend();
        rebuildPlanPreview();
        if (currentPlan == null || currentPlan.elements().isEmpty()) {
            Toast.makeText(this, "Nothing to send yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!backend.isReady()) {
            Toast.makeText(this, backend.describeAvailability(), Toast.LENGTH_SHORT).show();
            binding.txStatusText.setText(renderIdleStatus(backend));
            refreshButtons();
            return;
        }
        if (backend.isRunning()) {
            Toast.makeText(this, "TX is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean started = backend.start(currentPlan, snapshot ->
                runOnUiThread(() -> applyPlaybackSnapshot(snapshot)));
        if (!started) {
            Toast.makeText(this, "Unable to start TX.", Toast.LENGTH_SHORT).show();
        }
        refreshButtons();
    }

    private void stopTx() {
        selectedBackend().stop();
        binding.txStatusText.setText(renderIdleStatus(selectedBackend()));
        refreshButtons();
    }

    private void applyPlaybackSnapshot(CwTxPlaybackSnapshot snapshot) {
        lastPlaybackSnapshot = snapshot;
        binding.txStatusText.setText(renderTxStatus(snapshot));
        binding.txProgressText.setText(renderTxProgress(snapshot));
        refreshButtons();
    }

    private void refreshButtons() {
        CwTxBackend backend = selectedBackend();
        boolean running = backend.isRunning();
        binding.startTxButton.setEnabled(
                currentPlan != null
                        && !currentPlan.elements().isEmpty()
                        && backend.isReady()
                        && !running
        );
        binding.stopTxButton.setEnabled(running);
    }

    private String renderPlanSummary(CwTxPlan plan) {
        return "WPM: " + plan.wpm()
                + "\nTone: " + plan.toneFrequencyHz() + " Hz"
                + "\nDot: " + plan.dotDurationMs() + " ms"
                + "\nElements: " + plan.elements().size()
                + "\nEstimated duration: " + formatMs(plan.totalDurationMs());
    }

    private String renderBackendSummary(CwTxBackend backend) {
        return "Backend: " + backend.displayName()
                + "\nRoute: " + backend.describeRoute()
                + "\nReady: " + yesNo(backend.isReady())
                + "\nAvailability: " + backend.describeAvailability()
                + "\nUses current WPM/Tone: " + yesNo(backend.supportsLivePlanProfile())
                + "\nProgress snapshots: " + yesNo(backend.supportsProgressSnapshots());
    }

    private String renderRouteChecklist(CwTxBackend backend, CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        if ("local-sidetone".equals(backend.id())) {
            builder.append("Local checklist: use this route for dry-run verification, headphones are preferred, and current WPM/tone are applied directly.");
        } else if ("rig-text:audio-vox-text".equals(backend.id())) {
            builder.append("Audio VOX checklist: connect phone audio to the rig/keyer audio path, enable VOX on the target device, and start with conservative phone volume.");
            if (plan != null) {
                if (plan.toneFrequencyHz() < 500 || plan.toneFrequencyHz() > 900) {
                    builder.append("\nTone warning: VOX testing is usually easier around 500-900 Hz.");
                }
                if (plan.wpm() < 10 || plan.wpm() > 28) {
                    builder.append("\nWPM warning: initial VOX validation is usually easier around 10-28 WPM.");
                }
                if (plan.totalDurationMs() > 30000) {
                    builder.append("\nLength warning: very long over-the-air VOX tests are harder to calibrate; start with a shorter macro.");
                }
            }
        } else {
            builder.append("Rig route checklist: confirm backend readiness first, then verify how this adapter expects keying/PTT/audio to reach the target device.");
        }
        return builder.toString();
    }

    private String renderTxStatus(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return renderIdleStatus(selectedBackend());
        }
        return "State: " + snapshot.state().displayName()
                + "\nStatus: " + snapshot.statusMessage()
                + "\nCurrent element: " + (snapshot.currentElementLabel().isEmpty() ? "-" : snapshot.currentElementLabel())
                + "\nTone active: " + yesNo(snapshot.toneActive())
                + "\nElapsed: " + formatMs(snapshot.elapsedMs()) + " / " + formatMs(snapshot.totalDurationMs());
    }

    private String renderTxProgress(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return "Progress: 0%";
        }
        return "Progress: "
                + Math.round(snapshot.completionRatio() * 100.0d)
                + "%"
                + "\nElements: "
                + snapshot.completedElementCount()
                + " / "
                + snapshot.totalElementCount();
    }

    private int parsedPositiveInt(Editable editable, int fallback) {
        if (editable == null) {
            return fallback;
        }
        String raw = editable.toString().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String formatMs(int durationMs) {
        int clamped = Math.max(0, durationMs);
        int seconds = clamped / 1000;
        int millis = clamped % 1000;
        return String.format(Locale.US, "%d.%03ds", seconds, millis);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String renderIdleStatus(CwTxBackend backend) {
        if (backend.isReady()) {
            return "TX idle. Build a plan and press Start TX.";
        }
        return "TX idle.\nSelected backend is not ready yet.\nReason: " + backend.describeAvailability();
    }

    private CwTxBackend selectedBackend() {
        Object selectedItem = binding.backendSpinner.getSelectedItem();
        if (selectedItem instanceof TxBackendOption) {
            return ((TxBackendOption) selectedItem).backend();
        }
        return backendOptions.get(0).backend();
    }

    private void applySelectedPreset() {
        Object selectedItem = binding.presetSpinner.getSelectedItem();
        CwTxPreset preset = selectedItem instanceof CwTxPreset
                ? (CwTxPreset) selectedItem
                : CwTxPreset.GENERAL_CQ;
        syncingFields = true;
        binding.txTextEditText.setText(preset.render(normalizedStationCallsign()));
        syncingFields = false;
        lastPlaybackSnapshot = null;
        rebuildPlanPreview();
    }

    private String normalizedStationCallsign() {
        Editable editable = binding.stationCallsignEditText.getText();
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim().toUpperCase(Locale.US);
        return raw.isEmpty() ? null : raw;
    }

    private String resolveDefaultStationCallsign() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        if (overview != null && overview.activeDraft() != null && overview.activeDraft().stationCallsignUsed() != null) {
            return overview.activeDraft().stationCallsignUsed();
        }
        if (overview != null && overview.latestConfirmedLog() != null && overview.latestConfirmedLog().stationCallsign() != null) {
            return overview.latestConfirmedLog().stationCallsign();
        }
        return "BI9CLT";
    }

    private ArrayList<TxBackendOption> buildBackendOptions() {
        ArrayList<TxBackendOption> options = new ArrayList<>();
        options.add(new TxBackendOption(new LocalSidetoneTxBackend(txAudioOutput)));
        for (RigControlAdapter adapter : RigRegistry.defaultAdapters()) {
            if (adapter.supportsTextToCw()) {
                options.add(new TxBackendOption(new RigTextTxBackend(adapter)));
            }
        }
        return options;
    }

    private void stopAllBackends() {
        for (TxBackendOption option : backendOptions) {
            option.backend().stop();
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
