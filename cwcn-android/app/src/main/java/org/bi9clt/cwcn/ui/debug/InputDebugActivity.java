package org.bi9clt.cwcn.ui.debug;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.adif.CwAdifExporter;
import org.bi9clt.cwcn.core.adif.CwAdifFileWriter;
import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.AudioInputHealthFormatter;
import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.audio.LocalFileRxAudioSource;
import org.bi9clt.cwcn.core.audio.MicrophoneRxAudioSource;
import org.bi9clt.cwcn.core.audio.RxAudioSource;
import org.bi9clt.cwcn.core.audio.SyntheticFixtureRxAudioSource;
import org.bi9clt.cwcn.core.bootstrap.BootstrapModule;
import org.bi9clt.cwcn.core.bootstrap.BootstrapRegistry;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluator;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifier;
import org.bi9clt.cwcn.core.interpreter.CwInterpretationEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpretedToken;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoStateEvent;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.bi9clt.cwcn.databinding.ActivityInputDebugBinding;
import org.bi9clt.cwcn.ui.qso.QsoEditorActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.File;
import java.io.IOException;

public final class InputDebugActivity extends AppCompatActivity implements RxAudioSource.Callback {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int AMPLITUDE_MAX = 32767;
    private static final long LIVE_UI_REFRESH_INTERVAL_MS = 120L;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final String DEBUG_PREFERENCES = "cwcn_debug_preferences";
    private static final String PREF_PREFERRED_TONE_FREQUENCY_HZ = "preferred_tone_frequency_hz";
    private static final String PREF_LOCAL_FILE_URI = "local_file_uri";
    private static final String PREF_LOCAL_FILE_LABEL = "local_file_label";
    private static final String PREF_LOCAL_FOLDER_URI = "local_folder_uri";
    private static final String PREF_LOCAL_FOLDER_LABEL = "local_folder_label";

    private ActivityInputDebugBinding binding;
    private Handler mainHandler;
    private MicrophoneRxAudioSource microphoneRxAudioSource;
    private SyntheticFixtureRxAudioSource syntheticFixtureRxAudioSource;
    private LocalFileRxAudioSource localFileRxAudioSource;
    private RxAudioSource activeRxAudioSource;
    private AudioInputHealthTracker audioInputHealthTracker;
    private AudioSpectrumAnalyzer audioSpectrumAnalyzer;
    private CwSignalProcessor cwSignalProcessor;
    private CwHybridTimingModel cwTimingModel;
    private CwDecoder cwDecoder;
    private CwInterpreter cwInterpreter;
    private CwInterpreter qsoInterpreter;
    private QsoStateMachine qsoStateMachine;
    private LocalLogRepository localLogRepository;

