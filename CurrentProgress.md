# CWCN Current Progress

最后更新：2026-04-23

这份文档用于保存当前阶段的关键上下文，避免后续讨论过长时丢失已经确认的设计与工程进展。

## 1. 项目当前定位

- 项目名称：`CWCN`
- 目标形态：Android 端 `CW` 工作台
- 路线选择：复用 `FT8CN` 的部分工程经验与日志思路，但重写 `CW` 专属核心
- 当前阶段：完成设计收敛、独立 Android 起步工程搭建，并已进入 `CW` 信号处理与时序建模的第一版落地

## 2. 已确认的核心决策

这些内容已经在 [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md) 固化，不应轻易反复摇摆：

- 接收链路采用连续流，而不是 `FT8` 那样的固定时隙
- 高精度解码是核心目标，从第一天起按 `CW Skimmer` 级别目标设计
- 自适应 `WPM`、高噪声、`QSB`、真人 `OP` 手法、`Bug Key` 等鲁棒性归入同一高精度解码目标
- 发送链路统一表述为“文本驱动发射 / Text-to-CW”
- “直接发文本给电台 keyer”只是可选后端之一，不是整个产品定义
- QSO 管理采用四层模型：原始事件 / 草稿 / 正式日志 / 同步队列
- `ADIF` 导出是高优先级，且体验尽量保持与 `FT8CN` 一致
- 第三方平台同步优先级较低，后续通过适配器和异步队列追加
- Android 工程根命名空间固定为 `org.bi9clt.cwcn`

## 3. 设计文档当前覆盖范围

[CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md) 当前已经覆盖：

- 产品定位与非目标
- 总体模块拆分
- `RigTransport / RigControlAdapter / RxAudioSource`
- `CwSignalProcessor / CwTimingModel / CwDecoder / CwInterpreter`
- 文本驱动发射链路 `CwTxEngine`
- QSO 状态机与草稿模型
- 呼号识别、部分呼号收敛、反复 `QRZ` / `AGN` / `CALL AGAIN` 等场景
- `RST` 与常见 `CW` 缩略语、cut number、容错归一化规则
- 日志与 `ADIF` 导出设计
- 图标与品牌资源落点
- 研发里程碑 `M1 ~ M5`

## 4. 已落地的工程成果

### 4.1 独立 Android 工程

已创建独立工程目录：

- [cwcn-android](/D:/Workshop/CWCN/cwcn-android)

当前工程已具备：

- 独立 Gradle 工程结构
- `app` 模块
- `InputDebugActivity`
- 基础主题、颜色、启动页面
- 自适应图标接入
- 核心模块注册表占位结构
- 输入链路调试页
- 第一版设备状态探测
- 第一版麦克风 `AudioRecord` 采集骨架

### 4.2 命名空间与包结构

当前工程已经统一到：

- `namespace`: `org.bi9clt.cwcn`
- `applicationId`: `org.bi9clt.cwcn`
- Java 包路径：`org.bi9clt.cwcn.*`

相关入口文件：

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [app/build.gradle](/D:/Workshop/CWCN/cwcn-android/app/build.gradle)

当前包结构已经整理成：

- `ui/debug`
- `core/adif`
- `core/audio`
- `core/decoder`
- `core/interpreter`
- `core/qso`
- `core/rig`
- `core/signal`
- `core/timing`
- `core/bootstrap`

### 4.3 图标资源

当前图标资源已经落地：

- 主母版：[assets/branding/cwcn-icon.svg](/D:/Workshop/CWCN/assets/branding/cwcn-icon.svg)
- Android 接入版说明：[assets/branding/android/README.md](/D:/Workshop/CWCN/assets/branding/android/README.md)
- 导出规格：[assets/branding/EXPORT_SPEC.md](/D:/Workshop/CWCN/assets/branding/EXPORT_SPEC.md)

### 4.4 当前已落地的 M1 代码

当前已经完成 `M1` 的第一批实现，不再只是静态首页：

- 已建立 `RigTransport` 与 `RigControlAdapter` 接口层
- 已建立 `RxAudioSource` 抽象与 `MicrophoneRxAudioSource` 第一版实现
- 首页已改造成设备/输入调试页，可查看麦克风、蓝牙、USB 状态
- 已支持输入源选择占位
- 已支持录音权限申请
- 已支持第一版开始/停止采集
- 已支持音频帧回调计数、累计采样点、`Peak / RMS` 电平显示
- 已接入 `CwSignalProcessor v1`
- 已能把音频帧转成基础 `tone on/off` 事件流
- 已在调试页显示 `Tone Active/Idle`、门限、噪声底估计、事件统计与最近事件
- 已接入 `CwTimingModel v1`
- 已能把 `tone/gap` 持续时间抽出来，并给出 `DIT / DAH / INTRA_SYMBOL_GAP / LETTER_GAP / WORD_GAP` 的基础分类
- 已在调试页显示 `dot estimate`、估算 `WPM`、timing 事件统计与最近 timing 事件
- 已接入 `CwDecoder v1`
- 已能把 `DIT / DAH / GAP` 进一步收敛成最小莫尔斯序列与字符输出
- 已在调试页显示当前序列、已解码文本、symbol/character 计数与最近 decode 事件
- 已接入 `CwInterpreter v1`
- 已能把字符流提升成基础 token、归一化文本、callsign 候选与语义提示
- 已在调试页显示 raw text、normalized text、callsign candidates、hints 与最近 interpretation 事件
- 已接入 `QsoStateMachine v1`
- 已能把 interpreter 输出映射到 `calling_cq / reply_detected / report_exchange / closing / completed` 等基础阶段
- 已能生成最小 `QSO draft` 视图，包括 remote callsign candidate、report candidate、phase 与 draft-ready 状态
- 已扩展 `QSO draft` 的候选字段，当前包括 `rst_sent_candidate / rst_rcvd_candidate / name_candidate / qth_candidate`
- 已接入最小 `ADIF preview`，可从当前 draft 直接生成预览记录文本

主要文件：

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [RigTransport.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigTransport.java)
- [RigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigControlAdapter.java)
- [RigRegistry.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigRegistry.java)
- [RxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/RxAudioSource.java)
- [MicrophoneRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/MicrophoneRxAudioSource.java)
- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwToneEvent.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwToneEvent.java)
- [CwTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [CwTimingEvent.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingEvent.java)
- [CwDecoder.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/decoder/CwDecoder.java)
- [CwDecodeEvent.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/decoder/CwDecodeEvent.java)
- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpretedToken.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpretedToken.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [QsoDraftSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoDraftSnapshot.java)
- [CwAdifExporter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/adif/CwAdifExporter.java)

## 5. 已验证状态

以下内容已经实际验证过：

- `cwcn-android` 可以执行 `.\gradlew.bat assembleDebug`
- 当前起步工程可以成功构建 `debug` 包
- 图标资源已经正确接入 Android 工程

说明：

- 构建过程中已处理过一次 `compileSdk / 依赖版本` 对齐问题
- 已处理过一次 Kotlin 传递依赖冲突
- 已处理过一次 Java `record` 与 `sourceCompatibility` 不匹配问题

也就是说，当前工程不是“只写了文件但没验证”的状态，而是已经通过最小构建验证

## 6. 当前代码仍然只是骨架

目前还没有真正完成以下能力：

- 统一连接层的真实实现
- 蓝牙 / USB / CAT 的实际接入
- 麦克风或外部音频采集链路
- 频谱显示
- 草稿持久化与正式日志联动
- 更强的 callsign 收敛、部分呼号处理与高层容错归一化
- 发射后端实现
- 正式日志表与真实 `ADIF` 文件导出代码

当前阶段仍然属于“工程落地已开始，业务核心未开始写”的状态。

## 7. 推荐的下一步

最合理的下一步是从内存态 draft 继续往下走，进入持久化与正式日志层：

1. 建最小本地 `draft/log` 持久化模型
2. 把当前 `ADIF preview` 升级成真实导出动作
3. 继续增强 partial callsign 与 `QRZ / AGAIN` 多轮确认处理
4. 保留当前调试页作为 signal/timing/decoder/interpreter/qso/adif 的观察窗口

## 8. 后续继续协作时的建议

如果后续上下文过长，可以优先读取这几份文件：

- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)
- [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md)
- [cwcn-android/README.md](/D:/Workshop/CWCN/cwcn-android/README.md)

如果需要看参考来源，再看：

- [FT8CN/FT8CN机制梳理与CW新工程蓝本.md](/D:/Workshop/CWCN/FT8CN/FT8CN机制梳理与CW新工程蓝本.md)
- [Requirement.md](/D:/Workshop/CWCN/Requirement.md)

## 2026-04-21 Persistence + ADIF Export

- Added a minimal local repository layer based on `SharedPreferences + JSON`.
- The app can now save the current QSO draft locally and restore it on next launch.
- The app can confirm the current draft into a local confirmed-log list.
- The app can export confirmed logs as a real `.adi` file.
- Export target directory is the app-specific documents/files area under `exports/`.
- Current debug UI now includes `Save Draft`, `Confirm Log`, `Export ADIF`, and status text for storage/export.
- RST export now tolerates common CW shorthand normalization in the ADIF path:
- `N -> 9`
- `T -> 0`

### Key files

- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [ConfirmedQsoLog.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedQsoLog.java)
- [CwAdifExporter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/adif/CwAdifExporter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)

### Suggested next steps

1. Add manual draft correction fields for callsign / RST / name / QTH.
2. Split `confirmed log` from `live draft` more clearly in the UI.
3. Move storage from `SharedPreferences` to SQLite/Room when the log schema stabilizes.
4. Start preparing a real TX path (`Text -> CW`) beside the RX/decode chain.

## 2026-04-21 Manual Draft Correction UI

- Added a manual draft editor to the QSO panel for:
- `station callsign`
- `remote callsign`
- `RST sent`
- `RST rcvd`
- `NAME`
- `QTH`
- Added `Apply Edits` and `Reset Manual` actions.
- Manual corrections now override live decoder output until reset.
- Unapplied editor changes are kept in the UI and will not be overwritten by incoming decode refreshes.
- Manual correction flags are persisted inside the saved draft snapshot.

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Debug UI Front-End Health Summary

- Added a compact `signal health` summary block to the Debug page so front-end status is easier to judge during live microphone or fixture testing.
- The new summary converts raw `CwSignalSnapshot` values into a more human-readable heuristic state, including:
- `Healthy lock`
- `Weak lock / low dominance`
- `Tone detected but confidence low`
- `Searching / no stable target`
- `Tracking drift rising`
- The summary also exposes a short reason plus a few practical metrics in one place:
- tone dominance
- tone RMS
- attack headroom over threshold
- release margin over noise floor
- track offset from preferred tone
- When `Phone Microphone` is the selected source, the input-source summary now also shows a short `Mic Tone Watch` line so real-input testing can compare:
- preferred tone
- tracked tone
- frequency offset direction
- current tone confidence
- This is intentionally a UI observability layer, not a decoding decision layer, so it improves real-world tuning/debugging without changing the signal pipeline itself.

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)

## 2026-04-22 Narrowband Isolation Heuristic

- Extended the signal front end with a more explicit `narrowband isolation` measure so we are no longer relying on `tone dominance` alone when deciding whether the tracked CW tone is truly standing apart from residual wideband energy.
- The processor now derives:
- tracked tone RMS
- residual wideband RMS
- narrowband isolation ratio
- Lock retention and narrowband qualification now consider both:
- tone dominance
- narrowband isolation
- Practical goal:
- persistent broadband noise or non-target residue should be less able to lift `attack/release` behavior into a misleadingly strong state
- while a real target tone under moderate noise should still qualify and key normally
- The new metric is now exposed in Debug UI and microphone tone-watch text, so real-input testing can compare:
- dominance
- isolation
- residual RMS
- tracked tone offset
- Added deterministic broadband-noise unit coverage plus an offline regression check around the noisy directed-report fixture.

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Signal Run-History Observability

- Extended `CwSignalProcessor` again so Debug UI and offline regression can inspect not only the latest frame, but also the best front-end state reached during the current run.
- New run-history signal stats now include:

## 2026-04-23 Damaged Residue Recovery + Stream-End Flush

