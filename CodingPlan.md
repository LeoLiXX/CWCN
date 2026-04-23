# 安卓端 CW 工程设计草案

本文基于 [Requirement.md](/d:/Workshop/CWCN/Requirement.md) 与 [FT8CN机制梳理与CW新工程蓝本.md](/d:/Workshop/CWCN/FT8CN/FT8CN机制梳理与CW新工程蓝本.md) 的讨论结果整理，用于固化当前版本设计，并作为后续继续讨论和收敛的基础文档。

## 1. 项目定位

这是一个运行在安卓手机上的 CW 工作台应用，目标不是单一的 CW 解码器，也不是单一的 CW 发射器，而是一个完整的无线电操作辅助系统。

核心目标：

- 连接并控制多种电台，支持 `USB/OTG`、蓝牙串口、网络电台等接入方式。
- 对 CW 信号进行面向真实无线电环境的高鲁棒连续流接收、检测、解码与人工纠错辅助。
- 支持用户输入文本后，通过多种可能的后端方式驱动电台完成 CW 发射。
- 记录 QSO、导入导出 ADIF、同步第三方日志平台。
- 复用 FT8CN 在连接、日志、参考数据方面的成熟能力。

## 2. 统一术语

为避免后续讨论歧义，统一采用以下表述：

- `文本驱动发射`：用户输入文本，系统将其转换为 Morse 符号、键控事件或其他可发送表达，并通过某种发射后端驱动电台完成 CW 发射。
- `Text-to-CW`：与“文本驱动发射”同义，用于描述从文本到 CW 的完整发送链路。
- `CW 发射后端`：真正执行发射动作的后端实现，例如 `RTS`、`DTR`、音频 `VOX`、`CAT`、网络协议、内置 keyer 接口等。
- `连续流解码`：区别于 FT8 的固定时隙处理，CW 接收必须以连续音频流为基础进行处理。
- `语义解释`：在字符解码之后，对缩略语、Q code、QSO 阶段、意图等进行解释与推测。

注意：

- 不再使用“直接发文本给电台 Keyer”作为总表述。
- 更准确的说法是“文本驱动的 CW 发射能力”，而“发文本给电台 keyer”只是其中一种可能后端。

## 3. 产品边界

### 3.1 首版必须具备

- 单通道 CW 连续接收与基础解码。
- 频点跟踪、WPM 估计、字符级候选与置信度显示。
- 文本输入后发射 CW，优先支持 `RTS`、`DTR`、音频 `VOX`。
- 基础电台连接管理。
- QSO 草稿、人工确认、正式日志落库。
- ADIF 导入导出。
- callsign、Maidenhead、DXCC/ITU/CQ 等参考数据支撑。

### 3.2 首版产品能力可以分期，但底层目标不能降低

- 首版产品功能可以分阶段交付，但解码器架构必须从第一天就按 `CW Skimmer` 级别的高精度目标设计。
- 必须从第一阶段就建设用于高精度解码的回放、评测、对比体系。
- 一开始就做完整自动对话代理。
- 一开始就覆盖所有电台的专有 keyer 接口。
- 一开始就做多信号并行解码与 SDR 多通道处理。

### 3.3 后续增强方向

- 更强的自适应 WPM 与噪声鲁棒性。
- 多通道/多频点同时解码。
- 自动回复循环与更智能的句式推荐。
- 更深的 QSO 语义理解与半自动日志生成。
- 第三方日志平台深度同步。

## 4. 设计原则

- 复用 FT8CN 已成熟的“无线电 App 骨架”，不从零重造连接层和日志层。
- 将“连接/协议/音频/解码/语义/QSO/日志”严格分层。
- 将“原始证据”和“推断结果”分开保存，便于人工纠错。
- 把 `VOX` 视作兼容方案，不作为首选高质量发射方案。
- 先做工程闭环，再逐步增强算法精度。

## 5. 整体架构

建议采用以下模块划分：

1. `RigTransport`
2. `RigControlAdapter`
3. `RxAudioSource`
4. `CwSignalProcessor`
5. `CwTimingModel`
6. `CwDecoder`
7. `CwInterpreter`
8. `CwTxEngine`
9. `QsoStateMachine`
10. `LogRepository`
11. `ReferenceData`
12. `DecoderEvaluationLab`

推荐数据流：

```text
RigTransport / RxAudioSource
    -> CwSignalProcessor
    -> CwTimingModel
    -> CwDecoder
    -> CwInterpreter
    -> QsoStateMachine
    -> LogRepository

Typing UI / Macro UI
    -> CwTxEngine
    -> RigControlAdapter / RigTransport
```

## 6. 模块职责

### 6.1 RigTransport

职责：

- 管理物理链路。
- 屏蔽 USB、蓝牙 SPP、TCP/UDP/VITA 的差异。
- 提供稳定的连接、断开、收发能力。

可直接参考或迁移：

- `connector/`
- `serialport/`
- `bluetooth/`
- `icom/`
- `flex/`
- `x6100/`

### 6.2 RigControlAdapter

职责：

- 处理各机型的控制协议。
- 负责频率设置、模式切换、PTT、状态查询、专有命令等。
- 面向机型家族实现，如 `Icom`、`Yaesu`、`Kenwood`、`Elecraft`、`Flex`、`Xiegu`。

设计要点：

- 抽象不能只围绕传统 `CAT` 字节流。
- 必须允许文本协议、二进制协议、专有网络协议并存。
- 要能支持“只写控制”“读写双工”“网络保活状态机”等现实情况。

### 6.3 RxAudioSource

职责：

- 统一音频来源。
- 将不同来源重采样为内部统一采样率。
- 对上层暴露统一的连续音频流。

音频来源：

- 手机麦克风
- USB/蓝牙音频设备
- 网络电台音频流
- CAT over audio

建议直接借鉴：

- `wave/HamRecorder.java`

### 6.4 CwSignalProcessor

职责：

- 窄带滤波
- 频点跟踪
- 包络检测或相干检测
- 动态门限
- 噪声估计
- 对 `QSB` 的局部归一化与抗衰落处理
- 输出 `tone on/off` 事件流

这是新项目的第一核心模块。

### 6.5 CwTimingModel

职责：

- 实时 `WPM` 估计
- 点、划、字间隔、词间隔的时值建模
- 对真人 `OP` 手法漂移的适配
- 对 `Bug Key` 不对称节奏的容忍与建模
- 对真人 `OP` 因手速、犹豫或节奏失衡导致的“词内误空格 / 假词间隔”做容错
- 为上层解码提供概率化时序约束，而不是固定阈值

这是高精度 CW 解码的关键模块，建议从一开始就单独建模，而不是把它埋进 `CwSignalProcessor` 或 `CwDecoder` 里。

补充设计约束：

- 不能看到一次较长 gap 就立刻武断判定为 `word gap`，应允许其作为“异常 letter gap / hesitation gap”候选存在。
- 当长 gap 两侧字符在语义上更像同一单词、同一 callsign 或同一固定短语时，上层应允许重新并回，而不是只接受底层一次性切词结果。
- 该能力先作为低优先级鲁棒性增强项记录，不阻塞当前主线实现。
- synthetic fixture 侧应支持“指定字符边界的 hesitation pause”注入，便于用正常文本、异常时序来模拟 `5NN -> 5 NN`、`BK -> B K` 这类真人手法。

### 6.6 CwDecoder

职责：

- 将 `tone on/off` 事件流转换为 `dit/dah/gap`
- 将符号序列转换为字符
- 将字符拼接为文本
- 支持多候选路径与置信度，而不是只保留单一路径结果

建议输出内容：

- 字符候选
- 字符置信度
- 当前估计 WPM
- 是否疑似 prosign

### 6.7 CwInterpreter

职责：

- 解释缩略语、Q code、prosign
- 识别 callsign、RST、QTH、姓名、设备等实体
- 推测当前 QSO 阶段
- 给出下一句建议
- 按用户配置解释非标准或地方性约定写法

重点：

- 这是语义层，不应与底层信号解码器耦合。

#### 6.7.1 约定映射与归一化

真实 CW 中经常存在一些“圈内约定”或个人习惯写法，不能强行按标准字符表一刀切。

必须支持的原则：

- `raw_text` 永远保留原始解码结果，不做不可逆覆盖。
- `normalized_text` 才承载按规则解释后的文本。
- 常见 CW 报号缩写应默认容错处理，不要求用户额外配置。
- 约定映射必须可配置、可开关，而不是写死在解码器里。
- 同一份原始解码在不同配置下，允许得到不同的 `normalized_text`。

默认应内建的语义容错：

- `5NN -> 599`
- `ENN -> 599`
- `NN -> 99` 或在 `RST` 语境下补全为 `599`

建议拆分为两类规则：

可默认容错的常见写法：

- `5NN -> 599`
- `ENN -> 599`
- `NN -> 99`，并在明确 `RST` 语境下可补全为 `599`
- `TU -> THANKS`
- `TNX -> THANKS`
- `AGN -> AGAIN`
- `PSE -> PLEASE`

