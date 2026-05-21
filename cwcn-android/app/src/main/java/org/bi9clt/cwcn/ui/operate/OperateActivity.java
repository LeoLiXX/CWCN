package org.bi9clt.cwcn.ui.operate;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.RouteFallbackStore;
import org.bi9clt.cwcn.core.app.RxInputSettingsStore;
import org.bi9clt.cwcn.core.app.SqlLevelStore;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.app.TxTemplateStore;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.audio.AudioInputHealthFormatter;
import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.MicrophoneRxAudioSource;
import org.bi9clt.cwcn.core.audio.RxAudioSource;
import org.bi9clt.cwcn.core.audio.UsbExternalRxAudioSource;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.bi9clt.cwcn.core.qso.QsoStateEvent;
import org.bi9clt.cwcn.core.rx.LiveRxTraceRecorder;
import org.bi9clt.cwcn.core.rx.LiveRxTraceStore;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.rx.LiveRxToneEventStabilizer;
import org.bi9clt.cwcn.core.rx.CwFrontEndLearningGate;
import org.bi9clt.cwcn.core.rx.RxCoreComponents;
import org.bi9clt.cwcn.core.rx.RxFrameSignalRunner;
import org.bi9clt.cwcn.core.rx.RxPendingCharacterFlushDecider;
import org.bi9clt.cwcn.core.rx.RxStableDecodeDecider;
import org.bi9clt.cwcn.core.rx.RxTimingDecodeRunner;
import org.bi9clt.cwcn.core.rx.RxToneTimingRunner;
import org.bi9clt.cwcn.core.rx.RxToneModeBootstrapDecider;
import org.bi9clt.cwcn.core.rx.RxTurnActivityDecider;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rx.RxRawCommitGate;
import org.bi9clt.cwcn.core.rx.RxCommittedDecodeController;
import org.bi9clt.cwcn.core.rx.RxCommittedOutputController;
import org.bi9clt.cwcn.core.rx.RxBootstrapTimingObserver;
import org.bi9clt.cwcn.core.rx.RxStableDecodeClassifier;
import org.bi9clt.cwcn.core.rx.RxTrailingWindowRepair;
import org.bi9clt.cwcn.core.rx.RxTurnController;
import org.bi9clt.cwcn.core.rx.RxTurnSessionCoordinator;
import org.bi9clt.cwcn.core.rx.RxTurnSessionFinalizer;
import org.bi9clt.cwcn.core.rx.RxUnknownFallbackSuggestion;
import org.bi9clt.cwcn.core.rx.RxUnknownFallbackTracker;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.rig.AudioVoxRigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRouteStatusFormatter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumAnalyzer;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumSnapshot;
import org.bi9clt.cwcn.core.spectrum.SpectrumHistoryStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
import org.bi9clt.cwcn.core.spectrum.SqlThresholdAdvisor;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxState;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.bi9clt.cwcn.databinding.ActivityOperateBinding;
import org.bi9clt.cwcn.ui.navigation.FormalBottomNavStyler;
import org.bi9clt.cwcn.ui.qso.QsoEditorActivity;
import org.bi9clt.cwcn.ui.qso.QsoLogbookActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;
import org.bi9clt.cwcn.ui.settings.SettingsActivity;
import org.bi9clt.cwcn.ui.spectrum.SpectrumActivity;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import android.widget.Toast;

public final class OperateActivity extends AppCompatActivity implements RxAudioSource.Callback {
    private static final String ACTION_USB_KEYER_PERMISSION =
            "org.bi9clt.cwcn.action.OPERATE_USB_KEYER_PERMISSION";
    private static final long LIVE_RX_REFRESH_INTERVAL_MS = 700L;
    private static final long OPERATE_RX_PUBLISH_INTERVAL_MS = 450L;
    private static final long TX_PROGRESS_REFRESH_INTERVAL_MS = 120L;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final long OPERATE_RX_TIMING_COOLDOWN_RESET_MS = 2200L;
    private static final long OPERATE_RX_STABLE_DECODE_IDLE_RESET_MS = 4200L;
    private static final long OPERATE_RX_FRONTEND_IDLE_RESET_MS = 2800L;
    private static final int OPERATE_RX_WPM_RESET_DELTA = 3;
    private static final double OPERATE_RX_LOW_LOCKED_RATIO_MAX = 0.20d;
    private static final double OPERATE_RX_HIGH_UNLOCKED_RATIO_MIN = 0.55d;
    private static final double OPERATE_RX_SHORT_TONE_MAX_DOT_RATIO = 0.52d;
    private static final double OPERATE_RX_SHORT_TONE_MAX_ABSOLUTE_MS = 30.0d;
    private static final float SIDE_RAIL_DRAG_THRESHOLD_PX = 12f;
    public static final String EXTRA_START_OVERLAY = "org.bi9clt.cwcn.ui.operate.extra.START_OVERLAY";
    public static final String START_OVERLAY_CHART = "chart";
    private static final String PREFS_OPERATE_UI = "operate_ui";
    private static final String PREF_SIDE_RAIL_X = "side_rail_x";
    private static final String PREF_SIDE_RAIL_Y = "side_rail_y";
    private static final String PREF_MIC_PERMISSION_ASKED = "mic_permission_asked";
    private static final String PREF_TRANSCRIPT_USE_UTC = "transcript_use_utc";
    private static final String STATE_TRANSCRIPT_TIMELINE_ACTIVE = "transcript_timeline_active";
    private static final String STATE_TRANSCRIPT_NEXT_ID = "transcript_next_id";
    private static final String STATE_TRANSCRIPT_USE_UTC = "transcript_use_utc_state";
    private static final String STATE_TRANSCRIPT_ENTRIES = "transcript_entries";
    private static final String STATE_TRANSCRIPT_ENTRY_ID = "id";
    private static final String STATE_TRANSCRIPT_ENTRY_TYPE = "type";
    private static final String STATE_TRANSCRIPT_ENTRY_STARTED_AT = "started_at";
    private static final String STATE_TRANSCRIPT_ENTRY_UPDATED_AT = "updated_at";
    private static final String STATE_TRANSCRIPT_ENTRY_SOURCE = "source";
    private static final String STATE_TRANSCRIPT_ENTRY_STATE = "state";
    private static final String STATE_TRANSCRIPT_ENTRY_BODY = "body";
    private static final String STATE_TRANSCRIPT_ENTRY_PROGRESS = "progress";
    private static final String STATE_TRANSCRIPT_ENTRY_ACTIVE = "active";
    private static final String STATE_TRANSCRIPT_ENTRY_CALLSIGNS = "callsigns";
    private static final String STATE_TRANSCRIPT_ENTRY_TONE_HZ = "tone_hz";
    private static final String STATE_TRANSCRIPT_ENTRY_WPM = "wpm";
    private static final String STATE_SELECTED_OPERATE_REMOTE_CALLSIGN =
            "selected_operate_remote_callsign";
    private static final String TEMPLATE_CQ = "CQ";
    private static final String TEMPLATE_REPLY = "应答";
    private static final String TEMPLATE_QRZ = "QRZ";
    private static final String TEMPLATE_TU73 = "TU73";
    private static final boolean OPERATE_RX_RAW_ONLY_MODE = true;
    private static final String[] TEMPLATE_OPTIONS = {
            TEMPLATE_CQ,
            TEMPLATE_REPLY,
            TEMPLATE_QRZ,
            TEMPLATE_TU73
    };
    private static WeakReference<OperateActivity> sharedActiveInstance =
            new WeakReference<>(null);

    private enum OverlayMode {
        NONE,
        CHART,
        SQL,
        TEMPLATE
    }

    private enum TranscriptEntryType {
        RX,
        TX
    }

    private static final class StreamEntry {
        private final String label;
        private final String headline;
        private final String meta;
        private final CharSequence body;
        private final int cardBackgroundRes;
        private final int labelColorRes;
        private final boolean outgoing;
        private final boolean compact;
        private final boolean active;
        private final String qsoCommentSeed;

        private StreamEntry(
                String label,
                String headline,
                String meta,
                CharSequence body,
                int cardBackgroundRes,
                int labelColorRes,
                boolean outgoing,
                boolean compact,
                boolean active,
                String qsoCommentSeed
        ) {
            this.label = label;
            this.headline = headline;
            this.meta = meta;
            this.body = body;
            this.cardBackgroundRes = cardBackgroundRes;
            this.labelColorRes = labelColorRes;
            this.outgoing = outgoing;
            this.compact = compact;
            this.active = active;
            this.qsoCommentSeed = qsoCommentSeed;
        }
    }

    private static final class TranscriptEntry {
        private final long id;
        private final TranscriptEntryType type;
        private final long startedAtEpochMs;
        private long updatedAtEpochMs;
        private String sourceOrRouteLabel;
        private String stateLabel;
        private String bodyText;
        private ArrayList<String> callsignCandidates;
        private int progressIndex;
        private boolean active;
        private int toneFrequencyHz;
        private int wpm;

        private TranscriptEntry(
                long id,
                TranscriptEntryType type,
                long startedAtEpochMs
        ) {
            this.id = id;
            this.type = type;
            this.startedAtEpochMs = startedAtEpochMs;
            this.updatedAtEpochMs = startedAtEpochMs;
            this.sourceOrRouteLabel = "";
            this.stateLabel = "";
            this.bodyText = "";
            this.callsignCandidates = new ArrayList<>();
            this.progressIndex = -1;
            this.active = true;
            this.toneFrequencyHz = 0;
            this.wpm = 0;
        }
    }

    private static final class CallsignCandidateSpan extends CharacterStyle implements UpdateAppearance {
        private final String callsign;
        private final int foregroundColor;
        private final int backgroundColor;

        private CallsignCandidateSpan(String callsign, int foregroundColor, int backgroundColor) {
            this.callsign = callsign;
            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
        }

        private String callsign() {
            return callsign;
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setColor(foregroundColor);
            textPaint.bgColor = backgroundColor;
            textPaint.setFakeBoldText(true);
        }
    }