- Added explicit token-normalization observability across interpreter, debug UI, and offline fixture evaluation.
- `CwInterpretedToken` now records whether a normalized token was recovered from raw damaged text.
- Debug UI now underlines normalized tokens in the normalized-text line and shows a `Recovered/normalized` summary such as `?NN->599` and `B->BK`.
- `CwFixtureEvaluationResult` now reports:
- normalized token count / ratio
- normalized token pairs
- `recoveryPressureCode()` and `recoveryPressureLabel()`
- Damaged short-report residue recovery was extended for common human/QSB-style cases including:
- `UR?NN B`
- `UR 5NNEB`
- `UR ?NN EB`
- `R?NNB`
- Added offline fixture coverage for damaged directed-report and ack-report exchanges.
- Added `CwTimingModel.flushPendingGap(...)` so the final trailing gap at end-of-stream is no longer silently lost when replay or live capture stops after a short closing token.
- Wired the stream-end flush into:
- offline fixture evaluation
- debug UI stop / fixture completion path
- Result: damaged residue fixtures that previously stalled at partial recovery now reach full pass with:
- exact text recall `100%`
- callsign recall `100%`
- QSO semantics `100%`
- hint recall `100%`
- likely bottleneck `OK`
- recovery pressure `HIGH`
- Callsign cleanup was narrowed so we now suppress obvious clean trim artifacts like `I9CL` / `G7YO` without re-breaking contextual partial callsign handling.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest`
- `.\gradlew.bat assembleDebug`

## 2026-04-23 Split Token Recovery For False Intra-Word Spaces

- Added a conservative interpreter-side recovery path for a low-priority but practical real-operator case:
- an OP occasionally inserts an accidental extra pause inside one short CW token
- the decoder then surfaces that as a visible split such as:
- `5 NN`
- `B K`
- `AG N`
- Instead of trying to globally merge arbitrary split words, the interpreter now only rejoins a narrow set of high-value compact tokens:
- `BK`
- `KN`
- `TU`
- `TNX`
- `AGN`
- `PSE`
- `PLS`
- `5NN`
- `ENN`
- `599`
- This keeps the change low-risk:
- ordinary free text is still left alone
- callsign handling still follows its existing dedicated fragment-merge path
- `R` and `K` are not aggressively merged away from their standalone meanings
- Added regression coverage for:
- `UR 5 NN B K`
- `AG N PSE K`
- Added a lightweight offline fixture:
- `human_split_short_tokens_report_exchange`
- These cases now recover the intended clarification/report semantics without needing any timing-layer rewrite yet.

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

## 2026-04-23 Targeted Hesitation-Gap Fixture Support

- Extended synthetic timing profiles so a fixture can now inject an extra pause at exact character boundaries instead of only:
- every N characters
- globally stretched letter gaps
- globally stretched word gaps
- This lets us model a more realistic operator hesitation case where the transmitted text is still normal:
- `UR 5NN BK`
- but timing temporarily behaves more like:
- `UR 5 NN B K`
- without rewriting the source text itself
- `CwFixtureScenario.PartTimingProfile` now supports:
- `extraPauseCharacterOffsets`
- The timing summary text exposes these pauses as:
- `pause @17,20 +5.8 dot`
- Added a new offline fixture:
- `human_hesitation_gap_report_exchange`
- Added another hesitation-focused fixture:
- `human_hesitation_callsign_report_exchange`
- It uses normal text plus targeted pauses after the `5` and `B` positions, so the full synthetic pipeline now covers a genuine timing-layer “false word-space” pattern instead of only string-layer split text.
- The follow-up callsign fixture does the same thing inside the two callsigns, so we now also cover:
- normal text
- split-like timing
- callsign-fragment recovery
- directed-report semantics

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

## 2026-04-23 Damaged Short Report / Control Recovery

- Hardened `CwInterpreter` so short damaged CW report/control chains can still normalize correctly without polluting partial callsign handling.
- The normalization and split logic now share the same conservative control-residue recovery path, instead of using looser fuzzy matching in one place and stricter context logic in another.
- This specifically improves compact or slightly broken report exchanges such as:
- `UR?NN B`
- `UR 5NNEB`
- `UR ?NN EB`
- `R?NNB`
- Practical effect:
- `?NN / 5NN / ENN`-style damaged report residues can still converge to `599` in report context
- short control residues like `B / ? / EB` can still converge to `BK` in control context
- existing uncertain partial callsign flows like `H??Z AGN PSE K` remain preserved and are no longer mis-split as control tokens
- Added targeted interpreter + QSO semantics regression coverage for the new damaged short-chain cases.
- Wider verification also stayed green:
- targeted interpreter / QSO tests
- `CwFixturePipelineRegressionTest`
- full `testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

### Suggested next steps

1. Continue extending polluted-report fixtures toward more realistic hand-key/QSB timing irregularity rather than only token-level corruption.
2. Add a few more medium-frequency damaged exchange cases around `AGN / PSE / CALLSIGN AGAIN` mixed with uncertain callsigns.
3. Start exposing some of this recovery confidence in UI so operator review can distinguish exact copy from best-effort normalization.

## 2026-04-23 Interpreter Normalization Observability

- Added an explicit token-level `normalizedFromRaw()` marker so interpreter output can distinguish exact-copy tokens from tokens that were normalized or recovered from raw CW text.
- This does not change decoding semantics. It is purely an observability layer on top of the existing `rawText -> normalizedText` model.
- Debug UI now makes these recovered/normalized tokens easier to spot in two ways:
- normalized tokens that differ from raw input are underlined in the `Normalized Text` line
- the interpreter hint block now includes a compact `Recovered/normalized:` summary such as `?NN->599 / EB->BK`
- Added unit coverage to ensure damaged short report/control residues are explicitly marked as normalized-from-raw.

### Key files

- [CwInterpretedToken.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpretedToken.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-23 Offline Fixture Recovery Pressure Diagnostics

- Extended offline fixture evaluation so it now tracks how much the downstream result depended on explicit token normalization rather than raw exact-copy text alone.
- `CwFixtureEvaluationResult` now carries:
- normalized token count
- total token count
- `raw->normalized` token pair list
- a derived `recovery pressure` grade:
- `NONE`
- `LOW`
- `MED`
- `HIGH`
- This is intentionally separate from existing front-end quality and bottleneck codes:
- front-end quality still answers whether acquisition/locking looked healthy
- bottleneck code still answers where the likely failure lives
- recovery pressure now answers whether the result was mostly direct decode, or whether it survived by leaning on best-effort normalization
- The compact and full fixture summaries now expose this extra dimension, so boundary fixtures can say things like:
- front end stayed `GOOD`
- bottleneck is not `SIG`
- but recovery pressure is `MED/HIGH`
- Added regression coverage to verify that a human-style compact report-tail fixture exposes observable normalization pairs such as `5NN->599`.

### Key files

- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Hand-Key Damaged Report Residue Fixtures

- Added two new human-style offline fixtures that push damaged short report residues through the full synthetic audio pipeline instead of only token-level unit tests:
- `human_damaged_report_residue_exchange`
- `human_damaged_ack_report_exchange`
- These cases simulate hand-key/QSB/noise style timing irregularity around:
- `UR?NN B`
- `R?NNB`
- Current observed behavior is now explicitly locked in by regression:
- front-end quality stays healthy and does not collapse into `SIG`
- core QSO semantics still recover correctly
- `?NN->599` normalization is visible in fixture diagnostics
- recovery pressure is `MED`
- the remaining weak point is the trailing handoff/control tail and some callsign extraction detail, so these scenarios currently land in an interpreter-side boundary rather than a front-end failure bucket
- This is useful because it separates:
- “front-end lost the signal”
- from
- “front-end held, report semantics recovered, but late-stage token/handoff detail still drifted”

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Stream-End Gap Flush

- Identified and fixed a structural pipeline issue: before this change, the final pending character at end-of-stream could remain undecoded if no later `TONE_ON` arrived to force the final gap classification.
- Root cause:
- `CwTimingModel` originally only emitted gap events when the next tone-on arrived
- so fixture replay or manual stop could lose the last short tail token even when enough trailing silence already existed in the waveform
- Added `flushPendingGap(...)` to [CwTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- Wired that flush into:
- offline synthetic fixture evaluation
- Debug UI capture stop / fixture completion path
- Practical effect:
- trailing short control tails like final `B/BK` are no longer systematically dropped just because the stream ended
- the two human damaged-report fixtures now upgraded from partial semantic recovery to full text/hint recovery:
- `?NN->599`
- `B->BK`
- they still remain useful interpreter-boundary cases because callsign extraction detail is not yet perfect, but they are no longer missing the final control token due to pipeline shutdown behavior

### Key files

- [CwTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- `peak tone RMS`
- `peak narrowband isolation`
- `locked frame count / ratio`
- `max consecutive locked frames`
- This solves a practical blind spot from the previous round:
- a fixture or live microphone run can decode well in the middle and then fall back to `SEARCH` near the end
- the app now preserves evidence that the front end did in fact achieve a healthy lock earlier in the run
- Debug UI now surfaces these history stats in:
- `Mic Tone Watch`
- signal state detail
- signal health summary
- The health summary can now label a run as `Recovered earlier lock` when the latest frame is weak but the overall run clearly had a usable lock window.
- Offline regression now uses the new history metrics where appropriate, especially for noisy scenarios where the final frame alone is misleading.

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Fixture Evaluation Front-End Awareness

- Promoted the new signal run-history metrics from raw debug numbers into the offline fixture evaluation layer.
- `CwFixtureEvaluator` can now accept a `CwSignalSnapshot` alongside interpreter/QSO snapshots, and `CwFixtureEvaluationResult` stores a compact front-end history summary including:
- final lock state
- peak tone RMS
- peak narrowband isolation
- lock coverage across the run
- best consecutive lock run
- Practical effect:
- fixture diagnostics can now distinguish between:
- true front-end acquisition failure
- cases where the front end had a healthy earlier lock but later drift showed up more in decoder/interpreter/QSO stages
- `likely bottleneck` and `diagnostic notes` are now more context-aware, especially for noisy or end-of-run drop-lock scenarios.
- The evaluation summary text also now prints a dedicated `Front-end history` line when these metrics are available.
- Kept persistence compatibility simple:
- older stored fixture evaluations still load through the legacy constructor path
- new in-memory evaluations immediately benefit from the richer diagnostics without forcing a database schema migration

### Key files

- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)

## 2026-04-22 Front-End Quality Grade

- Refined `CwFixtureEvaluationResult` one step further by collapsing the detailed front-end history into a short quality grade that is easier to scan in fixture history:
- `GOOD`
- `DROP`
- `WEAK`
- `MISS`
- `NA`
- Practical meaning:
- `GOOD`: healthy lock retained through the evaluated end state
- `DROP`: earlier healthy lock existed, but the run ended unlocked
- `WEAK`: some acquisition happened, but it never looked comfortably stable
- `MISS`: no convincing narrow-band lock formed
- `NA`: no front-end history was attached
- This grade is now included in:
- compact fixture summaries used by recent-history UI
- full evaluator summary text
- Existing bottleneck logic remains, but the new grade makes it much faster to visually separate:
- true front-end misses
- late-run dropouts
- non-front-end semantic/interpreter drift after a usable lock window

### Key files

- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)

## 2026-04-22 Fixture-Declared Front-End Expectations

- Started turning `front-end quality` from a post-hoc diagnostic into an explicit part of selected fixture definitions.
- `CwFixtureScenario` now supports an optional `expectedFrontEndQualityCode`, normalized like:
- `GOOD`
- `DROP`
- `WEAK`
- `MISS`
- The Debug UI fixture description now shows this expected front-end grade when a scenario declares one.
- The offline regression path now automatically checks declared front-end quality expectations for those scenarios and fails with a readable summary if the actual grade drifts.
- Current first batch of fixtures with explicit front-end expectations focuses on the most useful signal-side baselines:
- `noisy_report_exchange`
- `nearby_interferer_directed_report`
- `moderate_interferer_directed_report`
- Practical effect:
- we are no longer only saying “this fixture should decode”
- we are also saying “this fixture’s front end should fail / drop / hold lock in this specific way”
- This makes future signal-processor tuning much less ambiguous.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [QsoDraftSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoDraftSnapshot.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)

## 2026-04-21 Callsign Highlight + Quick Apply

- Added visual highlighting for likely callsigns inside interpreter raw/normalized text.
- Added candidate callsign buttons in the QSO editor area.
- Tapping a candidate now fills the remote callsign and immediately applies it into the draft state machine.
- Candidate action works together with manual draft editing instead of bypassing it.
- This is the first usable version of the earlier “highlight suspected callsign” idea.

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)
- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpretedToken.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpretedToken.java)

## 2026-04-21 Formal QSO Editor Screen

- Added a standalone `QsoEditorActivity` as the first non-debug-oriented QSO editing screen.
- The debug page now has an `Open QSO Editor` entry point.
- The formal editor supports:
- loading the current stored draft
- editing core QSO fields
- saving the draft
- confirming a log
- exporting ADIF
- viewing a compact summary of recent confirmed logs
- Extracted shared utilities so export and draft creation are not duplicated:
- `CwAdifFileWriter`
- `QsoDraftFactory`

### Key files

- [QsoEditorActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoEditorActivity.java)
- [activity_qso_editor.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_qso_editor.xml)
- [CwAdifFileWriter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/adif/CwAdifFileWriter.java)
- [QsoDraftFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoDraftFactory.java)
- [AndroidManifest.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/AndroidManifest.xml)

## 2026-04-21 Logbook Screen + Load Back Into Editor

- Added a standalone `QsoLogbookActivity`.
- The formal editor can now open the logbook directly.
- The logbook supports:
- browsing confirmed logs as selectable entries
- showing detail for the selected log
- loading the selected confirmed log back into the active draft
- returning to the formal editor for second-pass correction
- Extended `QsoDraftFactory` so a confirmed log can be converted back into a draft snapshot.

### Key files

- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [activity_qso_logbook.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_qso_logbook.xml)
- [QsoEditorActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoEditorActivity.java)
- [QsoDraftFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoDraftFactory.java)
- [AndroidManifest.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/AndroidManifest.xml)

## 2026-04-21 Logbook Management Actions

- Extended the logbook from read-only browsing to basic management.
- Added per-selected-log actions:
- `Re-edit`
- `Mark Review / Clear Review`
- `Delete Selected Log`
- Added repository support for updating and deleting confirmed logs.
- Confirmed log entries can now be soft-managed before later introducing a richer database-backed logbook.

### Key files

- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [activity_qso_logbook.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_qso_logbook.xml)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [ConfirmedQsoLog.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedQsoLog.java)

## 2026-04-21 Logbook Safety + Time Formatting

- Added a delete confirmation dialog before removing a confirmed log.
- Improved confirmed-log time display from raw ADIF-like values to readable timestamps.
- Reused one shared formatter for logbook and editor confirmed-log summaries.

### Key files

- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [QsoEditorActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoEditorActivity.java)
- [LogDisplayFormatter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LogDisplayFormatter.java)

## 2026-04-21 Confirmed Log Storage Migration

- Migrated `confirmed logs` from `SharedPreferences + JSON` to a local SQLite-backed table.
- Added a dedicated database helper for confirmed-log schema management.
- Added one-time legacy migration:
- old confirmed-log JSON is imported into SQLite on first repository open
- successful migration clears the old JSON blob and records a migrated flag
- Repository external API stays compatible, so current UI layers did not need large rewrites.
- Current scope:
- `confirmed logs` are now stored in SQLite
- `draft` remains in `SharedPreferences` for now

### Key files

- [ConfirmedLogDatabaseHelper.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogDatabaseHelper.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)

## 2026-04-21 Logbook Filtering + Unified Query Interface

- Added confirmed-log filtering by callsign substring.
- Added `review only` filtering in the logbook.
- Added common sort options:
- newest first
- oldest first
- callsign A-Z
- callsign Z-A
- Added repository-level confirmed-log query model instead of only exposing full-list reads.
- Added repository overview snapshot so Home and QSO Editor can read shared summary data through one interface.

### Key files

- [ConfirmedLogQuery.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogQuery.java)
- [AppOverviewSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/AppOverviewSnapshot.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [activity_qso_logbook.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_qso_logbook.xml)
- [HomeActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/home/HomeActivity.java)
- [QsoEditorActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoEditorActivity.java)
- [ConfirmedQsoLog.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedQsoLog.java)

## 2026-04-21 Home Navigation Entry

- Added a dedicated `HomeActivity` as the normal launcher entry.
- Launcher intent is no longer bound to the debug screen.
- Home now links the three main working areas:
- `Debug`
- `QSO Editor`
- `Logbook`
- Home also surfaces lightweight operating context:
- current draft summary
- confirmed-log summary

### Key files

- [HomeActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/home/HomeActivity.java)
- [activity_home.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_home.xml)
- [AndroidManifest.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/AndroidManifest.xml)

## 2026-04-21 Draft Storage Migration

- Migrated the active `draft` from `SharedPreferences` into SQLite as well.
- Confirmed logs and draft now live under one local database helper.
- Added an `active_draft` table with a single-row style storage model.
- Added one-time legacy migration for the old draft JSON blob from `SharedPreferences`.
- Repository API remains compatible, so existing UI pages continue to work unchanged.

### Key files

- [ConfirmedLogDatabaseHelper.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogDatabaseHelper.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)

## 2026-04-21 Logbook Date Range Filtering

- Extended confirmed-log querying with optional QSO date range fields:
- `fromQsoDateUtc`
- `toQsoDateUtc`
- The logbook filter panel now supports date-range filtering in `YYYYMMDD` form.
- Input also tolerates separators such as `-` or `/` as long as the final value is a valid 8-digit date.
- Invalid ranges are rejected in the UI before query execution.
- Repository SQL filtering continues to work directly against `qso_date_utc`, so no schema migration was needed.

### Key files

- [ConfirmedLogQuery.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogQuery.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [activity_qso_logbook.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_qso_logbook.xml)

## 2026-04-21 Decoder Fixture Baseline v0

- Added a deterministic synthetic CW replay source for decoder evaluation work.
- The debug page now supports a new input source:
- `Synthetic Fixture`
- Added fixture scenario selection in the debug UI so the full chain can be replayed with repeatable sample profiles.
- Initial built-in fixture scenarios:
- clean CQ baseline
- noisy report exchange
- QSB / partial-callsign style sample
- Added a small fixture metadata layer so future real recordings and benchmark cases can follow the same shape.
- Current fixture playback feeds the existing live decode chain:
- `AudioFrame -> CwSignalProcessor -> CwTimingModel -> CwDecoder -> CwInterpreter -> QsoStateMachine`
- This is the first practical step toward an actual decoder regression / evaluation harness.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [InputSourceOption.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputSourceOption.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)

## 2026-04-21 Fixture Evaluation + Signal/Timing Robustness v2

- Extended fixture scenarios with built-in expectations:
- expected normalized text
- expected callsign candidates
- expected interpretation hints
- Added a lightweight fixture evaluator that compares replay output against scenario expectations and produces a pass/check style summary.
- The debug page now shows fixture evaluation output directly after a synthetic replay completes.
- Improved signal/timing core for the next decoder-accuracy round:
- audio frame size reduced from `1024` to `512` samples for finer timing resolution
- `CwSignalProcessor` now keeps both noise-floor and signal-floor estimates
- attack / release thresholds are now adaptive instead of one fixed margin
- `CwTimingModel` now maintains richer estimates for dot / dash / intra-gap timing
- gap timing can now feed back into dot estimation to stabilize WPM tracking
- Debug signal/timing panels now expose the richer internal state.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [CwTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [CwTimingSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingSnapshot.java)
- [MicrophoneRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/MicrophoneRxAudioSource.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)

## 2026-04-21 Fixture History + Failure Breakdown + Interpreter v2

- Fixture evaluation results are now persisted into the local SQLite database instead of only living in memory.
- Added a dedicated `fixture_evaluations` table under the existing app database helper.
- The debug page now shows:
- latest fixture evaluation summary
- recent fixture evaluation history
- more explicit failure reasons when a replay does not meet baseline
- Evaluation output is now more actionable:
- missing text tokens
- missing callsigns
- missing hints
- explicit failure-reason strings
- Interpreter handling was expanded for common CW shorthand and repeat-request style phrases:
- `PLS/PSE -> PLEASE`
- `TU/TNX -> THANKS`
- `CALL SIGN / CALLING SIGN -> CALLSIGN`
- partial callsign / uncertain copy hinting when `?` is present
- This reduces cases where the semantic meaning was roughly right but normalization/hint extraction lagged behind.

### Key files

- [ConfirmedLogDatabaseHelper.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogDatabaseHelper.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpretedToken.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpretedToken.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-21 Partial Callsign Merge + Confirmation Cycle

- Callsign handling now promotes one `primary` candidate instead of relying on the last raw callsign token.
- Multiple compatible callsign copies can now be merged when they differ only by uncertain characters such as `?`.
- Candidate ranking now prefers:
- more complete callsigns
- more recent confirming copies
- This improves flows such as:
- `BI9??Z` followed later by `BI9CLZ`
- repeat / `AGN` / `CALL AGAIN` style confirmation rounds
- `CwInterpreterSnapshot` now exposes `primaryCallsignCandidate` alongside the merged candidate list.
- `QsoStateMachine` now consumes the primary candidate and avoids downgrading a more complete callsign back to a weaker partial copy.
- Added a dedicated fixture scenario for partial-to-full callsign resolution so this behavior is covered by the replay/evaluation path.
- Debug UI now shows the primary callsign candidate explicitly.

### Key files

- [CwInterpreterSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterSnapshot.java)
- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-21 DE Context Roles + Primary Callsign Scoring

- `CwInterpreter` now keeps a lightweight callsign memory and uses `DE` context to prefer the speaking station as the primary candidate when possible.
- `QsoStateMachine` now parses `CALLA DE CALLB` style structures more intentionally:
- `DE` before-call can be used as the addressed/local-side callsign candidate
- `DE` after-call is preferred as the remote/speaking station candidate
- This improves directed exchange handling such as:
- `BI9CLT DE BG7YOZ UR 5NN BK`
- Fixture evaluation now includes a separate primary-callsign score (`P`) in addition to:
- text recall (`T`)
- callsign recall (`C`)
- hint recall (`H`)
- Added a dedicated directed-report fixture to exercise this context-sensitive role parsing.

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [ConfirmedLogDatabaseHelper.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogDatabaseHelper.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)

## 2026-04-21 Closing Semantics Fixture Coverage

- Extended fixture expectations from interpreter-only checks into QSO-state checks as well.
- Fixture evaluation now also scores `QSO semantics` (`Q`) for:
- expected `QsoPhase`
- expected `RST sent`
- expected `RST rcvd`
- Added a dedicated closing scenario:
- `BI9CLT DE BG7YOZ R 5NN TU 73 BK`
- This specifically covers:
- report acknowledgement / return report
- `TU` -> `THANKS`
- `73` closing
- `BK` handoff
- completed-phase detection
- Fixture evaluation history persistence was upgraded so these QSO-semantic results are kept in SQLite alongside the earlier `P/T/C/H` scores.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [ConfirmedLogDatabaseHelper.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedLogDatabaseHelper.java)

## 2026-04-21 QRZ Loop + Repeat Request Phase Fix

- Fixed a QSO-phase bug where `UR` by itself could incorrectly push the state machine into `REPORT_EXCHANGE`.
- Repeat / clarification flows such as:
- `BI9??Z UR CALL AGAIN PSE K`
- are now kept in `REPLY_DETECTED` instead of being mistaken for a real report exchange.
- `QRZ` loops are now preserved as calling flow:
- `QRZ QRZ DE BG7YOZ BG7YOZ K`
- now remains in `CALLING_CQ` instead of drifting into reply-detected logic just because a callsign is present.
- Added a dedicated `QRZ Loop` fixture so this behavior is regression-covered alongside the earlier partial-copy and closing fixtures.
- Added local JVM unit tests for the three highest-value conversation semantics:
- partial repeat / clarification request
- repeated `QRZ` solicitation
- `R 5NN TU 73 BK` completed closing flow

### Key files

- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-21 Multi-Round Callsign Convergence Tests

- Added explicit local JVM coverage for multi-round CW dialogue instead of only single-message semantics.
- Covered a practical clarification flow:
- `BI9??Z UR CALL AGAIN PSE K`
- followed by:
- `BI9CLZ DE BG7YOZ UR 5NN BK`
- Expected behavior is now locked in:
- addressed/local callsign converges from partial `BI9??Z` to full `BI9CLZ`
- remote/speaking callsign becomes `BG7YOZ`
- received report is captured as `599`
- phase advances into `REPORT_EXCHANGE`
- Also added regression coverage to ensure a later weaker partial copy does not downgrade an already-known full remote callsign.

### Key files

- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)

## 2026-04-22 Multi-Part Fixture Replay

- Extended synthetic fixture scenarios so one fixture can now replay multiple CW message parts in sequence within a single run.
- This keeps one interpreter / QSO state machine session alive across the whole replay, which is closer to a real multi-round CW contact.
- Added per-scenario metadata for:
- `messageParts`
- `interMessageGapMs`
- Existing single-message fixtures remain compatible and automatically behave as one-part scripts.
- Added the first multi-part regression fixture:
- `multi_round_callsign_resolution`
- Scripted flow:
- `BI9??Z UR CALL AGAIN PSE K`
- `BI9CLZ DE BG7YOZ UR 5NN BK`
- This now gives the debug replay/evaluation path direct coverage for:
- partial addressed callsign
- clarification request
- later full callsign resolution
- directed report exchange after confirmation
- Updated the debug page status text so synthetic fixtures show script-part count and inter-part gap when applicable.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Full Multi-Round QSO Script

- Added a longer built-in multi-part fixture that covers a more complete remote-side CW flow:
- clarification request
- full callsign confirmation
- directed report
- final `TU 73 BK` closing
- New fixture:
- `multi_round_full_qso`
- Scripted parts:
- `BI9??Z UR CALL AGAIN PSE K`
- `BI9CLZ DE BG7YOZ UR 5NN BK`
- `BI9CLZ DE BG7YOZ TU 73 BK`
- Expected end state is now regression-covered as:
- `QsoPhase.COMPLETED`
- local/addressed callsign resolved to `BI9CLZ`
- remote/speaking callsign held as `BG7YOZ`
- received report held as `599`
- Added matching local JVM coverage for the same three-round flow.

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Human-Style `?` + `KN` Semantics

- Added normalization for common human-style suffixed punctuation on known CW tokens:
- `QRZ? -> QRZ`
- `AGN? -> AGAIN`
- `PSE? -> PLEASE`
- Added `KN` as a first-class control/handoff token alongside existing `K` / `BK`.
- Interpreter hint extraction now treats `KN` as `Turn handoff / over`.
- QSO phase logic now also recognizes `KN` as a handoff/end-of-turn control token.
- Added built-in fixtures for these more human-style patterns:
- `questioned_qrz_kn`
- `questioned_clarification_kn`
- Added matching local JVM coverage for:
- `QRZ? DE BG7YOZ KN`
- `BI9??Z AGN? CALLSIGN PSE KN`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Glued Callsign / Sticky Spacing

- Added a first pre-tokenization heuristic for sticky operator spacing and glued repeated tokens.
- Current scope covers patterns such as:
- `CQCQCQ`
- `BI9CLTBI9CLTBI 9CL T`
- The interpreter now:
- expands repeated glued `CQ` runs into separate `CQ` tokens
- tries to merge suspicious alphanumeric fragment runs
- splits exact repeated callsign runs back into repeated full callsigns when possible
- Added built-in fixture coverage:
- `glued_callsign_run`
- Added matching local JVM coverage for:
- `CQCQCQ DE BI9CLTBI9CLTBI 9CL T`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Irregular Timing Fixture v1

- Extended synthetic fixture scenarios with timing-profile controls so replay can now model less machine-perfect CW timing.
- Added per-scenario support for:
- `timingJitterDepth`
- `dotSwingDepth`
- `timingJitterDepth` adds deterministic duration variance to both tones and gaps.
- `dotSwingDepth` adds alternating short/long dot behavior to make replay feel more hand-key / bug-like.
- Existing fixtures remain compatible and default to `0` for both timing parameters.
- Added two initial timing-focused built-in fixtures:
- `irregular_hand_key_cq`
- `irregular_bug_qsb_report`
- Debug fixture status text now also shows timing jitter and dot swing percentages when present.

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Common Glue / Split Callsign Cases

- Expanded interpreter pre-tokenization heuristics for several common real-operator glue/split patterns beyond the earlier repeated-callsign case.
- Newly handled examples include:
- `DEBI9CLT`
- `BI9 CLT`
- `BG7 YOZ`
- The interpreter now:
- splits `DE`-prefixed and `QRZ`-prefixed callsign tokens when the suffix is a valid callsign
- merges short adjacent alphanumeric callsign fragments into one callsign when the combined result is valid
- keeps the earlier repeated glued-callsign recovery path for patterns such as `BI9CLTBI9CLT...`
- Added built-in fixtures for these representative cases:
- `keyword_prefixed_callsign`
- `spaced_callsign_fragments`
- `spaced_directed_exchange`
- Added matching local JVM coverage for all three patterns.

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Compound Keyword + Callsign Glue Cases

- Extended interpreter pre-tokenization so it can split several additional compact real-operator patterns where keywords and callsigns collapse into one token.
- Newly handled examples include:
- `CQDEBI9CLT`
- `BI9CLTDEBG7YOZ`
- `QRZDEBG7YOZ`
- The interpreter now:
- recursively splits compact `CQ / DE / QRZ` chains when the remainder is still a plausible keyword/callsign segment
- handles `DE` or `QRZ` appearing between two strong segments instead of only at the very front of a token
- keeps the earlier conservative behavior so ordinary callsigns are not aggressively over-split
- Added built-in fixtures for these representative cases:
- `compact_cq_de_chain`
- `glued_de_between_callsigns`
- `qrz_de_compound`
- Added matching local JVM coverage for all three patterns.

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Irregular Timing Fixture v2

- Extended fixture timing from one global profile per scenario to optional per-part timing profiles.
- `CwFixtureScenario` now supports `PartTimingProfile` entries that can override or shape each message part with:
- `wpmScale`
- per-part timing jitter
- per-part dot swing
- dot-only jitter boost
- letter-gap / word-gap scaling
- deterministic extra pauses every N characters
- `SyntheticFixtureRxAudioSource` now applies those profiles while rendering each part, so a single replay can sound ragged in the clarification round, steadier in the report round, and tighter in the closing round.
- Added a concise timing-profile summary in the Debug page fixture description so we can see per-part rhythm shape without opening code.
- Added two built-in timing-v2 scenarios:
- `ragged_resolution_then_steady_report`
- `human_op_timing_full_qso`
- Added local JVM coverage for the new profile layer itself:
- profile summary rendering
- missing-part fallback to default timing profile

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)

## 2026-04-22 Irregular Timing Fixture v2.1

- Extended `PartTimingProfile` further so one message part can now also model:
- intra-part WPM drift from a starting rhythm to a later rhythm
- stretched handoff pause before `K / KN / BK`
- `SyntheticFixtureRxAudioSource` now renders fixture text word-by-word instead of treating the whole message as one raw character stream.
- That change makes it possible to shape:
- letter gaps inside a word
- word gaps between words
- special front-gap behavior before handoff/control words
- Added a focused timing fixture:
- `drifting_handoff_report`
- Updated the higher-value timing-v2 fixtures so they now use:
- gradual speed drift inside a part
- longer pause bias before the handoff/control word where appropriate
- Extended local JVM coverage so timing-profile summaries now show:
- drift target WPM
- handoff gap scale

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)

## 2026-04-22 Fixture Evaluation Diagnostics

- Upgraded fixture evaluation output from pure score reporting to a first-pass failure-localization view.
- `CwFixtureEvaluationResult` now derives a likely bottleneck category from the existing scores:
- `OK`
- `RUN`
- `SIG`
- `DEC`
- `INT`
- `QSO`
- `MIX`
- These labels are intentionally coarse and heuristic, but they are enough to tell whether a miss looks more like:
- front-end signal/timing loss
- timing/decoder drift
- interpreter/callsign extraction loss
- QSO semantic/state mapping drift
- Compact fixture history entries now include the diagnostic code directly, and full summaries now include:
- likely bottleneck label
- short diagnostic notes
- Added local JVM coverage for:
- front-end style collapse -> `SIG`
- late semantic drift with intact text -> `QSO`

### Key files

- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)