必须谨慎处理的非标准写法：

- `5TT`
- `AEE`
- 非通用的地方性缩写
- 仅在某些 OP 个人习惯中出现的字符替代
- 会直接影响呼号判断的单字符替换

建议支持一类“字符约定映射”配置：

- `9 -> N`
- `N -> 9`
- `0 -> T`
- `T -> 0`
- `A -> 1`
- `1 -> A`

说明：

- 这里不要求首版把所有 cut number / 缩写约定一次做全，但架构必须允许追加。
- `5NN` 这类常见报号不应依赖配置项，建议直接在解释层按默认规则容错归一。
- `ENN` 这类较常见 contest 写法也可默认按 `599` 处理。
- 类似 `5TT` 这类不够稳妥或缺乏统一出处的写法，不建议默认直接归一到 `599`。
- 你提到的 `9 -> N` 就属于这种“可配置约定映射”，应作为明确配置项支持。
- callsign 识别、QSO 阶段识别、草稿补全都应优先基于 `raw_text + normalized_text` 双轨判断，而不是只看单一路径。

建议增加“归一化配置档”概念：

- `normalization_profile`
- `character_substitution_rules`
- `cut_number_mode`

#### 6.7.2 规则字典表结构

为了让“默认规则 / 配置规则 / 人工确认边界”可实现，建议把归一化规则抽象成统一字典结构。

建议每条规则至少包含：

- `rule_id`
- `profile`
- `rule_type`
- `source_pattern`
- `target_value`
- `scope`
- `confidence`
- `enabled`
- `needs_review`
- `notes`

字段含义建议：

- `profile`：规则所属配置档，如 `strict_standard`、`common_cut_number`、`custom_bi9n_profile`
- `rule_type`：规则类型，如 `rst_normalization`、`text_abbreviation`、`character_substitution`
- `source_pattern`：原始匹配模式，如 `5NN`、`ENN`、`AGN`
- `target_value`：归一后的目标值，如 `599`、`AGAIN`
- `scope`：作用范围，如 `rst_only`、`free_text`、`callsign_forbidden`
- `confidence`：默认置信度，可用于排序或冲突处理
- `enabled`：是否启用
- `needs_review`：是否命中后默认需要人工确认
- `notes`：规则来源、风险说明或备注

推荐的规则类型：

- `rst_normalization`
- `text_abbreviation`
- `qso_token`
- `character_substitution`
- `callsign_guard`

作用范围建议：

- `rst_only`
- `free_text`
- `qso_phase`
- `callsign_forbidden`
- `callsign_allowed_with_review`

建议的默认规则字典：

| profile | rule_type | source_pattern | target_value | scope | needs_review |
|---|---|---|---|---|---|
| `common_cut_number` | `rst_normalization` | `5NN` | `599` | `rst_only` | `0` |
| `common_cut_number` | `rst_normalization` | `ENN` | `599` | `rst_only` | `0` |
| `common_cut_number` | `text_abbreviation` | `TU` | `THANKS` | `free_text` | `0` |
| `common_cut_number` | `text_abbreviation` | `TNX` | `THANKS` | `free_text` | `0` |
| `common_cut_number` | `text_abbreviation` | `AGN` | `AGAIN` | `free_text` | `0` |
| `strict_standard` | `callsign_guard` | `9->N` | `DISABLED` | `callsign_forbidden` | `1` |
| `custom_bi9n_profile` | `character_substitution` | `9->N` | `N` | `callsign_allowed_with_review` | `1` |

设计建议：

- 默认规则字典应内置在应用中，保证离线可用。
- 自定义规则应叠加在默认规则之上，而不是覆盖原始规则定义。
- 对 callsign 有破坏性的替换，应通过 `scope` 明确限制，避免误伤呼号。
- 同一 `source_pattern` 命中多条规则时，应按 `profile -> scope -> confidence` 的顺序决策。

#### 6.7.3 `M4` 首版内置规则清单

为避免首版做得过宽，建议把规则分成三层：

默认启用：

- `5NN -> 599`
- `ENN -> 599`
- `NN -> 99`，并且仅在明确 `RST` 语境下允许补全为 `599`
- `TU -> THANKS`
- `TNX -> THANKS`
- `AGN -> AGAIN`
- `PSE -> PLEASE`
- `BK -> BREAK`
- `DE -> FROM`

首版内置但默认关闭：

- `9 -> N`
- `N -> 9`
- `0 -> T`
- `T -> 0`
- `A -> 1`
- `1 -> A`

这些规则的默认状态建议为：

- 在自由文本解释中可见
- 对 callsign 解析默认不启用
- 若用户手动开启，也应带 `needs_review = 1`

首版不做自动归一：

- `5TT -> 599`
- 缺乏统一出处的地方性缩写
- 会明显改变呼号结构的多字符替换
- 需要依赖强上下文才能成立的个性化约定

`M4` 的实现原则：

- 默认启用的规则必须“高频、低争议、低风险”。
- 默认关闭的规则可以存在，但必须通过配置显式开启。
- 首版不做的规则即便用户提出需求，也先走人工确认链路，不直接自动写正式日志。

建议的首版规则优先级：

1. `RST` 相关归一
2. 常见 QSO 缩写词归一
3. 与状态机直接相关的 token 归一
4. 高风险字符替换规则

用途：

- 针对不同 OP、不同台风、不同比赛/平时风格切换解释策略。
- 允许用户快速切换“严格标准 / 常见缩写 / 自定义约定”三类配置。
- 为后续评测提供可重复的解释环境。

### 6.8 CwTxEngine

职责：

- 把文本转换为 Morse 符号或键控事件
- 调度具体发射后端
- 处理发送状态、暂停、恢复、回退、重复发送
- 控制前后沿时序

统一上层接口建议：

```text
sendCwText(text, wpm, pitchHz, leadInMs, tailMs)
sendCwSymbols(symbols)
sendKeyingEvents(events)
pause()
resume()
rewind()
stop()
```

### 6.9 QsoStateMachine

职责：

- 管理当前会话状态
- 识别 `CQ -> 应答 -> 报告交换 -> QTH/Name -> 73 -> 完成`
- 维护当前 QSO 草稿
- 决定何时可以生成正式日志

CW 特有交互要求：

- 不要求像 FT8 那样依赖固定结构报文才能进入 QSO 管理。
- 允许用户从某一条解码结果中直接提取“疑似呼号”并创建或更新 QSO 草稿。
- 呼号提取结果必须允许人工修正，修正后的值优先于自动识别结果。
- 当整段 QSO 交换内容较随意时，系统仍应支持“先锁定呼号，再逐步补全 RST / Name / QTH”等字段。

#### 6.9.1 标准 CW 流程优先设计

CWCN 的 QSO 设计应大致按标准 CW 习惯流程建模，但不能把真实电台操作硬塞成僵硬模板。

首要原则：

- 系统优先理解和辅助“标准流程”的 CW QSO。
- 标准流程用于驱动状态机、草稿补全、宏建议和自动回复。
- 真实世界里允许省略、缩写、倒序、重复呼叫、插入 `QRZ`、`AGN`、`BK` 等变体。
- 因此，模板是“高概率模式”，不是唯一合法语法。

建议优先覆盖的标准流程模板：

1. `CQ CQ CQ DE <MY_CALL> <MY_CALL> <MY_CALL> PSE K`
2. `<THEIR_CALL> DE <MY_CALL> K`
3. `<THEIR_CALL> DE <MY_CALL> UR 5NN`
4. `<THEIR_CALL> DE <MY_CALL> R R TU`
5. `QRZ QRZ DE <MY_CALL>`
6. `<THEIR_CALL> DE <MY_CALL> TNX FER CALL UR 5NN 5NN BK`
7. `<THEIR_CALL> DE <MY_CALL> NAME <NAME> QTH <QTH> BK`
8. `<THEIR_CALL> DE <MY_CALL> TU 73 EE`

需要特别注意：

- 有些 OP 会发得非常完整，有些只发 `CQ DE <CALL> K`。
- 有些应答只发 `<MY_CALL> DE <THEIR_CALL> 599`，甚至直接 `<MY_CALL> 5NN`。
- `QRZ QRZ DE <MY_CALL>` 既可能是 CQ 后继续收听，也可能是刚完成一轮后继续叫人。
- `PSE K`、`K`、`BK`、`KN` 的使用会随个人习惯变化。

#### 6.9.2 QSO 阶段状态机建议

建议把标准流程抽象为以下阶段：

1. `idle`
2. `calling_cq`
3. `awaiting_reply`
4. `reply_detected`
5. `report_exchange`
6. `info_exchange`
7. `closing`
8. `completed`

阶段含义建议：

