# CWCN Android Bootstrap

这是 `CWCN` 的第一版独立 Android 工程骨架。

当前目标不是直接交付完整解码器，而是把后续实现所需的基础工程先固定下来：

- 独立 Gradle 工程
- `CWCN` 自有图标与主题
- 最小可运行入口页
- 核心模块命名与分层起步结构

## 目录

- `app/` Android 应用模块
- `gradle/` Gradle wrapper

## 当前已接入

- 自适应图标资源
- `InputDebugActivity`
- 启动首页
- 核心模块注册表占位
- 输入链路调试页
- 第一版 `AudioRecord` 麦克风采集
- 第一版 `CwSignalProcessor` tone on/off 事件检测
- 第一版 `CwTimingModel` 时长与间隔分类
- 第一版 `CwDecoder` 字符级输出
- 第一版 `CwInterpreter` token 与基础语义提示
- 第一版 `QsoStateMachine` 阶段与草稿视图
- 第一版 `ADIF preview`

## 下一步

- 接入统一连接层
- 打通收音输入
- 搭建频谱与回放基础能力
- 继续推进草稿持久化、正式日志与真实 ADIF 导出

## 构建

在本目录执行：

```powershell
.\gradlew.bat assembleDebug
```

## Latest Progress

- Added a minimal local log repository in `core/log`.
- Added local draft save / restore support.
- Added confirmed QSO log persistence.
- Added real ADIF file export for confirmed logs.
- `InputDebugActivity` now has three new debug actions:
- `Save Draft`
- `Confirm Log`
- `Export ADIF`
- Current RX pipeline remains:
- `AudioFrame -> CwSignalProcessor -> CwTimingModel -> CwDecoder -> CwInterpreter -> QsoStateMachine`

## Main Files