## 2026-04-22 Front-End Timing Recovery + Offline Fixture Regression

- Added a true offline fixture regression path that runs:
- synthetic fixture frames
- `CwSignalProcessor`
- `CwTimingModel`
- `CwDecoder`
- `CwInterpreter`
- `QsoStateMachine`
- and then evaluates the final result with `CwFixtureEvaluator`
- This gives us a real JVM regression checkpoint for human-style timing fixtures instead of relying only on the Debug UI.
- Used that regression path to inspect the new human-timing fixtures and found the first dominant issue was front-end timing recovery:
- dot estimate had been drifting far too slow
- short gaps were being over-consumed at 512-sample framing
- Applied a focused front-end correction:
- reduced audio frame size from `512` to `256`
- reduced `TONE_OFF_HANG_MS` from `18` to `8`
- Result:
- timing estimate on the hard fixtures moved back near the intended range
- offline regression fixtures no longer collapse into the old catastrophic front-end state
- Updated the diagnostic heuristic so partially recovered human-timing fixtures are no longer over-labeled as `SIG` once QSO/text recovery is clearly underway.
- Added two offline regression cases as the new practical baseline:
- `drifting_handoff_report`
- `human_op_timing_full_qso`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [MicrophoneRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/MicrophoneRxAudioSource.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)

## 2026-04-22 Compact Report / Handoff Glue Recovery

- Extended interpreter compound-token recovery beyond callsign/keyword glue into compact directed-exchange chains.
- Newly covered chain patterns include representative cases such as:
- `UR5NNBK`
- `DEBG7YOZUR5NNBK`
- The interpreter now prefers bridge splits like `UR / BK / KN` ahead of later `5NN / 599` report fragments when recovering a compact report chain.
- This avoids mis-parsing patterns such as:
- `BG7YOZUR5NNBK`
- into a weaker pseudo-callsign like `BG7YOZUR`
- Added local JVM regression coverage for:
- glued report + handoff recovery
- compact full directed-exchange chain recovery
- Added a built-in fixture for the same family:
- `compact_report_handoff_chain`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)

## 2026-04-22 Tail Callsign + Compact Closing Chain Coverage

- Added another batch of compact real-operator glue scenarios where a report/closing chain is immediately followed by a repeated callsign tail.
- Newly covered semantics examples include:
- `BI9CLT DE BG7YOZ UR5NNBKBI9CLT`
- `BI9CLT DE BG7YOZ R5NNBKBI9CLT`
- `BI9CLT DE BG7YOZ TU73BKBI9CLT`
- `BI9CLT DEBG7YOZTU73BK`
- Tightened interpreter bridge splitting so `TU` / `TNX` are considered before later `73` splits inside compact tokens.
- This fixes cases where:
- `DEBG7YOZTU73BK`
- could previously drift into a false callsign like `BG7YOZTU`.
- Tightened QSO report extraction so a trailing standalone `599` is no longer auto-counted as a received report when the same line already contains an explicit `R 599` sent-report acknowledgement.
- Added built-in fixture entries for the same family, so these patterns are now available in the debug/evaluation workflow instead of existing only as unit tests.
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)

## 2026-04-22 Human-Timing Compact Chain Regression

- Added two harder single-part fixture scenarios that combine:
- compact sticky operator chains
- moderate QSB
- background noise
- deterministic hand-key style timing drift
- stretched handoff pauses
- New fixtures:
- `human_compact_report_tail_callsign`
- `human_compact_de_closing_chain`
- Added offline JVM regression coverage for both fixtures in the full pipeline:
- synthetic frames
- signal processor
- timing model
- decoder
- interpreter
- QSO state machine
- These two fixtures are intentionally calibrated as “hard but still useful” rather than exact-pass baselines.
- Current locked-in expectation is:
- no catastrophic `RUN` / `SIG` front-end collapse
- QSO semantics must still be recovered
- text/callsign recovery may still degrade, which makes them good boundary cases for later interpreter and decoder work
- Current observed behavior:
- `human_compact_report_tail_callsign` now preserves report semantics and operating-intent hints, but still loses parts of both callsigns
- `human_compact_de_closing_chain` now preserves closing-phase semantics, but still drops part of the addressed callsign and handoff token detail
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Partial Callsign Recovery v1

- Extended interpreter callsign handling for two practical partial-copy cases that started showing up in the newer compact human-timing fixtures:
- suffix fragments like `?YOZ` near `DE`
- prefix fragments like `BI9CL` repeated after a compact handoff/closing tail
- The interpreter now treats some context-anchored partial tokens as callsign candidates when they appear in typical callsign slots, such as:
- immediately before or after `DE`
- immediately after `BK / KN / K`
- immediately before `UR / R / THANKS / 73`
- Added anchored callsign merging so a shorter prefix/suffix fragment can collapse into a fuller callsign candidate when they are clearly compatible.
- This locks in cases like:
- `BI9CLT ... BI9CL`
- as one callsign candidate instead of two conflicting ones.
- Added remembered-speaker upgrade coverage so an earlier full remote callsign can upgrade a later contextual suffix fragment such as:
- `BG7YOZ`
- followed by
- `?YOZ`
- Added dedicated interpreter unit coverage for these behaviors.
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-22 Multi-Interferer Fixture Support

- Extended the synthetic fixture model beyond a single continuous interferer so crowded-band stress can now include multiple simultaneous off-target carriers.
- `CwFixtureScenario` now supports optional `additional interferers` while keeping all older single-interferer constructors intact.
- `SyntheticFixtureRxAudioSource` now renders:
- the original primary interferer path
- plus any additional continuous interferers, each with its own frequency, amplitude, and optional drift
- Debug UI selected-source summary now also shows those extra interferers, so live fixture replay is easier to interpret without opening code.
- Added two new offline fixture tiers:
- `moderate_dual_interferer_directed_report`
- intentionally softer two-carrier baseline
- currently lands in a usable `GOOD` front-end state with clean release, while downstream text/hint loss remains the more visible stress point
- `dual_interferer_directed_report`
- intentionally stronger crowded-band boundary
- currently exposes explicit `WRONG / TRK` wrong-tone acquisition risk under two-carrier pressure
- Practical value:
- we now have a better synthetic approximation of real crowded CW sub-band conditions
- and we can separate:
- a dual-carrier baseline that should still be workable
- from a dual-carrier boundary that is allowed to demonstrate tracker failure
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Bursty Boundary Fixture Split

- Took the new burst-capable interferer framework one step further by explicitly splitting bursty crowded-band stress into:
- a workable baseline
- an allowed boundary failure
- The existing `bursty_interferer_directed_report` remains the softer baseline.
- Added a stronger boundary partner:
- `bursty_dual_interferer_boundary_report`
- This fixture combines:
- intermittent nearby occupancy
- plus a second drifting burst carrier
- so the stress is no longer just “carrier is always there”, but “carrier pressure appears and disappears while the local spectral picture is moving”.
- Current observed locked-in behavior for this new boundary:
- front-end quality `WRONG`
- likely bottleneck `TRK`
- meaning the tracker can still form a strong stable lock, but on the wrong tone under bursty multi-source pressure
- Practical value:
- burst-style occupancy is now also split into baseline vs boundary, instead of only continuous-carrier stress having that structure
- so later front-end tuning can target:
- better resilience to intermittent adjacent activity
- without losing sight of which scenarios are intentionally still allowed to fail today
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Bursty Interferer Fixture Support

- Extended `ContinuousInterfererProfile` one step further so an added interferer can now be either:
- continuous
- drifting
- or bursty / duty-cycled
- New optional burst controls are now supported per added interferer:
- `burstOnMs`
- `burstOffMs`
- `burstOffsetMs`
- `SyntheticFixtureRxAudioSource` now applies a lightweight gated activity envelope to such interferers, with a small edge ramp so burst transitions do not devolve into purely hard rectangular switching.
- Debug UI selected-source summary now shows burst settings for extra interferers, so synthetic replay inspection can distinguish:
- continuous crowded-band occupancy
- intermittent / pulse-like occupancy
- Added and locked a first burst-oriented baseline fixture:
- `bursty_interferer_directed_report`
- This fixture keeps the QSO workable under moderate bursty off-target occupancy rather than only under continuous-carrier pressure.
- Practical value:
- we now have a synthetic middle ground between:
- easy static baselines
- and always-on crowded-band stress
- which is closer to real intermittent adjacent activity on a busy CW segment
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Partial Callsign Recovery v2

- Extended interpreter memory from one generic remembered primary callsign into role-aware contextual memory:
- remembered addressed callsign
- remembered speaker callsign
- This lets later compact/partial traffic reuse the earlier `DE`-context role split instead of only recovering whichever callsign happened to be primary.
- Added contextual candidate recovery so a later partial token can be upgraded directly into the candidate list when it is compatible with remembered role context.
- Practical target cases include:
- `BG7YOZ` followed later by `?YOZ`
- `BI9CLT` followed later by `BI9CL`
- Candidate lists now prefer the recovered full callsign instead of preserving both the partial and the full form as competing entries.
- Added interpreter regression coverage for:
- remembered speaker suffix recovery
- remembered addressed prefix recovery
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-22 Callsign Contamination Repair + Follow-up Fixture