- `calling_cq`：本台正在 CQ，或系统识别到对方正在 CQ。
- `awaiting_reply`：CQ 发出后等待呼叫返回。
- `reply_detected`：已识别到明确呼号应答，但信息仍不完整。
- `report_exchange`：进入 `UR 5NN / R 5NN / RST` 一类交换。
- `info_exchange`：进入 `NAME / QTH / RIG / WX / ANT` 等自由交换。
- `closing`：出现 `TU / 73 / EE / GL` 等收尾迹象。
- `completed`：已满足生成正式日志的最小条件。

状态流转不要求严格线性：

- `reply_detected -> report_exchange`
- `reply_detected -> info_exchange`
- `report_exchange -> closing`
- `report_exchange -> info_exchange`
- `info_exchange -> report_exchange`
- `closing -> awaiting_reply`，例如 `QRZ DE <MY_CALL>` 继续下一轮

#### 6.9.3 状态机识别信号

建议为 `QsoStateMachine` 增加“阶段触发词/模式”识别：

CQ/寻呼类：

- `CQ`
- `CQ CQ`
- `QRZ`
- `DE <CALL>`

应答类：

- `<MY_CALL> DE <THEIR_CALL>`
- `<MY_CALL> <THEIR_CALL>`
- `<THEIR_CALL> DE <MY_CALL>`

报告交换类：

- `UR 5NN`
- `RST 579`
- `R 5NN`
- `RRR`

信息交换类：

- `NAME`
- `QTH`
- `THX FER CALL`
- `RIG`
- `WX`
- `ANT`

收尾类：

- `TU`
- `TNX`
- `73`
- `EE`
- `CUL`

这些信号建议作为“阶段加权证据”，而不是绝对开关。

#### 6.9.4 阶段句式与字段补全建议

建议把每个阶段进一步细化为“常见句式模式 + 可补全字段 + 下一步建议动作”。

`calling_cq`

常见句式：

- `CQ CQ CQ DE <MY_CALL> PSE K`
- `CQ DE <MY_CALL> K`
- `QRZ QRZ DE <MY_CALL>`

可补全字段：

- `station_callsign_used`
- `tx_backend`
- `band`
- `freq`
- `qso_phase = calling_cq`

下一步建议：

- 进入 `awaiting_reply`
- 若检测到明确应答呼号，则转入 `reply_detected`

`reply_detected`

常见句式：

- `<MY_CALL> DE <THEIR_CALL>`
- `<MY_CALL> <THEIR_CALL>`
- `<THEIR_CALL> DE <MY_CALL>`
- `<MY_CALL> 5NN`
- `BI9??Z UR CALL AGN PSE`
- `?I9??Z UR CALLING SIGN AGAIN PLS`

可补全字段：

- `remote_callsign_candidate`
- `callsign_candidates`
- `source_decode_event_id`
- `draft_text`
- `normalized_text`
- `confidence_score`
- `qso_phase = reply_detected`

下一步建议：

- 创建或更新草稿
- 推荐 `Answer Macro` 或 `Report Macro`
- 等待 `RST` / `UR` / `R` 等更强确认词
- 如果呼号只有部分抄清，应允许以占位形式进入草稿，并标记 `need_manual_review = 1`
- 如果检测到 `AGN / AGAIN / CALL AGN / UR CALLING SIGN AGAIN PLS`，应继续归入当前确认流程，而不是新建 QSO

`report_exchange`

常见句式：

- `UR 5NN`
- `R 5NN`
- `RST 579`
- `TU 5NN`
- `5NN BK`

可补全字段：

- `rst_sent_candidate`
- `rst_rcvd_candidate`
- `estimated_wpm`
- `qso_phase = report_exchange`

报号容错建议：

- 解码文本可以直接保留 `5NN`、`TU 5NN` 这类原文。
- 但在 `RST` 语义提取时，应默认把 `5NN` 归一为 `599`。
- `ENN` 这类常见 contest 写法也可按 `599` 处理。
- 对 `5TT` 这类不够稳妥的写法，不建议默认直接归一为 `599`，应交由配置规则或人工确认。
- `rst_sent_candidate` 与 `rst_rcvd_candidate` 应保存归一后的结果，原始写法保留在 `raw_text` / `normalized_text` 中。

实现边界建议：

- 默认容错清单只覆盖“行业内足够常见且语义较稳定”的写法。
- 凡是会明显改变 callsign、地区前缀、数字含义的替换，都不应默认启用。
- 凡是缺乏统一出处、仅凭经验猜测的写法，都应降级为“配置项或人工确认”。

下一步建议：

- 如果双方报告都较明确，可转入 `info_exchange` 或 `closing`
- 如果报告存在冲突，保留多个候选并标记 `need_manual_review`

`info_exchange`

常见句式：

- `NAME <NAME>`
- `QTH <QTH>`
- `RIG <RIG>`
- `WX <WX>`
- `ANT <ANT>`
- `TNX FER CALL`

可补全字段：

- `name_candidate`
- `qth_candidate`
- `remote_grid_candidate`
- `normalized_text`
- `qso_phase = info_exchange`

下一步建议：

- 若出现 `TU / 73 / EE`，转入 `closing`
- 若中间又插入 `R 5NN` 之类内容，可回到 `report_exchange`

`closing`

常见句式：

- `TU 73`
- `TNX 73`
- `73 EE`
- `GL GD DX`
- `QRZ DE <MY_CALL>`

可补全字段：

- `qso_phase = closing`
- `confirmed_at` 候选时间

下一步建议：

- 若已具备最小日志条件，可进入 `completed`
- 若出现 `QRZ`，不一定意味着当前草稿应立即结束，需要结合上下文判断

`completed`

最小完成条件建议：

- 已有较高置信度的对方呼号
- 已知时间与频率/波段
- 至少存在一次较可信的应答或报告交换证据

进入 `completed` 后：

- 可生成待确认草稿
- 仍允许用户手工补齐 `RST / Name / QTH`
- 不要求所有扩展信息都完整才允许保存正式日志

#### 6.9.5 多轮 `QRZ` / 重复确认过程

这一点非常重要：真实 CW 里经常不是一轮叫完就清晰确认，可能会出现多轮 `QRZ`、重复呼号确认、重复报告交换。

需要显式支持的场景：

- `CQ -> reply_detected -> QRZ -> reply_detected`
- `reply_detected -> report_exchange -> QRZ -> report_exchange`
- `closing -> QRZ -> awaiting_reply`
- `reply_detected -> AGN? -> reply_detected`
- `report_exchange -> NIL/AGN -> report_exchange`
- `reply_detected(partial callsign) -> CALL AGN -> reply_detected(partial callsign improved)`
- `reply_detected(partial callsign) -> QRZ -> reply_detected(same station clarified)`

设计要求：

- `QRZ` 不应被简单视为“当前 QSO 已结束”。
- `QRZ` 在不同阶段的语义不同：
  - 在 `calling_cq / awaiting_reply` 中，更像继续寻呼。
  - 在 `reply_detected / report_exchange` 中，可能表示对当前应答仍不完全确认，要求重发呼号或信息。
  - 在 `closing` 中，才更像一轮结束后继续叫人。
- 不完整呼号不应被简单丢弃；应允许以占位形式存在，例如 `BI9??Z`、`BG?YOZ`、`?H1ABC`。
- 状态机应允许同一草稿在多轮 `QRZ` 和重复应答中持续存活，而不是每次都新建草稿。
- 当 `QRZ` 之后出现新的高置信度不同呼号时，才考虑新建另一条草稿。

建议增加“确认轮次”概念：

- `confirmation_round`
- `last_confirmation_prompt`
- `last_confirmed_callsign_confidence`
- `partial_callsign_mask`

用途：

- 记录当前草稿经历了几轮呼号确认或报告确认。
- 防止系统把第二次、第三次 `QRZ` 误判成全新 QSO。
- 在 UI 上提醒用户“当前仍处于确认拉扯阶段，建议人工核对呼号”。
- `partial_callsign_mask` 用于保存尚未完全确认的呼号形态，例如 `BI9??Z`。

建议的状态机处理：

- 如果 `QRZ` 后再次出现相同或近似呼号，优先更新当前草稿。
- 如果 `QRZ` 后出现完全不同且高置信度的新呼号，再考虑分叉为新草稿。
- 如果多轮 `QRZ` 后仍只有低置信度呼号，保持草稿为 `active` 且 `need_manual_review = 1`。
- 如果后续解码把 `BI9??Z` 收敛到 `BI9ABZ`，应优先在当前草稿上收敛更新，而不是新建草稿。
- 如果部分呼号在多轮确认后仍存在多个等可能候选，应保留候选列表并要求人工最终确认。

#### 6.9.6 部分呼号收敛规则

对 `BI9??Z -> BI9A?Z -> BI9ABZ` 这类过程，建议采用“兼容优先、冲突止步”的收敛策略。

可自动视为“同一候选持续收敛”的条件：

- 新旧候选长度一致，或长度差异只来自明显抄收缺失。
- 已确认字符位不冲突。
- 新候选只是把 `?` 逐步替换为更具体字符。
- 新候选与当前草稿的 callsign 前缀、区域前缀、已知电台上下文相容。
- 新候选置信度显著高于旧候选，且没有出现同分竞争候选。

