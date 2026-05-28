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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.RxInputSettingsStore;
import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.AudioInputHealthFormatter;
import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.audio.LocalFileRxAudioSource;
import org.bi9clt.cwcn.core.audio.MicrophoneRxAudioSource;
import org.bi9clt.cwcn.core.audio.RxAudioSource;
import org.bi9clt.cwcn.core.audio.SyntheticFixtureRxAudioSource;
import org.bi9clt.cwcn.core.audio.WavReplayFrameLoader;
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
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rx.RxCommittedDecodeController;
import org.bi9clt.cwcn.core.rx.RxCommittedOutputController;
import org.bi9clt.cwcn.core.rx.RxBootstrapTimingObserver;
import org.bi9clt.cwcn.core.rx.RxCoreComponents;
import org.bi9clt.cwcn.core.rx.CwFrontEndLearningGate;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.rx.RxDeveloperStartupToneHintAnalyzer;
import org.bi9clt.cwcn.core.rx.RxFrameSignalRunner;
import org.bi9clt.cwcn.core.rx.RxPendingCharacterFlushDecider;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisResult;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisRunner;
import org.bi9clt.cwcn.core.rx.RxReplayTurnSessionController;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rx.RxStableDecodeClassifier;
import org.bi9clt.cwcn.core.rx.RxTimingDecodeRunner;
import org.bi9clt.cwcn.core.rx.RxToneTimingRunner;
import org.bi9clt.cwcn.core.rx.RxRawCommitGate;
import org.bi9clt.cwcn.core.rx.RxTrailingWindowRepair;
import org.bi9clt.cwcn.core.rx.RxTurnSessionCoordinator;
import org.bi9clt.cwcn.core.rx.RxTurnSessionFinalizer;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.rx.experimental.ExperimentalRxFrontEndPipeline;
import org.bi9clt.cwcn.core.rx.experimental.ExperimentalRxFrontEndSnapshot;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumAnalyzer;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumSnapshot;
import org.bi9clt.cwcn.core.spectrum.SpectrumHistoryStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.bi9clt.cwcn.databinding.ActivityInputDebugBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
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
import java.io.InputStream;

public final class InputDebugActivity extends AppCompatActivity implements RxAudioSource.Callback {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int AMPLITUDE_MAX = 32767;
    private static final long LIVE_UI_REFRESH_INTERVAL_MS = 120L;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final int DEFAULT_DEBUG_SEED_WPM = 18;
    private static final int LOCAL_REPLAY_ANALYSIS_PREVIEW_MAX_CHARS = 120;
    private static final int EXPERIMENTAL_RX_SQL_PERCENT = 55;
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
    private RxCoreComponents debugRxCore;
    private RxFrameSignalRunner debugFrameSignalRunner;
    private LiveRxWpmGuard debugLiveRxWpmGuard;
    private CwFrontEndLearningGate debugFrontEndLearningGate;
    private TimingAnchorController debugTimingAnchorController;
    private RxRawCommitGate debugRawCommitGate;
    private CwSignalProcessor cwSignalProcessor;
    private ExperimentalRxFrontEndPipeline experimentalRxFrontEndPipeline;
    private CwHybridTimingModel cwTimingModel;
    private RxTimingDecodeRunner debugTimingDecodeRunner;
    private RxToneTimingRunner debugToneTimingRunner;
    private RxCommittedDecodeController debugCommittedDecodeController;
    private RxCommittedOutputController debugCommittedOutputController;
    private RxTurnSessionFinalizer debugTurnSessionFinalizer;
    private RxTurnSessionCoordinator debugTurnSessionCoordinator;
    private CwDecoder cwDecoder;
    private CwInterpreter cwInterpreter;
    private CwInterpreter qsoInterpreter;
    private QsoStateMachine qsoStateMachine;
    private LocalLogRepository localLogRepository;
    private RxSessionStore rxSessionStore;
    private SpectrumHistoryStore spectrumHistoryStore;