- [InputDebugActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [LocalLogRepository.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
- [ConfirmedQsoLog.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/ConfirmedQsoLog.java)
- [CwAdifExporter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/adif/CwAdifExporter.java)
- [QsoStateMachine.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoStateMachine.java)

## Manual Draft Editing

- The QSO panel now includes a manual draft editor.
- Manual edits override live decoded fields until `Reset Manual` is pressed.
- Editor text is protected from high-frequency decode refresh while there are unapplied local edits.

## Callsign Candidate Assist

- Likely callsigns are now visually highlighted in interpreter output.
- Detected callsign candidates are rendered as quick-action buttons in the QSO section.
- Tapping a candidate applies it to the remote callsign field and updates the draft immediately.

## Formal QSO Editor

- Added a standalone `QsoEditorActivity`.
- The debug page can now open the formal editor screen directly.
- The formal editor currently supports draft load/save, confirm-log, ADIF export, and recent confirmed-log summary.

## Logbook Screen

- Added a standalone `QsoLogbookActivity`.
- The editor can now open the logbook directly.
- Confirmed logs can be selected, inspected, and loaded back into the active draft for further correction.
- The logbook now also supports:
- `Re-edit`
- `Mark Review / Clear Review`
- `Delete Selected Log`
- Delete now shows a confirmation dialog before removal.
- Confirmed log timestamps are shown in a clearer human-readable format.

## Confirmed Log Storage

- Confirmed logs now use a local SQLite-backed table instead of `SharedPreferences + JSON`.
- The repository performs one-time legacy migration from the previous JSON blob.
- Active draft storage is still in `SharedPreferences` for now.

## Home Entry

- The app now launches into a dedicated `HomeActivity`.
- Home links `Debug`, `QSO Editor`, and `Logbook` as the main navigation entry.
- The previous debug page is no longer the launcher screen.

## Unified Local Storage

- Confirmed logs and the active draft now both use the local SQLite-backed storage layer.
- Legacy `SharedPreferences` JSON for both confirmed logs and draft is migrated on first repository open.

## Logbook Querying

- The logbook now supports:
- callsign filtering
- QSO date range filtering (`YYYYMMDD`)
- review-only filtering
- common sort orders
- Repository access has started to move toward shared query/overview models instead of page-by-page direct list assembly.

## Decoder Fixture Baseline

- The debug page now includes a deterministic `Synthetic Fixture` receive source.
- Fixture scenarios can be selected and replayed through the full decode stack.
- This gives us a repeatable baseline before deeper work on:
- adaptive WPM
- noise robustness
- QSB handling
- partial callsign recovery

## Fixture Evaluation

- Fixture scenarios now carry expected text / callsign / hint metadata.
- Replay completion now produces a lightweight evaluation summary inside the debug page.
- This is not yet a formal automated test suite, but it gives us an immediate regression signal while iterating on decoder behavior.
- Fixture evaluation history is now persisted locally in SQLite.
- Recent runs and more explicit failure reasons are shown in the debug page.

## Signal/Timing v2

- Frame granularity has been tightened from `1024` to `512` samples.
- `CwSignalProcessor` now uses adaptive attack/release thresholds with separate noise/signal floor estimates.
- `CwTimingModel` now keeps dot / dash / intra-gap estimates and feeds gap information back into dot estimation.

## Interpreter v2

- Added more normalization and hint extraction for common CW shorthand and repeat/clarification phrases.
- This includes `PLS/PSE`, `TU/TNX`, `CALL SIGN`, and uncertain-copy handling around partial callsigns.

## Callsign Resolution

- The interpreter now promotes a `primary` callsign candidate.
- Compatible partial callsigns can be merged into a more complete candidate when later confirmation arrives.
- The QSO state machine now prefers the stronger candidate and resists regressing from a full copy back to a weaker partial one.

## Context Roles

- `DE` context is now used to distinguish the addressed callsign from the speaking callsign in directed exchanges.
- This improves primary candidate selection and helps the QSO state machine keep better local/remote role guesses.

## Evaluation Scoring

- Fixture evaluation summaries now include a primary-callsign score (`P`) in addition to text/callsign/hint recall.

## QSO Semantic Fixture Coverage

- Fixture evaluation now also checks the downstream `QsoStateMachine`, not only interpreter output.
- A new `Q` score tracks whether a scenario reached the expected:
- `QsoPhase`
- `RST sent`
- `RST rcvd`
- Added a built-in closing/ack scenario:
- `BI9CLT DE BG7YOZ R 5NN TU 73 BK`
- This gives us repeatable regression coverage for:
- `R 599` return-report semantics
- `TU` / `73` closing behavior
- `BK` handoff
- `COMPLETED` phase detection
- Fixture history persistence has been extended so this extra QSO semantic evaluation is kept in local SQLite history too.

## Clarification / QRZ Flow Guardrails

- The QSO state machine no longer treats bare `UR` as sufficient evidence of a true report exchange.
- This avoids misclassifying clarification traffic such as:
- `BI9??Z UR CALL AGAIN PSE K`
- Repeated `QRZ` solicitation now stays in the calling path even when a station callsign is present:
- `QRZ QRZ DE BG7YOZ BG7YOZ K`
- Added a dedicated `QRZ Loop` fixture plus local JUnit coverage for:
- repeat/clarification flow
- QRZ loop flow
- acknowledgement + closing flow

## Multi-Round Callsign Convergence

- Local JUnit coverage now also exercises multi-round dialogue instead of only one-shot messages.
- This includes a clarification-to-resolution path where a partial addressed callsign:
- `BI9??Z`
- is later resolved by a fuller directed exchange:
- `BI9CLZ DE BG7YOZ UR 5NN BK`
- The current expected behavior is:
- local/addressed callsign upgrades to `BI9CLZ`
- remote/speaking callsign becomes `BG7YOZ`
- `599` is captured as the received report
- a later weaker partial copy must not downgrade an already-known full callsign

## Multi-Part Fixture Replay

- Synthetic fixture replay now supports multi-part scripted CW scenarios inside one replay session.
- This is important for more realistic CW evaluation because interpreter/QSO state can now persist across:
- clarification request
- confirmation round
- later directed report exchange
- Existing fixtures still work as before; they are treated as one-part scripts automatically.
- Added the first multi-part built-in fixture:
- `multi_round_callsign_resolution`
- The debug page now shows synthetic fixture script-part count and inter-part gap so multi-round scenarios are easier to recognize during replay.

## Full Multi-Round QSO Fixture

- Added a longer scripted fixture that goes beyond clarification and into closing:
- `multi_round_full_qso`
- It covers:
- partial callsign clarification
- later full callsign resolution
- directed `UR 5NN`
- final `TU 73 BK`
- Matching local JUnit coverage now checks that this three-round flow finishes in `COMPLETED` with the resolved local/remote callsigns and retained `599` report state.

## Human-Style `?` And `KN`

- Added support for common human-style punctuated tokens such as:
- `QRZ?`
- `AGN?`
- `PSE?`
- These are now normalized into the same semantic path as their plain-token forms.
- `KN` is now treated as a control/handoff token alongside `K` and `BK`.
- Added built-in fixture coverage and local JUnit coverage for these patterns so more casual real-operator traffic is less likely to regress.

## Glued Callsign Runs

- Added a first-pass normalization path for sticky spacing / glued repeated sends.
- This currently covers cases like:
- `CQCQCQ`
- `BI9CLTBI9CLTBI 9CL T`
- The interpreter can now expand glued `CQ` runs and recover exact repeated full callsigns from suspicious merged alphanumeric runs when the pattern is clear enough.
- Added matching fixture and local JUnit coverage for this operator-style callsign glue case.

## Irregular Timing Fixture v1

- Synthetic fixture replay now supports two simple timing-profile controls for more human-like CW:
- `timingJitterDepth`
- `dotSwingDepth`
- `timingJitterDepth` adds deterministic per-event duration variance across tones and gaps.
- `dotSwingDepth` alternates dot lengths to give a more hand-key / bug-like feel.
- Added two first timing-oriented fixtures:
- `irregular_hand_key_cq`
- `irregular_bug_qsb_report`
- The debug page fixture summary now shows timing jitter and dot swing when a scenario uses them.

## Common Glue / Split Callsign Cases

- Interpreter pre-tokenization now covers a few more high-value real-operator formatting quirks, including:
- `DEBI9CLT`
- `BI9 CLT`
- `BG7 YOZ`
- Current heuristics can:
- split keyword-prefixed callsigns such as `DEBI9CLT`
- merge short adjacent callsign fragments when the combined result is a valid callsign
- retain the earlier repeated glued-callsign recovery path
- Added built-in fixture coverage and local JUnit coverage for these representative glue/split cases.