示例：

- `BI9??Z -> BI9A?Z`：可自动收敛
- `BI9A?Z -> BI9ABZ`：可自动收敛
- `BG?YOZ -> BG7YOZ`：可自动收敛

必须暂停自动收敛、转人工确认的条件：

- 已确认字符位发生冲突，例如 `BI9A?Z` 后又出现 `BH9A?Z`
- 出现两个或以上分数接近的完整候选，例如 `BI9ABZ` 与 `BI9XBZ`
- 候选跨越明显不同前缀体系或地区前缀
- 多轮 `AGN / QRZ` 后呼号仍长期停留在低信息量占位
- 用户已经手工修正过呼号，系统后续识别与人工值冲突

建议的收敛规则：

1. 优先以“固定字符位兼容”判断是否属于同一候选。
2. 若兼容，则更新 `partial_callsign_mask`，并提升 `callsign_convergence_score`。
3. 若新候选已无占位符且明显优于旧候选，可自动写入 `remote_callsign_candidate`。
4. 若存在冲突，则保留多候选，不覆盖当前主值，并设置 `need_manual_review = 1`。
5. 若用户手工确认后，`callsign_state` 切换为人工锁定，后续自动收敛只作为提示。

建议增加两个草稿层状态字段：

- `callsign_state`
- `callsign_convergence_score`

推荐取值：

- `callsign_state = partial`
- `callsign_state = converging`
- `callsign_state = locked_auto`
- `callsign_state = locked_manual`
- `callsign_state = conflicting`

用途：

- `callsign_state` 用于表达当前呼号所处的确认阶段。
- `callsign_convergence_score` 用于记录“当前候选是否正在稳定收敛”。
- UI 可据此决定是显示绿色锁定、黄色待确认，还是红色冲突提示。

#### 6.9.7 宏与自动回复设计边界

因为你希望大致按标准流程走，所以 `Typing / Macro` 设计建议内置一组标准 CW 宏模板：

- `CQ Macro`: `CQ CQ CQ DE <MY_CALL> PSE K`
- `Answer Macro`: `<THEIR_CALL> DE <MY_CALL> K`
- `Report Macro`: `<THEIR_CALL> DE <MY_CALL> UR 5NN BK`
- `Info Macro`: `<THEIR_CALL> DE <MY_CALL> NAME <NAME> QTH <QTH> BK`
- `Close Macro`: `<THEIR_CALL> DE <MY_CALL> TU 73 EE`
- `QRZ Macro`: `QRZ QRZ DE <MY_CALL>`

自动化边界建议：

- 系统可以基于当前 `qso_phase` 推荐下一条宏。
- 系统可以在高置信度条件下自动停止 CQ 循环并切换到 `Answer Macro` 或 `Report Macro`。
- 系统不应在低置信度呼号条件下自动发出正式应答。
- 所有自动推荐都应允许一键改写或退回手工发送。

### 6.10 LogRepository

职责：

- 原始解码事件落库
- 当前 QSO 草稿持久化
- 正式日志落库
- 去重与更新
- ADIF 导入导出
- 第三方同步任务生成与状态维护

设计要求：

- 日志系统采用 `local-first`，任何外部同步失败都不能影响本地日志保存。
- 自动解码、草稿、正式日志三层数据必须分离。
- `ADIF` 导出属于高优先级核心能力，应早于第三方平台集成落地。
- 第三方同步通过独立 `SyncOutbox` 队列驱动，而不是直接耦合在“通联完成”回调里。
- 同一条正式日志要支持多目标平台分别记录同步状态、错误原因和最后重试时间。

建议复用：

- `database/`
- `log/`
- `html/`

### 6.11 ReferenceData

职责：

- callsign 前缀库
- Maidenhead 网格
- DXCC/CQ/ITU 统计
- 动态 callsign -> grid 记录
- 距离计算

建议复用：

- `callsign/`
- `maidenhead/`
- `count/`

### 6.12 DecoderEvaluationLab

职责：

- 建立标准化音频回放能力
- 管理测试语料与标注结果
- 覆盖真人 `OP`、`Bug Key`、`QSB`、背景噪声、邻频干扰等高难场景
- 输出字符准确率、词准确率、callsign 命中率、QSO 阶段识别率等评测指标
- 支持不同算法版本自动对比

这是高精度目标落地的保障模块，不属于后期优化项，而是核心研发基础设施。

## 7. 接收链路设计

CW 接收链路必须从 FT8 的“固定时隙”切换为“连续流”，并且必须明确面向以下真实困难场景设计：

- 真人 `OP` 的不稳定手法
- `Bug Key` 带来的不对称节奏
- `QSB` 导致的幅度塌陷与时序破坏
- 背景噪声、邻频干扰、频漂

建议拆为以下阶段：

1. 连续音频采样缓存
2. 频谱分析与主频跟踪
3. 窄带滤波
4. 包络检测
5. 动态阈值与信号分段
6. 时值建模与 `WPM` 自适应估计
7. `dit/dah/space` 判定
8. 多候选字符解码
9. 文本拼接
10. 语义解释

建议采用分层输出：

### 7.1 原始事件层

- 时间戳
- 频点
- `tone on/off`
- 持续时间
- 幅度
- SNR

### 7.2 符号层

- `dit`
- `dah`
- `intra-char gap`
- `char gap`
- `word gap`

### 7.3 字符层

- 候选字符
- 置信度
- 当前 WPM

### 7.4 文本与语义层

- 原始文本
- 规范化文本
- 语义角色
- QSO 阶段
- 疑似 callsign 片段
- callsign 候选及置信度

建议：

- 原始事件层、符号层、字符层、语义层必须严格分离。
- 所有“推测结果”都不能覆盖原始解码证据。
- 对 callsign 的识别结果应视为“高价值候选实体”，可以驱动草稿生成，但不能直接替代人工确认。

## 8. 发射链路设计

本项目的发送本质是：

```text
Text -> Morse symbols / keying events -> TxBackend -> Rig
```

而不是：

```text
Text -> Radio keyer
```

建议支持以下发射后端：

1. `AudioVoxBackend`
2. `RtsKeyingBackend`
3. `DtrKeyingBackend`
4. `CatPttPlusAudioBackend`
5. `CatDirectKeyingBackend`
6. `NetworkKeyingBackend`
7. `RigNativeKeyerBackend`

其中：

- `AudioVoxBackend` 是兼容方案。
- `RtsKeyingBackend` 与 `DtrKeyingBackend` 应优先落地。
- `RigNativeKeyerBackend` 只是可选增强，不是总设计前提。

### 8.1 发射时序控制

相比 FT8，CW 更依赖细粒度时序控制，建议显式支持：

- `PTT lead-in`
- `key-down rise guard`
- 字符间隔校正
- 单词间隔校正
- `tail hold`
- `break-in / semi-break-in`
- `watchdog`
- 用户中断

### 8.2 Typing 发送交互

Typing 模式至少支持：

- 输入文本即时排队
- 暂停
- 恢复
- 回退重发
- Repeat 间隔
- 收到有效应答后中断 CQ 循环
- 当前已发内容与待发内容显示

## 9. 日志与数据模型

建议保留三层数据：

1. 原始解码事件
2. 当前会话草稿
3. 正式确认 QSO

如需支持后续云端/第三方扩展，建议再补一层：

4. 外部同步任务队列

### 9.1 分层模型

`RawDecodeEvent`

- 保存接收时间线中的原始证据。
- 允许保留多候选字符、时序片段、置信度、频偏、估计 WPM、SNR 等信息。
- 主要用于回看、纠错、评测和后续算法迭代。

`QsoDraftSession`

- 表示“当前正在形成中的一次会话”。
- 可由 `QsoStateMachine` 持续更新。
- 允许不完整、允许冲突、允许人工修正。
- 可以同时附带推荐字段，如 callsign、RST、QTH、Name、草稿备注。
- 支持从单条解码条目直接创建草稿，而不强依赖完整 QSO 阶段判定。

`ConfirmedQsoLog`

- 只保存用户确认后的正式记录。
- 作为 `ADIF` 导出与后续第三方同步的唯一事实来源。
- 一旦生成，应尽量保持不可变；修订通过版本或修订记录完成。

`SyncOutboxItem`

- 一条正式日志可拆分出多个外部同步任务。
- 每个任务绑定一个目标平台，如 `Cloudlog`、`QRZ`。
- 记录状态：`pending`、`running`、`success`、`failed`、`need_reauth`。

在现有日志模型基础上，建议为 CW 增加或预留：

- `mode = CW`
- `wpm`
- `pitch_hz`
- `decoder_confidence`
- `raw_text`
- `normalized_text`
- `normalization_profile`
- `operator_corrected_text`
- `tx_backend`
- `rx_audio_source`
- `qso_phase`
- `need_manual_review`
- `review_reason`
- `confirmed_by_user`
- `confirmed_at`
- `adif_snapshot`
- `sync_targets`
- `sync_status`

