package org.bi9clt.cwcn.core.rig;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class RigProfileCatalog {
    private static final List<RigProfile> PROFILES = Collections.unmodifiableList(Arrays.asList(
            new RigProfile(
                    "audio-vox-generic",
                    "通用音频 VOX",
                    "通用",
                    "支持稳定 VOX 触发的任意电台",
                    RigTransport.TransportKind.AUDIO_VOX,
                    "audio-vox",
                    RigSupportLevel.BENCH_READY,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.AUDIO_VOX,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "当没有控制线时，这是最快进入扬声器或有线音频发射验证的路径。",
                    "把手机音频接入电台，设置保守的 VOX 延迟，并先用 DIT 和 VVV 验证起键是否干净。",
                    Arrays.asList(
                            "不提供频率、模式或 PTT 反馈。",
                            "效果依赖电台 VOX 行为和音频电平控制。"
                    )
            ),
            new RigProfile(
                    "usb-serial-keyer-generic",
                    "通用 USB 串口键控",
                    "通用",
                    "支持 RTS/DTR 键控的 CDC/ACM 键控器或串口接口",
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
                    "这是当前最适合真实设备、可预测 USB Host 发射键控的正式路径。",
                    "使用兼容 CDC/ACM 的设备，确认权限流程，选择 RTS 或 DTR，再先用 DIT / VVV 做短验证。",
                    Arrays.asList(
                            "默认前提是设备提供 CDC/ACM 风格控制接口。",
                            "当前还不是完整的 CAT 频率 / 模式集成路径。"
                    )
            ),
            new RigProfile(
                    "usb-serial-keyer-mock",
                    "模拟 USB 串口键控",
                    "CWCN",
                    "内部台架模拟器",
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
                    "这是一个不依赖外部硬件的 USB 流程、权限和失败阶段诊断路径。",
                    "仅用于台架和界面验证，不能替代真实硬件验收。",
                    Collections.singletonList("这是一条纯模拟路径，不会控制真实电台。")
            ),
            new RigProfile(
                    "generic-cat-serial",
                    "通用串口 CAT / PTT",
                    "规划中",
                    "FT-8xx / IC-7xx / TS-4xx 一类串口电台",
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
                    "这是面向“串口 CAT 加显式 PTT / 键控指令”电台的下一层 profile 家族。",
                    "设计目标是沉淀可复用的指令 / profile 层，而不是为每个机型单独写一次性代码。",
                    Arrays.asList(
                            "共享原生串口 CAT 适配器当前优先补齐就绪和探测链路。",
                            "只有在具体 CAT 家族尚不明确时才使用它；条件允许时优先选择更具体的 CAT 家族。"
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
                    "通用 Yaesu 串口 CAT",
                    "Yaesu 风格",
                    "FT 系列及兼容串口 CAT 电台",
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
                    "这是基于共享 CAT 骨架承接 Yaesu 风格串口命令族的具体 profile 占位。",
                    "先在电台配置中选择 Yaesu 风格 CAT，再固定波特率和串口提示，最后做机型级验证。",
                    Arrays.asList(
                            "共享原生串口 CAT 适配器已接入就绪和探测流程。",
                            "不同代际电台的命令细节仍可能存在差异。"
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
                    "Yaesu FT 系列（rigctld）",
                    "Yaesu",
                    "FT-710 / FT-891 / FT-991A 等 rigctld 路径",
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
                    "这是当前 CWCN 中 Yaesu 家族最正式、最推荐的首条验证路径：先通过 Hamlib rigctld 暴露电台，再从 App 做发射联调。",
                    "对 FT-710 及附近 Yaesu FT 机型，先验证 rigctld 主机 / 端口，再用极短 CW 台架报文验证后再进入长报文。",
                    Arrays.asList(
                            "这是当前 CWCN 中 Yaesu 家族最推荐的首条正式测试路径。",
                            "Android 原生 Yaesu 串口 CAT 还没有完全接入，当前先以 rigctld 作为桥接。",
                            "实际频率、模式和 PTT 覆盖仍取决于 rigctld 的机型支持与本地守护进程配置。"
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
                    "Icom 家族（rigctld）",
                    "Icom",
                    "IC-705 / IC-7300 / IC-9700 等 rigctld 路径",
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
                    "这是当前 CWCN 中 Icom 家族最正式、最推荐的首条验证路径：先通过 Hamlib rigctld 暴露电台，再从 App 做发射联调。",
                    "对常见 Icom 机型，先验证 rigctld 主机 / 端口，再用短 CW 台架报文验证后再进入长报文。",
                    Arrays.asList(
                            "这是当前 CWCN 中 Icom 家族最推荐的首条正式测试路径。",
                            "Android 原生 CI-V 路径仍在补齐中，当前先以 rigctld 作为桥接。",
                            "实际覆盖仍取决于 rigctld 的机型支持与本地守护进程配置。"
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
                    "通用 Icom CI-V",
                    "Icom",
                    "支持 CI-V 的串口电台与桥接设备",
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
                    "这是承接 Icom CI-V 风格电台的具体 CAT 家族占位，便于后续独立扩展 CI-V 设置而不打扰其他 CAT 家族。",
                    "当电台或桥接设备使用 CI-V 时选它；电台地址和传输层细节可以后续再补充。",
                    Arrays.asList(
                            "共享原生串口 CAT 适配器已接入就绪和探测流程。",
                            "电台地址和总线专属设置后续仍需要独立字段。"
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
                    "通用 Kenwood 串口 CAT",
                    "Kenwood 风格",
                    "TS 系列及兼容串口 CAT 电台",
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
                    "这是承接 Kenwood 风格 ASCII CAT 电台及兼容桥接设备的具体 CAT 家族占位。",
                    "当电台或桥接设备遵循 TS 风格 ASCII CAT 语义时，选择这一家族。",
                    Arrays.asList(
                            "共享原生串口 CAT 适配器已接入就绪和探测流程。",
                            "机型差异仍需要 profile 级验证。"
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
                    "Kenwood 家族（rigctld）",
                    "Kenwood",
                    "TS-590 / TS-890 / TS-2000 等 rigctld 路径",
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
                    "这是当前 CWCN 中 Kenwood 家族最正式、最推荐的首条验证路径：先通过 Hamlib rigctld 暴露电台，再从 App 做发射联调。",
                    "对常见 Kenwood 机型，先验证 rigctld 可达性，再用短 CW 台架报文验证后再进入长报文。",
                    Arrays.asList(
                            "这是当前 CWCN 中 Kenwood 家族最推荐的首条正式测试路径。",
                            "Android 原生 Kenwood 串口 CAT 仍在补齐中，当前先以 rigctld 作为桥接。",
                            "实际覆盖仍取决于 rigctld 的机型支持与本地守护进程配置。"
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
                    "通用网络 CAT",
                    "规划中",
                    "LAN / Wi-Fi CAT 桥接与网络电台服务",
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
                    "这是为通过 TCP / UDP 暴露 CAT 的电台或中间件预留的路径。",
                    "它会尽量保持与串口 CAT 一致的 profile / capability 形态，让 UI 在传输层变化时仍保持稳定。",
                    Collections.singletonList("当前还没有接入具体的传输层或会话实现。"),
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
                    "通用 Hamlib rigctld",
                    "Hamlib",
                    "rigctld 网络桥接",
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
                    "这是承接 rigctld 兼容桥接与局域网控制栈的具体网络 CAT 家族占位。",
                    "当电台通过 rigctld 或兼容桥接暴露出来时，使用主机 / 端口加 Hamlib rigctld 协议族的组合。",
                    Arrays.asList(
                            "当前还没有挂接完整的 rigctld 会话后端。",
                            "响应解析与能力发现仍待继续实现。"
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
                    "通用蓝牙串口电台",
                    "规划中",
                    "蓝牙 SPP 桥接与无线键控器",
                    RigTransport.TransportKind.BLUETOOTH_SERIAL,
                    "generic-text-to-cw",
                    RigSupportLevel.PLANNED,
                    EnumSet.of(
                            RigCapability.TEXT_TO_CW,
                            RigCapability.PTT_CONTROL,
                            RigCapability.BLUETOOTH_SERIAL,
                            RigCapability.LIVE_PROFILE_UPDATE
                    ),
                    "这是面向轻线缆场景的未来路径，等蓝牙配对、发现和会话稳定性策略明确后再接入。",
                    "当前可以先把 UI 和权限流程准备好，底层实现则等有线路径稳定后再推进。",
                    Collections.singletonList("当前还没有接入可用于生产的蓝牙电台后端。")
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