    private ActivityOperateBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private RouteFallbackStore routeFallbackStore;
    private RxInputSettingsStore rxInputSettingsStore;
    private SqlLevelStore sqlLevelStore;
    private StationProfileStore stationProfileStore;
    private RxSessionStore rxSessionStore;
    private SpectrumHistoryStore spectrumHistoryStore;
    private TxTemplateStore txTemplateStore;
    private DeveloperModeStore developerModeStore;
    private LiveRxTraceStore liveRxTraceStore;
    private MicrophoneRxAudioSource operateMicrophoneRxAudioSource;
    private UsbExternalRxAudioSource operateUsbExternalRxAudioSource;
    private RxAudioSource activeOperateRxAudioSource;
    private AudioInputHealthTracker operateAudioInputHealthTracker;
    private RxFrameSignalRunner operateFrameSignalRunner;
    private RxCoreComponents operateRxCore;
    private CwSignalProcessor operateSignalProcessor;
    private CwHybridTimingModel operateTimingModel;
    private LiveRxWpmGuard operateLiveRxWpmGuard;
    private LiveRxToneEventStabilizer operateToneEventStabilizer;
    private CwFrontEndLearningGate operateFrontEndLearningGate;
    private RxTurnController operateRxTurnController;
    private TimingAnchorController operateTimingAnchorController;
    private RxRawCommitGate operateRawCommitGate;
    private RxCommittedDecodeController operateCommittedDecodeController;
    private RxCommittedOutputController operateCommittedOutputController;
    private RxTurnSessionFinalizer operateTurnSessionFinalizer;
    private RxTurnSessionCoordinator operateTurnSessionCoordinator;
    private RxTimingDecodeRunner operateTimingDecodeRunner;
    private RxToneTimingRunner operateToneTimingRunner;
    private CwDecoder operateDecoder;
    private CwInterpreter operateRawInterpreter;
    private RxUnknownFallbackTracker operateUnknownFallbackTracker;
    private AudioSpectrumAnalyzer operateAudioSpectrumAnalyzer;
    private LiveRxTraceRecorder operateLiveRxTraceRecorder;
    private AudioSpectrumSnapshot lastOperateSpectrumSnapshot;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            mainHandler.postDelayed(this, LIVE_RX_REFRESH_INTERVAL_MS);
        }
    };
    private final Runnable txProgressRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!txSendInProgress) {
                return;
            }
            syncActiveTxPlaybackSnapshot();
            updateComposerUi();
            refreshConversationOnly();
            mainHandler.postDelayed(this, TX_PROGRESS_REFRESH_INTERVAL_MS);
        }
    };

    private RxSessionSnapshot lastRxSessionSnapshot;
    private SpectrumSnapshotData lastUiSpectrumSnapshot;
    private OverlayMode overlayMode = OverlayMode.NONE;
    private OverlayMode pendingLaunchOverlayMode = OverlayMode.NONE;
    private String selectedTemplate = TEMPLATE_CQ;
    private int sqlLevel = 55;
    private boolean templateSelectorInitialized;
    private boolean templateSelectorSyncing;
    private boolean txSendInProgress;
    private boolean txStopRequested;
    private String txDeliveryStatus = "";
    private boolean suppressComposerWatcher;
    private RigControlAdapter activeTxAdapter;
    private String activeTxText = "";
    private String activeTxRouteLabel = "";
    private CwTxPlan activeTxPlan;
    private CwTxPlaybackSnapshot activeTxPlaybackSnapshot;
    private boolean activeTxStopSupported;
    private SharedPreferences operateUiPreferences;
    private ActivityResultLauncher<String> microphonePermissionLauncher;
    private boolean microphonePermissionRequestedThisSession;
    private RxAudioSource.State operateRxState = RxAudioSource.State.IDLE;
    private long lastOperateRxPublishAtElapsedMs;
    private long lastOperateStableDecodeAtElapsedMs = -1L;
    private long lastOperateTimingResetAtElapsedMs = -1L;
    private boolean preserveRxAcrossSpectrumNavigation;
    private RxInputSettingsStore.MicSourceMode activeMicSourceMode;
    private RxInputSettingsStore.RxInputMode activeRxInputMode;
    private BroadcastReceiver usbPermissionReceiver;
    private String operateUsbStatusMessage = "";
    private final ArrayList<TranscriptEntry> operateTranscriptEntries = new ArrayList<>();
    private long nextTranscriptEntryId = 1L;
    private long activeRxTranscriptEntryId = -1L;
    private long activeRxTranscriptStartedAtEpochMs;
    private String activeRxTranscriptBaselineText = "";
    private long activeTxTranscriptEntryId = -1L;
    private boolean transcriptUseUtc = true;
    private boolean transcriptTimelineActive;
    private String selectedOperateRemoteCallsign;
    private boolean conversationAutoScrollPending = true;
    private boolean transcriptRxSuppressedDuringTx;
    private boolean immediateTxPausedRxCapture;
    private boolean operateActivityResumed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOperateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        microphonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        clearOperateRxPresentationState();
                    }
                    syncOperateRxEngine();
                    refreshUi();
                }
        );
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        routeFallbackStore = new RouteFallbackStore(this);
        rxInputSettingsStore = new RxInputSettingsStore(this);
        sqlLevelStore = new SqlLevelStore(this);
        stationProfileStore = new StationProfileStore(this);
        rxSessionStore = new RxSessionStore(this);
        spectrumHistoryStore = new SpectrumHistoryStore(this);
        txTemplateStore = new TxTemplateStore(this);
        developerModeStore = new DeveloperModeStore(this);
        liveRxTraceStore = new LiveRxTraceStore(this);
        registerUsbPermissionReceiver();
        sharedActiveInstance = new WeakReference<>(this);
        initializeOperateRxPipeline();
        operateUiPreferences = getSharedPreferences(PREFS_OPERATE_UI, MODE_PRIVATE);
        transcriptUseUtc = operateUiPreferences.getBoolean(PREF_TRANSCRIPT_USE_UTC, true);
        sqlLevel = sqlLevelStore.load();
        syncOperateSql();
        consumeLaunchIntent(getIntent());
        setupActions();
        restoreOperateUiState(savedInstanceState);
        binding.sideRail.post(this::restoreSideRailPosition);
        refreshUi();
        openPendingLaunchOverlayIfAny();
    }

    @Override
    protected void onResume() {
        super.onResume();
        operateActivityResumed = true;
        sharedActiveInstance = new WeakReference<>(this);
        preserveRxAcrossSpectrumNavigation = false;
        refreshUi();
        maybeEnsureMicrophonePermission();
        syncOperateRxEngine();
        openPendingLaunchOverlayIfAny();
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.postDelayed(liveRefreshRunnable, LIVE_RX_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeLaunchIntent(intent);
        refreshUi();
        openPendingLaunchOverlayIfAny();
    }

    @Override
    protected void onPause() {
        operateActivityResumed = false;
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.removeCallbacks(txProgressRefreshRunnable);
        if (!preserveRxAcrossSpectrumNavigation) {
            stopOperateRxCapture(true);
        }
        stopImmediateTxForLifecycle();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(liveRefreshRunnable);
        mainHandler.removeCallbacks(txProgressRefreshRunnable);
        OperateActivity currentSharedInstance = sharedActiveInstance.get();
        if (currentSharedInstance == this) {
            sharedActiveInstance = new WeakReference<>(null);
        }
        if (operateMicrophoneRxAudioSource != null) {
            operateMicrophoneRxAudioSource.release();
            operateMicrophoneRxAudioSource = null;
        }
        if (operateUsbExternalRxAudioSource != null) {
            operateUsbExternalRxAudioSource.release();
            operateUsbExternalRxAudioSource = null;
        }
        activeOperateRxAudioSource = null;
        if (usbPermissionReceiver != null) {
            unregisterReceiver(usbPermissionReceiver);
            usbPermissionReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_TRANSCRIPT_TIMELINE_ACTIVE, transcriptTimelineActive);
        outState.putLong(STATE_TRANSCRIPT_NEXT_ID, nextTranscriptEntryId);
        outState.putBoolean(STATE_TRANSCRIPT_USE_UTC, transcriptUseUtc);
        outState.putParcelableArrayList(STATE_TRANSCRIPT_ENTRIES, saveTranscriptEntries());
        outState.putString(STATE_SELECTED_OPERATE_REMOTE_CALLSIGN, selectedOperateRemoteCallsign);
    }

    private void setupActions() {
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.bottomNavView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_nav_operate) {
                hideOverlay();
                return true;
            }
            if (itemId == R.id.menu_nav_spectrum) {
                preserveRxAcrossSpectrumNavigation = true;
                startActivity(new Intent(this, SpectrumActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_logbook) {
                startActivity(new Intent(this, QsoLogbookActivity.class));
                return true;
            }
            if (itemId == R.id.menu_nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        binding.sideChartButton.setOnClickListener(view -> toggleOverlay(OverlayMode.CHART));
        binding.sideSqlButton.setOnClickListener(view -> toggleOverlay(OverlayMode.SQL));
        binding.sideTemplateButton.setOnClickListener(view -> toggleOverlay(OverlayMode.TEMPLATE));
        binding.sqlQuickChip.setOnClickListener(view -> toggleOverlay(OverlayMode.SQL));
        binding.transcriptTimeModeChip.setOnClickListener(view -> toggleTranscriptTimeMode());
        binding.clearTranscriptChip.setOnClickListener(view -> clearTranscriptDisplay());
        binding.callsignHintText.setOnClickListener(view -> handleCallsignHintTap());
        binding.callsignHintText.setOnLongClickListener(view -> {
            openQsoEditorFromCallsignHint();
            return true;
        });
        binding.overlayScrim.setOnClickListener(view -> hideOverlay());
        binding.closeOverlayButton.setOnClickListener(view -> hideOverlay());
        binding.rxFootnoteText.setOnClickListener(view -> {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                requestMicrophonePermission(true);
                return;
            }
            if (requestOperateUsbPermissionIfNeeded()) {
                return;
            }
            startActivity(new Intent(this, RigSetupActivity.class));
        });

        binding.sendTxButton.setOnClickListener(view -> startImmediateTx());
        binding.clearComposeButton.setOnClickListener(view -> binding.txComposeEditText.setText(""));
        binding.repeatTxButton.setOnClickListener(view -> startImmediateTx());
        binding.pauseTxButton.setOnClickListener(view -> stopImmediateTx());

        setupTemplateSelector();
        setupComposer();
        setupSqlControls();
        setupSideRailDrag();
    }

    private void consumeLaunchIntent(@Nullable Intent intent) {
        pendingLaunchOverlayMode = OverlayMode.NONE;
        if (intent == null) {
            return;
        }
        if (START_OVERLAY_CHART.equals(intent.getStringExtra(EXTRA_START_OVERLAY))) {
            pendingLaunchOverlayMode = OverlayMode.CHART;
        }
    }

    private void setupTemplateSelector() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.compact_spinner_item,
                TEMPLATE_OPTIONS
        );
        adapter.setDropDownViewResource(R.layout.compact_spinner_dropdown_item);
        binding.templateSelectorSpinner.setAdapter(adapter);
        binding.templateSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedItem = parent.getItemAtPosition(position);
                if (selectedItem == null) {
                    return;
                }
                if (!templateSelectorInitialized) {
                    templateSelectorInitialized = true;
                    selectTemplate(selectedItem.toString(), true);
                    return;
                }
                if (templateSelectorSyncing) {
                    return;
                }
                selectTemplate(selectedItem.toString(), true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.overlayTemplateCqButton.setOnClickListener(view -> selectTemplate(TEMPLATE_CQ, true));
        binding.overlayTemplateCqDxButton.setOnClickListener(view -> selectTemplate(TEMPLATE_REPLY, true));
        binding.overlayTemplateQrzButton.setOnClickListener(view -> selectTemplate(TEMPLATE_QRZ, true));
        binding.overlayTemplateTu73Button.setOnClickListener(view -> selectTemplate(TEMPLATE_TU73, true));

        syncTemplateSelector(TEMPLATE_CQ);
        selectTemplate(TEMPLATE_CQ, true);
    }

    private void setupComposer() {
        binding.txComposeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressComposerWatcher) {
                    return;
                }
                updateComposerUi();
                refreshConversationOnly();
                updateOverlayContent();
            }
        });
    }

    private void setupSqlControls() {
        binding.sqlSeekBar.setMax(100);
        syncSqlSeekBar();
        binding.wpmSeekBar.setMax(75);
        syncWpmSeekBarFromSettings();
        binding.sqlSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                sqlLevel = progress;
                persistSqlLevel();
                syncOperateSql();
                binding.sqlOverlayText.setText(renderSqlOverlayText(lastRxSessionSnapshot));
                refreshConversationOnly();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.wpmSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                persistOperateWpm(progressToWpm(progress));
                binding.sqlOverlayText.setText(renderSqlOverlayText(lastRxSessionSnapshot));
                updateComposerUi();
                refreshConversationOnly();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void syncSqlSeekBar() {
        if (binding.sqlSeekBar.getProgress() != sqlLevel) {
            binding.sqlSeekBar.setProgress(sqlLevel);
        }
    }

    private void persistSqlLevel() {
        if (operateUiPreferences == null) {
            return;
        }
        if (sqlLevelStore != null) {
            sqlLevelStore.save(sqlLevel);
        }
    }

    private void setupSideRailDrag() {
        View.OnTouchListener dragTouchListener = new View.OnTouchListener() {
            private float deltaX;
            private float deltaY;
            private float downRawX;
            private float downRawY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        deltaX = binding.sideRail.getX() - event.getRawX();
                        deltaY = binding.sideRail.getY() - event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging) {
                            float travelX = Math.abs(event.getRawX() - downRawX);
                            float travelY = Math.abs(event.getRawY() - downRawY);
                            dragging = travelX > dpToPx(SIDE_RAIL_DRAG_THRESHOLD_PX)
                                    || travelY > dpToPx(SIDE_RAIL_DRAG_THRESHOLD_PX);
                        }
                        if (!dragging) {
                            return true;
                        }
                        float nextX = event.getRawX() + deltaX;
                        float nextY = event.getRawY() + deltaY;
                        moveSideRailTo(nextX, nextY);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        persistSideRailPosition();
                        if (!dragging && event.getActionMasked() == MotionEvent.ACTION_UP && view != binding.sideRail) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
        binding.sideRail.setOnTouchListener(dragTouchListener);
        binding.sideChartButton.setOnTouchListener(dragTouchListener);
        binding.sideSqlButton.setOnTouchListener(dragTouchListener);
        binding.sideTemplateButton.setOnTouchListener(dragTouchListener);
    }

    private void syncWpmSeekBarFromSettings() {
        int wpm = resolveOperateWpm();
        int progress = Math.max(0, Math.min(75, wpm - 5));
        if (binding.wpmSeekBar.getProgress() != progress) {
            binding.wpmSeekBar.setProgress(progress);
        }
    }

    private int progressToWpm(int progress) {
        return Math.max(5, Math.min(80, progress + 5));
    }

    private int resolveOperateWpm() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return settings.defaultWpm();
    }

    private int resolveOperateToneFrequencyHz() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return settings.defaultToneFrequencyHz();
    }

    private void persistOperateWpm(int wpm) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        RigProfileSettings updated = new RigProfileSettings(
                wpm,
                existing.defaultToneFrequencyHz(),
                existing.usbKeyLine(),
                existing.usbPreferredDeviceName(),
                existing.serialCatProtocolFamily(),
                existing.serialCatBaudRate(),
                existing.serialCatPortHint(),
                existing.serialCatKeyLine(),
                existing.serialCatKeyingPortHint(),
                existing.serialCatKeyingPolarity(),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                existing.serialCatCivAddressHex(),
                existing.networkCatProtocolFamily(),
                existing.networkHost(),
                existing.networkPort(),
                existing.bluetoothDeviceHint()
        );
        rigSelectionStore.saveSettings(profile, updated);
    }

    private void syncOperateTimingSeedWpm() {
        if (operateRxCore == null) {
            return;
        }
        operateRxCore.applySeedWpm(resolveOperateWpm());
    }

    private void restoreSideRailPosition() {
        if (operateUiPreferences == null) {
            return;
        }
        if (!operateUiPreferences.contains(PREF_SIDE_RAIL_X)
                || !operateUiPreferences.contains(PREF_SIDE_RAIL_Y)) {
            return;
        }
        moveSideRailTo(
                operateUiPreferences.getFloat(PREF_SIDE_RAIL_X, binding.sideRail.getX()),
                operateUiPreferences.getFloat(PREF_SIDE_RAIL_Y, binding.sideRail.getY())
        );
    }

    private void persistSideRailPosition() {
        if (operateUiPreferences == null) {
            return;
        }
        operateUiPreferences.edit()
                .putFloat(PREF_SIDE_RAIL_X, binding.sideRail.getX())
                .putFloat(PREF_SIDE_RAIL_Y, binding.sideRail.getY())
                .apply();
    }

    private void moveSideRailTo(float requestedX, float requestedY) {
        View parent = (View) binding.sideRail.getParent();
        float sideMargin = dpToPx(6f);
        float topMargin = dpToPx(72f);
        float bottomMargin = dpToPx(96f);
        float minX = sideMargin;
        float maxX = Math.max(minX, parent.getWidth() - binding.sideRail.getWidth() - sideMargin);
        float minY = topMargin;
        float maxY = Math.max(minY, parent.getHeight() - binding.sideRail.getHeight() - bottomMargin);
        binding.sideRail.setX(Math.max(minX, Math.min(maxX, requestedX)));
        binding.sideRail.setY(Math.max(minY, Math.min(maxY, requestedY)));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void refreshUi() {
        lastRxSessionSnapshot = rxSessionStore.load();
        lastUiSpectrumSnapshot = latestSpectrumSnapshotForUi();
        binding.statusMainText.setText(renderStatusMain(lastRxSessionSnapshot));
        binding.statusDetailText.setText(renderStatusDetail(lastRxSessionSnapshot));
        binding.sourceChipText.setText(renderSourceChip(lastRxSessionSnapshot));
        boolean callsignHintAvailable = hasMeaningfulText(resolvePrimaryCallsignHint(lastRxSessionSnapshot))
                || hasMeaningfulText(selectedOperateRemoteCallsign);
        binding.callsignHintText.setText(renderCallsignHint(lastRxSessionSnapshot));
        binding.rxFootnoteText.setText(renderRxFootnote(lastRxSessionSnapshot));
        binding.callsignHintText.setEnabled(callsignHintAvailable);
        binding.callsignHintText.setAlpha(callsignHintAvailable ? 1.0f : 0.62f);
        binding.callsignHintText.setBackgroundResource(callsignHintAvailable
                ? R.drawable.operate_chip_active_background
                : R.drawable.operate_chip_background);
        setVisibleWhenHasText(binding.rxFootnoteText);
        binding.rxFootnoteText.setClickable(
                (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission())
                        || shouldUseUsbExternalRx()
        );
        binding.transcriptTimeModeChip.setText(transcriptUseUtc ? "UTC" : "Local");
        FormalBottomNavStyler.apply(binding.bottomNavView, FormalBottomNavStyler.Page.OPERATE);
        refreshConversationOnly();
        updateComposerUi();
        updateOverlayContent();
    }

    private void openPendingLaunchOverlayIfAny() {
        if (pendingLaunchOverlayMode == OverlayMode.NONE) {
            return;
        }
        overlayMode = pendingLaunchOverlayMode;
        pendingLaunchOverlayMode = OverlayMode.NONE;
        binding.overlayScrim.setVisibility(View.VISIBLE);
        binding.overlayPanel.setVisibility(View.VISIBLE);
        binding.sideRail.setVisibility(View.GONE);
        updateOverlayContent();
    }

    private void refreshConversationOnly() {
        renderConversationCards(buildOperateStreamEntries(lastRxSessionSnapshot));
    }

    private void updateComposerUi() {
        String composeText = safeValue(binding.txComposeEditText.getText() == null
                ? null
                : binding.txComposeEditText.getText().toString());
        boolean hasComposeText = !"-".equals(composeText);
        boolean showPlaybackControls = txSendInProgress || hasRepeatableTxText();
        binding.txPlaybackControlsRow.setVisibility(showPlaybackControls ? View.VISIBLE : View.GONE);
        binding.templatePreviewText.setText(renderTemplatePreview());
        binding.selectedTemplateChip.setText(renderTxSourceChip(hasComposeText));
        setVisibleWhenHasText(binding.selectedTemplateChip);
        binding.txRouteChip.setText(renderTxRouteChip());
        binding.txStageChip.setText(renderTxContentChip(hasComposeText, composeText));
        binding.sqlQuickChip.setText(renderSqlQuickChipText());
        binding.sendTxButton.setEnabled(!txSendInProgress);
        binding.sendTxButton.setAlpha(txSendInProgress ? 0.55f : 1.0f);
        binding.clearComposeButton.setVisibility(hasComposeText ? View.VISIBLE : View.GONE);
        binding.clearComposeButton.setEnabled(hasComposeText && !txSendInProgress);
        binding.pauseTxButton.setEnabled(txSendInProgress && activeTxStopSupported);
        binding.pauseTxButton.setAlpha(txSendInProgress && activeTxStopSupported ? 1.0f : 0.45f);
        binding.repeatTxButton.setVisibility(txSendInProgress ? View.GONE : View.VISIBLE);
        binding.repeatTxButton.setEnabled(!txSendInProgress && hasRepeatableTxText());
        binding.repeatTxButton.setAlpha(!txSendInProgress && hasRepeatableTxText() ? 1.0f : 0.45f);
        binding.txStatusText.setText(renderTxStatus(hasComposeText));
        setVisibleWhenHasText(binding.txStatusText);
        renderComposerPlaybackHighlight();
    }

    private void renderComposerPlaybackHighlight() {
        String composeText = binding.txComposeEditText.getText() == null
                ? ""
                : binding.txComposeEditText.getText().toString();
        if (!txSendInProgress || !hasMeaningfulText(composeText)) {
            clearComposerHighlightIfNeeded(composeText);
            return;
        }
        SpannableString styled = new SpannableString(composeText);
        int normalColor = ContextCompat.getColor(this, R.color.cwcn_title);
        int completedColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentColor = ContextCompat.getColor(this, R.color.cwcn_accent);
        int currentBackground = ContextCompat.getColor(this, R.color.cwcn_overlay_scrim);
        int currentIndex = activeTxPlaybackSnapshot == null ? -1 : activeTxPlaybackSnapshot.currentTextIndex();
        if (currentIndex < 0 || currentIndex >= composeText.length()) {
            styled.setSpan(
                    new ForegroundColorSpan(normalColor),
                    0,
                    composeText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            applyComposerStyledText(styled);
            return;
        }
        if (currentIndex > 0) {
            styled.setSpan(
                    new ForegroundColorSpan(completedColor),
                    0,
                    currentIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        styled.setSpan(
                new ForegroundColorSpan(currentColor),
                currentIndex,
                currentIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        styled.setSpan(
                new BackgroundColorSpan(currentBackground),
                currentIndex,
                currentIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        styled.setSpan(
                new StyleSpan(android.graphics.Typeface.BOLD),
                currentIndex,
                currentIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        if (currentIndex + 1 < composeText.length()) {
            styled.setSpan(
                    new ForegroundColorSpan(normalColor),
                    currentIndex + 1,
                    composeText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        applyComposerStyledText(styled);
    }

    private void clearComposerHighlightIfNeeded(String composeText) {
        Editable editable = binding.txComposeEditText.getText();
        if (!(editable instanceof Spanned)) {
            return;
        }
        if (editable.length() == 0) {
            return;
        }
        Object[] spans = editable.getSpans(0, editable.length(), Object.class);
        if (spans == null || spans.length == 0) {
            return;
        }
        applyComposerStyledText(new SpannableString(composeText));
    }

    private void applyComposerStyledText(CharSequence styledText) {
        int selectionStart = Math.max(0, binding.txComposeEditText.getSelectionStart());
        int selectionEnd = Math.max(0, binding.txComposeEditText.getSelectionEnd());
        suppressComposerWatcher = true;
        binding.txComposeEditText.setText(styledText);
        int length = binding.txComposeEditText.getText() == null ? 0 : binding.txComposeEditText.getText().length();
        binding.txComposeEditText.setSelection(
                Math.min(selectionStart, length),
                Math.min(selectionEnd, length)
        );
        suppressComposerWatcher = false;
    }

    private String renderStatusMain(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null) {
            RigControlAdapter adapter = resolveImmediateTxAdapter(profile);
            configureImmediateTxAdapter(adapter, profile);
            return RigRouteStatusFormatter.describeOperateStatusMain(
                    profile,
                    shouldUsePhoneMicrophoneRx(),
                    hasMicrophonePermission(),
                    adapter
            );
        }
        int tone = resolveBestTone(snapshot);
        String capture = snapshot.captureActive() ? "接收中" : "最近一轮";
        return capture
                + " | "
                + positiveOrDash(resolveDisplayWpm(snapshot)) + "WPM"
                + " | "
                + positiveOrDash(tone) + "Hz"
                + " | RAW";
    }

    private String renderStatusDetail(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            RigProfile profile = rigSelectionStore.selectedProfile();
            String routeHint = renderOperateRouteReadinessHint(profile);
            if (routeHint != null) {
                return resolveOperateRouteSummary(profile) + " | " + routeHint;
            }
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        String rigSummary = resolveOperateRouteSummary(profile);
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return rigSummary + " | 等待麦克风权限";
            }
            if (shouldUseUsbExternalRx()) {
                return rigSummary + " | 等待外部音频输入";
            }
            return shouldUsePhoneMicrophoneRx()
                    ? rigSummary + " | 手机接收待命"
                    : rigSummary + " | 已选发射路径，接收尚未接入电台音频";
        }
        return rigSummary
                + " | "
                + renderInputHealthPrefix(snapshot)
                + renderAge(snapshot.updatedAtEpochMs())
                + renderDeveloperFrontEndSuffix(snapshot);
    }

    private String renderSourceChip(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx()) {
                return "手机接收";
            }
            if (shouldUseUsbExternalRx()) {
                return "USB接收";
            }
            return "未接收";
        }
        return renderFriendlyOperateSourceLabel(snapshot.sourceLabel());
    }

    private String renderFriendlyOperateSourceLabel(@Nullable String sourceLabel) {
        if (!hasMeaningfulText(sourceLabel)) {
            return "RX输入";
        }
        String trimmed = sourceLabel.trim();
        String normalized = trimmed.toUpperCase(Locale.US);
        if (normalized.contains("PHONE MICROPHONE")) {
            return "手机接收";
        }
        if (normalized.contains("USB")) {
            return "USB接收";
        }
        if (normalized.contains("RX INPUT")) {
            return "RX输入";
        }
        return safeCompact(trimmed, 16);
    }

    private void renderConversationCards(List<StreamEntry> entries) {
        binding.conversationList.removeAllViews();
        for (int index = 0; index < entries.size(); index++) {
            StreamEntry entry = entries.get(index);
            binding.conversationList.addView(buildConversationCard(entry, index < entries.size() - 1));
        }
        if (conversationAutoScrollPending) {
            binding.conversationScrollView.post(() -> {
                binding.conversationScrollView.fullScroll(View.FOCUS_DOWN);
                conversationAutoScrollPending = false;
            });
        }
    }

    private View buildConversationCard(StreamEntry entry, boolean withBottomGap) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(entry.cardBackgroundRes);
        card.setPadding(
                px(entry.active ? (entry.compact ? 9 : 11) : (entry.compact ? 7 : 8)),
                px(entry.active ? (entry.compact ? 7 : 9) : (entry.compact ? 5 : 6)),
                px(entry.active ? (entry.compact ? 9 : 11) : (entry.compact ? 7 : 8)),
                px(entry.active ? (entry.compact ? 7 : 9) : (entry.compact ? 5 : 6))
        );

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int bottomGap = entry.active ? px(entry.compact ? 8 : 10) : px(entry.compact ? 3 : 4);
        if (withBottomGap) {
            cardParams.bottomMargin = bottomGap;
        } else if (entry.active) {
            cardParams.bottomMargin = px(4);
        }
        if (entry.outgoing) {
            cardParams.leftMargin = px(entry.active ? 20 : 14);
        } else if (entry.compact) {
            cardParams.leftMargin = px(entry.active ? 12 : 8);
            cardParams.rightMargin = px(entry.active ? 28 : 22);
        } else {
            cardParams.rightMargin = px(entry.active ? 12 : 8);
        }
        if (entry.active) {
            cardParams.topMargin = px(2);
        }
        card.setLayoutParams(cardParams);
        card.setAlpha(entry.active ? 1.0f : 0.9f);

        if (entry.active) {
            View accentLine = new View(this);
            LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    px(3)
            );
            accentParams.bottomMargin = px(7);
            accentLine.setLayoutParams(accentParams);
            accentLine.setBackgroundColor(ContextCompat.getColor(this, entry.labelColorRes));
            accentLine.setAlpha(entry.outgoing ? 0.82f : 0.72f);
            card.addView(accentLine);
        }

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.addView(topRow);

        TextView labelView = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelView.setLayoutParams(labelParams);
        labelView.setText(entry.label);
        labelView.setTextSize(entry.compact ? 8.5f : 10f);
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
        labelView.setTextColor(ContextCompat.getColor(this, entry.labelColorRes));
        labelView.setBackgroundResource(entry.active
                ? R.drawable.operate_chip_active_background
                : android.R.color.transparent);
        labelView.setPadding(px(6), px(2), px(6), px(2));
        labelView.setAlpha(entry.active ? 1.0f : 0.86f);
        topRow.addView(labelView);

        TextView headlineView = new TextView(this);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headlineParams.leftMargin = px(8);
        headlineView.setLayoutParams(headlineParams);
        headlineView.setText(entry.headline);
        headlineView.setTextSize(entry.compact ? 8.5f : 9.25f);
        headlineView.setTextColor(ContextCompat.getColor(
                this,
                entry.active ? entry.labelColorRes : R.color.cwcn_title
        ));
        headlineView.setTypeface(headlineView.getTypeface(),
                entry.active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        topRow.addView(headlineView);

        TextView metaView = new TextView(this);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = px(entry.compact ? 2 : 3);
        metaView.setLayoutParams(metaParams);
        metaView.setText(entry.meta);
        metaView.setTextSize(entry.compact ? 7.5f : 8.5f);
        metaView.setTextColor(ContextCompat.getColor(
                this,
                entry.active ? entry.labelColorRes : R.color.cwcn_subtitle
        ));
        metaView.setAlpha(entry.active ? 0.94f : 0.8f);
        card.addView(metaView);

        TextView bodyView = new TextView(this);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = px(entry.compact ? 2 : 4);
        bodyView.setLayoutParams(bodyParams);
        bodyView.setText(entry.body);
        bodyView.setTextSize(entry.compact ? 11f : 14f);
        bodyView.setTextColor(ContextCompat.getColor(this, R.color.cwcn_body));
        bodyView.setLineSpacing(0f, 1.12f);
        bodyView.setTypeface(android.graphics.Typeface.MONOSPACE);
        bodyView.setMaxLines(entry.compact ? 3 : Integer.MAX_VALUE);
        bodyView.setAlpha(entry.active ? 1.0f : 0.94f);
        bodyView.setTextIsSelectable(false);
        bodyView.setOnTouchListener((view, event) -> {
            bodyView.setTag(new float[]{event.getX(), event.getY()});
            return false;
        });
        bindTranscriptBodyInteractions(bodyView, entry);
        card.addView(bodyView);

        return card;
    }

    private void bindTranscriptBodyInteractions(TextView bodyView, StreamEntry entry) {
        CharSequence bodyText = entry == null ? null : entry.body;
        if (!(bodyText instanceof Spanned)) {
            bodyView.setOnTouchListener(null);
            bodyView.setOnClickListener(null);
            bodyView.setOnLongClickListener(null);
            bodyView.setLongClickable(false);
            return;
        }
        Spanned spanned = (Spanned) bodyText;
        CallsignCandidateSpan[] spans = spanned.getSpans(0, spanned.length(), CallsignCandidateSpan.class);
        if (spans == null || spans.length == 0) {
            bodyView.setOnTouchListener(null);
            bodyView.setOnClickListener(null);
            bodyView.setOnLongClickListener(null);
            bodyView.setLongClickable(false);
            return;
        }
        bodyView.setOnClickListener(view -> {
            String targetCallsign = resolveCallsignCandidateAtTouch(bodyView, spanned);
            if (!hasMeaningfulText(targetCallsign)) {
                return;
            }
            openQsoEditorForCallsign(
                    targetCallsign.trim().toUpperCase(Locale.US),
                    entry == null ? null : entry.qsoCommentSeed
            );
        });
        bodyView.setLongClickable(true);
        bodyView.setOnLongClickListener(view -> {
            String targetCallsign = resolveCallsignCandidateAtTouch(bodyView, spanned);
            if (!hasMeaningfulText(targetCallsign)) {
                return false;
            }
            openQsoEditorForCallsign(
                    targetCallsign.trim().toUpperCase(Locale.US),
                    entry == null ? null : entry.qsoCommentSeed
            );
            return true;
        });
    }

    @Nullable
    private String resolveCallsignCandidateAtTouch(TextView bodyView, Spanned spanned) {
        if (bodyView == null || spanned == null) {
            return null;
        }
        Object tag = bodyView.getTag();
        if (!(tag instanceof float[])) {
            return null;
        }
        float[] point = (float[]) tag;
        if (point.length < 2) {
            return null;
        }
        Layout layout = bodyView.getLayout();
        if (layout == null) {
            return null;
        }
        int x = Math.round(point[0]) - bodyView.getTotalPaddingLeft() + bodyView.getScrollX();
        int y = Math.round(point[1]) - bodyView.getTotalPaddingTop() + bodyView.getScrollY();
        if (x < 0 || y < 0 || x > layout.getWidth() || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);
        String exact = resolveCallsignCandidateAtOffset(spanned, offset);
        if (hasMeaningfulText(exact)) {
            return exact;
        }
        if (offset > 0) {
            String left = resolveCallsignCandidateAtOffset(spanned, offset - 1);
            if (hasMeaningfulText(left)) {
                return left;
            }
        }
        if (offset + 1 < spanned.length()) {
            return resolveCallsignCandidateAtOffset(spanned, offset + 1);
        }
        return null;
    }

    @Nullable
    private String resolveCallsignCandidateAtOffset(Spanned spanned, int offset) {
        if (spanned == null || offset < 0 || offset >= spanned.length()) {
            return null;
        }
        CallsignCandidateSpan[] spans = spanned.getSpans(offset, offset, CallsignCandidateSpan.class);
        if (spans == null || spans.length == 0) {
            return null;
        }
        return spans[0].callsign();
    }

    private int px(int dp) {
        return Math.round(dpToPx(dp));
    }

    private String renderRxFootnote(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            String routeHint = renderOperateRouteReadinessHint(rigSelectionStore.selectedProfile());
            if (routeHint != null) {
                return routeHint;
            }
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null && shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
            return "点这里授权麦克风，开始手机接收。";
        }
        if (snapshot == null && shouldUseUsbExternalRx()) {
            return "当前按外部音频输入模式工作，请确认 USB 音频设备已接入并被 Android 路由到录音输入。";
        }
        if (snapshot == null) {
            return resolveOperateRxHint(profile);
        }
        if (snapshot.inputLevelClipping()) {
            return "手机输入已经削顶，先拉远麦克风或降低监听音量，否则 WPM 会被推高并拖垮解码。";
        }
        if (snapshot.inputLevelHot()) {
            return snapshot.hasInputHealthHint()
                    ? snapshot.inputHealthHint()
                    : "手机输入偏热，建议拉远一点再看解码是否恢复稳定。";
        }
        if (shouldShowDeveloperFrontEndSummary(snapshot)) {
            return snapshot.developerFrontEndSummary();
        }
        if (isOperateRxRawOnlyMode()) {
            return "当前主界面只保留 transcript 与 RAW 抄收，日志判断由人工完成。";
        }
        return "";
    }

    private String renderTxStatus(boolean hasComposeText) {
        if (txSendInProgress) {
            if (txStopRequested) {
                return activeTxStopSupported ? "正在停止发射" : "已请求停止，等待适配器响应";
            }
            if (activeTxPlaybackSnapshot != null && hasMeaningfulText(activeTxPlaybackSnapshot.statusMessage())) {
                return renderPlaybackSnapshotStatus(activeTxPlaybackSnapshot);
            }
            return hasComposeText ? "正在发射输入文本" : "正在发射模板文本";
        }
        if (txDeliveryStatus != null && !txDeliveryStatus.trim().isEmpty()) {
            return txDeliveryStatus;
        }
        return "";
    }

    private String renderTxRouteChip() {
        int wpm = resolveOperateWpm();
        RigProfile profile = rigSelectionStore.selectedProfile();
        String route;
        if (txSendInProgress && hasMeaningfulText(activeTxRouteLabel)) {
            route = safeCompact(activeTxRouteLabel, 12);
        } else if (usePhoneFallbackRoute(profile)) {
            route = "手机音频";
        } else if (profile == null) {
            route = "未配置";
        } else {
            route = safeCompact(profile.displayName(), 12);
        }
        return wpm + "WPM | " + route;
    }

    private String renderTxSourceChip(boolean hasComposeText) {
        if (txSendInProgress) {
            return hasMeaningfulText(activeTxText) ? "发送中" : "模板发送";
        }
        if (hasComposeText) {
            return "输入文本";
        }
        return "模板 " + selectedTemplate;
    }

    private String renderTxContentChip(boolean hasComposeText, String composeText) {
        if (txSendInProgress && activeTxPlaybackSnapshot != null) {
            return renderPlaybackProgressSummary(activeTxPlaybackSnapshot);
        }
        if (!hasComposeText) {
            String templateText = buildTemplateText(selectedTemplate);
            return "模板 " + templateText.length() + "字";
        }
        int length = composeText == null || "-".equals(composeText) ? 0 : composeText.length();
        return "输入 " + length + "字";
    }

    private boolean usePhoneFallbackRoute(@Nullable RigProfile profile) {
        return profile == null && routeFallbackStore != null && routeFallbackStore.usePhoneFallback();
    }

    private boolean shouldUsePhoneMicrophoneRx() {
        RxInputSettingsStore.RxInputMode inputMode = rxInputSettingsStore == null
                ? RxInputSettingsStore.RxInputMode.AUTO
                : rxInputSettingsStore.rxInputMode();
        if (inputMode == RxInputSettingsStore.RxInputMode.PHONE_MICROPHONE) {
            return true;
        }
        if (inputMode == RxInputSettingsStore.RxInputMode.USB_EXTERNAL_AUDIO) {
            return false;
        }
        return usePhoneFallbackRoute(rigSelectionStore.selectedProfile());
    }

    private boolean shouldUseUsbExternalRx() {
        RxInputSettingsStore.RxInputMode inputMode = rxInputSettingsStore == null
                ? RxInputSettingsStore.RxInputMode.AUTO
                : rxInputSettingsStore.rxInputMode();
        if (inputMode == RxInputSettingsStore.RxInputMode.USB_EXTERNAL_AUDIO) {
            return true;
        }
        if (inputMode == RxInputSettingsStore.RxInputMode.PHONE_MICROPHONE) {
            return false;
        }
        return rigSelectionStore != null && rigSelectionStore.selectedProfile() != null;
    }

    private void initializeOperateRxPipeline() {
        rebuildOperateRxSources();
        operateAudioInputHealthTracker = new AudioInputHealthTracker();
        operateRxCore = new RxCoreComponents();
        operateSignalProcessor = operateRxCore.signalProcessor();
        operateFrameSignalRunner = new RxFrameSignalRunner(
                operateAudioInputHealthTracker,
                operateSignalProcessor
        );
        operateTimingModel = operateRxCore.timingModel();
        operateLiveRxWpmGuard = operateRxCore.liveRxWpmGuard();
        operateToneEventStabilizer = operateRxCore.toneEventStabilizer();
        operateFrontEndLearningGate = operateRxCore.frontEndLearningGate();
        operateRxTurnController = operateRxCore.turnController();
        operateTimingAnchorController = operateRxCore.timingAnchorController();
        operateRawCommitGate = operateRxCore.rawCommitGate();
        operateCommittedDecodeController = new RxCommittedDecodeController(
                operateSignalProcessor,
                operateTimingModel,
                operateLiveRxWpmGuard,
                operateRxTurnController,
                operateTimingAnchorController,
                operateRawCommitGate
        );
        operateTimingDecodeRunner = operateRxCore.timingDecodeRunner();
        operateToneTimingRunner = operateRxCore.toneTimingRunner();
        operateLiveRxTraceRecorder = new LiveRxTraceRecorder(this, liveRxTraceStore);
        syncOperateTimingSeedWpm();
        operateDecoder = operateRxCore.decoder();
        operateRawInterpreter = operateRxCore.rawInterpreter();
        operateUnknownFallbackTracker = new RxUnknownFallbackTracker();
        operateCommittedOutputController = new RxCommittedOutputController(
                operateRawInterpreter,
                operateUnknownFallbackTracker,
                null,
                null,
                operateRawCommitGate
        );
        operateTurnSessionFinalizer = new RxTurnSessionFinalizer(
                operateRxCore.turnTailRepairController(),
                operateCommittedOutputController
        );
        operateTurnSessionCoordinator = new RxTurnSessionCoordinator(
                operateSignalProcessor,
                operateTimingModel,
                operateLiveRxWpmGuard,
                operateRxTurnController,
                operateTimingAnchorController,
                operateRawCommitGate,
                operateTurnSessionFinalizer,
                operateToneEventStabilizer,
                null
        );
        operateAudioSpectrumAnalyzer = new AudioSpectrumAnalyzer();
        syncOperatePreferredTone();
        syncOperateFixedToneLearningWindow();
        syncOperateRxToneMode();
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void maybeEnsureMicrophonePermission() {
        if (!shouldUsePhoneMicrophoneRx() || hasMicrophonePermission()) {
            return;
        }
        requestMicrophonePermission(false);
    }

    private void requestMicrophonePermission(boolean userInitiated) {
        if (hasMicrophonePermission() || microphonePermissionLauncher == null) {
            return;
        }
        if (!userInitiated && microphonePermissionRequestedThisSession) {
            return;
        }
        microphonePermissionRequestedThisSession = true;
        if (operateUiPreferences != null) {
            operateUiPreferences.edit().putBoolean(PREF_MIC_PERMISSION_ASKED, true).apply();
        }
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void syncOperateRxEngine() {
        rebuildOperateRxSourcesIfNeeded();
        syncOperatePreferredTone();
        syncOperateFixedToneLearningWindow();
        syncOperateRxToneMode();
        syncOperateSql();
        syncOperateTimingSeedWpm();
        if (shouldPausePhoneRxDuringImmediateTx()) {
            stopOperateRxCapture(false);
            return;
        }
        if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
            stopOperateRxCapture(false);
            clearOperateRxPresentationState();
            return;
        }
        startOperateRxCapture();
    }

    private void syncOperatePreferredTone() {
        if (operateSignalProcessor == null || rigSelectionStore == null) {
            return;
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(rigSelectionStore.selectedProfile());
        operateSignalProcessor.setPreferredToneFrequencyHz(settings.defaultToneFrequencyHz());
    }

    private void syncOperateFixedToneLearningWindow() {
        if (operateSignalProcessor == null || rxInputSettingsStore == null) {
            return;
        }
        operateSignalProcessor.setFixedToneLearningWindowHz(
                rxInputSettingsStore.fixedToneLearningWindowHz()
        );
    }

    private void syncOperateRxToneMode() {
        syncOperateRxToneMode(-1L);
    }

    private void syncOperateRxToneMode(long nowElapsedMs) {
        if (operateSignalProcessor == null || rxInputSettingsStore == null) {
            return;
        }
        operateSignalProcessor.setRxToneMode(resolveEffectiveOperateRxToneMode(nowElapsedMs));
    }

    private CwSignalProcessor.RxToneMode resolveEffectiveOperateRxToneMode(long nowElapsedMs) {
        if (rxInputSettingsStore == null) {
            return CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        RxInputSettingsStore.RxToneMode configuredToneMode = rxInputSettingsStore.rxToneMode();
        if (configuredToneMode != RxInputSettingsStore.RxToneMode.AUTO_TRACK) {
            return CwSignalProcessor.RxToneMode.FIXED_TONE;
        }
        // Keep the opening conservative until local timing trust exists.
        // This avoids early auto-track drift smearing the first characters of a turn.
        boolean trustedTimingEstablished = operateTimingAnchorController != null
                && operateTimingAnchorController.trustedDotEstimateMs() > 0L;
        return RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                trustedTimingEstablished,
                operateRxTurnController,
                operateSignalProcessor == null ? null : operateSignalProcessor.snapshot(),
                nowElapsedMs
        );
    }

    private void syncOperateSql() {
        if (operateSignalProcessor == null) {
            return;
        }
        operateSignalProcessor.setSqlPercent(sqlLevel);
    }

    private void startOperateRxCapture() {
        RxAudioSource source = activeOperateRxAudioSource;
        if (source == null) {
            return;
        }
        if (source.state() == RxAudioSource.State.RUNNING
                || source.state() == RxAudioSource.State.STARTING) {
            return;
        }
        startOperateLiveTraceSession(source);
        source.start();
    }

    private void stopOperateRxCapture(boolean publishFinalSnapshot) {
        RxAudioSource source = activeOperateRxAudioSource;
        if (source == null) {
            return;
        }
        RxAudioSource.State state = source.state();
        if (state == RxAudioSource.State.IDLE || state == RxAudioSource.State.STOPPING) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        flushOperateToneEventStabilizer(
                nowElapsedMs,
                operateAudioInputHealthTracker == null ? null : operateAudioInputHealthTracker.snapshot()
        );
        flushOperatePendingDecode(nowElapsedMs);
        traceOperateTurnFinalization(
                operateTurnSessionFinalizer == null
                        ? null
                        : operateTurnSessionFinalizer.finalizeCurrentTurn(nowElapsedMs),
                nowElapsedMs,
                "stop"
        );
        if (!isRxTranscriptSuppressed()) {
            finalizeActiveRxTranscriptEntryFromCurrentSnapshot(System.currentTimeMillis());
        }
        if (publishFinalSnapshot) {
            publishOperateSessionSnapshot(false, true);
        }
        finishOperateLiveTraceSession("stop");
        source.stop();
    }

    private void rebuildOperateRxSourcesIfNeeded() {
        if (rxInputSettingsStore == null) {
            return;
        }
        RxInputSettingsStore.RxInputMode desiredInputMode = rxInputSettingsStore.rxInputMode();
        RxInputSettingsStore.MicSourceMode desiredMode = rxInputSettingsStore.micSourceMode();
        if (desiredMode == activeMicSourceMode
                && desiredInputMode == activeRxInputMode
                && operateMicrophoneRxAudioSource != null
                && operateUsbExternalRxAudioSource != null
                && activeOperateRxAudioSource != null) {
            return;
        }
        RxAudioSource currentSource = activeOperateRxAudioSource;
        boolean restartNeeded = currentSource != null
                && (currentSource.state() == RxAudioSource.State.RUNNING
                || currentSource.state() == RxAudioSource.State.STARTING);
        if (restartNeeded) {
            stopOperateRxCapture(false);
        }
        rebuildOperateRxSources();
        if (restartNeeded && (!shouldUsePhoneMicrophoneRx() || hasMicrophonePermission())) {
            startOperateRxCapture();
        }
    }

    private void rebuildOperateRxSources() {
        RxInputSettingsStore.MicSourceMode desiredMode = rxInputSettingsStore == null
                ? RxInputSettingsStore.MicSourceMode.UNPROCESSED
                : rxInputSettingsStore.micSourceMode();
        RxInputSettingsStore.RxInputMode desiredInputMode = rxInputSettingsStore == null
                ? RxInputSettingsStore.RxInputMode.AUTO
                : rxInputSettingsStore.rxInputMode();
        if (operateMicrophoneRxAudioSource != null) {
            operateMicrophoneRxAudioSource.release();
        }
        if (operateUsbExternalRxAudioSource != null) {
            operateUsbExternalRxAudioSource.release();
        }
        activeMicSourceMode = desiredMode;
        activeRxInputMode = desiredInputMode;
        operateMicrophoneRxAudioSource = new MicrophoneRxAudioSource(this, desiredMode);
        operateMicrophoneRxAudioSource.setCallback(this);
        operateUsbExternalRxAudioSource = new UsbExternalRxAudioSource(this, desiredMode);
        operateUsbExternalRxAudioSource.setCallback(this);
        activeOperateRxAudioSource = shouldUseUsbExternalRx()
                ? operateUsbExternalRxAudioSource
                : operateMicrophoneRxAudioSource;
    }

    private void clearOperateRxPresentationState() {
        if (rxSessionStore != null) {
            rxSessionStore.clear();
        }
        if (operateAudioInputHealthTracker != null) {
            operateAudioInputHealthTracker.reset();
        }
        if (operateRxCore != null) {
            operateRxCore.resetRuntimeState(resolveOperateWpm());
            syncOperatePreferredTone();
            syncOperateRxToneMode();
            syncOperateSql();
        }
        if (operateCommittedOutputController != null) {
            operateCommittedOutputController.reset();
        }
        finishOperateLiveTraceSession("clear");
        lastOperateStableDecodeAtElapsedMs = -1L;
        lastOperateTimingResetAtElapsedMs = -1L;
        lastRxSessionSnapshot = null;
        activeRxTranscriptEntryId = -1L;
        activeRxTranscriptStartedAtEpochMs = 0L;
        activeRxTranscriptBaselineText = "";
        transcriptRxSuppressedDuringTx = false;
        immediateTxPausedRxCapture = false;
    }

    private boolean isRxTranscriptSuppressed() {
        return transcriptRxSuppressedDuringTx && txSendInProgress;
    }

    private boolean shouldPausePhoneRxDuringImmediateTx() {
        return txSendInProgress
                && shouldUsePhoneMicrophoneRx()
                && hasMicrophonePermission()
                && immediateTxPausedRxCapture;
    }

    private String resolveOperateRouteSummary(@Nullable RigProfile profile) {
        return RigRouteStatusFormatter.describeOperateRouteSummary(
                profile,
                shouldUsePhoneMicrophoneRx(),
                hasMicrophonePermission()
        );
    }

    private String resolveTxRouteLabel(@Nullable RigProfile profile) {
        return RigRouteStatusFormatter.describeTxRouteLabel(
                profile,
                usePhoneFallbackRoute(profile)
        );
    }

    private String resolveOperateRxHint(@Nullable RigProfile profile) {
        return RigRouteStatusFormatter.describeOperateRxHint(
                profile,
                shouldUsePhoneMicrophoneRx(),
                hasMicrophonePermission()
        );
    }

    private String safeCompact(String value, int maxLength) {
        String safe = safeValue(value);
        if ("-".equals(safe) || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 1)) + "~";
    }

    private String renderTemplatePreview() {
        String templateText = rawTemplateText(selectedTemplate);
        if (templateText.length() <= 20) {
            return "模板预览: " + templateText;
        }
        return "模板预览: " + templateText.substring(0, 17) + "...";
    }

    private String renderCallsignHint(@Nullable RxSessionSnapshot snapshot) {
        List<String> candidates = resolveUiFriendlyCallsignCandidates(snapshot);
        String selectedCallsign = trimToNull(selectedOperateRemoteCallsign);
        if (selectedCallsign != null) {
            StringBuilder builder = new StringBuilder("对方呼号: ")
                    .append(selectedCallsign);
            builder.append("  |  已人工选定");
            if (!candidates.isEmpty()) {
                if (candidates.contains(selectedCallsign)) {
                    if (candidates.size() > 1) {
                        builder.append("  |  另 ")
                                .append(candidates.size() - 1)
                                .append(" 个候选");
                    }
                } else {
                    builder.append("  |  当前 RX 候选 ")
                            .append(candidates.get(0));
                    if (candidates.size() > 1) {
                        builder.append(" 等 ")
                                .append(candidates.size())
                                .append(" 个");
                    }
                }
            }
            String context = renderCallsignHintContext(snapshot);
            if (hasMeaningfulText(context)) {
                builder.append("  |  ").append(context.trim());
            }
            builder.append("  |  点按切换当前目标  |  长按写入日志");
            return builder.toString();
        }
        if (candidates.isEmpty()) {
            return "候选呼号: 等待更清晰的 RX turn";
        }
        String primary = candidates.get(0);
        StringBuilder builder = new StringBuilder("候选呼号: ")
                .append(primary.trim());
        if (candidates.size() > 1) {
            builder.append("  |  另 ").append(candidates.size() - 1).append(" 个候选");
        }
        String context = renderCallsignHintContext(snapshot);
        if (hasMeaningfulText(context)) {
            builder.append("  |  ").append(context.trim());
        }
        builder.append(candidates.size() > 1
                ? "  |  点按切换当前目标  |  长按写入日志"
                : "  |  点按设为当前目标  |  长按写入日志");
        return builder.toString();
    }

    @Nullable
    private String resolvePrimaryCallsignHint(@Nullable RxSessionSnapshot snapshot) {
        List<String> candidates = resolveUiFriendlyCallsignCandidates(snapshot);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<String> resolveUiFriendlyCallsignCandidates(@Nullable RxSessionSnapshot snapshot) {
        LinkedHashSet<String> orderedCandidates = new LinkedHashSet<>();
        CwInterpreterSnapshot rawSnapshot = operateCommittedOutputController == null
                ? null
                : operateCommittedOutputController.rawSnapshot();
        if (rawSnapshot != null) {
            appendUiFriendlyCallsignCandidate(orderedCandidates, rawSnapshot.primaryCallsignCandidate());
            if (rawSnapshot.callsignCandidates() != null) {
                for (String candidate : rawSnapshot.callsignCandidates()) {
                    appendUiFriendlyCallsignCandidate(orderedCandidates, candidate);
                }
            }
        }
        if (snapshot != null) {
            appendUiFriendlyCallsignCandidate(orderedCandidates, snapshot.primaryCallsignCandidate());
        }
        return new ArrayList<>(orderedCandidates);
    }

    private void appendUiFriendlyCallsignCandidate(
            LinkedHashSet<String> orderedCandidates,
            @Nullable String candidate
    ) {
        if (!isUiFriendlyCallsignCandidate(candidate)) {
            return;
        }
        orderedCandidates.add(candidate.trim().toUpperCase(Locale.US));
    }

    private String renderCallsignHintContext(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        ArrayList<String> parts = new ArrayList<>();
        if (snapshot.effectiveToneFrequencyHz() > 0) {
            parts.add(snapshot.effectiveToneFrequencyHz() + "Hz");
        }
        int displayWpm = resolveDisplayWpm(snapshot);
        if (displayWpm > 0) {
            parts.add(displayWpm + "WPM");
        }
        if (snapshot.hasDistinctFallbackSuggestedText()) {
            parts.add("有兜底候选");
        }
        return parts.isEmpty() ? "" : String.join("  |  ", parts);
    }

    private boolean isUiFriendlyCallsignCandidate(@Nullable String candidate) {
        if (!hasMeaningfulText(candidate)) {
            return false;
        }
        String normalized = candidate.trim().toUpperCase(Locale.US);
        if (normalized.length() > 8) {
            return false;
        }
        if (!normalized.matches("[A-Z0-9?]+")) {
            return false;
        }
        boolean hasDigit = false;
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isDigit(normalized.charAt(index))) {
                hasDigit = true;
                break;
            }
        }
        return hasDigit;
    }

    private void handleCallsignHintTap() {
        List<String> candidates = resolveUiFriendlyCallsignCandidates(lastRxSessionSnapshot);
        if (candidates.isEmpty()) {
            if (hasMeaningfulText(selectedOperateRemoteCallsign)) {
                new AlertDialog.Builder(this)
                        .setTitle("当前已选呼号")
                        .setMessage("当前没有新的 RX 候选。\n\n已选对方呼号: "
                                + selectedOperateRemoteCallsign
                                + "\n\n如需改写日志请长按；如需取消人工选择可在这里清除。")
                        .setPositiveButton("清除选择", (dialog, which) ->
                                clearSelectedOperateRemoteCallsign(true))
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            Toast.makeText(this, "当前没有可选的候选呼号。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (candidates.size() == 1) {
            selectOperateRemoteCallsign(candidates.get(0), true);
            return;
        }
        showCallsignCandidatePicker(candidates, false);
    }

    private void openQsoEditorFromCallsignHint() {
        if (localLogRepository == null) {
            Toast.makeText(this, "日志仓库当前不可用。", Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedCallsign = trimToNull(selectedOperateRemoteCallsign);
        if (selectedCallsign != null) {
            openQsoEditorForCallsign(selectedCallsign);
            return;
        }
        List<String> candidates = resolveUiFriendlyCallsignCandidates(lastRxSessionSnapshot);
        if (candidates.isEmpty()) {
            Toast.makeText(this, "当前没有可用的候选呼号。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (candidates.size() == 1) {
            openQsoEditorForCallsign(candidates.get(0));
            return;
        }
        showCallsignCandidatePicker(candidates, true);
    }

    private void showCallsignCandidatePicker(List<String> candidates, boolean openEditorAfterPick) {
        String[] candidateItems = candidates.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(openEditorAfterPick ? "选择候选呼号并写入日志" : "选择当前对方呼号")
                .setMessage(openEditorAfterPick
                        ? "基于当前 RAW 候选，选择后进入日志编辑。"
                        : "基于当前 RAW 候选，选择后将作为模板与日志种子的对方呼号。")
                .setItems(candidateItems, (dialog, which) -> {
                    if (which < 0 || which >= candidateItems.length) {
                        return;
                    }
                    if (openEditorAfterPick) {
                        openQsoEditorForCallsign(candidateItems[which]);
                        return;
                    }
                    selectOperateRemoteCallsign(candidateItems[which], true);
                })
                .setNegativeButton("取消", null);
        if (!openEditorAfterPick && hasMeaningfulText(selectedOperateRemoteCallsign)) {
            builder.setNeutralButton("清除已选", (dialog, which) ->
                    clearSelectedOperateRemoteCallsign(true));
        }
        builder.show();
    }

    private void openQsoEditorForCallsign(String callsign) {
        openQsoEditorForCallsign(callsign, null);
    }

    private void openQsoEditorForCallsign(String callsign, @Nullable String commentSeed) {
        if (localLogRepository == null) {
            Toast.makeText(this, "日志仓库当前不可用。", Toast.LENGTH_SHORT).show();
            return;
        }
        String normalizedCallsign = trimToNull(callsign);
        if (normalizedCallsign == null) {
            Toast.makeText(this, "当前呼号无效。", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedOperateRemoteCallsign = normalizedCallsign.toUpperCase(Locale.US);
        long seedTimestamp = resolveCallsignHintTimestamp(lastRxSessionSnapshot);
        String stationCallsign = trimToNull(
                stationProfileStore == null ? null : stationProfileStore.stationCallsign()
        );
        String rawContext = resolveCallsignHintRawContext(lastRxSessionSnapshot);
        ArrayList<String> hints = new ArrayList<>();
        hints.add("seeded-from-operate");
        hints.add("candidate-selected-manually");
        QsoDraftSnapshot seededDraft = new QsoDraftSnapshot(
                QsoPhase.IDLE,
                stationCallsign,
                normalizedCallsign,
                null,
                null,
                null,
                null,
                stationCallsign != null,
                true,
                false,
                false,
                false,
                false,
                rawContext == null ? "" : rawContext,
                hints,
                false,
                normalizedCallsign.contains("?"),
                seedTimestamp,
                new QsoStateEvent(seedTimestamp, QsoPhase.IDLE, "seeded from operate callsign hint")
        );
        localLogRepository.saveDraft(seededDraft);
        refreshUi();
        Intent intent = new Intent(this, QsoEditorActivity.class);
        if (hasMeaningfulText(commentSeed)) {
            intent.putExtra(QsoEditorActivity.EXTRA_SEED_COMMENT, commentSeed.trim());
        }
        startActivity(intent);
    }

    private void selectOperateRemoteCallsign(String callsign, boolean showToast) {
        String normalized = trimToNull(callsign);
        if (normalized == null) {
            return;
        }
        selectedOperateRemoteCallsign = normalized.toUpperCase(Locale.US);
        if (showToast) {
            Toast.makeText(
                    this,
                    "已选对方呼号: " + selectedOperateRemoteCallsign,
                    Toast.LENGTH_SHORT
            ).show();
        }
        refreshUi();
    }

    private void clearSelectedOperateRemoteCallsign(boolean showToast) {
        if (!hasMeaningfulText(selectedOperateRemoteCallsign)) {
            return;
        }
        selectedOperateRemoteCallsign = null;
        if (showToast) {
            Toast.makeText(this, "已清除人工选择的对方呼号。", Toast.LENGTH_SHORT).show();
        }
        refreshUi();
    }

    private long resolveCallsignHintTimestamp(@Nullable RxSessionSnapshot snapshot) {
        if (activeRxTranscriptStartedAtEpochMs > 0L) {
            return activeRxTranscriptStartedAtEpochMs;
        }
        if (snapshot != null && snapshot.updatedAtEpochMs() > 0L) {
            return snapshot.updatedAtEpochMs();
        }
        return System.currentTimeMillis();
    }

    @Nullable
    private String resolveCallsignHintRawContext(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        String rawText = normalizedTextOrNull(snapshot.rawText());
        if (rawText != null) {
            return rawText;
        }
        String fallbackText = normalizedTextOrNull(snapshot.fallbackSuggestedText());
        if (fallbackText != null) {
            return fallbackText;
        }
        return null;
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String renderReceiveMeta(RxSessionSnapshot snapshot) {
        ArrayList<String> parts = new ArrayList<>();
        if (snapshot.captureActive()) {
            parts.add("实时");
        }
        parts.add(renderFriendlyOperateSourceLabel(snapshot.sourceLabel()));
        parts.add(renderAge(snapshot.updatedAtEpochMs()));
        return String.join("  |  ", parts);
    }

    private boolean isOperateRxRawOnlyMode() {
        return OPERATE_RX_RAW_ONLY_MODE;
    }

    private void consumeOperateDecodeEvent(@Nullable CwDecodeEvent decodeEvent) {
        if (decodeEvent == null
                || operateCommittedOutputController == null
                || operateCommittedDecodeController == null
                || operateTurnSessionFinalizer == null) {
            return;
        }
        boolean stableDecodeAccepted = shouldTreatAsStableOperateDecode(decodeEvent);
        if (stableDecodeAccepted) {
            lastOperateStableDecodeAtElapsedMs = SystemClock.elapsedRealtime();
        }
        List<CwDecodeEvent> admittedEvents = operateCommittedDecodeController.admit(
                decodeEvent,
                stableDecodeAccepted
        );
        for (CwDecodeEvent admittedEvent : admittedEvents) {
            traceOperateDecodeEvent(admittedEvent);
            operateTurnSessionFinalizer.processCommittedDecodeEvent(admittedEvent);
            if (!isRxTranscriptSuppressed()) {
                updateActiveRxTranscriptEntryFromDecodeEvent(admittedEvent, System.currentTimeMillis());
            }
        }
    }

    @Nullable
    private String normalizedTextOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    private void setVisibleWhenHasText(TextView view) {
        if (view == null) {
            return;
        }
        CharSequence text = view.getText();
        boolean visible = text != null && text.toString().trim().length() > 0;
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void selectTemplate(String template, boolean populateComposer) {
        selectedTemplate = template;
        syncTemplateSelector(template);
        updateTemplateButtonStates();
        if (populateComposer) {
            binding.txComposeEditText.setText(buildTemplateText(template));
            binding.txComposeEditText.setSelection(binding.txComposeEditText.getText() == null
                    ? 0
                    : binding.txComposeEditText.getText().length());
        }
        updateComposerUi();
        updateOverlayContent();
    }

    private void updateTemplateButtonStates() {
        boolean cq = TEMPLATE_CQ.equals(selectedTemplate);
        boolean cqDx = TEMPLATE_REPLY.equals(selectedTemplate);
        boolean qrz = TEMPLATE_QRZ.equals(selectedTemplate);
        boolean tu73 = TEMPLATE_TU73.equals(selectedTemplate);
        applyTemplateButtonState(binding.overlayTemplateCqButton, cq);
        applyTemplateButtonState(binding.overlayTemplateCqDxButton, cqDx);
        applyTemplateButtonState(binding.overlayTemplateQrzButton, qrz);
        applyTemplateButtonState(binding.overlayTemplateTu73Button, tu73);
    }

    private void applyTemplateButtonState(View view, boolean selected) {
        view.setBackgroundResource(selected
                ? R.drawable.operate_chip_active_background
                : R.drawable.operate_chip_background);
    }

    private String buildTemplateText(String template) {
        String rawTemplate = rawTemplateText(template);
        return renderTemplateVariables(rawTemplate);
    }

    private String rawTemplateText(String template) {
        return txTemplateStore == null
                ? defaultTemplateFor(template)
                : txTemplateStore.resolveTemplate(template);
    }

    private String defaultTemplateFor(String template) {
        if (TEMPLATE_REPLY.equals(template)) {
            return TxTemplateStore.DEFAULT_REPLY;
        }
        if (TEMPLATE_QRZ.equals(template)) {
            return TxTemplateStore.DEFAULT_QRZ;
        }
        if (TEMPLATE_TU73.equals(template)) {
            return TxTemplateStore.DEFAULT_TU73;
        }
        return TxTemplateStore.DEFAULT_CQ;
    }

    private String resolveStationCallsign() {
        String saved = stationProfileStore == null ? null : stationProfileStore.stationCallsign();
        if (hasMeaningfulText(saved)) {
            return saved.trim();
        }
        return "<MYCALL>";
    }

    private String renderTemplateVariables(String rawTemplate) {
        if (!hasMeaningfulText(rawTemplate)) {
            return "";
        }
        String rendered = rawTemplate;
        rendered = replaceTemplateAliases(rendered, resolveStationCallsign(), "MYCALL", "MYCALLSIGN");
        rendered = replaceTemplateAliases(rendered, resolveRemoteCallsign(), "CALL", "CALLSIGN", "HISCALL");
        rendered = replaceTemplateAliases(rendered, resolveRstReceived(), "RST_RECV", "RST_RCVD", "MYRST");
        rendered = replaceTemplateAliases(rendered, resolveRstSent(), "RST", "RST_SENT", "URRST");
        rendered = replaceTemplateAliases(rendered, resolveStationName(), "NAME");
        rendered = replaceTemplateAliases(rendered, resolveStationQth(), "QTH");
        rendered = replaceTemplateAliases(rendered, resolveStationGrid(), "GRID", "MYGRID");
        rendered = replaceTemplateAliases(rendered, resolveStationRig(), "RIG");
        rendered = replaceTemplateAliases(rendered, resolveStationAntenna(), "ANT", "ANTENNA");
        rendered = replaceTemplateAliases(rendered, resolveStationWeather(), "WX", "WEATHER");
        return rendered;
    }

    private String replaceTemplateAliases(String text, String value, String... aliases) {
        if (text == null || aliases == null || aliases.length == 0) {
            return text;
        }
        String resolved = value;
        for (String alias : aliases) {
            if (!hasMeaningfulText(alias)) {
                continue;
            }
            String trimmedAlias = alias.trim();
            String fallback = "<" + trimmedAlias + ">";
            String replacement = hasMeaningfulText(resolved) ? resolved.trim() : fallback;
            text = text.replace("<" + trimmedAlias + ">", replacement);
            text = text.replace("<" + trimmedAlias.toLowerCase(Locale.US) + ">", replacement);
            text = text.replace("<" + trimmedAlias.toUpperCase(Locale.US) + ">", replacement);
            text = text.replace("{" + trimmedAlias + "}", replacement);
            text = text.replace("{" + trimmedAlias.toLowerCase(Locale.US) + "}", replacement);
            text = text.replace("{" + trimmedAlias.toUpperCase(Locale.US) + "}", replacement);
        }
        return text;
    }

    private String resolveStationName() {
        String saved = stationProfileStore == null ? null : stationProfileStore.operatorName();
        return hasMeaningfulText(saved) ? saved.trim() : "<NAME>";
    }

    private String resolveRemoteCallsign() {
        String selected = trimToNull(selectedOperateRemoteCallsign);
        if (selected != null) {
            return selected;
        }
        return "<CALL>";
    }

    private String resolveRstSent() {
        return "<RST>";
    }

    private String resolveRstReceived() {
        return "<RST_RCVD>";
    }

    private String resolveStationQth() {
        String saved = stationProfileStore == null ? null : stationProfileStore.qth();
        return hasMeaningfulText(saved) ? saved.trim() : "<QTH>";
    }

    private String resolveStationGrid() {
        String saved = stationProfileStore == null ? null : stationProfileStore.maidenheadGrid();
        return hasMeaningfulText(saved) ? saved.trim() : "<GRID>";
    }

    private String resolveStationRig() {
        String saved = stationProfileStore == null ? null : stationProfileStore.rigDescription();
        return hasMeaningfulText(saved) ? saved.trim() : "<RIG>";
    }

    private String resolveStationAntenna() {
        String saved = stationProfileStore == null ? null : stationProfileStore.antennaDescription();
        return hasMeaningfulText(saved) ? saved.trim() : "<ANT>";
    }

    private String resolveStationWeather() {
        String saved = stationProfileStore == null ? null : stationProfileStore.weatherDescription();
        return hasMeaningfulText(saved) ? saved.trim() : "<WX>";
    }

    private void syncTemplateSelector(String template) {
        if (binding == null || binding.templateSelectorSpinner == null) {
            return;
        }
        int index = 0;
        for (int itemIndex = 0; itemIndex < TEMPLATE_OPTIONS.length; itemIndex++) {
            if (TEMPLATE_OPTIONS[itemIndex].equals(template)) {
                index = itemIndex;
                break;
            }
        }
        templateSelectorSyncing = true;
        binding.templateSelectorSpinner.setSelection(index, false);
        templateSelectorSyncing = false;
    }

    private void toggleOverlay(OverlayMode mode) {
        if (overlayMode == mode && binding.overlayPanel.getVisibility() == View.VISIBLE) {
            hideOverlay();
            return;
        }
        overlayMode = mode;
        binding.overlayScrim.setVisibility(View.VISIBLE);
        binding.overlayPanel.setVisibility(View.VISIBLE);
        binding.sideRail.setVisibility(View.GONE);
        updateOverlayContent();
    }

    private void hideOverlay() {
        overlayMode = OverlayMode.NONE;
        binding.overlayScrim.setVisibility(View.GONE);
        binding.overlayPanel.setVisibility(View.GONE);
        binding.sideRail.setVisibility(View.VISIBLE);
        updateSideButtonStates();
    }

    private void updateOverlayContent() {
        updateSideButtonStates();
        if (overlayMode == OverlayMode.NONE) {
            return;
        }
        binding.chartOverlayContent.setVisibility(overlayMode == OverlayMode.CHART ? View.VISIBLE : View.GONE);
        binding.sqlOverlayContent.setVisibility(overlayMode == OverlayMode.SQL ? View.VISIBLE : View.GONE);
        binding.templateOverlayContent.setVisibility(overlayMode == OverlayMode.TEMPLATE ? View.VISIBLE : View.GONE);

        switch (overlayMode) {
            case CHART:
                binding.overlayTitleText.setText("接收视图");
                binding.overlaySubtitleText.setText("当前接收快照");
                binding.chartOverlayText.setText(renderChartOverlayText(lastRxSessionSnapshot));
                break;
            case SQL:
                binding.overlayTitleText.setText("调谐");
                binding.overlaySubtitleText.setText("调整接收门限与发射速度");
                syncSqlSeekBar();
                syncWpmSeekBarFromSettings();
                binding.sqlOverlayText.setText(renderSqlOverlayText(lastRxSessionSnapshot));
                break;
            case TEMPLATE:
                binding.overlayTitleText.setText("模板");
                binding.overlaySubtitleText.setText("当前待发送文本");
                binding.templateOverlayText.setText(renderTemplateOverlayText());
                break;
            default:
                break;
        }
    }

    private void updateSideButtonStates() {
        applySideButtonState(binding.sideChartButton, overlayMode == OverlayMode.CHART);
        applySideButtonState(binding.sideSqlButton, overlayMode == OverlayMode.SQL);
        applySideButtonState(binding.sideTemplateButton, overlayMode == OverlayMode.TEMPLATE);
        applyTemplateButtonState(binding.sqlQuickChip, overlayMode == OverlayMode.SQL);
    }

    private void applySideButtonState(View view, boolean active) {
        view.setBackgroundResource(active
                ? R.drawable.operate_side_button_active_background
                : R.drawable.operate_side_button_background);
    }

    private String renderChartOverlayText(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "当前没有活动接收会话。\n\n启用接收后，这里会显示当前接收快照。";
        }
        String callsignHint = resolvePrimaryCallsignHint(snapshot);
        return "状态: " + safeValue(snapshot.captureState())
                + "\n来源: " + safeValue(snapshot.sourceLabel())
                + "\nRAW: " + (snapshot.captureActive() ? "接收中" : "保持中")
                + "\n速度: " + positiveOrDash(resolveDisplayWpm(snapshot)) + " WPM"
                + "\n音调 偏好/跟踪/有效: "
                + positiveOrDash(snapshot.preferredToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.targetToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.effectiveToneFrequencyHz())
                + " Hz"
                + "\n呼号提示: " + safeValue(callsignHint)
                + "\n输入: " + safeValue(snapshot.inputHealthLabel())
                + "\n更新时间: " + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderSqlQuickChipText() {
        return "SQL " + sqlLevel;
    }

    private String renderSqlOverlayText(@Nullable RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("SQL 门限: ").append(sqlLevel).append("%")
                .append("\n发射速度: ").append(resolveOperateWpm()).append(" WPM");
        SqlThresholdAdvisor.Recommendation recommendation = SqlThresholdAdvisor.recommend(lastUiSpectrumSnapshot);
        if (recommendation.available()) {
            builder.append("\n系统建议线: ").append(recommendation.recommendedThresholdLevel())
                    .append("  |  噪声 ").append(recommendation.noiseFloorLevel());
            if (recommendation.limitedBySafetyFloor()) {
                builder.append("  |  受系统下限保护");
            } else if (recommendation.limitedByToneHeadroom()) {
                builder.append("  |  为弱 CW 预留过线空间");
            }
        }
        if (snapshot == null) {
            builder.append("\n\n当前没有活动接收会话。");
            return builder.toString();
        }
        builder.append("\n\n把这组值作为当前收发调谐参考。")
                .append("\n接收速度: ").append(positiveOrDash(resolveDisplayWpm(snapshot))).append(" WPM")
                .append("  |  音调: ").append(positiveOrDash(resolveBestTone(snapshot))).append(" Hz")
                .append("\n输入状态: ").append(safeValue(snapshot.inputHealthLabel()))
                .append("\n呼号提示: ").append(safeValue(resolvePrimaryCallsignHint(snapshot)))
                .append("\n更新时间: ").append(renderAge(snapshot.updatedAtEpochMs()));
        if (shouldShowDeveloperFrontEndSummary(snapshot)) {
            builder.append("\n前端门控: ").append(snapshot.developerFrontEndSummary());
        }
        return builder.toString();
    }

    private int resolveDisplayWpm(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.stableEstimatedWpm() > 0 ? snapshot.stableEstimatedWpm() : snapshot.estimatedWpm();
    }

    @Nullable
    private SpectrumSnapshotData latestSpectrumSnapshotForUi() {
        if (spectrumHistoryStore == null) {
            return null;
        }
        List<SpectrumSnapshotData> history = spectrumHistoryStore.loadHistory();
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    private String renderInputHealthPrefix(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.inputLevelClipping()) {
            return "输入削顶 | ";
        }
        if (snapshot.inputLevelHot()) {
            return "输入偏热 | ";
        }
        return "";
    }

    private String renderTemplateOverlayText() {
        String rawTemplate = rawTemplateText(selectedTemplate);
        String renderedTemplate = buildTemplateText(selectedTemplate);
        return "当前模板: " + selectedTemplate
                + "\n\n模板原文:\n"
                + rawTemplate
                + "\n\n展开后:\n"
                + renderedTemplate;
    }

    @Override
    public void onStateChanged(RxAudioSource.State state, String detail) {
        operateRxState = state == null ? RxAudioSource.State.IDLE : state;
        if (operateLiveRxTraceRecorder != null) {
            operateLiveRxTraceRecorder.recordState(
                    operateRxState.name(),
                    detail,
                    SystemClock.elapsedRealtime()
            );
            if (operateRxState == RxAudioSource.State.IDLE || operateRxState == RxAudioSource.State.ERROR) {
                finishOperateLiveTraceSession(operateRxState == RxAudioSource.State.ERROR ? "error" : "idle");
            }
        }
        if (operateRxState == RxAudioSource.State.IDLE
                && activeOperateRxAudioSource != null
                && (!shouldUsePhoneMicrophoneRx() || hasMicrophonePermission())) {
            publishOperateSessionSnapshot(false, true);
        } else if (operateRxState == RxAudioSource.State.ERROR) {
            publishOperateSessionSnapshot(false, true);
        }
        mainHandler.post(this::refreshUi);
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        if (frame == null
                || operateFrameSignalRunner == null
                || operateSignalProcessor == null
                || operateTimingModel == null
                || operateDecoder == null
                || operateRawInterpreter == null) {
            return;
        }
        RxFrameSignalRunner.Result frameSignalResult = operateFrameSignalRunner.processFrame(
                frame,
                SystemClock.elapsedRealtime()
        );
        if (frameSignalResult == null) {
            return;
        }
        AudioInputHealthSnapshot inputHealthSnapshot = frameSignalResult.inputHealthSnapshot();
        traceOperateFrame(frame, inputHealthSnapshot);

        long frameEndTimestampMs = frameSignalResult.frameEndTimestampMs();
        syncOperateRxToneMode(frameEndTimestampMs);
        maybeHandleOperateTurnTransition(
                frameSignalResult.signalSnapshotAfterProcess(),
                frameEndTimestampMs
        );
        captureOperateSpectrumSnapshot(frame);
        for (CwToneEvent toneEvent : frameSignalResult.toneEvents()) {
            routeOperateToneEvent(toneEvent, inputHealthSnapshot);
        }
        flushOperateToneEventStabilizer(frameEndTimestampMs, inputHealthSnapshot);
        maybeFlushPendingCharacterDuringSilence(frame, frameEndTimestampMs);
        operateTimingModel.observeClock(frameEndTimestampMs);
        maybeResetOperateTimingAfterIdle(frame, frameEndTimestampMs);
        publishOperateSessionSnapshot(true, false);
    }

    private void captureOperateSpectrumSnapshot(AudioFrame frame) {
        if (frame == null || operateSignalProcessor == null || operateAudioSpectrumAnalyzer == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        lastOperateSpectrumSnapshot = operateAudioSpectrumAnalyzer.process(
                frame,
                signalSnapshot.preferredToneFrequencyHz(),
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.toneHypothesisFrequencyHz(),
                signalSnapshot.preferredWindowWinnerFrequencyHz(),
                signalSnapshot.wideScanWinnerFrequencyHz(),
                signalSnapshot.acquisitionWinnerFrequencyHz(),
                signalSnapshot.finalAdoptedFrequencyHz(),
                signalSnapshot.acquisitionWinnerSource(),
                signalSnapshot.finalAdoptedSource(),
                signalSnapshot.hypothesisGuardExperimentEnabled(),
                signalSnapshot.hypothesisGuardApplied(),
                signalSnapshot.hypothesisGuardAppliedFrequencyHz(),
                signalSnapshot.hypothesisGuardDecision()
        );
        publishOperateSpectrumSnapshot();
    }

    private void publishOperateSpectrumSnapshot() {
        if (spectrumHistoryStore == null || lastOperateSpectrumSnapshot == null) {
            return;
        }
        SpectrumSnapshotData snapshotData = SpectrumSnapshotData.fromAudioSnapshot(
                lastOperateSpectrumSnapshot,
                System.currentTimeMillis(),
                operateSignalProcessor == null ? null : operateSignalProcessor.snapshot()
        );
        if (snapshotData != null) {
            spectrumHistoryStore.append(snapshotData);
        }
    }

    @Override
    public void onError(String message, Throwable throwable) {
        operateRxState = RxAudioSource.State.ERROR;
        if (operateLiveRxTraceRecorder != null) {
            operateLiveRxTraceRecorder.recordState(
                    RxAudioSource.State.ERROR.name(),
                    message,
                    SystemClock.elapsedRealtime()
            );
        }
        finishOperateLiveTraceSession("error");
        publishOperateSessionSnapshot(false, true);
        mainHandler.post(this::refreshUi);
    }

    @Nullable
    private CwTimingEvent prepareOperateTimingEventForDecode(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot currentSignalSnapshot,
            @Nullable CwTimingSnapshot currentTimingSnapshot
    ) {
        if (timingEvent == null
                || operateTimingModel == null
                || operateRawInterpreter == null) {
            return null;
        }
        maybeNoteOperateBootstrapCadenceObservation(timingEvent);
        maybeNoteOperateBootstrapTimingBoundary(timingEvent);
        if (operateRawCommitGate != null) {
            operateRawCommitGate.noteTimingEvent(
                    timingEvent,
                    RxStableDecodeDecider.hasTrustedTiming(operateTimingAnchorController),
                    operateTimingAnchorController == null
                            ? TimingAnchorController.TrustOrigin.NONE
                            : operateTimingAnchorController.trustOrigin(),
                    operateTimingAnchorController == null
                            ? 0L
                            : operateTimingAnchorController.trustedDotEstimateMs(),
                    operateTimingAnchorController == null
                            ? -1L
                            : operateTimingAnchorController.lastTrustedUpdateTimestampMs()
            );
        }
        return adaptOperateTimingEvent(
                timingEvent,
                currentSignalSnapshot,
                currentTimingSnapshot
        );
    }

    private void routeOperateToneEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (toneEvent == null || operateTimingModel == null || operateSignalProcessor == null) {
            return;
        }
        if (operateToneEventStabilizer == null) {
            dispatchOperateToneEvent(toneEvent, inputHealthSnapshot);
            return;
        }
        CwSignalSnapshot currentSignalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = operateTimingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveOperateReferenceDotEstimateMs(
                currentSignalSnapshot,
                currentTimingSnapshot,
                toneEvent.timestampMs()
        );
        List<CwToneEvent> stabilizedEvents = operateToneEventStabilizer.process(
                toneEvent,
                currentSignalSnapshot,
                inputHealthSnapshot,
                referenceDotEstimateMs
        );
        for (CwToneEvent stabilizedEvent : stabilizedEvents) {
            traceOperateToneEvent(stabilizedEvent);
            dispatchOperateToneEvent(stabilizedEvent, inputHealthSnapshot);
        }
    }

    private void flushOperateToneEventStabilizer(
            long nowTimestampMs,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (operateToneEventStabilizer == null) {
            return;
        }
        List<CwToneEvent> stabilizedEvents = operateToneEventStabilizer.flush(nowTimestampMs);
        for (CwToneEvent stabilizedEvent : stabilizedEvents) {
            traceOperateToneEvent(stabilizedEvent);
            dispatchOperateToneEvent(stabilizedEvent, inputHealthSnapshot);
        }
    }

    private void dispatchOperateToneEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (toneEvent == null
                || operateTimingModel == null
                || operateSignalProcessor == null) {
            return;
        }
        if (shouldSuppressOperateTimingToneEvent(toneEvent, inputHealthSnapshot)) {
            return;
        }
        if (operateTurnSessionFinalizer != null) {
            operateTurnSessionFinalizer.noteToneEvent(toneEvent);
        }
        CwSignalSnapshot currentSignalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = operateTimingModel.rawSnapshot();
        boolean allowTimingLearning = shouldAllowOperateTimingLearningForEvent(
                toneEvent,
                currentSignalSnapshot,
                currentTimingSnapshot
        );
        if (operateToneTimingRunner == null) {
            return;
        }
        final CwSignalSnapshot[] finalSignalSnapshot = new CwSignalSnapshot[1];
        final CwTimingSnapshot[] finalTimingSnapshot = new CwTimingSnapshot[1];
        operateToneTimingRunner.dispatchToneEvents(
                Collections.singletonList(toneEvent),
                event -> {
                    List<CwTimingEvent> timingEvents = operateTimingModel.process(
                            event,
                            allowTimingLearning
                    );
                    finalSignalSnapshot[0] = operateSignalProcessor.snapshot();
                    finalTimingSnapshot[0] = operateTimingModel.rawSnapshot();
                    return timingEvents;
                },
                null,
                timingEvent -> {
                    traceOperateTimingEvent(timingEvent, finalTimingSnapshot[0]);
                    return prepareOperateTimingEventForDecode(
                            timingEvent,
                            finalSignalSnapshot[0],
                            finalTimingSnapshot[0]
                    );
                },
                this::consumeOperateDecodeEvent
        );
    }

    private void maybeNoteOperateBootstrapTimingBoundary(@Nullable CwTimingEvent timingEvent) {
        CwSignalSnapshot signalSnapshot = operateSignalProcessor == null
                ? null
                : operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel == null
                ? null
                : operateTimingModel.rawSnapshot();
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        if (RxBootstrapTimingObserver.maybeNoteBootstrapTimingBoundary(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                operateTimingModel,
                operateLiveRxWpmGuard,
                operateTimingAnchorController,
                operateFrontEndLearningGate,
                operateRxTurnController
        )) {
            lastOperateStableDecodeAtElapsedMs = SystemClock.elapsedRealtime();
        }
    }

    private void maybeNoteOperateBootstrapCadenceObservation(@Nullable CwTimingEvent timingEvent) {
        CwSignalSnapshot signalSnapshot = operateSignalProcessor == null
                ? null
                : operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel == null
                ? null
                : operateTimingModel.rawSnapshot();
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        RxBootstrapTimingObserver.maybeNoteBootstrapCadenceObservation(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                operateTimingModel,
                operateLiveRxWpmGuard,
                operateTimingAnchorController,
                operateFrontEndLearningGate
        );
    }

    private boolean shouldTreatAsStableOperateDecode(@Nullable CwDecodeEvent decodeEvent) {
        if (decodeEvent == null
                || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                || operateSignalProcessor == null
                || operateTimingModel == null) {
            return false;
        }
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        if (signalSnapshot == null || timingSnapshot == null) {
            return false;
        }
        if (timingSnapshot.estimatedWpm() <= 0 || timingSnapshot.dotEstimateMs() <= 0L) {
            return false;
        }
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        return RxStableDecodeClassifier.passesSimpleStableDecode(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                operateFrontEndLearningGate,
                operateTimingAnchorController
        );
    }

    private CwTimingEvent adaptOperateTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        long nowElapsedMs = timingEvent == null
                ? SystemClock.elapsedRealtime()
                : timingEvent.timestampMs();
        CwTimingEvent adaptedTimingEvent = timingEvent;
        if (operateLiveRxWpmGuard != null) {
            adaptedTimingEvent = operateLiveRxWpmGuard.adaptTimingEvent(
                    adaptedTimingEvent,
                    signalSnapshot,
                    timingSnapshot,
                    nowElapsedMs
            );
        }
        if (operateTimingAnchorController != null) {
            adaptedTimingEvent = operateTimingAnchorController.adaptTimingEvent(
                    adaptedTimingEvent,
                    signalSnapshot,
                    timingSnapshot,
                    nowElapsedMs
            );
        }
        return adaptedTimingEvent;
    }

    private void flushOperatePendingDecode(long timestampMs) {
        if (operateTimingModel == null || operateDecoder == null) {
            return;
        }
        CwSignalSnapshot currentSignalSnapshot = operateSignalProcessor == null
                ? null
                : operateSignalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = operateTimingModel.rawSnapshot();
        boolean allowTimingLearning = shouldAllowOperateTimingLearning(
                currentSignalSnapshot,
                currentTimingSnapshot,
                timestampMs
        );
        List<CwTimingEvent> timingEvents = operateTimingModel.flushPendingGap(timestampMs, allowTimingLearning);
        currentSignalSnapshot = operateSignalProcessor == null
                ? null
                : operateSignalProcessor.snapshot();
        currentTimingSnapshot = operateTimingModel.rawSnapshot();
        if (operateTimingDecodeRunner != null) {
            CwSignalSnapshot finalSignalSnapshot = currentSignalSnapshot;
            CwTimingSnapshot finalTimingSnapshot = currentTimingSnapshot;
            operateTimingDecodeRunner.dispatchTimingEvents(
                    timingEvents,
                    timingEvent -> {
                        traceOperateTimingEvent(timingEvent, finalTimingSnapshot);
                        return prepareOperateTimingEventForDecode(
                                timingEvent,
                                finalSignalSnapshot,
                                finalTimingSnapshot
                        );
                    },
                    this::consumeOperateDecodeEvent
            );
            operateTimingDecodeRunner.flushPendingCharacter(
                    timestampMs,
                    this::consumeOperateDecodeEvent
            );
        }
    }

    private void maybeFlushPendingCharacterDuringSilence(
            AudioFrame frame,
            long frameEndTimestampMs
    ) {
        if (frame == null
                || operateTimingDecodeRunner == null
                || operateTimingModel == null
                || operateSignalProcessor == null
                || !operateTimingDecodeRunner.hasPendingCharacter()) {
            return;
        }
        long flushTimestampMs = RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(
                frame,
                frameEndTimestampMs
        );
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        maybeHandleOperateTurnTransition(signalSnapshot, flushTimestampMs);
        RxPendingCharacterFlushDecider.Decision flushDecision =
                RxPendingCharacterFlushDecider.evaluate(
                        frame,
                        flushTimestampMs,
                        signalSnapshot,
                        minimumOperateCharacterFlushGapMs(flushTimestampMs),
                        RxPendingCharacterFlushDecider.ActivityPolicy.MEANINGFUL_TURN_ACTIVITY
                );
        if (!flushDecision.shouldFlush()) {
            return;
        }
        operateTimingDecodeRunner.flushPendingCharacter(
                flushDecision.flushTimestampMs(),
                this::consumeOperateDecodeEvent
        );
    }

    private long minimumOperateCharacterFlushGapMs(long timelineTimestampMs) {
        if (operateTimingModel == null) {
            return 1L;
        }
        CwSignalSnapshot signalSnapshot = operateSignalProcessor == null
                ? null
                : operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        long dotEstimateMs = operateLiveRxWpmGuard == null
                ? Math.max(1L, timingSnapshot.dotEstimateMs())
                : Math.max(1L, operateLiveRxWpmGuard.resolveEffectiveDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                timelineTimestampMs > 0L ? timelineTimestampMs : SystemClock.elapsedRealtime()
        ));
        return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
    }

    private boolean shouldSuppressOperateTimingToneEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (toneEvent == null
                || toneEvent.type() != CwToneEvent.Type.TONE_OFF
                || operateTimingModel == null
                || operateSignalProcessor == null) {
            return false;
        }
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        if (signalSnapshot == null) {
            return false;
        }
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        if (timingSnapshot == null) {
            return false;
        }
        long referenceDotEstimateMs = resolveOperateReferenceDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                toneEvent.timestampMs()
        );
        if (referenceDotEstimateMs <= 0L || operateToneEventStabilizer == null) {
            return false;
        }
        return operateToneEventStabilizer.shouldSuppressShortTone(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                referenceDotEstimateMs
        );
    }

    private void maybeResetOperateTimingAfterIdle(
            @Nullable AudioFrame frame,
            long frameEndTimestampMs
    ) {
        if (frame == null
                || operateTimingModel == null
                || operateSignalProcessor == null
                || operateDecoder == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        if (signalSnapshot == null) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        long turnTimestampMs = frameEndTimestampMs > 0L
                ? frameEndTimestampMs
                : estimateFrameEndTimestampMs(frame);
        maybeHandleOperateTurnTransition(signalSnapshot, turnTimestampMs);
        if (RxTurnActivityDecider.isMeaningfulTurnActivity(signalSnapshot)
                || operateDecoder.hasPendingCharacter()) {
            return;
        }
        if (lastOperateTimingResetAtElapsedMs > 0L
                && (nowElapsedMs - lastOperateTimingResetAtElapsedMs) < OPERATE_RX_TIMING_COOLDOWN_RESET_MS) {
            return;
        }
        long stableDecodeIdleMs = lastOperateStableDecodeAtElapsedMs <= 0L
                ? Long.MAX_VALUE
                : nowElapsedMs - lastOperateStableDecodeAtElapsedMs;
        if (stableDecodeIdleMs >= OPERATE_RX_FRONTEND_IDLE_RESET_MS
                && signalSnapshot.recentLockedFrameRatio() <= OPERATE_RX_LOW_LOCKED_RATIO_MAX) {
            traceOperateMarker("frontend-reset", "low-lock idle reset", nowElapsedMs);
            if (operateSignalProcessor != null) {
                operateSignalProcessor.reset();
                syncOperatePreferredTone();
                syncOperateRxToneMode(nowElapsedMs);
                syncOperateSql();
            }
            operateTimingModel.softReset();
            if (operateLiveRxWpmGuard != null) {
                operateLiveRxWpmGuard.softReset();
            }
            if (operateToneEventStabilizer != null) {
                operateToneEventStabilizer.reset();
            }
            lastOperateTimingResetAtElapsedMs = nowElapsedMs;
            return;
        }
        if (stableDecodeIdleMs < OPERATE_RX_STABLE_DECODE_IDLE_RESET_MS) {
            return;
        }
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        if (timingSnapshot == null) {
            return;
        }
        int referenceWpm = resolveOperateTimingReferenceWpm(timingSnapshot);
        if (referenceWpm <= 0 || timingSnapshot.estimatedWpm() <= referenceWpm + OPERATE_RX_WPM_RESET_DELTA) {
            return;
        }
        if (signalSnapshot.recentLockedFrameRatio() > OPERATE_RX_LOW_LOCKED_RATIO_MAX) {
            return;
        }
        traceOperateMarker("timing-reset", "runaway idle reset", nowElapsedMs);
        if (operateSignalProcessor != null) {
            operateSignalProcessor.reset();
            syncOperatePreferredTone();
            syncOperateRxToneMode(nowElapsedMs);
            syncOperateSql();
        }
        operateTimingModel.softReset();
        if (operateLiveRxWpmGuard != null) {
            operateLiveRxWpmGuard.softReset();
        }
        if (operateToneEventStabilizer != null) {
            operateToneEventStabilizer.reset();
        }
        lastOperateTimingResetAtElapsedMs = nowElapsedMs;
    }

    private void maybeHandleOperateTurnTransition(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowElapsedMs
    ) {
        if (operateTurnSessionCoordinator == null
                || operateTimingModel == null
                || signalSnapshot == null) {
            return;
        }
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        int referenceWpm = resolveOperateTimingReferenceWpm(timingSnapshot);
        RxTurnSessionCoordinator.Observation observation = operateTurnSessionCoordinator.observe(
                signalSnapshot,
                false,
                nowElapsedMs,
                referenceWpm
        );
        if (observation.startedNewTurn()) {
            syncOperateRxToneMode(nowElapsedMs);
            beginActiveRxTranscriptTurn(System.currentTimeMillis());
            traceOperateMarker("turn-start", observation.reason(), nowElapsedMs);
        } else if (observation.endedTurn()) {
            traceOperateTurnFinalization(
                    observation.turnFinalization(),
                    nowElapsedMs,
                    "turn-end"
            );
            finalizeActiveRxTranscriptEntryFromCurrentSnapshot(System.currentTimeMillis());
            traceOperateMarker("turn-end", observation.reason(), nowElapsedMs);
            if (observation.frontEndResetApplied()) {
                syncOperatePreferredTone();
                syncOperateRxToneMode(nowElapsedMs);
                syncOperateSql();
            }
        }
    }

    private void traceOperateTurnFinalization(
            @Nullable RxTurnSessionFinalizer.TurnFinalization turnFinalization,
            long flushTimestampMs,
            String reason
    ) {
        if (turnFinalization == null) {
            return;
        }
        RxTrailingWindowRepair.RepairResult repairResult = turnFinalization.repairResult();
        traceOperateMarker(
                "tail-repair",
                safeValue(reason)
                        + " base=" + safeCompact(repairResult.baseTailText(), 28)
                        + " repaired=" + safeCompact(repairResult.repairedTailText(), 28),
                flushTimestampMs
        );
    }

    private int resolveOperateTimingReferenceWpm(@Nullable CwTimingSnapshot timingSnapshot) {
        if (operateLiveRxWpmGuard != null) {
            int referenceWpm = operateLiveRxWpmGuard.resolveReferenceWpm(timingSnapshot);
            if (referenceWpm > 0) {
                return referenceWpm;
            }
        }
        if (timingSnapshot != null && timingSnapshot.estimatedWpm() > 0) {
            return timingSnapshot.estimatedWpm();
        }
        return 0;
    }

    private long resolveOperateReferenceDotEstimateMs(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timelineTimestampMs
    ) {
        if (operateLiveRxWpmGuard != null) {
            long referenceDotEstimateMs = operateLiveRxWpmGuard.resolveReferenceDotEstimateMs(timingSnapshot);
            if (referenceDotEstimateMs > 0L) {
                return referenceDotEstimateMs;
            }
            return operateLiveRxWpmGuard.resolveEffectiveDotEstimateMs(
                    signalSnapshot,
                    timingSnapshot,
                    timelineTimestampMs > 0L ? timelineTimestampMs : SystemClock.elapsedRealtime()
            );
        }
        return timingSnapshot == null ? 0L : timingSnapshot.dotEstimateMs();
    }

    private boolean shouldAllowOperateTimingLearning(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timelineTimestampMs
    ) {
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        if (operateFrontEndLearningGate != null
                && !operateFrontEndLearningGate.shouldAllowTimingLearning(
                signalSnapshot,
                inputHealthSnapshot
        )) {
            return false;
        }
        if (operateLiveRxWpmGuard == null) {
            return operateTimingAnchorController == null
                    || operateTimingAnchorController.shouldAllowTimingLearning(
                    signalSnapshot,
                    timingSnapshot,
                    true,
                    timelineTimestampMs > 0L ? timelineTimestampMs : SystemClock.elapsedRealtime()
            );
        }
        long nowElapsedMs = timelineTimestampMs > 0L
                ? timelineTimestampMs
                : SystemClock.elapsedRealtime();
        boolean baseAllow = operateLiveRxWpmGuard.shouldAllowTimingLearning(
                signalSnapshot,
                timingSnapshot,
                nowElapsedMs
        );
        return operateTimingAnchorController == null
                || operateTimingAnchorController.shouldAllowTimingLearning(
                signalSnapshot,
                timingSnapshot,
                baseAllow,
                nowElapsedMs
        );
    }

    private boolean shouldAllowOperateTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                operateTimingAnchorController
        );
        if (operateFrontEndLearningGate != null
                && !operateFrontEndLearningGate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                trustedTimingEstablished
        )) {
            return false;
        }
        if (operateLiveRxWpmGuard == null) {
            return operateTimingAnchorController == null
                    || operateTimingAnchorController.shouldAllowTimingLearningForEvent(
                    toneEvent,
                    signalSnapshot,
                    timingSnapshot,
                    toneEvent != null,
                    toneEvent == null ? SystemClock.elapsedRealtime() : toneEvent.timestampMs()
            );
        }
        long nowElapsedMs = toneEvent == null
                ? SystemClock.elapsedRealtime()
                : toneEvent.timestampMs();
        boolean baseAllow = operateLiveRxWpmGuard.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                nowElapsedMs
        );
        return operateTimingAnchorController == null
                || operateTimingAnchorController.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                baseAllow,
                nowElapsedMs
        );
    }

    private long estimateFrameEndTimestampMs(@Nullable AudioFrame frame) {
        return RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(
                frame,
                SystemClock.elapsedRealtime()
        );
    }

    private void publishOperateSessionSnapshot(boolean throttle, boolean force) {
        if (rxSessionStore == null
                || operateSignalProcessor == null
                || operateTimingModel == null
                || operateRawInterpreter == null) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        if (!force && throttle && (nowElapsedMs - lastOperateRxPublishAtElapsedMs) < OPERATE_RX_PUBLISH_INTERVAL_MS) {
            return;
        }

        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        CwInterpreterSnapshot rawSnapshot = operateCommittedOutputController == null
                ? null
                : operateCommittedOutputController.rawSnapshot();
        RxUnknownFallbackSuggestion fallbackSuggestion = operateCommittedOutputController == null
                ? RxUnknownFallbackSuggestion.none(rawSnapshot == null ? "" : rawSnapshot.rawText())
                : operateCommittedOutputController.fallbackSuggestion();
        AudioInputHealthSnapshot inputHealthSnapshot = operateAudioInputHealthTracker == null
                ? null
                : operateAudioInputHealthTracker.snapshot();
        int stableEstimatedWpm = operateLiveRxWpmGuard == null
                ? timingSnapshot.estimatedWpm()
                : operateLiveRxWpmGuard.resolveDisplayWpm(
                signalSnapshot,
                timingSnapshot,
                nowElapsedMs
        );
        List<String> rawCallsignCandidates = resolveUiFriendlyCallsignCandidates(lastRxSessionSnapshot);
        String rawPrimaryCallsign = rawCallsignCandidates.isEmpty() ? "" : rawCallsignCandidates.get(0);
        RxSessionSnapshot sessionSnapshot = new RxSessionSnapshot(
                System.currentTimeMillis(),
                activeOperateRxAudioSource == null ? "RX Input" : activeOperateRxAudioSource.displayName(),
                operateRxState.name(),
                operateRxState == RxAudioSource.State.RUNNING || operateRxState == RxAudioSource.State.STARTING,
                signalSnapshot.preferredToneFrequencyHz(),
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.effectiveTrackedToneFrequencyHz(),
                timingSnapshot.estimatedWpm(),
                stableEstimatedWpm,
                rawSnapshot == null ? "" : rawSnapshot.rawText(),
                fallbackSuggestion == null ? "" : fallbackSuggestion.suggestedText(),
                fallbackSuggestion == null ? "" : fallbackSuggestion.notesText(),
                rawSnapshot == null ? "" : rawSnapshot.normalizedText(),
                rawPrimaryCallsign == null ? "" : rawPrimaryCallsign,
                inputHealthSnapshot == null ? "" : AudioInputHealthFormatter.summaryLabel(inputHealthSnapshot),
                inputHealthSnapshot == null ? "" : AudioInputHealthFormatter.coachHint(inputHealthSnapshot),
                inputHealthSnapshot != null && inputHealthSnapshot.recentHotFrameRatio() >= 0.50d,
                inputHealthSnapshot != null && inputHealthSnapshot.recentClippingFrameRatio() >= 0.10d,
                renderDeveloperFrontEndSummary(signalSnapshot, timingSnapshot, nowElapsedMs)
        );
        rxSessionStore.save(sessionSnapshot);
        lastOperateRxPublishAtElapsedMs = nowElapsedMs;
        lastRxSessionSnapshot = sessionSnapshot;
        if (!isRxTranscriptSuppressed()) {
            refreshActiveRxTranscriptEntryFromSnapshot(sessionSnapshot);
        }
    }

    private String renderDeveloperFrontEndSummary(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        StringBuilder builder = new StringBuilder();
        if (operateTimingModel != null) {
            builder.append("tm ").append(operateTimingModel.debugStrategySummary());
        }
        if (operateLiveRxWpmGuard != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(operateLiveRxWpmGuard.compactDebugSummary(
                    signalSnapshot,
                    timingSnapshot,
                    nowElapsedMs
            ));
        }
        if (operateRxTurnController != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(operateRxTurnController.compactDebugSummary());
        }
        if (operateTimingAnchorController != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(operateTimingAnchorController.compactDebugSummary());
        }
        if (operateToneEventStabilizer != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(operateToneEventStabilizer.stats().compactSummary());
        }
        return builder.toString();
    }

    private void startOperateLiveTraceSession(@Nullable RxAudioSource source) {
        if (operateLiveRxTraceRecorder == null
                || developerModeStore == null
                || !developerModeStore.isEnabled()) {
            return;
        }
        int preferredToneHz = operateSignalProcessor == null
                ? 0
                : operateSignalProcessor.snapshot().preferredToneFrequencyHz();
        operateLiveRxTraceRecorder.startSession(
                source == null ? "RX Input" : source.displayName(),
                preferredToneHz,
                sqlLevel
        );
    }

    private void finishOperateLiveTraceSession(String reason) {
        if (operateLiveRxTraceRecorder == null) {
            return;
        }
        operateLiveRxTraceRecorder.finishSession(reason);
    }

    private void traceOperateFrame(
            @Nullable AudioFrame frame,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (operateLiveRxTraceRecorder == null || operateSignalProcessor == null || operateTimingModel == null) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        int displayWpm = operateLiveRxWpmGuard == null
                ? timingSnapshot.estimatedWpm()
                : operateLiveRxWpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        operateLiveRxTraceRecorder.recordFrame(
                frame,
                inputHealthSnapshot,
                signalSnapshot,
                timingSnapshot,
                displayWpm,
                renderDeveloperFrontEndSummary(signalSnapshot, timingSnapshot, nowElapsedMs)
        );
    }

    private void traceOperateToneEvent(@Nullable CwToneEvent toneEvent) {
        if (operateLiveRxTraceRecorder == null || operateSignalProcessor == null) {
            return;
        }
        operateLiveRxTraceRecorder.recordToneEvent(toneEvent, operateSignalProcessor.snapshot());
    }

    private void traceOperateTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (operateLiveRxTraceRecorder == null) {
            return;
        }
        operateLiveRxTraceRecorder.recordTimingEvent(timingEvent, timingSnapshot);
    }

    private void traceOperateDecodeEvent(@Nullable CwDecodeEvent decodeEvent) {
        if (operateLiveRxTraceRecorder == null || operateSignalProcessor == null || operateTimingModel == null) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.rawSnapshot();
        int displayWpm = operateLiveRxWpmGuard == null
                ? timingSnapshot.estimatedWpm()
                : operateLiveRxWpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        operateLiveRxTraceRecorder.recordDecodeEvent(
                decodeEvent,
                displayWpm,
                renderDeveloperFrontEndSummary(signalSnapshot, timingSnapshot, nowElapsedMs)
        );
    }

    private void traceOperateMarker(String label, @Nullable String detail, long timestampMs) {
        if (operateLiveRxTraceRecorder == null) {
            return;
        }
        operateLiveRxTraceRecorder.recordMarker(label, detail, timestampMs);
    }

    private boolean shouldShowDeveloperFrontEndSummary(@Nullable RxSessionSnapshot snapshot) {
        return snapshot != null
                && developerModeStore != null
                && developerModeStore.isEnabled()
                && snapshot.hasDeveloperFrontEndSummary();
    }

    private String renderDeveloperFrontEndSuffix(@Nullable RxSessionSnapshot snapshot) {
        if (!shouldShowDeveloperFrontEndSummary(snapshot)) {
            return "";
        }
        return " | " + snapshot.developerFrontEndSummary();
    }

    private void startImmediateTx() {
        if (txSendInProgress) {
            return;
        }
        String txText = resolveImmediateTxText();
        if (txText == null || txText.trim().isEmpty()) {
            setTxDeliveryStatus("没有可发射的文本");
            return;
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigControlAdapter adapter = resolveImmediateTxAdapter(profile);
        if (adapter == null) {
            setTxDeliveryStatus(usePhoneFallbackRoute(profile)
                    ? "手机发射链路不可用"
                    : "设置中没有可用发射链路");
            return;
        }
        configureImmediateTxAdapter(adapter, profile);
        if (!adapter.isReady()) {
            setTxDeliveryStatus(adapter.describeAvailability());
            return;
        }
        CwTxPlan txPlan = buildActiveTxPlan(txText, profile);
        txSendInProgress = true;
        txStopRequested = false;
        txDeliveryStatus = "";
        activeTxAdapter = adapter;
        activeTxText = txText;
        activeTxRouteLabel = adapter.displayName();
        activeTxPlan = txPlan;
        activeTxPlaybackSnapshot = initialPlaybackSnapshot(txPlan);
        activeTxStopSupported = adapterSupportsStop(adapter);
        transcriptRxSuppressedDuringTx = true;
        finalizeActiveRxTranscriptEntryFromCurrentSnapshot(System.currentTimeMillis());
        immediateTxPausedRxCapture = false;
        if (shouldUsePhoneMicrophoneRx() && hasMicrophonePermission()) {
            RxAudioSource source = activeOperateRxAudioSource;
            if (source != null
                    && (source.state() == RxAudioSource.State.RUNNING
                    || source.state() == RxAudioSource.State.STARTING)) {
                immediateTxPausedRxCapture = true;
                stopOperateRxCapture(false);
            }
        }
        beginActiveTxTranscriptEntry(System.currentTimeMillis());
        mainHandler.removeCallbacks(txProgressRefreshRunnable);
        mainHandler.post(txProgressRefreshRunnable);
        updateComposerUi();
        new Thread(() -> {
            boolean sent = adapter.sendText(txText);
            String status;
            if (txStopRequested) {
                status = "发射已停止";
            } else {
                status = sent
                        ? "已通过 " + adapter.displayName() + " 发射"
                        : adapter.displayName() + " 发射失败: " + adapter.describeAvailability();
            }
            runOnUiThread(() -> {
                mainHandler.removeCallbacks(txProgressRefreshRunnable);
                syncActiveTxPlaybackSnapshot();
                txSendInProgress = false;
                txStopRequested = false;
                txDeliveryStatus = status;
                activeTxAdapter = null;
                activeTxPlaybackSnapshot = finalizePlaybackSnapshot(activeTxPlaybackSnapshot, sent, status);
                activeTxStopSupported = false;
                finalizeActiveTxTranscriptEntry(status);
                transcriptRxSuppressedDuringTx = false;
                if (immediateTxPausedRxCapture) {
                    immediateTxPausedRxCapture = false;
                    if (operateActivityResumed) {
                        syncOperateRxEngine();
                    }
                }
                updateComposerUi();
                refreshConversationOnly();
            });
        }, "operate-immediate-tx").start();
    }

    private void stopImmediateTx() {
        RigControlAdapter adapter = activeTxAdapter;
        if (!txSendInProgress || adapter == null) {
            return;
        }
        txStopRequested = true;
        syncActiveTxPlaybackSnapshot();
        if (activeTxStopSupported) {
            adapter.stopTextTransmission();
        }
        updateComposerUi();
    }

    private void stopImmediateTxForLifecycle() {
        RigControlAdapter adapter = activeTxAdapter;
        if (!txSendInProgress || adapter == null) {
            return;
        }
        txStopRequested = true;
        if (adapterSupportsStop(adapter)) {
            adapter.stopTextTransmission();
        }
    }

    public static void requestSharedOperateRxStop() {
        OperateActivity activeInstance = sharedActiveInstance.get();
        if (activeInstance == null) {
            return;
        }
        activeInstance.preserveRxAcrossSpectrumNavigation = false;
        activeInstance.stopOperateRxCapture(true);
        activeInstance.clearOperateRxPresentationState();
    }

    public static void requestSharedOperateRxResume() {
        OperateActivity activeInstance = sharedActiveInstance.get();
        if (activeInstance == null) {
            return;
        }
        activeInstance.preserveRxAcrossSpectrumNavigation = true;
        activeInstance.syncOperateRxEngine();
    }

    public static void requestSharedOperatePreferredToneUpdate(int frequencyHz) {
        OperateActivity activeInstance = sharedActiveInstance.get();
        if (activeInstance == null || activeInstance.operateSignalProcessor == null) {
            return;
        }
        activeInstance.operateSignalProcessor.setPreferredToneFrequencyHz(frequencyHz);
    }

    public static void requestSharedOperateSqlUpdate(int sqlLevel) {
        OperateActivity activeInstance = sharedActiveInstance.get();
        if (activeInstance == null || activeInstance.operateSignalProcessor == null) {
            return;
        }
        activeInstance.sqlLevel = Math.max(0, Math.min(100, sqlLevel));
        activeInstance.operateSignalProcessor.setSqlPercent(activeInstance.sqlLevel);
    }

    private void syncActiveTxPlaybackSnapshot() {
        RigControlAdapter adapter = activeTxAdapter;
        if (adapter == null) {
            return;
        }
        CwTxPlaybackSnapshot snapshot = adapter.currentTxPlaybackSnapshot();
        if (snapshot != null) {
            activeTxPlaybackSnapshot = snapshot;
        }
        refreshActiveTxTranscriptEntry();
    }

    @Nullable
    private String resolveImmediateTxText() {
        String composeText = binding.txComposeEditText.getText() == null
                ? ""
                : binding.txComposeEditText.getText().toString().trim();
        if (!composeText.isEmpty()) {
            return renderTemplateVariables(composeText).trim();
        }
        String templateText = buildTemplateText(selectedTemplate);
        return templateText == null ? "" : templateText.trim();
    }

    @Nullable
    private RigControlAdapter resolveImmediateTxAdapter(@Nullable RigProfile profile) {
        String desiredAdapterId;
        if (profile == null) {
            if (!usePhoneFallbackRoute(null)) {
                return null;
            }
            desiredAdapterId = "audio-vox-text";
        } else {
            desiredAdapterId = profile.adapterId();
        }
        if ("audio-vox".equals(desiredAdapterId)) {
            desiredAdapterId = "audio-vox-text";
        }
        if (desiredAdapterId == null || desiredAdapterId.trim().isEmpty()) {
            return null;
        }
        for (RigControlAdapter adapter : RigRegistry.defaultAdapters(this)) {
            if (desiredAdapterId.equals(adapter.id())) {
                return adapter;
            }
        }
        return null;
    }

    private void configureImmediateTxAdapter(RigControlAdapter adapter, @Nullable RigProfile profile) {
        if (adapter == null) {
            return;
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        if (adapter.supportsConfigurableTextToCwProfile()) {
            adapter.configureTextToCwProfile(
                    settings.defaultWpm(),
                    settings.defaultToneFrequencyHz()
            );
        }
        if (adapter instanceof UsbSerialKeyerRigControlAdapter) {
            UsbSerialKeyerRigControlAdapter usbAdapter = (UsbSerialKeyerRigControlAdapter) adapter;
            usbAdapter.setKeyLine(settings.usbKeyLine());
            usbAdapter.selectDevice(settings.usbPreferredDeviceName());
        }
    }

    @Nullable
    private CwTxPlan buildActiveTxPlan(String txText, @Nullable RigProfile profile) {
        if (!hasMeaningfulText(txText)) {
            return null;
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return new CwTxEngine().buildPlan(
                txText,
                settings.defaultWpm(),
                settings.defaultToneFrequencyHz()
        );
    }

    private boolean adapterSupportsStop(@Nullable RigControlAdapter adapter) {
        return adapter instanceof AudioVoxRigControlAdapter
                || adapter instanceof UsbSerialKeyerRigControlAdapter
                || adapter instanceof SerialCatRigControlAdapter;
    }

    private boolean requestOperateUsbPermissionIfNeeded() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerStatusAdapter(profile);
        if (adapter == null || adapter.isReady()) {
            return false;
        }
        try {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_USB_KEYER_PERMISSION).setPackage(getPackageName()),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT
            );
            boolean requested = adapter.requestUsbPermission(pendingIntent);
            operateUsbStatusMessage = requested
                    ? "USB 权限请求已发送，请在系统弹窗中允许。"
                    : "当前无法发起 USB 权限请求：" + adapter.describeAvailability();
        } catch (RuntimeException exception) {
            operateUsbStatusMessage = "USB 权限请求失败：" + safeThrowableMessage(exception);
        }
        refreshUi();
        return true;
    }

    private void registerUsbPermissionReceiver() {
        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (!ACTION_USB_KEYER_PERMISSION.equals(intent.getAction())) {
                    return;
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                operateUsbStatusMessage = granted
                        ? "USB 权限已授予。"
                        : "USB 权限被拒绝。";
                refreshUi();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_KEYER_PERMISSION);
        ContextCompat.registerReceiver(
                this,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Nullable
    private UsbSerialKeyerRigControlAdapter resolveUsbKeyerStatusAdapter(@Nullable RigProfile profile) {
        if (profile == null
                || !profile.hasCapability(org.bi9clt.cwcn.core.rig.RigCapability.KEY_LINE_CONTROL)) {
            return null;
        }
        RigControlAdapter adapter = resolveImmediateTxAdapter(profile);
        if (!(adapter instanceof UsbSerialKeyerRigControlAdapter)) {
            return null;
        }
        configureImmediateTxAdapter(adapter, profile);
        return (UsbSerialKeyerRigControlAdapter) adapter;
    }

    @Nullable
    private String renderOperateRouteReadinessHint(@Nullable RigProfile profile) {
        RigControlAdapter adapter = resolveImmediateTxAdapter(profile);
        configureImmediateTxAdapter(adapter, profile);
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigRouteStatusFormatter.describeRouteReadiness(
                profile,
                adapter,
                settings,
                profile != null && profile.hasCapability(org.bi9clt.cwcn.core.rig.RigCapability.KEY_LINE_CONTROL)
                        ? operateUsbStatusMessage
                        : null
        );
    }

    @Nullable
    private String renderUsbKeyerReadinessHint(@Nullable RigProfile profile) {
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerStatusAdapter(profile);
        return RigRouteStatusFormatter.describeUsbKeyerOperateHint(adapter, operateUsbStatusMessage);
    }

    private String safeThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "unknown failure" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private boolean hasRepeatableTxText() {
        return hasMeaningfulText(activeTxText) || hasMeaningfulText(resolveImmediateTxText());
    }

    private String renderPlaybackProgressSummary(@Nullable CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return "发射";
        }
        String state = renderPlaybackProgressChip(snapshot);
        String normalizedText = safeValue(snapshot.normalizedText());
        int length = "-".equals(normalizedText) ? 0 : normalizedText.length();
        int currentIndex = snapshot.currentTextIndex();
        if (length <= 0 || currentIndex < 0) {
            return state;
        }
        int progressed = Math.min(length, currentIndex + 1);
        return state + " " + progressed + "/" + length;
    }

    private String resolveVisibleActiveTxText(String fallbackDraftText) {
        if (hasMeaningfulText(activeTxText)) {
            return activeTxText.trim();
        }
        return fallbackDraftText == null ? "" : fallbackDraftText.trim();
    }

    private boolean shouldShowTransmitStream(String visibleTxText) {
        if (!hasMeaningfulText(visibleTxText)) {
            return false;
        }
        return (!txSendInProgress && hasMeaningfulText(txDeliveryStatus))
                || activeTxPlaybackSnapshot != null;
    }

    private CharSequence renderActiveTxStreamBody(String visibleTxText) {
        if (!hasMeaningfulText(visibleTxText)) {
            return "";
        }
        SpannableString styled = new SpannableString(visibleTxText);
        int bodyColor = ContextCompat.getColor(this, R.color.cwcn_body);
        int completedColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentIndex = activeTxPlaybackSnapshot == null ? -1 : activeTxPlaybackSnapshot.currentTextIndex();
        if (currentIndex < 0 || currentIndex >= visibleTxText.length()) {
            styled.setSpan(
                    new ForegroundColorSpan(bodyColor),
                    0,
                    visibleTxText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            return styled;
        }
        if (currentIndex > 0) {
            styled.setSpan(
                    new ForegroundColorSpan(completedColor),
                    0,
                    currentIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        styled.setSpan(
                new ForegroundColorSpan(currentColor),
                currentIndex,
                currentIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        styled.setSpan(
                new StyleSpan(android.graphics.Typeface.BOLD),
                currentIndex,
                currentIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        if (currentIndex + 1 < visibleTxText.length()) {
            styled.setSpan(
                    new ForegroundColorSpan(bodyColor),
                    currentIndex + 1,
                    visibleTxText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return styled;
    }

    private String renderActiveTxStreamMeta() {
        String route = hasMeaningfulText(activeTxRouteLabel) ? activeTxRouteLabel : renderTxRouteChip();
        String stage = activeTxPlaybackSnapshot == null ? "发射中" : renderPlaybackProgressChip(activeTxPlaybackSnapshot);
        return route + "  |  " + stage;
    }

    @Nullable
    private CwTxPlaybackSnapshot initialPlaybackSnapshot(@Nullable CwTxPlan plan) {
        if (plan == null) {
            return null;
        }
        return new CwTxPlaybackSnapshot(
                CwTxState.PLAYING,
                plan.normalizedText(),
                plan.morsePreview(),
                0,
                plan.elements().size(),
                0,
                plan.totalDurationMs(),
                "",
                -1,
                false,
                "发射已开始"
        );
    }

    @Nullable
    private CwTxPlaybackSnapshot finalizePlaybackSnapshot(
            @Nullable CwTxPlaybackSnapshot existingSnapshot,
            boolean sent,
            String status
    ) {
        if (activeTxPlan == null && existingSnapshot == null) {
            return null;
        }
        CwTxPlaybackSnapshot seed = existingSnapshot == null
                ? initialPlaybackSnapshot(activeTxPlan)
                : existingSnapshot;
        if (seed == null) {
            return null;
        }
        CwTxState state = sent ? CwTxState.COMPLETED : CwTxState.ERROR;
        if ("发射已停止".equals(status)) {
            state = CwTxState.STOPPED;
        }
        int totalElements = seed.totalElementCount();
        int completedElements = state == CwTxState.COMPLETED ? totalElements : seed.completedElementCount();
        int totalDuration = seed.totalDurationMs();
        int elapsedMs = state == CwTxState.COMPLETED ? totalDuration : seed.elapsedMs();
        return new CwTxPlaybackSnapshot(
                state,
                seed.normalizedText(),
                seed.morsePreview(),
                completedElements,
                totalElements,
                elapsedMs,
                totalDuration,
                "",
                -1,
                false,
                status
        );
    }

    private String renderPlaybackProgressChip(@Nullable CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return "发射";
        }
        if (snapshot.state() == CwTxState.STOPPED) {
            return "已停";
        }
        if (snapshot.state() == CwTxState.ERROR) {
            return "错误";
        }
        if (snapshot.state() == CwTxState.COMPLETED) {
            return "完成";
        }
        return "发射中";
    }

    private String renderPlaybackSnapshotStatus(@Nullable CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (hasMeaningfulText(snapshot.statusMessage())) {
            builder.append(snapshot.statusMessage().trim());
        } else {
            builder.append(snapshot.state().displayName());
        }
        if (snapshot.totalDurationMs() > 0) {
            builder.append("  |  ")
                    .append(snapshot.elapsedMs())
                    .append("/")
                    .append(snapshot.totalDurationMs())
                    .append("ms");
        }
        if (snapshot.currentElementLabel() != null && !snapshot.currentElementLabel().trim().isEmpty()) {
            builder.append("  |  当前字符 ").append(snapshot.currentElementLabel().trim());
        }
        return builder.toString();
    }

    private void setTxDeliveryStatus(String status) {
        txDeliveryStatus = status == null ? "" : status.trim();
        if (!txSendInProgress) {
            activeTxRouteLabel = "";
            activeTxPlan = null;
            activeTxStopSupported = false;
        }
        updateComposerUi();
    }

    private int resolveBestTone(RxSessionSnapshot snapshot) {
        if (snapshot.effectiveToneFrequencyHz() > 0) {
            return snapshot.effectiveToneFrequencyHz();
        }
        if (snapshot.targetToneFrequencyHz() > 0) {
            return snapshot.targetToneFrequencyHz();
        }
        return snapshot.preferredToneFrequencyHz();
    }

    private boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String yesNoZh(boolean value) {
        return value ? "是" : "否";
    }

    private String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private String renderAge(long updatedAtEpochMs) {
        if (updatedAtEpochMs <= 0L) {
            return "-";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - updatedAtEpochMs);
        long ageSeconds = ageMs / 1000L;
        if (ageSeconds < 60L) {
            return ageSeconds + "秒前";
        }
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) {
            return ageMinutes + "分钟前";
        }
        long ageHours = ageMinutes / 60L;
        return String.format(Locale.CHINA, "%d小时前", ageHours);
    }

    private void toggleTranscriptTimeMode() {
        transcriptUseUtc = !transcriptUseUtc;
        if (operateUiPreferences != null) {
            operateUiPreferences.edit().putBoolean(PREF_TRANSCRIPT_USE_UTC, transcriptUseUtc).apply();
        }
        binding.transcriptTimeModeChip.setText(transcriptUseUtc ? "UTC" : "Local");
        refreshConversationOnly();
    }

    private void clearTranscriptDisplay() {
        operateTranscriptEntries.clear();
        transcriptTimelineActive = true;
        activeTxTranscriptEntryId = -1L;
        activeRxTranscriptEntryId = -1L;
        activeRxTranscriptStartedAtEpochMs = 0L;
        activeRxTranscriptBaselineText = "";
        conversationAutoScrollPending = true;
        markConversationDirty();
        refreshConversationOnly();
    }

    private void beginActiveRxTranscriptTurn(long startedAtEpochMs) {
        if (activeRxTranscriptEntryId != -1L) {
            finalizeActiveRxTranscriptEntryFromCurrentSnapshot(startedAtEpochMs);
        }
        transcriptTimelineActive = true;
        CwInterpreterSnapshot rawSnapshot = operateCommittedOutputController == null
                ? null
                : operateCommittedOutputController.rawSnapshot();
        activeRxTranscriptBaselineText = rawSnapshot == null ? "" : safeTranscriptText(rawSnapshot.rawText());
        activeRxTranscriptStartedAtEpochMs = startedAtEpochMs <= 0L
                ? System.currentTimeMillis()
                : startedAtEpochMs;
        activeRxTranscriptEntryId = appendTranscriptEntry(
                TranscriptEntryType.RX,
                activeRxTranscriptStartedAtEpochMs
        ).id;
        refreshActiveRxTranscriptEntryFromSnapshot(lastRxSessionSnapshot);
    }

    private void reopenActiveRxTranscriptEntry() {
        activeRxTranscriptEntryId = appendTranscriptEntry(
                TranscriptEntryType.RX,
                activeRxTranscriptStartedAtEpochMs > 0L
                        ? activeRxTranscriptStartedAtEpochMs
                        : System.currentTimeMillis()
        ).id;
        refreshActiveRxTranscriptEntryFromSnapshot(lastRxSessionSnapshot);
    }

    private void restoreOperateUiState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String restoredSelectedCallsign = trimToNull(
                savedInstanceState.getString(STATE_SELECTED_OPERATE_REMOTE_CALLSIGN)
        );
        selectedOperateRemoteCallsign = restoredSelectedCallsign == null
                ? null
                : restoredSelectedCallsign.toUpperCase(Locale.US);
        transcriptUseUtc = savedInstanceState.getBoolean(STATE_TRANSCRIPT_USE_UTC, transcriptUseUtc);
        transcriptTimelineActive = savedInstanceState.getBoolean(
                STATE_TRANSCRIPT_TIMELINE_ACTIVE,
                transcriptTimelineActive
        );
        nextTranscriptEntryId = savedInstanceState.getLong(
                STATE_TRANSCRIPT_NEXT_ID,
                nextTranscriptEntryId
        );
        operateTranscriptEntries.clear();
        ArrayList<Bundle> savedEntries = savedInstanceState.getParcelableArrayList(
                STATE_TRANSCRIPT_ENTRIES
        );
        if (savedEntries != null) {
            for (Bundle savedEntry : savedEntries) {
                TranscriptEntry restoredEntry = restoreTranscriptEntry(savedEntry);
                if (restoredEntry != null) {
                    operateTranscriptEntries.add(restoredEntry);
                    nextTranscriptEntryId = Math.max(nextTranscriptEntryId, restoredEntry.id + 1L);
                }
            }
        }
        if (!operateTranscriptEntries.isEmpty()) {
            transcriptTimelineActive = true;
        }
        activeRxTranscriptEntryId = -1L;
        activeRxTranscriptStartedAtEpochMs = 0L;
        activeRxTranscriptBaselineText = "";
        activeTxTranscriptEntryId = -1L;
        conversationAutoScrollPending = true;
    }

    @Nullable
    private Bundle saveTranscriptEntry(TranscriptEntry transcriptEntry) {
        Bundle bundle = new Bundle();
        bundle.putLong(STATE_TRANSCRIPT_ENTRY_ID, transcriptEntry.id);
        bundle.putString(STATE_TRANSCRIPT_ENTRY_TYPE, transcriptEntry.type.name());
        bundle.putLong(STATE_TRANSCRIPT_ENTRY_STARTED_AT, transcriptEntry.startedAtEpochMs);
        bundle.putLong(STATE_TRANSCRIPT_ENTRY_UPDATED_AT, transcriptEntry.updatedAtEpochMs);
        bundle.putString(STATE_TRANSCRIPT_ENTRY_SOURCE, transcriptEntry.sourceOrRouteLabel);
        bundle.putString(STATE_TRANSCRIPT_ENTRY_STATE, transcriptEntry.stateLabel);
        bundle.putString(STATE_TRANSCRIPT_ENTRY_BODY, transcriptEntry.bodyText);
        bundle.putStringArrayList(
                STATE_TRANSCRIPT_ENTRY_CALLSIGNS,
                new ArrayList<>(transcriptEntry.callsignCandidates)
        );
        bundle.putInt(STATE_TRANSCRIPT_ENTRY_PROGRESS, transcriptEntry.progressIndex);
        bundle.putBoolean(STATE_TRANSCRIPT_ENTRY_ACTIVE, transcriptEntry.active);
        bundle.putInt(STATE_TRANSCRIPT_ENTRY_TONE_HZ, transcriptEntry.toneFrequencyHz);
        bundle.putInt(STATE_TRANSCRIPT_ENTRY_WPM, transcriptEntry.wpm);
        return bundle;
    }

    private ArrayList<Bundle> saveTranscriptEntries() {
        ArrayList<Bundle> savedEntries = new ArrayList<>();
        for (TranscriptEntry transcriptEntry : operateTranscriptEntries) {
            savedEntries.add(saveTranscriptEntry(transcriptEntry));
        }
        return savedEntries;
    }

    @Nullable
    private TranscriptEntry restoreTranscriptEntry(@Nullable Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String typeName = bundle.getString(STATE_TRANSCRIPT_ENTRY_TYPE);
        TranscriptEntryType type;
        try {
            type = typeName == null ? null : TranscriptEntryType.valueOf(typeName);
        } catch (IllegalArgumentException ignored) {
            type = null;
        }
        if (type == null) {
            return null;
        }
        TranscriptEntry entry = new TranscriptEntry(
                bundle.getLong(STATE_TRANSCRIPT_ENTRY_ID, nextTranscriptEntryId),
                type,
                bundle.getLong(STATE_TRANSCRIPT_ENTRY_STARTED_AT, System.currentTimeMillis())
        );
        entry.updatedAtEpochMs = bundle.getLong(
                STATE_TRANSCRIPT_ENTRY_UPDATED_AT,
                entry.startedAtEpochMs
        );
        entry.sourceOrRouteLabel = safeTranscriptText(
                bundle.getString(STATE_TRANSCRIPT_ENTRY_SOURCE, "")
        );
        entry.stateLabel = safeTranscriptText(
                bundle.getString(STATE_TRANSCRIPT_ENTRY_STATE, "")
        );
        entry.bodyText = safeTranscriptText(
                bundle.getString(STATE_TRANSCRIPT_ENTRY_BODY, "")
        );
        ArrayList<String> restoredCallsigns = bundle.getStringArrayList(STATE_TRANSCRIPT_ENTRY_CALLSIGNS);
        entry.callsignCandidates = restoredCallsigns == null
                ? new ArrayList<>()
                : new ArrayList<>(restoredCallsigns);
        entry.progressIndex = bundle.getInt(STATE_TRANSCRIPT_ENTRY_PROGRESS, -1);
        entry.active = false;
        entry.toneFrequencyHz = bundle.getInt(STATE_TRANSCRIPT_ENTRY_TONE_HZ, 0);
        entry.wpm = bundle.getInt(STATE_TRANSCRIPT_ENTRY_WPM, 0);
        return entry;
    }

    private void updateActiveRxTranscriptEntryFromDecodeEvent(
            @Nullable CwDecodeEvent decodeEvent,
            long updatedAtEpochMs
    ) {
        if (decodeEvent == null) {
            return;
        }
        if (activeRxTranscriptEntryId == -1L) {
            beginActiveRxTranscriptTurn(updatedAtEpochMs);
        }
        TranscriptEntry entry = findTranscriptEntry(activeRxTranscriptEntryId);
        if (entry == null) {
            return;
        }
        entry.bodyText = extractTurnTranscriptText(
                activeRxTranscriptBaselineText,
                decodeEvent.outputText()
        );
        entry.updatedAtEpochMs = updatedAtEpochMs;
        entry.active = true;
        entry.stateLabel = "接收中";
        if (lastRxSessionSnapshot != null) {
            entry.sourceOrRouteLabel = lastRxSessionSnapshot.sourceLabel();
        }
        entry.callsignCandidates = new ArrayList<>(resolveUiFriendlyCallsignCandidates(lastRxSessionSnapshot));
        applyRxTranscriptMetrics(entry, lastRxSessionSnapshot);
        markConversationDirty();
    }

    private void refreshActiveRxTranscriptEntryFromSnapshot(@Nullable RxSessionSnapshot snapshot) {
        if (activeRxTranscriptEntryId == -1L || snapshot == null) {
            return;
        }
        TranscriptEntry entry = findTranscriptEntry(activeRxTranscriptEntryId);
        if (entry == null) {
            return;
        }
        entry.bodyText = extractTurnTranscriptText(
                activeRxTranscriptBaselineText,
                snapshot.rawText()
        );
        entry.updatedAtEpochMs = Math.max(
                entry.updatedAtEpochMs,
                snapshot.updatedAtEpochMs()
        );
        entry.active = snapshot.captureActive();
        entry.stateLabel = snapshot.captureActive() ? "接收中" : "保持中";
        entry.sourceOrRouteLabel = snapshot.sourceLabel();
        entry.callsignCandidates = new ArrayList<>(resolveUiFriendlyCallsignCandidates(snapshot));
        applyRxTranscriptMetrics(entry, snapshot);
        if (entry.bodyText.isEmpty()) {
            return;
        }
        markConversationDirty();
    }

    private void finalizeActiveRxTranscriptEntryFromCurrentSnapshot(long finalizedAtEpochMs) {
        if (activeRxTranscriptEntryId == -1L) {
            return;
        }
        CwInterpreterSnapshot rawSnapshot = operateCommittedOutputController == null
                ? null
                : operateCommittedOutputController.rawSnapshot();
        TranscriptEntry entry = findTranscriptEntry(activeRxTranscriptEntryId);
        if (entry == null) {
            activeRxTranscriptEntryId = -1L;
            activeRxTranscriptStartedAtEpochMs = 0L;
            activeRxTranscriptBaselineText = "";
            return;
        }
        if (rawSnapshot != null) {
            entry.bodyText = extractTurnTranscriptText(
                    activeRxTranscriptBaselineText,
                    rawSnapshot.rawText()
            );
        }
        entry.callsignCandidates = new ArrayList<>(resolveUiFriendlyCallsignCandidates(lastRxSessionSnapshot));
        entry.updatedAtEpochMs = finalizedAtEpochMs;
        entry.active = false;
        entry.stateLabel = "接收";
        applyRxTranscriptMetrics(entry, lastRxSessionSnapshot);
        if (entry.bodyText.trim().isEmpty()) {
            operateTranscriptEntries.remove(entry);
        }
        activeRxTranscriptEntryId = -1L;
        activeRxTranscriptStartedAtEpochMs = 0L;
        activeRxTranscriptBaselineText = "";
        markConversationDirty();
    }

    private void beginActiveTxTranscriptEntry(long startedAtEpochMs) {
        transcriptTimelineActive = true;
        TranscriptEntry entry = appendTranscriptEntry(
                TranscriptEntryType.TX,
                startedAtEpochMs <= 0L ? System.currentTimeMillis() : startedAtEpochMs
        );
        activeTxTranscriptEntryId = entry.id;
        refreshActiveTxTranscriptEntry();
    }

    private void refreshActiveTxTranscriptEntry() {
        if (activeTxTranscriptEntryId == -1L) {
            return;
        }
        TranscriptEntry entry = findTranscriptEntry(activeTxTranscriptEntryId);
        if (entry == null) {
            return;
        }
        entry.updatedAtEpochMs = System.currentTimeMillis();
        entry.sourceOrRouteLabel = hasMeaningfulText(activeTxRouteLabel)
                ? activeTxRouteLabel
                : renderTxRouteChip();
        entry.stateLabel = txSendInProgress
                ? renderPlaybackProgressChip(activeTxPlaybackSnapshot)
                : safeValue(txDeliveryStatus);
        entry.bodyText = resolveVisibleActiveTxText(
                binding.txComposeEditText.getText() == null
                        ? ""
                        : binding.txComposeEditText.getText().toString().trim()
        );
        entry.progressIndex = activeTxPlaybackSnapshot == null
                ? -1
                : activeTxPlaybackSnapshot.currentTextIndex();
        entry.active = txSendInProgress;
        applyTxTranscriptMetrics(entry);
        if (!entry.bodyText.trim().isEmpty()) {
            markConversationDirty();
        }
    }

    private void finalizeActiveTxTranscriptEntry(String status) {
        if (activeTxTranscriptEntryId == -1L) {
            return;
        }
        TranscriptEntry entry = findTranscriptEntry(activeTxTranscriptEntryId);
        if (entry == null) {
            activeTxTranscriptEntryId = -1L;
            return;
        }
        entry.updatedAtEpochMs = System.currentTimeMillis();
        entry.stateLabel = safeValue(status);
        entry.progressIndex = -1;
        entry.active = false;
        applyTxTranscriptMetrics(entry);
        activeTxTranscriptEntryId = -1L;
        markConversationDirty();
    }

    private TranscriptEntry appendTranscriptEntry(
            TranscriptEntryType type,
            long startedAtEpochMs
    ) {
        TranscriptEntry entry = new TranscriptEntry(
                nextTranscriptEntryId++,
                type,
                startedAtEpochMs
        );
        operateTranscriptEntries.add(entry);
        markConversationDirty();
        return entry;
    }

    @Nullable
    private TranscriptEntry findTranscriptEntry(long entryId) {
        for (TranscriptEntry entry : operateTranscriptEntries) {
            if (entry.id == entryId) {
                return entry;
            }
        }
        return null;
    }

    private void markConversationDirty() {
        conversationAutoScrollPending = true;
    }

    private void applyRxTranscriptMetrics(
            @Nullable TranscriptEntry transcriptEntry,
            @Nullable RxSessionSnapshot snapshot
    ) {
        if (transcriptEntry == null || snapshot == null) {
            return;
        }
        transcriptEntry.toneFrequencyHz = Math.max(0, snapshot.effectiveToneFrequencyHz());
        transcriptEntry.wpm = Math.max(0, resolveDisplayWpm(snapshot));
    }

    private void applyTxTranscriptMetrics(@Nullable TranscriptEntry transcriptEntry) {
        if (transcriptEntry == null) {
            return;
        }
        transcriptEntry.toneFrequencyHz = Math.max(0, resolveOperateToneFrequencyHz());
        transcriptEntry.wpm = Math.max(0, resolveOperateWpm());
    }

    @Nullable
    private String buildTranscriptQsoCommentSeed(@Nullable TranscriptEntry transcriptEntry) {
        if (transcriptEntry == null) {
            return null;
        }
        ArrayList<String> parts = new ArrayList<>();
        String primarySegment = formatOperateMetricSegment(
                transcriptEntry.type == TranscriptEntryType.TX ? "TX" : "RX",
                transcriptEntry.wpm,
                transcriptEntry.toneFrequencyHz
        );
        if (hasMeaningfulText(primarySegment)) {
            parts.add(primarySegment);
        }
        if (transcriptEntry.type == TranscriptEntryType.RX) {
            String txSegment = formatOperateMetricSegment(
                    "TX",
                    resolveOperateWpm(),
                    resolveOperateToneFrequencyHz()
            );
            if (hasMeaningfulText(txSegment)) {
                parts.add(txSegment);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        parts.add("QSO by CWCN");
        return String.join("; ", parts);
    }

    @Nullable
    private String formatOperateMetricSegment(String label, int wpm, int toneFrequencyHz) {
        ArrayList<String> parts = new ArrayList<>();
        if (wpm > 0) {
            parts.add(wpm + " WPM");
        }
        if (toneFrequencyHz > 0) {
            parts.add(toneFrequencyHz + " Hz");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return label + " " + String.join(" / ", parts);
    }

    private String extractTurnTranscriptText(String baselineText, String fullOutputText) {
        String safeBaselineText = safeTranscriptText(baselineText);
        String safeFullOutputText = safeTranscriptText(fullOutputText);
        if (safeFullOutputText.isEmpty()) {
            return "";
        }
        if (safeBaselineText.isEmpty()) {
            return safeFullOutputText;
        }
        if (safeFullOutputText.startsWith(safeBaselineText)) {
            return safeTranscriptText(
                    safeFullOutputText.substring(safeBaselineText.length())
            );
        }
        return safeFullOutputText;
    }

    private String safeTranscriptText(String value) {
        return value == null ? "" : value.trim();
    }

    private String renderTranscriptTimestamp(long timestampEpochMs) {
        if (timestampEpochMs <= 0L) {
            return "-";
        }
        TimeZone timeZone = transcriptUseUtc
                ? TimeZone.getTimeZone("UTC")
                : TimeZone.getDefault();
        boolean sameDay = isSameTranscriptDay(timestampEpochMs, System.currentTimeMillis(), timeZone);
        SimpleDateFormat formatter = new SimpleDateFormat(
                sameDay ? "HH:mm:ss" : "MM-dd HH:mm:ss",
                Locale.US
        );
        formatter.setTimeZone(timeZone);
        return formatter.format(new Date(timestampEpochMs));
    }

    private boolean isSameTranscriptDay(long firstEpochMs, long secondEpochMs, TimeZone timeZone) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.US);
        formatter.setTimeZone(timeZone);
        return formatter.format(new Date(firstEpochMs)).equals(
                formatter.format(new Date(secondEpochMs))
        );
    }

    private StreamEntry toStreamEntry(TranscriptEntry transcriptEntry) {
        boolean outgoing = transcriptEntry.type == TranscriptEntryType.TX;
        String label = outgoing ? "TX" : "RX";
        String headline = renderTranscriptTimestamp(transcriptEntry.startedAtEpochMs);
        String meta = renderTranscriptMeta(transcriptEntry);
        CharSequence body = outgoing
                ? renderTranscriptTxBody(transcriptEntry)
                : renderTranscriptRxBody(transcriptEntry);
        int backgroundRes = transcriptEntry.active
                ? (outgoing ? R.drawable.operate_stream_card_tx : R.drawable.operate_stream_card_rx)
                : R.drawable.operate_stream_card_txt;
        return new StreamEntry(
                label,
                headline,
                meta,
                body,
                backgroundRes,
                outgoing ? R.color.cwcn_tx_line : R.color.cwcn_rx_line,
                outgoing,
                false,
                transcriptEntry.active,
                buildTranscriptQsoCommentSeed(transcriptEntry)
        );
    }

    private String renderTranscriptMeta(TranscriptEntry transcriptEntry) {
        if (transcriptEntry.type == TranscriptEntryType.RX) {
            ArrayList<String> parts = new ArrayList<>();
            if (transcriptEntry.active) {
                parts.add("实时");
            }
            if (hasMeaningfulText(transcriptEntry.sourceOrRouteLabel)) {
                parts.add(renderFriendlyOperateSourceLabel(transcriptEntry.sourceOrRouteLabel));
            }
            return parts.isEmpty() ? "-" : String.join("  |  ", parts);
        }
        ArrayList<String> parts = new ArrayList<>();
        if (hasMeaningfulText(transcriptEntry.stateLabel)) {
            parts.add(transcriptEntry.stateLabel.trim());
        }
        if (hasMeaningfulText(transcriptEntry.sourceOrRouteLabel)) {
            parts.add(transcriptEntry.sourceOrRouteLabel.trim());
        }
        if (transcriptEntry.type == TranscriptEntryType.TX
                && transcriptEntry.active
                && hasMeaningfulText(transcriptEntry.bodyText)
                && transcriptEntry.progressIndex >= 0) {
            parts.add(renderTranscriptProgressMeta(transcriptEntry));
        }
        return parts.isEmpty() ? "-" : String.join("  |  ", parts);
    }

    private CharSequence renderTranscriptRxBody(TranscriptEntry transcriptEntry) {
        String visibleText = safeTranscriptText(transcriptEntry.bodyText);
        if (visibleText.isEmpty()) {
            return "-";
        }
        if (transcriptEntry.callsignCandidates == null || transcriptEntry.callsignCandidates.isEmpty()) {
            return visibleText;
        }
        SpannableString styled = new SpannableString(visibleText);
        int defaultCandidateForeground = ContextCompat.getColor(this, R.color.cwcn_title);
        int defaultCandidateBackground = ContextCompat.getColor(this, R.color.cwcn_primary_variant);
        int selectedCandidateForeground = ContextCompat.getColor(this, R.color.cwcn_accent);
        int selectedCandidateBackground = ContextCompat.getColor(this, R.color.cwcn_chip_active);
        String selectedCallsign = trimToNull(selectedOperateRemoteCallsign);
        for (String candidate : transcriptEntry.callsignCandidates) {
            String normalizedCandidate = trimToNull(candidate);
            if (normalizedCandidate == null) {
                continue;
            }
            boolean selected = selectedCallsign != null
                    && selectedCallsign.equalsIgnoreCase(normalizedCandidate);
            applyCallsignHighlight(
                    styled,
                    visibleText,
                    normalizedCandidate,
                    selected
                            ? selectedCandidateForeground
                            : defaultCandidateForeground,
                    selected
                            ? selectedCandidateBackground
                            : defaultCandidateBackground
            );
        }
        return styled;
    }

    private void applyCallsignHighlight(
            SpannableString styled,
            String visibleText,
            String candidate,
            int foregroundColor,
            int backgroundColor
    ) {
        if (styled == null || !hasMeaningfulText(visibleText) || !hasMeaningfulText(candidate)) {
            return;
        }
        String upperText = visibleText.toUpperCase(Locale.US);
        String upperCandidate = candidate.trim().toUpperCase(Locale.US);
        int searchStart = 0;
        while (searchStart < upperText.length()) {
            int index = upperText.indexOf(upperCandidate, searchStart);
            if (index < 0) {
                break;
            }
            int end = index + upperCandidate.length();
            if (isTranscriptCallsignBoundary(upperText, index, end)) {
                styled.setSpan(
                        new CallsignCandidateSpan(candidate.trim().toUpperCase(Locale.US), foregroundColor, backgroundColor),
                        index,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            searchStart = end;
        }
    }

    private boolean isTranscriptCallsignBoundary(String text, int start, int end) {
        if (text == null) {
            return false;
        }
        boolean leftBoundary = start <= 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean rightBoundary = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return leftBoundary && rightBoundary;
    }

    private String renderTranscriptProgressMeta(TranscriptEntry transcriptEntry) {
        String visibleText = safeTranscriptText(transcriptEntry.bodyText);
        if (visibleText.isEmpty()) {
            return "0%";
        }
        int clampedIndex = Math.max(0, Math.min(transcriptEntry.progressIndex, visibleText.length() - 1));
        int percent = Math.round(((clampedIndex + 1) * 100f) / visibleText.length());
        return percent + "%";
    }

    private CharSequence renderTranscriptTxBody(TranscriptEntry transcriptEntry) {
        String visibleText = safeTranscriptText(transcriptEntry.bodyText);
        if (visibleText.isEmpty()) {
            return "-";
        }
        if (!transcriptEntry.active
                || transcriptEntry.progressIndex < 0
                || transcriptEntry.progressIndex >= visibleText.length()) {
            return visibleText;
        }
        SpannableString styled = new SpannableString(visibleText);
        int bodyColor = ContextCompat.getColor(this, R.color.cwcn_body);
        int completedColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        if (transcriptEntry.progressIndex > 0) {
            styled.setSpan(
                    new ForegroundColorSpan(completedColor),
                    0,
                    transcriptEntry.progressIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        styled.setSpan(
                new ForegroundColorSpan(currentColor),
                transcriptEntry.progressIndex,
                transcriptEntry.progressIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        styled.setSpan(
                new StyleSpan(android.graphics.Typeface.BOLD),
                transcriptEntry.progressIndex,
                transcriptEntry.progressIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        if (transcriptEntry.progressIndex + 1 < visibleText.length()) {
            styled.setSpan(
                    new ForegroundColorSpan(bodyColor),
                    transcriptEntry.progressIndex + 1,
                    visibleText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return styled;
    }

    private List<StreamEntry> buildOperateStreamEntries(@Nullable RxSessionSnapshot snapshot) {
        ArrayList<StreamEntry> entries = new ArrayList<>();
        if (transcriptTimelineActive) {
            for (TranscriptEntry transcriptEntry : operateTranscriptEntries) {
                if (!hasMeaningfulText(transcriptEntry.bodyText)) {
                    continue;
                }
                entries.add(toStreamEntry(transcriptEntry));
            }
            if (!entries.isEmpty()) {
                return entries;
            }
            entries.add(new StreamEntry(
                    "RX",
                    "Transcript",
                    "当前还没有已保留的 RX/TX 条目",
                    "新的收发 turn 会按时间顺序继续堆叠在这里",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false,
                    false,
                    null
            ));
            return entries;
        }
        RigProfile profile = rigSelectionStore.selectedProfile();

        if (snapshot == null) {
            entries.add(new StreamEntry(
                    "RX",
                    "等待接收",
                    shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()
                            ? "手机麦克风 | 需要授权"
                            : usePhoneFallbackRoute(profile)
                            ? "手机麦克风 | 待命"
                            : "接收链路待配置",
                    shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()
                            ? "需要先授予麦克风权限，CWCN 才能通过手机麦克风接收。"
                            : usePhoneFallbackRoute(profile)
                            ? "当前通过手机麦克风监听来波 CW。"
                            : "当前还没有有效接收链路，请检查设置或电台配置。",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false,
                    false,
                    null
            ));
        } else {
            String rawText = normalizedTextOrNull(snapshot.rawText());
            entries.add(new StreamEntry(
                    "RX",
                    renderTranscriptTimestamp(snapshot.updatedAtEpochMs()),
                    renderReceiveMeta(snapshot),
                    safeValue(rawText),
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false,
                    snapshot.captureActive(),
                    null
            ));
        }

        if (entries.isEmpty()) {
            entries.add(new StreamEntry(
                    "RX",
                    "-",
                    "-",
                    "-",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false,
                    false,
                    null
            ));
        }
        return entries;
    }
}