### 9.2 `ConfirmedQsoLog` 字段建议

首版建议把正式日志字段分为三层：

必需字段：

- `id`
- `station_callsign`
- `operator_callsign`
- `remote_callsign`
- `mode`
- `qso_date`
- `time_on`
- `band` 或 `freq`

强烈建议字段：

- `time_off`
- `rst_sent`
- `rst_rcvd`
- `name`
- `qth`
- `my_gridsquare`
- `gridsquare`
- `comment`
- `tx_backend`
- `wpm`
- `confirmed_at`

内部保留字段：

- `decoder_confidence`
- `raw_text`
- `normalized_text`
- `normalization_profile`
- `operator_corrected_text`
- `qso_phase`
- `need_manual_review`
- `review_reason`
- `adif_snapshot`

约束建议：

- `ConfirmedQsoLog` 只保存“已确认事实”，不再混入推测中的候选值。
- 首版宁可少字段，也不要把不稳定推断硬写入正式日志。
- `wpm`、`tx_backend` 这类 CW 特有信息要保留在本地模型里，但不要求全部进入首版 `ADIF`。
- `normalization_profile` 用于记录当前正式日志采用了哪套约定映射规则，便于回看与复现。
- `rst_sent` / `rst_rcvd` 建议保存归一后的标准值，例如把 `5NN` 记为 `599`。

### 9.3 `ADIF` 导出范围

`M4` 阶段优先落地的是稳定可用的 `ADIF` 导出，而不是第三方平台同步。

首版导出范围建议：

- 导出对象仅限 `ConfirmedQsoLog`。
- 支持导出单条 QSO、当前筛选结果、全部正式日志。
- 支持按时间范围导出。
- 默认导出标准字段，不导出大量内部调试字段。
- 导出体验保留 FT8CN 的双路径模式：`系统分享导出` 与 `本地 Web 下载导出`。

首版建议映射到 `ADIF` 的字段：

- `CALL`
- `STATION_CALLSIGN`
- `OPERATOR`
- `QSO_DATE`
- `TIME_ON`
- `TIME_OFF`
- `MODE=CW`
- `BAND`
- `FREQ`
- `RST_SENT`
- `RST_RCVD`
- `NAME`
- `QTH`
- `GRIDSQUARE`
- `MY_GRIDSQUARE`
- `COMMENT`

首版不强制导出的内部字段：

- `decoder_confidence`
- `raw_text`
- `normalized_text`
- `operator_corrected_text`
- `qso_phase`
- `need_manual_review`
- `review_reason`
- `sync_status`

导出原则：

- `ADIF` 内容只来自正式日志，不直接读取草稿和原始事件。
- 导出时固定生成一份 `adif_snapshot`，保证“再次导出”和“后续同步”载荷一致。
- 如果某字段没有足够把握，就留空，不用猜测值补齐。
- 首版优先保证导出兼容性和可读性，再考虑附加自定义字段。
- 交互上优先保留 FT8CN 已验证过的导出体验，而不是重新发明一套完全不同的流程。

### 9.4 状态流转

建议状态流转为：

1. 接收链路持续写入 `RawDecodeEvent`。
2. `CwInterpreter` 和 `QsoStateMachine` 基于事件流更新 `QsoDraftSession`。
3. 当系统判断“可能完成一次 QSO”时，生成待确认草稿，而不是直接写正式日志。
4. 用户确认后，生成 `ConfirmedQsoLog`。
5. `ConfirmedQsoLog` 固化一份 `ADIF snapshot`，作为导出和后续第三方同步的统一载荷。
6. 同步管理器按目标平台生成 `SyncOutboxItem` 并异步执行。

### 9.5 第三方同步架构

这一层属于后续增强能力，不阻塞首版 QSO 管理与 `ADIF` 导出上线。

建议采用“可插拔同步适配器”：

- `LogSyncAdapter` 统一定义能力边界，如 `validateConfig()`、`buildPayload()`、`upload()`、`mapResult()`。
- 首批适配器可以是 `CloudlogSyncAdapter`、`QrzSyncAdapter`。
- 后续可以平滑扩展 `ClubLog`、`LoTW`、`eQSL`，而不污染核心日志模型。

同步原则：

- 本地日志先成功，再考虑外部同步。
- 外部同步默认异步执行。
- 网络失败、认证失效、目标服务异常都只影响同步状态，不回滚本地 QSO。
- 要支持去重键或幂等策略，避免重复上传。
- 要支持手动重试、批量重试和按平台重试。

### 9.6 与 FT8CN 现状的关系

FT8CN 现有实现里，QSO 成功后会直接触发 `Cloudlog` / `QRZ` 上传，这对功能验证足够简单，但对 CWCN 来说建议进一步解耦：

- FT8CN 适合复用其配置项、ADIF 处理和第三方服务接入经验。
- FT8CN 现有 `QSLTable / QSLRecord / ShareLogs` 已经形成了较完整的“正式日志 + ADIF 导出”链路，CWCN 应优先参考这一层的数据模型与字段命名。
- FT8CN 的 `Messages` 更偏向 FT8 固定报文结果，不适合直接承载 CW 连续流原始事件。
- 因此，CWCN 的正式日志层优先基于 FT8CN 扩展；`RawDecodeEvent` 与 `QsoDraftSession` 两层按 CW 特性自行设计。
- 针对 `9 -> N` 这类非标准约定，建议沿用 FT8CN 的配置化思路，但实现为 CWCN 自己的“归一化配置档”，而不是写死规则。
- CWCN 不建议把第三方上传直接写死在 QSO 成功回调中。
- 更合适的做法是“正式日志落库 -> 生成同步任务 -> 后台同步器处理”。

设计原则：

- 自动解码结果默认不等于正式日志。
- 正式日志原则上允许人工确认。
- 需要保留足够证据支持回看和修正。
- 不依赖任何“CWCN 官方中心服务器”才能完成日志主流程。

### 9.7 数据表结构草案

表结构策略：

- 优先沿用 FT8CN 现有 `QSLTable` 作为正式日志事实表，而不是新造一张完全平行的正式日志表。
- `QslCallsigns` 可以继续作为“按呼号聚合的摘要/索引表”保留，但不作为唯一事实来源。
- CW 专属的连续流原始证据和会话草稿，新增专用表承载。
- 第三方同步相关表放到后续版本追加，不阻塞 `M4`。

#### 9.7.1 `QSLTable` 扩展方案

建议继续使用 FT8CN 的 `QSLTable` 作为 `ConfirmedQsoLog` 落库表。

保留 FT8CN 现有核心字段：

- `id`
- `call`
- `gridsquare`
- `mode`
- `rst_sent`
- `rst_rcvd`
- `qso_date`
- `time_on`
- `qso_date_off`
- `time_off`
- `band`
- `freq`
- `station_callsign`
- `my_gridsquare`
- `comment`
- `isQSL`
- `isLotW_import`
- `isLotW_QSL`

建议为 CWCN 追加字段：

- `operator_callsign`
- `name`
- `qth`
- `tx_backend`
- `rx_audio_source`
- `wpm`
- `pitch_hz`
- `decoder_confidence`
- `raw_text`
- `normalized_text`
- `operator_corrected_text`
- `qso_phase`
- `need_manual_review`
- `review_reason`
- `confirmed_by_user`
- `confirmed_at`
- `adif_snapshot`
- `created_at`
- `updated_at`

字段定稿说明：

- `M4` 阶段统一使用 `tx_backend` 表示实际发射后端，不再单独拆出 `keying_backend` 字段。
- 如果后续确实要区分“高层发射后端”和“低层键控方式”，再追加 `keying_method` 一类字段，而不是在 `M4` 阶段同时保留两个近义字段。

这样做的好处：

- 能最大化复用 FT8CN 现有 `ADIF` 导出逻辑与查询习惯。
- FT8CN 已有日志查看、过滤、导出思路可以直接平移。
- 后续即便要兼容历史 FT8CN 数据，也更容易做迁移或共存。

#### 9.7.2 `QslCallsigns` 角色定位

建议继续保留 `QslCallsigns`，但降级为辅助摘要表：

- 记录已联络过的呼号。
- 缓存最近联络时间、模式、网格、波段等摘要信息。
- 用于“已联络呼号提示”“统计”“快速查重”。

不建议：

- 让 `QslCallsigns` 承担正式日志事实表职责。
- 在摘要表中保存大量 CW 原始证据。

#### 9.7.3 `CwDecodeEvent` 新表

这张表用于替代 FT8CN `Messages` 在 CW 场景下的职责，但按连续流特性重新设计。

建议字段：

