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
import android.view.ViewGroup;
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

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.AppLanguageStore;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.app.OperateRouteModeStore;
import org.bi9clt.cwcn.core.app.RouteFallbackStore;
import org.bi9clt.cwcn.core.app.RxInputSettingsStore;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.app.TxTemplateEntry;
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
import org.bi9clt.cwcn.core.rig.RigProfileFamilies;
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
import org.bi9clt.cwcn.ui.rig.RigUiLabels;
import org.bi9clt.cwcn.ui.tx.TxTemplateListActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsActivity extends AppCompatActivity {
    private static final String ACTION_USB_KEYER_PERMISSION =
            "org.bi9clt.cwcn.action.SETTINGS_USB_KEYER_PERMISSION";
    private static final int REQUEST_LOCATION_PERMISSION = 2001;

    private enum FixedToneLearningWindowPreset {
        TIGHT(
                R.string.settings_fixed_tone_preset_tight_display,
                R.string.settings_fixed_tone_preset_tight_short,
                30
        ),
        STANDARD(
                R.string.settings_fixed_tone_preset_standard_display,
                R.string.settings_fixed_tone_preset_standard_short,
                50
        ),
        WIDE(
                R.string.settings_fixed_tone_preset_wide_display,
                R.string.settings_fixed_tone_preset_wide_short,
                70
        ),
        CUSTOM(
                R.string.settings_fixed_tone_preset_custom_display,
                R.string.settings_fixed_tone_preset_custom_short,
                null
        );

        private final int displayNameResId;
        private final int shortLabelResId;
        @Nullable
        private final Integer windowHz;

        FixedToneLearningWindowPreset(int displayNameResId, int shortLabelResId, @Nullable Integer windowHz) {
            this.displayNameResId = displayNameResId;
            this.shortLabelResId = shortLabelResId;
            this.windowHz = windowHz;
        }

        @Nullable
        Integer windowHz() {
            return windowHz;
        }

        int displayNameResId() {
            return displayNameResId;
        }

        int shortLabelResId() {
            return shortLabelResId;
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
            return name();
        }
    }

    private ActivitySettingsBinding binding;
    private AppLanguageStore appLanguageStore;
    private DeveloperModeStore developerModeStore;
    private StationProfileStore stationProfileStore;
    private RigSelectionStore rigSelectionStore;
    private OperateRouteModeStore operateRouteModeStore;
    private RouteFallbackStore routeFallbackStore;
    private RxInputSettingsStore rxInputSettingsStore;
    private TxTemplateStore txTemplateStore;
    private LocalLogRepository localLogRepository;
    private boolean syncingEditors;
    private boolean autoSaveEnabled;
    private boolean stationProfileDirty;
    private boolean cwDefaultsDirty;
    private boolean routeSettingsDirty;
    private boolean routeFallbackDirty;
    private String stationProfileStatusMessage = "";
    private String cwDefaultsStatusMessage = "";
    private String routeSettingsStatusMessage = "";
    private String routeFallbackStatusMessage = "";
    private BroadcastReceiver usbPermissionReceiver;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;
    private boolean syncingUsbDeviceSelection;
    private String usbRouteStatusMessage = "";
    private boolean syncingLanguageSelection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        appLanguageStore = new AppLanguageStore(this);
        developerModeStore = new DeveloperModeStore(this);
        stationProfileStore = new StationProfileStore(this);
        rigSelectionStore = new RigSelectionStore(this);
        operateRouteModeStore = new OperateRouteModeStore(this);
        routeFallbackStore = new RouteFallbackStore(this);
        rxInputSettingsStore = new RxInputSettingsStore(this);
        txTemplateStore = new TxTemplateStore(this);
        localLogRepository = new LocalLogRepository(this);
        registerUsbPermissionReceiver();
        binding.versionText.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        setupEditorWatchers();
        setupRouteEditorControls();
        setupRouteFallbackControls();
        setupRxInputControls();
        setupLanguageControls();
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
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindRxInputModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindRxInputModeLabel(view, getItem(position));
                return view;
            }
        };
        inputModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxInputModeSpinner.setAdapter(inputModeAdapter);

        ArrayAdapter<RxInputSettingsStore.MicSourceMode> micSourceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RxInputSettingsStore.MicSourceMode.values()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindMicSourceModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindMicSourceModeLabel(view, getItem(position));
                return view;
            }
        };
        micSourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxMicSourceModeSpinner.setAdapter(micSourceAdapter);

        ArrayAdapter<RxInputSettingsStore.RxToneMode> toneModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RxInputSettingsStore.RxToneMode.values()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindRxToneModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindRxToneModeLabel(view, getItem(position));
                return view;
            }
        };
        toneModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.rxToneModeSpinner.setAdapter(toneModeAdapter);

        ArrayAdapter<FixedToneLearningWindowPreset> fixedToneWindowPresetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                FixedToneLearningWindowPreset.values()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindFixedTonePresetLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindFixedTonePresetLabel(view, getItem(position));
                return view;
            }
        };
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

    private void setupLanguageControls() {
        ArrayAdapter<AppLanguageStore.LanguageMode> languageModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                AppLanguageStore.LanguageMode.values()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindLanguageModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindLanguageModeLabel(view, getItem(position));
                return view;
            }
        };
        languageModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.languageModeSpinner.setAdapter(languageModeAdapter);
        binding.languageModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingLanguageSelection) {
                    return;
                }
                AppLanguageStore.LanguageMode selectedMode = selectedSpinnerValue(
                        binding.languageModeSpinner,
                        AppLanguageStore.LanguageMode.FOLLOW_SYSTEM
                );
                if (selectedMode == appLanguageStore.languageMode()) {
                    return;
                }
                appLanguageStore.setLanguageMode(selectedMode);
                boolean changed = appLanguageStore.applyLanguageMode(selectedMode);
                if (changed) {
                    recreate();
                } else {
                    refreshUi();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void bindLanguageModeLabel(View view, @Nullable AppLanguageStore.LanguageMode mode) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(renderLanguageModeLabel(mode));
    }

    private void bindFixedTonePresetLabel(View view, @Nullable FixedToneLearningWindowPreset preset) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(preset == null ? "" : getString(preset.displayNameResId()));
    }

    private void bindRxInputModeLabel(View view, @Nullable RxInputSettingsStore.RxInputMode mode) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(mode == null ? "" : getString(mode.displayNameResId()));
    }

    private void bindMicSourceModeLabel(View view, @Nullable RxInputSettingsStore.MicSourceMode mode) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(mode == null ? "" : getString(mode.displayNameResId()));
    }

    private void bindRxToneModeLabel(View view, @Nullable RxInputSettingsStore.RxToneMode mode) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(mode == null ? "" : getString(mode.displayNameResId()));
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
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindRouteFallbackModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindRouteFallbackModeLabel(view, getItem(position));
                return view;
            }
        };
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

    private void bindRouteFallbackModeLabel(View view, @Nullable RouteFallbackStore.Mode mode) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(mode == null ? "" : getString(mode.displayNameResId()));
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
        binding.openTxTemplateLibraryButton.setOnClickListener(view ->
                startActivity(new Intent(this, TxTemplateListActivity.class)));
    }

    private void setupInfoButtons() {
        binding.stationInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_station_title),
                getString(R.string.settings_info_station_message)
        ));
        binding.radioRouteInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_radio_route_title),
                getString(R.string.settings_info_radio_route_message)
        ));
        binding.routeFallbackInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_route_fallback_title),
                getString(R.string.settings_info_route_fallback_message)
        ));
        binding.catKeyingInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_cat_keying_title),
                getString(R.string.settings_info_cat_keying_message)
        ));
        binding.cwDefaultsInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_cw_defaults_title),
                getString(R.string.settings_info_cw_defaults_message)
        ));
        binding.txTemplatesInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_tx_templates_title),
                getString(R.string.settings_info_tx_templates_message)
        ));
        binding.developerInfoButton.setOnClickListener(view -> showInfoDialog(
                getString(R.string.settings_info_developer_title),
                getString(R.string.settings_info_developer_message)
        ));
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.common_acknowledged, null)
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
            stationProfileStatusMessage = getString(R.string.settings_status_location_permission_denied);
            refreshUi();
            Toast.makeText(this, R.string.settings_toast_location_permission_required, Toast.LENGTH_SHORT).show();
            return;
        }
        detectGridFromLastKnownLocation();
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        boolean showRouteEditorControls = shouldShowRouteEditorControls();
        String catKeyingHint = renderCatKeyingHintText();
        syncLanguageEditor();
        syncStationProfileEditors(overview);
        syncCwDefaultsEditors();
        syncRouteEditors();
        syncRouteFallbackEditor();
        updateRoutePanelVisibility();
        syncRouteEditorHints();
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
        binding.languageSummaryText.setText(getString(R.string.settings_language_summary));
        binding.languageStatusText.setText(renderLanguageStatusText());
        setVisibleWhenHasText(binding.stationProfileStatusText);
        setVisibleWhenHasText(binding.cwDefaultsStatusText);
        setVisibleWhenHasText(binding.txTemplatesStatusText);
        setVisibleWhenHasText(binding.languageStatusText);
        if (showRouteEditorControls) {
            setVisibleWhenHasText(binding.routeSettingsStatusText);
        } else {
            binding.routeSettingsStatusText.setVisibility(View.GONE);
        }
        setVisibleWhenHasText(binding.routeFallbackStatusText);
        binding.developerModeStatusText.setText(renderDeveloperModeStatus(developerModeEnabled));
        binding.toggleDeveloperModeButton.setText(developerModeEnabled
                ? R.string.settings_toggle_developer_hide
                : R.string.settings_toggle_developer_show);
        binding.developerToolsPanel.setVisibility(developerModeEnabled ? View.VISIBLE : View.GONE);
        binding.developerToolsNoteText.setText(developerModeEnabled
                ? R.string.settings_developer_tools_note_visible
                : R.string.settings_developer_tools_note_hidden);
        syncUsbRouteState();
    }

    private void syncLanguageEditor() {
        syncingLanguageSelection = true;
        selectSpinnerValue(binding.languageModeSpinner, appLanguageStore.languageMode());
        syncingLanguageSelection = false;
    }

    private String renderLanguageStatusText() {
        AppLanguageStore.LanguageMode mode = appLanguageStore.languageMode();
        if (mode == AppLanguageStore.LanguageMode.FOLLOW_SYSTEM) {
            return getString(R.string.settings_language_status_follow_system);
        }
        return getString(R.string.settings_language_status_selected, renderLanguageModeLabel(mode));
    }

    private String renderLanguageModeLabel(@Nullable AppLanguageStore.LanguageMode mode) {
        if (mode == null) {
            return "";
        }
        switch (mode) {
            case ENGLISH:
                return getString(R.string.settings_language_english);
            case SIMPLIFIED_CHINESE:
                return getString(R.string.settings_language_simplified_chinese);
            case FOLLOW_SYSTEM:
            default:
                return getString(R.string.settings_language_follow_system);
        }
    }

    private void syncRouteEditorHints() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile != null && RigProfileFamilies.isXieguFamily(profile)) {
            binding.serialCatPortHintEditText.setHint(R.string.settings_hint_serial_port_xiegu);
            binding.serialCatCivAddressEditText.setHint(R.string.settings_hint_xiegu_address);
            return;
        }
        binding.serialCatPortHintEditText.setHint(R.string.settings_hint_serial_port);
        binding.serialCatCivAddressEditText.setHint(R.string.settings_hint_civ);
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
        return getString(
                R.string.settings_operating_defaults_summary,
                safeValue(grid),
                safeValue(rig),
                safeValue(ant),
                safeValue(wx),
                settings.defaultWpm(),
                settings.defaultToneFrequencyHz(),
                renderFixedToneLearningWindowSummary(rxInputSettingsStore.fixedToneLearningWindowHz()),
                renderRxInputModeLabel(rxInputSettingsStore.rxInputMode()),
                renderRxToneModeLabel(rxInputSettingsStore.rxToneMode()),
                renderMicSourceModeLabel(rxInputSettingsStore.micSourceMode())
        );
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
        String summary = RigRouteStatusFormatter.describeSettingsRouteSummary(
                profile,
                settings,
                routeFallbackStore.usePhoneFallback(),
                readiness
        );
        OperateRouteModeStore.Mode routeMode = operateRouteModeStore == null
                ? OperateRouteModeStore.Mode.STANDARD_AUTO
                : operateRouteModeStore.mode(profile);
        if (routeMode == OperateRouteModeStore.Mode.HYBRID_PHONE_RX) {
            summary += "\n" + getString(
                    R.string.settings_radio_route_mode_line,
                    getString(routeMode.displayNameResId())
            );
        }
        return summary;
    }

    private String renderRouteFallbackText() {
        RouteFallbackStore.Mode mode = routeFallbackStore.mode();
        boolean usePhoneFallback = routeFallbackStore.usePhoneFallback();
        return getString(
                R.string.settings_route_fallback_text,
                renderRouteFallbackModeLabel(mode),
                usePhoneFallback
                        ? getString(R.string.settings_route_fallback_rx_phone_mic)
                        : getString(R.string.settings_route_fallback_disabled),
                usePhoneFallback
                        ? getString(R.string.settings_route_fallback_tx_phone_audio)
                        : getString(R.string.settings_route_fallback_disabled)
        );
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
        if (!shouldShowRouteEditorControls()) {
            return profile == null
                    ? getString(R.string.settings_cat_keying_hint_pick_route)
                    : getString(R.string.settings_cat_keying_hint_manage_in_rig_setup);
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        RigControlAdapter adapter = resolveRouteStatusAdapter(profile, settings);
        return renderRouteActionHint(profile, adapter, settings);
    }

    private String renderCwDefaultsText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return getString(
                R.string.settings_cw_defaults_summary,
                settings.defaultWpm(),
                settings.defaultToneFrequencyHz(),
                renderFixedToneLearningWindowSummary(rxInputSettingsStore.fixedToneLearningWindowHz()),
                renderRxInputModeLabel(rxInputSettingsStore.rxInputMode()),
                renderRxToneModeLabel(rxInputSettingsStore.rxToneMode()),
                renderMicSourceModeLabel(rxInputSettingsStore.micSourceMode())
        );
    }

    private String renderFixedToneLearningWindowSummary(int windowHz) {
        FixedToneLearningWindowPreset preset = FixedToneLearningWindowPreset.fromWindowHz(windowHz);
        if (preset == FixedToneLearningWindowPreset.CUSTOM) {
            return getString(R.string.settings_fixed_tone_summary_custom, windowHz);
        }
        return getString(
                R.string.settings_fixed_tone_summary_preset,
                windowHz,
                getString(preset.shortLabelResId())
        );
    }

    private String renderTxTemplatesSummary() {
        List<TxTemplateEntry> templates = txTemplateStore.loadTemplates();
        ArrayList<String> names = new ArrayList<>();
        for (TxTemplateEntry entry : templates) {
            if (entry != null && hasMeaningfulText(entry.name())) {
                names.add(entry.name().trim());
            }
        }
        String namesSummary = names.isEmpty()
                ? ""
                : getString(R.string.settings_tx_templates_summary_names, String.join(" / ", names));
        return getString(
                R.string.settings_tx_templates_summary,
                templates.size(),
                namesSummary,
                String.join(" ", txTemplateStore.supportedPlaceholders())
        );
    }

    private String renderDeveloperModeStatus(boolean enabled) {
        return enabled
                ? getString(R.string.settings_status_developer_mode_on)
                : getString(R.string.settings_status_developer_mode_off);
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
        String stationCallsign = normalizeUppercaseEditorValue(binding.stationCallsignEditText);
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
                ? getString(R.string.settings_status_station_profile_cleared)
                : getString(R.string.settings_status_station_profile_saved);
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
            stationProfileStatusMessage = getString(R.string.settings_status_location_service_unavailable);
            refreshUi();
            Toast.makeText(this, R.string.settings_toast_location_service_unavailable, Toast.LENGTH_SHORT).show();
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
            stationProfileStatusMessage = getString(R.string.settings_status_location_permission_denied);
            refreshUi();
            Toast.makeText(this, R.string.settings_toast_location_permission_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (bestLocation == null) {
            stationProfileStatusMessage = getString(R.string.settings_status_no_recent_location);
            refreshUi();
            Toast.makeText(this, R.string.settings_toast_no_recent_location, Toast.LENGTH_SHORT).show();
            return;
        }

        String grid = MaidenheadGridUtil.fromLatLon(bestLocation.getLatitude(), bestLocation.getLongitude());
        if (!MaidenheadGridUtil.isValid(grid)) {
            stationProfileStatusMessage = getString(R.string.settings_status_invalid_grid);
            refreshUi();
            Toast.makeText(this, R.string.settings_toast_invalid_grid, Toast.LENGTH_SHORT).show();
            return;
        }

        syncingEditors = true;
        binding.maidenheadGridEditText.setText(grid);
        syncingEditors = false;
        stationProfileDirty = true;
        saveStationProfile();
        stationProfileStatusMessage = getString(R.string.settings_status_grid_filled, grid);
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
                getString(R.string.settings_error_wpm_integer),
                5,
                80,
                getString(R.string.settings_error_wpm_range)
        );
        if (defaultWpm == null) {
            return;
        }

        Integer defaultTone = parsePositiveInt(
                binding.defaultToneEditText.getText(),
                binding.defaultToneEditText,
                getString(R.string.settings_error_tone_integer),
                200,
                2000,
                getString(R.string.settings_error_tone_range)
        );
        if (defaultTone == null) {
            return;
        }

        Integer fixedToneLearningWindowHz = parsePositiveInt(
                binding.fixedToneLearningWindowEditText.getText(),
                binding.fixedToneLearningWindowEditText,
                getString(R.string.settings_error_fixed_tone_window_integer),
                org.bi9clt.cwcn.core.signal.CwSignalProcessor.MIN_FIXED_TONE_LEARNING_WINDOW_HZ,
                org.bi9clt.cwcn.core.signal.CwSignalProcessor.MAX_FIXED_TONE_LEARNING_WINDOW_HZ,
                getString(
                        R.string.settings_error_fixed_tone_window_range,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.MIN_FIXED_TONE_LEARNING_WINDOW_HZ,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.MAX_FIXED_TONE_LEARNING_WINDOW_HZ
                )
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
        cwDefaultsStatusMessage = getString(
                R.string.settings_status_cw_defaults_saved,
                resolveCwDefaultsScope(profile)
        );
        refreshUi();
    }

    private void saveRouteSettings() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            routeSettingsStatusMessage = getString(R.string.settings_status_route_settings_missing_profile);
            refreshUi();
            return;
        }
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        Integer serialBaud = parsePositiveInt(
                binding.serialCatBaudRateEditText.getText(),
                binding.serialCatBaudRateEditText,
                getString(R.string.settings_error_baud_integer),
                300,
                921600,
                getString(R.string.settings_error_baud_range)
        );
        if (serialBaud == null) {
            return;
        }
        Integer networkPort = parsePositiveInt(
                binding.networkPortEditText.getText(),
                binding.networkPortEditText,
                getString(R.string.settings_error_port_integer),
                1,
                65535,
                getString(R.string.settings_error_port_range)
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
        routeSettingsStatusMessage = getString(
                R.string.settings_status_route_settings_saved,
                profile.displayName()
        );
        refreshUi();
    }

    private void saveRouteFallback() {
        RouteFallbackStore.Mode selectedMode = selectedSpinnerValue(
                binding.routeFallbackModeSpinner,
                routeFallbackStore.mode()
        );
        routeFallbackStore.setMode(selectedMode);
        routeFallbackDirty = false;
        routeFallbackStatusMessage = getString(
                R.string.settings_status_route_fallback_saved,
                renderRouteFallbackModeLabel(routeFallbackStore.mode())
        );
        refreshUi();
    }

    private String renderRxInputModeLabel(@Nullable RxInputSettingsStore.RxInputMode mode) {
        return getString((mode == null
                ? RxInputSettingsStore.RxInputMode.AUTO
                : mode).displayNameResId());
    }

    private String renderMicSourceModeLabel(@Nullable RxInputSettingsStore.MicSourceMode mode) {
        return getString((mode == null
                ? RxInputSettingsStore.MicSourceMode.MIC
                : mode).displayNameResId());
    }

    private String renderRxToneModeLabel(@Nullable RxInputSettingsStore.RxToneMode mode) {
        return getString((mode == null
                ? RxInputSettingsStore.RxToneMode.AUTO_TRACK
                : mode).displayNameResId());
    }

    private String renderRouteFallbackModeLabel(@Nullable RouteFallbackStore.Mode mode) {
        return getString((mode == null
                ? RouteFallbackStore.Mode.AUTO_PHONE_FALLBACK
                : mode).displayNameResId());
    }

    private String renderStationProfileEditorStatus() {
        if (stationProfileDirty) {
            return getString(R.string.settings_editor_autosave_pending);
        }
        return stationProfileStatusMessage;
    }

    private String renderCwDefaultsEditorStatus() {
        if (cwDefaultsDirty) {
            return getString(R.string.settings_editor_autosave_pending);
        }
        return cwDefaultsStatusMessage;
    }

    private String renderTxTemplatesEditorStatus() {
        return getString(R.string.settings_status_tx_templates_external);
    }

    private String renderRouteSettingsScopeText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return getString(R.string.settings_scope_no_pinned_route);
        }
        return getString(
                R.string.settings_scope_route_editing,
                profile.displayName(),
                RigUiLabels.transportKind(this, profile.transportKind())
        );
    }

    private String renderRouteSettingsEditorStatus() {
        if (routeSettingsDirty) {
            return getString(R.string.settings_editor_autosave_pending);
        }
        return routeSettingsStatusMessage;
    }

    private String renderRouteFallbackEditorStatus() {
        if (routeFallbackDirty) {
            return getString(R.string.settings_status_route_fallback_switching);
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
            return getString(R.string.settings_profile_source_complete);
        }
        if (hasSavedCallsign || hasSavedName || hasSavedQth || hasSavedGrid || hasSavedRig || hasSavedAnt || hasSavedWx) {
            return getString(R.string.settings_profile_source_partial);
        }
        return hasInferredStationProfile(overview)
                ? getString(R.string.settings_profile_source_inferred)
                : getString(R.string.settings_profile_source_empty);
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
        return profile == null
                ? getString(R.string.settings_cw_defaults_scope_fallback)
                : profile.displayName();
    }

    private boolean shouldShowRouteEditorControls() {
        return developerModeStore.isEnabled();
    }

    private void updateRoutePanelVisibility() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        boolean showRouteEditorControls = shouldShowRouteEditorControls();
        boolean hasProfile = profile != null;
        boolean serialVisible = hasProfile && profile.hasCapability(RigCapability.SERIAL_CAT);
        binding.routeSettingsScopeText.setVisibility(showRouteEditorControls ? View.VISIBLE : View.GONE);
        binding.routeSettingsStatusText.setVisibility(showRouteEditorControls ? binding.routeSettingsStatusText.getVisibility() : View.GONE);
        binding.serialRouteSettingsPanel.setVisibility(showRouteEditorControls && serialVisible ? View.VISIBLE : View.GONE);
        binding.serialCatCivAddressEditText.setVisibility(showRouteEditorControls && serialVisible && rigSelectionStore.loadSettings(profile).serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                ? View.VISIBLE
                : View.GONE);
        binding.networkRouteSettingsPanel.setVisibility(showRouteEditorControls && hasProfile && profile.hasCapability(RigCapability.NETWORK_CAT) ? View.VISIBLE : View.GONE);
        binding.usbRouteSettingsPanel.setVisibility(showRouteEditorControls && hasProfile && profile.hasCapability(RigCapability.KEY_LINE_CONTROL) ? View.VISIBLE : View.GONE);
    }

    private void syncUsbRouteState() {
        if (!shouldShowRouteEditorControls()) {
            binding.usbDeviceSpinner.setVisibility(View.GONE);
            binding.requestUsbKeyerPermissionButton.setVisibility(View.GONE);
            binding.usbRouteStatusText.setVisibility(View.GONE);
            return;
        }
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
            binding.usbRouteStatusText.setText(R.string.settings_usb_adapter_missing);
            return;
        }
        List<UsbSerialDeviceOption> devices = refreshUsbDeviceSelection(adapter, settings.usbPreferredDeviceName());
        boolean hasTargetDevice = adapter.hasTargetDevice();
        boolean ready = adapter.isReady();
        binding.requestUsbKeyerPermissionButton.setEnabled(hasTargetDevice && !ready);
        binding.requestUsbKeyerPermissionButton.setText(ready
                ? getString(R.string.settings_usb_permission_ready)
                : hasTargetDevice
                ? getString(R.string.settings_usb_permission_request)
                : getString(R.string.settings_usb_permission_no_device));
        if (usbRouteStatusMessage != null && !usbRouteStatusMessage.trim().isEmpty()) {
            binding.usbRouteStatusText.setText(usbRouteStatusMessage);
            if (!hasTargetDevice || ready) {
                usbRouteStatusMessage = "";
            }
            return;
        }
        String deviceSummary = hasRealUsbDeviceOption(devices)
                ? valueOrEmpty(settings.usbPreferredDeviceName()).isEmpty()
                ? getString(R.string.settings_usb_route_auto_device)
                : settings.usbPreferredDeviceName()
                : getString(R.string.settings_usb_route_no_candidates);
        binding.usbRouteStatusText.setText(getString(
                R.string.settings_usb_route_summary,
                adapter.displayName(),
                RigRouteStatusFormatter.describeUsbKeyerReadiness(adapter, null),
                settings.usbKeyLine(),
                deviceSummary
        ));
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
            return getString(R.string.settings_route_action_hint_no_profile);
        }
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            if (!(adapter instanceof UsbSerialKeyerRigControlAdapter)) {
                return getString(R.string.settings_route_action_hint_usb_adapter_missing);
            }
            UsbSerialKeyerRigControlAdapter usbAdapter = (UsbSerialKeyerRigControlAdapter) adapter;
            if (usbAdapter.isReady()) {
                return getString(R.string.settings_route_action_hint_usb_ready);
            }
            if (!usbAdapter.hasTargetDevice()) {
                return getString(R.string.settings_route_action_hint_usb_insert);
            }
            return getString(R.string.settings_route_action_hint_usb_permission);
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            if (!hasMeaningfulText(settings.serialCatPortHint())
                    && !hasMeaningfulText(settings.serialCatKeyingPortHint())) {
                return getString(
                        RigProfileFamilies.isXieguFamily(profile)
                                ? R.string.settings_route_action_hint_xiegu_fill_ports
                                : R.string.settings_route_action_hint_serial_fill_ports
                );
            }
            if (adapter != null && adapter.isReady()) {
                if (RigProfileFamilies.isXieguPortableUsbFamily(profile)) {
                    return getString(R.string.settings_route_action_hint_xiegu_portable_ready);
                }
                if (RigProfileFamilies.isXieguG90Line(profile)) {
                    return getString(R.string.settings_route_action_hint_xiegu_g90_ready);
                }
                return getString(R.string.settings_route_action_hint_serial_ready);
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                    && !hasMeaningfulText(settings.serialCatCivAddressHex())) {
                return getString(
                        RigProfileFamilies.isXieguFamily(profile)
                                ? R.string.settings_route_action_hint_xiegu_need_address
                                : R.string.settings_route_action_hint_serial_need_civ
                );
            }
            return getString(
                    RigProfileFamilies.isXieguFamily(profile)
                            ? R.string.settings_route_action_hint_xiegu_check
                            : R.string.settings_route_action_hint_serial_check
            );
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            if (!hasMeaningfulText(settings.networkHost())) {
                return getString(R.string.settings_route_action_hint_network_fill);
            }
            if (adapter != null && adapter.isReady()) {
                return getString(R.string.settings_route_action_hint_network_ready);
            }
            return getString(R.string.settings_route_action_hint_network_check);
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            return getString(R.string.settings_route_action_hint_vox);
        }
        return getString(R.string.settings_route_action_hint_generic);
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
                    ? getString(R.string.settings_usb_permission_requested_status)
                    : getString(
                            R.string.settings_usb_permission_unavailable_status,
                            adapter.describeAvailability()
                    );
        } catch (RuntimeException exception) {
            usbRouteStatusMessage = getString(
                    R.string.settings_usb_permission_failed_status,
                    safeThrowableMessage(exception)
            );
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
                        ? getString(R.string.settings_usb_permission_granted_status)
                        : getString(R.string.settings_usb_permission_denied_status);
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
            return throwable == null ? getString(R.string.settings_unknown_error) : throwable.getClass().getSimpleName();
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

    private String normalizeUppercaseEditorValue(EditText editText) {
        Editable editable = editText == null ? null : editText.getText();
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            return null;
        }
        String normalized = raw.toUpperCase(Locale.US);
        if (editText != null && !normalized.equals(editText.getText() == null ? "" : editText.getText().toString())) {
            syncingEditors = true;
            editText.setText(normalized);
            editText.setSelection(normalized.length());
            syncingEditors = false;
        }
        return normalized;
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
                editText.setError(getString(R.string.settings_error_civ_address_hex));
            }
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                if (editText != null) {
                    editText.setError(getString(R.string.settings_error_civ_address_hex));
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