- Extended callsign recovery one step further for compact dirty strings where a callsign fragment is polluted by nearby report/control text, for example patterns like:
- `?YOZUR5...`
- The interpreter now derives candidate repair variants by splitting a suspicious callsign candidate around compact bridge keywords and retrying recovery against remembered role callsigns.
- Added dedicated interpreter coverage for this contaminated-fragment recovery path.
- Added a new multi-part offline fixture:
- `human_compact_report_tail_followup`
- This fixture is designed to verify a more practical workflow:
- first part establishes `DE`-context roles in a cleaner report exchange
- second part becomes partial and compact
- current regression goal is not exact callsign recall yet
- current locked-in expectation is:
- no front-end collapse
- `REPORT_EXCHANGE` semantics preserved
- report extraction preserved
- intent hints such as partial-copy resolution remain active
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Callsign Contamination De-noise v2

- Tightened unequal-length callsign merge so we no longer over-prefer the shorter side just because the extra edge contains `?`.
- This specifically fixes the regression where valid partial callsigns such as:
- `BI9??Z`
- could collapse into an over-short candidate like:
- `BI9?`
- The interpreter now only prefers the shorter anchored candidate when it looks like a trusted clean callsign and the extra edge is strongly contamination-like, such as:
- short `?` noise
- compact control/report residue
- duplicated leading or trailing edge characters from sticky send behavior
- Added token-level de-noise for two common pollution classes before they reach callsign recovery:
- `BK??`-style control residue
- `5CB`-style short report residue when it appears in report/handoff context
- Added focused interpreter coverage for mixed polluted traffic like:
- `?? DE ?BBG7YOZ UR 5CB BK??`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Target Tone Tracking v1

- Upgraded the front-end signal processor from pure broadband RMS thresholding to a hybrid mode with lightweight narrowband tone tracking.
- `CwSignalProcessor` now:
- keeps a preferred CW audio pitch center, currently defaulting to `650 Hz`
- scans a bounded frequency window around that preferred pitch with a Goertzel-style detector
- tracks the strongest in-window tone and derives a narrowband tone RMS estimate
- uses that tone RMS as the primary detection level when the target tone is considered locked
- This is intentionally still `v1`, not a full skimmer-style spectral front end, but it gives the later timing/decoder layers cleaner tone on/off transitions when broadband energy is misleading.
- Added extra signal snapshot/debug state so the app can now show:
- preferred tone frequency
- tracked tone frequency
- lock/search state
- tone RMS
- tone dominance ratio against total frame RMS
- Added focused JVM coverage for:
- locking near a `670 Hz` target tone after lead-in silence
- resisting a stronger out-of-band tone while staying on the preferred in-window target
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Preferred Tone Control In Debug UI

- Promoted the new target-tone preference from hardcoded processor state into a real debug control flow.
- The Debug page now includes:
- a numeric preferred-tone input in Hz
- an `Apply Tone` action
- a `Use Fixture Tone` shortcut that copies the selected synthetic fixture's configured tone frequency into the live processor
- Preferred tone is now persisted locally across app restarts through a lightweight debug preference.
- The selected source summary now also shows the currently active preferred tone so fixture/microphone experiments are easier to read at a glance.
- Added focused JVM coverage to lock the supported tone window behavior itself:
- values above the current search window clamp to `850 Hz`
- values below the current search window clamp to `450 Hz`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest assembleDebug`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [activity_input_debug.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_input_debug.xml)
- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)

## 2026-04-22 Interferer-Aware Fixture v1

- Extended the synthetic fixture model so a scenario can now optionally include a second continuous interferer tone in addition to:
- the intended CW tone
- background noise
- QSB
- timing drift
- `SyntheticFixtureRxAudioSource` now mixes that second carrier directly into rendered frames, which gives us a practical way to exercise adjacent-channel / nearby-carrier behavior in offline regression instead of only hand-waving about it.
- Added a new built-in fixture:
- `nearby_interferer_directed_report`
- This scenario is intentionally useful as a boundary probe, not a solved baseline:
- current front-end can still identify the preferred tone region
- but a nearby continuous interferer can still collapse end-to-end decoding badly enough that the diagnostic lands in `SIG`
- This is now a locked-in known gap rather than an anecdotal suspicion.
- Tightened target-tone scanning slightly so in-window candidates are weighted by proximity to the preferred CW pitch instead of following the strongest frequency blindly.
- Added JVM coverage for:
- fixture interferer-field plumbing
- preferred-frequency bias beating a stronger but farther in-window tone in direct signal tests
- the new interferer fixture's current diagnostic classification as a front-end boundary case
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Unknown Decode Echo + Interferer Gate v2

- Locked the transcript rule that real Morse `?` and decode-failure placeholder must be different concepts.
- `CwDecoder` now emits a stable Unicode placeholder for unknown characters instead of conflating them with a real question mark.
- `CwDecodeEvent` already preserves the raw dot-dash source sequence, and the test suite now explicitly locks:
- `..--.. -> ?`
- unknown sequence such as `.-.- -> □`
- The interpreter now preserves `□` in visible transcript flow but treats it like `?` for semantic recovery, so callsign repair and QSO hint extraction can still work across undecodable characters.
- `CwSignalProcessor` gained a narrowband-vs-broadband dominance gate in front of tone attack, which helps reject continuous interferers without throwing away legitimate target-tone energy.
- The offline fixture pipeline now aligns preferred tone with each fixture's configured tone frequency, so regression runs measure anti-interference behavior instead of an avoidable default-tone mismatch.
- Split interferer regression into two tiers:
- `nearby_interferer_directed_report`
- still a harder nearby-carrier stress sample, but no longer expected to collapse into `SIG`
- `moderate_interferer_directed_report`
- tuned as a softer nearby-interferer baseline that should stay out of `RUN` / `SIG` and preserve usable text + semantic recovery
- Added focused JVM coverage for:
- continuous out-of-band carrier should not false-trigger tone activity
- moderate interferer should still allow target-tone activation
- `□` should behave like an uncertainty marker in interpreter semantics
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md)
- [CwDecoder.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/decoder/CwDecoder.java)
- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-22 Callsign Contamination Cleanup v3

- Extended contextual callsign recovery so very short uncertain fragments like `?LT` can still enter remembered-callsign repair when they appear in a strong `DE`/handoff context.
- Added remembered-callsign cleanup for two common one-edge contamination patterns seen in noisy/interfered traffic:
- leading sticky residue such as `HBG7YOZ`
- trailing sticky residue such as `BG7YOZU`
- The interpreter now prefers the remembered clean callsign for primary selection when the contextual speaker token is just a contaminated edge-wrap of that remembered callsign.
- Candidate ranking was tightened so a recovered clean callsign can stay visible in the candidate list instead of being merged away again by a one-character edge contamination variant.
- This specifically improves the nearby/moderate interferer offline fixtures, where the end-to-end pipeline may still lose the beginning or ending edge of a callsign, but the interpreter can now preserve:
- `BG7YOZ`
- instead of only polluted variants like:
- `HBG7YOZ`
- `BG7YOZU`
- `?LT`
- Added focused JVM coverage for:
- `?LT -> BI9CLT` remembered addressed recovery
- `HBG7YOZ -> BG7YOZ` remembered speaker cleanup
- `BG7YOZU -> BG7YOZ` remembered speaker cleanup
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Remembered Callsign De-noise Stabilization

- Finished the follow-up fix for the new remembered-callsign cleanup path.
- The actual stable rule is now:
- keep generic anchored merge conservative
- only prefer the clean remembered callsign over a polluted edge-wrap inside remembered-context recovery paths
- This avoids a regression where normal partial candidates like `BI9CL` or `?YOZ` could get over-collapsed by an overly broad merge rule.
- Current remembered-context behavior now holds for all of these cases:
- `?LT -> BI9CLT`
- `HBG7YOZ -> BG7YOZ`
- `BG7YOZU -> BG7YOZ`
- Full validation status at this checkpoint:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-22 Human-Timing Regression Baselines v2

- Added two more offline human-style timing fixtures so regression coverage now extends beyond plain `UR 5NN BK` into:
- ragged clarification traffic
- compact ack-plus-closing traffic
- New fixtures:
- `human_ragged_clarification_report`
- `human_compact_ack_closing_chain`
- These are intentionally calibrated as semantic baselines, not exact-text goldens.
- Current locked-in expectation for both is:
- the front end must stay out of `RUN` / `SIG`
- QSO semantics must remain correct
- text and hint recovery should stay usable, even if some edge tokens still degrade
- Current observed boundary notes:
- `human_ragged_clarification_report` reliably preserves `REPORT_EXCHANGE`, `RST 599`, and both main callsigns, but still tends to lose some first-round partial-copy detail and late handoff tokens
- `human_compact_ack_closing_chain` reliably preserves `COMPLETED` plus sent-report semantics, but still degrades station-identification and `73/BK` edge detail enough that callsign extraction remains a known weak point
- Added focused JVM regression coverage and re-verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Interpreter Edge Recovery v4

- Tightened the interpreter around two concrete weak spots exposed by the newer human-timing baselines:
- compact callsign pollution from ack/report residue
- ragged clarification traffic where the partial callsign is immediately followed by `AGN/PSE`
- Added compact-residue cleanup so a trusted clean callsign candidate now wins over polluted variants such as:
- `BG7YOZR5`
- instead of letting the suffix `R5` keep the longer dirty token on top
- Extended contextual partial-callsign detection so leading fragments like:
- `H??Z AGN PSE K`
- can still enter the callsign/clarification path even without a nearby `DE`
- Added semantic normalization for closing residue tokens like:
- `73B`
- `?3B`
- so they contribute `73 closing` semantics instead of polluting callsign candidates
- Added focused interpreter regression coverage for all three cases and re-verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 DE-Context Recovery v5

- Added a targeted recovery rule for degraded tokens that end with `DE` and are immediately followed by a likely speaker callsign token.
- Practical effect:
- strings like `?DE BG7YOZ ...`
- can now be re-split back into a usable `DE` context instead of losing the station-identification boundary entirely
- This improves later contextual role recovery for:
- speaker callsign extraction
- station callsign carry-forward across multi-round QSO state
- Added focused regression coverage for:
- interpreter-level `?DE BG7YOZ ...` recovery
- QSO-level follow-up where an earlier clean round established `BI9CLT`, and a later compact ack/closing round starts with a degraded `?DE`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Control-Prefixed Station Recovery v6

- Extended the new trailing-`DE` recovery path so it can also peel off compact control residue when the station callsign is glued in front of `DE`.
- Practical target patterns now covered:
- `KBI9CLTDE BG7YOZ ...`
- `BKBI9CLTDE BG7YOZ ...`
- The interpreter now restores these into a usable control-plus-identification structure instead of treating the whole prefix as one polluted callsign token.
- This keeps:
- station callsign extraction
- `DE` role context
- closing / completion semantics
- stable in compact human-style ack/closing traffic
- Added focused regression coverage at both levels:
- interpreter-level compact token recovery
- QSO-level completed/closing state preservation
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Fully-Glued Compact Chain Recovery v7

- Extended compact-chain recovery one step further for the most extreme single-token pattern in this family:
- `BKBI9CLTDEBG7YOZR5NNTU73BK`
- Added two narrow parsing improvements to support this without reopening earlier regressions:
- bare ack-chain splitting for tokens like `R5NN...`
- compact `callsign + R + report` cluster splitting for fragments like `BG7YOZR5NN`
- This allows the interpreter/QSO stack to recover:
- control handoff
- station identification
- speaker callsign
- sent report semantics
- closing / 73 semantics
- from one fully glued compact token instead of requiring spaces or multi-token boundaries
- Added focused regression coverage for:
- interpreter-level callsign/hint recovery
- QSO-level `COMPLETED` phase + `RST sent` preservation
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)

## 2026-04-22 Fully-Glued Offline Fixture Baseline

- Promoted the new fully-glued compact ack/closing pattern into the offline audio fixture library:
- `fully_glued_ack_closing_chain`
- This gives us an end-to-end regression path for:
- synthetic audio frames
- signal processor
- timing model
- decoder
- interpreter
- QSO state machine
- Current status of this fixture is intentionally locked as a semantic baseline, not an exact-text baseline.
- Current observed behavior:
- the pipeline reliably preserves `COMPLETED` phase and `RST sent = 599`
- closing semantics survive
- but the front half of the compact token can still degrade enough that station identification and callsign extraction lag behind the string-level interpreter tests
- Locked-in expectation for now:
- no `RUN` / `SIG` collapse
- `QSO semantics = 100%`
- text/hint recovery remains usable, but not exact
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Timing Bootstrap Stabilization

- Tightened the startup behavior of `CwTimingModel` for the common bad case where the very first recovered tone is a dash.
- Before this change, a leading dash could get treated as the initial dot estimate, which inflated the dot baseline by about `3x` and made early:
- tone classification
- intra-symbol gaps
- first letter gaps
- drift toward sticky/merged decoding in compact leading-edge traffic
- The model now uses a lightweight bootstrap heuristic:
- if the first tone looks dash-like relative to the default startup dot, seed dot from roughly `dash / 3`
- then blend that inferred dot with the default boot dot so startup remains stable across both faster and slower senders
- Practical effect:
- the first long tone can already classify as `DAH`
- the first real letter gap after that tone no longer collapses into an intra-symbol gap by default
- Added dedicated JVM coverage for:
- first long tone should bootstrap as `DAH` instead of inflating dot estimate
- first letter gap after a leading dash should still classify as `LETTER_GAP`
- Tightened the `fully_glued_ack_closing_chain` offline regression so it now also locks a sane startup dot-estimate window instead of only checking end-state semantics.
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.timing.CwTimingModelTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [CwTimingModelTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/timing/CwTimingModelTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Signal Edge Timestamp Refinement

- Tightened `CwSignalProcessor` so `tone on/off` timestamps are closer to the actual signal edge instead of being pinned to the frame that merely confirmed the state transition.
- Two practical refinements landed:
- rising edge:
- `TONE_ON` now estimates its timestamp by interpolating the threshold crossing between the previous frame and the first qualified frame
- falling edge:
- the processor still uses a short hang to avoid chatter, but `TONE_OFF` now reports the estimated silence-start edge rather than the later confirmation frame
- This means we no longer bake the debounce / confirmation delay directly into:
- tone duration
- gap timing seen by `CwTimingModel`
- early compact-token leading-edge behavior
- Added focused JVM coverage for:
- `TONE_ON` can land before the qualified frame boundary
- `TONE_OFF` timestamp uses estimated silence start instead of the later confirmation frame
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Frame-Local Edge Refinement

- Extended `CwSignalProcessor` one step beyond cross-frame threshold interpolation.
- The processor now tries a conservative frame-local edge estimate when the current frame clearly looks like:
- rising transition:
- early samples are quiet and later samples are stably active
- falling transition:
- early samples are active and later samples are stably quiet
- Implementation notes:
- builds a lightweight absolute-amplitude envelope from the current frame
- derives a local transition threshold from the frame's own envelope span
- only trusts the frame-local estimate when the within-frame contrast is strong enough
- otherwise falls back to the already-stable cross-frame interpolation path
- Practical effect:
- compact leading edges can now land inside the actual first active frame instead of being smeared backward/forward only by frame-level RMS
- short trailing tails can preserve a more realistic silence-start point for later timing classification
- Added focused JVM coverage for:
- half-frame late onset should place `TONE_ON` inside that frame
- short early tail should preserve a frame-local `TONE_OFF` edge instead of only collapsing at the next frame boundary
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)

## 2026-04-22 Soft-Edge Fixture Support

- Extended the offline fixture model so synthetic CW is no longer limited to ideal hard-edged keying.
- `CwFixtureScenario` can now optionally describe:
- rise ramp in milliseconds
- fall ramp in milliseconds
- `SyntheticFixtureRxAudioSource` now applies a smooth edge envelope to each tone segment when those values are present, which gives us a much closer approximation to:
- softer audio-shaped edges
- less idealized radio / audio path keying
- transition frames that do not look like perfect square-wave on/off boundaries
- Added a new offline regression baseline:
- `soft_edge_compact_ack_closing_chain`
- This keeps the familiar compact `R5NNTU73BK` semantic stress case, but renders it with:
- soft rise/fall edges
- light QSB
- moderate timing irregularity
- Current locked-in expectation:
- no `RUN` / `SIG` collapse
- completed-QSO semantics still survive
- text/hint recovery stays usable even if exact text is not the goal
- Added focused JVM coverage for:
- fixture ramp configuration exposure
- summary string includes edge-ramp description
- soft-edge compact closing fixture stays in the non-catastrophic bucket end-to-end
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)

## 2026-04-22 Tone-Drift Fixture Support

- Extended the offline fixture model again so synthetic CW is no longer limited to a perfectly fixed target pitch.
- `CwFixtureScenario` now supports:
- total target-tone drift in Hz across the rendered waveform
- timing-profile summary now includes that drift when present
- `SyntheticFixtureRxAudioSource` now renders the target CW tone with a gradual instantaneous-frequency change across the full sample stream while keeping phase continuous.
- This gives us a practical regression path for:
- slow pitch drift
- slight BFO / audio-path mismatch drift
- a moving preferred-tone target that still stays within the current tracking window
- Added a new fixture baseline:
- `drifting_soft_edge_compact_ack_closing_chain`
- This combines:
- compact ack/closing traffic
- soft keying edges
- light QSB
- gradual upward tone drift
- Current locked-in expectation:
- no `RUN` / `SIG` collapse
- tracked tone remains near the preferred target region
- completed-QSO semantics still survive
- Added focused JVM coverage for:
- drift configuration exposure in the fixture model
- timing-profile summary includes tone drift
- drifting soft-edge compact closing remains in the non-catastrophic offline bucket
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)

## 2026-04-22 Debug UI Signal Observability Refresh

- Promoted the newer front-end fixture details into the Debug UI so synthetic and later real-input experiments are easier to interpret without reading code or test logs.
- `InputDebugActivity` now shows richer fixture profile detail for synthetic scenarios, including when present:
- QSB depth and cycle
- tone drift in Hz
- rise/fall edge ramp duration
- The signal section now also reports live tracking error:
- `tracked tone - preferred tone`
- This makes it much faster to tell whether a fixture is intentionally drifting, or whether the front-end is wandering away from the chosen CW pitch on its own.
- Practical effect:
- fixture-side realism and front-end tracking behavior are now visible in one screen
- later microphone/debug work will have a clearer baseline for comparing:
- synthetic drifting target
- real audio drift / detune
- operator-selected preferred tone
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Front-End Expectation Stabilization

- Finished stabilizing the new fixture-declared `expectedFrontEndQualityCode` path after the first integration pass exposed constructor-shape mismatches in `CwFixtureScenario`.
- Refined the front-end grading logic one step further so a fixture is no longer forced into `DROP` just because the replay ends in normal tail silence.
- New practical distinction:
- `GOOD` now covers both:
- healthy lock retained through the end of the evaluated run
- healthy lock during active tone windows followed by a clean final `TONE_OFF` release into tail silence
- `DROP` is now reserved more narrowly for runs that really had:
- an earlier healthy lock window
- but also meaningful unlocked drift during active tone windows before the run ended
- To support this, the signal front end now tracks:
- tone-active frame count
- tone-active but unlocked frame ratio
- worst consecutive unlocked gap during tone-active windows
- The evaluator now uses those metrics to separate:
- normal fixture tail silence
- genuine late-run front-end degradation
- Current explicitly asserted front-end baseline batch now includes both signal-stress and humanized timing fixtures that have been individually re-observed after the grading refinement:
- `noisy_report_exchange`
- `nearby_interferer_directed_report`
- `moderate_interferer_directed_report`
- `human_ragged_clarification_report`
- `human_compact_ack_closing_chain`
- `human_op_timing_full_qso`
- `human_compact_de_closing_chain`
- `human_compact_report_tail_callsign`
- `human_compact_report_tail_followup`
- `soft_edge_compact_ack_closing_chain`
- `drifting_soft_edge_compact_ack_closing_chain`
- `fully_glued_ack_closing_chain`
- `compact_ack_report_tail_callsign`
- Current observed locked-in front-end grades are now intentionally split:
- now `GOOD` because the run shows healthy active-tone lock plus a clean release into tail silence:
- `noisy_report_exchange`
- `nearby_interferer_directed_report`
- `moderate_interferer_directed_report`
- `human_ragged_clarification_report`
- `human_compact_ack_closing_chain`
- `human_op_timing_full_qso`
- `human_compact_de_closing_chain`
- `human_compact_report_tail_callsign`
- `human_compact_report_tail_followup`
- `soft_edge_compact_ack_closing_chain`
- `drifting_soft_edge_compact_ack_closing_chain`
- `fully_glued_ack_closing_chain`
- some remaining declared fixtures outside the currently exercised regression set should still be treated as provisional until they are individually replay-verified again
- Meaning:
- many fixtures that previously looked like late front-end drops are now understood as:
- healthy active-tone lock
- followed by either a clean release or at most a single-frame unlock glitch near release
- while any future `DROP` baseline should now represent a more substantive active-tone degradation than that
- Practical decision for now:
- keep the expectation infrastructure enabled
- keep Debug UI visibility enabled
- keep regression assertion enabled
- but continue avoiding bulk-declaring additional fixture grades until each one is observed and locked individually
- Also expanded `CwFixtureScenario` constructor coverage for common no-interferer timing profiles, so later adding expectations to:
- `human_*`
- `soft_edge_*`
- `drifting_*`
- compact single-token closing / tail fixtures using the shortest single-message constructor
- fixtures will not require another constructor cleanup round first
- Re-verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest assembleDebug`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)