- `id`
- `session_id`
- `event_time_utc`
- `event_seq`
- `center_freq_hz`
- `audio_offset_hz`
- `snr`
- `estimated_wpm`
- `signal_level`
- `threshold_level`
- `event_type`
- `duration_ms`
- `symbol_value`
- `symbol_confidence`
- `char_candidates`
- `best_char`
- `char_confidence`
- `callsign_candidates`
- `partial_callsign_mask`
- `highlighted_spans`
- `raw_chunk_ref`
- `created_at`

说明：

- `event_type` 可表示 `tone_on`、`tone_off`、`symbol`、`char`、`word_gap` 等阶段性事件。
- `char_candidates` 允许保存多候选字符，推荐用 JSON 文本存储。
- `raw_chunk_ref` 可选，指向录音片段或环形缓冲区索引。

#### 9.7.4 `CwQsoDraftSession` 新表

这张表用于持久化“正在形成中的会话草稿”。

建议字段：

- `id`
- `session_id`
- `started_at`
- `updated_at`
- `status`
- `remote_callsign_candidate`
- `partial_callsign_mask`
- `callsign_state`
- `callsign_convergence_score`
- `station_callsign_used`
- `rst_sent_candidate`
- `rst_rcvd_candidate`
- `name_candidate`
- `qth_candidate`
- `my_gridsquare_used`
- `remote_grid_candidate`
- `band`
- `freq`
- `rx_audio_source`
- `tx_backend`
- `estimated_wpm`
- `draft_text`
- `normalized_text`
- `qso_phase`
- `confidence_score`
- `need_manual_review`
- `review_reason`
- `source_decode_event_id`
- `confirmed_qsl_id`

状态建议：

- `active`
- `pending_confirm`
- `confirmed`
- `discarded`

说明：

- `CwQsoDraftSession` 允许保存候选值和冲突值。
- 当用户确认后，由该表生成一条 `QSLTable` 正式记录。
- 正式记录一旦生成，草稿只做追溯，不再反向覆盖正式日志。
- `source_decode_event_id` 用于支持“从某条解码记录发起建草稿”的轻量工作流。

#### 9.7.5 `CwDraftEventLink` 新表

如果需要把草稿和原始证据建立可追踪关联，建议增加关联表：

- `id`
- `draft_session_id`
- `decode_event_id`
- `link_role`

用途：

- 支持从正式日志或草稿回看“这条判断基于哪些事件”。
- 为后续人工复核界面提供证据链。

如果首版希望更轻量，也可以先不建这张表，而是在 `CwDecodeEvent.session_id` 基础上做会话归属。

#### 9.7.6 `SyncOutbox` 后续扩展表

这张表属于 `M5`，不要求首版落地。

建议字段：

- `id`
- `qsl_id`
- `target`
- `status`
- `payload_snapshot`
- `retry_count`
- `last_error`
- `last_attempt_at`
- `completed_at`
- `created_at`

#### 9.7.7 `NormalizationRule` 可选配置表

如果后续希望把自定义归一化规则持久化到本地数据库，建议增加可选配置表：

- `id`
- `profile`
- `rule_type`
- `source_pattern`
- `target_value`
- `scope`
- `confidence`
- `enabled`
- `needs_review`
- `notes`
- `created_at`
- `updated_at`

用途：

- 保存用户自定义规则
- 持久化不同 `normalization_profile`
- 支持导入导出个人习惯配置
- 为 `9 -> N` 这类特例提供本地化配置入口

#### 9.7.8 索引建议

建议至少建立以下索引：

- `QSLTable(call, qso_date, time_on, mode)`
- `QSLTable(station_callsign, qso_date, time_on)`
- `QslCallsigns(callsign, startTime, finishTime, mode)`
- `CwDecodeEvent(session_id, event_seq)`
- `CwDecodeEvent(event_time_utc)`
- `CwQsoDraftSession(status, updated_at)`

#### 9.7.9 `M4` 最小落地范围

`M4` 阶段建议只落地：

- 扩展后的 `QSLTable`
- 可选保留 `QslCallsigns`
- `CwQsoDraftSession`
- `CwDecodeEvent`

`M4` 阶段可暂缓：

- `CwDraftEventLink`
- `SyncOutbox`
- 复杂的同步状态表

这样能保证首版先把“解码证据 -> 草稿 -> 正式日志 -> ADIF 导出”主链打通。

#### 9.7.10 `M4` 字段定稿

为避免实现阶段反复改名，`M4` 版本字段命名统一按以下规则收敛：

`QSLTable`：

- 保留 FT8CN 原字段名：`call`、`gridsquare`、`station_callsign`、`my_gridsquare`
- CW 追加字段定稿为：`operator_callsign`、`name`、`qth`、`tx_backend`、`rx_audio_source`、`wpm`、`pitch_hz`、`decoder_confidence`、`raw_text`、`normalized_text`、`normalization_profile`、`operator_corrected_text`、`qso_phase`、`need_manual_review`、`review_reason`、`confirmed_by_user`、`confirmed_at`、`adif_snapshot`、`created_at`、`updated_at`

`CwQsoDraftSession`：

- 候选字段统一使用 `_candidate` 后缀
- 已确定但尚未写入正式日志的本机信息，统一使用 `_used` 后缀
- 关联正式日志统一命名为 `confirmed_qsl_id`
- 未完全确认的呼号统一命名为 `partial_callsign_mask`
- 呼号收敛状态统一使用 `callsign_state` 与 `callsign_convergence_score`

`CwDecodeEvent`：

- 解码估计速度统一命名为 `estimated_wpm`
- 单条正式日志中的最终速度统一命名为 `wpm`
- 原始候选列表统一命名为 `char_candidates`
- 疑似呼号高亮相关结果统一视为“候选实体”，不直接写入正式日志事实字段

命名约束：

- 不在 `M4` 同时并存 `tx_backend` 与 `keying_backend`
- 不混用 `phase` 与 `qso_phase`
- 不混用 `my_grid_used` 与 `my_gridsquare_used`
- 不混用 `promoted_qsl_id` 与 `confirmed_qsl_id`

### 9.8 SQLite DDL 草稿

这一节给出一版偏实现导向的 SQLite 草稿。

原则：

- `QSLTable` 继续沿用 FT8CN 现有表，采用“增列迁移”，不建议首版直接重建。
- `CwDecodeEvent` 与 `CwQsoDraftSession` 作为 `M4` 新表。
- `CwDraftEventLink` 与 `SyncOutbox` 作为后续可选表。

#### 9.8.1 `QSLTable` 增列草稿

下面这些字段建议通过迁移方式追加到 FT8CN 现有 `QSLTable`：

```sql
-- FT8CN 现有 QSLTable 基础上追加
ALTER TABLE QSLTable ADD COLUMN operator_callsign TEXT;
ALTER TABLE QSLTable ADD COLUMN name TEXT;
ALTER TABLE QSLTable ADD COLUMN qth TEXT;
ALTER TABLE QSLTable ADD COLUMN tx_backend TEXT;
ALTER TABLE QSLTable ADD COLUMN rx_audio_source TEXT;
ALTER TABLE QSLTable ADD COLUMN wpm REAL;
ALTER TABLE QSLTable ADD COLUMN pitch_hz REAL;
ALTER TABLE QSLTable ADD COLUMN decoder_confidence REAL;
ALTER TABLE QSLTable ADD COLUMN raw_text TEXT;
ALTER TABLE QSLTable ADD COLUMN normalized_text TEXT;
ALTER TABLE QSLTable ADD COLUMN normalization_profile TEXT;
ALTER TABLE QSLTable ADD COLUMN operator_corrected_text TEXT;
ALTER TABLE QSLTable ADD COLUMN qso_phase TEXT;
ALTER TABLE QSLTable ADD COLUMN need_manual_review INTEGER DEFAULT 0;
ALTER TABLE QSLTable ADD COLUMN review_reason TEXT;
ALTER TABLE QSLTable ADD COLUMN confirmed_by_user TEXT;
ALTER TABLE QSLTable ADD COLUMN confirmed_at TEXT;
ALTER TABLE QSLTable ADD COLUMN adif_snapshot TEXT;
ALTER TABLE QSLTable ADD COLUMN created_at TEXT;
ALTER TABLE QSLTable ADD COLUMN updated_at TEXT;
```

说明：

- 实际实现时建议沿用 FT8CN 现有 `alterTable(...)` 迁移模式，避免重复加列时报错。
- `confirmed_at`、`created_at`、`updated_at` 推荐统一使用 UTC ISO-8601 文本。
- `need_manual_review` 用 `INTEGER(0/1)` 保持和 FT8CN 现有布尔风格一致。
- `normalization_profile` 推荐保存当前使用的解释配置名，例如 `strict_standard`、`cut_number_common`、`custom_bi9n_profile`。

#### 9.8.2 `CwQsoDraftSession` 建表草稿