    private long receivedFrameCount;
    private long receivedSampleCount;
    private int lastPeakAmplitude;
    private double lastRmsAmplitude;
    private int localReplayAnalysisSqlPercent = -1;
    private String localReplayAnalysisKey = "";
    private String localReplayAnalysisSummary = "";
    private long localReplayAnalysisGeneration;
    private String debugTurnLifecycleStatusMessage = "Turn: idle.";
    private String debugTurnTailRepairStatusMessage = "Tail repair: none.";
    private CwFixtureScenario lastFixtureScenario;
    private CwFixtureEvaluationResult lastFixtureEvaluationResult;
    private List<CwFixtureEvaluationResult> recentFixtureEvaluationResults = new ArrayList<>();
    private String fixtureEvaluationStatusMessage = "";
    private boolean fixtureReplayInProgress;
    private boolean hypothesisGuardExperimentEnabled;
    private boolean batchRunInProgress;
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
        microphoneRxAudioSource = new MicrophoneRxAudioSource(
                this,
                RxInputSettingsStore.MicSourceMode.UNPROCESSED
        );
        microphoneRxAudioSource.setCallback(this);
        syntheticFixtureRxAudioSource = new SyntheticFixtureRxAudioSource();
        syntheticFixtureRxAudioSource.setCallback(this);
        localFileRxAudioSource = new LocalFileRxAudioSource(this);
        localFileRxAudioSource.setCallback(this);
        audioInputHealthTracker = new AudioInputHealthTracker();
        audioSpectrumAnalyzer = new AudioSpectrumAnalyzer();
        debugRxCore = new RxCoreComponents();
        cwSignalProcessor = debugRxCore.signalProcessor();
        debugFrameSignalRunner = new RxFrameSignalRunner(
                audioInputHealthTracker,
                cwSignalProcessor
        );
        debugLiveRxWpmGuard = debugRxCore.liveRxWpmGuard();
        debugFrontEndLearningGate = debugRxCore.frontEndLearningGate();
        debugTimingAnchorController = debugRxCore.timingAnchorController();
        debugRawCommitGate = debugRxCore.rawCommitGate();
        cwSignalProcessor.setExperimentalHypothesisGuardEnabled(false);
        experimentalRxFrontEndPipeline = new ExperimentalRxFrontEndPipeline();
        cwTimingModel = debugRxCore.timingModel();
        debugRxCore.applySeedWpm(DEFAULT_DEBUG_SEED_WPM);
        debugTimingDecodeRunner = debugRxCore.timingDecodeRunner();
        debugToneTimingRunner = debugRxCore.toneTimingRunner();
        debugCommittedDecodeController = new RxCommittedDecodeController(
                cwSignalProcessor,
                cwTimingModel,
                debugLiveRxWpmGuard,
                debugRxCore.turnController(),
                debugTimingAnchorController,
                debugRawCommitGate
        );
        cwDecoder = debugRxCore.decoder();
        cwInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        qsoInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY);
        qsoStateMachine = new QsoStateMachine();
        debugCommittedOutputController = new RxCommittedOutputController(
                debugRxCore.rawInterpreter(),
                null,
                qsoInterpreter,
                qsoStateMachine,
                debugRawCommitGate
        );
        debugTurnSessionFinalizer = new RxTurnSessionFinalizer(
                debugRxCore.turnTailRepairController(),
                debugCommittedOutputController
        );
        debugTurnSessionCoordinator = new RxTurnSessionCoordinator(
                cwSignalProcessor,
                cwTimingModel,
                debugLiveRxWpmGuard,
                debugRxCore.turnController(),
                debugTimingAnchorController,
                debugRawCommitGate,
                debugTurnSessionFinalizer,
                debugRxCore.toneEventStabilizer(),
                null
        );
        localLogRepository = new LocalLogRepository(this);
        rxSessionStore = new RxSessionStore(this);
        spectrumHistoryStore = new SpectrumHistoryStore(this);
        restorePreferredToneFrequency();
        restoreSelectedLocalFile();
        consumeDeveloperTraceIntent(getIntent());
        restoreSelectedLocalFolder();
        refreshStoredState();

        binding.versionText.setText(getString(R.string.bootstrap_version, BuildConfig.VERSION_NAME));
        binding.summaryText.setText(getString(R.string.bootstrap_summary));
        binding.moduleListText.setText(renderModuleList(BootstrapRegistry.defaultModules()));
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
        consumeDeveloperTraceIntent(getIntent());
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
                hypothesisGuardExperimentEnabled
                        ? "前端实验开关：开启"
                        : "前端实验开关：关闭"
        );
    }

    private void refreshUiSnapshot() {
        InputSourceOption selectedOption = selectedInputSource();
        CwFixtureScenario selectedScenario = selectedFixtureScenario();
        RxAudioSource selectedSource = sourceForOption(selectedOption);
        CwInterpreterSnapshot interpreterSnapshot = cwInterpreter.snapshot();
        CwInterpreterSnapshot committedInterpreterSnapshot = currentCommittedInterpreterSnapshot();
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
        ensureLocalReplayAnalysis(localFileSelected);
        binding.localFileLabelText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.localFileStatusText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.localFolderStatusText.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.selectLocalFileButton.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        binding.selectLocalFolderButton.setVisibility(localFileSelected ? View.VISIBLE : View.GONE);
        setStableText(binding.localFileStatusText, renderLocalReplayStatus());
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
        setStableText(binding.rxFocusDecodeText, renderRxFocusDecode(committedInterpreterSnapshot));
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
        publishRxSessionSnapshot(interpreterSnapshot);
    }

    private void toggleDebugDetails() {
        detailedPanelsVisible = !detailedPanelsVisible;
        refreshUiSnapshot();
    }

    private void updateDebugPanelVisibility() {
        int detailedVisibility = detailedPanelsVisible ? View.VISIBLE : View.GONE;
        binding.toggleDebugDetailsButton.setText(detailedPanelsVisible
                ? "收起深度诊断面板"
                : "展开深度诊断面板");
        binding.deviceStatusPanel.setVisibility(detailedVisibility);
        binding.interpreterPanel.setVisibility(detailedVisibility);
        binding.decoderPanel.setVisibility(detailedVisibility);
        binding.timingPanel.setVisibility(detailedVisibility);
        binding.capturePanel.setVisibility(detailedVisibility);
        binding.signalPanel.setVisibility(detailedVisibility);
        binding.modulePanel.setVisibility(detailedVisibility);
        binding.toggleHypothesisGuardButton.setVisibility(detailedVisibility);
        binding.runBatchAnalysisButton.setVisibility(detailedVisibility);
        binding.batchRunStatusText.setVisibility(detailedVisibility);
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

    private String renderRxFocusDecode(@Nullable CwInterpreterSnapshot interpreterSnapshot) {
        return getString(R.string.interpreter_raw_prefix)
                + debugDisplayText(interpreterSnapshot == null ? null : interpreterSnapshot.rawText())
                + "\n"
                + getString(R.string.interpreter_normalized_prefix)
                + debugDisplayText(interpreterSnapshot == null ? null : interpreterSnapshot.normalizedText())
                + "\n"
                + getString(
                R.string.interpreter_callsign_value,
                summarizeCallsignCandidates(interpreterSnapshot)
        );
    }

    private String renderRawCommitGateSummary() {
        return (debugRawCommitGate.gateOpenInCurrentTurn() ? "已打开" : "缓冲中")
                + " | 待提交 " + debugRawCommitGate.pendingFinalEventCount();
    }

    private CwInterpreterSnapshot currentCommittedInterpreterSnapshot() {
        if (debugCommittedOutputController == null) {
            return cwInterpreter.snapshot();
        }
        CwInterpreterSnapshot snapshot = debugCommittedOutputController.rawSnapshot();
        return snapshot == null ? cwInterpreter.snapshot() : snapshot;
    }

    private QsoDraftSnapshot currentQsoSnapshot() {
        if (debugCommittedOutputController == null) {
            return qsoStateMachine.snapshot();
        }
        QsoDraftSnapshot snapshot = debugCommittedOutputController.qsoSnapshot();
        return snapshot == null ? qsoStateMachine.snapshot() : snapshot;
    }

    private boolean shouldTreatAsStableDebugDecode(@Nullable CwDecodeEvent decodeEvent) {
        if (decodeEvent == null || cwSignalProcessor == null || cwTimingModel == null) {
            return false;
        }
        AudioInputHealthSnapshot inputHealthSnapshot = audioInputHealthTracker == null
                ? null
                : audioInputHealthTracker.snapshot();
        return RxStableDecodeClassifier.passesSimpleStableDecode(
                decodeEvent,
                cwSignalProcessor.snapshot(),
                cwTimingModel.rawSnapshot(),
                inputHealthSnapshot,
                debugFrontEndLearningGate,
                debugTimingAnchorController
        );
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
        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        StringBuilder builder = new StringBuilder()
                .append("输入说明：")
                .append(option.description());
        RxAudioSource source = sourceForOption(option);
        if (source != null) {
            builder.append("\n实际来源：")
                    .append(source.displayName())
                    .append(" | 状态：")
                    .append(source.state().displayName());
        }
        if (option == InputSourceOption.SYNTHETIC_FIXTURE && scenario != null) {
            builder.append("\n夹具：").append(scenario.displayName())
                    .append(" [").append(scenario.id()).append("]")
                    .append("\n报文：").append(scenario.message())
                    .append("\n画像：")
                    .append(scenario.wpm()).append(" WPM，")
                    .append(scenario.toneFrequencyHz()).append(" Hz，噪声 ")
                    .append(scenario.noiseAmplitude())
                    .append("\n分段数：").append(scenario.messageParts().size());
            if (scenario.messageParts().size() > 1) {
                builder.append("（段间隔 ")
                        .append(scenario.interMessageGapMs())
                        .append(" ms）");
            }
            if (detailedPanelsVisible) {
                if (scenario.interfererToneAmplitude() > 0 && scenario.interfererToneFrequencyHz() > 0) {
                    builder.append("\n主干扰：")
                            .append(scenario.interfererToneFrequencyHz())
                            .append(" Hz @ ")
                            .append(scenario.interfererToneAmplitude());
                    if (Math.abs(scenario.interfererToneDriftHz()) > 0.0d) {
                        builder.append("（漂移 ")
                                .append(String.format(Locale.US, "%+.1f", scenario.interfererToneDriftHz()))
                                .append(" Hz）");
                    }
                }
                if (!scenario.additionalInterferers().isEmpty()) {
                    builder.append("\n额外干扰数：").append(scenario.additionalInterferers().size());
                }
                if (scenario.qsbDepth() > 0.0d) {
                    builder.append("\nQSB：")
                            .append(Math.round(scenario.qsbDepth() * 100.0d))
                            .append("% / ")
                            .append(scenario.qsbCycleMs())
                            .append(" ms");
                }
                if (Math.abs(scenario.toneDriftHz()) > 0.0d) {
                    builder.append("\n主音漂移：")
                            .append(String.format(Locale.US, "%+.1f", scenario.toneDriftHz()))
                            .append(" Hz");
                }
                if (scenario.riseRampMs() > 0 || scenario.fallRampMs() > 0) {
                    builder.append("\n边沿：")
                            .append(scenario.riseRampMs())
                            .append("/")
                            .append(scenario.fallRampMs())
                            .append(" ms");
                }
                builder.append("\n时序：").append(scenario.timingProfileSummary());
                if (scenario.expectedFrontEndQualityCode() != null) {
                    builder.append("\n期望前端：")
                            .append(scenario.expectedFrontEndQualityCode())
                            .append("\n实际前端：")
                            .append(renderObservedFrontEndStatus(scenario.expectedFrontEndQualityCode()));
                }
                builder.append("\n备注：").append(scenario.notes());
            }
        }
        if (option == InputSourceOption.LOCAL_FILE_REPLAY) {
            builder.append("\n文件：").append(localFileRxAudioSource.selectionSummary());
        }
        if (option == InputSourceOption.PHONE_MICROPHONE) {
            builder.append("\n输入健康：")
                    .append(AudioInputHealthFormatter.summaryLabel(audioInputHealthTracker.snapshot()));
            builder.append("\n前端状态：")
                    .append(CwFrontEndHealthClassifier.qualityCode(signalSnapshot))
                    .append(" / ")
                    .append(CwFrontEndHealthClassifier.bottleneckCode(signalSnapshot))
                    .append(" - ")
                    .append(CwFrontEndHealthClassifier.qualityLabel(signalSnapshot));
            builder.append("\n音调跟踪：").append(renderTrackedToneQuickSummary(signalSnapshot));
        }
        builder.append("\n参考音调：")
                .append(signalSnapshot.preferredToneFrequencyHz())
                .append(" Hz")
                .append("\n")
                .append(renderSourceHealthCoach(option, scenario));
        return builder.toString();
    }

    private String renderTrackedToneQuickSummary(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "尚无前端快照。";
        }
        int offsetHz = snapshot.targetToneFrequencyHz() - snapshot.preferredToneFrequencyHz();
        return (snapshot.targetToneLocked() ? "已锁定" : "搜索中")
                + "，当前 "
                + snapshot.targetToneFrequencyHz()
                + " Hz（"
                + String.format(Locale.US, "%+d", offsetHz)
                + " Hz）"
                + "，近窗口锁定 "
                + Math.round(snapshot.recentLockedFrameRatio() * 100.0d)
                + "%";
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
                builder.append("（符合预期）");
            } else {
                builder.append("（期望 ").append(expectedQualityCode).append("）");
            }
        }
        return builder.toString();
    }

    private String renderMicrophoneToneWatch() {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        int trackingErrorHz = CwFrontEndHealthClassifier.trackingErrorHz(snapshot);
        String offsetLabel;
        if (Math.abs(trackingErrorHz) <= 15) {
            offsetLabel = "接近参考音调";
        } else if (trackingErrorHz > 0) {
            offsetLabel = "高于参考音调";
        } else {
            offsetLabel = "低于参考音调";
        }
        return "麦克风前端："
                + CwFrontEndHealthClassifier.qualityCode(snapshot)
                + " / "
                + CwFrontEndHealthClassifier.bottleneckCode(snapshot)
                + " - "
                + CwFrontEndHealthClassifier.qualityLabel(snapshot)
                + "\n判断原因："
                + CwFrontEndHealthClassifier.reason(snapshot)
                + "\n当前趋势："
                + renderMicrophoneTrend(snapshot)
                + "\n音调跟踪："
                + (snapshot.targetToneLocked() ? "已锁定" : "搜索中")
                + " | 当前 "
                + snapshot.targetToneFrequencyHz()
                + " Hz ("
                + String.format(Locale.US, "%+d", trackingErrorHz)
                + " Hz, "
                + offsetLabel
                + ")"
                + "\n锁定置信：主导度 "
                + Math.round(snapshot.toneDominanceRatio() * 100.0d)
                + "%，音调 RMS "
                + String.format(Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                + "，隔离度 "
                + Math.round(snapshot.narrowbandIsolationRatio() * 100.0d)
                + "%"
                + "\n运行统计：峰值隔离度 "
                + Math.round(snapshot.peakNarrowbandIsolationRatio() * 100.0d)
                + "%，锁定覆盖率 "
                + Math.round(snapshot.lockedFrameRatio() * 100.0d)
                + "%"
                + "\n最近窗口：锁定 "
                + Math.round(snapshot.recentLockedFrameRatio() * 100.0d)
                + "%，搜索 "
                + Math.round(snapshot.recentSearchFrameRatio() * 100.0d)
                + "%，有音未锁 "
                + Math.round(snapshot.recentActiveUnlockedFrameRatio() * 100.0d)
                + "%"
                + "\n最近对准：近目标锁定 "
                + Math.round(snapshot.recentNearTargetLockedFrameRatio() * 100.0d)
                + "%，偏目标锁定 "
                + Math.round(snapshot.recentFarOffTargetLockedFrameRatio() * 100.0d)
                + "%"
                + "\n前端走势："
                + CwFrontEndHealthClassifier.recentTrendLabel(snapshot)
                + "\n历史轨迹："
                + renderRecentFrontEndHistory(snapshot)
                + "\n当前主峰候选："
                + cwSignalProcessor.debugActiveLeaderCompactSummary()
                + "\n释放观察：有音未锁 "
                + Math.round(snapshot.toneActiveUnlockedFrameRatio() * 100.0d)
                + "%，最坏连续段 "
                + snapshot.maxConsecutiveToneActiveUnlockedFrames()
                + " 帧";
    }

    private String renderMicrophoneInputHealth() {
        AudioInputHealthSnapshot snapshot = audioInputHealthTracker.snapshot();
        return "麦克风输入："
                + AudioInputHealthFormatter.summaryLabel(snapshot)
                + "\n输入窗口："
                + AudioInputHealthFormatter.compactWindowSummary(snapshot)
                + "\n输入细节：峰值 "
                + snapshot.lastPeakAmplitude()
                + "，RMS "
                + String.format(Locale.US, "%.1f", snapshot.lastRmsAmplitude())
                + "，削顶 "
                + Math.round(snapshot.lastClippedSampleRatio() * 100.0d)
                + "%"
                + "\n输入历史："
                + AudioInputHealthFormatter.stateHistory(snapshot);
    }

    private String renderSourceHealthCoach(
            InputSourceOption option,
            @Nullable CwFixtureScenario scenario
    ) {
        CwSignalSnapshot snapshot = cwSignalProcessor.snapshot();
        StringBuilder builder = new StringBuilder();
        builder.append("当前建议：");
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
            return "真机麦克风： " + AudioInputHealthFormatter.coachHint(inputSnapshot);
        }
        if (!CwFrontEndHealthClassifier.hasFrontEndHistory(snapshot)) {
            return "先启动麦克风输入，在参考音调附近给一个稳定单音，观察历史轨迹是否脱离 idle/search。";
        }
        return "真机麦克风： " + CwFrontEndHealthClassifier.liveCheckHint(snapshot);
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

    private CharSequence renderLocalReplayStatus() {
        StringBuilder builder = new StringBuilder(localFileRxAudioSource.selectionSummary());
        if (!localReplayAnalysisSummary.isEmpty()) {
            builder.append("\n\n").append(localReplayAnalysisSummary);
        } else if (localFileRxAudioSource.selectedFileUri() != null) {
            builder.append("\n\nShared replay inspection: waiting.");
        }
        return builder.toString();
    }

    private void consumeDeveloperTraceIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String traceWavPath = intent.getStringExtra(DeveloperToolsActivity.EXTRA_TRACE_WAV_FILE_PATH);
        if (traceWavPath == null || traceWavPath.trim().isEmpty()) {
            return;
        }
        File wavFile = new File(traceWavPath.trim());
        if (!wavFile.exists() || !wavFile.isFile()) {
            return;
        }
        int preferredToneFrequencyHz = intent.getIntExtra(
                DeveloperToolsActivity.EXTRA_TRACE_PREFERRED_TONE_FREQUENCY_HZ,
                -1
        );
        int sqlPercent = intent.getIntExtra(
                DeveloperToolsActivity.EXTRA_TRACE_SQL_PERCENT,
                -1
        );
        Uri uri = Uri.fromFile(wavFile);
        localFileRxAudioSource.setSelectedFile(uri, wavFile.getName());
        if (preferredToneFrequencyHz > 0) {
            cwSignalProcessor.setPreferredToneFrequencyHz(preferredToneFrequencyHz);
            binding.preferredToneFrequencyEditText.setText(String.valueOf(
                    cwSignalProcessor.snapshot().preferredToneFrequencyHz()
            ));
        }
        if (sqlPercent >= 0) {
            cwSignalProcessor.setSqlPercent(sqlPercent);
        }
        localReplayAnalysisSqlPercent = sqlPercent >= 0 ? sqlPercent : -1;
        binding.inputSourceSpinner.setSelection(InputSourceOption.LOCAL_FILE_REPLAY.ordinal());
        StringBuilder footer = new StringBuilder("已载入最近一次 Operate 现场 trace: " + wavFile.getName());
        if (preferredToneFrequencyHz > 0 || sqlPercent >= 0) {
            footer.append(" | 现场设置");
            if (preferredToneFrequencyHz > 0) {
                footer.append(" Tone ").append(cwSignalProcessor.snapshot().preferredToneFrequencyHz()).append(" Hz");
            }
            if (sqlPercent >= 0) {
                footer.append(preferredToneFrequencyHz > 0 ? " /" : "");
                footer.append(" SQL ").append(sqlPercent).append("%");
            }
        }
        binding.footerText.setText(footer.toString());
        intent.removeExtra(DeveloperToolsActivity.EXTRA_TRACE_WAV_FILE_PATH);
        intent.removeExtra(DeveloperToolsActivity.EXTRA_TRACE_PREFERRED_TONE_FREQUENCY_HZ);
        intent.removeExtra(DeveloperToolsActivity.EXTRA_TRACE_SQL_PERCENT);
    }

    private void ensureLocalReplayAnalysis(boolean localFileSelected) {
        Uri selectedFileUri = localFileSelected ? localFileRxAudioSource.selectedFileUri() : null;
        if (selectedFileUri == null) {
            if (!localReplayAnalysisKey.isEmpty() || !localReplayAnalysisSummary.isEmpty()) {
                localReplayAnalysisKey = "";
                localReplayAnalysisSummary = "";
                localReplayAnalysisGeneration += 1L;
            }
            return;
        }
        int preferredToneFrequencyHz = cwSignalProcessor.snapshot().preferredToneFrequencyHz();
        String analysisKey = buildLocalReplayAnalysisKey(
                selectedFileUri,
                localFileRxAudioSource.selectedFileLabel(),
                preferredToneFrequencyHz,
                localReplayAnalysisSqlPercent
        );
        if (analysisKey.isEmpty()) {
            if (!localReplayAnalysisKey.isEmpty() || !localReplayAnalysisSummary.isEmpty()) {
                localReplayAnalysisKey = "";
                localReplayAnalysisSummary = "";
                localReplayAnalysisGeneration += 1L;
            }
            return;
        }
        if (analysisKey.equals(localReplayAnalysisKey) && !localReplayAnalysisSummary.isEmpty()) {
            return;
        }
        localReplayAnalysisKey = analysisKey;
        localReplayAnalysisSummary = "Shared replay inspection: processing...";
        long generation = ++localReplayAnalysisGeneration;
        Uri replayFileUri = selectedFileUri;
        String replayFileLabel = localFileRxAudioSource.selectedFileLabel();
        new Thread(
                () -> runLocalReplayAnalysis(
                        replayFileUri,
                        replayFileLabel,
                        preferredToneFrequencyHz,
                        localReplayAnalysisSqlPercent,
                        analysisKey,
                        generation
                ),
                "cwcn-input-debug-replay-analysis"
        ).start();
    }

    private void runLocalReplayAnalysis(
            Uri replayFileUri,
            String replayFileLabel,
            int preferredToneFrequencyHz,
            int sqlPercent,
            String analysisKey,
            long generation
    ) {
        String summary;
        try {
            LocalReplayInspectionBundle analysisBundle = analyzeLocalReplay(
                    replayFileUri,
                    replayFileLabel,
                    preferredToneFrequencyHz,
                    sqlPercent
            );
            summary = formatLocalReplayAnalysisSummary(
                    analysisBundle.analysisResult(),
                    analysisBundle.startupToneHint()
            );
        } catch (Exception exception) {
            summary = "Shared replay inspection: failed - " + safeReplayAnalysisErrorMessage(exception);
        }
        String finalSummary = summary;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (generation != localReplayAnalysisGeneration || !analysisKey.equals(localReplayAnalysisKey)) {
                return;
            }
            localReplayAnalysisSummary = finalSummary;
            refreshUiSnapshot();
        });
    }

    private LocalReplayInspectionBundle analyzeLocalReplay(
            Uri replayFileUri,
            String replayFileLabel,
            int preferredToneFrequencyHz,
            int sqlPercent
    ) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(replayFileUri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open local replay file.");
            }
            WavReplayFrameLoader.LoadedWav loadedWav = new WavReplayFrameLoader().load(
                    inputStream,
                    replayFileLabel
            );
            return new LocalReplayInspectionBundle(
                    new RxReplayAnalysisRunner().analyze(
                            loadedWav.frames(),
                            preferredToneFrequencyHz,
                            sqlPercent,
                            DEFAULT_DEBUG_SEED_WPM
                    ),
                    new RxDeveloperStartupToneHintAnalyzer().analyze(
                            loadedWav.frames(),
                            preferredToneFrequencyHz
                    )
            );
        }
    }

    private String buildLocalReplayAnalysisKey(
            Uri replayFileUri,
            String replayFileLabel,
            int preferredToneFrequencyHz,
            int sqlPercent
    ) {
        if (replayFileUri == null) {
            return "";
        }
        return replayFileUri
                + "|"
                + nonEmpty(replayFileLabel)
                + "|"
                + preferredToneFrequencyHz
                + "|"
                + sqlPercent;
    }

    private String formatLocalReplayAnalysisSummary(
            @Nullable RxReplayAnalysisResult analysisResult,
            @Nullable RxDeveloperStartupToneHintAnalyzer.Result startupToneHint
    ) {
        if (analysisResult == null) {
            return "Shared replay inspection: no result.";
        }
        int trackedToneHz = analysisResult.signalSnapshot() == null
                ? 0
                : analysisResult.signalSnapshot().effectiveTrackedToneFrequencyHz();
        double estimatedWpm = analysisResult.timingSnapshot() == null
                ? 0.0d
                : analysisResult.timingSnapshot().estimatedWpmPrecise() > 0.0d
                ? analysisResult.timingSnapshot().estimatedWpmPrecise()
                : analysisResult.timingSnapshot().estimatedWpm();
        return "Shared replay inspection: "
                + analysisResult.processedFrameCount()
                + " frames | "
                + analysisResult.toneEventCount()
                + " tone | "
                + analysisResult.decodeEventCount()
                + " decode | "
                + analysisResult.turnCount()
                + " turns | "
                + analysisResult.tailRepairCount()
                + " repairs | tracked "
                + (trackedToneHz > 0 ? trackedToneHz + " Hz" : "-")
                + " | WPM "
                + String.format(Locale.US, "%.1f", estimatedWpm)
                + "\nDeveloper hint: "
                + formatStartupToneHint(startupToneHint)
                + "\nTurns: "
                + buildTurnWindowSummary(analysisResult.turnWindows())
                + "\nPreview: "
                + trimReplayPreview(bestReplayPreview(analysisResult));
    }

    private String formatStartupToneHint(
            @Nullable RxDeveloperStartupToneHintAnalyzer.Result startupToneHint
    ) {
        if (startupToneHint == null) {
            return "startup raw spectrum hint not executed.";
        }
        if (!startupToneHint.accepted()) {
            return "no conservative startup fixed-tone suggestion ("
                    + startupToneHint.decisionCode()
                    + ")";
        }
        return String.format(
                Locale.US,
                "try fixed tone %d Hz (startup raw spectrum only, not replay-verified; %s, %d frames)",
                startupToneHint.suggestedToneHz(),
                startupToneHint.clusterSummary(),
                startupToneHint.supportFrames()
        );
    }

    private String buildTurnWindowSummary(
            List<RxReplayTurnSessionController.TurnWindow> turnWindows
    ) {
        if (turnWindows == null || turnWindows.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(4, turnWindows.size());
        for (int index = 0; index < limit; index++) {
            RxReplayTurnSessionController.TurnWindow turnWindow = turnWindows.get(index);
            if (turnWindow == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("T")
                    .append(turnWindow.turnIndex())
                    .append(" ")
                    .append(turnWindow.turnStartTimestampMs())
                    .append("-")
                    .append(turnWindow.turnEndTimestampMs())
                    .append(" ms");
        }
        if (turnWindows.size() > limit) {
            builder.append(" | ...");
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
    }

    private String bestReplayPreview(@Nullable RxReplayAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "(empty)";
        }
        String decodedText = analysisResult.decodedText();
        if (decodedText != null && !decodedText.trim().isEmpty()) {
            return decodedText.trim();
        }
        if (analysisResult.decoderSnapshot() != null) {
            String fallback = analysisResult.decoderSnapshot().decodedText();
            if (fallback != null && !fallback.trim().isEmpty()) {
                return fallback.trim();
            }
        }
        return "(empty)";
    }

    private String trimReplayPreview(@Nullable String text) {
        if (text == null) {
            return "(empty)";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return "(empty)";
        }
        if (compact.length() <= LOCAL_REPLAY_ANALYSIS_PREVIEW_MAX_CHARS) {
            return compact;
        }
        return compact.substring(0, LOCAL_REPLAY_ANALYSIS_PREVIEW_MAX_CHARS - 1) + "…";
    }

    private String safeReplayAnalysisErrorMessage(@Nullable Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().trim().isEmpty()) {
            return "unknown error";
        }
        return exception.getMessage().trim();
    }

    private static final class LocalReplayInspectionBundle {
        private final RxReplayAnalysisResult analysisResult;
        private final RxDeveloperStartupToneHintAnalyzer.Result startupToneHint;

        private LocalReplayInspectionBundle(
                RxReplayAnalysisResult analysisResult,
                RxDeveloperStartupToneHintAnalyzer.Result startupToneHint
        ) {
            this.analysisResult = analysisResult;
            this.startupToneHint = startupToneHint;
        }

        private RxReplayAnalysisResult analysisResult() {
            return analysisResult;
        }

        private RxDeveloperStartupToneHintAnalyzer.Result startupToneHint() {
            return startupToneHint;
        }
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
        localReplayAnalysisSqlPercent = -1;
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
        if (debugRxCore != null) {
            debugRxCore.resetRuntimeState(DEFAULT_DEBUG_SEED_WPM);
        }
        cwSignalProcessor.setExperimentalHypothesisGuardEnabled(hypothesisGuardExperimentEnabled);
        experimentalRxFrontEndPipeline.reset();
        cwInterpreter.reset();
        if (debugCommittedOutputController != null) {
            debugCommittedOutputController.reset();
        } else {
            qsoInterpreter.reset();
            qsoStateMachine.reset();
        }
        debugTurnLifecycleStatusMessage = "Turn: idle.";
        debugTurnTailRepairStatusMessage = "Tail repair: none.";
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
        boolean completed = syntheticFixtureRxAudioSource != null
                && syntheticFixtureRxAudioSource.wasLastReplayCompleted();
        if (completedItem.kind == BatchRunItem.Kind.SYNTHETIC_FIXTURE && completedItem.fixtureScenario != null) {
            CwFixtureEvaluationResult evaluationResult = CwFixtureEvaluator.evaluate(
                    completedItem.fixtureScenario,
                    currentCommittedInterpreterSnapshot(),
                    currentQsoSnapshot(),
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
        finalizeDebugRunAtStop(SystemClock.elapsedRealtime());
        stopAllSources();
        experimentalRxFrontEndPipeline.reset();
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
        publishSpectrumSnapshot();
    }

    private void publishSpectrumSnapshot() {
        if (spectrumHistoryStore == null) {
            return;
        }
        SpectrumSnapshotData snapshotData = SpectrumSnapshotData.fromAudioSnapshot(
                lastSpectrumSnapshot,
                System.currentTimeMillis(),
                cwSignalProcessor == null ? null : cwSignalProcessor.snapshot()
        );
        if (snapshotData != null) {
            spectrumHistoryStore.append(snapshotData);
        }
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
                + "\nRetune Guard: " + (snapshot.lockedRetuneGuardHolding() ? "HOLD" : "OPEN")
                + " | band " + snapshot.lockedRetuneGuardBand()
                + " | cand " + (snapshot.lockedRetuneGuardCandidateFrequencyHz() > 0
                ? snapshot.lockedRetuneGuardCandidateFrequencyHz() + " Hz"
                : "none")
                + " | drift " + snapshot.lockedRetuneGuardDriftHz() + " Hz"
                + " | seen " + snapshot.lockedRetuneGuardObservedScans()
                + "/" + snapshot.lockedRetuneGuardRequiredScans()
                + " | remain " + snapshot.lockedRetuneGuardRemainingScans()
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
                + "\nWorst Active Unlock Gap: " + snapshot.maxConsecutiveToneActiveUnlockedFrames() + " frame(s)"
                + "\nTurn State: " + debugRxCore.turnController().compactDebugSummary()
                + "\nRaw Gate: " + renderRawCommitGateSummary();
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
                + "\nExperimental gate: " + renderExperimentalFrontEndDetail()
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
        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        long nowTimestampMs = SystemClock.elapsedRealtime();
        return "Timing Strategy: " + cwTimingModel.debugStrategySummary()
                + "\nDot Estimate: " + snapshot.dotEstimateMs() + " ms"
                + "\nDash Estimate: " + snapshot.dashEstimateMs() + " ms"
                + "\nIntra Gap Estimate: " + snapshot.intraGapEstimateMs() + " ms"
                + "\nEstimated WPM: " + snapshot.estimatedWpm()
                + "\nTurn: " + debugTurnLifecycleStatusMessage
                + "\nTail Repair: " + debugTurnTailRepairStatusMessage
                + "\nWPM Guard: " + debugLiveRxWpmGuard.compactDebugSummary(
                signalSnapshot,
                snapshot,
                nowTimestampMs
        )
                + "\nTiming Anchor: " + debugTimingAnchorController.compactDebugSummary();
    }

    private void traceDebugTurnFinalization(
            @Nullable RxTurnSessionFinalizer.TurnFinalization turnFinalization,
            long timestampMs,
            String reason
    ) {
        if (turnFinalization == null) {
            debugTurnTailRepairStatusMessage = "Tail repair: none.";
            return;
        }
        RxTrailingWindowRepair.RepairResult repairResult = turnFinalization.repairResult();
        debugTurnTailRepairStatusMessage = "Tail repair: "
                + safePreview(repairResult.baseTailText(), 24)
                + " -> "
                + safePreview(repairResult.repairedTailText(), 24)
                + " @"
                + timestampMs
                + "ms"
                + " (" + safeText(reason) + ")";
    }

    private String safePreview(@Nullable String value, int maxChars) {
        String text = safeText(value);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }

    private String currentTurnDebugSummary() {
        if (debugRxCore == null || debugRxCore.turnController() == null) {
            return "-";
        }
        String summary = debugRxCore.turnController().compactDebugSummary();
        return summary == null || summary.trim().isEmpty() ? "-" : summary.trim();
    }

    private String debugDisplayText(@Nullable String value) {
        String normalized = safeText(value).trim();
        return normalized.isEmpty() ? "-" : normalized;
    }

    private String summarizeCallsignCandidates(@Nullable CwInterpreterSnapshot snapshot) {
        if (snapshot == null || snapshot.callsignCandidates().isEmpty()) {
            return "-";
        }
        return getString(
                R.string.interpreter_callsign_summary,
                debugDisplayText(snapshot.primaryCallsignCandidate()),
                String.join(", ", snapshot.callsignCandidates())
        );
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
        StringBuilder builder = new StringBuilder();
        builder.append("最近 timing 事件：")
                .append(timingKindLabel(lastTimingEvent.kind()))
                .append(" / ")
                .append(timingClassificationLabel(lastTimingEvent.classification()))
                .append("\nDuration：")
                .append(lastTimingEvent.durationMs())
                .append(" ms")
                .append("\nDot Estimate：")
                .append(lastTimingEvent.dotEstimateMs())
                .append(" ms")
                .append(" / ratio ")
                .append(formatTimingRatio(lastTimingEvent.ratioToDotEstimate()))
                .append("x");
        if (lastTimingEvent.kind() == CwTimingEvent.Kind.GAP) {
            builder.append("\nIntra Gap Estimate：")
                    .append(lastTimingEvent.intraGapEstimateMs())
                    .append(" ms")
                    .append(" / ratio ")
                    .append(formatTimingRatio(lastTimingEvent.ratioToIntraGapEstimate()))
                    .append("x");
            String note = renderGapTimingNote(lastTimingEvent);
            if (!note.isEmpty()) {
                builder.append("\nNote：").append(note);
            }
        }
        return builder.toString();
    }

    private String formatTimingRatio(double ratio) {
        return String.format(Locale.US, "%.2f", ratio);
    }

    private String renderGapTimingNote(CwTimingEvent timingEvent) {
        if (timingEvent == null || timingEvent.kind() != CwTimingEvent.Kind.GAP) {
            return "";
        }
        double dotRatio = timingEvent.ratioToDotEstimate();
        double intraRatio = timingEvent.ratioToIntraGapEstimate();
        if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP) {
            if (dotRatio >= 3.60d) {
                return "Letter-gap classification, but decoder soft word-break promotion is likely.";
            }
            if (dotRatio >= 3.15d && intraRatio < 5.0d) {
                return "Borderline word-like gap blocked by weak intra-gap evidence.";
            }
            return "Stable letter-gap region under current timing estimates.";
        }
        if (timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP) {
            if (dotRatio < 4.35d) {
                return "Word-gap classification relies on strong intra-gap evidence, not just raw dot ratio.";
            }
            return "Clear word-gap region under current timing estimates.";
        }
        if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return "Short gap kept inside one Morse character.";
        }
        if (timingEvent.classification() == CwTimingEvent.Classification.UNKNOWN) {
            return "Gap fell outside the expected timing buckets.";
        }
        return "";
    }

    private String renderDecoderState() {
        CwDecoderSnapshot snapshot = cwDecoder.snapshot();
        String sequence = snapshot.currentSequence().isEmpty()
                ? getString(R.string.decoder_sequence_empty)
                : snapshot.currentSequence();
        return getString(
                R.string.decoder_state_value,
                currentTurnDebugSummary(),
                renderRawCommitGateSummary(),
                sequence
        );
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
        return buildHighlightedInterpreterText(getString(R.string.interpreter_raw_prefix), snapshot, false);
    }

    private CharSequence renderInterpreterNormalizedText(CwInterpreterSnapshot snapshot) {
        if (snapshot.normalizedText().isEmpty()) {
            return getString(R.string.interpreter_normalized_empty);
        }
        return buildHighlightedInterpreterText(getString(R.string.interpreter_normalized_prefix), snapshot, true);
    }

    private String renderInterpreterCallsigns(CwInterpreterSnapshot snapshot) {
        if (snapshot.callsignCandidates().isEmpty()) {
            return getString(R.string.interpreter_callsign_empty);
        }
        return getString(
                R.string.interpreter_callsign_value,
                summarizeCallsignCandidates(snapshot)
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
            builder.append(getString(R.string.interpreter_normalized_summary_value, normalizedSummary));
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

    private void refreshStoredState() {
        recentFixtureEvaluationResults = localLogRepository.loadRecentFixtureEvaluations(4);
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
            if (state == RxAudioSource.State.IDLE || state == RxAudioSource.State.ERROR) {
                finalizeDebugRunAtStop(SystemClock.elapsedRealtime());
            }
            if (batchRunInProgress
                    && activeBatchRunItem != null
                    && (state == RxAudioSource.State.IDLE || state == RxAudioSource.State.ERROR)) {
                handleBatchRunTerminalState(state, detail);
            }
            if (fixtureReplayInProgress
                    && state == RxAudioSource.State.IDLE
                    && lastFixtureScenario != null) {
                boolean completed = syntheticFixtureRxAudioSource != null
                        && syntheticFixtureRxAudioSource.wasLastReplayCompleted();
                lastFixtureEvaluationResult = CwFixtureEvaluator.evaluate(
                        lastFixtureScenario,
                        currentCommittedInterpreterSnapshot(),
                        currentQsoSnapshot(),
                        cwSignalProcessor.snapshot(),
                        completed
                );
                localLogRepository.saveFixtureEvaluation(lastFixtureEvaluationResult);
                refreshStoredState();
                fixtureReplayInProgress = false;
                fixtureEvaluationStatusMessage = completed
                        ? "夹具评估已完成。"
                        : "夹具评估记录了一次未完整结束的回放。";
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
        if (debugTimingDecodeRunner != null) {
            CwSignalSnapshot currentSignalSnapshot = cwSignalProcessor == null
                    ? null
                    : cwSignalProcessor.snapshot();
            CwTimingSnapshot currentTimingSnapshot = cwTimingModel == null
                    ? null
                    : cwTimingModel.rawSnapshot();
            boolean allowTimingLearning = shouldAllowDebugTimingLearning(
                    currentSignalSnapshot,
                    currentTimingSnapshot,
                    timestampMs
            );
            List<CwTimingEvent> timingEvents = cwTimingModel.flushPendingGap(
                    timestampMs,
                    allowTimingLearning
            );
            currentSignalSnapshot = cwSignalProcessor == null
                    ? null
                    : cwSignalProcessor.snapshot();
            currentTimingSnapshot = cwTimingModel.rawSnapshot();
            AudioInputHealthSnapshot inputHealthSnapshot = audioInputHealthTracker == null
                    ? null
                    : audioInputHealthTracker.snapshot();
            CwSignalSnapshot finalSignalSnapshot = currentSignalSnapshot;
            CwTimingSnapshot finalTimingSnapshot = currentTimingSnapshot;
            debugTimingDecodeRunner.dispatchTimingEvents(
                    timingEvents,
                    timingEvent -> prepareDebugTimingEventForDecode(
                            timingEvent,
                            finalSignalSnapshot,
                            finalTimingSnapshot,
                            inputHealthSnapshot
                    ),
                    this::consumeDebugDecodeEvent
            );
            debugTimingDecodeRunner.flushPendingCharacter(
                    timestampMs,
                    this::consumeDebugDecodeEvent
            );
        }
    }

    private void finalizeDebugRunAtStop(long timestampMs) {
        flushPendingDecodeAt(timestampMs);
        if (debugTurnSessionFinalizer != null) {
            traceDebugTurnFinalization(
                    debugTurnSessionFinalizer.finalizeCurrentTurn(timestampMs),
                    timestampMs,
                    "stop"
            );
            debugTurnSessionFinalizer.endTurn();
        }
    }

    private void consumeDebugDecodeEvent(CwDecodeEvent decodeEvent) {
        if (decodeEvent == null) {
            return;
        }
        cwInterpreter.process(decodeEvent);
        if (debugCommittedDecodeController == null || debugTurnSessionFinalizer == null) {
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                    && !decodeEvent.unknownCharacter()) {
                cwTimingModel.notifyStableDecode(decodeEvent.timestampMs());
            }
            qsoInterpreter.process(decodeEvent);
            qsoStateMachine.process(qsoInterpreter.snapshot(), decodeEvent.timestampMs());
            return;
        }
        boolean stableDecodeAccepted = shouldTreatAsStableDebugDecode(decodeEvent);
        List<CwDecodeEvent> admittedEvents = debugCommittedDecodeController.admit(
                decodeEvent,
                stableDecodeAccepted
        );
        for (CwDecodeEvent admittedEvent : admittedEvents) {
            debugTurnSessionFinalizer.processCommittedDecodeEvent(admittedEvent);
        }
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        if (frame == null || debugFrameSignalRunner == null) {
            return;
        }
        receivedFrameCount += 1L;
        receivedSampleCount += frame.sampleCount();
        lastPeakAmplitude = frame.peakAmplitude();
        lastRmsAmplitude = frame.rmsAmplitude();
        RxFrameSignalRunner.Result frameSignalResult = debugFrameSignalRunner.processFrame(
                frame,
                SystemClock.elapsedRealtime()
        );
        if (frameSignalResult == null) {
            return;
        }
        AudioInputHealthSnapshot inputHealthSnapshot = frameSignalResult.inputHealthSnapshot();
        CwSignalSnapshot signalSnapshotBeforeProcess = frameSignalResult.signalSnapshotBeforeProcess();
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
        feedExperimentalFrontEnd(frame, inputHealthSnapshot);
        maybeHandleDebugTurnTransition(
                frameSignalResult.signalSnapshotAfterProcess(),
                frameSignalResult.frameEndTimestampMs()
        );
        if (debugTurnSessionFinalizer != null) {
            for (CwToneEvent toneEvent : frameSignalResult.toneEvents()) {
                debugTurnSessionFinalizer.noteToneEvent(toneEvent);
            }
        }
        if (debugToneTimingRunner != null) {
            final CwSignalSnapshot[] finalSignalSnapshot = new CwSignalSnapshot[1];
            final CwTimingSnapshot[] finalTimingSnapshot = new CwTimingSnapshot[1];
            debugToneTimingRunner.dispatchToneEvents(
                    frameSignalResult.toneEvents(),
                    toneEvent -> {
                        CwSignalSnapshot currentSignalSnapshot = cwSignalProcessor.snapshot();
                        CwTimingSnapshot currentTimingSnapshot = cwTimingModel.rawSnapshot();
                        boolean allowTimingLearning = shouldAllowDebugTimingLearningForEvent(
                                toneEvent,
                                currentSignalSnapshot,
                                currentTimingSnapshot,
                                inputHealthSnapshot
                        );
                        List<CwTimingEvent> timingEvents = cwTimingModel.process(
                                toneEvent,
                                allowTimingLearning
                        );
                        finalSignalSnapshot[0] = cwSignalProcessor.snapshot();
                        finalTimingSnapshot[0] = cwTimingModel.rawSnapshot();
                        return timingEvents;
                    },
                    null,
                    timingEvent -> prepareDebugTimingEventForDecode(
                            timingEvent,
                            finalSignalSnapshot[0],
                            finalTimingSnapshot[0],
                            inputHealthSnapshot
                    ),
                    this::consumeDebugDecodeEvent
            );
        }
        maybeFlushPendingCharacterDuringSilence(frame);
        scheduleLiveUiRefresh();
    }

    private void maybeFlushPendingCharacterDuringSilence(AudioFrame frame) {
        if (frame == null
                || debugTimingDecodeRunner == null
                || cwTimingModel == null
                || cwSignalProcessor == null
                || !debugTimingDecodeRunner.hasPendingCharacter()) {
            return;
        }
        long flushTimestampMs = estimateFrameEndTimestampMs(frame);
        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        maybeHandleDebugTurnTransition(signalSnapshot, flushTimestampMs);
        RxPendingCharacterFlushDecider.Decision flushDecision =
                RxPendingCharacterFlushDecider.evaluate(
                        frame,
                        flushTimestampMs,
                        signalSnapshot,
                        minimumLiveCharacterFlushGapMs(flushTimestampMs),
                        RxPendingCharacterFlushDecider.ActivityPolicy.MEANINGFUL_TURN_ACTIVITY
                );
        if (!flushDecision.shouldFlush()) {
            return;
        }
        debugTimingDecodeRunner.flushPendingCharacter(
                flushDecision.flushTimestampMs(),
                this::consumeDebugDecodeEvent
        );
    }

    private boolean shouldAllowDebugTimingLearning(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timelineTimestampMs
    ) {
        if (debugFrontEndLearningGate != null
                && !debugFrontEndLearningGate.shouldAllowTimingLearning(
                signalSnapshot,
                audioInputHealthTracker == null ? null : audioInputHealthTracker.snapshot()
        )) {
            return false;
        }
        long nowTimestampMs = timelineTimestampMs > 0L
                ? timelineTimestampMs
                : SystemClock.elapsedRealtime();
        boolean baseAllow = debugLiveRxWpmGuard == null
                || debugLiveRxWpmGuard.shouldAllowTimingLearning(
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs
        );
        return debugTimingAnchorController == null
                || debugTimingAnchorController.shouldAllowTimingLearning(
                signalSnapshot,
                timingSnapshot,
                baseAllow,
                nowTimestampMs
        );
    }

    private boolean shouldAllowDebugTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        boolean trustedTimingEstablished = debugTimingAnchorController != null
                && debugTimingAnchorController.trustedDotEstimateMs() > 0L;
        if (debugFrontEndLearningGate != null
                && !debugFrontEndLearningGate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                trustedTimingEstablished
        )) {
            return false;
        }
        long nowTimestampMs = toneEvent == null
                ? SystemClock.elapsedRealtime()
                : toneEvent.timestampMs();
        boolean baseAllow = debugLiveRxWpmGuard == null
                || debugLiveRxWpmGuard.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs
        );
        return debugTimingAnchorController == null
                || debugTimingAnchorController.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                baseAllow,
                nowTimestampMs
        );
    }

    @Nullable
    private CwTimingEvent prepareDebugTimingEventForDecode(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot currentSignalSnapshot,
            @Nullable CwTimingSnapshot currentTimingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (timingEvent == null) {
            return null;
        }
        RxBootstrapTimingObserver.maybeNoteBootstrapCadenceObservation(
                timingEvent,
                currentSignalSnapshot,
                currentTimingSnapshot,
                inputHealthSnapshot,
                cwTimingModel,
                debugLiveRxWpmGuard,
                debugTimingAnchorController,
                debugFrontEndLearningGate
        );
        RxBootstrapTimingObserver.maybeNoteBootstrapTimingBoundary(
                timingEvent,
                currentSignalSnapshot,
                currentTimingSnapshot,
                inputHealthSnapshot,
                cwTimingModel,
                debugLiveRxWpmGuard,
                debugTimingAnchorController,
                debugFrontEndLearningGate,
                debugRxCore.turnController()
        );
        if (debugRawCommitGate != null) {
            debugRawCommitGate.noteTimingEvent(
                    timingEvent,
                    debugTimingAnchorController != null
                            && debugTimingAnchorController.trustedDotEstimateMs() > 0L,
                    debugTimingAnchorController == null
                            ? TimingAnchorController.TrustOrigin.NONE
                            : debugTimingAnchorController.trustOrigin(),
                    debugTimingAnchorController == null
                            ? 0L
                            : debugTimingAnchorController.trustedDotEstimateMs(),
                    debugTimingAnchorController == null
                            ? -1L
                            : debugTimingAnchorController.lastTrustedUpdateTimestampMs()
            );
        }
        return adaptDebugTimingEvent(
                timingEvent,
                currentSignalSnapshot,
                currentTimingSnapshot
        );
    }

    @Nullable
    private CwTimingEvent adaptDebugTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        long nowTimestampMs = timingEvent == null
                ? SystemClock.elapsedRealtime()
                : timingEvent.timestampMs();
        CwTimingEvent adaptedTimingEvent = timingEvent;
        if (debugLiveRxWpmGuard != null) {
            adaptedTimingEvent = debugLiveRxWpmGuard.adaptTimingEvent(
                    adaptedTimingEvent,
                    signalSnapshot,
                    timingSnapshot,
                    nowTimestampMs
            );
        }
        if (debugTimingAnchorController != null) {
            adaptedTimingEvent = debugTimingAnchorController.adaptTimingEvent(
                    adaptedTimingEvent,
                    signalSnapshot,
                    timingSnapshot,
                    nowTimestampMs
            );
        }
        return adaptedTimingEvent;
    }

    private void maybeHandleDebugTurnTransition(
            @Nullable CwSignalSnapshot signalSnapshot,
            long timestampMs
    ) {
        if (debugTurnSessionCoordinator == null
                || cwTimingModel == null
                || signalSnapshot == null) {
            return;
        }
        CwTimingSnapshot timingSnapshot = cwTimingModel.rawSnapshot();
        boolean hasPendingCharacter = debugTimingDecodeRunner != null
                ? debugTimingDecodeRunner.hasPendingCharacter()
                : cwDecoder != null && cwDecoder.hasPendingCharacter();
        RxTurnSessionCoordinator.Observation observation = debugTurnSessionCoordinator.observe(
                signalSnapshot,
                hasPendingCharacter,
                timestampMs,
                resolveDebugTimingReferenceWpm(timingSnapshot)
        );
        if (observation.startedNewTurn()) {
            debugTurnLifecycleStatusMessage = "Turn start: " + observation.reason()
                    + " | seed=" + observation.turnSeedWpm();
        } else if (observation.endedTurn()) {
            debugTurnLifecycleStatusMessage = "Turn end: " + observation.reason()
                    + (observation.frontEndResetApplied() ? " | front-end reset" : "");
            traceDebugTurnFinalization(
                    observation.turnFinalization(),
                    timestampMs,
                    observation.reason()
            );
        }
        if (observation.endedTurn() && observation.frontEndResetApplied()) {
            experimentalRxFrontEndPipeline.reset();
        }
    }

    private int resolveDebugTimingReferenceWpm(@Nullable CwTimingSnapshot timingSnapshot) {
        if (timingSnapshot == null) {
            return 0;
        }
        if (timingSnapshot.estimatedWpm() > 0) {
            return timingSnapshot.estimatedWpm();
        }
        return (int) Math.round(Math.max(0.0d, timingSnapshot.estimatedWpmPrecise()));
    }

    private long minimumLiveCharacterFlushGapMs(long timelineTimestampMs) {
        if (cwTimingModel == null) {
            return 1L;
        }
        CwSignalSnapshot signalSnapshot = cwSignalProcessor == null
                ? null
                : cwSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = cwTimingModel.rawSnapshot();
        long dotEstimateMs = debugLiveRxWpmGuard == null
                ? Math.max(1L, timingSnapshot.dotEstimateMs())
                : Math.max(1L, debugLiveRxWpmGuard.resolveEffectiveDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                timelineTimestampMs > 0L ? timelineTimestampMs : SystemClock.elapsedRealtime()
        ));
        return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
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
        CwInterpreterSnapshot committedInterpreterSnapshot = currentCommittedInterpreterSnapshot();
        binding.frameStatsText.setText(renderFrameStats());
        updateLevelViews(lastPeakAmplitude, lastRmsAmplitude);
        refreshAudioSpectrumView();
        setStableText(binding.signalStateText, renderSignalState());
        setStableText(binding.signalHealthText, renderSignalHealthSummary());
        setStableText(
                binding.rxFocusStatusText,
                renderRxFocusStatus(selectedInputSource(), sourceForOption(selectedInputSource()))
        );
        setStableText(binding.rxFocusDecodeText, renderRxFocusDecode(committedInterpreterSnapshot));
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
        binding.fixtureEvaluationText.setText(renderFixtureEvaluationStatus());
        binding.rxFocusStopCaptureButton.setEnabled(anySourceActive());
        binding.runBatchAnalysisButton.setEnabled(!anySourceActive() && !batchRunInProgress);
        publishRxSessionSnapshot(committedInterpreterSnapshot);
    }

    private void feedExperimentalFrontEnd(
            AudioFrame frame,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (frame == null || experimentalRxFrontEndPipeline == null || cwSignalProcessor == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        long timestampMs = estimateFrameEndTimestampMs(frame);
        experimentalRxFrontEndPipeline.process(
                signalSnapshot,
                inputHealthSnapshot,
                EXPERIMENTAL_RX_SQL_PERCENT,
                timestampMs
        );
    }

    private String renderExperimentalFrontEndDetail() {
        if (experimentalRxFrontEndPipeline == null) {
            return "not available";
        }
        ExperimentalRxFrontEndSnapshot snapshot = experimentalRxFrontEndPipeline.snapshot();
        if (snapshot == null || snapshot.lastObservation() == null) {
            return "waiting for frame observations";
        }
        return "state " + snapshot.envelopeState()
                + " | pressure "
                + Math.round(snapshot.admissionPressure() * 100.0d)
                + "%"
                + " | pendingResume "
                + yesNo(snapshot.pendingResume())
                + " | track "
                + snapshot.lastObservation().trackedToneFrequencyHz()
                + " Hz"
                + " | tone/noise "
                + String.format(
                Locale.US,
                "%.2f",
                snapshot.lastObservation().noiseFloorEstimate() <= 0.0d
                        ? 0.0d
                        : snapshot.lastObservation().trackedToneRmsAmplitude()
                        / snapshot.lastObservation().noiseFloorEstimate()
        )
                + "x"
                + " | lock "
                + Math.round(snapshot.lastObservation().recentLockedFrameRatio() * 100.0d)
                + "%"
                + " | near "
                + Math.round(snapshot.lastObservation().recentNearTargetLockedFrameRatio() * 100.0d)
                + "%"
                + " | unl "
                + Math.round(snapshot.lastObservation().recentActiveUnlockedFrameRatio() * 100.0d)
                + "%";
    }

    private long estimateFrameEndTimestampMs(@Nullable AudioFrame frame) {
        return RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(
                frame,
                SystemClock.elapsedRealtime()
        );
    }

    private void publishRxSessionSnapshot(CwInterpreterSnapshot interpreterSnapshot) {
        if (rxSessionStore == null) {
            return;
        }
        InputSourceOption selectedOption = selectedInputSource();
        RxAudioSource selectedSource = sourceForOption(selectedOption);
        CwSignalSnapshot signalSnapshot = cwSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = cwTimingModel.snapshot();
        AudioInputHealthSnapshot inputHealthSnapshot = audioInputHealthTracker == null
                ? null
                : audioInputHealthTracker.snapshot();
        String primaryCallsignCandidate = interpreterSnapshot == null
                ? ""
                : safeText(interpreterSnapshot.primaryCallsignCandidate());
        rxSessionStore.save(new RxSessionSnapshot(
                System.currentTimeMillis(),
                selectedOption == null ? "" : selectedOption.toString(),
                selectedSource == null ? RxAudioSource.State.IDLE.displayName() : selectedSource.state().displayName(),
                anySourceActive(),
                signalSnapshot.preferredToneFrequencyHz(),
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.effectiveTrackedToneFrequencyHz(),
                timingSnapshot.estimatedWpm(),
                timingSnapshot.estimatedWpm(),
                interpreterSnapshot == null ? "" : interpreterSnapshot.rawText(),
                interpreterSnapshot == null ? "" : interpreterSnapshot.rawText(),
                interpreterSnapshot == null ? "" : interpreterSnapshot.normalizedText(),
                primaryCallsignCandidate,
                inputHealthSnapshot == null ? "" : AudioInputHealthFormatter.summaryLabel(inputHealthSnapshot),
                inputHealthSnapshot == null ? "" : AudioInputHealthFormatter.coachHint(inputHealthSnapshot),
                inputHealthSnapshot != null && inputHealthSnapshot.recentHotFrameRatio() >= 0.50d,
                inputHealthSnapshot != null && inputHealthSnapshot.recentClippingFrameRatio() >= 0.10d,
                currentTurnDebugSummary(),
                renderRawCommitGateSummary(),
                renderExperimentalFrontEndDetail()
        ));
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
}