## 2026-04-22 Debug UI Clean-Release Observability

- Promoted the new `clean release` distinction into the Debug UI so front-end end-state interpretation is now closer to what the fixture evaluator is doing.
- The signal panel and microphone tone-watch now expose:
- tone-active unlocked frame ratio
- worst consecutive unlocked gap during tone-active windows
- The signal-health summary can now explicitly say:
- `Healthy lock with clean release`
- instead of showing a generic late unlocked state that looks like a front-end drop
- Practical effect:
- when a fixture or live run ends with expected post-message silence, the UI no longer makes that look the same as:
- true late-run loss of narrow-band lock during active tone windows
- This should make it much easier to compare:
- fixture replay summary
- live microphone behavior
- offline evaluator front-end grade
- without mentally translating between different concepts of “end unlocked”

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Active-Tone Lock Retention Tuning

- Tightened `CwSignalProcessor` again, this time specifically around active-tone lock retention under nearby continuous interferers.
- Practical idea:
- keep initial acquisition strict
- but once a tone is already active and the tracked frequency is still plausible, allow slightly more tolerant lock retention through short pressure dips
- This is aimed at the exact class of boundary cases where:
- the desired CW tone is already being copied
- a nearby steady carrier plus mild QSB momentarily pushes isolation down
- and the old logic would drop lock too eagerly in the middle of an otherwise usable tone window
- Added focused JVM coverage for this behavior:
- a target tone can lock normally under moderate nearby interference
- after lock is established, a short weaker segment under the same interferer should still preserve lock
- Current status after this round:
- the active-tone retention path is more stable
- clean-release interpretation and UI observability are now much better aligned
- the more difficult nearby-interferer fixture has now improved enough to grade as `GOOD`
- the moderate nearby-interferer fixture has now also crossed into `GOOD` after tightening local frequency contrast plus allowing a single-frame active-tone unlock glitch to still count as a clean release
- This means the current interferer branch has materially improved:
- `nearby_interferer_directed_report` -> `GOOD`
- `moderate_interferer_directed_report` -> `GOOD`
- so the next interferer milestone likely requires either:
- a newly harder baseline
- or a more realistic multi-carrier / drifting-interferer scenario

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Drifting Interferer Fixture Support

- Extended the offline fixture model so the synthetic interferer tone can now drift over the full rendered waveform, not just the primary CW tone.
- `CwFixtureScenario` now exposes:
- `interfererToneDriftHz`
- timing-profile summary text now includes `interferer drift ... Hz` when present
- `SyntheticFixtureRxAudioSource` now renders the interferer with a gradual instantaneous-frequency change while keeping phase continuous across the stream.
- Added a new regression fixture:
- `drifting_nearby_interferer_directed_report`
- Added a second explicit boundary fixture:
- `sweeping_boundary_interferer_directed_report`
- This fixture is intentionally calibrated as a `moderate drifting interferer baseline`:
- harder than the fixed-carrier nearby/moderate interferer cases
- but not so aggressive that it immediately drags the tracker into a non-usable boundary failure
- After replay verification, this baseline is now explicitly locked at front-end quality `GOOD`.
- The new sweeping boundary fixture deliberately captures a different failure mode:
- front-end lock can still look `GOOD`
- but the tracker can be pulled onto the wrong nearby tone strongly enough that the overall bottleneck is still classified as `SIG`
- Added focused JVM coverage for:
- fixture-model exposure of interferer drift
- summary-string exposure of interferer drift
- offline pipeline regression for the new drifting-interferer baseline
- Debug UI synthetic fixture summary now also shows interferer drift when present, so later real-input comparison work can more easily distinguish:
- fixed nearby carrier pressure
- drifting nearby carrier pressure
- target-tone drift
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Wrong-Tone Acquisition Guardrails

- Tightened `CwSignalProcessor` around a more specific real-world failure mode:
- front-end lock may remain strong
- but periodic retune can still get dragged onto a nearby stronger carrier instead of the intended CW tone
- Two new guardrail layers now exist in the retune path:
- stronger bias toward the user-selected preferred tone region
- continuity bias toward the currently tracked tone when a usable lock already exists
- Added a lightweight `candidate stability` gate:
- a farther retune candidate does not immediately take over on first sight
- it must stay stable across multiple retune scans before replacing the current track
- This is specifically aimed at:
- sweeping nearby interferers
- drifting carriers that momentarily cross the local contrast neighborhood
- short-lived strong wrong-tone candidates during active copy
- Added focused JVM coverage for:
- stronger nearby in-window tone still should not beat the closer preferred candidate at acquisition time
- an already locked target should resist periodic retune drift toward a farther nearby carrier
- a sweeping nearby carrier should not immediately hijack acquisition while crossing the scan window
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Wrong-Tone Evaluation Semantics

- Promoted `wrong-tone acquisition` from an implied diagnosis into an explicit evaluation state.
- `CwFixtureEvaluationResult` now carries:
- preferred tone frequency
- tracked tone frequency
- tracking error in Hz
- New front-end quality state:
- `WRONG`
- Meaning:
- the front end formed a strong stable lock
- but it stayed far enough away from the preferred tone that this should not be treated as ordinary `GOOD` acquisition
- New likely bottleneck state:
- `TRK`
- Label:
- `Wrong-tone acquisition / tracking`
- Practical effect:
- we can now clearly distinguish:
- `MISS` = never formed a convincing lock
- `DROP` = had earlier healthy lock then lost it
- `GOOD` = healthy target acquisition
- `WRONG` = locked confidently, but on the wrong tone
- Offline regression has been updated accordingly:
- `sweeping_boundary_interferer_directed_report` is now intentionally asserted as:
- front-end quality `WRONG`
- bottleneck `TRK`
- Debug UI signal health summary now also exposes the same class of condition as:
- `Strong lock on off-target tone`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFixtureEvaluator.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluator.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Live Front-End Health Classification Alignment