```sql
CREATE TABLE IF NOT EXISTS CwQsoDraftSession (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL UNIQUE,
    started_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    status TEXT NOT NULL,
    remote_callsign_candidate TEXT,
    partial_callsign_mask TEXT,
    callsign_state TEXT,
    callsign_convergence_score REAL,
    station_callsign_used TEXT,
    rst_sent_candidate TEXT,
    rst_rcvd_candidate TEXT,
    name_candidate TEXT,
    qth_candidate TEXT,
    my_gridsquare_used TEXT,
    remote_grid_candidate TEXT,
    band TEXT,
    freq TEXT,
    rx_audio_source TEXT,
    tx_backend TEXT,
    estimated_wpm REAL,
    draft_text TEXT,
    normalized_text TEXT,
    normalization_profile TEXT,
    qso_phase TEXT,
    confidence_score REAL,
    need_manual_review INTEGER DEFAULT 0,
    review_reason TEXT,
    source_decode_event_id INTEGER,
    confirmed_qsl_id INTEGER,
    FOREIGN KEY (source_decode_event_id) REFERENCES CwDecodeEvent(id),
    FOREIGN KEY (confirmed_qsl_id) REFERENCES QSLTable(id)
);
```

#### 9.8.3 `CwDecodeEvent` 建表草稿

```sql
CREATE TABLE IF NOT EXISTS CwDecodeEvent (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    event_time_utc TEXT NOT NULL,
    event_seq INTEGER NOT NULL,
    center_freq_hz REAL,
    audio_offset_hz REAL,
    snr REAL,
    estimated_wpm REAL,
    signal_level REAL,
    threshold_level REAL,
    event_type TEXT NOT NULL,
    duration_ms REAL,
    symbol_value TEXT,
    symbol_confidence REAL,
    char_candidates TEXT,
    best_char TEXT,
    char_confidence REAL,
    callsign_candidates TEXT,
    partial_callsign_mask TEXT,
    highlighted_spans TEXT,
    raw_chunk_ref TEXT,
    created_at TEXT NOT NULL
);
```

字段说明建议：

- `event_type` 取值可先约定为 `tone_on`、`tone_off`、`symbol`、`char`、`word_gap`。
- `char_candidates` 建议先存 JSON 文本，例如 `[{"char":"A","score":0.82}]`。
- `callsign_candidates` 建议存 JSON 文本，例如 `[{"value":"BG7YOZ","score":0.91}]`。
- `partial_callsign_mask` 建议保存当前最可信的“未完全确认呼号”，例如 `BI9??Z`。
- `highlighted_spans` 建议存 UI 可消费的高亮区间，例如 `[{"type":"callsign","start":12,"end":18,"score":0.88}]`。
- `session_id` 先不强制做外键，避免会话拆分策略在早期频繁变更时牵动迁移。

#### 9.8.4 `CwDraftEventLink` 可选建表草稿

```sql
CREATE TABLE IF NOT EXISTS CwDraftEventLink (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    draft_session_id INTEGER NOT NULL,
    decode_event_id INTEGER NOT NULL,
    link_role TEXT,
    FOREIGN KEY (draft_session_id) REFERENCES CwQsoDraftSession(id),
    FOREIGN KEY (decode_event_id) REFERENCES CwDecodeEvent(id)
);
```

#### 9.8.5 `SyncOutbox` 可选建表草稿

```sql
CREATE TABLE IF NOT EXISTS SyncOutbox (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    qsl_id INTEGER NOT NULL,
    target TEXT NOT NULL,
    status TEXT NOT NULL,
    payload_snapshot TEXT,
    retry_count INTEGER DEFAULT 0,
    last_error TEXT,
    last_attempt_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (qsl_id) REFERENCES QSLTable(id)
);
```

#### 9.8.6 `NormalizationRule` 可选建表草稿

```sql
CREATE TABLE IF NOT EXISTS NormalizationRule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    profile TEXT NOT NULL,
    rule_type TEXT NOT NULL,
    source_pattern TEXT NOT NULL,
    target_value TEXT,
    scope TEXT NOT NULL,
    confidence REAL DEFAULT 1.0,
    enabled INTEGER DEFAULT 1,
    needs_review INTEGER DEFAULT 0,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

#### 9.8.7 索引 DDL 草稿

```sql
CREATE INDEX IF NOT EXISTS idx_qsltable_call_date_mode
ON QSLTable(call, qso_date, time_on, mode);

CREATE INDEX IF NOT EXISTS idx_qsltable_station_date
ON QSLTable(station_callsign, qso_date, time_on);

CREATE INDEX IF NOT EXISTS idx_qslcallsigns_callsign_time_mode
ON QslCallsigns(callsign, startTime, finishTime, mode);

CREATE INDEX IF NOT EXISTS idx_cwdecodeevent_session_seq
ON CwDecodeEvent(session_id, event_seq);

CREATE INDEX IF NOT EXISTS idx_cwdecodeevent_time
ON CwDecodeEvent(event_time_utc);

CREATE INDEX IF NOT EXISTS idx_cwqsodraft_status_updated
ON CwQsoDraftSession(status, updated_at);

CREATE INDEX IF NOT EXISTS idx_cwqsodraft_confirmed_qsl
ON CwQsoDraftSession(confirmed_qsl_id);

