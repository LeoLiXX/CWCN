package org.bi9clt.cwcn.core.bootstrap;

import java.util.Arrays;
import java.util.List;

public final class BootstrapRegistry {
    private BootstrapRegistry() {
    }

    public static List<BootstrapModule> defaultModules() {
        return Arrays.asList(
                new BootstrapModule(
                        "rig",
                        "RigTransport / RigControlAdapter",
                        "先统一 USB / 蓝牙串口 / CAT 接入边界，给后续发射与收音留一个稳定入口。",
                        ModuleStatus.IN_PROGRESS
                ),
                new BootstrapModule(
                        "audio",
                        "RxAudioSource / CwSignalProcessor",
                        "先打通麦克风或电台音频输入链路，再接入基础频谱、音调门限与回放能力。",
                        ModuleStatus.NEXT_UP
                ),
                new BootstrapModule(
                        "decode",
                        "CwTimingModel / CwDecoder / CwInterpreter",
                        "连续流解码、高精度时序建模、多候选字符与 QSO 语义解释仍按独立模块推进。",
                        ModuleStatus.PLANNED
                ),
                new BootstrapModule(
                        "tx",
                        "CwTxEngine",
                        "文本驱动发射会统一抽象，后续逐步补齐 RTS / DTR / VOX / 外部 keyer 等后端。",
                        ModuleStatus.PLANNED
                ),
                new BootstrapModule(
                        "logbook",
                        "QsoStateMachine / LogRepository",
                        "日志链路按原始事件、草稿、正式日志三层落地，并保留 FT8CN 风格 ADIF 导出体验。",
                        ModuleStatus.PLANNED
                )
        );
    }
}
