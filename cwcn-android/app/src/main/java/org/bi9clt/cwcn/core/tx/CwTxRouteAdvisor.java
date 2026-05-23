package org.bi9clt.cwcn.core.tx;

public final class CwTxRouteAdvisor {
    private CwTxRouteAdvisor() {
    }

    public static String buildChecklist(CwTxBackend backend, CwTxPlan plan) {
        if (backend == null) {
            return "请先选择一条 TX 路径。";
        }
        if ("local-sidetone".equals(backend.id())) {
            return "本地侧音检查单：这条路径适合做脱机演练验证，建议优先佩戴耳机，当前 WPM 和音调会直接应用到本地侧音。";
        }
        if ("rig-text:audio-vox-text".equals(backend.id())) {
            return buildAudioVoxChecklist(plan);
        }
        if (backend.id() != null && backend.id().startsWith("rig-text:usb-serial-keyer")) {
            return buildUsbKeyerChecklist(plan);
        }
        if ("rig-text:hamlib-rigctld".equals(backend.id())) {
            return buildHamlibRigctldChecklist(plan);
        }
        return "电台链路检查单：先确认当前路径已经就绪，再核对这个适配器希望通过什么方式把键控、PTT 或音频送到目标设备。";
    }

    private static String buildAudioVoxChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("音频 VOX 检查单：把手机音频接到电台或键控器的音频输入链路，打开目标设备上的 VOX，并从保守的手机音量开始验证。");
        if (plan != null) {
            if (plan.toneFrequencyHz() < 500 || plan.toneFrequencyHz() > 900) {
                builder.append("\n音调提示：VOX 验证通常在 500-900 Hz 更容易起步。");
            }
            if (plan.wpm() < 10 || plan.wpm() > 28) {
                builder.append("\nWPM 提示：VOX 初始验证通常在 10-28 WPM 更容易成功。");
            }
            if (plan.totalDurationMs() > 30000) {
                builder.append("\n时长提示：空口 VOX 长报文更难校准，建议先从更短的模板开始。");
            }
        }
        return builder.toString();
    }

    private static String buildUsbKeyerChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("USB RTS/DTR 检查单：确认 USB 串口键控器已经接入，核对实际接线使用的是哪根控制线，并让第一次测试尽量短。");
        builder.append("\n安全恢复：如果控制线看起来卡住了，先用“释放键控线”，再执行“刷新 USB 设备”。");
        if (plan != null) {
            if (plan.wpm() > 35) {
                builder.append("\nWPM 提示：在硬件确认 RTS/DTR 时序之前，建议先从 35 WPM 以下开始。");
            }
            if (plan.totalDurationMs() > 20000) {
                builder.append("\n时长提示：初始硬件键控测试尽量短一些，控制线异常时更容易恢复。");
            }
            builder.append("\n音调说明：RTS/DTR 键控不受音调频率影响，只看时序。");
        }
        return builder.toString();
    }

    private static String buildHamlibRigctldChecklist(CwTxPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hamlib rigctld 检查单：确认电台配置已固定到网络 CAT 配置，填好主机和端口，并在长报文之前先验证 rigctld 能接受 send_morse 命令。");
        builder.append("\n恢复提示：如果 TX 一上来就失败，先检查网络可达性，以及 rigctld 是否已经绑定到目标电台。");
        builder.append("\nYaesu 说明：对 FT-710 及相近的 FT 系列机型，在原生 Android Yaesu CAT 还未接入前，这仍是 CWCN 当前优先推荐的正式验证路径。");
        if (plan != null) {
            if (plan.wpm() > 35) {
                builder.append("\nWPM 提示：在确认目标电台上的 rigctld KEYSPD 行为之前，建议先从 35 WPM 以下开始。");
            }
            if (plan.toneFrequencyHz() < 400 || plan.toneFrequencyHz() > 900) {
                builder.append("\n音高提示：很多电台在 CWPITCH 取中间值时行为更稳定。");
            }
            if (plan.totalDurationMs() > 20000) {
                builder.append("\n时长提示：第一次网络 CAT 测试尽量短一些，更容易隔离命令侧和电台侧的行为。");
            }
        }
        return builder.toString();
    }
}