CREATE INDEX IF NOT EXISTS idx_normalizationrule_profile_type
ON NormalizationRule(profile, rule_type, enabled);
```

#### 9.8.8 `M4` 建议实际落地顺序

1. 迁移扩展 `QSLTable`。
2. 建 `CwQsoDraftSession`。
3. 建 `CwDecodeEvent`。
4. 建必要索引。
5. 跑通“草稿确认 -> `QSLTable` -> `ADIF` 导出”主流程。

`M4` 阶段不强求：

- `CwDraftEventLink`
- `SyncOutbox`
- `NormalizationRule`
- 复杂外键约束
- 过早把所有候选数据都结构化拆表

首版目标是先把模型跑稳，而不是一开始就把数据库做成最复杂形态。

## 10. UI 设计方向

建议将 UI 分为四个主工作区：

### 10.1 Radio

- 机型选择
- 连接方式
- 控制方式
- 音频源
- 发射方式
- 参数配置

### 10.2 Decode

- 窄带频谱或瀑布
- 连续滚动文本
- 候选字符
- 置信度标记
- 当前跟踪频点
- 当前估计 WPM
- 疑似呼号高亮
- 从单条解码结果发起“创建草稿 / 填入呼号”
- 部分呼号占位显示，如 `BI9??Z`
- 归一化配置切换，如 `strict_standard / 9->N / custom`

#### 10.2.1 疑似呼号点击菜单

当用户点击某条解码结果中的疑似呼号高亮片段时，建议弹出轻量操作菜单：

- `创建新草稿`
- `填入当前草稿`
- `替换当前草稿呼号`
- `标记为误识别`
- `查看候选呼号列表`
- `以部分呼号创建草稿`

交互建议：

- 如果当前没有活动草稿，默认主操作为 `创建新草稿`。
- 如果当前已有活动草稿，默认主操作为 `填入当前草稿`。
- `替换当前草稿呼号` 属于高风险操作，建议二次确认或明显提示。
- `标记为误识别` 后，可降低该片段后续高亮优先级，但不删除原始解码证据。
- `查看候选呼号列表` 时，应展示候选值和置信度，并允许用户手工输入修正。
- 当只有部分呼号时，应允许用户先接受占位值，后续再收敛修正。

#### 10.2.2 归一化配置

建议在解码区或设置页提供轻量的“归一化配置”入口。

首版建议至少支持：

- `Strict Standard`
- `Common Cut Number`
- `Custom Mapping`

建议在配置说明中明确：

- `Strict Standard`：只做最保守解释，不主动扩展非标准写法
- `Common Cut Number`：启用 `5NN -> 599`、`ENN -> 599` 这类常见规则
- `Custom Mapping`：允许用户自行追加诸如 `9 -> N` 的特殊约定

配置能力建议：

- 开关 `9 -> N`
- 开关 `N -> 9`
- 开关 `0 -> T`
- 开关 `T -> 0`
- 允许保存为命名配置档
- 允许查看当前配置档命中的规则清单
- 允许编辑自定义规则字典

交互原则：

- 切换配置后，不覆盖 `raw_text`。
- 切换配置后，允许重新生成 `normalized_text` 和候选实体。
- 若配置切换会影响当前草稿呼号判断，应给出提示而不是静默覆盖。
- 常见报号归一如 `5NN -> 599` 应作为默认容错，不要求用户手动开启。

建议的配置页信息结构：

- 当前激活配置档
- 默认规则列表
- 自定义规则列表
- 单条规则启用/停用
- 规则作用域说明
- “此规则会影响 callsign 判断”的风险提示

### 10.3 TX

- Typing 输入框
- 宏按钮
- Repeat
- 暂停/恢复/回退
- 已发/待发内容

### 10.4 Log

- 当前 QSO 草稿
- 正式日志
- ADIF 导入导出
- 第三方同步状态与手动重试入口
- 统计与查询

#### 10.4.1 ADIF 导出交互

建议保留 FT8CN 已有的双路径导出体验：

- `分享导出`：按当前筛选条件生成 `.adi`，直接调起系统分享面板。
- `本地 Web 导出`：通过内置本地 HTTP 服务提供下载入口，方便在同一局域网设备上获取日志文件。

交互目标：

- 对 FT8CN 老用户保持熟悉感。
- 同时满足“手机内直接分享”和“局域网浏览器下载”两类使用习惯。
- `CWCN` 首版不需要重新设计一套更复杂的导出向导。

#### 10.4.2 草稿面板自动填充规则

建议把草稿字段分为三类：

自动填充：

- `remote_callsign_candidate`
- `partial_callsign_mask`
- `callsign_state`
- `callsign_convergence_score`
- `station_callsign_used`
- `band`
- `freq`
- `rx_audio_source`
- `tx_backend`
- `estimated_wpm`
- `draft_text`
- `normalized_text`
- `qso_phase`
- `source_decode_event_id`

自动推荐但需人工确认：

- `rst_sent_candidate`
- `rst_rcvd_candidate`
- `name_candidate`
- `qth_candidate`
- `remote_grid_candidate`

默认不自动落正式日志：

- 任何低置信度 callsign 候选
- 任何仅有部分占位且未经人工确认的呼号
- 任何 `callsign_state = conflicting` 的呼号
- 任何存在冲突的 `RST / Name / QTH`
- 基于上下文猜测但没有明确证据支撑的字段

确认策略建议：

- 呼号一旦由用户修正，应锁定为当前草稿主值，后续自动识别只能作为候选提示。
- 当草稿由单条解码结果发起时，允许先只保存“呼号 + 时间/频率 + 原文片段”。
- 当只有部分呼号时，允许先保存 `partial_callsign_mask`，但不能直接落正式日志的 `remote_callsign`。
- 当 `callsign_state = locked_manual` 时，后续自动识别不得覆盖正式呼号主值。
- 其余字段可在后续解码过程中持续补全。
- 进入正式日志前，至少应让用户确认 `remote_callsign`、时间、频率或波段。

UI 原则：

- “机器猜测”和“人工确认”分开展示。
- 不支持字符必须明确标红提示。
- 接收内容和发送内容应呈现为时间线，而不是混在一块。
- 高精度解码场景下，必须允许用户查看候选字符、原始时序片段和低置信度标记。
- 疑似呼号高亮是辅助识别功能，不应伪装成已确认事实。
- 用户应能直接点击某条解码结果中的疑似呼号，高效发起或修正 QSO 草稿。

## 11. 复用 FT8CN 的策略

### 11.1 优先复用

- 连接抽象
- 机型适配骨架
- 音频统一入口
- 日志数据库
- ADIF 导入导出
- Callsign / Maidenhead / 统计体系
- Web 下载和导入能力

### 11.2 优先重写

- `ft8listener/`
- `ft8transmit/GenerateFT8.java`
- `ft8transmit/FT8TransmitSignal.java`
- FT8 专用 native 编解码链路

### 11.3 结论

应复用的是：

- FT8CN 的无线电 App 骨架

应重建的是：

- CW 专属接收
- CW 专属语义解释
- CW 多后端发射

## 12. 研发里程碑建议

### M1 可运行监听版

- 完成连接层接入
- 完成统一收音
- 完成基础频谱显示
- 支持人工监听记录
- 建立最小可用的回放与评测框架

### M2 可发送版

- 完成 Text-to-CW 核心发送链路
- 支持 `RTS`
- 支持 `DTR`
- 支持音频 `VOX`

### M3 高精度解码基础版

- 完成连续流解码
- 完成 `CwTimingModel`
- 实现多候选字符解码
- 支持候选字符与置信度显示
- 将自适应 `WPM`、高噪声/QSB/真人手法鲁棒性明确纳入同一高精度解码目标

### M4 半自动 QSO 版

- QSO 阶段识别
- 草稿生成
- 人工确认后写日志
- 本地优先的正式日志模型落地
- `ADIF` 导出能力落地

### M5 智能增强版

- 缩略语解释
- 句式推荐
- 自动回复循环
- 第三方同步增强
- `SyncOutbox` 队列落地
- Cloudlog / QRZ 适配器完善

## 13. 当前已确认决策

- 项目定位为“安卓端 CW 工作台”。
- 技术上采用“复用 FT8CN 骨架，重写 CW 核心”的路线。
- 接收链路采用连续流，而不是固定时隙。
- 高精度解码是核心目标，不是后期附加优化项。
- 自适应 `WPM` 与高噪声/QSB/真人手法鲁棒性属于同一高精度解码目标，不单列为独立诉求。
- 解码器架构从第一天起按 `CW Skimmer` 级别目标设计。
- 发送链路统一称为“文本驱动发射 / Text-to-CW”。
- “直接发文本给电台 keyer”不是总目标，只是可选后端之一。
- `VOX` 作为兼容方案保留，但不作为首选。
- 正式 QSO 记录要保留人工确认空间。
- QSO 管理采用“原始事件 / 草稿 / 正式日志 / 同步队列”四层模型。
- `ADIF` 导出优先级高于第三方平台集成。
- 数据表结构策略采用“正式日志层优先参考 FT8CN，CW 原始事件层与草稿层自行设计”。
- 第三方平台同步采用可插拔适配器和异步队列，不绑定官方中心服务器，并可在后续版本追加。
- Android 工程的根命名空间约定为 `org.bi9clt.cwcn`。
- `namespace`、`applicationId`、Java/Kotlin 包路径与后续新增模块目录都应默认与 `org.bi9clt.cwcn` 保持一致，除非后续有明确迁移决策。
- 应用图标采用独立的 `CWCN` 品牌图标，不直接沿用 `FT8CN` 图标。
- 当前图标母版保存在 `assets/branding/cwcn-icon.svg`，Android 接入版资源保存在 `assets/branding/android/`。

## 14. 下一步讨论建议

后续可以优先继续收敛以下主题：

1. 首版一定要支持哪些电台与连接方式。
2. `CwSignalProcessor` 的 v1 算法边界。
3. `CwTxEngine` 的统一接口是否还要细化。
4. 日志表结构是否直接扩展现有 FT8CN 模型。
5. 首版 UI 信息密度和交互优先级。

## 15. 图标与品牌资源

### 15.1 当前结论

- `CWCN` 需要自己的独立图标，原因不是“为了和 `FT8CN` 强行区分”，而是产品语义已经从固定时隙数字模式转向“CW 工作台”。
- 图标主语义采用“电键 + 无线电信号”，优先传达 `CW` 发报、监听、解码三件事，而不是泛化成普通聊天或普通工具图标。
- 视觉方向以深色底、黄铜电键、青绿色信号元素为主，兼顾电台器材感与现代 Android 图标识别度。

### 15.2 资源落点

- 主母版：`assets/branding/cwcn-icon.svg`
- Android 接入版说明：`assets/branding/android/README.md`
- Android 自适应图标入口：`assets/branding/android/res/mipmap-anydpi-v26/ic_launcher.xml`
- Android 前景矢量：`assets/branding/android/res/drawable/ic_cwcn_launcher_foreground.xml`
- Android 单色图标：`assets/branding/android/res/drawable/ic_cwcn_launcher_monochrome.xml`

### 15.3 工程接入策略

- 在 `CWCN` 自己的 Android 工程建立后，优先直接接入自适应图标资源，不再沿用 `FT8CN` 的 `drawable/ft8cn_icon` 做过渡。
- `AndroidManifest.xml` 使用 `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`。
- Android 13 及以上保留 `monochrome` 单色图标支持。
- 在工程初期即可固定图标命名，避免后续包名、资源名、截图和文档反复回改。

### 15.4 导出原则

- `SVG` 母版作为唯一视觉源头，后续所有 `PNG/WebP`、启动图、商店图均从此母版派生。
- 工程接入版允许为了 `VectorDrawable` 做简化，但不能改变主语义。
- 位图导出规格单独维护在 `assets/branding/EXPORT_SPEC.md`。
## 16. Unknown Decode Echo

- `CwDecoder` 必须区分真实问号和解码失败字符。
- 真实的摩尔斯问号 `..--..` 继续回显为 `?`。
- 无法映射到已知摩尔斯表的字符，统一回显为 `□`。
- `□` 的选择原则是：它不属于摩尔斯常用字符集合，并且能和真实 `?` 明确区分。
- 每个解码事件都应保留原始点划序列；对于 `□`，这条信息尤其重要，便于调试、UI 展示和人工修正。
- 示例可见文本：`BI9□LT ? K`
- 示例原始细节：其中 `□` 对应原始序列例如 `.-.-`
- 后续解释层和 QSO 语义层可以把 `□` 当作一种“不确定字符”参与容错，但转录层必须保留它与真实 `?` 的差异。
- 测试至少需要锁定：
- `..--.. -> ?`
- 未知序列例如 `.-.- -> □`
- `□` 对应事件保留原始 `sourceSequence`