- Aligned live Debug UI microphone observability with the newer offline front-end grading semantics instead of keeping a separate ad-hoc wording path.
- Added a reusable `CwFrontEndHealthClassifier` so snapshot-based live runs can now emit the same compact concepts used by fixture evaluation:
- `GOOD`
- `WRONG`
- `DROP`
- `MISS`
- `WEAK`
- `NA`
- The classifier also exposes a compact live diagnosis code:
- `OK`
- `TRK`
- `SIG`
- `NA`
- `InputDebugActivity` now uses this classifier in three places:
- microphone tone watch
- signal state panel
- signal health summary
- Practical effect on live microphone/debug sessions:
- a strong off-target lock is now shown explicitly as `WRONG / TRK`
- a healthy retained lock or clean release is shown as `GOOD / OK`
- a run with earlier healthy lock that later fell back to search is shown as `DROP / SIG`
- a run that never formed a convincing lock is shown as `MISS / SIG`
- Added focused JVM tests for the classifier so the live-view semantics stay pinned even if thresholds are adjusted later.
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFrontEndHealthClassifier.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifier.java)
- [CwFrontEndHealthClassifierTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifierTest.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Shared Front-End Health Logic

- Finished the follow-through so live Debug UI and offline fixture evaluation now share the same front-end grading implementation instead of only sharing wording.
- `CwFrontEndHealthClassifier` was expanded into the single place that owns:
- front-end quality code thresholds
- clean-release / wrong-tone / earlier-lock / signal-loss predicates
- compact quality and diagnosis labels
- `CwFixtureEvaluationResult` now delegates its front-end history grading to that classifier instead of keeping a second copy of the thresholds inline.
- Practical benefit:
- future threshold tuning for `GOOD / WRONG / DROP / MISS / WEAK` can happen in one place
- live microphone observation and offline replay regression are less likely to silently drift apart
- the existing evaluator tests now also act as a backstop for the shared classifier path
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwFrontEndHealthClassifier.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifier.java)
- [CwFixtureEvaluationResult.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResult.java)
- [CwFixtureEvaluationResultTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureEvaluationResultTest.java)

## 2026-04-22 Debug UI Expected-vs-Observed Front-End Status

- Tightened the synthetic fixture workflow in Debug UI one more step.
- When a fixture declares an expected front-end quality, the selected-source summary now also shows:
- current observed front-end quality
- current observed bottleneck code
- whether the current live state matches the fixture expectation
- This means fixture replay checking is now easier to do at a glance:
- `Expected front-end: GOOD`
- `Observed front-end: GOOD / OK (matches expected)`
- or
- `Observed front-end: WEAK / SIG (expected GOOD)`
- This is intentionally lightweight, but it saves a lot of panel-hopping while iterating on interferer/timing fixtures.
- Verified with:
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

## 2026-04-22 Debug UI Lock Trend And Retune Pressure View

- Added a lightweight live `trend` layer on top of the existing front-end health summary so microphone and fixture testing can show whether the tone tracker is:
- steadily holding lock
- building retune pressure toward another tone
- sitting inside an active unlock gap
- still searching without a stable candidate
- `CwSignalSnapshot` now exposes a few extra real-time observability fields from `CwSignalProcessor`:
- current lock streak
- current tone-active unlock streak
- pending retune candidate frequency
- pending retune candidate stability scan count
- This has now been extended one step further with a small recent-history ring buffer:
- recent front-end state history
- recent tracked-tone offset history relative to preferred tone
- Debug UI uses those fields in two places:
- `Mic Trend` inside microphone tone watch
- extra signal-state lines for current lock streak and pending retune candidate
- plus a compact recent-history line in microphone tone watch:
- state history such as `..uLLLLLL...`
- offset history such as `0000++>>>`
- Practical effect:
- when a nearby interferer is trying to pull the tracker away, the UI can now say that retune pressure is building and show which frequency it is leaning toward
- when lock is simply stable, the UI says so explicitly instead of making us infer it from coverage percentages alone
- Added a focused signal regression check so snapshot-level observability now also verifies that a stable run exposes a growing current lock streak.
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwSignalProcessor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalProcessor.java)
- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwSignalProcessorTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
- [CwFrontEndHealthClassifierTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifierTest.java)

## 2026-04-22 Extra Callsign Pollution Recovery Fixtures

- Added another small batch of interpreter regression cases focused on more realistic callsign contamination patterns rather than idealized spaced tokens.
- Newly pinned cases now include:
- leading-noise plus glued closing chain around a remembered callsign such as `?BBG7YOZTU73BK??`
- glued ack/report/closing residue such as `BG7YOZR5NNTU73BK`
- uncertain callsign followed by clarification flow such as `BI9??Z UR CALLSIGN AGAIN PSE`
- portable callsign form such as `BG7YOZ/P`
- Current result:
- the existing callsign cleanup / remembered-context recovery logic already handles these cases without additional implementation changes
- so exact-callsign recovery coverage is now pinned more tightly by tests instead of only by manual reasoning
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Key files

- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)

## 2026-04-22 Deterministic Wobbly Bursty Interferer Fixture

- Extended the synthetic interferer model one step beyond fixed-duty burst behavior.
- `CwFixtureScenario.ContinuousInterfererProfile` now supports a deterministic `burst wobble` layer:
- `burstWobbleDepth`
- `burstWobbleCycleMs`
- Practical meaning:
- the adjacent carrier can still be fully deterministic and replay-stable
- but its burst timing is no longer strictly periodic frame after frame
- this better approximates real intermittent nearby activity than a perfectly repeating `70/130 ms` or `90/70 ms` rhythm
- `SyntheticFixtureRxAudioSource` now applies that wobble as a bounded low-frequency timing offset before burst gating, so occupancy feels less synthetic while remaining regression-friendly.
- Debug UI fixture summary now also exposes wobble parameters for additional interferers.
- Added a new moderate baseline fixture:
- `wobbly_bursty_interferer_directed_report`
- Current observed role of that fixture:
- front-end should still grade `GOOD`
- but it is meaningfully closer to a real adjacent intermittent activity pattern than the earlier strictly periodic burst baseline
- Added model coverage for summary-string exposure of wobble configuration and offline pipeline coverage for the new baseline.
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwFixtureScenario.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureScenario.java)
- [SyntheticFixtureRxAudioSource.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/audio/SyntheticFixtureRxAudioSource.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwFixtureScenarioTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFixtureScenarioTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-22 Wobbly Dual-Interferer Split

- Extended the new `wobbly occupancy` idea from a single intermittent adjacent carrier into a two-interferer crowded-band family.
- Added two new fixtures:
- `wobbly_dual_interferer_directed_report`
- `wobbly_dual_interferer_boundary_report`
- Important finding from replay calibration:
- these scenarios did **not** primarily expose wrong-tone acquisition
- the front-end still held the preferred target tone well enough to grade `GOOD`
- but decode / interpretation quality degraded much earlier than in the simpler burst or wobble baselines
- Practical implication:
- we now have a clearer fixture split between:
- front-end tracking boundaries such as `WRONG / TRK`
- downstream decode-stress boundaries where front-end lock remains healthy but copied text/QSO semantics fall apart
- The moderate `wobbly_dual_interferer_directed_report` fixture was tuned down until it stayed usable as a crowded-but-workable baseline.
- The harsher `wobbly_dual_interferer_boundary_report` fixture is now intentionally pinned as:
- front-end `GOOD`
- bottleneck `SIG`
- very low text / semantic recovery
- This gives us a much better next target for future work:
- timing/decoder robustness under irregular intermittent adjacent occupancy
- not just more retune / tracker hardening
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.wobblyDualInterfererFixtureStillRemainsWorkable --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.wobblyDualInterfererBoundaryFixtureCanExposeWrongToneTracking`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)

## 2026-04-23 Incremental Callsign Regression Fix

- Added two new interpreter regression cases that simulate realistic cumulative decode growth rather than one-shot final strings:
- `BI9CLT DE BG7YOZ UR 5 NN B K`
- `BI9CLT D E BG 7YOZ UR 5NN BK`
- These tests reproduced the exact pipeline failure where final normalized text was correct, but the addressed callsign regressed from `BI9CLT` to remembered partial `BI9CL`.
- Root cause:
- the remembered-callsign contamination rule was treating a trailing single-letter `T` as removable edge noise
- which caused a valid full callsign like `BI9CLT` to be downgraded back to `BI9CL` during incremental recovery
- Fixed by narrowing the single-letter contamination set so `T` is no longer stripped as generic remembered-edge contamination.
- Important effect:
- we still keep the existing keyword-residue cleanup path for `TU/TNX`-style tails
- but we no longer eat legitimate callsign suffixes ending in `T`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureScenarioTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Additional Hesitation False-Space Baselines

- Added two more low-priority human-operator hesitation fixtures aimed at `false intra-word spaces` inside callsigns:
- addressed callsign leaning toward `BI 9CLT`
- speaker callsign leaning toward `B G7YOZ`
- Both are modeled as normal source text plus targeted hesitation pauses, rather than literally pre-splitting the message text.
- Practical outcome:
- these now extend the existing `human_split_short_tokens_*` and `human_hesitation_*` family with more realistic callsign-specific spacing errors
- while still remaining stable enough to serve as passing offline baselines
- The stronger single-letter speaker-prefix split variant (`B G7YOZ`) is now promoted into a passing baseline after interpreter-side contextual callsign-edge recovery was added.
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanHesitationAddressedDigitSplitFixtureStillRecoversBothCallsigns --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanHesitationSpeakerPrefixSplitFixtureStillRecoversBothCallsigns`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)

## 2026-04-23 Contextual Single-Letter Callsign Edge Recovery

- Extended interpreter-side adjacent-fragment recovery so a single isolated letter can now merge back into a neighboring callsign body when all of the following are true:
- merged result is a valid callsign
- the single-letter fragment sits on the edge of the merged callsign
- surrounding tokens indicate a likely callsign slot such as `DE ... UR`
- This specifically upgrades realistic copies like:
- `BI9CLT DE B G7YOZ UR 5NN BK`
- `BI9CLT DE BG7YO Z UR 5NN BK`
- Important guardrail:
- the rule stays contextual, so it does not broadly disable the existing residue protections that keep `B K`, `K BI9CLT`, and similar control/report fragments from being over-merged.
- Added interpreter regression coverage for:
- one-shot recovery of single-letter prefix/suffix callsign splits
- incremental decode growth of the `B G7YOZ` case
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)

## 2026-04-23 Contextual Uncertain Single-Character Callsign Edges

- Extended the same edge-recovery idea one step further so contextual single-character callsign edges can now also carry `?` uncertainty instead of being forced into a clean trimmed callsign.
- Newly pinned interpreter cases now include:
- `BI9CLT DE ? G7YOZ UR 5NN BK`
- `BI9CLT DE BG7YO ? UR 5NN BK`
- plus incremental decode growth of the `? G7YOZ` prefix case
- Practical effect:
- the interpreter now preserves partial candidates like `?G7YOZ` and `BG7YO?`
- instead of aggressively trimming them down into clean inner fragments such as `G7YOZ` when the unknown edge could still be a real callsign character
- Important guardrail:
- trimmed repair variants from uncertain raw candidates are now prevented from silently dropping all `?` uncertainty by default
- so a clean full callsign should come from remembered/contextual upgrade logic, not from blindly cropping an uncertain edge away
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)

## 2026-04-23 Remembered Upgrade Over Uncertain Single-Character Prefix

- Added explicit regression coverage for the case where the interpreter first sees a clean speaker callsign and later only receives an uncertain split edge such as `? G7YOZ`.
- The remembered speaker callsign is now pinned as the authoritative clean recovery path for this scenario:
- text remains uncertain (`?G7YOZ`)
- callsign candidate list still converges back to clean `BG7YOZ`
- partial-copy hint is preserved
- Added a new two-part offline fixture:
- `human_remembered_uncertain_prefix_closing`
- It models a realistic full-session flow:
- first part establishes `BG7YOZ` cleanly during report exchange
- second part copies a closing message as `? G7YOZ TU 73 BK`
- Important calibration result:
- this is now a stable passing pipeline baseline, and the full-session expectation is aligned to `COMPLETED` rather than just `CLOSING`
- because the offline evaluator sees the earlier report exchange and later closing as one completed QSO flow
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanRememberedUncertainPrefixClosingFixtureKeepsUncertainTextButRecoversCleanCallsign`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Ambiguous Callsign Conflict Guard And Residue Recovery Stabilization

- Added new interpreter coverage for conflict-style callsign ambiguity where an uncertain speaker fragment such as `BG7Y?Z` can match more than one clean callsign candidate.
- Current intended behavior is now explicit:
- do not force an arbitrary clean upgrade when multiple plausible clean matches exist
- keep the uncertain current copy visible
- keep earlier clean remembered context intact so a later clearer copy can still recover correctly
- Hardened remembered-callsign updates so they no longer downgrade a previously clean remembered callsign into a shorter anchored fragment such as `BI9CL`.
- Narrowed callsign-run / keyword interaction so `AGN?`-style clarification tokens no longer merge into callsign runs.
- Extended repeated-callsign run handling so human-style glued repetitions with a truncated final repeat can still collapse back to the base callsign.
- Added a small safety fix in repeated-run parsing to avoid substring crashes on shorter compact decode events seen in fixture playback.
- Regression outcome after this round:
- clarification flows such as `BI9??Z AGN? CALLSIGN PSE KN` again keep `BI9??Z` as the remote callsign candidate
- glued repeated calling flows such as `CQCQCQ DE BI9CLTBI9CLTBI 9CL T` again recover `BI9CLT`
- remembered addressed/speaker recovery still works for short fragments and uncertain middle splits
- compact `AG` / `TU` residue no longer pulls callsigns back to polluted forms like `BI9CLTAG` or `BG7YOZT`
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.qso.CwConversationSemanticsTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanHesitationClarificationFixtureStillRecoversRepeatFlow --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanRememberedUncertainAddressedMiddleClosingFixtureKeepsUncertainTextButRecoversCleanCallsign`
- `.\gradlew.bat testDebugUnitTest`

