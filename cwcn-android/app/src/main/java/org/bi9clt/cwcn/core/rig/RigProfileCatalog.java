package org.bi9clt.cwcn.core.rig;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class RigProfileCatalog {
    private static final List<RigProfile> PROFILES = Collections.unmodifiableList(Arrays.asList(
            new RigProfile(
                    "audio-vox-generic",
                    "Generic Audio VOX",
                    "Generic",
                    "Any radio with reliable VOX trigger",
                    RigTransport.TransportKind.AUDIO_VOX,
                    "audio-vox",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.AUDIO_VOX,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "Fastest path for speaker or wired-audio TX testing when no control cable is available.",
                    "Route phone audio into the rig, set a conservative VOX delay, and confirm the radio keys cleanly on DIT and VVV patterns.",
                    Arrays.asList(
                            "No frequency/mode/PTT feedback.",
                            "Relies on radio VOX behavior and audio level discipline."
                    )
            ),
            new RigProfile(
                    "usb-serial-keyer-generic",
                    "Generic USB Serial Keyer",
                    "Generic",
                    "CDC/ACM keyer or serial interface with RTS/DTR keying",
                    RigTransport.TransportKind.USB_SERIAL,
                    "usb-serial-keyer",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.PTT_CONTROL,
                            RigCapability.KEY_LINE_CONTROL,
                            RigCapability.USB_DEVICE_SELECTION,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "Current best real-device control path for deterministic TX keying over USB host.",
                    "Use a CDC/ACM-compatible device, confirm permission flow, choose RTS or DTR, then bench with DIT/VVV before longer traffic.",
                    Arrays.asList(
                            "Assumes CDC/ACM-style control interface.",
                            "Not yet a full CAT frequency/mode integration path."
                    )
            ),
            new RigProfile(
                    "usb-serial-keyer-mock",
                    "Mock USB Serial Keyer",
                    "CWCN",
                    "Internal bench simulator",
                    RigTransport.TransportKind.USB_SERIAL,
                    "usb-serial-keyer-mock",
                    RigSupportLevel.DEBUG_ONLY,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.PTT_CONTROL,
                            RigCapability.KEY_LINE_CONTROL,
                            RigCapability.USB_DEVICE_SELECTION,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "Diagnostic route for USB flow, permission, and failure-stage testing without external hardware.",
                    "Use only for bench and UI validation. It is not a substitute for hardware acceptance.",
                    Collections.singletonList("Synthetic route only; no real rig is controlled.")
            ),
            new RigProfile(
                    "generic-cat-serial",
                    "Generic Serial CAT / PTT",
                    "Planned",
                    "FT-8xx / IC-7xx / TS-4xx style serial rigs",
                    RigTransport.TransportKind.USB_SERIAL,
                    "generic-cat",
                    RigSupportLevel.PLANNED,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.SERIAL_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Next-layer profile family for rigs that expose serial CAT plus explicit PTT/keying commands.",
                    "Design target is a reusable command/profile layer rather than one-off per-model code.",
                    Arrays.asList(
                            "Shared native serial CAT adapter now covers readiness/probe plumbing first.",
                            "Use this only when the exact CAT family is still unknown; prefer a more specific CAT family when possible."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.GENERIC,
                            9600,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "yaesu-cat-serial-generic",
                    "Generic Yaesu Serial CAT",
                    "Yaesu-style",
                    "FT-series and compatible serial CAT rigs",
                    RigTransport.TransportKind.USB_SERIAL,
                    "generic-cat",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.SERIAL_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Concrete CAT family placeholder for Yaesu-style serial command sets on top of the shared CAT schema.",
                    "Start with the Yaesu-style CAT family in Rig Setup, then pin baud rate and serial port hints before model-specific validation.",
                    Arrays.asList(
                            "Shared native serial CAT adapter is attached for readiness/probe workflows.",
                            "Exact command dialect may still vary by radio generation."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.YAESU_STYLE,
                            38400,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "yaesu-rigctld-network-family",
                    "Yaesu FT-Series via rigctld",
                    "Yaesu",
                    "FT-710 / FT-891 / FT-991A and similar via rigctld",
                    RigTransport.TransportKind.NETWORK_CAT,
                    "hamlib-rigctld",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.NETWORK_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Current best formal Yaesu-family test path in CWCN: expose the radio through Hamlib rigctld first, then bench TX from the app.",
                    "For FT-710 and nearby Yaesu FT models, start with rigctld host/port validation, then run a very short CW bench macro before longer traffic.",
                    Arrays.asList(
                            "This is the recommended first test path for Yaesu family radios in CWCN today.",
                            "Direct native Android Yaesu serial CAT is still not attached yet; use rigctld as the bridge for now.",
                            "Actual frequency/mode/PTT coverage still depends on rigctld model support and local daemon configuration."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.YAESU_STYLE,
                            38400,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "icom-rigctld-network-family",
                    "Icom Family via rigctld",
                    "Icom",
                    "IC-705 / IC-7300 / IC-9700 and similar via rigctld",
                    RigTransport.TransportKind.NETWORK_CAT,
                    "hamlib-rigctld",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.NETWORK_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Current best formal Icom-family test path in CWCN: expose the radio through Hamlib rigctld first, then bench TX from the app.",
                    "For common Icom rigs, start with rigctld host/port validation, then run a short CW bench macro before longer traffic.",
                    Arrays.asList(
                            "Recommended first formal test path for Icom-family radios in CWCN today.",
                            "Direct native Android CI-V work is still pending; use rigctld as the bridge for now.",
                            "Actual coverage still depends on rigctld model support and local daemon configuration."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.ICOM_CIV,
                            19200,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "icom-civ-serial-generic",
                    "Generic Icom CI-V",
                    "Icom",
                    "CI-V capable serial rigs and bridges",
                    RigTransport.TransportKind.USB_SERIAL,
                    "generic-cat",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.SERIAL_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Concrete CAT family placeholder for Icom CI-V style rigs so CI-V-specific setup can grow without disturbing other CAT families.",
                    "Use the Icom CI-V CAT family when the rig or bridge speaks CI-V; radio address and transport specifics can be added later.",
                    Arrays.asList(
                            "Shared native serial CAT adapter is attached for readiness/probe workflows.",
                            "Radio-address and bus-specific settings still need dedicated fields."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.ICOM_CIV,
                            19200,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "kenwood-cat-serial-generic",
                    "Generic Kenwood Serial CAT",
                    "Kenwood-style",
                    "TS-series and compatible serial CAT rigs",
                    RigTransport.TransportKind.USB_SERIAL,
                    "generic-cat",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.SERIAL_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Concrete CAT family placeholder for Kenwood-style ASCII CAT rigs and compatible bridges.",
                    "Select the Kenwood-style CAT family when the rig or bridge follows TS-style ASCII CAT semantics.",
                    Arrays.asList(
                            "Shared native serial CAT adapter is attached for readiness/probe workflows.",
                            "Model quirks still need profile-specific validation."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.KENWOOD_STYLE,
                            57600,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "kenwood-rigctld-network-family",
                    "Kenwood Family via rigctld",
                    "Kenwood",
                    "TS-590 / TS-890 / TS-2000 and similar via rigctld",
                    RigTransport.TransportKind.NETWORK_CAT,
                    "hamlib-rigctld",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.NETWORK_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Current best formal Kenwood-family test path in CWCN: expose the radio through Hamlib rigctld first, then bench TX from the app.",
                    "For common Kenwood rigs, start with rigctld reachability, then run a short CW bench macro before longer traffic.",
                    Arrays.asList(
                            "Recommended first formal test path for Kenwood-family radios in CWCN today.",
                            "Direct native Android Kenwood serial CAT is still pending; use rigctld as the bridge for now.",
                            "Actual coverage still depends on rigctld model support and local daemon configuration."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.KENWOOD_STYLE,
                            57600,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "generic-network-cat",
                    "Generic Network CAT",
                    "Planned",
                    "LAN/Wi-Fi CAT bridges and networked rig servers",
                    RigTransport.TransportKind.NETWORK_CAT,
                    "generic-cat",
                    RigSupportLevel.PLANNED,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.NETWORK_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Reserved for rigs or middleware that expose CAT over TCP/UDP rather than direct serial.",
                    "Keep the same profile/capability shape as serial CAT so the UI can stay stable while the transport changes.",
                    Collections.singletonList("No transport/session implementation attached yet."),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.GENERIC,
                            9600,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "hamlib-rigctld-network-generic",
                    "Generic Hamlib rigctld",
                    "Hamlib",
                    "rigctld network bridge",
                    RigTransport.TransportKind.NETWORK_CAT,
                    "hamlib-rigctld",
                    RigSupportLevel.PLANNED,
                    EnumSet.of(
                            RigCapability.PTT_CONTROL,
                            RigCapability.NETWORK_CAT,
                            RigCapability.FREQUENCY_READ,
                            RigCapability.FREQUENCY_SET,
                            RigCapability.MODE_READ,
                            RigCapability.MODE_SET
                    ),
                    "Concrete network CAT family placeholder for rigctld-compatible bridges and LAN-connected control stacks.",
                    "Use host/port plus the Hamlib rigctld CAT family when a radio is exposed through rigctld or a compatible bridge.",
                    Arrays.asList(
                            "No rigctld session backend is attached yet.",
                            "Response parsing and capability discovery still need implementation."
                    ),
                    new RigProfileSettings(
                            18,
                            650,
                            SerialKeyerTxOutput.KeyLine.RTS,
                            null,
                            CatProtocolFamily.GENERIC,
                            9600,
                            null,
                            CatProtocolFamily.HAMLIB_RIGCTLD,
                            null,
                            4532,
                            null
                    )
            ),
            new RigProfile(
                    "generic-bluetooth-serial",
                    "Generic Bluetooth Serial Rig",
                    "Planned",
                    "Bluetooth SPP bridges and wireless keyers",
                    RigTransport.TransportKind.BLUETOOTH_SERIAL,
                    "generic-text-to-cw",
                    RigSupportLevel.PLANNED,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.PTT_CONTROL,
                            RigCapability.BLUETOOTH_SERIAL,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "Future route for cable-light setups once Bluetooth pairing, discovery, and session stability are defined.",
                    "UI and permission flow should be prepared now, but low-level implementation can wait until the wired routes stabilize.",
                    Collections.singletonList("No production Bluetooth rig backend is attached yet.")
            )
    ));

    private RigProfileCatalog() {
    }

    public static List<RigProfile> defaultProfiles() {
        return PROFILES;
    }

    public static RigProfile findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        for (RigProfile profile : PROFILES) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        return null;
    }
}
