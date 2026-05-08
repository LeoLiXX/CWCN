package org.bi9clt.cwcn.ui.operate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.app.RouteFallbackStore;
import org.bi9clt.cwcn.core.app.SqlLevelStore;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.MicrophoneRxAudioSource;
import org.bi9clt.cwcn.core.audio.RxAudioSource;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.rx.RxSessionStore;
import org.bi9clt.cwcn.core.rig.AudioVoxRigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.spectrum.SpectrumHistoryStore;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
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
import org.bi9clt.cwcn.ui.debug.AudioSpectrumAnalyzer;
import org.bi9clt.cwcn.ui.debug.AudioSpectrumSnapshot;
import org.bi9clt.cwcn.ui.spectrum.SpectrumActivity;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class OperateActivity extends AppCompatActivity implements RxAudioSource.Callback {
    private static final long LIVE_RX_REFRESH_INTERVAL_MS = 700L;
    private static final long OPERATE_RX_PUBLISH_INTERVAL_MS = 450L;
    private static final long TX_PROGRESS_REFRESH_INTERVAL_MS = 120L;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final float SIDE_RAIL_DRAG_THRESHOLD_PX = 12f;
    public static final String EXTRA_START_OVERLAY = "org.bi9clt.cwcn.ui.operate.extra.START_OVERLAY";
    public static final String START_OVERLAY_CHART = "chart";
    private static final String PREFS_OPERATE_UI = "operate_ui";
    private static final String PREF_SIDE_RAIL_X = "side_rail_x";
    private static final String PREF_SIDE_RAIL_Y = "side_rail_y";
    private static final String PREF_MIC_PERMISSION_ASKED = "mic_permission_asked";
    private static final String TEMPLATE_CQ = "CQ";
    private static final String TEMPLATE_CQ_DX = "CQ DX";
    private static final String TEMPLATE_QRZ = "QRZ";
    private static final String TEMPLATE_TU73 = "TU73";
    private static final String[] TEMPLATE_OPTIONS = {
            TEMPLATE_CQ,
            TEMPLATE_CQ_DX,
            TEMPLATE_QRZ,
            TEMPLATE_TU73
    };
    private static WeakReference<OperateActivity> sharedActiveInstance =
            new WeakReference<>(null);

    private enum OverlayMode {
        NONE,
        CHART,
        SQL,
        TEMPLATE,
        DRAFT
    }

    private static final class StreamEntry {
        private final String label;
        private final String meta;
        private final CharSequence body;
        private final int cardBackgroundRes;
        private final int labelColorRes;
        private final boolean outgoing;
        private final boolean compact;

        private StreamEntry(
                String label,
                String meta,
                CharSequence body,
                int cardBackgroundRes,
                int labelColorRes,
                boolean outgoing,
                boolean compact
        ) {
            this.label = label;
            this.meta = meta;
            this.body = body;
            this.cardBackgroundRes = cardBackgroundRes;
            this.labelColorRes = labelColorRes;
            this.outgoing = outgoing;
            this.compact = compact;
        }
    }

    private ActivityOperateBinding binding;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private RouteFallbackStore routeFallbackStore;
    private SqlLevelStore sqlLevelStore;
    private StationProfileStore stationProfileStore;
    private RxSessionStore rxSessionStore;
    private SpectrumHistoryStore spectrumHistoryStore;
    private MicrophoneRxAudioSource operateMicrophoneRxAudioSource;
    private CwSignalProcessor operateSignalProcessor;
    private CwHybridTimingModel operateTimingModel;
    private CwDecoder operateDecoder;
    private CwInterpreter operateRawInterpreter;
    private CwInterpreter operateSemanticInterpreter;
    private org.bi9clt.cwcn.core.qso.QsoStateMachine operateQsoStateMachine;
    private AudioSpectrumAnalyzer operateAudioSpectrumAnalyzer;
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

    private AppOverviewSnapshot lastOverview;
    private RxSessionSnapshot lastRxSessionSnapshot;
    private OverlayMode overlayMode = OverlayMode.NONE;
    private OverlayMode pendingLaunchOverlayMode = OverlayMode.NONE;
    private String selectedTemplate = TEMPLATE_CQ;
    private int sqlLevel = 55;
    private boolean templateSelectorInitialized;
    private boolean templateSelectorSyncing;
    private boolean txSendInProgress;
    private boolean txStopRequested;
    private String txDeliveryStatus = "";
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
    private boolean preserveRxAcrossSpectrumNavigation;

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
        sqlLevelStore = new SqlLevelStore(this);
        stationProfileStore = new StationProfileStore(this);
        rxSessionStore = new RxSessionStore(this);
        spectrumHistoryStore = new SpectrumHistoryStore(this);
        sharedActiveInstance = new WeakReference<>(this);
        initializeOperateRxPipeline();
        operateUiPreferences = getSharedPreferences(PREFS_OPERATE_UI, MODE_PRIVATE);
        sqlLevel = sqlLevelStore.load();
        syncOperateSql();
        consumeLaunchIntent(getIntent());
        setupActions();
        binding.sideRail.post(this::restoreSideRailPosition);
        refreshUi();
        openPendingLaunchOverlayIfAny();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        super.onDestroy();
    }

    private void setupActions() {
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.reviewDraftButton.setOnClickListener(view ->
                startActivity(new Intent(this, QsoEditorActivity.class)));
        binding.expandDraftButton.setOnClickListener(view -> toggleOverlay(OverlayMode.DRAFT));
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
        binding.overlayScrim.setOnClickListener(view -> hideOverlay());
        binding.closeOverlayButton.setOnClickListener(view -> hideOverlay());
        binding.rxFootnoteText.setOnClickListener(view -> {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                requestMicrophonePermission(true);
            }
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
        binding.overlayTemplateCqDxButton.setOnClickListener(view -> selectTemplate(TEMPLATE_CQ_DX, true));
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
                updateComposerUi();
                refreshConversationOnly();
                updateOverlayContentSafe();
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
        lastOverview = localLogRepository.loadOverview();
        lastRxSessionSnapshot = rxSessionStore.load();
        binding.statusMainText.setText(renderStatusMainSafe(lastRxSessionSnapshot));
        binding.statusDetailText.setText(renderStatusDetailSafe(lastRxSessionSnapshot));
        binding.sourceChipText.setText(renderSourceChipSafe(lastRxSessionSnapshot));
        binding.draftSummaryHeadlineText.setText(renderDraftSummaryHeadline(lastOverview));
        binding.draftSummaryMetaText.setText(renderDraftSummaryMeta(lastOverview));
        binding.draftStatusText.setText(renderDraftStatus(lastOverview, lastRxSessionSnapshot));
        binding.draftPreviewText.setText(renderDraftPreviewSafe(lastOverview));
        binding.rxFootnoteText.setText(renderRxFootnote(lastRxSessionSnapshot));
        setVisibleWhenHasText(binding.draftStatusText);
        setVisibleWhenHasText(binding.draftPreviewText);
        setVisibleWhenHasText(binding.rxFootnoteText);
        binding.rxFootnoteText.setClickable(shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission());
        FormalBottomNavStyler.apply(binding.bottomNavView, FormalBottomNavStyler.Page.OPERATE);
        refreshConversationOnly();
        updateComposerUi();
        updateOverlayContentSafe();
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
        updateOverlayContentSafe();
    }

    private void refreshConversationOnly() {
        renderConversationCards(buildOperateStreamEntries(lastRxSessionSnapshot, lastOverview));
    }

    private void updateComposerUi() {
        String draftText = safeValue(binding.txComposeEditText.getText() == null
                ? null
                : binding.txComposeEditText.getText().toString());
        boolean hasDraft = !"-".equals(draftText);
        binding.txPlaybackControlsRow.setVisibility(txSendInProgress ? View.VISIBLE : View.GONE);
        binding.templatePreviewText.setText(renderTemplatePreview());
        binding.selectedTemplateChip.setText(selectedTemplate);
        binding.txRouteChip.setText(renderTxRouteChipSafe());
        binding.txStageChip.setText(renderTxStageChipSafe(hasDraft, draftText));
        binding.sqlQuickChip.setText(renderSqlQuickChipText());
        binding.sendTxButton.setEnabled(!txSendInProgress);
        binding.sendTxButton.setAlpha(txSendInProgress ? 0.55f : 1.0f);
        binding.clearComposeButton.setVisibility(hasDraft ? View.VISIBLE : View.GONE);
        binding.clearComposeButton.setEnabled(hasDraft && !txSendInProgress);
        binding.pauseTxButton.setEnabled(txSendInProgress && activeTxStopSupported);
        binding.pauseTxButton.setAlpha(txSendInProgress && activeTxStopSupported ? 1.0f : 0.45f);
        binding.repeatTxButton.setVisibility(txSendInProgress ? View.GONE : View.VISIBLE);
        binding.repeatTxButton.setEnabled(!txSendInProgress && hasRepeatableTxText());
        binding.repeatTxButton.setAlpha(!txSendInProgress && hasRepeatableTxText() ? 1.0f : 0.45f);
        binding.txStatusText.setText(renderTxStatusSafe(hasDraft));
        setVisibleWhenHasText(binding.txStatusText);
    }

    private String renderStatusMain(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return "麦克风 | 权限";
            }
            return usePhoneFallbackRoute(profile)
                    ? "麦克风 | 待命"
                    : "空闲 | 无接收";
        }
        int tone = resolveBestTone(snapshot);
        String remote = safeValue(snapshot.remoteCallsign());
        String capture = snapshot.captureActive() ? "接收中" : "保持";
        return capture
                + " | "
                + positiveOrDash(snapshot.estimatedWpm()) + "WPM"
                + " | "
                + positiveOrDash(tone) + "Hz"
                + " | "
                + remote;
    }

    private String renderStatusDetail(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        String rigSummary = resolveOperateRouteSummary(profile);
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return rigSummary + " | 允许麦克风";
            }
            return usePhoneFallbackRoute(profile)
                    ? rigSummary + " | 手机接收"
                    : rigSummary + " | 等待中";
        }
        return safeValue(snapshot.phaseDisplayName())
                + " | "
                + rigSummary
                + " | "
                + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderSourceChip(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return usePhoneFallbackRoute(rigSelectionStore.selectedProfile()) ? "麦克风" : "空闲";
        }
        return snapshot.captureActive() ? "接收中" : "保持";
    }

    private List<StreamEntry> buildStreamEntries(
            @Nullable RxSessionSnapshot snapshot,
            @Nullable AppOverviewSnapshot overview
    ) {
        java.util.ArrayList<StreamEntry> entries = new java.util.ArrayList<>();
        RigProfile profile = rigSelectionStore.selectedProfile();

        if (snapshot == null) {
            entries.add(new StreamEntry(
                    "接收",
                    shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()
                            ? "手机麦克风  |  需要权限"
                            : usePhoneFallbackRoute(profile)
                            ? "手机麦克风  |  待命"
                            : "接收链路待配置",
                    shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()
                            ? "需要先授予麦克风权限，CWCN 才能通过手机麦克风接收。"
                            : usePhoneFallbackRoute(profile)
                            ? "当前通过手机麦克风监听来波 CW。"
                            : "当前还没有有效接收链路，请检查设置或电台配置。",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false
            ));
        } else {
            String normalizedText = normalizedTextOrNull(snapshot.normalizedText());
            String rawText = normalizedTextOrNull(snapshot.rawText());
            entries.add(new StreamEntry(
                    "接收",
                    renderReceiveMeta(snapshot),
                    safeValue(rawText),
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false
            ));
            if (rawText != null
                    && normalizedText != null
                    && !normalizedText.equals(rawText)) {
                entries.add(new StreamEntry(
                        "解释",
                        safeValue(snapshot.phaseDisplayName()) + "  |  归一化",
                        normalizedText,
                        R.drawable.operate_stream_card_txt,
                        R.color.cwcn_accent,
                        false,
                        true
                ));
            }
        }

        String txDraft = binding.txComposeEditText.getText() == null
                ? ""
                : binding.txComposeEditText.getText().toString().trim();
        String visibleTxText = resolveVisibleActiveTxText(txDraft);
        if (shouldShowTransmitStream(visibleTxText)) {
            entries.add(new StreamEntry(
                    "发射",
                    renderActiveTxStreamMeta(),
                    renderActiveTxStreamBody(visibleTxText),
                    R.drawable.operate_stream_card_tx,
                    R.color.cwcn_tx_line,
                    true,
                    false
            ));
        }

        if (entries.isEmpty()) {
            entries.add(new StreamEntry(
                    "RX WAIT",
                    "-",
                    "-",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false
            ));
        }
        return entries;
    }

    private void renderConversationCards(List<StreamEntry> entries) {
        binding.conversationList.removeAllViews();
        for (int index = 0; index < entries.size(); index++) {
            StreamEntry entry = entries.get(index);
            binding.conversationList.addView(buildConversationCard(entry, index < entries.size() - 1));
        }
    }

    private View buildConversationCard(StreamEntry entry, boolean withBottomGap) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(entry.cardBackgroundRes);
        card.setPadding(
                px(entry.compact ? 9 : 11),
                px(entry.compact ? 7 : 9),
                px(entry.compact ? 9 : 11),
                px(entry.compact ? 7 : 9)
        );

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (withBottomGap) {
            cardParams.bottomMargin = px(entry.compact ? 4 : 6);
        }
        if (entry.outgoing) {
            cardParams.leftMargin = px(20);
        } else if (entry.compact) {
            cardParams.leftMargin = px(12);
            cardParams.rightMargin = px(28);
        } else {
            cardParams.rightMargin = px(12);
        }
        card.setLayoutParams(cardParams);

        TextView labelView = new TextView(this);
        labelView.setText(entry.label);
        labelView.setTextSize(entry.compact ? 9f : 11f);
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
        labelView.setTextColor(ContextCompat.getColor(this, entry.labelColorRes));
        card.addView(labelView);

        TextView metaView = new TextView(this);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = px(entry.compact ? 1 : 2);
        metaView.setLayoutParams(metaParams);
        metaView.setText(entry.meta);
        metaView.setTextSize(entry.compact ? 8f : 9f);
        metaView.setTextColor(ContextCompat.getColor(this, R.color.cwcn_subtitle));
        card.addView(metaView);

        TextView bodyView = new TextView(this);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = px(entry.compact ? 3 : 5);
        bodyView.setLayoutParams(bodyParams);
        bodyView.setText(entry.body);
        bodyView.setTextSize(entry.compact ? 11f : 14f);
        bodyView.setTextColor(ContextCompat.getColor(this, R.color.cwcn_body));
        bodyView.setLineSpacing(0f, 1.12f);
        bodyView.setTypeface(android.graphics.Typeface.MONOSPACE);
        bodyView.setMaxLines(entry.compact ? 3 : Integer.MAX_VALUE);
        card.addView(bodyView);

        return card;
    }

    private int px(int dp) {
        return Math.round(dpToPx(dp));
    }

    private String renderRxFootnote(@Nullable RxSessionSnapshot snapshot) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderRxFootnoteProductionV2(snapshot);
        }
        if (snapshot == null && shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
            return "点这里授予麦克风权限，以启用手机接收";
        }
        if (snapshot == null) {
            if (usePhoneFallbackRoute(rigSelectionStore.selectedProfile())) {
                return "当前启用手机麦克风兜底接收";
            }
            return "";
        }
        if (snapshot.needManualReview()) {
            return "建议先人工复核，再确认草稿";
        }
        if (snapshot.readyForDraftConfirmation()) {
            return "草稿已基本可确认";
        }
        return "";
    }

    private String renderDraftSummaryHeadline(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "当前没有活动草稿";
        }
        return safeValue(draft.remoteCallsignCandidate())
                + "  |  "
                + draft.phase().displayName()
                + "  |  "
                + (draft.readyForDraftConfirmation() ? "可确认" : "构建中");
    }

    private String renderDraftSummaryMeta(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "RST -/-  |  姓名 -  |  QTH -";
        }
        return "RST "
                + safeValue(draft.rstSentCandidate())
                + "/"
                + safeValue(draft.rstRcvdCandidate())
                + "  |  姓名 "
                + safeValue(draft.nameCandidate())
                + "  |  QTH "
                + safeValue(draft.qthCandidate());
    }

    private String renderDraftStatus(
            @Nullable AppOverviewSnapshot overview,
            @Nullable RxSessionSnapshot snapshot
    ) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "";
        }
        if (draft.needManualReview() || (snapshot != null && snapshot.needManualReview())) {
            return "写入日志前需要人工复核";
        }
        if (draft.readyForDraftConfirmation()) {
            return "可复核并确认";
        }
        return "草稿仍在根据最近抄收内容继续构建";
    }

    private String renderDraftPreview(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "";
        }
        String normalized = normalizedTextOrNull(draft.normalizedText());
        if (normalized != null) {
            return normalized;
        }
        List<String> hints = safeHints(draft);
        if (!hints.isEmpty()) {
            return hints.get(0);
        }
        return "暂时还没有可读的草稿内容。";
    }

    private String renderTxStatus(boolean hasDraft) {
        if (txSendInProgress) {
            if (txStopRequested) {
                return activeTxStopSupported ? "正在停止发射" : "已请求停止，等待适配器响应";
            }
            if (activeTxPlaybackSnapshot != null && hasMeaningfulText(activeTxPlaybackSnapshot.statusMessage())) {
                return renderPlaybackSnapshotStatus(activeTxPlaybackSnapshot);
            }
            return hasDraft ? "正在发送文本" : "正在发送模板";
        }
        if (txDeliveryStatus != null && !txDeliveryStatus.trim().isEmpty()) {
            return txDeliveryStatus;
        }
        return "";
    }

    private String renderTxRouteChip() {
        if (txSendInProgress && hasMeaningfulText(activeTxRouteLabel)) {
            return safeCompact(activeTxRouteLabel, 12);
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (usePhoneFallbackRoute(profile)) {
            return "手机发射";
        }
        if (profile == null) {
            return "未配置";
        }
        return safeCompact(profile.displayName(), 12);
    }

    private String renderTxStageChip(boolean hasDraft, String draftText) {
        if (txSendInProgress && activeTxPlaybackSnapshot != null) {
            return renderPlaybackProgressChip(activeTxPlaybackSnapshot);
        }
        if (!hasDraft) {
            return "模板 " + selectedTemplate;
        }
        int length = draftText == null || "-".equals(draftText) ? 0 : draftText.length();
        return "文本 " + length + "字";
    }

    private boolean usePhoneFallbackRoute(@Nullable RigProfile profile) {
        return profile == null && routeFallbackStore != null && routeFallbackStore.usePhoneFallback();
    }

    private boolean shouldUsePhoneMicrophoneRx() {
        return usePhoneFallbackRoute(rigSelectionStore.selectedProfile());
    }

    private void initializeOperateRxPipeline() {
        operateMicrophoneRxAudioSource = new MicrophoneRxAudioSource(this);
        operateMicrophoneRxAudioSource.setCallback(this);
        operateSignalProcessor = new CwSignalProcessor();
        operateTimingModel = new CwHybridTimingModel();
        operateDecoder = new CwDecoder();
        operateRawInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        operateSemanticInterpreter = new CwInterpreter(CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY);
        operateQsoStateMachine = new org.bi9clt.cwcn.core.qso.QsoStateMachine();
        operateAudioSpectrumAnalyzer = new AudioSpectrumAnalyzer();
        QsoDraftSnapshot persistedDraft = localLogRepository == null ? null : localLogRepository.loadDraft();
        if (persistedDraft != null) {
            operateQsoStateMachine.restoreDraft(persistedDraft);
        }
        syncOperatePreferredTone();
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
        syncOperatePreferredTone();
        syncOperateSql();
        if (!shouldUsePhoneMicrophoneRx() || !hasMicrophonePermission()) {
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

    private void syncOperateSql() {
        if (operateSignalProcessor == null) {
            return;
        }
        operateSignalProcessor.setSqlPercent(sqlLevel);
    }

    private void startOperateRxCapture() {
        if (operateMicrophoneRxAudioSource == null) {
            return;
        }
        if (operateMicrophoneRxAudioSource.state() == RxAudioSource.State.RUNNING
                || operateMicrophoneRxAudioSource.state() == RxAudioSource.State.STARTING) {
            return;
        }
        operateMicrophoneRxAudioSource.start();
    }

    private void stopOperateRxCapture(boolean publishFinalSnapshot) {
        if (operateMicrophoneRxAudioSource == null) {
            return;
        }
        RxAudioSource.State state = operateMicrophoneRxAudioSource.state();
        if (state == RxAudioSource.State.IDLE || state == RxAudioSource.State.STOPPING) {
            return;
        }
        flushOperatePendingDecode(SystemClock.elapsedRealtime());
        if (publishFinalSnapshot) {
            publishOperateSessionSnapshot(false, true);
        }
        operateMicrophoneRxAudioSource.stop();
    }

    private void clearOperateRxPresentationState() {
        if (rxSessionStore != null) {
            rxSessionStore.clear();
        }
        lastRxSessionSnapshot = null;
    }

    private String resolveOperateRouteSummary(@Nullable RigProfile profile) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return resolveOperateRouteSummaryProductionV2(profile);
        }
        if (profile != null) {
            return safeCompact(profile.displayName(), 18);
        }
        return usePhoneFallbackRoute(null) ? "手机麦克风/音频" : "未配置链路";
    }

    private String resolveTxRouteLabel(@Nullable RigProfile profile) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return resolveTxRouteLabelProductionV2(profile);
        }
        if (profile != null) {
            return profile.displayName();
        }
        return usePhoneFallbackRoute(null) ? "手机音频兜底" : "未配置发射链路";
    }

    private String safeCompact(String value, int maxLength) {
        String safe = safeValue(value);
        if ("-".equals(safe) || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 1)) + "~";
    }

    private String renderTemplatePreview() {
        String templateText = buildTemplateText(selectedTemplate, lastOverview);
        if (templateText.length() <= 20) {
            return templateText;
        }
        return templateText.substring(0, 17) + "...";
    }

    private String renderReceiveMeta(RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(safeValue(snapshot.phaseDisplayName()))
                .append("  |  ")
                .append("原始")
                .append("  |  ")
                .append(renderAge(snapshot.updatedAtEpochMs()));
        if (snapshot.needManualReview()) {
            builder.append("  |  待复核");
        }
        return builder.toString();
    }

    private String renderDraftStreamMeta(QsoDraftSnapshot draft) {
        return safeValue(draft.remoteCallsignCandidate())
                + "  |  "
                + draft.phase().displayName()
                + "  |  "
                + (draft.needManualReview() ? "review" : "tracking");
    }

    private String renderDraftStreamBody(QsoDraftSnapshot draft) {
        String normalized = normalizedTextOrNull(draft.normalizedText());
        StringBuilder builder = new StringBuilder();
        if (normalized != null) {
            builder.append(normalized);
        } else {
            builder.append("No normalized draft text yet.");
        }
        builder.append("\nRST ")
                .append(safeValue(draft.rstSentCandidate()))
                .append("/")
                .append(safeValue(draft.rstRcvdCandidate()))
                .append("  |  Name ")
                .append(safeValue(draft.nameCandidate()))
                .append("  |  QTH ")
                .append(safeValue(draft.qthCandidate()));
        return builder.toString();
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
            binding.txComposeEditText.setText(buildTemplateText(template, lastOverview));
            binding.txComposeEditText.setSelection(binding.txComposeEditText.getText() == null
                    ? 0
                    : binding.txComposeEditText.getText().length());
        }
        updateComposerUi();
        updateOverlayContent();
    }

    private void updateTemplateButtonStates() {
        boolean cq = TEMPLATE_CQ.equals(selectedTemplate);
        boolean cqDx = TEMPLATE_CQ_DX.equals(selectedTemplate);
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

    private String buildTemplateText(String template, @Nullable AppOverviewSnapshot overview) {
        String myCall = resolveStationCallsign(overview);
        if (TEMPLATE_CQ_DX.equals(template)) {
            return "CQ DX CQ DX DE " + myCall + " K";
        }
        if (TEMPLATE_QRZ.equals(template)) {
            return "QRZ? DE " + myCall + " K";
        }
        if (TEMPLATE_TU73.equals(template)) {
            return "TU 73 DE " + myCall + " SK";
        }
        return "CQ CQ DE " + myCall + " K";
    }

    private String resolveStationCallsign(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore == null ? null : stationProfileStore.stationCallsign();
        if (hasMeaningfulText(saved)) {
            return saved.trim();
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.stationCallsignUsed())) {
            return draft.stationCallsignUsed().trim();
        }
        return "<MYCALL>";
    }

    private List<String> safeHints(@Nullable QsoDraftSnapshot draft) {
        if (draft == null || draft.hints() == null) {
            return Collections.emptyList();
        }
        return draft.hints();
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
        updateOverlayContentSafe();
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
        binding.draftOverlayContent.setVisibility(overlayMode == OverlayMode.DRAFT ? View.VISIBLE : View.GONE);

        switch (overlayMode) {
            case CHART:
                binding.overlayTitleText.setText("接收视图");
                binding.overlaySubtitleText.setText("当前接收快照");
                binding.chartOverlayText.setText(renderChartOverlayText(lastRxSessionSnapshot));
                break;
            case SQL:
                binding.overlayTitleText.setText("SQL");
                binding.overlaySubtitleText.setText("当前门限参考");
                binding.sqlOverlayText.setText(renderSqlOverlayText(lastRxSessionSnapshot));
                break;
            case TEMPLATE:
                binding.overlayTitleText.setText("模板");
                binding.overlaySubtitleText.setText("当前待发送文本");
                binding.templateOverlayText.setText(renderTemplateOverlayText());
                break;
            case DRAFT:
                binding.overlayTitleText.setText("通联详情");
                binding.overlaySubtitleText.setText("当前草稿摘要");
                binding.draftOverlayText.setText(renderDraftOverlayText(lastOverview, lastRxSessionSnapshot));
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
        return "状态: " + safeValue(snapshot.captureState())
                + "\n来源: " + safeValue(snapshot.sourceLabel())
                + "\n阶段: " + safeValue(snapshot.phaseDisplayName())
                + "\n速度: " + positiveOrDash(snapshot.estimatedWpm()) + " WPM"
                + "\n音调 偏好/跟踪/有效: "
                + positiveOrDash(snapshot.preferredToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.targetToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.effectiveToneFrequencyHz())
                + " Hz"
                + "\n对方呼号: " + safeValue(snapshot.remoteCallsign())
                + "\n更新时间: " + renderAge(snapshot.updatedAtEpochMs());
    }

    private String renderSqlQuickChipText() {
        return "SQL " + sqlLevel;
    }

    private String renderSqlOverlayText(@Nullable RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("门限: ").append(sqlLevel).append("%");
        if (snapshot == null) {
            builder.append("\n\n当前没有活动接收会话。");
            return builder.toString();
        }
        builder.append("\n把这个值作为当前接收门限参考。")
                .append("\n速度: ").append(positiveOrDash(snapshot.estimatedWpm())).append(" WPM")
                .append("  |  音调: ").append(positiveOrDash(resolveBestTone(snapshot))).append(" Hz")
                .append("\n复核标记: ").append(yesNo(snapshot.needManualReview()))
                .append("\n更新时间: ").append(renderAge(snapshot.updatedAtEpochMs()));
        return builder.toString();
    }

    private String renderTemplateOverlayText() {
        return "当前模板: " + selectedTemplate
                + "\n\n"
                + buildTemplateText(selectedTemplate, lastOverview);
    }

    private String renderDraftOverlayText(
            @Nullable AppOverviewSnapshot overview,
            @Nullable RxSessionSnapshot snapshot
    ) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "当前没有活动草稿。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("对方呼号: ").append(safeValue(draft.remoteCallsignCandidate()))
                .append("\n阶段: ").append(draft.phase().displayName())
                .append("\nRST: ").append(safeValue(draft.rstSentCandidate()))
                .append("/").append(safeValue(draft.rstRcvdCandidate()))
                .append("\n姓名: ").append(safeValue(draft.nameCandidate()))
                .append("\nQTH: ").append(safeValue(draft.qthCandidate()))
                .append("\n可确认: ").append(yesNo(draft.readyForDraftConfirmation()))
                .append("\n人工复核: ").append(yesNo(draft.needManualReview()))
                .append("\n更新时间: ").append(renderAge(draft.updatedAtEpochMs()))
                .append("\n\n归一化草稿:\n")
                .append(safeValue(draft.normalizedText()));

        List<String> hints = safeHints(draft);
        if (hints.isEmpty()) {
            builder.append("\n\n提示: -");
        } else {
            builder.append("\n\n提示:");
            for (String hint : hints) {
                builder.append("\n- ").append(hint);
            }
        }

        if (snapshot != null && hasMeaningfulText(snapshot.rawText())) {
            builder.append("\n\n最近 RAW:\n").append(snapshot.rawText().trim());
        }
        return builder.toString();
    }

    @Override
    public void onStateChanged(RxAudioSource.State state, String detail) {
        operateRxState = state == null ? RxAudioSource.State.IDLE : state;
        if (operateRxState == RxAudioSource.State.IDLE && shouldUsePhoneMicrophoneRx() && hasMicrophonePermission()) {
            publishOperateSessionSnapshot(false, true);
        } else if (operateRxState == RxAudioSource.State.ERROR) {
            publishOperateSessionSnapshot(false, true);
        }
        mainHandler.post(this::refreshUi);
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        if (frame == null
                || operateSignalProcessor == null
                || operateTimingModel == null
                || operateDecoder == null
                || operateRawInterpreter == null
                || operateSemanticInterpreter == null
                || operateQsoStateMachine == null) {
            return;
        }

        List<CwToneEvent> toneEvents = operateSignalProcessor.process(frame);
        captureOperateSpectrumSnapshot(frame);
        for (CwToneEvent toneEvent : toneEvents) {
            List<CwTimingEvent> timingEvents = operateTimingModel.process(toneEvent);
            for (CwTimingEvent timingEvent : timingEvents) {
                processOperateTimingEvent(timingEvent);
            }
        }
        maybeFlushPendingCharacterDuringSilence(frame);
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
                System.currentTimeMillis()
        );
        if (snapshotData != null) {
            spectrumHistoryStore.append(snapshotData);
        }
    }

    @Override
    public void onError(String message, Throwable throwable) {
        operateRxState = RxAudioSource.State.ERROR;
        publishOperateSessionSnapshot(false, true);
        mainHandler.post(this::refreshUi);
    }

    private void processOperateTimingEvent(CwTimingEvent timingEvent) {
        if (operateDecoder == null
                || operateRawInterpreter == null
                || operateSemanticInterpreter == null
                || operateQsoStateMachine == null) {
            return;
        }
        List<CwDecodeEvent> decodeEvents = operateDecoder.process(timingEvent);
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            operateRawInterpreter.process(decodeEvent);
            operateSemanticInterpreter.process(decodeEvent);
            operateQsoStateMachine.process(operateSemanticInterpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private void flushOperatePendingDecode(long timestampMs) {
        if (operateTimingModel == null || operateDecoder == null) {
            return;
        }
        List<CwTimingEvent> timingEvents = operateTimingModel.flushPendingGap(timestampMs);
        for (CwTimingEvent timingEvent : timingEvents) {
            processOperateTimingEvent(timingEvent);
        }
        List<CwDecodeEvent> trailingDecodeEvents = operateDecoder.flushPendingCharacter(timestampMs);
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            operateRawInterpreter.process(decodeEvent);
            operateSemanticInterpreter.process(decodeEvent);
            operateQsoStateMachine.process(operateSemanticInterpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private void maybeFlushPendingCharacterDuringSilence(AudioFrame frame) {
        if (frame == null
                || operateDecoder == null
                || operateTimingModel == null
                || operateSignalProcessor == null
                || !operateDecoder.hasPendingCharacter()) {
            return;
        }

        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
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
        if (silentGapMs < minimumOperateCharacterFlushGapMs()) {
            return;
        }

        List<CwDecodeEvent> trailingDecodeEvents = operateDecoder.flushPendingCharacter(flushTimestampMs);
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            operateRawInterpreter.process(decodeEvent);
            operateSemanticInterpreter.process(decodeEvent);
            operateQsoStateMachine.process(operateSemanticInterpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private long minimumOperateCharacterFlushGapMs() {
        if (operateTimingModel == null) {
            return 1L;
        }
        CwTimingSnapshot timingSnapshot = operateTimingModel.snapshot();
        long dotEstimateMs = Math.max(1L, timingSnapshot.dotEstimateMs());
        return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
    }

    private void publishOperateSessionSnapshot(boolean throttle, boolean force) {
        if (rxSessionStore == null
                || operateSignalProcessor == null
                || operateTimingModel == null
                || operateRawInterpreter == null
                || operateQsoStateMachine == null) {
            return;
        }
        long nowElapsedMs = SystemClock.elapsedRealtime();
        if (!force && throttle && (nowElapsedMs - lastOperateRxPublishAtElapsedMs) < OPERATE_RX_PUBLISH_INTERVAL_MS) {
            return;
        }

        CwSignalSnapshot signalSnapshot = operateSignalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = operateTimingModel.snapshot();
        CwInterpreterSnapshot rawSnapshot = operateRawInterpreter.snapshot();
        QsoDraftSnapshot qsoSnapshot = operateQsoStateMachine.snapshot();
        RxSessionSnapshot sessionSnapshot = new RxSessionSnapshot(
                System.currentTimeMillis(),
                operateMicrophoneRxAudioSource == null ? "Phone Microphone" : operateMicrophoneRxAudioSource.displayName(),
                operateRxState.name(),
                operateRxState == RxAudioSource.State.RUNNING || operateRxState == RxAudioSource.State.STARTING,
                signalSnapshot.preferredToneFrequencyHz(),
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.effectiveTrackedToneFrequencyHz(),
                timingSnapshot.estimatedWpm(),
                rawSnapshot == null ? "" : rawSnapshot.rawText(),
                qsoSnapshot == null ? "" : qsoSnapshot.normalizedText(),
                qsoSnapshot == null || qsoSnapshot.phase() == null ? "" : qsoSnapshot.phase().displayName(),
                qsoSnapshot == null ? "" : qsoSnapshot.remoteCallsignCandidate(),
                qsoSnapshot != null && qsoSnapshot.readyForDraftConfirmation(),
                qsoSnapshot != null && qsoSnapshot.needManualReview()
        );
        rxSessionStore.save(sessionSnapshot);
        if (localLogRepository != null && qsoSnapshot != null) {
            localLogRepository.saveDraft(qsoSnapshot);
        }
        lastOperateRxPublishAtElapsedMs = nowElapsedMs;
        lastRxSessionSnapshot = sessionSnapshot;
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
    }

    @Nullable
    private String resolveImmediateTxText() {
        String composeText = binding.txComposeEditText.getText() == null
                ? ""
                : binding.txComposeEditText.getText().toString().trim();
        if (!composeText.isEmpty()) {
            return composeText;
        }
        String templateText = buildTemplateText(selectedTemplate, lastOverview);
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
                || adapter instanceof UsbSerialKeyerRigControlAdapter;
    }

    private boolean hasRepeatableTxText() {
        return hasMeaningfulText(activeTxText) || hasMeaningfulText(resolveImmediateTxText());
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
        return txSendInProgress
                || hasMeaningfulText(txDeliveryStatus)
                || activeTxPlaybackSnapshot != null;
    }

    private CharSequence renderActiveTxStreamBody(String visibleTxText) {
        if (!hasMeaningfulText(visibleTxText)) {
            return "";
        }
        SpannableString styled = new SpannableString(visibleTxText);
        int bodyColor = ContextCompat.getColor(this, R.color.cwcn_body);
        int completedColor = ContextCompat.getColor(this, R.color.cwcn_subtitle);
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
        if (snapshot.totalDurationMs() > 0) {
            int percent = (int) Math.round(snapshot.completionRatio() * 100.0d);
            return Math.max(0, Math.min(100, percent)) + "%";
        }
        if (snapshot.totalElementCount() > 0) {
            return snapshot.completedElementCount() + "/" + snapshot.totalElementCount();
        }
        return snapshot.state().displayName();
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
            return ageSeconds + "s ago";
        }
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) {
            return ageMinutes + "m ago";
        }
        long ageHours = ageMinutes / 60L;
        return String.format(Locale.US, "%dh ago", ageHours);
    }

    private String renderStatusMainSafe(@Nullable RxSessionSnapshot snapshot) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderStatusMainProductionV2(snapshot);
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return "麦克风 | 需授权";
            }
            return usePhoneFallbackRoute(profile)
                    ? "麦克风 | 待命"
                    : "空闲 | 无接收";
        }
        int tone = resolveBestTone(snapshot);
        String remote = safeValue(snapshot.remoteCallsign());
        String capture = snapshot.captureActive() ? "接收中" : "保持";
        return capture
                + " | "
                + positiveOrDash(snapshot.estimatedWpm()) + "WPM"
                + " | "
                + positiveOrDash(tone) + "Hz"
                + " | "
                + remote;
    }

    private String renderStatusDetailSafe(@Nullable RxSessionSnapshot snapshot) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderStatusDetailProductionV2(snapshot);
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        String rigSummary = resolveOperateRouteSummary(profile);
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return rigSummary + " | 允许麦克风";
            }
            return usePhoneFallbackRoute(profile)
                    ? rigSummary + " | 手机接收"
                    : rigSummary + " | 等待中";
        }
        return safeValue(snapshot.phaseDisplayName())
                + " | "
                + rigSummary
                + " | "
                + renderAgeSafe(snapshot.updatedAtEpochMs());
    }

    private String renderSourceChipSafe(@Nullable RxSessionSnapshot snapshot) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderSourceChipProductionV2(snapshot);
        }
        if (snapshot == null) {
            return usePhoneFallbackRoute(rigSelectionStore.selectedProfile()) ? "麦克风" : "空闲";
        }
        return snapshot.captureActive() ? "接收中" : "保持";
    }

    private List<StreamEntry> buildOperateStreamEntries(
            @Nullable RxSessionSnapshot snapshot,
            @Nullable AppOverviewSnapshot overview
    ) {
        java.util.ArrayList<StreamEntry> entries = new java.util.ArrayList<>();
        RigProfile profile = rigSelectionStore.selectedProfile();

        if (snapshot == null) {
            entries.add(new StreamEntry(
                    "接收",
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
                    false
            ));
        } else {
            String normalizedText = normalizedTextOrNull(snapshot.normalizedText());
            String rawText = normalizedTextOrNull(snapshot.rawText());
            entries.add(new StreamEntry(
                    "接收",
                    renderReceiveMetaSafe(snapshot),
                    safeValue(rawText),
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false
            ));
            if (rawText != null
                    && normalizedText != null
                    && !normalizedText.equals(rawText)) {
                entries.add(new StreamEntry(
                        "解释",
                        safeValue(snapshot.phaseDisplayName()) + "  |  归一化",
                        normalizedText,
                        R.drawable.operate_stream_card_txt,
                        R.color.cwcn_accent,
                        false,
                        true
                ));
            }
        }

        String txDraft = binding.txComposeEditText.getText() == null
                ? ""
                : binding.txComposeEditText.getText().toString().trim();
        String visibleTxText = resolveVisibleActiveTxText(txDraft);
        if (shouldShowTransmitStream(visibleTxText)) {
            entries.add(new StreamEntry(
                    "发射",
                    renderActiveTxStreamMetaSafe(),
                    renderActiveTxStreamBodySafe(visibleTxText),
                    R.drawable.operate_stream_card_tx,
                    R.color.cwcn_tx_line,
                    true,
                    false
            ));
        }

        if (entries.isEmpty()) {
            entries.add(new StreamEntry(
                    "接收",
                    "-",
                    "-",
                    R.drawable.operate_stream_card_rx,
                    R.color.cwcn_rx_line,
                    false,
                    false
            ));
        }
        return entries;
    }

    private String renderDraftPreviewSafe(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "";
        }
        String normalized = normalizedTextOrNull(draft.normalizedText());
        if (normalized != null) {
            return normalized;
        }
        List<String> hints = safeHints(draft);
        if (!hints.isEmpty()) {
            return hints.get(0);
        }
        return "暂时还没有可读的草稿内容。";
    }

    private String renderTxStatusSafe(boolean hasDraft) {
        if (txSendInProgress) {
            if (txStopRequested) {
                return activeTxStopSupported ? "正在停止发射" : "已请求停止，等待适配器响应";
            }
            if (activeTxPlaybackSnapshot != null && hasMeaningfulText(activeTxPlaybackSnapshot.statusMessage())) {
                return renderPlaybackSnapshotStatusSafe(activeTxPlaybackSnapshot);
            }
            return hasDraft ? "正在发送文本" : "正在发送模板";
        }
        if (txDeliveryStatus != null && !txDeliveryStatus.trim().isEmpty()) {
            return txDeliveryStatus;
        }
        return "";
    }

    private String renderTxRouteChipSafe() {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderTxRouteChipProductionV2();
        }
        int wpm = resolveOperateWpm();
        RigProfile profile = rigSelectionStore.selectedProfile();
        String route;
        if (txSendInProgress && hasMeaningfulText(activeTxRouteLabel)) {
            route = safeCompact(activeTxRouteLabel, 12);
        } else if (usePhoneFallbackRoute(profile)) {
            route = "手机发射";
        } else if (profile == null) {
            route = "未配置";
        } else {
            route = safeCompact(profile.displayName(), 12);
        }
        return wpm + "WPM | " + route;
    }

    private String renderTxStageChipSafe(boolean hasDraft, String draftText) {
        if (txSendInProgress && activeTxPlaybackSnapshot != null) {
            return renderPlaybackProgressChipSafe(activeTxPlaybackSnapshot);
        }
        if (!hasDraft) {
            return "模板 " + selectedTemplate;
        }
        int length = draftText == null || "-".equals(draftText) ? 0 : draftText.length();
        return "文本 " + length + "字";
    }

    private void updateOverlayContentSafe() {
        updateSideButtonStates();
        if (overlayMode == OverlayMode.NONE) {
            return;
        }
        binding.chartOverlayContent.setVisibility(overlayMode == OverlayMode.CHART ? View.VISIBLE : View.GONE);
        binding.sqlOverlayContent.setVisibility(overlayMode == OverlayMode.SQL ? View.VISIBLE : View.GONE);
        binding.templateOverlayContent.setVisibility(overlayMode == OverlayMode.TEMPLATE ? View.VISIBLE : View.GONE);
        binding.draftOverlayContent.setVisibility(overlayMode == OverlayMode.DRAFT ? View.VISIBLE : View.GONE);

        switch (overlayMode) {
            case CHART:
                binding.overlayTitleText.setText("接收视图");
                binding.overlaySubtitleText.setText("当前接收快照");
                binding.chartOverlayText.setText(renderChartOverlayTextSafe(lastRxSessionSnapshot));
                break;
            case SQL:
                binding.overlayTitleText.setText("调谐");
                binding.overlaySubtitleText.setText("调整接收门限与发射速度");
                syncSqlSeekBar();
                syncWpmSeekBarFromSettings();
                binding.sqlOverlayText.setText(renderSqlOverlayTextSafe(lastRxSessionSnapshot));
                break;
            case TEMPLATE:
                binding.overlayTitleText.setText("模板");
                binding.overlaySubtitleText.setText("当前待发送文本");
                binding.templateOverlayText.setText(renderTemplateOverlayTextSafe());
                break;
            case DRAFT:
                binding.overlayTitleText.setText("通联详情");
                binding.overlaySubtitleText.setText("当前草稿摘要");
                binding.draftOverlayText.setText(renderDraftOverlayTextSafe(lastOverview, lastRxSessionSnapshot));
                break;
            default:
                break;
        }
    }

    private String renderChartOverlayTextSafe(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return "当前没有活动接收会话。\n\n启用接收后，这里会显示当前接收快照。";
        }
        return "状态: " + safeValue(snapshot.captureState())
                + "\n来源: " + safeValue(snapshot.sourceLabel())
                + "\n阶段: " + safeValue(snapshot.phaseDisplayName())
                + "\n速度: " + positiveOrDash(snapshot.estimatedWpm()) + " WPM"
                + "\n音调 偏好/跟踪/有效: "
                + positiveOrDash(snapshot.preferredToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.targetToneFrequencyHz())
                + " / "
                + positiveOrDash(snapshot.effectiveToneFrequencyHz())
                + " Hz"
                + "\n对方呼号: " + safeValue(snapshot.remoteCallsign())
                + "\n更新时间: " + renderAgeSafe(snapshot.updatedAtEpochMs());
    }

    private String renderSqlOverlayTextSafe(@Nullable RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("SQL 门限: ").append(sqlLevel).append("%")
                .append("\n发射速度: ").append(resolveOperateWpm()).append(" WPM");
        if (snapshot == null) {
            builder.append("\n\n当前没有活动接收会话。");
            return builder.toString();
        }
        builder.append("\n\n把这组值作为当前收发调谐参考。")
                .append("\n接收速度: ").append(positiveOrDash(snapshot.estimatedWpm())).append(" WPM")
                .append("  |  音调: ").append(positiveOrDash(resolveBestTone(snapshot))).append(" Hz")
                .append("\n待复核: ").append(yesNoZh(snapshot.needManualReview()))
                .append("\n更新时间: ").append(renderAgeSafe(snapshot.updatedAtEpochMs()));
        return builder.toString();
    }

    private String renderTemplateOverlayTextSafe() {
        return "当前模板: " + selectedTemplate + "\n\n" + buildTemplateText(selectedTemplate, lastOverview);
    }

    private String renderDraftOverlayTextSafe(
            @Nullable AppOverviewSnapshot overview,
            @Nullable RxSessionSnapshot snapshot
    ) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft == null) {
            return "当前没有活动草稿。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("对方呼号: ").append(safeValue(draft.remoteCallsignCandidate()))
                .append("\n阶段: ").append(draft.phase().displayName())
                .append("\nRST: ").append(safeValue(draft.rstSentCandidate()))
                .append("/").append(safeValue(draft.rstRcvdCandidate()))
                .append("\n姓名: ").append(safeValue(draft.nameCandidate()))
                .append("\nQTH: ").append(safeValue(draft.qthCandidate()))
                .append("\n可确认: ").append(yesNoZh(draft.readyForDraftConfirmation()))
                .append("\n人工复核: ").append(yesNoZh(draft.needManualReview()))
                .append("\n更新时间: ").append(renderAgeSafe(draft.updatedAtEpochMs()))
                .append("\n\n归一化草稿:\n")
                .append(safeValue(draft.normalizedText()));

        List<String> hints = safeHints(draft);
        if (hints.isEmpty()) {
            builder.append("\n\n提示: -");
        } else {
            builder.append("\n\n提示:");
            for (String hint : hints) {
                builder.append("\n- ").append(hint);
            }
        }
        if (snapshot != null && hasMeaningfulText(snapshot.rawText())) {
            builder.append("\n\n最近 RAW:\n").append(snapshot.rawText().trim());
        }
        return builder.toString();
    }

    private String renderReceiveMetaSafe(RxSessionSnapshot snapshot) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderReceiveMetaProductionV2(snapshot);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(safeValue(snapshot.phaseDisplayName()))
                .append("  |  原始")
                .append("  |  ")
                .append(renderAgeSafe(snapshot.updatedAtEpochMs()));
        if (snapshot.needManualReview()) {
            builder.append("  |  待复核");
        }
        return builder.toString();
    }

    private CharSequence renderActiveTxStreamBodySafe(String visibleTxText) {
        if (!hasMeaningfulText(visibleTxText)) {
            return "";
        }
        SpannableString styled = new SpannableString(visibleTxText);
        int bodyColor = ContextCompat.getColor(this, R.color.cwcn_body);
        int completedColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentColor = ContextCompat.getColor(this, R.color.cwcn_tx_line);
        int currentIndex = activeTxPlaybackSnapshot == null ? -1 : activeTxPlaybackSnapshot.currentTextIndex();
        if (currentIndex < 0 || currentIndex >= visibleTxText.length()) {
            styled.setSpan(new ForegroundColorSpan(bodyColor), 0, visibleTxText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return styled;
        }
        if (currentIndex > 0) {
            styled.setSpan(new ForegroundColorSpan(completedColor), 0, currentIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        styled.setSpan(new ForegroundColorSpan(currentColor), currentIndex, currentIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), currentIndex, currentIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (currentIndex + 1 < visibleTxText.length()) {
            styled.setSpan(new ForegroundColorSpan(bodyColor), currentIndex + 1, visibleTxText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    private String renderActiveTxStreamMetaSafe() {
        String route = hasMeaningfulText(activeTxRouteLabel) ? activeTxRouteLabel : renderTxRouteChipSafe();
        String stage = activeTxPlaybackSnapshot == null ? "发射中" : renderPlaybackProgressChipSafe(activeTxPlaybackSnapshot);
        return route + "  |  " + stage;
    }

    private String renderPlaybackProgressChipSafe(@Nullable CwTxPlaybackSnapshot snapshot) {
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

    private String renderPlaybackSnapshotStatusSafe(@Nullable CwTxPlaybackSnapshot snapshot) {
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

    private String renderAgeSafe(long updatedAtEpochMs) {
        if (SystemClock.elapsedRealtime() >= 0L) {
            return renderAgeProductionV2(updatedAtEpochMs);
        }
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

    private String yesNoZh(boolean value) {
        return value ? "是" : "否";
    }
    private String resolveOperateRouteSummaryProduction(@Nullable RigProfile profile) {
        return "RX " + resolveRxRouteLabelProduction(profile) + " | TX " + resolveTxRouteLabelProduction(profile);
    }

    private String resolveRxRouteLabelProduction(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            return hasMicrophonePermission() ? "手机麦克风" : "手机麦克风待授权";
        }
        if (profile == null) {
            return "未配置";
        }
        return "电台音频未接入";
    }

    private String resolveTxRouteLabelProduction(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            return "手机音频";
        }
        if (profile == null) {
            return "未配置";
        }
        return safeCompact(profile.displayName(), 18);
    }

    private String resolveOperateRxHintProduction(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            if (!hasMicrophonePermission()) {
                return "需要先授权麦克风，才能开始手机接收。";
            }
            return "当前按默认策略走手机麦克风接收。";
        }
        if (profile == null) {
            return "当前没有选中的电台路径，也没有启用手机兜底。";
        }
        return "已选电台发射路径，但正式 RX 仍未接入电台音频。";
    }

    private String renderRxFootnoteProduction(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null && shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
            return "点这里授权麦克风，开始手机接收。";
        }
        if (snapshot == null) {
            return resolveOperateRxHintProduction(profile);
        }
        if (snapshot.needManualReview()) {
            return "接收内容建议先人工复核，再确认草稿。";
        }
        if (snapshot.readyForDraftConfirmation()) {
            return "当前接收已经可以支持草稿确认。";
        }
        return "";
    }

    private String renderStatusMainProduction(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return "手机接收 | 等待授权";
            }
            return usePhoneFallbackRoute(profile)
                    ? "手机接收 | 待命"
                    : "接收未就绪 | 无电台音频";
        }
        int tone = resolveBestTone(snapshot);
        String remote = safeValue(snapshot.remoteCallsign());
        String capture = snapshot.captureActive() ? "接收中" : "保持中";
        return capture
                + " | "
                + positiveOrDash(snapshot.estimatedWpm()) + "WPM"
                + " | "
                + positiveOrDash(tone) + "Hz"
                + " | "
                + remote;
    }

    private String renderStatusDetailProduction(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        String rigSummary = resolveOperateRouteSummaryProduction(profile);
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return rigSummary + " | 等待麦克风权限";
            }
            return usePhoneFallbackRoute(profile)
                    ? rigSummary + " | 手机接收待命"
                    : rigSummary + " | 已选发射路径，接收尚未接入电台音频";
        }
        return safeValue(snapshot.phaseDisplayName())
                + " | "
                + rigSummary
                + " | "
                + renderAgeProduction(snapshot.updatedAtEpochMs());
    }

    private String renderSourceChipProduction(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return usePhoneFallbackRoute(rigSelectionStore.selectedProfile()) ? "手机接收" : "未接收";
        }
        return snapshot.captureActive() ? "接收中" : "保持中";
    }

    private String renderTxRouteChipProduction() {
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

    private String renderReceiveMetaProduction(RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(safeValue(snapshot.phaseDisplayName()))
                .append("  |  RAW")
                .append("  |  ")
                .append(renderAgeProduction(snapshot.updatedAtEpochMs()));
        if (snapshot.needManualReview()) {
            builder.append("  |  待复核");
        }
        return builder.toString();
    }

    private String renderAgeProduction(long updatedAtEpochMs) {
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
    private String resolveOperateRouteSummaryProductionV2(@Nullable RigProfile profile) {
        return "RX " + resolveRxRouteLabelProductionV2(profile) + " | TX " + resolveTxRouteLabelProductionV2(profile);
    }

    private String resolveRxRouteLabelProductionV2(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            return hasMicrophonePermission() ? "手机麦克风" : "手机麦克风待授权";
        }
        if (profile == null) {
            return "未配置";
        }
        return "电台音频未接入";
    }

    private String resolveTxRouteLabelProductionV2(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            return "手机音频";
        }
        if (profile == null) {
            return "未配置";
        }
        return safeCompact(profile.displayName(), 18);
    }

    private String resolveOperateRxHintProductionV2(@Nullable RigProfile profile) {
        if (usePhoneFallbackRoute(profile)) {
            if (!hasMicrophonePermission()) {
                return "需要先授权麦克风，才能开始手机接收。";
            }
            return "当前按默认策略走手机麦克风接收。";
        }
        if (profile == null) {
            return "当前没有选中的电台路径，也没有启用手机兜底。";
        }
        return "已选电台发射路径，但正式 RX 仍未接入电台音频。";
    }

    private String renderRxFootnoteProductionV2(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null && shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
            return "点这里授权麦克风，开始手机接收。";
        }
        if (snapshot == null) {
            return resolveOperateRxHintProductionV2(profile);
        }
        if (snapshot.needManualReview()) {
            return "接收内容建议先人工复核，再确认草稿。";
        }
        if (snapshot.readyForDraftConfirmation()) {
            return "当前接收已经可以支持草稿确认。";
        }
        return "";
    }

    private String renderStatusMainProductionV2(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return "手机接收 | 等待授权";
            }
            return usePhoneFallbackRoute(profile)
                    ? "手机接收 | 待命"
                    : "接收未就绪 | 无电台音频";
        }
        int tone = resolveBestTone(snapshot);
        String remote = safeValue(snapshot.remoteCallsign());
        String capture = snapshot.captureActive() ? "接收中" : "保持中";
        return capture
                + " | "
                + positiveOrDash(snapshot.estimatedWpm()) + "WPM"
                + " | "
                + positiveOrDash(tone) + "Hz"
                + " | "
                + remote;
    }

    private String renderStatusDetailProductionV2(@Nullable RxSessionSnapshot snapshot) {
        RigProfile profile = rigSelectionStore.selectedProfile();
        String rigSummary = resolveOperateRouteSummaryProductionV2(profile);
        if (snapshot == null) {
            if (shouldUsePhoneMicrophoneRx() && !hasMicrophonePermission()) {
                return rigSummary + " | 等待麦克风权限";
            }
            return usePhoneFallbackRoute(profile)
                    ? rigSummary + " | 手机接收待命"
                    : rigSummary + " | 已选发射路径，接收尚未接入电台音频";
        }
        return safeValue(snapshot.phaseDisplayName())
                + " | "
                + rigSummary
                + " | "
                + renderAgeProductionV2(snapshot.updatedAtEpochMs());
    }

    private String renderSourceChipProductionV2(@Nullable RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            return usePhoneFallbackRoute(rigSelectionStore.selectedProfile()) ? "手机接收" : "未接收";
        }
        return snapshot.captureActive() ? "接收中" : "保持中";
    }

    private String renderTxRouteChipProductionV2() {
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

    private String renderReceiveMetaProductionV2(RxSessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(safeValue(snapshot.phaseDisplayName()))
                .append("  |  RAW")
                .append("  |  ")
                .append(renderAgeProductionV2(snapshot.updatedAtEpochMs()));
        if (snapshot.needManualReview()) {
            builder.append("  |  待复核");
        }
        return builder.toString();
    }

    private String renderAgeProductionV2(long updatedAtEpochMs) {
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
}