    private long receivedFrameCount;
    private long receivedSampleCount;
    private int lastPeakAmplitude;
    private double lastRmsAmplitude;
    private QsoDraftSnapshot persistedDraftSnapshot;
    private int confirmedLogCount;
    private String qsoStorageStatusMessage = "";
    private String adifExportStatusMessage = "";
    private String qsoEditorStatusMessage = "";
    private String callsignCandidateStatusMessage = "";
    private CwFixtureScenario lastFixtureScenario;
    private CwFixtureEvaluationResult lastFixtureEvaluationResult;
    private List<CwFixtureEvaluationResult> recentFixtureEvaluationResults = new ArrayList<>();
    private String fixtureEvaluationStatusMessage = "";
    private boolean fixtureReplayInProgress;
    private boolean hypothesisGuardExperimentEnabled;
    private boolean batchRunInProgress;
    private boolean syncingDraftEditor;
    private boolean draftEditorDirty;
    private boolean detailedPanelsVisible;
    private boolean activityResumed;
    private boolean liveUiRefreshPosted;
    private long lastLiveUiRefreshAtMs;
    private AudioSpectrumSnapshot lastSpectrumSnapshot;
    private Uri selectedLocalFolderUri;
    private String selectedLocalFolderLabel = "";
    private String batchRunStatusMessage = "Batch run idle.";
    private Uri batchPreviousLocalFileUri;
    private String batchPreviousLocalFileLabel = "";
    private ArrayList<BatchRunItem> pendingBatchRunItems = new ArrayList<>();
    private BatchRunItem activeBatchRunItem;
    private int batchRunTotalCount;
    private int batchRunCompletedCount;
    private final Map<Integer, Integer> stableTextMinLines = new HashMap<>();
    private final Runnable liveUiRefreshRunnable = this::refreshLiveUiFromPipeline;
    private ActivityResultLauncher<String[]> localFilePickerLauncher;
    private ActivityResultLauncher<Uri> localFolderPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInputDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mainHandler = new Handler(Looper.getMainLooper());
        localFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleLocalFilePicked
        );
        localFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                this::handleLocalFolderPicked
        );
        microphoneRxAudioSource = new MicrophoneRxAudioSource(this);
        microphoneRxAudioSource.setCallback(this);
        syntheticFixtureRxAudioSource = new SyntheticFixtureRxAudioSource();
        syntheticFixtureRxAudioSource.setCallback(this);
        localFileRxAudioSource = new LocalFileRxAudioSource(this);
        localFileRxAudioSource.setCallback(this);
        audioInputHealthTracker = new AudioInputHealthTracker();
        audioSpectrumAnalyzer = new AudioSpectrumAnalyzer();
        cwSignalProcessor = new CwSignalProcessor();
        cwSignalProcessor.setExperimentalHypothesisGuardEnabled(false);
        cwTimingModel = new CwHybridTimingModel();
        cwDecoder = new CwDecoder();
        cwInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        qsoInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY);
        qsoStateMachine = new QsoStateMachine();
        localLogRepository = new LocalLogRepository(this);
        restorePreferredToneFrequency();
        restoreSelectedLocalFile();
        restoreSelectedLocalFolder();
        restorePersistedDraftIfAvailable();
        refreshStoredState();

        binding.versionText.setText(getString(R.string.bootstrap_version, BuildConfig.VERSION_NAME));
        binding.summaryText.setText(getString(R.string.bootstrap_summary));
        binding.moduleListText.setText(renderModuleList(BootstrapRegistry.defaultModules()));
        setupDraftEditor();
        setupInputSourceSelector();
        setupFixtureScenarioSelector();
        setupPreferredToneControls();
        setupActions();
        refreshUiSnapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        refreshStoredState();
        refreshUiSnapshot();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        liveUiRefreshPosted = false;
        mainHandler.removeCallbacks(liveUiRefreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(liveUiRefreshRunnable);
        stopAllSources();
        microphoneRxAudioSource.release();
        syntheticFixtureRxAudioSource.release();
        localFileRxAudioSource.release();
        super.onDestroy();
    }

    private String renderModuleList(List<BootstrapModule> modules) {
        StringBuilder builder = new StringBuilder();
        for (BootstrapModule module : modules) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("[").append(module.status().displayName()).append("] ")
                    .append(module.title())
                    .append("\n")
                    .append(module.description());
        }
        return builder.toString();
    }

    private void setupInputSourceSelector() {
        ArrayAdapter<InputSourceOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.asList(InputSourceOption.values())
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.inputSourceSpinner.setAdapter(adapter);
        binding.inputSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshUiSnapshot();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshUiSnapshot();
            }
        });
    }

    private void setupFixtureScenarioSelector() {
        ArrayAdapter<CwFixtureScenario> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CwFixtureLibrary.scenarios()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.fixtureScenarioSpinner.setAdapter(adapter);
        binding.fixtureScenarioSpinner.setSelection(0);
        binding.fixtureScenarioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshUiSnapshot();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshUiSnapshot();
            }
        });
    }

    private void setupDraftEditor() {
        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingDraftEditor) {
                    draftEditorDirty = true;
                    binding.qsoEditorStatusText.setText(renderQsoEditorStatus());
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(watcher);
        binding.remoteCallsignEditText.addTextChangedListener(watcher);
        binding.rstSentEditText.addTextChangedListener(watcher);
        binding.rstRcvdEditText.addTextChangedListener(watcher);
        binding.nameEditText.addTextChangedListener(watcher);
        binding.qthEditText.addTextChangedListener(watcher);
    }

    private void setupActions() {
        binding.refreshStatusButton.setOnClickListener(view -> refreshUiSnapshot());
        binding.requestPermissionButton.setOnClickListener(view -> requestMissingPermissions());
        binding.startCaptureButton.setOnClickListener(view -> startSelectedSource());
        binding.stopCaptureButton.setOnClickListener(view -> stopCapture());
        binding.rxFocusStartCaptureButton.setOnClickListener(view -> startSelectedSource());
        binding.rxFocusStopCaptureButton.setOnClickListener(view -> stopCapture());
        binding.toggleHypothesisGuardButton.setOnClickListener(view -> toggleHypothesisGuardExperiment());
        binding.toggleDebugDetailsButton.setOnClickListener(view -> toggleDebugDetails());
        binding.applyPreferredToneButton.setOnClickListener(view -> applyPreferredToneFromEditor());
        binding.useFixtureToneButton.setOnClickListener(view -> applySelectedFixtureTone());
        binding.selectLocalFileButton.setOnClickListener(view -> launchLocalFilePicker());
        binding.selectLocalFolderButton.setOnClickListener(view -> launchLocalFolderPicker());
        binding.runBatchAnalysisButton.setOnClickListener(view -> startBatchAnalysis());
        binding.applyDraftEditsButton.setOnClickListener(view -> applyDraftEditorEdits());
        binding.resetDraftEditsButton.setOnClickListener(view -> resetDraftEditorEdits());
    }

    private void setupPreferredToneControls() {
        binding.preferredToneFrequencyEditText.setText(String.valueOf(
                cwSignalProcessor.snapshot().preferredToneFrequencyHz()
        ));
    }

    private void toggleHypothesisGuardExperiment() {
        hypothesisGuardExperimentEnabled = !hypothesisGuardExperimentEnabled;
        cwSignalProcessor.setExperimentalHypothesisGuardEnabled(hypothesisGuardExperimentEnabled);
        updateHypothesisGuardButton();
        refreshUiSnapshot();
    }

    private void updateHypothesisGuardButton() {
        if (binding == null) {
            return;
        }
        binding.toggleHypothesisGuardButton.setText(
                hypothesisGuardExperimentEnabled ? "Hyp Guard ON" : "Hyp Guard OFF"
        );
    }

    private void refreshUiSnapshot() {
        InputSourceOption selectedOption = selectedInputSource();
        CwFixtureScenario selectedScenario = selectedFixtureScenario();
        RxAudioSource selectedSource = sourceForOption(selectedOption);
        CwInterpreterSnapshot interpreterSnapshot = cwInterpreter.snapshot();
        QsoDraftSnapshot qsoSnapshot = qsoStateMachine.snapshot();
        binding.fixtureScenarioSpinner.setEnabled(selectedOption == InputSourceOption.SYNTHETIC_FIXTURE);
        binding.fixtureScenarioLabelText.setVisibility(selectedOption == InputSourceOption.SYNTHETIC_FIXTURE
                ? View.VISIBLE
                : View.GONE);
        binding.fixtureScenarioSpinner.setVisibility(selectedOption == InputSourceOption.SYNTHETIC_FIXTURE
                ? View.VISIBLE
                : View.GONE);
        binding.useFixtureToneButton.setEnabled(selectedOption == InputSourceOption.SYNTHETIC_FIXTURE);
        binding.useFixtureToneButton.setVisibility(selectedOption == InputSourceOption.SYNTHETIC_FIXTURE
                ? View.VISIBLE
                : View.GONE);
        boolean localFileSelected = selectedOption == InputSourceOption.LOCAL_FILE_REPLAY;
        binding.localFileLabelText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.localFileStatusText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.localFolderStatusText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.selectLocalFileButton.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.selectLocalFolderButton.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        setStableText(binding.localFileStatusText, localFileRxAudioSource.selectionSummary());
        setStableText(binding.localFolderStatusText, renderLocalFolderStatus());
        binding.fixtureEvaluationText.setVisibility(detailedPanelsVisible || fixtureReplayInProgress
                ? View.VISIBLE
                : View.GONE);
        updateHypothesisGuardButton();
        updateDebugPanelVisibility();
        binding.selectedSourceStatusText.setText(renderSelectedSourceStatus(selectedOption, selectedScenario));
        binding.fixtureEvaluationText.setText(renderFixtureEvaluationStatus());
        binding.permissionHintText.setText(renderPermissionStatus());
        binding.microphoneStatusText.setText(renderMicrophoneStatus());
        binding.bluetoothStatusText.setText(renderBluetoothStatus());
        binding.usbStatusText.setText(renderUsbStatus());
        binding.transportRegistryText.setText(renderTransportRegistry());
        binding.captureStateText.setText(getString(
                R.string.capture_state_value,
                selectedSource == null ? RxAudioSource.State.IDLE.displayName() : selectedSource.state().displayName()
        ));
        binding.frameStatsText.setText(renderFrameStats());
        updateLevelViews(lastPeakAmplitude, lastRmsAmplitude);
        refreshAudioSpectrumView();
        setStableText(binding.signalStateText, renderSignalState());
        setStableText(binding.signalHealthText, renderSignalHealthSummary());
        setStableText(binding.rxFocusStatusText, renderRxFocusStatus(selectedOption, selectedSource));
        setStableText(binding.rxFocusDecodeText, renderRxFocusDecode(interpreterSnapshot));
        setStableText(binding.batchRunStatusText, renderBatchRunStatus());
        binding.signalEventStatsText.setText(renderSignalEventStats());
        binding.lastSignalEventText.setText(renderLastSignalEvent());
        binding.timingStateText.setText(renderTimingState());
        binding.timingEventStatsText.setText(renderTimingEventStats());
        binding.lastTimingEventText.setText(renderLastTimingEvent());
        binding.decoderStateText.setText(renderDecoderState());
        binding.decoderEventStatsText.setText(renderDecoderEventStats());
        setStableText(binding.decoderOutputText, renderDecoderOutput());
        binding.lastDecoderEventText.setText(renderLastDecoderEvent());
        setStableText(binding.interpreterRawText, renderInterpreterRawText(interpreterSnapshot));
        setStableText(binding.interpreterNormalizedText, renderInterpreterNormalizedText(interpreterSnapshot));
        binding.interpreterCallsignsText.setText(renderInterpreterCallsigns(interpreterSnapshot));
        binding.interpreterHintsText.setText(renderInterpreterHints(interpreterSnapshot));
        binding.lastInterpreterEventText.setText(renderLastInterpreterEvent());
        binding.qsoPhaseText.setText(renderQsoPhase(qsoSnapshot));
        setStableText(binding.qsoDraftText, renderQsoDraft(qsoSnapshot));
        syncDraftEditorFromSnapshot(qsoSnapshot, false);
        binding.qsoEditorStatusText.setText(renderQsoEditorStatus());
        refreshCallsignCandidateButtons(interpreterSnapshot);
        binding.callsignCandidateStatusText.setText(renderCallsignCandidateStatus(interpreterSnapshot));
        binding.qsoReadinessText.setText(renderQsoReadiness(qsoSnapshot));
        binding.lastQsoEventText.setText(renderLastQsoEvent(qsoSnapshot));
        binding.qsoStorageStatusText.setText(renderQsoStorageStatus());
        binding.adifFieldMapText.setText(renderAdifFieldMap());
        binding.adifPreviewText.setText(renderAdifPreview());
        binding.adifExportStatusText.setText(renderAdifExportStatus());

        boolean canStartSelectedSource = false;
        if (selectedSource != null) {
            boolean permissionSatisfied = selectedOption != InputSourceOption.PHONE_MICROPHONE
                    || hasRecordAudioPermission();
            canStartSelectedSource = permissionSatisfied
                    && selectedSource.isAvailable()
                    && selectedSource.state() != RxAudioSource.State.RUNNING
                    && selectedSource.state() != RxAudioSource.State.STARTING
                    && selectedSource.state() != RxAudioSource.State.STOPPING;
        }

        binding.startCaptureButton.setEnabled(
                !selectedOption.implemented() || canStartSelectedSource
        );
        binding.rxFocusStartCaptureButton.setEnabled(
                !selectedOption.implemented() || canStartSelectedSource
        );
        binding.stopCaptureButton.setEnabled(anySourceActive());
        binding.rxFocusStopCaptureButton.setEnabled(anySourceActive());
        binding.runBatchAnalysisButton.setEnabled(!anySourceActive() && !batchRunInProgress);
        binding.applyDraftEditsButton.setEnabled(draftEditorDirty);
        binding.resetDraftEditsButton.setEnabled(hasManualCorrections(qsoSnapshot));
        binding.saveDraftButton.setEnabled(hasDraftContent(qsoSnapshot) || draftEditorDirty);
        binding.confirmLogButton.setEnabled(hasConfirmableDraft(qsoSnapshot) || hasRemoteCallsignInEditor());
        binding.exportAdifButton.setEnabled(confirmedLogCount > 0);
    }

    private void toggleDebugDetails() {
        detailedPanelsVisible = !detailedPanelsVisible;
        refreshUiSnapshot();
    }

    private void updateDebugPanelVisibility() {
        int detailedVisibility = detailedPanelsVisible ? View.VISIBLE : View.GONE;
        binding.toggleDebugDetailsButton.setText(detailedPanelsVisible
                ? "Hide Detailed Panels"
                : "Show Detailed Panels");
        binding.deviceStatusPanel.setVisibility(detailedVisibility);
        binding.adifPanel.setVisibility(View.GONE);
        binding.qsoPanel.setVisibility(View.GONE);
        binding.interpreterPanel.setVisibility(detailedVisibility);
        binding.decoderPanel.setVisibility(detailedVisibility);
        binding.timingPanel.setVisibility(detailedVisibility);
        binding.capturePanel.setVisibility(detailedVisibility);
        binding.signalPanel.setVisibility(detailedVisibility);
        binding.modulePanel.setVisibility(detailedVisibility);
    }

    private String renderRxFocusStatus(InputSourceOption selectedOption, RxAudioSource selectedSource) {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        String sourceLabel = selectedOption == null ? "(none)" : selectedOption.toString();
        String captureState = selectedSource == null
                ? RxAudioSource.State.IDLE.displayName()
                : selectedSource.state().displayName();
        return "Source: " + sourceLabel
                + "\nCapture: " + captureState
                + "\nInput: peak " + lastPeakAmplitude
                + " / rms " + Math.round(lastRmsAmplitude)
                + "\nTone: pref " + snapshot.preferredToneFrequencyHz()
                + " Hz, tracked " + snapshot.targetToneFrequencyHz()
                + " Hz, lock " + yesNo(snapshot.targetToneLocked())
                + "\nHyp Guard: " + (snapshot.hypothesisGuardExperimentEnabled() ? "ON" : "OFF")
                + " | apply " + snapshot.hypothesisGuardApplyCount()
                + " | " + snapshot.hypothesisGuardDecision()
                + "\nFront-end: " + CwFrontEndHealthClassifier.qualityCode(snapshot)
                + " / " + CwFrontEndHealthClassifier.bottleneckCode(snapshot)
                + " - " + CwFrontEndHealthClassifier.reason(snapshot)
                + "\nLevels: tone " + Math.round(snapshot.lastToneRmsAmplitude())
                + ", residual " + Math.round(snapshot.lastWidebandResidualRmsAmplitude())
                + ", isolation " + Math.round(snapshot.narrowbandIsolationRatio() * 100.0d)
                + "%"
                + "\nEvents: on/off " + snapshot.totalToneOnEvents()
                + "/" + snapshot.totalToneOffEvents()
                + ", frame-gap resets " + snapshot.frameGapResetCount();
    }

    private String renderRxFocusDecode(CwInterpreterSnapshot interpreterSnapshot) {
        return "Decoded: " + renderDecoderOutput()
                + "\nNormalized: " + renderInterpreterNormalizedText(interpreterSnapshot)
                + "\nCallsigns: " + renderInterpreterCallsigns(interpreterSnapshot);
    }

    private InputSourceOption selectedInputSource() {
        Object selectedItem = binding.inputSourceSpinner.getSelectedItem();
        if (selectedItem instanceof InputSourceOption) {
            return (InputSourceOption) selectedItem;
        }
        return InputSourceOption.SYNTHETIC_FIXTURE;
    }

    private CwFixtureScenario selectedFixtureScenario() {
        Object selectedItem = binding.fixtureScenarioSpinner.getSelectedItem();
        if (selectedItem instanceof CwFixtureScenario) {
            return (CwFixtureScenario) selectedItem;
        }
        return CwFixtureLibrary.defaultScenario();
    }

    private RxAudioSource sourceForOption(InputSourceOption option) {
        if (option == null) {
            return null;
        }
        switch (option) {
            case SYNTHETIC_FIXTURE:
                return syntheticFixtureRxAudioSource;
            case PHONE_MICROPHONE:
                return microphoneRxAudioSource;
            case LOCAL_FILE_REPLAY:
                return localFileRxAudioSource;
            case BLUETOOTH_LINK:
            case USB_EXTERNAL:
            default:
                return null;
        }
    }

    private String renderSelectedSourceStatus(InputSourceOption option, CwFixtureScenario scenario) {
        StringBuilder builder = new StringBuilder(option.description());
        RxAudioSource source = sourceForOption(option);
        if (source != null) {
            builder.append("\nSource: ").append(source.displayName());
        }
        if (option == InputSourceOption.SYNTHETIC_FIXTURE && scenario != null) {
            builder.append("\nFixture: ").append(scenario.displayName())
                    .append(" [").append(scenario.id()).append("]")
                    .append("\nMessage: ").append(scenario.message())
                    .append("\nScript parts: ").append(scenario.messageParts().size());
            if (scenario.messageParts().size() > 1) {
                builder.append(" (gap ").append(scenario.interMessageGapMs()).append(" ms)");
            }
            builder.append("\nProfile: ")
                    .append(scenario.wpm()).append(" WPM, ")
                    .append(scenario.toneFrequencyHz()).append(" Hz, noise ")
                    .append(scenario.noiseAmplitude());
            if (scenario.interfererToneAmplitude() > 0 && scenario.interfererToneFrequencyHz() > 0) {
                builder.append(", interferer ")
                        .append(scenario.interfererToneFrequencyHz())
                        .append(" Hz @ ")
                        .append(scenario.interfererToneAmplitude());
                if (Math.abs(scenario.interfererToneDriftHz()) > 0.0d) {
                    builder.append(" (drift ")
                            .append(String.format(Locale.US, "%+.1f", scenario.interfererToneDriftHz()))
                            .append(" Hz)");
                }
            }
            if (!scenario.additionalInterferers().isEmpty()) {
                for (CwFixtureScenario.ContinuousInterfererProfile interferer : scenario.additionalInterferers()) {
                    builder.append(", extra ")
                            .append(interferer.toneFrequencyHz())
                            .append(" Hz @ ")
                            .append(interferer.toneAmplitude());
                    if (Math.abs(interferer.toneDriftHz()) > 0.0d) {
                        builder.append(" (drift ")
                                .append(String.format(Locale.US, "%+.1f", interferer.toneDriftHz()))
                                .append(" Hz)");
                    }
                    if (interferer.isBursting()) {
                        builder.append(" [burst ")
                                .append(interferer.burstOnMs())
                                .append("/")
                                .append(interferer.burstOffMs())
                                .append(" ms");
                        if (interferer.burstOffsetMs() > 0) {
                            builder.append(", offset ")
                                    .append(interferer.burstOffsetMs())
                                    .append(" ms");
                        }
                        if (interferer.hasBurstWobble()) {
                            builder.append(", wobble ")
                                    .append(Math.round(interferer.burstWobbleDepth() * 100.0d))
                                    .append("%/")
                                    .append(interferer.burstWobbleCycleMs())
                                    .append(" ms");
                        }
                        builder.append("]");
                    }
                }
            }
            if (scenario.qsbDepth() > 0.0d) {
                builder.append(", QSB ")
                        .append(Math.round(scenario.qsbDepth() * 100.0d))
                        .append("% / ")
                        .append(scenario.qsbCycleMs())
                        .append(" ms");
            }
            if (Math.abs(scenario.toneDriftHz()) > 0.0d) {
                builder.append(", drift ")
                        .append(String.format(Locale.US, "%+.1f", scenario.toneDriftHz()))
                        .append(" Hz");
            }
            if (scenario.riseRampMs() > 0 || scenario.fallRampMs() > 0) {
                builder.append(", edge ")
                        .append(scenario.riseRampMs())
                        .append("/")
                        .append(scenario.fallRampMs())
                        .append(" ms");
            }
            builder.append("\nTiming: ").append(scenario.timingProfileSummary());
            if (scenario.expectedFrontEndQualityCode() != null) {
                builder.append("\nExpected front-end: ")
                        .append(scenario.expectedFrontEndQualityCode())
                        .append("\nObserved front-end: ")
                        .append(renderObservedFrontEndStatus(scenario.expectedFrontEndQualityCode()));
            }
            builder.append("\nNotes: ").append(scenario.notes());
        }
        if (option == InputSourceOption.LOCAL_FILE_REPLAY) {
            builder.append("\n").append(localFileRxAudioSource.selectionSummary());
        }
        if (option == InputSourceOption.PHONE_MICROPHONE) {
            builder.append("\n").append(renderMicrophoneInputHealth());
            builder.append("\n").append(renderMicrophoneToneWatch());
        }
        builder.append("\nPreferred Tone: ")
                .append(cwSignalProcessor.snapshot().preferredToneFrequencyHz())
                .append(" Hz")
                .append("\n")
                .append(renderSourceHealthCoach(option, scenario));
        return builder.toString();
    }

    private String renderObservedFrontEndStatus(@Nullable String expectedQualityCode) {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        String observedQualityCode = CwFrontEndHealthClassifier.qualityCode(snapshot);
        String observedBottleneckCode = CwFrontEndHealthClassifier.bottleneckCode(snapshot);
        StringBuilder builder = new StringBuilder()
                .append(observedQualityCode)
                .append(" / ")
                .append(observedBottleneckCode)
                .append(" - ")
                .append(CwFrontEndHealthClassifier.qualityLabel(snapshot));
        if (expectedQualityCode != null) {
            if (expectedQualityCode.equals(observedQualityCode)) {
                builder.append(" (matches expected)");
            } else {
                builder.append(" (expected ").append(expectedQualityCode).append(")");
            }
        }
        return builder.toString();
    }

    private String renderMicrophoneToneWatch() {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        int trackingErrorHz = CwFrontEndHealthClassifier.trackingErrorHz(snapshot);
        String offsetLabel;
        if (Math.abs(trackingErrorHz) <= 15) {
            offsetLabel = "near target";
        } else if (trackingErrorHz > 0) {
            offsetLabel = "above preferred";
        } else {
            offsetLabel = "below preferred";
        }
        return "Mic Front-End: "
                + CwFrontEndHealthClassifier.qualityCode(snapshot)
                + " / "
                + CwFrontEndHealthClassifier.bottleneckCode(snapshot)
                + " - "
                + CwFrontEndHealthClassifier.qualityLabel(snapshot)
                + "\nMic Reason: "
                + CwFrontEndHealthClassifier.reason(snapshot)
                + "\nMic Trend: "
                + renderMicrophoneTrend(snapshot)
                + "\nMic Tone Watch: "
                + (snapshot.targetToneLocked() ? "LOCKED" : "SEARCH")
                + " | tracked "
                + snapshot.targetToneFrequencyHz()
                + " Hz ("
                + String.format(Locale.US, "%+d", trackingErrorHz)
                + " Hz, "
                + offsetLabel
                + ")"
                + "\nMic confidence: dominance "
                + Math.round(snapshot.toneDominanceRatio() * 100.0d)
                + "%, tone RMS "
                + String.format(Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                + ", isolation "
                + Math.round(snapshot.narrowbandIsolationRatio() * 100.0d)
                + "%"
                + "\nMic run stats: peak isolation "
                + Math.round(snapshot.peakNarrowbandIsolationRatio() * 100.0d)
                + "%, lock coverage "
                + Math.round(snapshot.lockedFrameRatio() * 100.0d)
                + "%"
                + "\nMic recent window: lock "
                + Math.round(snapshot.recentLockedFrameRatio() * 100.0d)
                + "%, search "
                + Math.round(snapshot.recentSearchFrameRatio() * 100.0d)
                + "%, active unlock "
                + Math.round(snapshot.recentActiveUnlockedFrameRatio() * 100.0d)
                + "%"
                + "\nMic recent alignment: near-target lock "
                + Math.round(snapshot.recentNearTargetLockedFrameRatio() * 100.0d)
                + "%, off-target lock "
                + Math.round(snapshot.recentFarOffTargetLockedFrameRatio() * 100.0d)
                + "%"
                + "\nMic recent trend: "
                + CwFrontEndHealthClassifier.recentTrendLabel(snapshot)
                + "\nMic history: "
                + renderRecentFrontEndHistory(snapshot)
                + "\nMic active leaders: "
                + cwSignalProcessor.debugActiveLeaderCompactSummary()
                + "\nMic release view: active unlock "
                + Math.round(snapshot.toneActiveUnlockedFrameRatio() * 100.0d)
                + "%, worst gap "
                + snapshot.maxConsecutiveToneActiveUnlockedFrames()
                + " frame(s)";
    }

    private String renderMicrophoneInputHealth() {
        AudioInputHealthSnapshot snapshot = audioInputHealthTracker.snapshot();
        return "Mic Input: "
                + AudioInputHealthFormatter.summaryLabel(snapshot)
                + "\nMic input window: "
                + AudioInputHealthFormatter.compactWindowSummary(snapshot)
                + "\nMic input detail: peak "
                + snapshot.lastPeakAmplitude()
                + ", RMS "
                + String.format(Locale.US, "%.1f", snapshot.lastRmsAmplitude())
                + ", clipped "
                + Math.round(snapshot.lastClippedSampleRatio() * 100.0d)
                + "%"
                + "\nMic input history: "
                + AudioInputHealthFormatter.stateHistory(snapshot);
    }

    private String renderSourceHealthCoach(
            InputSourceOption option,
            @Nullable CwFixtureScenario scenario
    ) {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        StringBuilder builder = new StringBuilder();
        builder.append("Focus: ");
        if (option == InputSourceOption.PHONE_MICROPHONE) {
            builder.append(renderMicrophoneHealthCoach(snapshot));
        } else if (option == InputSourceOption.LOCAL_FILE_REPLAY) {
            builder.append(renderLocalFileHealthCoach(snapshot));
        } else if (option == InputSourceOption.SYNTHETIC_FIXTURE && scenario != null) {
            builder.append(renderFixtureHealthCoach(snapshot, scenario));
        } else {
            builder.append("Select a live source to start front-end observation.");
        }
        return builder.toString();
    }

    private String renderLocalFileHealthCoach(CwSignalSnapshot snapshot) {
        if (!localFileRxAudioSource.isAvailable()) {
            return "Choose a phone-local WAV file first. M4A/AAC is allowed, but WAV PCM is the recommended baseline for reproducible debugging.";
        }
        if (!CwFrontEndHealthClassifier.hasFrontEndHistory(snapshot)) {
            return "Start local replay and compare the tracked tone against the visible spectrum peak before judging downstream decode quality.";
        }
        return "Local replay check: " + CwFrontEndHealthClassifier.liveCheckHint(snapshot);
    }

    private String renderMicrophoneHealthCoach(CwSignalSnapshot snapshot) {
        AudioInputHealthSnapshot inputSnapshot = audioInputHealthTracker.snapshot();
        if (inputSnapshot.totalFrames() <= 0) {
            return AudioInputHealthFormatter.coachHint(inputSnapshot);
        }
        if (inputSnapshot.recentClippingFrameRatio() >= 0.10d
                || inputSnapshot.recentQuietFrameRatio() >= 0.60d
                || inputSnapshot.recentHotFrameRatio() >= 0.50d) {
            return "Live mic check: " + AudioInputHealthFormatter.coachHint(inputSnapshot);
        }
        if (!CwFrontEndHealthClassifier.hasFrontEndHistory(snapshot)) {
            return "Start the mic source, key a steady tone near the preferred pitch, and watch for the history row to leave idle/search.";
        }
        return "Live mic check: " + CwFrontEndHealthClassifier.liveCheckHint(snapshot);
    }

    private String renderFixtureHealthCoach(
            CwSignalSnapshot snapshot,
            CwFixtureScenario scenario
    ) {
        int preferredToneHz = snapshot.preferredToneFrequencyHz();
        int fixtureToneHz = scenario.toneFrequencyHz();
        int fixtureAlignmentErrorHz = snapshot.targetToneFrequencyHz() - fixtureToneHz;
        String preferredAlignment = describeToneAlignment(preferredToneHz - fixtureToneHz);
        if (!CwFrontEndHealthClassifier.hasFrontEndHistory(snapshot)) {
            return "Fixture check: replay the scenario and compare the tracked tone against the fixture tone "
                    + fixtureToneHz
                    + " Hz before judging downstream decode quality.";
        }
        if (CwFrontEndHealthClassifier.suggestsWrongToneLock(snapshot)) {
            return "Fixture check: the tracker likely chose the wrong carrier. Preferred tone is "
                    + preferredToneHz
                    + " Hz, fixture tone is "
                    + fixtureToneHz
                    + " Hz (preferred is "
                    + preferredAlignment
                    + ").";
        }
        return "Fixture check: tracked tone is "
                + describeToneAlignment(fixtureAlignmentErrorHz)
                + " relative to the fixture target "
                + fixtureToneHz
                + " Hz, while preferred tone is "
                + preferredAlignment
                + " relative to that same fixture target. "
                + CwFrontEndHealthClassifier.liveCheckHint(snapshot);
    }

    private String describeToneAlignment(int offsetHz) {
        int absoluteOffsetHz = Math.abs(offsetHz);
        if (absoluteOffsetHz <= 10) {
            return "well aligned";
        }
        if (absoluteOffsetHz <= 20) {
            return offsetHz > 0 ? "slightly above target" : "slightly below target";
        }
        if (absoluteOffsetHz <= 45) {
            return offsetHz > 0 ? "noticeably above target" : "noticeably below target";
        }
        return offsetHz > 0 ? "far above target" : "far below target";
    }

    private String renderMicrophoneTrend(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "No front-end history yet.";
        }
        int pendingCandidateFrequencyHz = snapshot.pendingRetuneCandidateFrequencyHz();
        int pendingCandidateOffsetHz = pendingCandidateFrequencyHz - snapshot.targetToneFrequencyHz();
        String pendingDirectionLabel;
        if (Math.abs(pendingCandidateOffsetHz) <= 15) {
            pendingDirectionLabel = "near current track";
        } else if (pendingCandidateOffsetHz > 0) {
            pendingDirectionLabel = "above current track";
        } else {
            pendingDirectionLabel = "below current track";
        }

        if (snapshot.targetToneLocked()) {
            if (snapshot.pendingRetuneCandidateStableScans() > 0
                    && Math.abs(pendingCandidateOffsetHz) >= 20) {
                return "Lock streak "
                        + snapshot.consecutiveLockedFrames()
                        + " frame(s), but retune pressure is building toward "
                        + pendingCandidateFrequencyHz
                        + " Hz ("
                        + String.format(Locale.US, "%+d", pendingCandidateOffsetHz)
                        + " Hz, "
                        + pendingDirectionLabel
                        + ", stable scans "
                        + snapshot.pendingRetuneCandidateStableScans()
                        + ")";
            }
            return "Lock is steady with current streak "
                    + snapshot.consecutiveLockedFrames()
                    + " frame(s) and no meaningful retune pressure.";
        }

        if (snapshot.toneActive() && snapshot.consecutiveToneActiveUnlockedFrames() > 0) {
            return "Tone is active but currently unlocked for "
                    + snapshot.consecutiveToneActiveUnlockedFrames()
                    + " frame(s); candidate watch is "
                    + pendingCandidateFrequencyHz
                    + " Hz (stable scans "
                    + snapshot.pendingRetuneCandidateStableScans()
                    + ").";
        }

        if (snapshot.pendingRetuneCandidateStableScans() > 0) {
            return "Still searching; candidate watch is "
                    + pendingCandidateFrequencyHz
                    + " Hz ("
                    + pendingDirectionLabel
                    + ", stable scans "
                    + snapshot.pendingRetuneCandidateStableScans()
                    + ").";
        }

        return "Still searching with no stable retune candidate yet.";
    }

    private String renderRecentFrontEndHistory(CwSignalSnapshot snapshot) {
        if (snapshot == null || snapshot.recentHistoryFrameCount() <= 0) {
            return "(empty)";
        }
        return "state "
                + new String(snapshot.recentFrontEndStateHistory())
                + " | offset "
                + renderTrackingOffsetHistory(snapshot.recentTrackingOffsetHistoryHz())
                + " | legend L=locked, u=active-unlocked, l=quiet-lock, .=idle/search";
    }

    private String renderTrackingOffsetHistory(int[] offsetsHz) {
        if (offsetsHz == null || offsetsHz.length == 0) {
            return "(empty)";
        }
        StringBuilder builder = new StringBuilder(offsetsHz.length);
        for (int offsetHz : offsetsHz) {
            builder.append(offsetHistoryCode(offsetHz));
        }
        return builder.toString();
    }

    private char offsetHistoryCode(int offsetHz) {
        int absoluteOffsetHz = Math.abs(offsetHz);
        if (absoluteOffsetHz <= 15) {
            return '0';
        }
        if (absoluteOffsetHz >= 45) {
            return offsetHz > 0 ? '>' : '<';
        }
        return offsetHz > 0 ? '+' : '-';
    }

    private void restorePreferredToneFrequency() {
        int savedFrequencyHz = getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .getInt(PREF_PREFERRED_TONE_FREQUENCY_HZ, cwSignalProcessor.snapshot().preferredToneFrequencyHz());
        cwSignalProcessor.setPreferredToneFrequencyHz(savedFrequencyHz);
    }

    private void restoreSelectedLocalFile() {
        String savedUri = getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .getString(PREF_LOCAL_FILE_URI, "");
        String savedLabel = getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .getString(PREF_LOCAL_FILE_LABEL, "");
        if (savedUri == null || savedUri.trim().isEmpty()) {
            return;
        }
        Uri uri = Uri.parse(savedUri);
        localFileRxAudioSource.setSelectedFile(uri, savedLabel);
    }

    private void restoreSelectedLocalFolder() {
        String savedUri = getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .getString(PREF_LOCAL_FOLDER_URI, "");
        String savedLabel = getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .getString(PREF_LOCAL_FOLDER_LABEL, "");
        if (savedUri == null || savedUri.trim().isEmpty()) {
            return;
        }
        selectedLocalFolderUri = Uri.parse(savedUri);
        selectedLocalFolderLabel = safeFolderLabel(selectedLocalFolderUri, savedLabel);
    }

    private void launchLocalFilePicker() {
        if (localFilePickerLauncher == null) {
            Toast.makeText(this, "Local file picker is unavailable right now.", Toast.LENGTH_SHORT).show();
            return;
        }
        localFilePickerLauncher.launch(new String[]{
                "audio/wav",
                "audio/x-wav",
                "audio/wave",
                "audio/*",
                "application/octet-stream"
        });
    }

    private void handleLocalFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            binding.footerText.setText("Local file selection was cancelled.");
            refreshUiSnapshot();
            return;
        }
        tryPersistableReadPermission(uri);
        String label = localFileRxAudioSource.resolveDisplayName(uri);
        localFileRxAudioSource.setSelectedFile(uri, label);
        getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(PREF_LOCAL_FILE_URI, uri.toString())
                .putString(PREF_LOCAL_FILE_LABEL, label)
                .apply();
        binding.footerText.setText("Selected local replay file: " + label);
        refreshUiSnapshot();
    }

    private void launchLocalFolderPicker() {
        if (localFolderPickerLauncher == null) {
            Toast.makeText(this, "Local folder picker is unavailable right now.", Toast.LENGTH_SHORT).show();
            return;
        }
        localFolderPickerLauncher.launch(selectedLocalFolderUri);
    }

    private void handleLocalFolderPicked(@Nullable Uri uri) {
        if (uri == null) {
            binding.footerText.setText("Local batch folder selection was cancelled.");
            refreshUiSnapshot();
            return;
        }
        tryPersistableReadPermission(uri);
        selectedLocalFolderUri = uri;
        selectedLocalFolderLabel = safeFolderLabel(uri, "");
        getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(PREF_LOCAL_FOLDER_URI, uri.toString())
                .putString(PREF_LOCAL_FOLDER_LABEL, selectedLocalFolderLabel)
                .apply();
        binding.footerText.setText("Selected local batch folder: " + selectedLocalFolderLabel);
        refreshUiSnapshot();
    }

    private void tryPersistableReadPermission(Uri uri) {
        if (uri == null) {
            return;
        }
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) {
        }
    }

    private String renderLocalFolderStatus() {
        if (selectedLocalFolderUri == null) {
            return "No batch folder selected. Choose a folder that contains local audio recordings for one-click replay.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Batch folder: ").append(selectedLocalFolderLabel.isEmpty()
                ? selectedLocalFolderUri
                : selectedLocalFolderLabel);
        builder.append("\nUri: ").append(selectedLocalFolderUri);
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, selectedLocalFolderUri);
            if (directory == null || !directory.exists() || !directory.isDirectory()) {
                builder.append("\nStatus: folder is unavailable right now.");
                return builder.toString();
            }
            List<BatchRunItem> localItems = buildLocalFolderBatchItems();
            builder.append("\nDirect audio files: ").append(localItems.size());
            if (!localItems.isEmpty()) {
                builder.append("\nPreview: ");
                int previewCount = Math.min(3, localItems.size());
                for (int index = 0; index < previewCount; index++) {
                    if (index > 0) {
                        builder.append(" / ");
                    }
                    builder.append(localItems.get(index).label);
                }
                if (localItems.size() > previewCount) {
                    builder.append(" / ...");
                }
            }
        } catch (SecurityException securityException) {
            builder.append("\nStatus: read permission is not available anymore.");
        }
        return builder.toString();
    }

    private String renderBatchRunStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append(batchRunStatusMessage);
        if (batchRunInProgress) {
            builder.append("\nProgress: ")
                    .append(batchRunCompletedCount)
                    .append("/")
                    .append(batchRunTotalCount);
            if (activeBatchRunItem != null) {
                builder.append("\nCurrent: ").append(activeBatchRunItem.displayLabel());
            } else if (!pendingBatchRunItems.isEmpty()) {
                builder.append("\nNext: ").append(pendingBatchRunItems.get(0).displayLabel());
            }
            builder.append("\nPending: ").append(pendingBatchRunItems.size());
        }
        return builder.toString();
    }

    private void startBatchAnalysis() {
        if (batchRunInProgress) {
            Toast.makeText(this, "Batch run is already in progress.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (anySourceActive()) {
            Toast.makeText(this, "Stop the current source before starting a batch run.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<BatchRunItem> queue = new ArrayList<>();
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            queue.add(BatchRunItem.synthetic(scenario));
        }
        List<BatchRunItem> localItems = buildLocalFolderBatchItems();
        queue.addAll(localItems);
        if (queue.isEmpty()) {
            batchRunStatusMessage = "Nothing to run. Synthetic fixtures are unavailable and no local audio folder is selected.";
            refreshUiSnapshot();
            return;
        }

        batchPreviousLocalFileUri = localFileRxAudioSource.selectedFileUri();
        batchPreviousLocalFileLabel = localFileRxAudioSource.selectedFileLabel();
        pendingBatchRunItems = queue;
        activeBatchRunItem = null;
        batchRunInProgress = true;
        batchRunTotalCount = queue.size();
        batchRunCompletedCount = 0;
        batchRunStatusMessage = "Batch queued: "
                + CwFixtureLibrary.scenarios().size()
                + " synthetic fixture(s), "
                + localItems.size()
                + " local audio file(s).";
        binding.footerText.setText(batchRunStatusMessage);
        refreshUiSnapshot();
        mainHandler.post(this::startNextBatchRunItem);
    }

    private void startNextBatchRunItem() {
        if (!batchRunInProgress) {
            return;
        }
        if (pendingBatchRunItems.isEmpty()) {
            finishBatchRun("Batch run completed.", false);
            return;
        }

        BatchRunItem item = pendingBatchRunItems.remove(0);
        activeBatchRunItem = item;
        batchRunStatusMessage = "Running "
                + (batchRunCompletedCount + 1)
                + "/"
                + batchRunTotalCount
                + ": "
                + item.displayLabel();
        binding.footerText.setText(batchRunStatusMessage);

        if (item.kind == BatchRunItem.Kind.SYNTHETIC_FIXTURE) {
            lastFixtureScenario = item.fixtureScenario;
            syntheticFixtureRxAudioSource.setScenario(item.fixtureScenario);
            fixtureReplayInProgress = false;
            fixtureEvaluationStatusMessage = "Batch replaying fixture " + item.fixtureScenario.displayName() + ".";
            if (!startPreparedSourceRun(syntheticFixtureRxAudioSource)) {
                activeBatchRunItem = null;
                batchRunCompletedCount += 1;
                mainHandler.post(this::startNextBatchRunItem);
            }
            return;
        }

        localFileRxAudioSource.setSelectedFile(item.localFileUri, item.label);
        fixtureReplayInProgress = false;
        fixtureEvaluationStatusMessage = "Batch replaying local file " + item.label + ".";
        if (!startPreparedSourceRun(localFileRxAudioSource)) {
            activeBatchRunItem = null;
            batchRunCompletedCount += 1;
            mainHandler.post(this::startNextBatchRunItem);
        }
    }

    private boolean startPreparedSourceRun(RxAudioSource selectedSource) {
        if (selectedSource == null) {
            return false;
        }
        receivedFrameCount = 0L;
        receivedSampleCount = 0L;
        lastPeakAmplitude = 0;
        lastRmsAmplitude = 0.0d;
        audioSpectrumAnalyzer.reset();
        lastSpectrumSnapshot = null;
        audioInputHealthTracker.reset();
        cwSignalProcessor.reset();
        cwSignalProcessor.setExperimentalHypothesisGuardEnabled(hypothesisGuardExperimentEnabled);
        cwTimingModel.reset();
        cwDecoder.reset();
        cwInterpreter.reset();
        qsoInterpreter.reset();
        qsoStateMachine.reset();
        stopAllSourcesExcept(selectedSource);
        activeRxAudioSource = selectedSource;
        selectedSource.start();
        refreshUiSnapshot();
        return true;
    }

    private void handleBatchRunTerminalState(RxAudioSource.State state, String detail) {
        if (!batchRunInProgress || activeBatchRunItem == null) {
            return;
        }
        BatchRunItem completedItem = activeBatchRunItem;
        boolean completed = detail != null && detail.toLowerCase(Locale.US).contains("completed");
        flushPendingDecodeAt(SystemClock.elapsedRealtime());
        if (completedItem.kind == BatchRunItem.Kind.SYNTHETIC_FIXTURE && completedItem.fixtureScenario != null) {
            CwFixtureEvaluationResult evaluationResult = CwFixtureEvaluator.evaluate(
                    completedItem.fixtureScenario,
                    cwInterpreter.snapshot(),
                    qsoStateMachine.snapshot(),
                    cwSignalProcessor.snapshot(),
                    completed
            );
            lastFixtureEvaluationResult = evaluationResult;
            localLogRepository.saveFixtureEvaluation(evaluationResult);
        }
        refreshStoredState();
        batchRunCompletedCount += 1;
        activeBatchRunItem = null;
        if (!batchRunInProgress) {
            return;
        }
        if (pendingBatchRunItems.isEmpty()) {
            finishBatchRun("Batch run completed.", false);
        } else {
            batchRunStatusMessage = "Finished "
                    + batchRunCompletedCount
                    + "/"
                    + batchRunTotalCount
                    + ". Preparing next item.";
            mainHandler.post(this::startNextBatchRunItem);
        }
    }

    private void finishBatchRun(String message, boolean cancelled) {
        if (!batchRunInProgress) {
            return;
        }
        restorePostBatchSelections();
        batchRunInProgress = false;
        pendingBatchRunItems.clear();
        activeBatchRunItem = null;
        batchRunTotalCount = 0;
        batchRunStatusMessage = message;
        binding.footerText.setText(batchRunStatusMessage);
        refreshUiSnapshot();
    }

    private void restorePostBatchSelections() {
        localFileRxAudioSource.setSelectedFile(batchPreviousLocalFileUri, batchPreviousLocalFileLabel);
        batchPreviousLocalFileUri = null;
        batchPreviousLocalFileLabel = "";
    }

    private List<BatchRunItem> buildLocalFolderBatchItems() {
        ArrayList<BatchRunItem> items = new ArrayList<>();
        if (selectedLocalFolderUri == null) {
            return items;
        }
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, selectedLocalFolderUri);
            if (directory == null || !directory.exists() || !directory.isDirectory()) {
                return items;
            }
            for (DocumentFile child : directory.listFiles()) {
                if (!isBatchAudioFile(child)) {
                    continue;
                }
                String label = child.getName();
                if (label == null || label.trim().isEmpty()) {
                    label = localFileRxAudioSource.resolveDisplayName(child.getUri());
                }
                items.add(BatchRunItem.local(child.getUri(), label));
            }
        } catch (SecurityException ignored) {
            return items;
        }
        Collections.sort(items, (left, right) -> left.label.compareToIgnoreCase(right.label));
        return items;
    }

    private boolean isBatchAudioFile(@Nullable DocumentFile file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String mimeType = file.getType();
        if (mimeType != null && mimeType.toLowerCase(Locale.US).startsWith("audio/")) {
            return true;
        }
        String name = file.getName();
        if (name == null) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.US);
        return lowerName.endsWith(".wav")
                || lowerName.endsWith(".m4a")
                || lowerName.endsWith(".aac")
                || lowerName.endsWith(".mp3")
                || lowerName.endsWith(".flac")
                || lowerName.endsWith(".ogg")
                || lowerName.endsWith(".opus");
    }

    private String safeFolderLabel(@Nullable Uri uri, @Nullable String fallback) {
        if (uri == null) {
            return "";
        }
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, uri);
            if (directory != null) {
                String name = directory.getName();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null ? uri.toString() : lastSegment;
    }

    private String nonEmpty(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "(empty)";
        }
        return value;
    }

    private void applyPreferredToneFromEditor() {
        String rawValue = binding.preferredToneFrequencyEditText.getText() == null
                ? ""
                : binding.preferredToneFrequencyEditText.getText().toString().trim();
        if (rawValue.isEmpty()) {
            Toast.makeText(this, "Enter a preferred tone frequency in Hz.", Toast.LENGTH_SHORT).show();
            return;
        }

        int requestedFrequencyHz;
        try {
            requestedFrequencyHz = Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "Tone frequency must be a whole number in Hz.", Toast.LENGTH_SHORT).show();
            return;
        }

        applyPreferredToneFrequency(requestedFrequencyHz, true);
    }

    private void applySelectedFixtureTone() {
        CwFixtureScenario scenario = selectedFixtureScenario();
        if (scenario == null) {
            return;
        }
        applyPreferredToneFrequency(scenario.toneFrequencyHz(), true);
    }

    private void applyPreferredToneFrequency(int requestedFrequencyHz, boolean showToast) {
        cwSignalProcessor.setPreferredToneFrequencyHz(requestedFrequencyHz);
        int appliedFrequencyHz = cwSignalProcessor.snapshot().preferredToneFrequencyHz();
        binding.preferredToneFrequencyEditText.setText(String.valueOf(appliedFrequencyHz));
        getSharedPreferences(DEBUG_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putInt(PREF_PREFERRED_TONE_FREQUENCY_HZ, appliedFrequencyHz)
                .apply();
        binding.footerText.setText("Preferred tone updated to " + appliedFrequencyHz + " Hz.");
        if (showToast) {
            Toast.makeText(this, "Preferred tone " + appliedFrequencyHz + " Hz", Toast.LENGTH_SHORT).show();
        }
        refreshUiSnapshot();
    }

    private String renderFixtureEvaluationStatus() {
        StringBuilder builder = new StringBuilder();
        if (fixtureReplayInProgress && lastFixtureScenario != null) {
            builder.append("Fixture evaluation pending for ")
                    .append(lastFixtureScenario.displayName())
                    .append(".");
        } else if (lastFixtureEvaluationResult != null) {
            builder.append(lastFixtureEvaluationResult.renderSummary());
        } else if (!fixtureEvaluationStatusMessage.isEmpty()) {
            builder.append(fixtureEvaluationStatusMessage);
        } else {
            builder.append("No fixture evaluation yet.");
        }

        if (!recentFixtureEvaluationResults.isEmpty()) {
            builder.append("\n\nRecent history:");
            for (CwFixtureEvaluationResult result : recentFixtureEvaluationResults) {
                builder.append("\n- ").append(result.renderCompactSummary());
            }
        }
        return builder.toString();
    }

    private boolean anySourceActive() {
        return isSourceActive(microphoneRxAudioSource)
                || isSourceActive(syntheticFixtureRxAudioSource)
                || isSourceActive(localFileRxAudioSource);
    }

    private boolean isSourceActive(RxAudioSource source) {
        if (source == null) {
            return false;
        }
        RxAudioSource.State state = source.state();
        return state == RxAudioSource.State.RUNNING
                || state == RxAudioSource.State.STARTING
                || state == RxAudioSource.State.STOPPING;
    }

    private void stopAllSources() {
        microphoneRxAudioSource.stop();
        syntheticFixtureRxAudioSource.stop();
        localFileRxAudioSource.stop();
        activeRxAudioSource = null;
    }

    private void stopAllSourcesExcept(RxAudioSource retainedSource) {
        if (retainedSource != microphoneRxAudioSource) {
            microphoneRxAudioSource.stop();
        }
        if (retainedSource != syntheticFixtureRxAudioSource) {
            syntheticFixtureRxAudioSource.stop();
        }
        if (retainedSource != localFileRxAudioSource) {
            localFileRxAudioSource.stop();
        }
    }

    private void startSelectedSource() {
        if (batchRunInProgress) {
            Toast.makeText(this, "Batch run is in progress. Stop it first if you want a manual replay.", Toast.LENGTH_SHORT).show();
            return;
        }
        InputSourceOption selectedOption = selectedInputSource();
        if (!selectedOption.implemented()) {
            Toast.makeText(this, R.string.source_not_ready_toast, Toast.LENGTH_SHORT).show();
            refreshUiSnapshot();
            return;
        }

        RxAudioSource selectedSource = sourceForOption(selectedOption);
        if (selectedSource == null) {
            Toast.makeText(this, "Selected source is not wired yet.", Toast.LENGTH_SHORT).show();
            refreshUiSnapshot();
            return;
        }

        if (selectedOption == InputSourceOption.PHONE_MICROPHONE && !hasRecordAudioPermission()) {
            requestMissingPermissions();
            return;
        }
        if (selectedOption == InputSourceOption.LOCAL_FILE_REPLAY && !localFileRxAudioSource.isAvailable()) {
            launchLocalFilePicker();
            return;
        }

        if (selectedOption == InputSourceOption.SYNTHETIC_FIXTURE) {
            lastFixtureScenario = selectedFixtureScenario();
            syntheticFixtureRxAudioSource.setScenario(lastFixtureScenario);
            lastFixtureEvaluationResult = null;
            fixtureReplayInProgress = true;
            fixtureEvaluationStatusMessage = "Running fixture " + lastFixtureScenario.displayName() + ".";
        } else {
            fixtureReplayInProgress = false;
        }
        startPreparedSourceRun(selectedSource);
    }

    private void stopCapture() {
        stopAllSources();
        flushPendingDecodeAt(SystemClock.elapsedRealtime());
        if (fixtureReplayInProgress) {
            fixtureEvaluationStatusMessage = "Fixture replay stopped before completion.";
        }
        if (batchRunInProgress) {
            finishBatchRun("Batch run cancelled by user.", true);
        }
        refreshUiSnapshot();
    }

    private String renderPermissionStatus() {
        List<String> missingPermissions = collectMissingPermissions();
        if (missingPermissions.isEmpty()) {
            return getString(R.string.permission_ready);
        }
        return getString(R.string.permission_missing_value, joinLabels(missingPermissions));
    }

    private String renderMicrophoneStatus() {
        boolean hasMicFeature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        return getString(
                R.string.device_status_template,
                hasMicFeature ? getString(R.string.status_supported) : getString(R.string.status_unsupported),
                hasRecordAudioPermission() ? getString(R.string.status_granted) : getString(R.string.status_missing),
                microphoneRxAudioSource.state().displayName()
        );
    }

    private String renderBluetoothStatus() {
        boolean hasFeature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        boolean permissionGranted = hasBluetoothConnectPermission();
        String adapterState = getString(R.string.status_unavailable);
        try {
            BluetoothManager manager = ContextCompat.getSystemService(this, BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter != null) {
                adapterState = adapter.isEnabled() ? getString(R.string.status_enabled) : getString(R.string.status_disabled);
            }
        } catch (SecurityException securityException) {
            adapterState = getString(R.string.status_permission_required);
        }

        return getString(
                R.string.device_status_template,
                hasFeature ? getString(R.string.status_supported) : getString(R.string.status_unsupported),
                permissionGranted ? getString(R.string.status_granted) : getString(R.string.status_missing),
                adapterState
        );
    }

    private String renderUsbStatus() {
        boolean hasFeature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        return getString(
                R.string.device_status_template,
                hasFeature ? getString(R.string.status_supported) : getString(R.string.status_unsupported),
                getString(R.string.status_not_needed),
                hasFeature ? getString(R.string.status_ready_to_probe) : getString(R.string.status_unavailable)
        );
    }

    private String renderTransportRegistry() {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.transport_registry_title_inline)).append("\n");
        for (RigTransport transport : RigRegistry.defaultTransports()) {
            builder.append("[").append(transport.kind().name()).append("] ")
                    .append(transport.displayName())
                    .append("\n")
                    .append(transport.describeAvailability(this))
                    .append("\n\n");
        }

        builder.append(getString(R.string.adapter_registry_title_inline)).append("\n");
        for (RigControlAdapter adapter : RigRegistry.defaultAdapters(this)) {
            builder.append(adapter.displayName())
                    .append("\n")
                    .append(adapter.describeCapabilities())
                    .append("\n")
                    .append(adapter.describeAvailability())
                    .append("\n")
                    .append(getString(
                            R.string.adapter_capability_template,
                            yesNo(adapter.supportsTextToCw()),
                            yesNo(adapter.supportsPttControl())
                    ))
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String renderFrameStats() {
        if (receivedFrameCount == 0L) {
            return getString(R.string.capture_frame_empty);
        }
        String stateLabel = activeRxAudioSource == null
                ? RxAudioSource.State.IDLE.displayName()
                : activeRxAudioSource.state().displayName();
        return getString(
                R.string.capture_frame_stats,
                receivedFrameCount,
                receivedSampleCount,
                stateLabel
        );
    }

    private void updateLevelViews(int peakAmplitude, double rmsAmplitude) {
        int peakPercent = Math.min(100, peakAmplitude * 100 / AMPLITUDE_MAX);
        int rmsPercent = Math.min(100, (int) Math.round(rmsAmplitude * 100.0d / AMPLITUDE_MAX));
        binding.peakProgressBar.setProgress(peakPercent);
        binding.rmsProgressBar.setProgress(rmsPercent);
        binding.peakLevelText.setText(getString(R.string.capture_peak_value, peakAmplitude, peakPercent));
        binding.rmsLevelText.setText(getString(
                R.string.capture_rms_value,
                String.format(Locale.US, "%.1f", rmsAmplitude),
                rmsPercent
        ));
    }

    private void refreshAudioSpectrumView() {
        binding.rxFocusSpectrumView.setSpectrumSnapshot(lastSpectrumSnapshot);
        setStableText(binding.rxFocusSpectrumSummaryText, renderAudioSpectrumSummary());
        binding.audioSpectrumView.setSpectrumSnapshot(lastSpectrumSnapshot);
        setStableText(binding.audioSpectrumSummaryText, renderAudioSpectrumSummary());
    }

    private CharSequence renderAudioSpectrumSummary() {
        if (lastSpectrumSnapshot == null) {
            return "Waiting for live audio. TRK is the actual live decode reference. The chart will also show PREF, HYP, HG, PW, WS, AQ, AD, and a noise baseline.";
        }
        float noise = lastSpectrumSnapshot.noiseFloorMagnitude();
        float peak = lastSpectrumSnapshot.peakMagnitude();
        int separation = Math.round(peak - noise);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append("Decode ref: ");
        appendSpectrumLegendTag(builder, "TRK", 0xFFF4B04F);
        builder.append(" = live target actually used by RX decode");
        builder.append("\nPeak ")
                .append(String.valueOf(lastSpectrumSnapshot.peakFrequencyHz()))
                .append(" Hz | preferred ")
                .append(String.valueOf(lastSpectrumSnapshot.preferredToneHz()))
                .append(" Hz | tracked ")
                .append(String.valueOf(lastSpectrumSnapshot.trackedToneHz()))
                .append(" Hz | hyp ")
                .append(String.valueOf(lastSpectrumSnapshot.hypothesisToneHz()))
                .append(" Hz");
        builder.append("\nNoise baseline ")
                .append(String.valueOf(Math.round(noise)))
                .append(" | peak level ")
                .append(String.valueOf(Math.round(peak)))
                .append(" | separation ")
                .append(String.format(Locale.US, "%+d", separation));
        builder.append("\nHyp Guard ")
                .append(lastSpectrumSnapshot.hypothesisGuardEnabled() ? "ON" : "OFF")
                .append(" | last ")
                .append(String.valueOf(lastSpectrumSnapshot.hypothesisGuardAppliedToneHz()))
                .append(" Hz | ")
                .append(lastSpectrumSnapshot.hypothesisGuardDecision());
        builder.append("\nLegend: ");
        appendSpectrumLegendTag(builder, "PREF", 0xFFB8C0CC);
        builder.append(" setting, ");
        appendSpectrumLegendTag(builder, "HYP", 0xFFD96CFF);
        builder.append(" statistical hypothesis, ");
        appendSpectrumLegendTag(builder, "HG", 0xFF1DE9B6);
        builder.append(" last guard point, ");
        appendSpectrumLegendTag(builder, "PW", 0xFF4FC3F7);
        builder.append(" preferred-window winner, ");
        appendSpectrumLegendTag(builder, "WS", 0xFFEF5350);
        builder.append(" wide-scan winner, ");
        appendSpectrumLegendTag(builder, "AQ", 0xFF81C784);
        builder.append(" acquisition winner, ");
        appendSpectrumLegendTag(builder, "AD", 0xFFFFD54F);
        builder.append(" final adopted scan result, ");
        appendSpectrumLegendTag(builder, "TRK", 0xFFF4B04F);
        builder.append(" actual decode reference");
        return builder;
    }

    private void appendSpectrumLegendTag(SpannableStringBuilder builder, String label, int color) {
        if (builder == null || label == null || label.isEmpty()) {
            return;
        }
        int start = builder.length();
        builder.append(label);
        builder.setSpan(
                new ForegroundColorSpan(color),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    private String renderSignalState() {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        int toneErrorHz = CwFrontEndHealthClassifier.trackingErrorHz(snapshot);
        return "Tone: " + (snapshot.toneActive() ? getString(R.string.signal_tone_active) : getString(R.string.signal_tone_idle))
                + "\nTarget Tone Lock: " + (snapshot.targetToneLocked() ? "LOCKED" : "SEARCH")
                + "\nFront-End Class: " + CwFrontEndHealthClassifier.qualityCode(snapshot)
                + " (" + CwFrontEndHealthClassifier.qualityLabel(snapshot) + ")"
                + "\nLikely Bottleneck: " + CwFrontEndHealthClassifier.bottleneckCode(snapshot)
                + " (" + CwFrontEndHealthClassifier.bottleneckLabel(snapshot) + ")"
                + "\nPreferred Tone: " + snapshot.preferredToneFrequencyHz() + " Hz"
                + "\nTracked Tone: " + snapshot.targetToneFrequencyHz() + " Hz"
                + "\nHypothesis Tone: " + snapshot.toneHypothesisFrequencyHz()
                + " Hz | conf " + Math.round(snapshot.toneHypothesisConfidence() * 100.0d)
                + "% | src " + snapshot.toneHypothesisSource()
                + "\nHyp Guard: " + (snapshot.hypothesisGuardExperimentEnabled() ? "ON" : "OFF")
                + " | apply " + snapshot.hypothesisGuardApplyCount()
                + " | last " + snapshot.hypothesisGuardAppliedFrequencyHz()
                + " Hz | " + snapshot.hypothesisGuardDecision()
                + "\nEffective Tracked: " + renderDisplayToneWithRaw(
                snapshot.effectiveTrackedToneFrequencyHz(),
                snapshot.targetToneFrequencyHz()
        )
                + "\nPrevious Target Before Scan: " + snapshot.previousTargetBeforeScanFrequencyHz() + " Hz"
                + " | RMS " + String.format(Locale.US, "%.1f", snapshot.previousTargetBeforeScanToneRms())
                + " | score " + String.format(Locale.US, "%.1f", snapshot.previousTargetBeforeScanSelectionScore())
                + " | " + (snapshot.previousTargetBeforeScanLocked() ? "LOCK" : "cand")
                + "\nPending Retune Candidate: " + snapshot.pendingRetuneCandidateFrequencyHz()
                + " Hz"
                + " (" + snapshot.pendingRetuneCandidateStableScans() + " stable scans)"
                + "\nPreferred Window Winner: " + snapshot.preferredWindowWinnerFrequencyHz() + " Hz"
                + " | RMS " + String.format(Locale.US, "%.1f", snapshot.preferredWindowWinnerToneRms())
                + " | score " + String.format(Locale.US, "%.1f", snapshot.preferredWindowWinnerSelectionScore())
                + " | conf " + Math.round(snapshot.preferredWindowWinnerConfidence() * 100.0d) + "%"
                + " | " + (snapshot.preferredWindowWinnerLocked() ? "LOCK" : "cand")
                + "\nPreferred Runner-Up: " + (snapshot.preferredWindowRunnerUpFrequencyHz() > 0
                ? snapshot.preferredWindowRunnerUpFrequencyHz() + " Hz"
                + " | score " + String.format(Locale.US, "%.1f", snapshot.preferredWindowRunnerUpSelectionScore())
                : "not captured")
                + "\nPreferred Top Candidates: " + snapshot.preferredWindowTopCandidatesSummary()
                + "\nWide Scan Winner: " + (snapshot.wideScanWinnerFrequencyHz() > 0
                ? snapshot.wideScanWinnerFrequencyHz() + " Hz"
                + " | RMS " + String.format(Locale.US, "%.1f", snapshot.wideScanWinnerToneRms())
                + " | score " + String.format(Locale.US, "%.1f", snapshot.wideScanWinnerSelectionScore())
                + " | conf " + Math.round(snapshot.wideScanWinnerConfidence() * 100.0d) + "%"
                + " | " + (snapshot.wideScanWinnerLocked() ? "LOCK" : "cand")
                : "not used")
                + "\nWide Runner-Up: " + (snapshot.wideScanRunnerUpFrequencyHz() > 0
                ? snapshot.wideScanRunnerUpFrequencyHz() + " Hz"
                + " | score " + String.format(Locale.US, "%.1f", snapshot.wideScanRunnerUpSelectionScore())
                : "not captured")
                + "\nWide Top Candidates: " + snapshot.wideScanTopCandidatesSummary()
                + "\nAcquisition Winner: " + renderDisplayToneWithRaw(
                snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                snapshot.acquisitionWinnerFrequencyHz()
        )
                + " | RMS " + String.format(Locale.US, "%.1f", snapshot.acquisitionWinnerToneRms())
                + " | score " + String.format(Locale.US, "%.1f", snapshot.acquisitionWinnerSelectionScore())
                + " | conf " + Math.round(snapshot.acquisitionWinnerConfidence() * 100.0d) + "%"
                + " | " + snapshot.acquisitionWinnerSource()
                + "\nAcquisition Runner-Up: " + (snapshot.acquisitionRunnerUpFrequencyHz() > 0
                ? snapshot.acquisitionRunnerUpFrequencyHz() + " Hz"
                + " | score " + String.format(Locale.US, "%.1f", snapshot.acquisitionRunnerUpSelectionScore())
                : "not captured")
                + "\nAcquisition Decision: " + snapshot.acquisitionDecisionDetail()
                + "\nFinal Adopted: " + renderDisplayToneWithRaw(
                snapshot.effectiveFinalAdoptedFrequencyHz(),
                snapshot.finalAdoptedFrequencyHz()
        )
                + " | RMS " + String.format(Locale.US, "%.1f", snapshot.finalAdoptedToneRms())
                + " | score " + String.format(Locale.US, "%.1f", snapshot.finalAdoptedSelectionScore())
                + " | conf " + Math.round(snapshot.finalAdoptedConfidence() * 100.0d) + "%"
                + " | " + snapshot.finalAdoptedSource()
                + "\nFinal Adoption Detail: " + snapshot.finalAdoptionDetail()
                + "\nTracking Error: " + String.format(Locale.US, "%+d", toneErrorHz) + " Hz"
                + "\nAttack Threshold: " + snapshot.currentThreshold()
                + "\nRelease Threshold: " + snapshot.releaseThreshold()
                + "\nNoise Floor: " + snapshot.noiseFloorEstimate()
                + "\nSignal Floor: " + snapshot.signalFloorEstimate()
                + "\nLast RMS: " + String.format(Locale.US, "%.1f", snapshot.lastRmsAmplitude())
                + "\nTone RMS: " + String.format(Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                + "\nResidual RMS: " + String.format(Locale.US, "%.1f", snapshot.lastWidebandResidualRmsAmplitude())
                + "\nTone Dominance: " + Math.round(snapshot.toneDominanceRatio() * 100.0d) + "%"
                + "\nBand Isolation: " + Math.round(snapshot.narrowbandIsolationRatio() * 100.0d) + "%"
                + "\nPeak Tone RMS: " + String.format(Locale.US, "%.1f", snapshot.peakToneRmsAmplitude())
                + "\nPeak Isolation: " + Math.round(snapshot.peakNarrowbandIsolationRatio() * 100.0d) + "%"
                + "\nLock Coverage: " + Math.round(snapshot.lockedFrameRatio() * 100.0d) + "%"
                + " (" + snapshot.lockedFrameCount() + "/" + snapshot.processedFrameCount() + " frames)"
                + "\nFrame Gap Resets: " + snapshot.frameGapResetCount()
                + " | Last gap: " + snapshot.lastFrameGapMs() + " ms"
                + " | Worst gap: " + snapshot.worstFrameGapMs() + " ms"
                + "\nCurrent Lock Streak: " + snapshot.consecutiveLockedFrames() + " frame(s)"
                + "\nBest Lock Run: " + snapshot.maxConsecutiveLockedFrames() + " frame(s)"
                + "\nTone-Active Unlock: " + Math.round(snapshot.toneActiveUnlockedFrameRatio() * 100.0d) + "%"
                + " (" + snapshot.toneActiveUnlockedFrameCount() + "/" + snapshot.toneActiveFrameCount() + " active frames)"
                + "\nCurrent Active Unlock Gap: " + snapshot.consecutiveToneActiveUnlockedFrames() + " frame(s)"
                + "\nWorst Active Unlock Gap: " + snapshot.maxConsecutiveToneActiveUnlockedFrames() + " frame(s)";
    }

    private String renderDisplayToneWithRaw(int displayFrequencyHz, int rawFrequencyHz) {
        if (displayFrequencyHz <= 0) {
            return rawFrequencyHz + " Hz";
        }
        if (rawFrequencyHz <= 0 || displayFrequencyHz == rawFrequencyHz) {
            return displayFrequencyHz + " Hz";
        }
        return displayFrequencyHz + " Hz (raw " + rawFrequencyHz + " Hz)";
    }

    private String renderSignalEventStats() {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        return getString(
                R.string.signal_event_stats,
                snapshot.totalToneOnEvents(),
                snapshot.totalToneOffEvents()
        );
    }

    private String renderSignalHealthSummary() {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        int trackingErrorHz = CwFrontEndHealthClassifier.trackingErrorHz(snapshot);
        int attackHeadroom = snapshot.signalFloorEstimate() - snapshot.currentThreshold();
        int releaseMargin = snapshot.releaseThreshold() - snapshot.noiseFloorEstimate();
        double dominancePercent = snapshot.toneDominanceRatio() * 100.0d;
        double isolationPercent = snapshot.narrowbandIsolationRatio() * 100.0d;
        double peakIsolationPercent = snapshot.peakNarrowbandIsolationRatio() * 100.0d;
        double lockCoveragePercent = snapshot.lockedFrameRatio() * 100.0d;
        double toneActiveUnlockPercent = snapshot.toneActiveUnlockedFrameRatio() * 100.0d;

        return "Health: " + CwFrontEndHealthClassifier.qualityLabel(snapshot)
                + " [" + CwFrontEndHealthClassifier.qualityCode(snapshot)
                + " / " + CwFrontEndHealthClassifier.bottleneckCode(snapshot) + "]"
                + "\nReason: " + CwFrontEndHealthClassifier.reason(snapshot)
                + "\nDiagnosis: " + CwFrontEndHealthClassifier.bottleneckLabel(snapshot)
                + "\nRecent Trend: " + CwFrontEndHealthClassifier.recentTrendLabel(snapshot)
                + "\nNext Check: " + CwFrontEndHealthClassifier.liveCheckHint(snapshot)
                + "\nDominance: " + Math.round(dominancePercent) + "%"
                + " | Isolation: " + Math.round(isolationPercent) + "%"
                + " | Tone RMS: " + String.format(Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                + "\nResidual RMS: " + String.format(Locale.US, "%.1f", snapshot.lastWidebandResidualRmsAmplitude())
                + " | Attack headroom: " + String.format(Locale.US, "%+d", attackHeadroom)
                + " | Release margin: " + String.format(Locale.US, "%+d", releaseMargin)
                + "\nPeak isolation: " + Math.round(peakIsolationPercent) + "%"
                + " | Lock coverage: " + Math.round(lockCoveragePercent) + "%"
                + "\nStream continuity: resets " + snapshot.frameGapResetCount()
                + " | last gap " + snapshot.lastFrameGapMs() + " ms"
                + " | reset threshold " + snapshot.lastFrameGapResetThresholdMs() + " ms"
                + "\nWorst frame gap: " + snapshot.worstFrameGapMs() + " ms"
                + " | Last reset at: "
                + (snapshot.lastFrameGapResetAtMs() >= 0L ? snapshot.lastFrameGapResetAtMs() + " ms" : "none")
                + "\nRecent window: lock " + Math.round(snapshot.recentLockedFrameRatio() * 100.0d) + "%"
                + " | search " + Math.round(snapshot.recentSearchFrameRatio() * 100.0d) + "%"
                + " | active unlock " + Math.round(snapshot.recentActiveUnlockedFrameRatio() * 100.0d) + "%"
                + "\nRecent alignment: near-target " + Math.round(snapshot.recentNearTargetLockedFrameRatio() * 100.0d) + "%"
                + " | off-target " + Math.round(snapshot.recentFarOffTargetLockedFrameRatio() * 100.0d) + "%"
                + "\nTone-active unlock: " + Math.round(toneActiveUnlockPercent) + "%"
                + " | Worst active gap: " + snapshot.maxConsecutiveToneActiveUnlockedFrames() + " frame(s)"
                + "\nTrack offset: " + String.format(Locale.US, "%+d", trackingErrorHz) + " Hz";
    }

    private String renderLastSignalEvent() {
        CwToneEvent lastEvent = cwSignalProcessor.snapshot().lastEvent();
        if (lastEvent == null) {
            return getString(R.string.signal_last_event_empty);
        }

        String eventType = lastEvent.type() == CwToneEvent.Type.TONE_ON
                ? getString(R.string.signal_event_on)
                : getString(R.string.signal_event_off);
        return getString(
                R.string.signal_last_event_value,
                eventType,
                lastEvent.timestampMs(),
                lastEvent.toneDurationMs(),
                String.format(Locale.US, "%.1f", lastEvent.rmsAmplitude())
        );
    }

    private String renderTimingState() {
        CwTimingSnapshot snapshot = cwTimingModel.snapshot();
        return "Timing Strategy: " + cwTimingModel.debugStrategySummary()
                + "\nDot Estimate: " + snapshot.dotEstimateMs() + " ms"
                + "\nDash Estimate: " + snapshot.dashEstimateMs() + " ms"
                + "\nIntra Gap Estimate: " + snapshot.intraGapEstimateMs() + " ms"
                + "\nEstimated WPM: " + snapshot.estimatedWpm();
    }

    private String renderTimingEventStats() {
        CwTimingSnapshot snapshot = cwTimingModel.snapshot();
        return getString(
                R.string.timing_event_stats,
                snapshot.totalToneEvents(),
                snapshot.totalGapEvents()
        );
    }

    private String renderLastTimingEvent() {
        CwTimingEvent lastTimingEvent = cwTimingModel.snapshot().lastTimingEvent();
        if (lastTimingEvent == null) {
            return getString(R.string.timing_last_event_empty);
        }
        return getString(
                R.string.timing_last_event_value,
                timingKindLabel(lastTimingEvent.kind()),
                timingClassificationLabel(lastTimingEvent.classification()),
                lastTimingEvent.durationMs(),
                lastTimingEvent.dotEstimateMs()
        );
    }

    private String renderDecoderState() {
        CwDecoderSnapshot snapshot = cwDecoder.snapshot();
        String sequence = snapshot.currentSequence().isEmpty()
                ? getString(R.string.decoder_sequence_empty)
                : snapshot.currentSequence();
        return getString(R.string.decoder_state_value, sequence);
    }

    private String renderDecoderEventStats() {
        CwDecoderSnapshot snapshot = cwDecoder.snapshot();
        return getString(
                R.string.decoder_event_stats,
                snapshot.totalSymbols(),
                snapshot.totalCharacters()
        );
    }

    private String renderDecoderOutput() {
        CwDecoderSnapshot snapshot = cwDecoder.snapshot();
        if (snapshot.decodedText().isEmpty()) {
            return getString(R.string.decoder_output_empty);
        }
        return getString(R.string.decoder_output_value, snapshot.decodedText());
    }

    private String renderLastDecoderEvent() {
        CwDecodeEvent lastDecodeEvent = cwDecoder.snapshot().lastDecodeEvent();
        if (lastDecodeEvent == null) {
            return getString(R.string.decoder_last_event_empty);
        }
        return getString(
                R.string.decoder_last_event_value,
                decoderEventTypeLabel(lastDecodeEvent.type()),
                lastDecodeEvent.emittedValue(),
                lastDecodeEvent.currentSequence().isEmpty()
                        ? getString(R.string.decoder_sequence_empty)
                        : lastDecodeEvent.currentSequence(),
                lastDecodeEvent.outputText().isEmpty()
                        ? getString(R.string.decoder_output_none)
                        : lastDecodeEvent.outputText()
        );
    }

    private CharSequence renderInterpreterRawText(CwInterpreterSnapshot snapshot) {
        if (snapshot.rawText().isEmpty()) {
            return getString(R.string.interpreter_raw_empty);
        }
        return buildHighlightedInterpreterText("Raw Text: ", snapshot, false);
    }

    private CharSequence renderInterpreterNormalizedText(CwInterpreterSnapshot snapshot) {
        if (snapshot.normalizedText().isEmpty()) {
            return getString(R.string.interpreter_normalized_empty);
        }
        return buildHighlightedInterpreterText("Normalized Text: ", snapshot, true);
    }

    private String renderInterpreterCallsigns(CwInterpreterSnapshot snapshot) {
        if (snapshot.callsignCandidates().isEmpty()) {
            return getString(R.string.interpreter_callsign_empty);
        }
        String primary = snapshot.primaryCallsignCandidate() == null
                ? "(none)"
                : snapshot.primaryCallsignCandidate();
        return getString(
                R.string.interpreter_callsign_value,
                "Primary: " + primary + " | All: " + String.join(", ", snapshot.callsignCandidates())
        );
    }

    private String renderInterpreterHints(CwInterpreterSnapshot snapshot) {
        String normalizedSummary = buildNormalizationSummary(snapshot);
        if (snapshot.phraseHints().isEmpty() && normalizedSummary.isEmpty()) {
            return getString(R.string.interpreter_hints_empty);
        }
        StringBuilder builder = new StringBuilder();
        if (!snapshot.phraseHints().isEmpty()) {
            builder.append(
                    getString(
                            R.string.interpreter_hints_value,
                            String.join(" / ", snapshot.phraseHints())
                    )
            );
        }
        if (!normalizedSummary.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("Recovered/normalized: ").append(normalizedSummary);
        }
        return builder.toString();
    }

    private String renderLastInterpreterEvent() {
        CwInterpretationEvent lastEvent = cwInterpreter.snapshot().lastEvent();
        if (lastEvent == null) {
            return getString(R.string.interpreter_last_event_empty);
        }
        return getString(
                R.string.interpreter_last_event_value,
                lastEvent.latestTokenSummary(),
                lastEvent.rawText().isEmpty() ? getString(R.string.decoder_output_none) : lastEvent.rawText(),
                lastEvent.normalizedText().isEmpty() ? getString(R.string.decoder_output_none) : lastEvent.normalizedText()
        );
    }

    private CharSequence buildHighlightedInterpreterText(
            String label,
            CwInterpreterSnapshot snapshot,
            boolean useNormalizedText
    ) {
        SpannableStringBuilder builder = new SpannableStringBuilder(label);
        List<CwInterpretedToken> tokens = snapshot.tokens();
        if (tokens.isEmpty()) {
            builder.append(useNormalizedText ? snapshot.normalizedText() : snapshot.rawText());
            return builder;
        }

        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            CwInterpretedToken token = tokens.get(i);
            String displayText = useNormalizedText ? token.normalizedText() : token.rawText();
            appendHighlightedToken(builder, displayText, token, useNormalizedText);
        }
        return builder;
    }

    private void appendHighlightedToken(
            SpannableStringBuilder builder,
            String displayText,
            CwInterpretedToken token,
            boolean useNormalizedText
    ) {
        CwInterpretedToken.Type type = token.type();
        int start = builder.length();
        builder.append(displayText);
        int end = builder.length();
        if (type == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
            builder.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.cwcn_secondary)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            builder.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        } else if (type == CwInterpretedToken.Type.REPORT) {
            builder.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.cwcn_accent)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (useNormalizedText && token.normalizedFromRaw()) {
            builder.setSpan(
                    new UnderlineSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private String buildNormalizationSummary(CwInterpreterSnapshot snapshot) {
        LinkedHashSet<String> normalizedPairs = new LinkedHashSet<>();
        for (CwInterpretedToken token : snapshot.tokens()) {
            if (token.normalizedFromRaw()) {
                normalizedPairs.add(token.rawText() + "->" + token.normalizedText());
            }
        }
        return normalizedPairs.isEmpty() ? "" : String.join(" / ", normalizedPairs);
    }

    private String renderQsoPhase(QsoDraftSnapshot snapshot) {
        return getString(R.string.qso_phase_value, snapshot.phase().displayName());
    }

    private String renderQsoDraft(QsoDraftSnapshot snapshot) {
        String myCall = snapshot.stationCallsignUsed() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.stationCallsignUsed();
        String callsign = snapshot.remoteCallsignCandidate() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.remoteCallsignCandidate();
        String rstSent = snapshot.rstSentCandidate() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.rstSentCandidate();
        String rstRcvd = snapshot.rstRcvdCandidate() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.rstRcvdCandidate();
        String name = snapshot.nameCandidate() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.nameCandidate();
        String qth = snapshot.qthCandidate() == null
                ? getString(R.string.qso_draft_none)
                : snapshot.qthCandidate();
        String hints = snapshot.hints().isEmpty()
                ? getString(R.string.qso_draft_none)
                : String.join(" / ", snapshot.hints());
        return getString(R.string.qso_draft_value, myCall, callsign, rstSent, rstRcvd, name, qth, hints);
    }

    private String renderQsoReadiness(QsoDraftSnapshot snapshot) {
        return snapshot.readyForDraftConfirmation()
                ? getString(R.string.qso_ready_yes)
                : getString(R.string.qso_ready_no);
    }

    private String renderLastQsoEvent(QsoDraftSnapshot snapshot) {
        QsoStateEvent event = snapshot.lastEvent();
        if (event == null) {
            return getString(R.string.qso_event_empty);
        }
        return getString(
                R.string.qso_event_value,
                event.phase().displayName(),
                event.summary()
        );
    }

    private String renderAdifFieldMap() {
        List<String> mappings = CwAdifExporter.previewMappedFields(qsoStateMachine.snapshot());
        if (mappings.isEmpty()) {
            return getString(R.string.adif_field_map_empty);
        }
        return getString(R.string.adif_field_map_value, String.join(" / ", mappings));
    }

    private String renderAdifPreview() {
        String preview = CwAdifExporter.buildPreview(qsoStateMachine.snapshot());
        if (preview.isEmpty()) {
            return getString(R.string.adif_preview_empty);
        }
        return getString(R.string.adif_preview_value, preview);
    }

    private String renderQsoEditorStatus() {
        QsoDraftSnapshot snapshot = qsoStateMachine.snapshot();
        StringBuilder builder = new StringBuilder();
        builder.append(hasManualCorrections(snapshot) ? "Manual override active." : "Using live decoded draft.");
        if (draftEditorDirty) {
            builder.append("\nEditor has unapplied changes.");
        }
        if (!qsoEditorStatusMessage.isEmpty()) {
            builder.append("\nLast edit: ").append(qsoEditorStatusMessage);
        }
        return builder.toString();
    }

    private String renderCallsignCandidateStatus(CwInterpreterSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        if (snapshot.callsignCandidates().isEmpty()) {
            builder.append("No callsign candidates detected yet.");
        } else {
            builder.append("Primary candidate: ")
                    .append(snapshot.primaryCallsignCandidate() == null ? "(none)" : snapshot.primaryCallsignCandidate())
                    .append("\nTap a candidate to fill the remote callsign.");
        }
        if (!callsignCandidateStatusMessage.isEmpty()) {
            builder.append("\nLast candidate action: ").append(callsignCandidateStatusMessage);
        }
        return builder.toString();
    }

    private void refreshCallsignCandidateButtons(CwInterpreterSnapshot snapshot) {
        binding.callsignCandidateContainer.removeAllViews();
        List<String> candidates = snapshot.callsignCandidates();
        if (candidates.isEmpty()) {
            AppCompatButton button = createCandidateButton("(none)");
            button.setEnabled(false);
            binding.callsignCandidateContainer.addView(button);
            return;
        }

        for (String candidate : candidates) {
            AppCompatButton button = createCandidateButton(candidate);
            button.setOnClickListener(view -> applyCallsignCandidate(candidate));
            binding.callsignCandidateContainer.addView(button);
        }
    }

    private AppCompatButton createCandidateButton(String label) {
        AppCompatButton button = new AppCompatButton(this);
        button.setText(label);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMarginEnd(dpToPx(8));
        button.setLayoutParams(layoutParams);
        return button;
    }

    private void applyCallsignCandidate(String callsign) {
        syncingDraftEditor = true;
        binding.remoteCallsignEditText.setText(callsign);
        syncingDraftEditor = false;
        draftEditorDirty = true;
        callsignCandidateStatusMessage = "Filled remote callsign with " + callsign + ".";
        applyDraftEditorEdits();
    }

    private void syncDraftEditorFromSnapshot(QsoDraftSnapshot snapshot, boolean force) {
        if (!force && draftEditorDirty) {
            return;
        }
        syncingDraftEditor = true;
        binding.stationCallsignEditText.setText(valueOrEmpty(snapshot.stationCallsignUsed()));
        binding.remoteCallsignEditText.setText(valueOrEmpty(snapshot.remoteCallsignCandidate()));
        binding.rstSentEditText.setText(valueOrEmpty(snapshot.rstSentCandidate()));
        binding.rstRcvdEditText.setText(valueOrEmpty(snapshot.rstRcvdCandidate()));
        binding.nameEditText.setText(valueOrEmpty(snapshot.nameCandidate()));
        binding.qthEditText.setText(valueOrEmpty(snapshot.qthCandidate()));
        syncingDraftEditor = false;
        draftEditorDirty = false;
    }

    private void applyDraftEditorEdits() {
        qsoStateMachine.applyManualCorrections(
                normalizedEditorValue(binding.stationCallsignEditText.getText()),
                normalizedEditorValue(binding.remoteCallsignEditText.getText()),
                normalizedEditorValue(binding.rstSentEditText.getText()),
                normalizedEditorValue(binding.rstRcvdEditText.getText()),
                normalizedEditorValue(binding.nameEditText.getText()),
                normalizedEditorValue(binding.qthEditText.getText()),
                System.currentTimeMillis()
        );
        draftEditorDirty = false;
        qsoEditorStatusMessage = "Applied manual draft corrections.";
        refreshUiSnapshot();
    }

    private void resetDraftEditorEdits() {
        qsoStateMachine.clearManualCorrections(System.currentTimeMillis());
        draftEditorDirty = false;
        qsoEditorStatusMessage = "Cleared manual draft corrections.";
        syncDraftEditorFromSnapshot(qsoStateMachine.snapshot(), true);
        refreshUiSnapshot();
    }

    private String renderQsoStorageStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append("Stored draft: ");
        if (persistedDraftSnapshot != null && hasDraftContent(persistedDraftSnapshot)) {
            String label = persistedDraftSnapshot.remoteCallsignCandidate();
            if (label == null || label.isEmpty()) {
                label = persistedDraftSnapshot.phase().displayName();
            }
            builder.append(label);
        } else {
            builder.append("none");
        }
        if (!qsoStorageStatusMessage.isEmpty()) {
            builder.append("\nLast action: ").append(qsoStorageStatusMessage);
        }
        return builder.toString();
    }

    private String renderAdifExportStatus() {
        return "QSO/ADIF workflow entries are hidden for now so this screen can stay focused on RX analysis.";
    }

    private void restorePersistedDraftIfAvailable() {
        QsoDraftSnapshot storedDraft = localLogRepository.loadDraft();
        if (storedDraft == null) {
            return;
        }
        qsoStateMachine.restoreDraft(storedDraft);
        qsoStorageStatusMessage = "Restored saved draft from local storage.";
    }

    private void refreshStoredState() {
        persistedDraftSnapshot = localLogRepository.loadDraft();
        recentFixtureEvaluationResults = localLogRepository.loadRecentFixtureEvaluations(4);
    }

    private void applyDraftEditorIfNeeded() {
        if (draftEditorDirty) {
            applyDraftEditorEdits();
        }
    }

    private boolean hasDraftContent(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return (snapshot.remoteCallsignCandidate() != null && !snapshot.remoteCallsignCandidate().isEmpty())
                || (snapshot.normalizedText() != null && !snapshot.normalizedText().isEmpty())
                || snapshot.rstSentCandidate() != null
                || snapshot.rstRcvdCandidate() != null
                || snapshot.nameCandidate() != null
                || snapshot.qthCandidate() != null
                || !snapshot.hints().isEmpty();
    }

    private boolean hasConfirmableDraft(QsoDraftSnapshot snapshot) {
        return snapshot != null
                && snapshot.remoteCallsignCandidate() != null
                && !snapshot.remoteCallsignCandidate().isEmpty();
    }

    private boolean hasManualCorrections(QsoDraftSnapshot snapshot) {
        return snapshot.stationCallsignManuallySet()
                || snapshot.remoteCallsignManuallySet()
                || snapshot.rstSentManuallySet()
                || snapshot.rstRcvdManuallySet()
                || snapshot.nameManuallySet()
                || snapshot.qthManuallySet();
    }

    private boolean hasRemoteCallsignInEditor() {
        String remoteCallsign = normalizedEditorValue(binding.remoteCallsignEditText.getText());
        return remoteCallsign != null && !remoteCallsign.isEmpty();
    }

    private String normalizedEditorValue(Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void setStableText(TextView textView, CharSequence text) {
        if (textView == null) {
            return;
        }
        int viewId = textView.getId();
        Integer existingMinLines = stableTextMinLines.get(viewId);
        if (existingMinLines != null && existingMinLines > 0) {
            textView.setMinLines(existingMinLines);
        }
        textView.setText(text);
        textView.post(() -> {
            if (binding == null) {
                return;
            }
            int measuredLines = Math.max(
                    Math.max(1, textView.getLineCount()),
                    estimateMinimumStableLines(text)
            );
            Integer previousMaxLines = stableTextMinLines.get(viewId);
            if (previousMaxLines == null || measuredLines > previousMaxLines) {
                stableTextMinLines.put(viewId, measuredLines);
                textView.setMinLines(measuredLines);
            }
        });
    }

    private int estimateMinimumStableLines(CharSequence text) {
        if (text == null || text.length() == 0) {
            return 1;
        }
        int lineCount = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lineCount += 1;
            }
        }
        return lineCount;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void requestMissingPermissions() {
        List<String> missingPermissions = collectMissingPermissions();
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, R.string.permission_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                missingPermissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
        );
    }

    private List<String> collectMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (!hasRecordAudioPermission()) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                && !hasBluetoothConnectPermission()) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        return missingPermissions;
    }

    private boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String joinLabels(List<String> permissionNames) {
        StringBuilder builder = new StringBuilder();
        for (String permissionName : permissionNames) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(shortPermissionLabel(permissionName));
        }
        return builder.toString();
    }

    private String shortPermissionLabel(String permissionName) {
        if (Manifest.permission.RECORD_AUDIO.equals(permissionName)) {
            return "RECORD_AUDIO";
        }
        if (Manifest.permission.BLUETOOTH_CONNECT.equals(permissionName)) {
            return "BLUETOOTH_CONNECT";
        }
        return permissionName;
    }

    private String yesNo(boolean value) {
        return value ? getString(R.string.status_yes) : getString(R.string.status_no);
    }

    @Override
    public void onStateChanged(RxAudioSource.State state, String detail) {
        mainHandler.post(() -> {
            if (batchRunInProgress
                    && activeBatchRunItem != null
                    && (state == RxAudioSource.State.IDLE || state == RxAudioSource.State.ERROR)) {
                handleBatchRunTerminalState(state, detail);
            }
            if (fixtureReplayInProgress
                    && state == RxAudioSource.State.IDLE
                    && lastFixtureScenario != null) {
                boolean completed = detail != null && detail.toLowerCase(Locale.US).contains("completed");
                flushPendingDecodeAt(SystemClock.elapsedRealtime());
                lastFixtureEvaluationResult = CwFixtureEvaluator.evaluate(
                        lastFixtureScenario,
                        cwInterpreter.snapshot(),
                        qsoStateMachine.snapshot(),
                        cwSignalProcessor.snapshot(),
                        completed
                );
                localLogRepository.saveFixtureEvaluation(lastFixtureEvaluationResult);
                refreshStoredState();
                fixtureReplayInProgress = false;
                fixtureEvaluationStatusMessage = completed
                        ? "Fixture evaluation finished."
                        : "Fixture evaluation captured a partial run.";
            }
            if (!anySourceActive()) {
                activeRxAudioSource = null;
            }
            binding.captureStateText.setText(getString(R.string.capture_state_value, state.displayName()));
            if (detail != null && !detail.isEmpty()) {
                binding.footerText.setText(detail);
            }
            refreshUiSnapshot();
        });
    }

    private void flushPendingDecodeAt(long timestampMs) {
        List<CwTimingEvent> timingEvents = cwTimingModel.flushPendingGap(timestampMs);
        for (CwTimingEvent timingEvent : timingEvents) {
            List<CwDecodeEvent> decodeEvents = cwDecoder.process(timingEvent);
            for (CwDecodeEvent decodeEvent : decodeEvents) {
                cwInterpreter.process(decodeEvent);
                qsoInterpreter.process(decodeEvent);
                qsoStateMachine.process(qsoInterpreter.snapshot(), decodeEvent.timestampMs());
            }
        }
        List<CwDecodeEvent> trailingDecodeEvents = cwDecoder.flushPendingCharacter(timestampMs);
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            cwInterpreter.process(decodeEvent);
            qsoInterpreter.process(decodeEvent);
            qsoStateMachine.process(qsoInterpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        receivedFrameCount += 1L;
        receivedSampleCount += frame.sampleCount();
        lastPeakAmplitude = frame.peakAmplitude();
        lastRmsAmplitude = frame.rmsAmplitude();
        audioInputHealthTracker.process(frame);
        CwSignalSnapshot signalSnapshotBeforeProcess = cwSignalProcessor.snapshot();
        lastSpectrumSnapshot = audioSpectrumAnalyzer.process(
                frame,
                signalSnapshotBeforeProcess.preferredToneFrequencyHz(),
                signalSnapshotBeforeProcess.targetToneFrequencyHz(),
                signalSnapshotBeforeProcess.toneHypothesisFrequencyHz(),
                signalSnapshotBeforeProcess.preferredWindowWinnerFrequencyHz(),
                signalSnapshotBeforeProcess.wideScanWinnerFrequencyHz(),
                signalSnapshotBeforeProcess.acquisitionWinnerFrequencyHz(),
                signalSnapshotBeforeProcess.finalAdoptedFrequencyHz(),
                signalSnapshotBeforeProcess.acquisitionWinnerSource(),
                signalSnapshotBeforeProcess.finalAdoptedSource(),
                signalSnapshotBeforeProcess.hypothesisGuardExperimentEnabled(),
                signalSnapshotBeforeProcess.hypothesisGuardApplied(),
                signalSnapshotBeforeProcess.hypothesisGuardAppliedFrequencyHz(),
                signalSnapshotBeforeProcess.hypothesisGuardDecision()
        );
        List<CwToneEvent> toneEvents = cwSignalProcessor.process(frame);
        for (CwToneEvent toneEvent : toneEvents) {
            List<CwTimingEvent> timingEvents = cwTimingModel.process(toneEvent);
            for (CwTimingEvent timingEvent : timingEvents) {
                List<CwDecodeEvent> decodeEvents = cwDecoder.process(timingEvent);
                for (CwDecodeEvent decodeEvent : decodeEvents) {
                    cwInterpreter.process(decodeEvent);
                    qsoInterpreter.process(decodeEvent);
                    qsoStateMachine.process(qsoInterpreter.snapshot(), decodeEvent.timestampMs());
                }
            }
        }
        maybeFlushPendingCharacterDuringSilence(frame);
        scheduleLiveUiRefresh();
    }

    private void maybeFlushPendingCharacterDuringSilence(AudioFrame frame) {
        if (frame == null || cwDecoder == null || cwTimingModel == null || cwSignalProcessor == null) {
            return;
        }
        if (!cwDecoder.hasPendingCharacter()) {
            return;
        }

        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        if (signalSnapshot.toneActive()) {
            return;
        }
        CwToneEvent lastSignalEvent = signalSnapshot.lastEvent();
        if (lastSignalEvent == null || lastSignalEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return;
        }

        long frameDurationMs = Math.max(
                1L,
                Math.round(frame.sampleCount() * 1000.0d / frame.sampleRateHz())
        );
        long flushTimestampMs = frame.capturedAtMs() + frameDurationMs;
        long silentGapMs = Math.max(0L, flushTimestampMs - lastSignalEvent.timestampMs());
        long minFlushGapMs = minimumLiveCharacterFlushGapMs();
        if (silentGapMs < minFlushGapMs) {
            return;
        }

        List<CwDecodeEvent> trailingDecodeEvents = cwDecoder.flushPendingCharacter(flushTimestampMs);
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            cwInterpreter.process(decodeEvent);
            qsoInterpreter.process(decodeEvent);
            qsoStateMachine.process(qsoInterpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private long minimumLiveCharacterFlushGapMs() {
        CwTimingSnapshot timingSnapshot = cwTimingModel.snapshot();
        long dotEstimateMs = Math.max(1L, timingSnapshot.dotEstimateMs());
        return Math.max(
                1L,
                Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO)
        );
    }

    private void scheduleLiveUiRefresh() {
        if (!activityResumed || liveUiRefreshPosted || mainHandler == null) {
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        long delayMs = Math.max(0L, LIVE_UI_REFRESH_INTERVAL_MS - (nowMs - lastLiveUiRefreshAtMs));
        liveUiRefreshPosted = true;
        mainHandler.postDelayed(liveUiRefreshRunnable, delayMs);
    }

    private void refreshLiveUiFromPipeline() {
        liveUiRefreshPosted = false;
        if (!activityResumed || binding == null) {
            return;
        }
        lastLiveUiRefreshAtMs = SystemClock.elapsedRealtime();
        CwInterpreterSnapshot interpreterSnapshot = cwInterpreter.snapshot();
        QsoDraftSnapshot qsoSnapshot = qsoStateMachine.snapshot();
        binding.frameStatsText.setText(renderFrameStats());
        updateLevelViews(lastPeakAmplitude, lastRmsAmplitude);
        refreshAudioSpectrumView();
        setStableText(binding.signalStateText, renderSignalState());
        setStableText(binding.signalHealthText, renderSignalHealthSummary());
        setStableText(
                binding.rxFocusStatusText,
                renderRxFocusStatus(selectedInputSource(), sourceForOption(selectedInputSource()))
        );
        setStableText(binding.rxFocusDecodeText, renderRxFocusDecode(interpreterSnapshot));
        binding.signalEventStatsText.setText(renderSignalEventStats());
        binding.lastSignalEventText.setText(renderLastSignalEvent());
        binding.timingStateText.setText(renderTimingState());
        binding.timingEventStatsText.setText(renderTimingEventStats());
        binding.lastTimingEventText.setText(renderLastTimingEvent());
        binding.decoderStateText.setText(renderDecoderState());
        binding.decoderEventStatsText.setText(renderDecoderEventStats());
        setStableText(binding.decoderOutputText, renderDecoderOutput());
        binding.lastDecoderEventText.setText(renderLastDecoderEvent());
        setStableText(binding.interpreterRawText, renderInterpreterRawText(interpreterSnapshot));
        setStableText(binding.interpreterNormalizedText, renderInterpreterNormalizedText(interpreterSnapshot));
        binding.interpreterCallsignsText.setText(renderInterpreterCallsigns(interpreterSnapshot));
        binding.interpreterHintsText.setText(renderInterpreterHints(interpreterSnapshot));
        binding.lastInterpreterEventText.setText(renderLastInterpreterEvent());
        binding.qsoPhaseText.setText(renderQsoPhase(qsoSnapshot));
        setStableText(binding.qsoDraftText, renderQsoDraft(qsoSnapshot));
        binding.fixtureEvaluationText.setText(renderFixtureEvaluationStatus());
        syncDraftEditorFromSnapshot(qsoSnapshot, false);
        binding.qsoEditorStatusText.setText(renderQsoEditorStatus());
        refreshCallsignCandidateButtons(interpreterSnapshot);
        binding.callsignCandidateStatusText.setText(renderCallsignCandidateStatus(interpreterSnapshot));
        binding.qsoReadinessText.setText(renderQsoReadiness(qsoSnapshot));
        binding.lastQsoEventText.setText(renderLastQsoEvent(qsoSnapshot));
        binding.qsoStorageStatusText.setText(renderQsoStorageStatus());
        binding.adifFieldMapText.setText(renderAdifFieldMap());
        binding.adifPreviewText.setText(renderAdifPreview());
        binding.adifExportStatusText.setText(renderAdifExportStatus());
        binding.applyDraftEditsButton.setEnabled(draftEditorDirty);
        binding.resetDraftEditsButton.setEnabled(hasManualCorrections(qsoSnapshot));
        binding.rxFocusStopCaptureButton.setEnabled(anySourceActive());
        binding.runBatchAnalysisButton.setEnabled(!anySourceActive() && !batchRunInProgress);
    }

    @Override
    public void onError(String message, Throwable throwable) {
        mainHandler.post(() -> {
            if (!batchRunInProgress) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            binding.footerText.setText(message);
            if (batchRunInProgress) {
                batchRunStatusMessage = "Batch item error: " + message;
            }
            refreshUiSnapshot();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshUiSnapshot();
        }
    }

    private String timingKindLabel(CwTimingEvent.Kind kind) {
        if (kind == CwTimingEvent.Kind.TONE) {
            return getString(R.string.timing_kind_tone);
        }
        return getString(R.string.timing_kind_gap);
    }

    private String timingClassificationLabel(CwTimingEvent.Classification classification) {
        switch (classification) {
            case DIT:
                return getString(R.string.timing_class_dit);
            case DAH:
                return getString(R.string.timing_class_dah);
            case INTRA_SYMBOL_GAP:
                return getString(R.string.timing_class_intra_gap);
            case LETTER_GAP:
                return getString(R.string.timing_class_letter_gap);
            case WORD_GAP:
                return getString(R.string.timing_class_word_gap);
            default:
                return getString(R.string.timing_class_unknown);
        }
    }

    private String decoderEventTypeLabel(CwDecodeEvent.Type type) {
        switch (type) {
            case SYMBOL_APPENDED:
                return getString(R.string.decoder_event_symbol);
            case CHARACTER_DECODED:
                return getString(R.string.decoder_event_character);
            case WORD_BREAK:
                return getString(R.string.decoder_event_word_break);
            default:
                return type.name();
        }
    }

    private static final class BatchRunItem {
        private enum Kind {
            SYNTHETIC_FIXTURE,
            LOCAL_AUDIO
        }

        private final Kind kind;
        private final String label;
        private final CwFixtureScenario fixtureScenario;
        private final Uri localFileUri;

        private BatchRunItem(
                Kind kind,
                String label,
                @Nullable CwFixtureScenario fixtureScenario,
                @Nullable Uri localFileUri
        ) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.fixtureScenario = fixtureScenario;
            this.localFileUri = localFileUri;
        }

        static BatchRunItem synthetic(CwFixtureScenario scenario) {
            return new BatchRunItem(
                    Kind.SYNTHETIC_FIXTURE,
                    scenario.displayName() + " [" + scenario.id() + "]",
                    scenario,
                    null
            );
        }

        static BatchRunItem local(Uri uri, String label) {
            return new BatchRunItem(Kind.LOCAL_AUDIO, label, null, uri);
        }

        String displayLabel() {
            return label;
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
