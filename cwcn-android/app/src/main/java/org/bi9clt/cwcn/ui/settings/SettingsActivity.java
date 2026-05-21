package org.bi9clt.cwcn.ui.settings;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.app.RouteFallbackStore;
import org.bi9clt.cwcn.core.app.RxInputSettingsStore;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.app.TxTemplateStore;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.log.MaidenheadGridUtil;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRouteStatusFormatter;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.KeyingPolarity;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.core.rig.UsbSerialDeviceOption;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;
import org.bi9clt.cwcn.databinding.ActivitySettingsBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;

import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends AppCompatActivity {
    private static final String ACTION_USB_KEYER_PERMISSION =
            "org.bi9clt.cwcn.action.SETTINGS_USB_KEYER_PERMISSION";
    private static final int REQUEST_LOCATION_PERMISSION = 2001;

    private enum FixedToneLearningWindowPreset {
        TIGHT("人工锁频: 紧锁 ±30Hz", "紧锁", 30),
        STANDARD("人工锁频: 标准 ±50Hz", "标准", 50),
        WIDE("人工锁频: 宽松 ±70Hz", "宽松", 70),
        CUSTOM("人工锁频: 自定义", "自定义", null);

        private final String displayName;
        private final String shortLabel;
        @Nullable
        private final Integer windowHz;

        FixedToneLearningWindowPreset(String displayName, String shortLabel, @Nullable Integer windowHz) {
            this.displayName = displayName;
            this.shortLabel = shortLabel;
            this.windowHz = windowHz;
        }

        @Nullable
        Integer windowHz() {
            return windowHz;
        }

        String shortLabel() {
            return shortLabel;
        }

        static FixedToneLearningWindowPreset fromWindowHz(int windowHz) {
            for (FixedToneLearningWindowPreset preset : values()) {
                if (preset.windowHz != null && preset.windowHz == windowHz) {
                    return preset;
                }
            }
            return CUSTOM;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private ActivitySettingsBinding binding;
    private DeveloperModeStore developerModeStore;
    private StationProfileStore stationProfileStore;
    private RigSelectionStore rigSelectionStore;
    private RouteFallbackStore routeFallbackStore;
    private RxInputSettingsStore rxInputSettingsStore;
    private TxTemplateStore txTemplateStore;
    private LocalLogRepository localLogRepository;
    private boolean syncingEditors;
    private boolean autoSaveEnabled;
    private boolean stationProfileDirty;
    private boolean cwDefaultsDirty;
    private boolean txTemplatesDirty;
    private boolean routeSettingsDirty;
    private boolean routeFallbackDirty;
    private String stationProfileStatusMessage = "";
    private String cwDefaultsStatusMessage = "";
    private String txTemplatesStatusMessage = "";
    private String routeSettingsStatusMessage = "";
    private String routeFallbackStatusMessage = "";
    private BroadcastReceiver usbPermissionReceiver;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;
    private boolean syncingUsbDeviceSelection;
    private String usbRouteStatusMessage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        developerModeStore = new DeveloperModeStore(this);
        stationProfileStore = new StationProfileStore(this);
        rigSelectionStore = new RigSelectionStore(this);
        routeFallbackStore = new RouteFallbackStore(this);
        rxInputSettingsStore = new RxInputSettingsStore(this);
        txTemplateStore = new TxTemplateStore(this);
        localLogRepository = new LocalLogRepository(this);
        registerUsbPermissionReceiver();
        binding.versionText.setText("设置 " + BuildConfig.VERSION_NAME);
        setupEditorWatchers();
        setupRouteEditorControls();
        setupRouteFallbackControls();
        setupRxInputControls();
        setupInfoButtons();
        setupActions();
        refreshUi();
        autoSaveEnabled = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onPause() {
        commitPendingEdits();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (usbPermissionReceiver != null) {
            unregisterReceiver(usbPermissionReceiver);
            usbPermissionReceiver = null;
        }
        super.onDestroy();
    }

    private void setupEditorWatchers() {
        TextWatcher stationWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors && autoSaveEnabled) {
                    stationProfileDirty = true;
                    binding.stationProfileStatusText.setText(renderStationProfileEditorStatus());
                }
            }
        };
        TextWatcher cwDefaultsWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors && autoSaveEnabled) {
                    cwDefaultsDirty = true;
                    binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
                }
            }
        };
        TextWatcher txTemplatesWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors && autoSaveEnabled) {
                    txTemplatesDirty = true;
                    binding.txTemplatesStatusText.setText(renderTxTemplatesEditorStatus());
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(stationWatcher);
        binding.operatorNameEditText.addTextChangedListener(stationWatcher);
        binding.qthEditText.addTextChangedListener(stationWatcher);
        binding.maidenheadGridEditText.addTextChangedListener(stationWatcher);
        binding.rigDescriptionEditText.addTextChangedListener(stationWatcher);
        binding.antennaDescriptionEditText.addTextChangedListener(stationWatcher);
        binding.weatherDescriptionEditText.addTextChangedListener(stationWatcher);
        binding.defaultWpmEditText.addTextChangedListener(cwDefaultsWatcher);
        binding.defaultToneEditText.addTextChangedListener(cwDefaultsWatcher);
        binding.fixedToneLearningWindowEditText.addTextChangedListener(cwDefaultsWatcher);
        binding.templateCqEditText.addTextChangedListener(txTemplatesWatcher);
        binding.templateCqDxEditText.addTextChangedListener(txTemplatesWatcher);
        binding.templateQrzEditText.addTextChangedListener(txTemplatesWatcher);
        binding.templateTu73EditText.addTextChangedListener(txTemplatesWatcher);
        TextWatcher routeWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors && autoSaveEnabled) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                }
            }
        };
        binding.serialCatPortHintEditText.addTextChangedListener(routeWatcher);
        binding.serialCatBaudRateEditText.addTextChangedListener(routeWatcher);
        binding.serialCatKeyingPortHintEditText.addTextChangedListener(routeWatcher);
        binding.serialCatCivAddressEditText.addTextChangedListener(routeWatcher);
        binding.networkHostEditText.addTextChangedListener(routeWatcher);
        binding.networkPortEditText.addTextChangedListener(routeWatcher);
        binding.usbPreferredDeviceNameEditText.addTextChangedListener(routeWatcher);

        attachAutoSaveOnFocusLoss(binding.stationCallsignEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.operatorNameEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.qthEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.maidenheadGridEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.rigDescriptionEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.antennaDescriptionEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.weatherDescriptionEditText, this::saveStationProfile);
        attachAutoSaveOnFocusLoss(binding.defaultWpmEditText, this::saveCwDefaults);
        attachAutoSaveOnFocusLoss(binding.defaultToneEditText, this::saveCwDefaults);
        attachAutoSaveOnFocusLoss(binding.fixedToneLearningWindowEditText, this::saveCwDefaults);
        attachAutoSaveOnFocusLoss(binding.templateCqEditText, this::saveTxTemplates);
        attachAutoSaveOnFocusLoss(binding.templateCqDxEditText, this::saveTxTemplates);
        attachAutoSaveOnFocusLoss(binding.templateQrzEditText, this::saveTxTemplates);
        attachAutoSaveOnFocusLoss(binding.templateTu73EditText, this::saveTxTemplates);
        attachAutoSaveOnFocusLoss(binding.serialCatPortHintEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.serialCatBaudRateEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.serialCatKeyingPortHintEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.serialCatCivAddressEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.networkHostEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.networkPortEditText, this::saveRouteSettings);
        attachAutoSaveOnFocusLoss(binding.usbPreferredDeviceNameEditText, this::saveRouteSettings);
    }

    private void setupRxInputControls() {
        ArrayAdapter<RxInputSettingsStore.RxInputMode> inputModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RxInputSettingsStore.RxInputMode.values()
        );
        inputModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxInputModeSpinner.setAdapter(inputModeAdapter);

        ArrayAdapter<RxInputSettingsStore.MicSourceMode> micSourceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RxInputSettingsStore.MicSourceMode.values()
        );
        micSourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxMicSourceModeSpinner.setAdapter(micSourceAdapter);

        ArrayAdapter<RxInputSettingsStore.RxToneMode> toneModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RxInputSettingsStore.RxToneMode.values()
        );
        toneModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxToneModeSpinner.setAdapter(toneModeAdapter);

        ArrayAdapter<FixedToneLearningWindowPreset> fixedToneWindowPresetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                FixedToneLearningWindowPreset.values()
        );
        fixedToneWindowPresetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.fixedToneLearningWindowPresetSpinner.setAdapter(fixedToneWindowPresetAdapter);

        AdapterView.OnItemSelectedListener cwDefaultsSpinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors && autoSaveEnabled) {
                    cwDefaultsDirty = true;
                    binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
                    saveCwDefaults();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        binding.rxInputModeSpinner.setOnItemSelectedListener(cwDefaultsSpinnerListener);
        binding.rxMicSourceModeSpinner.setOnItemSelectedListener(cwDefaultsSpinnerListener);
        binding.rxToneModeSpinner.setOnItemSelectedListener(cwDefaultsSpinnerListener);
        binding.fixedToneLearningWindowPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingEditors || !autoSaveEnabled) {
                    return;
                }
                FixedToneLearningWindowPreset selectedPreset = selectedSpinnerValue(
                        binding.fixedToneLearningWindowPresetSpinner,
                        FixedToneLearningWindowPreset.fromWindowHz(rxInputSettingsStore.fixedToneLearningWindowHz())
                );
                Integer windowHz = selectedPreset.windowHz();
                if (windowHz != null) {
                    syncingEditors = true;
                    binding.fixedToneLearningWindowEditText.setText(String.valueOf(windowHz));
                    syncingEditors = false;
                }
                cwDefaultsDirty = true;
                binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
                saveCwDefaults();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupRouteEditorControls() {
        ArrayAdapter<SerialKeyerTxOutput.KeyLine> keyLineAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        );
        keyLineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyLineSpinner.setAdapter(keyLineAdapter);
        binding.usbKeyLineSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        ));
        ((ArrayAdapter<?>) binding.usbKeyLineSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        ArrayAdapter<KeyingPolarity> keyingPolarityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                KeyingPolarity.values()
        );
        keyingPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyingPolaritySpinner.setAdapter(keyingPolarityAdapter);

        binding.serialCatKeyLineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors && autoSaveEnabled) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                    saveRouteSettings();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.serialCatKeyingPolaritySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors && autoSaveEnabled) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                    saveRouteSettings();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.usbKeyLineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors && autoSaveEnabled) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                    saveRouteSettings();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        usbDeviceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        usbDeviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.usbDeviceSpinner.setAdapter(usbDeviceAdapter);
        binding.usbDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingUsbDeviceSelection) {
                    return;
                }
                Object item = parent.getItemAtPosition(position);
                if (!(item instanceof UsbSerialDeviceOption)) {
                    return;
                }
                UsbSerialDeviceOption option = (UsbSerialDeviceOption) item;
                syncingEditors = true;
                binding.usbPreferredDeviceNameEditText.setText(option.isAuto()
                        ? ""
                        : valueOrEmpty(option.deviceName()));
                syncingEditors = false;
                if (autoSaveEnabled) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                    saveRouteSettings();
                }
                syncUsbRouteState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupRouteFallbackControls() {
        ArrayAdapter<RouteFallbackStore.Mode> fallbackModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RouteFallbackStore.Mode.values()
        );
        fallbackModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.routeFallbackModeSpinner.setAdapter(fallbackModeAdapter);
        binding.routeFallbackModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors && autoSaveEnabled) {
                    routeFallbackDirty = true;
                    binding.routeFallbackStatusText.setText(renderRouteFallbackEditorStatus());
                    saveRouteFallback();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.gridAutoDetectButton.setOnClickListener(view -> requestGridAutoDetect());
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
        binding.requestUsbKeyerPermissionButton.setOnClickListener(view -> requestUsbKeyerPermission());
    }

    private void setupInfoButtons() {
        binding.stationInfoButton.setOnClickListener(view -> showInfoDialog(
                "台站资料",
                "这里保存正式使用的台站信息。呼号、姓名、QTH、梅登海格网格，以及 <RIG> / <ANT> / <WX> 这类模板变量都从这里取值，不再需要单独点保存。"
                        + "梅登海格网格支持通过定位按钮读取系统最近位置并自动换算为 4 位网格。"
        ));
        binding.radioRouteInfoButton.setOnClickListener(view -> showInfoDialog(
                "电台链路",
                "这里查看当前正式路由，真正的电台选择和探测仍在 Rig Setup 完成。如果没有固定路由，是否回落到手机麦克风和手机音频，由“无电台兜底”决定。"
        ));
        binding.routeFallbackInfoButton.setOnClickListener(view -> showInfoDialog(
                "无电台兜底",
                "自动模式下，没有固定电台时，RX 可回落到手机麦克风，TX 可回落到手机音频。仅电台模式则完全禁止这种兜底。"
        ));
        binding.catKeyingInfoButton.setOnClickListener(view -> showInfoDialog(
                "CAT 与键控",
                "这里保存当前固定路由的 CAT、网络、USB 键控参数。Rig Setup 负责探测和诊断，这里负责日常使用时的正式参数。"
        ));
        binding.cwDefaultsInfoButton.setOnClickListener(view -> showInfoDialog(
                "CW 默认值",
                "这里配置默认 WPM、固定音调、人工锁频学习窗口和 RX 输入策略。"
                        + "普通情况建议保持“标准 ±50Hz”；样本很脏且中心频率已知时，可尝试“紧锁 ±30Hz”；"
                        + "如果担心收得过窄，也可以切到“宽松 ±70Hz”或直接自定义数值。"
        ));
        binding.txTemplatesInfoButton.setOnClickListener(view -> showInfoDialog(
                "发送模板",
                "这里定义 Operate 页的 4 个正式发送模板：呼叫、应答、设备信息、收尾。支持 <MYCALL>、<CALL>、<RST>、<RST_SENT>、<RST_RCVD>、<NAME>、<QTH>、<GRID>、<RIG>、<ANT>、<WX> 占位符，修改后会自动保存。\n\n其中：<RST> 与 <RST_SENT> 等价，表示我发给对方的信号报告，内容描述的是“对方信号到我这里的接收情况”；<RST_RCVD> 表示对方发给我的信号报告，内容描述的是“我的信号到对方那里的接收情况”。"
        ));
        binding.developerInfoButton.setOnClickListener(view -> showInfoDialog(
                "高级 / 开发",
                "只有开发者模式保留显式开关。打开后会显示 RX 调试、TX 调试台和链路诊断工具，正常操作时建议保持关闭。"
        ));
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }
        boolean granted = false;
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (!granted) {
            stationProfileStatusMessage = "没有定位权限，无法自动获取网格。";
            refreshUi();
            Toast.makeText(this, "Location permission is required to detect grid.", Toast.LENGTH_SHORT).show();
            return;
        }
        detectGridFromLastKnownLocation();
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        String catKeyingHint = renderCatKeyingHintText();
        syncStationProfileEditors(overview);
        syncCwDefaultsEditors();
        syncTxTemplateEditors();
        syncRouteEditors();
        syncRouteFallbackEditor();
        updateRoutePanelVisibility();
        binding.stationProfileText.setText(renderStationProfileText(overview));
        binding.operatingDefaultsText.setText(renderOperatingDefaultsText());
        binding.radioRouteText.setText(renderRadioRouteText());
        binding.routeFallbackText.setText(renderRouteFallbackText());
        binding.catKeyingText.setText(renderCatKeyingText());
        binding.catKeyingHintText.setText(catKeyingHint);
        binding.catKeyingHintText.setVisibility(catKeyingHint.isEmpty() ? View.GONE : View.VISIBLE);
        binding.routeSettingsScopeText.setText(renderRouteSettingsScopeText());
        binding.cwDefaultsText.setText(renderCwDefaultsText());
        binding.txTemplatesSummaryText.setText(renderTxTemplatesSummary());
        binding.stationProfileStatusText.setText(renderStationProfileEditorStatus());
        binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
        binding.txTemplatesStatusText.setText(renderTxTemplatesEditorStatus());
        binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
        binding.routeFallbackStatusText.setText(renderRouteFallbackEditorStatus());
        setVisibleWhenHasText(binding.stationProfileStatusText);
        setVisibleWhenHasText(binding.cwDefaultsStatusText);
        setVisibleWhenHasText(binding.txTemplatesStatusText);
        setVisibleWhenHasText(binding.routeSettingsStatusText);
        setVisibleWhenHasText(binding.routeFallbackStatusText);
        binding.developerModeStatusText.setText(renderDeveloperModeStatus(developerModeEnabled));
        binding.toggleDeveloperModeButton.setText(developerModeEnabled
                ? "关闭开发者模式"
                : "启用开发者模式");
        binding.developerToolsPanel.setVisibility(developerModeEnabled ? View.VISIBLE : View.GONE);
        binding.developerToolsNoteText.setText(developerModeEnabled
                ? "RX 观察 / TX 调试台 / 电台诊断"
                : "开发工具已隐藏");
        syncUsbRouteState();
    }

    private String renderStationProfileText(@Nullable AppOverviewSnapshot overview) {
        String stationCallsign = resolveStationCallsign(overview);
        String name = resolveStationName(overview);
        String qth = resolveStationQth(overview);
        return safeValue(stationCallsign)
                + "  |  "
                + safeValue(name)
                + "  |  "
                + safeValue(qth);
    }

    private String renderOperatingDefaultsText() {
        String grid = stationProfileStore.maidenheadGrid();
        String rig = stationProfileStore.rigDescription();
        String ant = stationProfileStore.antennaDescription();
        String wx = stationProfileStore.weatherDescription();
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return "网格 " + safeValue(grid)
                + "  |  RIG " + safeValue(rig)
                + "\nANT " + safeValue(ant)
                + "  |  WX " + safeValue(wx)
                + "\nCW "
                + settings.defaultWpm()
                + " WPM / "
                + settings.defaultToneFrequencyHz()
                + " Hz / "
                + renderFixedToneLearningWindowSummary(rxInputSettingsStore.fixedToneLearningWindowHz())
                + "\nRX "
                + rxInputSettingsStore.rxInputMode().displayName()
                + " / "
                + rxInputSettingsStore.rxToneMode().displayName()
                + " / "
                + rxInputSettingsStore.micSourceMode().displayName();
    }

    private String renderRadioRouteText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        RigControlAdapter adapter = resolveRouteStatusAdapter(profile, settings);
        String readiness = RigRouteStatusFormatter.describeRouteReadiness(
                profile,
                adapter,
                settings,
                profile != null && profile.hasCapability(RigCapability.KEY_LINE_CONTROL)
                        ? usbRouteStatusMessage
                        : null
        );
        return RigRouteStatusFormatter.describeSettingsRouteSummary(
                profile,
                settings,
                routeFallbackStore.usePhoneFallback(),
                readiness
        );
    }

    private String renderRouteFallbackText() {
        RouteFallbackStore.Mode mode = routeFallbackStore.mode();
        return "模式: " + mode.displayName()
                + "\nRX " + (routeFallbackStore.usePhoneFallback() ? "手机麦克风" : "关闭")
                + "  |  TX " + (routeFallbackStore.usePhoneFallback() ? "手机音频" : "关闭");
    }

    private String renderRouteFallbackSummary() {
        return RigRouteStatusFormatter.describeFallbackSummary(routeFallbackStore.usePhoneFallback());
    }

    private String renderCatKeyingText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return RigRouteStatusFormatter.describeSettingsCatKeyingSummary(profile, settings);
    }

    private String renderCatKeyingHintText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        RigControlAdapter adapter = resolveRouteStatusAdapter(profile, settings);
        return renderRouteActionHint(profile, adapter, settings);
    }

    private String renderCwDefaultsText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return settings.defaultWpm() + " WPM"
                + "  |  "
                + settings.defaultToneFrequencyHz() + " Hz"
                + "  |  "
                + renderFixedToneLearningWindowSummary(rxInputSettingsStore.fixedToneLearningWindowHz())
                + "\n"
                + rxInputSettingsStore.rxInputMode().displayName()
                + "  |  "
                + rxInputSettingsStore.rxToneMode().displayName()
                + "  |  "
                + rxInputSettingsStore.micSourceMode().displayName();
    }

    private String renderFixedToneLearningWindowSummary(int windowHz) {
        FixedToneLearningWindowPreset preset = FixedToneLearningWindowPreset.fromWindowHz(windowHz);
        if (preset == FixedToneLearningWindowPreset.CUSTOM) {
            return "锁频 ±" + windowHz + " Hz（自定义）";
        }
        return "锁频 ±" + windowHz + " Hz（" + preset.shortLabel() + "）";
    }

    private String renderTxTemplatesSummary() {
        return "宏定义说明:\n"
                + "<MYCALL>  我的呼号\n"
                + "<CALL>  对方呼号\n"
                + "<RST>  我发给对方的信号报告（等同 <RST_SENT>）\n"
                + "<RST_SENT>  我发给对方的信号报告，描述对方信号到我这里的情况\n"
                + "<RST_RCVD>  对方发给我的信号报告，描述我的信号到对方那里的情况\n"
                + "<NAME>  我的姓名\n"
                + "<QTH>  我的 QTH\n"
                + "<GRID>  我的梅登海格网格\n"
                + "<RIG>  我的设备信息\n"
                + "<ANT>  我的天线信息\n"
                + "<WX>  我的天气/环境说明";
    }

    private String renderDeveloperModeStatus(boolean enabled) {
        return enabled
                ? "当前已显示开发调试入口。"
                : "当前为正式使用视图，开发调试入口已隐藏。";
    }

    private String resolveStationCallsign(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.stationCallsign();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.stationCallsignUsed())) {
            return draft.stationCallsignUsed();
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.stationCallsign())) {
            return log.stationCallsign();
        }
        return null;
    }

    private String resolveStationName(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.operatorName();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.name())) {
            return log.name();
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.nameCandidate())) {
            return draft.nameCandidate();
        }
        return null;
    }

    private String resolveStationQth(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.qth();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.qth())) {
            return log.qth();
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.qthCandidate())) {
            return draft.qthCandidate();
        }
        return null;
    }

    private void syncStationProfileEditors(@Nullable AppOverviewSnapshot overview) {
        if (stationProfileDirty) {
            return;
        }
        syncingEditors = true;
        binding.stationCallsignEditText.setText(valueOrEmpty(resolveStationCallsign(overview)));
        binding.operatorNameEditText.setText(valueOrEmpty(resolveStationName(overview)));
        binding.qthEditText.setText(valueOrEmpty(resolveStationQth(overview)));
        binding.maidenheadGridEditText.setText(valueOrEmpty(stationProfileStore.maidenheadGrid()));
        binding.rigDescriptionEditText.setText(valueOrEmpty(stationProfileStore.rigDescription()));
        binding.antennaDescriptionEditText.setText(valueOrEmpty(stationProfileStore.antennaDescription()));
        binding.weatherDescriptionEditText.setText(valueOrEmpty(stationProfileStore.weatherDescription()));
        syncingEditors = false;
    }

    private void syncCwDefaultsEditors() {
        if (cwDefaultsDirty) {
            return;
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(rigSelectionStore.selectedProfile());
        syncingEditors = true;
        binding.defaultWpmEditText.setText(String.valueOf(settings.defaultWpm()));
        binding.defaultToneEditText.setText(String.valueOf(settings.defaultToneFrequencyHz()));
        int fixedToneLearningWindowHz = rxInputSettingsStore.fixedToneLearningWindowHz();
        binding.fixedToneLearningWindowEditText.setText(String.valueOf(fixedToneLearningWindowHz));
        selectSpinnerValue(
                binding.fixedToneLearningWindowPresetSpinner,
                FixedToneLearningWindowPreset.fromWindowHz(fixedToneLearningWindowHz)
        );
        selectSpinnerValue(binding.rxInputModeSpinner, rxInputSettingsStore.rxInputMode());
        selectSpinnerValue(binding.rxMicSourceModeSpinner, rxInputSettingsStore.micSourceMode());
        selectSpinnerValue(binding.rxToneModeSpinner, rxInputSettingsStore.rxToneMode());
        syncingEditors = false;
    }

    private void syncTxTemplateEditors() {
        if (txTemplatesDirty) {
            return;
        }
        syncingEditors = true;
        binding.templateCqEditText.setText(txTemplateStore.cqTemplate());
        binding.templateCqDxEditText.setText(txTemplateStore.cqDxTemplate());
        binding.templateQrzEditText.setText(txTemplateStore.qrzTemplate());
        binding.templateTu73EditText.setText(txTemplateStore.tu73Template());
        syncingEditors = false;
    }

    private void syncRouteEditors() {
        if (routeSettingsDirty) {
            syncUsbDeviceEditorSelection(rigSelectionStore.selectedProfile(), readCurrentRouteEditorSettings());
            return;
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        syncingEditors = true;
        binding.serialCatBaudRateEditText.setText(String.valueOf(settings.serialCatBaudRate()));
        binding.serialCatPortHintEditText.setText(valueOrEmpty(settings.serialCatPortHint()));
        binding.serialCatKeyingPortHintEditText.setText(valueOrEmpty(settings.serialCatKeyingPortHint()));
        binding.serialCatCivAddressEditText.setText(valueOrEmpty(settings.serialCatCivAddressHex()));
        binding.networkHostEditText.setText(valueOrEmpty(settings.networkHost()));
        binding.networkPortEditText.setText(String.valueOf(settings.networkPort()));
        binding.usbPreferredDeviceNameEditText.setText(valueOrEmpty(settings.usbPreferredDeviceName()));
        selectSpinnerValue(binding.serialCatKeyLineSpinner, settings.serialCatKeyLine());
        selectSpinnerValue(binding.serialCatKeyingPolaritySpinner, settings.serialCatKeyingPolarity());
        selectSpinnerValue(binding.usbKeyLineSpinner, settings.usbKeyLine());
        syncingEditors = false;
        syncUsbDeviceEditorSelection(profile, settings);
    }

    private void syncRouteFallbackEditor() {
        if (routeFallbackDirty) {
            return;
        }
        syncingEditors = true;
        selectSpinnerValue(binding.routeFallbackModeSpinner, routeFallbackStore.mode());
        syncingEditors = false;
    }

    private void saveStationProfile() {
        String stationCallsign = normalizedEditorValue(binding.stationCallsignEditText.getText());
        String operatorName = normalizedEditorValue(binding.operatorNameEditText.getText());
        String qth = normalizedEditorValue(binding.qthEditText.getText());
        String maidenheadGrid = normalizedEditorValue(binding.maidenheadGridEditText.getText());
        String rigDescription = normalizedEditorValue(binding.rigDescriptionEditText.getText());
        String antennaDescription = normalizedEditorValue(binding.antennaDescriptionEditText.getText());
        String weatherDescription = normalizedEditorValue(binding.weatherDescriptionEditText.getText());
        stationProfileStore.save(
                stationCallsign,
                operatorName,
                qth,
                maidenheadGrid,
                rigDescription,
                antennaDescription,
                weatherDescription
        );
        stationProfileDirty = false;
        stationProfileStatusMessage = isStationProfileEmpty(
                stationCallsign,
                operatorName,
                qth,
                maidenheadGrid,
                rigDescription,
                antennaDescription,
                weatherDescription
        )
                ? "台站资料已清空，正在回退到草稿/日志推断。"
                : "台站资料已自动保存。";
        refreshUi();
    }

    private void requestGridAutoDetect() {
        if (hasLocationPermission()) {
            detectGridFromLastKnownLocation();
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_LOCATION_PERMISSION
        );
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void detectGridFromLastKnownLocation() {
        LocationManager locationManager = getSystemService(LocationManager.class);
        if (locationManager == null) {
            stationProfileStatusMessage = "系统没有可用的定位服务。";
            refreshUi();
            Toast.makeText(this, "Location service is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        Location bestLocation = null;
        try {
            List<String> providers = locationManager.getProviders(true);
            if (providers == null || providers.isEmpty()) {
                providers = locationManager.getAllProviders();
            }
            if (providers != null) {
                for (String provider : providers) {
                    Location candidate = locationManager.getLastKnownLocation(provider);
                    bestLocation = pickBetterLocation(bestLocation, candidate);
                }
            }
        } catch (SecurityException exception) {
            stationProfileStatusMessage = "没有定位权限，无法自动获取网格。";
            refreshUi();
            Toast.makeText(this, "Location permission is required to detect grid.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bestLocation == null) {
            stationProfileStatusMessage = "没有可用的最近位置，暂时无法自动获取网格。";
            refreshUi();
            Toast.makeText(this, "No last known location is available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String grid = MaidenheadGridUtil.fromLatLon(bestLocation.getLatitude(), bestLocation.getLongitude());
        if (!MaidenheadGridUtil.isValid(grid)) {
            stationProfileStatusMessage = "最近位置无法换算成有效网格。";
            refreshUi();
            Toast.makeText(this, "Unable to convert location into a Maidenhead grid.", Toast.LENGTH_SHORT).show();
            return;
        }

        syncingEditors = true;
        binding.maidenheadGridEditText.setText(grid);
        syncingEditors = false;
        stationProfileDirty = true;
        saveStationProfile();
        stationProfileStatusMessage = "已根据最近位置自动填写网格 " + grid + "。";
        refreshUi();
    }

    @Nullable
    private Location pickBetterLocation(@Nullable Location currentBest, @Nullable Location candidate) {
        if (candidate == null) {
            return currentBest;
        }
        if (currentBest == null) {
            return candidate;
        }
        boolean candidateHasAccuracy = candidate.hasAccuracy();
        boolean currentHasAccuracy = currentBest.hasAccuracy();
        if (candidateHasAccuracy && !currentHasAccuracy) {
            return candidate;
        }
        if (!candidateHasAccuracy && currentHasAccuracy) {
            return currentBest;
        }
        if (candidateHasAccuracy && currentHasAccuracy) {
            float accuracyDelta = candidate.getAccuracy() - currentBest.getAccuracy();
            if (accuracyDelta < 0f) {
                return candidate;
            }
            if (accuracyDelta > 0f) {
                return currentBest;
            }
        }
        return candidate.getTime() >= currentBest.getTime() ? candidate : currentBest;
    }

    private void saveCwDefaults() {
        binding.defaultWpmEditText.setError(null);
        binding.defaultToneEditText.setError(null);
        binding.fixedToneLearningWindowEditText.setError(null);

        Integer defaultWpm = parsePositiveInt(
                binding.defaultWpmEditText.getText(),
                binding.defaultWpmEditText,
                "WPM 必须是整数。",
                5,
                80,
                "WPM 建议保持在 5 到 80 之间。"
        );
        if (defaultWpm == null) {
            return;
        }

        Integer defaultTone = parsePositiveInt(
                binding.defaultToneEditText.getText(),
                binding.defaultToneEditText,
                "音调必须是整数。",
                200,
                2000,
                "音调建议保持在 200 到 2000 Hz 之间。"
        );
        if (defaultTone == null) {
            return;
        }

        Integer fixedToneLearningWindowHz = parsePositiveInt(
                binding.fixedToneLearningWindowEditText.getText(),
                binding.fixedToneLearningWindowEditText,
                "锁频学习窗口必须是整数。",
                org.bi9clt.cwcn.core.signal.CwSignalProcessor.MIN_FIXED_TONE_LEARNING_WINDOW_HZ,
                org.bi9clt.cwcn.core.signal.CwSignalProcessor.MAX_FIXED_TONE_LEARNING_WINDOW_HZ,
                "锁频学习窗口建议保持在 "
                        + org.bi9clt.cwcn.core.signal.CwSignalProcessor.MIN_FIXED_TONE_LEARNING_WINDOW_HZ
                        + " 到 "
                        + org.bi9clt.cwcn.core.signal.CwSignalProcessor.MAX_FIXED_TONE_LEARNING_WINDOW_HZ
                        + " Hz 之间。"
        );
        if (fixedToneLearningWindowHz == null) {
            return;
        }

        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        RigProfileSettings updated = new RigProfileSettings(
                defaultWpm,
                defaultTone,
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
        rxInputSettingsStore.setRxInputMode(selectedSpinnerValue(
                binding.rxInputModeSpinner,
                rxInputSettingsStore.rxInputMode()
        ));
        rxInputSettingsStore.setMicSourceMode(selectedSpinnerValue(
                binding.rxMicSourceModeSpinner,
                rxInputSettingsStore.micSourceMode()
        ));
        rxInputSettingsStore.setRxToneMode(selectedSpinnerValue(
                binding.rxToneModeSpinner,
                rxInputSettingsStore.rxToneMode()
        ));
        rxInputSettingsStore.setFixedToneLearningWindowHz(fixedToneLearningWindowHz);
        cwDefaultsDirty = false;
        cwDefaultsStatusMessage = "CW / RX 默认值已自动保存到 " + resolveCwDefaultsScope(profile) + "。";
        refreshUi();
    }

    private void saveTxTemplates() {
        txTemplateStore.save(
                normalizedEditorValue(binding.templateCqEditText.getText()),
                normalizedEditorValue(binding.templateCqDxEditText.getText()),
                normalizedEditorValue(binding.templateQrzEditText.getText()),
                normalizedEditorValue(binding.templateTu73EditText.getText())
        );
        txTemplatesDirty = false;
        txTemplatesStatusMessage = "发送模板已自动保存。";
        refreshUi();
    }

    private void saveRouteSettings() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            routeSettingsStatusMessage = "当前还没有固定正式路由，请先到 Rig Setup 选择。";
            refreshUi();
            return;
        }
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        Integer serialBaud = parsePositiveInt(
                binding.serialCatBaudRateEditText.getText(),
                binding.serialCatBaudRateEditText,
                "波特率必须是整数。",
                300,
                921600,
                "波特率建议保持在 300 到 921600 之间。"
        );
        if (serialBaud == null) {
            return;
        }
        Integer networkPort = parsePositiveInt(
                binding.networkPortEditText.getText(),
                binding.networkPortEditText,
                "端口必须是整数。",
                1,
                65535,
                "端口建议保持在 1 到 65535 之间。"
        );
        if (networkPort == null) {
            return;
        }
        String serialPortHint = normalizedEditorValue(binding.serialCatPortHintEditText.getText());
        String serialKeyingPortHint = normalizedEditorValue(binding.serialCatKeyingPortHintEditText.getText());
        String networkHost = normalizedEditorValue(binding.networkHostEditText.getText());
        String preferredUsbDevice = normalizedEditorValue(binding.usbPreferredDeviceNameEditText.getText());
        String civAddress = null;
        if (existing.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                || hasMeaningfulText(binding.serialCatCivAddressEditText.getText() == null
                ? null
                : binding.serialCatCivAddressEditText.getText().toString())) {
            civAddress = normalizeHexByte(binding.serialCatCivAddressEditText);
            if (civAddress == null && hasMeaningfulText(binding.serialCatCivAddressEditText.getText() == null
                    ? null
                    : binding.serialCatCivAddressEditText.getText().toString())) {
                return;
            }
        }
        RigProfileSettings updated = new RigProfileSettings(
                existing.defaultWpm(),
                existing.defaultToneFrequencyHz(),
                selectedSpinnerValue(binding.usbKeyLineSpinner, existing.usbKeyLine()),
                preferredUsbDevice,
                existing.serialCatProtocolFamily(),
                serialBaud,
                serialPortHint,
                selectedSpinnerValue(binding.serialCatKeyLineSpinner, existing.serialCatKeyLine()),
                serialKeyingPortHint,
                selectedSpinnerValue(binding.serialCatKeyingPolaritySpinner, existing.serialCatKeyingPolarity()),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                civAddress,
                existing.networkCatProtocolFamily(),
                networkHost,
                networkPort,
                existing.bluetoothDeviceHint()
        );
        rigSelectionStore.saveSettings(profile, updated);
        routeSettingsDirty = false;
        routeSettingsStatusMessage = "链路参数已自动保存到 " + profile.displayName() + "。";
        refreshUi();
    }

    private void saveRouteFallback() {
        RouteFallbackStore.Mode selectedMode = selectedSpinnerValue(
                binding.routeFallbackModeSpinner,
                routeFallbackStore.mode()
        );
        routeFallbackStore.setMode(selectedMode);
        routeFallbackDirty = false;
        routeFallbackStatusMessage = "无电台兜底已自动保存为：" + routeFallbackStore.mode().displayName() + "。";
        refreshUi();
    }

    private String renderStationProfileEditorStatus() {
        if (stationProfileDirty) {
            return "编辑中，离开输入框后自动保存";
        }
        return stationProfileStatusMessage;
    }

    private String renderCwDefaultsEditorStatus() {
        if (cwDefaultsDirty) {
            return "编辑中，离开输入框后自动保存";
        }
        return cwDefaultsStatusMessage;
    }

    private String renderTxTemplatesEditorStatus() {
        if (txTemplatesDirty) {
            return "编辑中，离开输入框后自动保存";
        }
        return txTemplatesStatusMessage;
    }

    private String renderRouteSettingsScopeText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "当前未固定正式路由";
        }
        return "当前编辑: " + profile.displayName()
                + "\n传输: " + profile.transportKind().name();
    }

    private String renderRouteSettingsEditorStatus() {
        if (routeSettingsDirty) {
            return "编辑中，离开输入框后自动保存";
        }
        return routeSettingsStatusMessage;
    }

    private String renderRouteFallbackEditorStatus() {
        if (routeFallbackDirty) {
            return "正在切换，自动保存中";
        }
        return routeFallbackStatusMessage;
    }

    private String renderStationProfileSource(@Nullable AppOverviewSnapshot overview) {
        boolean hasSavedCallsign = hasMeaningfulText(stationProfileStore.stationCallsign());
        boolean hasSavedName = hasMeaningfulText(stationProfileStore.operatorName());
        boolean hasSavedQth = hasMeaningfulText(stationProfileStore.qth());
        boolean hasSavedGrid = hasMeaningfulText(stationProfileStore.maidenheadGrid());
        boolean hasSavedRig = hasMeaningfulText(stationProfileStore.rigDescription());
        boolean hasSavedAnt = hasMeaningfulText(stationProfileStore.antennaDescription());
        boolean hasSavedWx = hasMeaningfulText(stationProfileStore.weatherDescription());
        if (hasSavedCallsign && hasSavedName && hasSavedQth && hasSavedGrid) {
            return "完整台站资料";
        }
        if (hasSavedCallsign || hasSavedName || hasSavedQth || hasSavedGrid || hasSavedRig || hasSavedAnt || hasSavedWx) {
            return "部分台站资料，缺项继续回退";
        }
        return hasInferredStationProfile(overview)
                ? "来自活动草稿 / 最近日志的推断"
                : "还没有保存正式台站资料";
    }

    private boolean hasInferredStationProfile(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        return (draft != null && (hasMeaningfulText(draft.stationCallsignUsed())
                || hasMeaningfulText(draft.nameCandidate())
                || hasMeaningfulText(draft.qthCandidate())))
                || (log != null && (hasMeaningfulText(log.stationCallsign())
                || hasMeaningfulText(log.name())
                || hasMeaningfulText(log.qth())));
    }

    private String resolveCwDefaultsScope(@Nullable RigProfile profile) {
        return profile == null ? "兜底链路默认值" : profile.displayName();
    }

    private void updateRoutePanelVisibility() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        boolean hasProfile = profile != null;
        boolean serialVisible = hasProfile && profile.hasCapability(RigCapability.SERIAL_CAT);
        binding.routeSettingsScopeText.setVisibility(View.VISIBLE);
        binding.serialRouteSettingsPanel.setVisibility(serialVisible ? View.VISIBLE : View.GONE);
        binding.serialCatCivAddressEditText.setVisibility(serialVisible && rigSelectionStore.loadSettings(profile).serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                ? View.VISIBLE
                : View.GONE);
        binding.networkRouteSettingsPanel.setVisibility(hasProfile && profile.hasCapability(RigCapability.NETWORK_CAT) ? View.VISIBLE : View.GONE);
        binding.usbRouteSettingsPanel.setVisibility(hasProfile && profile.hasCapability(RigCapability.KEY_LINE_CONTROL) ? View.VISIBLE : View.GONE);
    }

    private void syncUsbRouteState() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = readCurrentRouteEditorSettings();
        boolean usbVisible = profile != null && profile.hasCapability(RigCapability.KEY_LINE_CONTROL);
        binding.usbDeviceSpinner.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.requestUsbKeyerPermissionButton.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.usbRouteStatusText.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        if (!usbVisible) {
            return;
        }
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            binding.requestUsbKeyerPermissionButton.setEnabled(false);
            binding.usbRouteStatusText.setText("当前固定路由还没有接上 USB 键控适配器。");
            return;
        }
        List<UsbSerialDeviceOption> devices = refreshUsbDeviceSelection(adapter, settings.usbPreferredDeviceName());
        boolean hasTargetDevice = adapter.hasTargetDevice();
        boolean ready = adapter.isReady();
        binding.requestUsbKeyerPermissionButton.setEnabled(hasTargetDevice && !ready);
        binding.requestUsbKeyerPermissionButton.setText(ready
                ? "USB 权限已就绪"
                : hasTargetDevice
                ? "申请 USB 权限"
                : "未检测到 USB 设备");
        if (usbRouteStatusMessage != null && !usbRouteStatusMessage.trim().isEmpty()) {
            binding.usbRouteStatusText.setText(usbRouteStatusMessage);
            if (!hasTargetDevice || ready) {
                usbRouteStatusMessage = "";
            }
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("路由: ").append(adapter.displayName())
                .append("\n状态: ").append(RigRouteStatusFormatter.describeUsbKeyerReadiness(adapter, null))
                .append("\n键控线: ").append(settings.usbKeyLine())
                .append("\n设备: ").append(hasRealUsbDeviceOption(devices)
                        ? valueOrEmpty(settings.usbPreferredDeviceName()).isEmpty()
                        ? "自动 / 首个可用设备"
                        : settings.usbPreferredDeviceName()
                        : "没有可用候选设备");
        binding.usbRouteStatusText.setText(builder.toString());
    }

    private List<UsbSerialDeviceOption> refreshUsbDeviceSelection(
            UsbSerialKeyerRigControlAdapter adapter,
            @Nullable String preferredDeviceName
    ) {
        List<UsbSerialDeviceOption> devices = new ArrayList<>();
        devices.add(UsbSerialDeviceOption.autoOption());
        devices.addAll(adapter.availableDevices());
        syncingUsbDeviceSelection = true;
        usbDeviceAdapter.clear();
        usbDeviceAdapter.addAll(devices);
        usbDeviceAdapter.notifyDataSetChanged();
        binding.usbDeviceSpinner.setSelection(findUsbDeviceSelectionIndex(devices, preferredDeviceName), false);
        syncingUsbDeviceSelection = false;
        return devices;
    }

    private void syncUsbDeviceEditorSelection(@Nullable RigProfile profile, RigProfileSettings settings) {
        if (usbDeviceAdapter == null || profile == null || !profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return;
        }
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            return;
        }
        refreshUsbDeviceSelection(adapter, settings.usbPreferredDeviceName());
    }

    private int findUsbDeviceSelectionIndex(
            List<UsbSerialDeviceOption> devices,
            @Nullable String preferredDeviceName
    ) {
        if (preferredDeviceName == null || preferredDeviceName.trim().isEmpty()) {
            return 0;
        }
        for (int index = 0; index < devices.size(); index++) {
            UsbSerialDeviceOption device = devices.get(index);
            if (!device.isAuto() && preferredDeviceName.equals(device.deviceName())) {
                return index;
            }
        }
        return 0;
    }

    private boolean hasRealUsbDeviceOption(List<UsbSerialDeviceOption> devices) {
        for (UsbSerialDeviceOption device : devices) {
            if (!device.isAuto()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private RigControlAdapter resolveRouteStatusAdapter(
            @Nullable RigProfile profile,
            RigProfileSettings settings
    ) {
        if (profile == null) {
            return null;
        }
        for (RigControlAdapter candidate : RigRegistry.defaultAdapters(this)) {
            if (!profile.adapterId().equals(candidate.id())) {
                continue;
            }
            if (candidate.supportsConfigurableTextToCwProfile()) {
                candidate.configureTextToCwProfile(
                        settings.defaultWpm(),
                        settings.defaultToneFrequencyHz()
                );
            }
            if (candidate instanceof UsbSerialKeyerRigControlAdapter) {
                UsbSerialKeyerRigControlAdapter adapter = (UsbSerialKeyerRigControlAdapter) candidate;
                adapter.setKeyLine(settings.usbKeyLine());
                adapter.selectDevice(settings.usbPreferredDeviceName());
            }
            return candidate;
        }
        return null;
    }

    private String renderRouteActionHint(
            @Nullable RigProfile profile,
            @Nullable RigControlAdapter adapter,
            RigProfileSettings settings
    ) {
        if (profile == null) {
            return "下一步：先进入 Rig Setup 固定一个正式路由；如果暂时没有电台，可保留手机兜底。";
        }
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            if (!(adapter instanceof UsbSerialKeyerRigControlAdapter)) {
                return "下一步：当前 USB 键控适配器尚未挂接，请先回 Rig Setup 检查固定路由。";
            }
            UsbSerialKeyerRigControlAdapter usbAdapter = (UsbSerialKeyerRigControlAdapter) adapter;
            if (usbAdapter.isReady()) {
                return "下一步：USB 键控已经就绪，可以回 Operate 做真实发射联调。";
            }
            if (!usbAdapter.hasTargetDevice()) {
                return "下一步：插入 OTG / USB 线控设备，然后回到这里申请权限。";
            }
            return "下一步：先点“申请 USB 权限”，确认 Android 授权通过。";
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            if (!hasMeaningfulText(settings.serialCatPortHint())
                    && !hasMeaningfulText(settings.serialCatKeyingPortHint())) {
                return "下一步：补齐串口 CAT 端口，或填写独立键控端口，再保存设置。";
            }
            if (adapter != null && adapter.isReady()) {
                return "下一步：串口 CAT 基础链路已具备，可开始 PTT / 键控联调。";
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                    && !hasMeaningfulText(settings.serialCatCivAddressHex())) {
                return "下一步：补充 CI-V 地址，再继续做串口 CAT 联调。";
            }
            return "下一步：检查串口权限、波特率和端口提示是否与实际电台一致。";
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            if (!hasMeaningfulText(settings.networkHost())) {
                return "下一步：填写 rigctld 主机和端口，再保存设置。";
            }
            if (adapter != null && adapter.isReady()) {
                return "下一步：网络 CAT 已具备基础配置，可开始验证 rigctld 连通和发射控制。";
            }
            return "下一步：确认 rigctld 已在目标主机启动，并且手机当前网络可达。";
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            return "下一步：连接音频线、校准 VOX 门限，然后回 Operate 做短报文发射验证。";
        }
        return "下一步：先完成当前路由的基础配置，再回 Operate 验证正式链路。";
    }

    @Nullable
    private UsbSerialKeyerRigControlAdapter resolveUsbKeyerAdapter(
            @Nullable RigProfile profile,
            RigProfileSettings settings
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return null;
        }
        for (RigControlAdapter candidate : RigRegistry.defaultAdapters(this)) {
            if (!profile.adapterId().equals(candidate.id())) {
                continue;
            }
            if (!(candidate instanceof UsbSerialKeyerRigControlAdapter)) {
                continue;
            }
            UsbSerialKeyerRigControlAdapter adapter = (UsbSerialKeyerRigControlAdapter) candidate;
            adapter.setKeyLine(settings.usbKeyLine());
            adapter.selectDevice(settings.usbPreferredDeviceName());
            return adapter;
        }
        return null;
    }

    private RigProfileSettings readCurrentRouteEditorSettings() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        return new RigProfileSettings(
                existing.defaultWpm(),
                existing.defaultToneFrequencyHz(),
                selectedSpinnerValue(binding.usbKeyLineSpinner, existing.usbKeyLine()),
                normalizedEditorValue(binding.usbPreferredDeviceNameEditText.getText()),
                existing.serialCatProtocolFamily(),
                parsePositiveIntOrFallback(binding.serialCatBaudRateEditText.getText(), existing.serialCatBaudRate()),
                normalizedEditorValue(binding.serialCatPortHintEditText.getText()),
                selectedSpinnerValue(binding.serialCatKeyLineSpinner, existing.serialCatKeyLine()),
                normalizedEditorValue(binding.serialCatKeyingPortHintEditText.getText()),
                selectedSpinnerValue(binding.serialCatKeyingPolaritySpinner, existing.serialCatKeyingPolarity()),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                normalizedEditorValue(binding.serialCatCivAddressEditText.getText()),
                existing.networkCatProtocolFamily(),
                normalizedEditorValue(binding.networkHostEditText.getText()),
                parsePositiveIntOrFallback(binding.networkPortEditText.getText(), existing.networkPort()),
                existing.bluetoothDeviceHint()
        );
    }

    private int parsePositiveIntOrFallback(Editable editable, int fallback) {
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void requestUsbKeyerPermission() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(
                profile,
                readCurrentRouteEditorSettings()
        );
        if (adapter == null) {
            return;
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
            usbRouteStatusMessage = requested
                    ? "USB 权限请求已发出，请在 Android 弹窗中允许后再返回这里。"
                    : "当前无法发起 USB 权限请求。状态：" + adapter.describeAvailability();
        } catch (RuntimeException exception) {
            usbRouteStatusMessage = "USB 权限请求失败：" + safeThrowableMessage(exception);
        }
        refreshUi();
    }

    private void registerUsbPermissionReceiver() {
        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (!ACTION_USB_KEYER_PERMISSION.equals(intent.getAction())) {
                    return;
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                usbRouteStatusMessage = granted
                        ? "Android 已授予 USB 权限。"
                        : "Android 拒绝了 USB 权限。";
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

    private String safeThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "unknown failure" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    @Nullable
    private <T> T selectedSpinnerValue(Spinner spinner, T fallback) {
        Object selectedItem = spinner.getSelectedItem();
        if (selectedItem == null) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            T value = (T) selectedItem;
            return value;
        } catch (ClassCastException ignored) {
            return fallback;
        }
    }

    private void attachAutoSaveOnFocusLoss(EditText editText, Runnable saveAction) {
        if (editText == null || saveAction == null) {
            return;
        }
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && !syncingEditors && autoSaveEnabled) {
                saveAction.run();
            }
        });
    }

    private void commitPendingEdits() {
        if (stationProfileDirty) {
            saveStationProfile();
        }
        if (cwDefaultsDirty) {
            saveCwDefaults();
        }
        if (txTemplatesDirty) {
            saveTxTemplates();
        }
        if (routeSettingsDirty) {
            saveRouteSettings();
        }
        if (routeFallbackDirty) {
            saveRouteFallback();
        }
    }

    private boolean isStationProfileEmpty(
            @Nullable String stationCallsign,
            @Nullable String operatorName,
            @Nullable String qth,
            @Nullable String maidenheadGrid,
            @Nullable String rigDescription,
            @Nullable String antennaDescription,
            @Nullable String weatherDescription
    ) {
        return !hasMeaningfulText(stationCallsign)
                && !hasMeaningfulText(operatorName)
                && !hasMeaningfulText(qth)
                && !hasMeaningfulText(maidenheadGrid)
                && !hasMeaningfulText(rigDescription)
                && !hasMeaningfulText(antennaDescription)
                && !hasMeaningfulText(weatherDescription);
    }

    private void selectSpinnerValue(Spinner spinner, Object value) {
        if (spinner == null || spinner.getAdapter() == null || value == null) {
            return;
        }
        for (int index = 0; index < spinner.getAdapter().getCount(); index++) {
            Object candidate = spinner.getAdapter().getItem(index);
            if (value.equals(candidate)) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private String normalizeHexByte(android.widget.EditText editText) {
        Editable editable = editText == null ? null : editText.getText();
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            if (editText != null) {
                editText.setError(null);
            }
            return null;
        }
        String normalized = raw.toUpperCase();
        if (normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 1) {
            normalized = "0" + normalized;
        }
        if (normalized.length() != 2) {
            if (editText != null) {
                editText.setError("CI-V address must be 1-2 hex digits.");
            }
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                if (editText != null) {
                    editText.setError("CI-V address must be 1-2 hex digits.");
                }
                return null;
            }
        }
        if (editText != null) {
            editText.setError(null);
        }
        return normalized;
    }

    @Nullable
    private Integer parsePositiveInt(
            Editable editable,
            android.widget.EditText editText,
            String parseError,
            int minValue,
            int maxValue,
            String rangeError
    ) {
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            editText.setError(parseError);
            Toast.makeText(this, parseError, Toast.LENGTH_SHORT).show();
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            editText.setError(parseError);
            Toast.makeText(this, parseError, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (value < minValue || value > maxValue) {
            editText.setError(rangeError);
            Toast.makeText(this, rangeError, Toast.LENGTH_SHORT).show();
            return null;
        }
        return value;
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

    private boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeValue(String value) {
        return hasMeaningfulText(value) ? value.trim() : "-";
    }

    private void setVisibleWhenHasText(TextView view) {
        if (view == null) {
            return;
        }
        CharSequence text = view.getText();
        boolean visible = text != null && text.toString().trim().length() > 0;
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
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