### Key files

- [CwInterpreter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/interpreter/CwInterpreter.java)
- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwConversationSemanticsTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/CwConversationSemanticsTest.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Current Priority Reset

- Re-aligned the near-term roadmap around overall product value instead of continuing to let callsign recovery dominate the main line by default.
- Current priority order is now:
- `P0` real-input RX stability
- `P0` signal / timing robustness under real microphone conditions, QSB, drift, and interference
- `P1` front-end observability and manual correction UX
- `P1` TX v1 minimum loop (`text -> Morse -> sidetone -> stop/progress`)
- `P1` QSO draft to confirmed log to ADIF workflow polish
- `P2` callsign intelligence as continuous enhancement, driven by real samples and fixture gaps
- `P2` rig / keyer transmission integration after TX v1 is stable
- `P3` third-party sync and broader log ecosystem work
- Current milestone framing is now:
- `M1` stabilize the receive chain under real input and improve debug observability
- `M2` make the current RX/QSO path comfortably operable even when recognition is imperfect
- `M3` add the first usable TX loop without blocking on external rig integration
- Practical interpretation:
- callsign recovery remains important, but it is now treated as an ongoing polish lane rather than the only main battlefield
- the immediate next coding work should prefer microphone/debug/signal-side observability and robustness improvements

### Key files

- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)
- [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md)

## 2026-04-23 M1 Recent-Window Front-End Observability

- Continued `M1` by pushing debug observability beyond simple cumulative lock percentages.
- `CwSignalSnapshot` now exposes recent-window counters/ratios derived from the rolling front-end history buffer:
- recent locked frames
- recent active-unlocked frames
- recent search frames
- recent near-target lock ratio
- recent far-off-target lock ratio
- `CwFrontEndHealthClassifier` now also provides:
- `recentTrendLabel(...)`
- `liveCheckHint(...)`
- The Debug UI now surfaces those directly in the receive path:
- microphone tone watch includes recent lock/search/unlock percentages and recent alignment quality
- the shared signal health summary now includes a recent trend line and a more actionable “next check” suggestion
- fixture mode also now reuses the same hinting layer, so comparing preferred tone vs fixture tone vs tracked tone is easier without mentally decoding raw metrics each time
- Practical outcome:
- when testing with live microphone input, it is now easier to tell whether the latest unlocked state is just a clean tail or whether the recent window has actually been unstable
- when testing with fixtures, it is easier to see whether the problem is wrong-tone pressure, search-heavy acquisition, or a downstream decoder/interpreter issue
- Regression coverage added:
- `CwSignalSnapshotTest` pins recent-window ratios and alignment statistics
- `CwFrontEndHealthClassifierTest` now pins recent trend and live-check hint behavior for off-target lock, search-heavy windows, and stable near-target lock
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalSnapshotTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest`
- `.\gradlew.bat testDebugUnitTest`

### Key files

- [CwSignalSnapshot.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshot.java)
- [CwFrontEndHealthClassifier.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifier.java)
- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [CwSignalSnapshotTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalSnapshotTest.java)
- [CwFrontEndHealthClassifierTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/eval/CwFrontEndHealthClassifierTest.java)

## 2026-04-23 M2 Draft And Log Review Workflow Polish

- Shifted the current work from signal-side `M1` observability into `M2` workflow usability.
- Added a reusable workflow formatter so the app now uses one shared logic layer for:
- draft review status
- field origin / locked-vs-live summary
- recommended next step
- confirmed-log review queue messaging
- `QSO Editor` now shows:
- draft review summary
- next-step guidance
- field origin summary
- dynamic `Confirm With Review Flag` button text when the current draft is still uncertain
- a direct `Clear Draft` action to abandon the active draft cleanly
- `Logbook` now shows:
- global review queue count
- filtered-vs-total log count
- selected-log review explanation and next-step guidance
- clearer `[Review]` marking in the log list
- safer review/edit flow wording such as `Review In Editor`
- Fixed an important correctness bug in the logbook workflow:
- `mark review` and `delete` now operate by confirmed-log database `id`
- instead of using the index from a filtered/sorted list against the default repository order
- which could previously target the wrong log under filters or alternate sort orders
- `Home` summary now also reflects the draft next-step hint and confirmed-log review queue count.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.qso.QsoWorkflowSummaryFormatterTest`
- `.\gradlew.bat testDebugUnitTest`

### Key files

- [QsoWorkflowSummaryFormatter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoWorkflowSummaryFormatter.java)
- [QsoEditorActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoEditorActivity.java)
- [QsoLogbookActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/qso/QsoLogbookActivity.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [QsoWorkflowSummaryFormatterTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/qso/QsoWorkflowSummaryFormatterTest.java)

## 2026-04-23 Remembered Upgrade Over Uncertain Addressed Callsign Middle Split

- Added addressed-side remembered recovery coverage for cases where the called station later appears with an uncertain middle split such as `BI ?CLT`.
- Newly pinned interpreter cases now include:
- direct remembered upgrade from `BI ?CLT` to clean `BI9CLT`
- realistic two-stage incremental growth where a clean earlier addressed callsign is remembered before the uncertain split version arrives
- Added a new two-part offline fixture:
- `human_remembered_uncertain_addressed_middle_closing`
- It models:
- a clean earlier report exchange that establishes the addressed callsign
- followed by a later closing copy containing `BI ?CLT DE BG7YOZ TU 73 BK`
- Practical outcome:
- the end-to-end pipeline now treats this as a stable completed-QSO baseline
- text can remain visibly uncertain (`BI?CLT`)
- while callsign recovery still converges to the clean remembered `BI9CLT`
- Calibration note:
- current end-to-end behavior does not surface `Partial callsign resolved` hints for this addressed-side case, so the fixture expectation was aligned to the actual stable semantics rather than forcing a more ambitious hint contract prematurely
- Verified with:
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.interpreter.CwInterpreterCallsignRecoveryTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest.humanRememberedUncertainAddressedMiddleClosingFixtureKeepsUncertainTextButRecoversCleanCallsign`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`

### Key files

- [CwInterpreterCallsignRecoveryTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/interpreter/CwInterpreterCallsignRecoveryTest.java)
- [CwFixtureLibrary.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)
- [CwFixturePipelineRegressionTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwFixturePipelineRegressionTest.java)

## 2026-04-23 Priority Execution Pass 1 2 3

- Re-anchored the active execution order as:
- `1` RX real-input stability and observability
- `2` M2 review/edit/confirm/log workflow closure
- `3` TX v1 minimal core loop

### 1. RX real-input stability

- Added per-frame microphone clipping awareness into `AudioFrame` and `MicrophoneRxAudioSource`.
- Added a small reusable input-health layer:
- `AudioInputHealthTracker`
- `AudioInputHealthSnapshot`
- `AudioInputHealthFormatter`
- `InputDebugActivity` now shows a direct microphone-input health block in addition to front-end lock health, so live testing can distinguish:
- too quiet input
- hot input
- clipping input
- usable input
- microphone coaching now prefers input-level problems first before suggesting front-end tuning.

### 2. M2 review/edit/log workflow closure

- `QsoLogbookActivity` no longer round-trips a confirmed log through the active draft just to edit it.
- The logbook now opens `QsoEditorActivity` with the selected confirmed-log id.
- `QsoEditorActivity` now supports a real confirmed-log edit mode:
- reload selected log
- reset unsaved edits back to the selected log
- save editor changes back into the same confirmed log
- keep or clear manual-review state based on the edited snapshot
- The editor still keeps `Save Draft Copy` available, so confirmed-log review and ad-hoc draft capture can coexist.
- Repository support was extended with `loadConfirmedLogById(...)`.
- `ConfirmedQsoLog` now has a focused helper for applying editable draft fields while preserving log identity and UTC logging metadata.

### 3. TX v1 minimal core loop

- Added a first `core/tx` package:
- `CwTxEngine`
- `CwTxPlan`
- `CwTxElement`
- Extended that first TX slice from planning-only into a minimal runnable loop:
- `CwTxState`
- `CwTxPlaybackSnapshot`
- `CwTxAudioOutput`
- `CwTxRunner`
- `CwTxBackend`
- `LocalSidetoneTxBackend`
- `RigTextTxBackend`
- Added a first Android TX page:
- `TxActivity`
- `AudioTrackTxAudioOutput`
- `Home` now exposes `Open TX Console`.
- `TxActivity` now also exposes backend selection, so the UI contract is no longer hard-wired to local audio only.
- `TxActivity` now also exposes:
- station callsign input
- reusable TX presets / macros
- backend ready / not-ready state
- explicit availability messaging before transmit
- Current scope is still intentionally minimal, but no longer planning-only:
- normalize operator text
- map supported characters to Morse
- build a deterministic key-down / key-up timing plan from WPM
- expose a Morse preview plus total transmit duration
- play local sidetone with `AudioTrack`
- show start / stop / progress / current-element status
- surface future rig text-to-CW adapters as formal TX routes even when they are still placeholder-only
- keep placeholder rig backends visible but blocked with explicit `not ready` semantics instead of failing ambiguously
- This is now the first reusable layer for later rig/keyer backends and a richer TX workflow.
- Added the first actually working rig-path prototype:
- `AudioVoxRigControlAdapter`
- This adapter is now registered in `RigRegistry` as a real `text-to-CW` route instead of a placeholder.
- Practical meaning:
- the app can now generate CW audio through a formal rig adapter path
- a radio or external keyer can in principle be driven through audio / VOX
- without yet requiring CAT / USB serial / Bluetooth serial integration
- The Audio VOX route now also consumes the live TX profile from `TxActivity`:
- current WPM
- current tone frequency
- `TxActivity` now surfaces a route-specific checklist / warning block, so VOX testing is guided by:
- backend readiness
- tone-range hints
- WPM-range hints
- excessive-length warning for early over-the-air tests
- Supporting refactor:
- the reusable `AudioTrackTxAudioOutput` implementation was moved into `core.tx`
- so both `TxActivity` and future rig adapters can share the same audio-output primitive without UI-layer coupling
- Added a route-advice layer:
- `CwTxRouteAdvisor`
- The TX page now renders backend-specific checklists instead of hard-coded UI-only wording, including:
- local sidetone dry-run guidance
- Audio VOX calibration / warning guidance
- USB RTS/DTR hardware-keying guidance
- Added the second real adapter prototype:
- `UsbSerialKeyerRigControlAdapter`
- plus supporting abstractions:
- `SerialKeyerPort`
- `SerialKeyerTxOutput`
- `DisconnectedSerialKeyerPort`
- Practical meaning:
- the project now has two real transmission-path prototypes under the same rig adapter contract:
- `Audio VOX`
- `USB serial RTS/DTR keying`
- The current USB keyer route is still backed by a disconnected port in the default registry,
- but unlike a pure placeholder, the adapter now already contains real keying/timing logic and is covered by tests,
- so the remaining work is mainly to replace the disconnected port with an actual USB serial session implementation.

### Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Suggested next step after this checkpoint

- keep `1` moving by comparing the new microphone input health summary against real on-device recordings
- then calibrate the new TX sidetone page on real devices and decide whether to attach station presets / canned messages next
- after that, start separating `local sidetone TX` from future `external rig/keyer TX backend` responsibilities

## 2026-04-23 TX Hardware Route Follow-Up: USB Permission + Key-Line Controls

- Continued the TX hardware route after the first `Audio VOX` and `USB serial keyer` prototypes.
- The `USB serial keyer` backend is no longer just discoverable in the UI; `TxActivity` now has a route-specific control block for:
- matched USB CDC/ACM device summary
- current backend availability
- active `RTS` / `DTR` key-line selection
- explicit `Request USB Permission` action
- Added an Android USB permission request flow for the selected USB keyer backend:
- `TxActivity` sends a permission request with a dedicated broadcast action
- permission grant / deny now feeds back into the TX page immediately
- backend summary and start-button readiness now refresh after the permission result
- `UsbSerialKeyerRigControlAdapter` was generalized slightly so it now behaves more like a real route:
- backend id is now `usb-serial-keyer`
- readiness reflects whether the port factory can actually open a hardware path, not only whether a port was already opened once
- the active key line can be switched between `RTS` and `DTR` from UI
- `RigTextTxBackend` now exposes its underlying rig adapter to allow route-specific TX UI controls without hard-coding UI logic into the core backend contract
- `SerialKeyerTxOutput.KeyLine` is now public so UI and tests can share the same control-line enum
- Practical outcome:
- the TX page is now much closer to a real bench-testing tool for a simple USB serial keyer
- the remaining gap is no longer “basic app wiring”, but mainly richer hardware UX such as explicit device selection and broader USB-serial chipset support

### Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug`

### Suggested next step

- keep moving on the same branch by replacing the current “first matched CDC/ACM device” logic with explicit USB device selection and persisted route preferences
