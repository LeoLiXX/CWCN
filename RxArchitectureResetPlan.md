# RX Architecture Reset Plan

Last updated: 2026-05-20

## 目标

这份文档只保留三类信息：

- 当前仍然成立的结构性结论
- 已经证伪、应停止继续投入的错误做法
- 接下来真正要落地的新 RX 方案

历史实验细节、阶段性岔路、彼此冲突的中间判断，不再继续保留在主文档里干扰实现。

## 当前工作边界

从现在开始，这一轮主线只聚焦：

- `RX -> RAW`

更具体地说：

- 可以继续分析整条 live 链路
- 但当前只把“影响 `CwDecoder.outputText()` 的问题”计入 RX 主线

当前明确纳入范围的部分是：

- `AudioInputHealthTracker`
- `CwSignalProcessor`
- `LiveRxToneEventStabilizer`
- `CwHybridTimingModel`
- `LiveRxWpmGuard`
- `CwDecoder`

当前明确不纳入主线范围的部分是：

- `CwInterpreter` 对 RAW 的再切词、再合并、再拆分
- callsign candidate 恢复与排序
- phrase hint / QSO semantics 推断
- 各类基于字符串形态的 semantic repair

执行纪律应固定为：

- 如果某个 case 的 `rawText / outputText` 已经对了
  - 但 `normalizedText`、callsign、语义提示仍然不理想
  - 这不再算 RX 问题
- 如果某个 case 的主要症状是：
  - 起音没进来
  - tone 锁偏
  - release / valley / continuity / retune 判断错误
  - WPM learner 被错误事件带偏
  - gap 被分错导致 RAW 本身断错或粘错
  - 这些才继续属于 RX 主线

这条边界不是说：

- `CwInterpreter` 不重要

而是说：

- 它属于下一层问题
- 在当前阶段不应再与 RX 主线混在一起推进

## 2026-05-20 当前扫描结论

这一轮重新扫过之后，需要把下面几类结果明确分开：

### 1. 当前可以继续当作 RX 主线健康门的验证集合

下面这组回归已经再次跑过：

- `LiveRxWpmGuardTest`
- `TimingAnchorControllerTest`
- `CwRecording16LiveCommittedRawRegressionTest`
- `CwLocalAudioLiveLikeRegressionTest`
- `CwToneSweepTurnContinuityRegressionTest`

当前结果是：

- 现在五组都通过

因此如果只看：

- `RX -> RAW`
- 且以当前真实链路/主录音 regression 为主

那么主线已经不是“大面积失控”，而是：

- 当前代表性健康门已经恢复全绿
- 剩下的问题主要落在更宽的 robustness / stress sweep，而不是主链路健康门

### 2. 当前不应与 RX 主线健康度混算的失败集合

下面这些失败，不能直接拿来判定“RX 主线是否已坏”：

- `CwFixturePipelineRegressionTest`
  - 这里混有 interpreter / phrase hint / callsign / QSO semantics
  - 已超出本轮 `RX -> RAW` 边界
- `CwSpeedSweepLiveLikeRegressionTest`
  - 这是短窗口故意变速 fixture
  - 已明确降级，不再继续作为本轮核心驱动 case
- `CwPreferredOffsetMatrixRegressionTest`
- `CwFastPreferredOffsetMatrixRegressionTest`
  - 这两组大量覆盖 `preferred tone` 与真实音调显著偏离的组合
  - 更像 robustness / stress matrix
  - 不能直接等同于当前生产主链路的 release gate

### 3. `LocalAudioFolderRegression` 当前更适合做审计矩阵，不适合继续当硬门

当前 `CwLocalAudioFolderRegressionTest` 里混放了三种不同性质的约束：

- 严格全文 exact match
- 实用 soft floor
- 仅可观测性 / 不崩塌检查

但我们这轮已经明确同意过：

- `recording(7)` 当前仍以 `Fixed` 输出的关键信息更有价值
- `recording(10) / (14) / (15)` 不应再为了全文完美去牵着 RX 主线跑
- `recording(12)` 当前仍属于弱 QSB / 中段信息不稳 case，不适合拿严格全文断言硬卡
- `20260427*` 这两条脏录音主要是噪声/错音调竞争问题，本来也不该和健康主样本同权重混算

补充一条本轮重新实测后的正式结论：

- `recording(12)` 当前可以判定为：
  - `USER_FIXED_ACCEPTABLE`
- 也就是说：
  - 它暂时不应继续作为 `live-default / auto-track` 主线 driver
  - 但如果产品允许用户手动选择 `preferred fixed = 700 ±30Hz`
    - 这个 case 已经可以接受降级

当前分支重新实测得到的是：

- `HYBRID_DEFAULT, sql=55`
  - `recall=0.724`
  - `primary=2/4`
- `HYBRID_DEFAULT, sql=15`
  - `recall=0.690`
  - `primary=1/4`
- `STATIC_FIXED @ 700 ±30Hz, sql=55`
  - `recall=0.897`
  - `primary=3/4`
- `STATIC_FIXED @ 700 ±30Hz, sql=15`
  - `recall=0.793`
  - `primary=2/4`

这说明：

- 当前分支支持：
  - `preferred fixed = 700 ±30Hz`
    作为 `recording(12)` 的产品兜底方案
- 当前分支不支持：
  - “必须把 SQL 再继续压低，`recording(12)` 才更容易过”

所以这组测试现在更适合承担：

- 当前输出快照审计
- 关键信息是否还在
- tone / WPM / observability 是否崩塌

而不适合继续承担：

- “所有本地录音都必须全文 canonical exact match”

补一条已经收住的实施约束：

- startup constrained preferred learn
  - 当前只允许作为开发者提示层存在
  - 现在的接入方式应固定为：
    - 仅基于 startup raw spectrum 的人工建议
    - 只展示在 developer / local replay 工具里
  - 明确不允许：
    - 写回共享 `RxReplayAnalysisResult`
    - 冒充 shared replay 已验证结论
    - 直接驱动 live RX 自动改频

### 4. 当前已经验证收敛的一条前端脏点

这轮已经确认并收住的一条前端问题是：

- tone sweep low-edge tail 里
- `representativeLockedToneFrequencyHz` 旧锚点会在新 target 已回到低频后继续滞留
- 短 post-release gap 一来
- trusted-anchor / continuity 路径会把 authority 拉回旧 anchor

已经实际确认过的旧症状是：

- live `target` 已回到 `420 / 410Hz`
- `rep` 仍停在 `801Hz`
- gap 一来 target 会再次被旧 `801Hz` 抢回
- 最终尾部 `SK` 会掉失

当前已经验证的修复结果是：

- pre-frame attack anchor 在短 post-release gap 内会优先继承最近稳定 target
- 上述 tail window 里 target 不再被立即拉回 `801Hz`
- `toneSweepRestoresSkTailAcrossShortLowEdgeGaps` 已恢复通过

这说明：

- stale representative authority 的“tail 抢权”问题已经被单独隔离并收住

### 5. 当前仍然存在、但不应误算为主链路失控的问题

当前还没有解决的是：

- 更宽的 multi-tone / preferred-offset stress 下
- 全段 recall 仍不稳定

典型表现是：

- tone sweep 全文仍未完整恢复
- preferred-offset matrix 在大偏移组合下仍会掉到低 recall

这类问题目前应归类为：

- robustness / stress sweep

而不是：

- 当前代表性 RX 主链路健康门失败

### 6. 场景优先级应服从真实使用面，而不是服从最苛刻 fixture

后续决定投入顺序时，应固定遵守下面这个优先级原则：

- 如果某个 case 明显贴近真实使用场景
  - 例如真实录音
  - 例如用户实际会遇到的弱 QSB / 邻频 / 错频 / 起音偏弱
  - 例如真实机内 preferred tone 与收音链路基本一致
  - 这类 case 优先级更高
- 如果某个 case 明显偏离真实使用场景
  - 例如故意做的极端 preferred-offset 全矩阵
  - 例如短窗口连续大幅变速
  - 例如为了放大某种机制而拼出来的 synthetic stress fixture
  - 这类 case 可以继续保留用于审计
  - 但应降低优先级

也就是说：

- 这类 case 仍然有价值
- 但它们的价值主要是：
  - 揭示脆弱性
  - 防止以后误回退
  - 帮助理解边界条件

而不是：

- 主导当前 RX 主线的取舍
- 逼着主链路为不典型场景做过度妥协

### 当前已验证的 RAW 边界结论

下面几条已经通过 fixture trace 拿到过具体 `gap ratio` 数字，后续不应再混为一谈：

#### 1. `4.x dot` 的临界粘连，仍然属于值得继续收的 RX 边界问题

代表 case：

- `near_frequency_narrowband_noise_report`

已确认：

- `DE|BG7YOZ`
- `5NN|BK`

这两处边界 gap 大约落在：

- `4.63 ~ 4.98 dot`

它们不是“完全没有边界证据”，而是：

- 已经接近 word-gap
- 但略低于原先过硬的 soft promotion 门限

因此这类问题适合继续通过：

- `CwDecoder` 侧的保守 soft-word-break promotion

来收敛，而不需要重新打开更大的 interpreter 侧修词工作。

#### 1.5. `2.39 dot` 的 boundary-opening 过度粘连，仍然属于 RX 内部可修复问题

代表 case：

- `human_hesitation_gap_report_exchange`

已确认在 `BI9|CLT` 里曾出现过：

- `----.-.-.`

这不是一个“没有任何边界证据的全胶合串”，而是：

- `2734ms` 处存在一个真实 gap
- `duration=182ms`
- `trusted dot=76ms`
- ratio 大约是 `2.39 dot`

trace 已确认这条边界在：

- raw 分类
- `LiveRxWpmGuard`

两层都还能保持为：

- `LETTER_GAP`

真正把它压回去的是：

- `TimingAnchorController` 的 boundary-opening hold

也就是说，这类问题的本质不是：

- 需要靠语义去猜 `9C`

而是：

- opening hold floor 过高，把一个已经具备边界证据的早期 letter-gap 又错误压回了 `INTRA_SYMBOL_GAP`

因此这条线应明确归类为：

- 仍值得继续收的 RX 内部边界问题

而不是：

- hesitation false-space
- 或 interpreter / callsign repair

同时也要与后半段那类：

- `5NN -> 5 NN`
- `BK -> B K`

的 `6.x ~ 8.x dot` 假词间隔分裂明确分开看待。

这里还需要补一条使用约束：

- `human_hesitation_gap_report_exchange` 在 fixture 定义里本来就显式注入了额外长停顿
- 具体是 `PartTimingProfile(..., 5.8d, Arrays.asList(17, 20))`
- 并且描述里已经写明：`5` 与 `B` 后面会被故意拉长，制造 `5 NN / B K`

因此这个 fixture 现在只适合继续承担：

- `BI9|CLT` opening boundary 粘连修复的 RX stress case

不再适合被当成：

- “自动键控、无犹豫发送，本不该出现 `5 NN / B K`” 的证据

如果后续要继续判断：

- `5NN|BK` 是否被 RX 从正常发送中错误裂开

应优先改看：

- 无 hesitation 注入的 fixture
- 或真实录音样本

#### 2. `5.5 ~ 8.5 dot` 的人工犹豫分裂，不是同一个门槛问题

代表 case：

- `human_hesitation_clarification_flow`
- `human_split_short_tokens_report_exchange`

已确认：

- `AGN`
- `PSE`
- `5NN`
- `BK`

这些被拆开的地方，gap 已经大致落在：

- `5.5 ~ 8.5 dot`

并且多数已经在 timing 层被正式判成：

- `WORD_GAP`

因此它们不是：

- “soft word break promotion 不够积极”

而更像：

- 人工手拍导致的超长字内犹豫
- 以及当前 timing/gap 语义对这类人类发送风格过于字典化

更重要的是，trace 已经确认：

- 在这些 case 里
- false-space 与 true word-space
- 在 trusted cadence / boundary anchor 建立之后
- 仍然都会稳定落在 `6.x ~ 7.x dot` 一带
- 并且 raw -> `LiveRxWpmGuard` -> `TimingAnchorController`
  三层分类都继续保持 `WORD_GAP`

这意味着当前这条线至少已经可以明确排除：

- “只是 raw dot 估计飘了，所以被重锚后本来应该回到 letter-gap”

也就是说，如果还想继续把这类 false-space 在 RAW 层彻底消掉：

- 就不能再指望单纯调整 gap ratio 阈值
- 否则会直接压到同一批 case 里的真实 word gap

这条线如果要继续做，必须明确知道：

- 这已经不是简单再挪一个 promotion 阈值就能解决

#### 3. `2.3 ~ 3.3 dot` 的全胶合 compact chain，单靠 RX timing 基本无足够证据

代表 case：

- `fully_glued_ack_closing_chain`
- `human_compact_ack_closing_chain`
- `human_compact_report_tail_followup`

已确认很多期望边界只落在：

- `2.3 ~ 3.3 dot`

这个范围与正常 letter-gap 已高度重叠。

因此对这类 case，应明确承认：

- 仅凭当前 RX/timing 证据
- 很多边界本来就难以被可靠恢复

也就是说，这类问题如果继续强行要在 RAW 层完全拆开：

- 很容易演变成把 interpreter/token 语义修补偷偷搬回 decoder

当前主线不应这样做。

## 当前保留的有效结论

### 0. 速度建模必须满足四条硬约束

这是当前 RX 重构必须明确遵守的现实约束。

#### 约束 1. RX 启动后应快速逼近可解码区间

理想情况不是：

- 长时间在错误 WPM 附近徘徊
- 再慢慢试错

而是：

- 在进入一段新的 CW 信息流后
- 尽快逼近一个“已经足够可解码”的速度区间

因此后续内部状态里，`WPM` 不应只被当成一个最终显示值，
而应当被当成：

- 当前局部上下文中对发送速度的实时估计

这个估计值可以是：

- 一个连续的小数

因为它本质上更像：

- 当前 posterior mean / running estimate

而不是一个必须离散跳变的整数档位。

#### 约束 2. 同一段 CW 信息流中，WPM 应基本稳定

在同一段对话里，无论是：

- PC keying
- 还是同一个 OP 手工发送

速度通常都只会在一个较小范围内波动。

因此合理模型应该是：

- 一旦系统已经在该段信息流里学到一个可信速度
- 后续更新应当明显偏向“稳定当前值”
- 只有在证据持续充分时，才允许它缓慢漂移

换句话说：

- 在同一个局部上下文里，`WPM` 应是低波动量
- 而不是一个可以被短时噪声、弱谷或爆音轻易带飞的量

#### 约束 3. 一段 CW 结束，应视为一个局部上下文结束

这是这轮重构里最重要的新约束之一。

过去的问题在于：

- 旧 RX 倾向于把整个接收过程当成一个长上下文
- 导致上一段发送习惯、上一位 OP 的速度特征
  会继续污染下一段

新的正式假设应是：

- 一段 CW 信息流结束
- 就意味着一个局部接收上下文结束

新的信息流到来时，应进入新的局部上下文。

这意味着：

- 同一段内部，应强烈保留已学到的稳定速度
- 段与段之间，应允许重新学习
- 不同发送习惯之间，不应再共享同一个长期 learner 状态

这也是后文 `epoch` 概念的现实含义：

- `epoch` 不是机械按时间切片
- 而是“一个局部 CW 对话上下文”

补充说明：

- `Turn` 硬隔离现在应被视为
  - 一个当前默认策略
  - 而不是不可动摇的教条
- 2026-05-16 对 `录音2` 与 `capture.wav` 做的 `carry on/off` A/B 已确认：
  - 开启 cross-turn carry 只会改后续 turn 的 seed
    - 例如 `录音2 Turn2: 15 -> 27`
    - `capture Turn2/Turn3: 15 -> 23 / 21`
  - 但并没有把 trust 建立时间提前
  - 也没有改善最终 RAW 文本
- 因此当前结论是：
  - 可以保留 carry 作为实验开关
  - 但默认主线仍应保持 fresh-seed turn reset
  - 直到某个真实 fixture 明确证明 carry 有净收益

#### 约束 4. TX 发送速率只能作为非常弱的先验

TX 速率不是无用，但它只能是：

- 一个很弱的初始参考起点

它不能成为：

- 硬锚点
- 长期强约束
- 或在弱证据下反复把 RX 拉回去的主导量

因为现实里：

- 对方未必跟你的 TX 一样快
- 同一场景下也可能切换不同发送者

因此正式原则是：

- `TX WPM` 只在新 epoch 的 bootstrap 初期提供弱先验
- 一旦局部上下文内已经建立可信速度
- `TX WPM` 就必须迅速退居次要位置

### 1. `WPM runaway` 不是主根因

它是宏观症状，不是最早发生的错误。

真正更靠前的问题在：

- front-end segmentation
- weak valley / release / onset 边界处理
- learner 被坏事件持续污染

### 2. live 路径与旧离线 bench 不等价

真实链路至少包含：

- `AudioInputHealthTracker`
- `CwSignalProcessor`
- `LiveRxToneEventStabilizer`
- `CwHybridTimingModel`
- `LiveRxWpmGuard`
- `CwDecoder`
- `CwInterpreter`

旧离线回放大量只覆盖：

- `CwSignalProcessor`
- `CwHybridTimingModel`
- `CwDecoder`

因此：

- “离线看起来没问题”不等于“真机没问题”
- 后续验证必须优先看 live-like replay 与真机可复现问题

### 3. 手机 Mic 不是边缘场景，而是主设计约束

真机 Mic 暴露出的：

- 共振
- 近讲失真
- 电平变化
- burst / cough / weak valley

不是附属兼容性问题，而是当前 RX 最脆弱的真实输入约束。

### 4. 频点跟踪大体不是主病灶

目前多轮 probe 都支持：

- 主 tracked tone 大体仍在目标频点附近
- 当前主要坏掉的是：
  - run segmentation
  - gap meaning
  - learner update authority

所以主线不应再优先围绕“重新找频点”打转。

### 4.1 `20260427_222505 / 20260427_224524` 是当前少数“频点选择例外”

这两个 dirty case 需要单独记一条例外，不然很容易被前面的总判断误导。

当前 probe 已明确显示：

- `20260427_222505`
  - live `HYBRID_BOOTSTRAP`
    - `recall=0.400`
  - `STATIC_FIXED_TONE`
    - `0.533`
  - `FIXED_UNTIL_TRUST`
    - `0.567`
  - 强制固定频点 replay
    - `660Hz -> 0.700`
    - `740Hz -> 0.733`
- `20260427_224524`
  - live `HYBRID_BOOTSTRAP`
    - `0.318`
  - `FIXED_UNTIL_TRUST`
    - 反而降到 `0.045`
  - 强制固定频点 replay
    - `660Hz -> 0.682`
    - `740Hz -> 0.773`

这说明：

- 对大多数 case 来说
  - 频点跟踪不是主病灶
- 但对这两个 dirty case 来说
  - “最终落到哪个频点”
  - 的确已经成为主要收益杠杆之一

进一步的结构结论是：

- `20260427_222505`
  - 当前更像：
    - opening/bootstrap 阶段放开偏晚
    - 再叠加 continuity / locked-retune 对远频候选的过强压制
- `20260427_224524`
  - 当前更像：
    - preferred 附近候选本身就不稳
    - wide candidate 即使出现，也难以稳定完成资格化

因此这两个 case 的下一步主线不是：

- 再抠通用 WPM learner
- 再堆 generic decoder repair

而是：

- front-end 选频仲裁
- far candidate 的资格化与提交条件
- 以及 fixed/bootstrap 模式在脏样本中的退出纪律

#### 4.1.0 用户侧 fallback 结论：这两个 dirty case 值得保留“人工锁频”模式

从当前数字看，这里已经不只是“也许可以试试”的程度，而是：

- 现有 `FIXED_TONE` 主链本身
  - 就已经接近“用户手动锁定一个中心音调，再在其附近窄窗跟踪”的行为
- 而 `20260427_222505 / 20260427_224524`
  - 恰好又是当前少数确实能从人工选频里获得大收益的 case

因此当前应明确记住：

- “人工锁频”不是新的 DSP 主线
- 但它非常适合作为：
  - 脏样本
  - 音调已知
  - 或用户能从频谱 / 人耳快速判断主峰
  - 时的用户可选 fallback

当前可直接引用的收益数字：

- `20260427_222505`
  - live `HYBRID_BOOTSTRAP`
    - `0.400`
  - `STATIC_FIXED_TONE @ pref=700`
    - `0.533`
  - 强制固定频点 replay
    - `740Hz -> 0.733`
- `20260427_224524`
  - live `HYBRID_BOOTSTRAP`
    - `0.318`
  - 当前默认 `pref=700` 的 `STATIC_FIXED_TONE`
    - 没有形成明显收益
  - 但只要把人工中心频率调到更接近真实峰值
    - `640Hz -> 0.864`
    - `680Hz -> 0.864`
    - `760Hz -> 0.909`

所以这条结论的正确使用方式是：

- 不把它误判成“auto-track 已经不重要”
- 而是承认：
  - 对绝大多数普通 case
    - 仍应优先把自动链路做好
  - 但对少数高噪、错峰、带脉冲爆音的 dirty case
    - 用户人工锁一个音调中心
    - 很可能比继续让系统自行挣扎更有效

#### 4.1.0.1 如果真实使用能把 RX 音频主动对到目标音调附近，那么锁频窗口应允许收窄

2026-05-19 又补了一轮更贴近真实使用的 probe：

- 前提不是“中心频率乱飘、完全未知”
- 而是：
  - 用户会把 RIG 接收音频主动对齐到自己的目标 tone
  - 因此实际信号大概率本来就在 `700Hz` 上下

在这个前提下，继续把人工锁频窗口放成默认 `±50Hz`
并不一定是最好的。

`CwDirtyCaseFixedToneLearningWindowProbeTest` 当前结果：

- `20260427_222505`
  - `HYBRID_DEFAULT`
    - `0.400`
  - `STATIC_FIXED @ ±30Hz`
    - `0.733`
  - `STATIC_FIXED @ ±50Hz`
    - `0.533`
  - `STATIC_FIXED @ ±70Hz`
    - `0.500`
- `20260427_224524`
  - `HYBRID_DEFAULT`
    - `0.318`
  - `HYBRID @ ±30Hz`
    - `0.682`
  - `STATIC_FIXED @ ±30Hz`
    - `0.682`
  - `STATIC_FIXED @ ±50Hz`
    - `0.318`
  - `STATIC_FIXED @ ±70Hz`
    - `0.318`

这条结果非常关键，因为它说明：

- 对这类 dirty case
  - 问题不只是“有没有人工锁频”
- 还包括：
  - “人工锁频的学习窗口是否收得足够窄”

当前阶段最稳妥的产品结论应是：

- 保留人工锁频为用户可选 fallback
- 并且允许用户调节其学习窗口
- 默认值暂时不急着贸然全局改窄
  - 因为还没有做完更大范围回归
- 但对于：
  - 用户已知音调
  - 且实际链路可主动把 RX 音频对到目标 tone
  - 的正式使用场景
  - `±30Hz` 已经值得优先作为推荐档位

#### 4.1.0.2 但 `±30Hz` 不能被误当成“人工锁频通用推荐档”

上面的 dirty-case 结果虽然很好，但后续又补了一轮代表性回归，
专门检查：

- `recording(7)`
- `recording(10)`
- `recording(14)`
- `recording(15)`

并且尽量按更合理的中心频率来跑：

- `recording(7) @ 700Hz`
  - 保留历史 fixed 基线
- `recording(7) @ 600Hz`
  - 更接近人工听感判断
- `recording(10) @ 500Hz`
- `recording(14) @ 800Hz`
- `recording(15) @ 800Hz`

`CwManualFixedToneRepresentativeProbeTest` 当前结果：

- `recording(7) @ 700Hz`
  - `STATIC_FIXED @ ±30Hz`
    - 直接塌到 `recall=0.143`
    - `key=0/3`
  - `STATIC_FIXED @ ±50Hz`
    - `0.857`
    - `key=3/3`
  - `STATIC_FIXED @ ±70Hz`
    - `0.786`
    - `key=3/3`
- `recording(7) @ 600Hz`
  - `±30 / ±50 / ±70`
    - 基本都在 `0.714`
    - `key=1/3`
  - 这说明这个 case 不是单纯靠收窄窗口就能自然修好
- `recording(10) @ 500Hz`
  - `HYBRID_DEFAULT`
    - `0.570`
  - `STATIC_FIXED @ ±30 / ±50 / ±70`
    - 都几乎失效
    - `recall=0.028`
    - `key=0/6`
  - 这说明人工锁频本身就不是这个 case 的主收益杠杆
- `recording(10) @ 700Hz`
  - 后续 bootstrap 诊断 probe 又补了一轮更贴近产品决策的对比：
    - `HYBRID @ 700Hz`
      - `recall=0.9718`
    - `STATIC_FIXED @ 700Hz`
      - `recall=0.9718`
    - `STATIC_AUTO @ 700Hz`
      - `recall=0.6620`
    - `FIXED_UNTIL_TRUST_THEN_AUTO @ 700Hz`
      - `recall=0.6831`
  - 这把边界进一步讲清楚了：
    - 这个 case 的高分，并不是来自 auto-track 主动拉升
    - 恰恰相反，一旦真的较早切进 `AUTO_TRACK`
      - 字符级稳定性会明显下降
  - 因此对这类“已知中心 tone、而 fixed 本身已经足够好”的场景：
    - 更合理的产品选择是直接交给用户人工选择 `FIXED_TONE`
    - 而不是继续把“自动锁频是否还能再榨一点收益”当成 RX 主线问题
- `recording(14) @ 800Hz`
  - `HYBRID_DEFAULT`
    - `0.771`
    - `key=3/3`
  - `STATIC_FIXED @ ±30Hz`
    - `0.657`
    - `key=2/3`
  - `STATIC_FIXED @ ±50 / ±70`
    - `0.714`
    - `key=2/3`
- `recording(15) @ 800Hz`
  - `HYBRID_DEFAULT`
    - `1.000`
    - `key=4/4`
  - `STATIC_FIXED @ ±30Hz`
    - `0.975`
    - `key=3/4`
  - `STATIC_FIXED @ ±50 / ±70`
    - `1.000`
    - `key=4/4`

这轮回归把边界讲得更清楚了：

- `±30Hz`
  - 对 `20260427_222505 / 20260427_224524` 这类 dirty case
    - 很可能是高收益档
- 但对更一般的人工锁频场景
  - 它并不稳
  - 有时甚至会明显比 `±50Hz` 更差

因此当前更稳妥的产品策略应改写为：

- 不把 `±30Hz` 作为人工锁频的全局推荐档
- 继续保留 `±50Hz` 作为更保守、更通用的默认值
- 把 `±30Hz` 理解成：
  - 用户已知中心频率
  - 且当前样本明显属于 dirty / wrong-tone competition / burst-noise 场景时
  - 可主动尝试的更激进窄窗档
- 因此用户侧交互也应倾向于：
  - `紧锁 ±30Hz`
  - `标准 ±50Hz`
  - `宽松 ±70Hz`
  - `自定义`
  - 这类 preset，而不是只暴露一个没有语义的裸数字
- 对像 `recording(10)` 这类已经验证“fixed 明显优于 auto / trust-then-auto”的场景：
  - 产品上允许用户直接选择 `FIXED_TONE`
  - 主线研发暂不再为“自动锁频必须赢过人工固定”反复投入

#### 4.1.1 新 probe 进一步缩小了“preferred tone 自适应”空间

本轮又补了两个更贴近实战的问题验证：

- `CwDirtyCaseStartupPreferredToneProbeTest`
  - 先看前几秒 frame trace
  - 再把学出来的频点当作新的 `preferredTone` 重跑整条 live-like
- `CwDirtyCaseMidstreamPreferredToneSwitchProbeTest`
  - 仍以 `700Hz` 起步
  - 但在中途某个绝对时间点把 `preferredTone` 改到 `640 / 740 / 760`
  - 观察 hybrid 主链是否真的被拉好

结果比预想更收敛：

- `20260427_222505`
  - startup-learning
    - `2500ms -> 学到 520Hz`
      - 这是明显被脏噪声带偏
      - 虽然 `HYBRID` 从 `0.400 -> 0.467`
      - 但方向不可信，`STATIC_FIXED` 甚至直接掉到 `0.067`
    - `4000/6000ms -> 学到 650Hz`
      - `HYBRID -> 0.500`
      - 比 baseline 好一点
      - 但仍明显弱于直接已知更优频点 `740Hz` 的 fixed replay
  - midstream switch
    - 切到 `740Hz`
      - 最好大约到 `0.500`
    - 仍然没有接近 `STATIC_FIXED @ 740Hz -> 0.733`
  - 说明：
    - 单纯改 `preferredTone`
    - 不能解决它更深层的 continuity / locked-retune 压制问题

- `20260427_224524`
  - startup-learning
    - 前几秒稳定学到 `520Hz`
      - 这几乎可以确认也是被电火花/脏噪声带偏
      - `HYBRID` 虽从 `0.318 -> 0.545`
      - 但解码文本形态并没有朝正确 CQ/DE 结构明显收敛
  - midstream switch
    - 切到 `740/760Hz`
      - 最多只到 `0.409`
      - 远低于直接从更优频点起步的：
        - `pref=640/680 -> 0.864`
        - `pref=760 -> 0.909`
  - 说明：
    - 这个 case 的关键不只是“后来把 preferredTone 改对”
    - 而是开局那段 front-end 资格化 / continuity fallback 已经把结构走坏了

因此这里可以先写下一个明确结论：

- 不应把“early preferred-tone auto-learning”
  - 作为下一条生产主线
- 也不应高估“midstream 改 preferredTone”
  - 对 dirty case 的真实修复能力

更实际的主线仍然是：

- 为什么 wide candidate 明明能看见
  - 却进不了稳定资格化
- 为什么 continuity / search fallback
  - 会把坏的早期结构保活太久
- 以及 bootstrap 阶段
  - 什么时候该继续 fixed
  - 什么时候该放 auto

#### 4.1.2 再缩一层：`224524` 当前也不太像是 “wide gate 没开”

本轮又补了三支更细的诊断 probe：

- `CwDirtyCaseQualificationDiagnosisProbeTest`
- `CwDirtyCaseEarlyTimelineProbeTest`
- `CwDirtyCaseForcedWideAcquisitionProbeTest`

这三支 probe 合起来给出的信息比前面更明确：

- `20260427_224524`
  - baseline 下
    - 总 frame `1728`
    - `wideScanWinner` 真正出现的 frame 只有 `6`
    - 且“strong far-wide evidence” 只有 `2`
    - 这 `2` 个 frame 都在极早期 `128ms / 144ms`
    - 本质上还是很弱、很脏的 onset
  - 更关键的是：
    - 即便加了一个纯实验性的
      - `forced wide acquisition`
    - 让解锁态 frame 都跑 wide 扫描
    - live 结果仍然完全不变：
      - `0.318 -> 0.318`

这意味着：

- 当前瓶颈并不是：
  - `shouldRunWideAcquisitionScan()` 这道门太保守
- 至少对 `224524` 来说
  - 把 wide gate 强行打开
  - 并不会自然产生更多真正可用的 wide winner

因此这里又能先排除一条看起来合理、但收益不大的方向：

- 不要把“放宽 wide scan 触发条件”
  - 当成 `224524` 的下一条主修线

现在更像的问题是：

- acquisition scoring 本身
  - 在 `preferred=700` 这个先验下
  - 已经把整段 opening 的候选排序带偏
- 以及 preferred-window 路径里
  - 某些 `690~760Hz` 的候选虽然在波形上可见
  - 但没有稳定走到“可提交”的结构

所以接下来的更真实主线应当转成：

- preferred-window / acquisition scoring 的偏置强度
- 非锁定态 candidate 的资格化阈值
- 以及这两者如何共同决定 early target 的首次成形

### 5. `recording16` 与 `capture.wav` 需要分开做验收

当前接受的判断是：

- `recording16`
  - 主要用来盯 front-end segmentation
  - 尤其是 startup / weak-valley / release 语义
- `capture.wav`
  - 主要用来盯 live-like / weak-lock / repeated-pass 稳定性
  - 以及新前端接回 timing/decode 后的真实收益

两者不能再被当成“同一个 probe 换个输入”。

### 5.1 repeated-session probe 已迁回统一 live-like 主链

这条结论是本轮新确认的，而且很重要。

过去 `CwLocalAudioRepeatedSessionProbeTest` 自带一套旧 harness：

- 自己维护 signal / timing / decode 状态
- 没有完整经过当前 `RxTurnController + TimingAnchorController` 主链

这意味着过去它打印出来的 repeated-pass 现象，
并不能严格代表当前 RX 主结构。

现在这条 probe 已经改成：

- 只负责构造 repeated timeline
- 真正的 live-like 解码全部交给 `LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(...)`
- 再按 round 做切片观察

因此从这一轮开始：

- `recording16` repeated-pass
- `capture.wav`

都已经是在统一主链上的观测结果。

### 5.2 新 probe 迁回主链后的最新事实

当前已经可以把问题进一步拆开：

1. `recording16` single-pass 本体

- `CwRecording16TimingStateProbeTest` 仍显示：
  - 单次播放内部 `front 40 / middle 30 / rear 30`

### 5.2.1 `capture.wav` 的首个分叉点已经定位到“Turn 内 bootstrap”而不是“纯后段累计漂移”

新增 probe：

- `CwCaptureWrongCharacterAlignmentProbeTest`

它不再泛泛统计弱信号窗口，而是直接抓：

- 每个 Turn 最早出现的错误字符
- 该字符附近的 frame / tone / timing / decode 窗口
- 以及 `recording16` 在相同 expected index / 相近 turn offset 的参考窗口

当前事实非常明确：

1. Turn 1

- 第一个错误就发生在 opening 起点
- 不是后半段漂移
- 表现为：
  - 在真正 `C` 之前先吐出一个 `?`
  - 随后又跟出一个空格
  - 即 `? CQ ...`

这说明：

- turn 内 bootstrap 还没建立可信字符边界时
- RX 已经允许错误字符进入 RAW 输出

2. Turn 2

- 第一个错误同样发生在 opening 起点
- 表现为：
  - 期望的首个 `C`
  - 被解成了 `Z`
  - timing 形态已经明显是错误合并后的 `--..`

这说明：

- 问题不只是显示层或 WPM 数值
- 而是 opening bootstrap 阶段就已经发生了 tone/gap segmentation 崩坏

3. Turn 3

- 前面一小段可以先对上 `CQ CQ `
- 但在后续 opening 中的下一个 `C` 位置开始分叉
- 表现为：
  - `CQ CQ K ...`

这说明：

- Turn 切分已经把“跨 Turn 持续越飘越远”的问题压掉了一部分
- 但单 Turn 内的 bootstrap / early trust 建立仍然不够稳定

因此这轮之后，主线判断更新为：

- 现在最该攻的不是 `seedWpm`
- 也不是泛化的“某个弱窗口 det/noise 偏低”
- 而是：
  - 在 turn 内 bootstrap 尚未建立 trust 之前
  - 如何阻止 phantom char / merged opening cluster 进入 RAW

这也是后续 RX 设计必须正面回答的问题：

- opening 阶段哪些事件可以暂存但不能立刻提交
- 何时才允许字符正式进入 RAW
- trust 建立前的负反馈和学习权限到底该如何收紧

### 5.2.2 `HEAD` 已证伪“前 40% 基本正常”这个判断

为避免继续建立在错误回忆上，这条也必须写死：

- 纯 `HEAD` 回放验证已经确认
- `capture.wav` 的 Turn 1 并不存在“前 40% 大体正常，后面才开始飘”
- Turn 1 opening 从一开始就已经不干净

具体含义是：

- 之前看到的某些 overall recall / window recall
- 会被后续偶然命中抬高
- 但这并不代表 opening prefix 是正确的

因此今后验收 `capture.wav` 时，必须优先看：

- 每个 turn opening 的前几字符是否干净
- 首个错误字符出现在什么位置
- trust 建立前是否已经把坏字符写进 RAW

而不能再用：

- “总 recall 还可以”
- 或“前 40% 似乎差不多”

来替代 opening correctness 的判断。

### 5.2.3 `recording16` repeated-pass 现状

- 在 unified repeated probe 下：
  - round 1 基本正常
  - round 2 开始明显劣化
  - round 3 继续恶化

这说明剩余主问题已经不能简单描述为：

- `recording16` 单段内部尾部继续累计失控

更准确地说，它已经更像：

- turn 结束后的释放 / 保留 / 再 bootstrap
- 在下一轮 turn 开始时重新进入错误工作点

### 5.2.4 `capture.wav` repeated-pass 现状

- unified repeated probe 依然明显很差
- 但现在它的现象已经是主链真实现象，而不是旧 harness 假象

因此下一阶段的主线应进一步收缩到：

- cross-turn retained state
- post-silence demote / reset
- new-turn bootstrap 如何重新贴回最近可信时基

### 5.2.5 turn-local RAW commit gate 已经出现结构收益，但收益边界也已看清

这一条是 `2026-05-14` 新确认的当前基线。

当前已经落地并证明有净收益的组合是：

- turn isolation
- `pre-trust RAW commit gate`
- `TimingAnchorController` 的 trust-origin 区分
- 针对 `CADENCE` trust 的延迟开门 + 小窗口 replay recover

它带来的当前真实收益是：

1. `recording16`

- single-pass 仍保持正常
- repeated-session 已恢复为稳定
- `seedWpm` 在 `0 .. 28` 的 sweep 下几乎不改变结果

这说明：

- 当前 RX 对干净单 turn 输入
- 已经基本不再依赖 seed 去“硬拖进正确工作点”

2. `capture.wav`

- `Turn 2` 已从 opening 直接坏掉
  - 推进到至少能稳定起出 `CQ DE ...`
- 首个错误字符位置已推进到：
  - `idx=3`
  - 不再是早先的 `idx=0`

这说明：

- `CADENCE` trust 不是空谈
- 在弱锁定 opening 上，延迟提交 + replay recover 确实能把 RAW 拉回一截

3. 当前收益边界

- `Turn 1` 仍在 opening 很早处出错
  - 当前首错仍是 `idx=1`
- `Turn 3` 也仍有 opening 后不久的分叉
  - 当前首错仍是 `idx=3`
- `capture.wav` 的 `Turn 2`
  - 虽然已经不再从第一个字符就崩
  - 但仍只到 `CQ DE ...`
  - 还没有恢复到完整 `CQ CQ CQ DE ...`

因此必须明确：

- 这一轮的净收益主要成立在
  - `CADENCE bootstrap recover`
- 它还没有解决
  - `BOUNDARY bootstrap`
  - 更早的 opening segmentation 健康度

当前下一步不应再回头主攻：

- `seedWpm` 微调
- `TX WPM` 强参考
- 再堆一轮 generic replay/backfill

而应继续正面解决：

- `Turn 1 / Turn 3` 这种 `BOUNDARY` opening 为什么还会在 trust 刚建立时就已经坏
- 以及 opening 之前的 tone/gap segmentation 是否已经先天不足

### 5.2.6 `BOUNDARY` turn 不能靠 replay 主线硬救，front-end rescue 也必须拆开看

这也是 `2026-05-14` 新确认的结构结论。

先确认两件事：

1. `trust - 4dot replay`

- 没有被“一刀否决”
- 它仍然保留为：
  - `CADENCE` trust 建立后的局部 recover 手段

但新的 boundary replay probe 已经说明：

- 对 `Turn 1 / Turn 3`
- 即使从 trust 附近强制回放小窗口
- 也不能把 opening 恢复成正确 `CQ`

这说明：

- `BOUNDARY` turn 的主问题
- 不是“trust 之后少回放一小段”
- 而是更早的 tone/gap segmentation 已经先坏了

2. front-end rescue 不能打包一起进主线

本轮把一个复合 front-end 补丁拆成两部分后，结论已经清楚：

- `tone-active weak valley bridge`
  - 对 `Turn 2` 的 `CADENCE` bootstrap 有真实帮助
  - 当前可以保留在主实验路径
- `near-target re-attack rescue`
  - 会把 `Turn 1` opening 从 `CTT E ...`
    拉坏成更差的 `CTNT D E ...`
  - 当前不应进入主路径

因此主线约束进一步收紧为：

- 允许：
  - `tone-active` 连续 run 内部的弱谷值 bridge
- 暂不允许：
  - `tone-off` 之后的 softened re-attack rescue

这条边界很重要，因为它避免我们再次把：

- `Turn 2` 的局部收益

和：

- `Turn 1` 的 opening 破坏

绑成一个不可控的混合补丁。

#### 5.2.6.a `BOUNDARY opening gap hold` 的最新验证边界

这一轮还额外验证了一条很具体的 opening discipline：

- 在 `BOUNDARY trust` 已建立、但 turn 内还没有形成 post-trust stable decode 之前
- 对可疑的 closing gap
  - 先延迟关字符
  - 不让 opening prefix 过早定型

这条规则的净结论已经比较清楚：

1. 它不是无效动作

- `Turn 1` 的 opening 确实从更早的错误闭合
  - 被往前推了一步
- 具体表现是：
  - 旧路径里更早的 `CTT...`
  - 先被拉成 `CM...`
  - 再被收敛到 `CG...`

这说明：

- `BOUNDARY opening` 里确实存在
  - “closing gap 过早把字符关死”
  这个真实问题

2. 但它不是主解

- 继续放宽 opening gap hold 的容忍范围
  - 只会开始吞掉真实边界
  - 却没有把 `Turn 1 / Turn 3` 拉回正确的 `Q / C`

这说明：

- 当前 `BOUNDARY opening` 的剩余问题
  - 已经不只是“gap 阈值还差一点”
- 更像是：
  - opening tone evidence 本身仍不完整
  - 或 opening 局部 rescue 仍缺位

因此下一步正式判断是：

- `BOUNDARY opening gap hold`
  - 作为 post-trust discipline 可以保留
- 但主攻点不应继续押在
  - “再放宽一点 gap hold”
- 而应转向：
  - opening local rescue
  - 或更早的 front-end segmentation 补救

#### 5.2.6.b `opening onset rescue` 已再次验证为非主解

这一轮又专门试了两类更偏 `onset` 侧的局部补救：

- `tone-off` 之后的 near-target softened onset
- 下一帧真正站稳后，把同频弱起始往前 backdate

结果对 `CwCaptureWrongCharacterAlignmentProbeTest` 的核心观察几乎没有改变：

- `Turn 1`
  - 首错仍是 `idx=1`
  - `actual=G`
  - `expected=Q`
- `Turn 3`
  - 首错仍是 `idx=3`
  - `actual=K`
  - `expected=C`

这说明：

- 单纯在 `TONE_ON` 这一侧做小修补
  - 还碰不到当前主病灶

更重要的是，这轮 probe 还把两种 opening 失败分得更清楚了：

1. `Turn 1`

- 错字前的 pre-on 窗口里
  - 几乎没有可用的 near-target warm-up residue
- 直到真正 `TONE_ON` 前
  - 都是很低的 `dom / iso`
  - 不足以支撑 onset backdate

这意味着：

- `Turn 1` 当前不是“明明已经有一小段可救 onset，只是没往前补”
- 而是更早的 tone evidence 本身就已经缺失

2. `Turn 3`

- 错字前窗口里能看到：
  - `TONE_OFF` 之后
  - 仍有数帧 near-target、接近 release threshold 的尾部残留
- 也就是：
  - same-tone tail 还没完全死干净
  - 但字符边界已经先被关掉

这意味着：

- `Turn 3` 更像是：
  - `release-tail hold / late-off discipline`
    不够
- 而不是：
  - `onset` 还差一点点就能补回

补充一个新的已验证事实：

- 给 `release-tail hold` 增加“当前 run 已经自证稳定”这条窄旁路后
  - `Turn 3` opening 的第一条 `DAH`
    已可从约 `126ms`
    拉长到约 `161ms`
  - 后续首个 intra-symbol gap
    也可从约 `67ms`
    压到约 `33ms`
- 但最终首错字符仍然是 `D`

这说明：

- `late-off hold` 方向本身不是错路
- 它已经能修到 opening 内部的第一层 premature release
- 但剩余错误已经进一步集中到：
  - 后续 symbol length / closing discipline
  - 而不是最早那一下 `TONE_OFF`

因此新的正式收敛是：

- `onset local rescue`
  - 当前不再作为下一轮主攻方向
- 下一轮更应该优先盯：
  - prior-tone release carry
  - late-off hold
  - 以及 gap commit 之前的 same-tone tail continuity

### 5.3 `capture.wav` 三段 turn 之前被合并成一段的直接原因已确认

这是本轮新确认并已修正的结构问题。

之前 `capture.wav` 在 natural-turn probe 下只显示 `1 turn`，并不代表：

- 三段来波之间真的没有可分割静默

更直接的原因是：

- `RxTurnController.observe(...)`
  在 live-like test path 里并没有像真机一样“每帧都被时钟驱动”
- 它主要只在：
  - 有 tone event
  - 或 decoder 仍有 pending character
    时被调用
- 一旦进入纯白噪声静默段
  - 反而没人继续推进 turn-end silence 计时
  - 于是 turn 只能拖到整段文件结束才收尾

这已经修正为：

- live-like replay 每帧都继续观察 turn clock
- turn liveness 不再直接使用宽松的 `toneActive()`
- 而是改为更窄的 `meaningful turn activity`
  - 结合 SQL 越线
  - 锁定痕迹
  - 窄带形态

修正后的新事实是：

- `capture.wav` natural-turn probe 已经恢复为 `3 turns`

因此当前剩余主问题进一步收缩为：

- 不是“为什么三段没有切开”
- 而是“为什么第 3 段内部仍会重新上抬到偏高 WPM 并导致 RAW 崩坏”

### 5.4 `raw chain` vs `live-like chain` 的最新对照结论

这是本轮新增的关键归因证据。

我们专门把同一份输入分别跑了：

- `raw chain`
  - `CwSignalProcessor -> CwHybridTimingModel -> CwDecoder`
- `live-like chain`
  - 当前完整主实验链

得到的结构性结论是：

1. `recording16`

- `raw chain` 的最终文本明显差
- 但它在 `F00_40 / M40_70 / R70_100` 上的：
  - toneAvg
  - gapAvg
  - signal lock / near / dom / iso
  与 `live-like chain` 基本一致

这说明：

- 对干净输入，live-like 层的主要价值是：
  - timing / WPM / turn 级约束
  - 最终把已经基本健康的前端事件解释正确

2. `capture.wav`

- `raw chain` 与 `live-like chain` 在三段 play 上的：
  - toneAvg
  - gapAvg
  - signal lock / near / dom / iso
  几乎完全一致
- 差异主要只体现在：
  - rawWpm 被后层拉回一些
  - decode 文本被后层压缩/整理一些

这意味着：

- `capture.wav` 当前最早的坏点，发生在 `live-like` 之前
- `LiveRxWpmGuard / TimingAnchorController / turn` 这些后层
  不是主根因
- 它们最多只能对坏前端事件做有限整形
  不能把已经切碎的 tone/gap 重新变对

3. 当前正式归因

因此当前必须把主病灶定位为：

- `CwSignalProcessor` 内部的 keying segmentation

而不是：

- `WPM runaway`
- `trusted anchor`
- `turn reset`
- `LiveRxToneEventStabilizer`

### 5.4.1 `near_frequency_narrowband_noise_report` 已证实剩余主病灶在前端选频/判键

这是 `2026-05-15` 新补上的硬结论。

我们把同一份 `near_frequency_narrowband_noise_report` 输入做了 forced replay 对照：

- 当前 live-like 基线
  - 只能得到很差的部分文本
- 强制复用 tracked / effective / hypothesis tone
  - 依然几乎出不来有效 timing / decode
- 强制固定 `670Hz`
  - 可直接恢复出：
    - `BI9CLT DE BG7YOZ UR 5NN BK`

这条对照的含义非常直接：

- 当前 timing / decoder 链路
  - 在拿到正确 keyed `670Hz` 事件流时
  - 是可以正常工作的
- 因此这个 fixture 的剩余主问题
  - 不是 `seedWpm`
  - 不是 `WPM runaway`
  - 也不是 decoder / interpreter
- 而是：
  - `CwSignalProcessor`
    没有把真实 keyed `670Hz`
    从 `690 / 700 / 710Hz` 一类连续近频窄带干扰里稳定选出来

因此这条线的下一步必须收敛到：

- acquisition candidate scoring
- preferred-window / wide-scan arbitration
- keyed signal 与连续近频 carrier 的判别

而不是再回头继续：

- 调 `seedWpm`
- 叠 generic timing patch
- 或把问题继续归因给后层 trust / decode

#### 5.4.1.a 这条线已经有最终验收态，不再只是“怀疑前端”

这一点也必须写死，避免后续再次把它当成未定问题。

最终已经通过的对照是：

- `CwNearFrequencyToneEventGapProbeTest.printNearFrequencyLiveVsForced670ToneEventGaps`
  - `live text=BI9CLT DE BG7YOZ UR 5NN BK`
  - `forced text=BI9CLT DE BG7YOZ UR 5NN BK`
  - `liveOnlyActive=0`
  - `liveWeakBridge=0`
  - `liveAboveRelease=0`
- `CwFixturePipelineRegressionTest.nearFrequencyNarrowbandNoiseFixtureAvoidsWrongToneLock`
  - 已恢复通过

这说明当前正式结论已经从：

- “怀疑 timing / decoder 没跟上”

收敛成：

- 只要前端能把正确 keyed `670Hz` 事件流稳定交出来
- 后面的 timing / decode
  - 在这个 fixture 上已经足够工作

#### 5.4.1.b 这条线最后真正起作用的是“成熟上下文里的更强负反馈”

最终收敛的补救，不是再做一轮 startup 搜索，也不是更换 decoder。

真正有效的是三件事同时成立：

1. continuity anchor 更早进入 trusted context
2. trusted context 里，far tone-on / drifting estimate 会被 continuity anchor 压回
3. weak bridge 只有在 trust 证据和绝对幅度都还成立时才允许继续生效

这意味着：

- 同样叫 `continuity anchor`
- 在 startup / search 阶段
  - 不能过早变成强约束
- 但在成熟 turn-local context 里
  - 必须比启动期使用更紧的 drift 门限

当前这条线已经明确接受：

- mature trusted continuity context
  - 应使用比 startup / search 更紧的 drift threshold
- 当前实现中这一角色已经体现在：
  - `TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ`

因此今后如果这条 fixture 再退化，优先回看的位置应是：

- trusted continuity anchor 的进入条件
- trusted context 下的 far tone-on suppression
- weak bridge 的 trust / floor gating

而不是重新优先怀疑：

- `seedWpm`
- `LiveRxWpmGuard`
- decoder / interpreter

### 5.4.2 `LiveRxWpmGuard` 的 trust 前 retarget 已修正，但它不是这条 fixture 的主解

这一条也要写死，避免后续重复怀疑同一个位置。

已经确认并落地的修正是：

- `LiveRxWpmGuard.adaptTimingEvent(...)`
  - 在 `trustedWpm <= 0` 时
  - 直接返回 raw timing event
  - 不再在 trust 建立前改写 startup timing

它的意义是：

- pre-trust 阶段的 raw timing
  - 终于不会先被 guard 带偏
- `capture.wav` 三段 turn 回归
  - 仍保持通过
- `recording16`
  - 仍保持当前可接受基线

但同时新的 forced replay 对照已经说明：

- 即使这条 guard 修正保留
- `near_frequency_narrowband_noise_report`
  仍然主要卡在前端 target discrimination

所以当前正式结论是：

- 这条 fix 是有效修正
- 但它不是 `near_frequency` 的主病灶
- 这条线后续主修复入口仍然必须回到：
  - `CwSignalProcessor`
  - 的 acquisition / discrimination

### 5.4.3 `recording8` 这条 severe-noise 线已经确认：不要再把主时间花在 `seed / SQL / 隐藏大 gap` 上

这是 `2026-05-16` 新补上的硬结论。

当前已经补齐的对照有三组：

1. `seedWpm sweep`

- `CwRecording8SqlSweepProbeTest.printRecording8SeedSweep`
  显示：
  - `seed=12 .. 26`
    - final text 几乎完全不变
    - `recall` 基本钉在 `0.7746`
  - `seed=28`
    - 也只出现很轻微扰动
    - opening 甚至会从 `? CQ ...`
      摇成 `GQ CQ ...`
    - 但并没有带来结构性修复

这说明：

- 这条样本当前不是 `seedWpm`
  没带好
- 再继续围绕 seed 微调
  只会重复进入低收益循环

2. `SQL sweep`

- `CwRecording8SqlSweepProbeTest.printRecording8SqlSweep`
  已覆盖：
  - `sql=40 / 55 / 65 / 70 / 75 / 80`
- 当前观察到：
  - final text 依然几乎不变
  - `recall` 仍钉在 `0.7746`
  - 提高到 `70+`
    - 只会让 stable 输出明显缩短
    - 并触发更多 `anchor-guard`
    - 不是主解

这说明：

- `recording8` 目前也不是
  靠 `SQL` 再往上拧
  就能突然变干净

3. 第二失败窗原始波形

- 直接对 `录音 (8).wav`
  在约 `11014ms .. 11678ms`
  做了原始包络抽样
- 结果看到的是：
  - 多组强 tone cluster 之间
  - 真实只有大约 `56ms .. 64ms`
    的低能量 valley
  - 没有隐藏的
    `150ms+`
    字母间隔或词间隔

这条事实的含义非常重要：

- 当前第二失败窗里
  不是“本来有一个大 gap，
  只是前端把它吞掉了”
- 至少在原始波形层面
  没有证据支持继续沿着
  “把隐藏 word gap 救回来”
  这条思路硬追

因此这条 severe-noise 样本当前的正式定位应是：

- 它仍然是很有价值的 observability fixture
- 但它不适合再被当成：
  - `seedWpm`
  - `SQL`
  - 或“恢复一个并不存在的长 gap”
  的主战场

后续如果还要继续投入 `recording8`，
更合理的方向应是：

- 接受它本身就是严重退化输入
- 只把它用于观察：
  - noisy keyed segmentation 的上限
  - raw 输出在极差输入下的可读性边界
- 而不要再拿它去反复证明：
  - `seed` 没带好
  - `SQL` 不够高
  - 或某个“被隐藏的大 gap”
    还没被找出来

### 5.5 当前最具体的前端病灶线索

最新 probe 已经给出一个非常强的局部线索：

- `recording16 F00_40`
  - weak-valley bridge frame 数量很高
- `capture play1 / play3 F00_40`
  - weak-valley bridge frame 数量极低
- 但同时：
  - tracked tone 频点大体仍接近目标区

这意味着当前更像是：

- 同频连续性还在
- 但 `release / weak-valley bridge` 没有把它保住
- tone 被过早切断，gap 被拉长
- 随后 timing 只能拿这些错误 tone/gap 去估计 dot

因此下一阶段的主线应明确转向：

- `CwSignalProcessor`
  - `currentReleaseThreshold`
  - `shouldBridgeAutoTrackWeakValley(...)`
  - `isAutoTrackWeakValleyCandidate(...)`
  - `shouldSuppressWeakLockedBranchTone(...)`

特别是要重新审视一个结构问题：

- 当前 weak-valley bridge 过度依赖
  - `toneActive`
  - `targetToneLocked`
  - 已建立的 locked-branch reference

而 Mic capture 的现实恰恰可能是：

- `near-target continuity` 还在
- 但强 `locked` 状态已经掉下去

于是 bridge 几乎不工作，tone 被提前切碎。

这比“WPM 又漂了”更早，也更接近根因。

### 5.5.1 `capture-play2` opening 的第一处真实坏点已经进一步收缩到“合法短 gap 被 weak valley bridge 吞掉”

这是 `2026-05-14` 新确认的更细结论。

新增 probe：

- `CwCapturePlay2TimingDivergenceProbeTest.printCapturePlay2OpeningToneBirthWindow`

当前事实是：

1. opening 的第一条大坏 `DAH`

- `capture-play2`
  - `TONE_ON @5653`
  - `TONE_OFF @6208`
  - `dur=555ms`
- 对应 timing event：
  - `#01 @6208 TONE/DAH dur=555 rawDot 80 -> 148`

2. 这条 `555ms` run 不是因为“起不来”

- onset 在 `@5653` 已经成功建立
- `@5664` 开始就已经：
  - `attack=true`
  - `toneOn=ALLOW:ATTACK_THRESHOLD`
- 因此当前 opening 主病灶
  - 不是频点没锁到
  - 不是 seed 没带好
  - 也不是 `TONE_ON` 根本起不来

3. 真正的问题是：同一 opening 内多个合法短 gap 被继续当成 same-tone weak valley

- `capture-play2` 在这条长 run 内部，多次出现：
  - `weak=true/1`
  - `weak=true/0`
- 典型位置在 turn 相对时间：
  - `+128ms`
  - `+224ms`
  - `+416ms`
  - `+512ms`
- 这些位置都没有形成真正的 `TONE_OFF`
  - tone 继续保持 active
  - 最终整段被合并成一个 `555ms` 的长 tone

4. 对齐 `recording16` 的同相对 opening 窗口后，结构差异已经非常清楚

- `recording16` 在同一相对窗口里不是一个长 tone
- 而是：
  - `155ms tone`
  - `36ms gap`
  - `60ms tone`
  - `36ms gap`
  - `156ms tone`
  - `36ms gap`
  - `60ms tone`
- 也就是说：
  - `capture-play2` 里那些被吞掉的 weak valley 位置
  - 正好对应 `recording16` 里真实存在的短 gap / 短 tone 边界

5. 当前正式归因

- opening 的第一处结构错误
  - 不是 trust 之后才坏
  - 也不是 WPM 慢慢漂起来
- 而是：
  - front-end 在 turn opening 的前几百毫秒里
  - 把本应成立的短 gap 继续解释成 same-tone weak valley
  - 从而把多个合法 element 合并成一个长 run

因此下一步的主攻点必须继续收缩到：

- `shouldBridgeAutoTrackWeakValley(...)`
- `isAutoTrackWeakValleyCandidate(...)`
- weak-valley bridge 在 bootstrap opening 阶段的生效边界
- 以及它是否应该更早退让、接受更强的负反馈约束

这条结论同时也证伪了两种旧优先级：

- 继续优先怀疑 `seedWpm / TX WPM`
- 继续优先怀疑 opening `TONE_ON` 起始补救

因为 probe 已经直接显示：

- onset 已经成功
- 真正被吞掉的是 opening 内部的短 gap 语义

### 5.5.2 `recording16` 的首个错位不是同一种“gap 被吞”，而是 `WEAK_CONTINUITY_WINDOW` 过早重开了弱起点

这是同一天追加确认的另一条关键分叉结论。

新增 / 强化 probe：

- `CwRecording16BoundaryDriftProbeTest`

当前事实：

1. `recording16` 的第一个真正错位字符

- 不在尾段
- 也不是先从 `capture.wav` 那种 opening merge 复制过来
- 而是在第三个 `CQ` 开头：
  - expected: `C`
  - observed: 先前是 `=`
  - 收紧后变成 `X`

2. 放大首错窗口后，问题形态很明确

- `@6000` 处前一个 tone 结束后
- 原实现会在 `@6032` 触发：
  - `toneOn=ALLOW:POST_RELEASE_RESCUE`
  - `rescue=ALLOW:WEAK_CONTINUITY_WINDOW`
- 这个 onset 质量并不高，却被当成了新的合法 tone
- 随后又在更强的真正回升处继续形成后续 tone
- 结果把本应更简单的字形，扯成了额外的 `.` / `-` 组合

3. 也就是说，`recording16` 这里的主病灶不是：

- weak valley bridge 把 gap 吞掉
- 而是 `post-release weak continuity` 把 sub-release 的弱回升过早当成了新 onset

4. 因此这里需要的负反馈不是“更敢 bridge”

- 恰好相反
- continuity rescue 可以放宽 full attack threshold
- 但不能在连原 `releaseThreshold` 都还没重新站上来的时候就重开 tone

据此，本轮已落地的一条结构修正是：

- 对 `weakPostReleaseOnsetRescueCandidate + rescueContinuationWindowActive`
- 新增约束：
  - detection 至少重新站上 `releaseThreshold`
  - 否则直接拒绝这次 continuity rescue

当前验证结果：

- `recording16`
  - `norm 0.55 -> 0.60`
  - 首错字符从 `=` 收敛到 `X`
  - 说明最早那颗“幽灵弱起点”被压掉了
- `capture-play2`
  - opening 仍保持：
    - `147ms tone`
    - `50ms gap`
    - `44ms tone`
    - `52ms gap`
  - `firstMajorUpshift` 仍为 `none`

因此这里的正式结论是：

- `capture-play2 opening`
  - 主要防的是 `weak valley bridge` 吞 gap
- `recording16 first mismatch`
  - 主要防的是 `weak continuity rescue` 过早重开弱 onset

这两类问题不能再混成一个病灶处理。

### 5.5.3 为什么这里转去盯 `recording16`，不是继续只盯 `capture.wav / Turn2`

这个转向不是换题，而是刻意降变量。

当前拆解应当明确成两层：

1. `capture.wav / Turn2`

- 更适合验证：
  - turn 边界是否真的切开
  - turn 之间的上下文 / seed / trust 是否互相污染

2. `recording16`

- 更适合验证：
  - 单个 turn 内部
  - `bootstrap / post-release rescue / weak continuity`
  - 到底是谁先把本来合法的字符结构带坏

之所以不能继续只盯 `Turn2`，是因为 `Turn2` 同时混着：

- 手机 capture 噪声
- 三段 replay
- turn 间静默
- 单 turn 内部的前端结构错误

这些变量叠在一起，容易让我们继续在“边界锅”与“单 turn 前端锅”之间来回误判。

所以当前约定是：

- 用 `recording16` 充当 `Turn2` 的单-turn 显微镜
- 先把单-turn 内部的 front-end 主病灶钉死
- 然后再回到 `capture.wav` 验证：
  - turn 切分是否仍成立
  - `Turn1 / Turn2 / Turn3` 是否继续互不污染

### 5.5.4 本轮新增结论：`weak onset chain` 不能记住“不够格”的旧残留起点

上一轮虽然已经加了：

- `weak continuity rescue` 至少要重新站上 `releaseThreshold`

但 probe 继续显示一个结构漏洞：

- `@6032` 的 sub-release 弱残留虽然已经不能直接 reopen
- 后面更强的合法 rescue 仍会把 `toneOnTimestamp`
  - 回填到更早的 weak chain 起点

这本质上还是一种隐藏的正反馈：

- “不够格的旧残留”
  - 先被弱链记住
- “后来真正够格的回升”
  - 再被拖回这个旧残留时间戳

因此本轮又补了一条更干净的结构原则：

- `weak onset chain`
  - 只能记住“可信起点”
- 可信起点的判据暂定为：
  - 有 `frame-local tone-on`
  - 或 detection 已重新站上 `releaseThreshold`

也就是说：

- weak chain 仍可保留 continuity 语义
- 但它不再有资格把 later valid rescue 回填到 sub-release residue

当前结果：

- `recording16` 主链文本已回到：
  - `CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.`
  - `norm=1.00`
- `capture-play2`
  - opening 仍保持：
    - `147ms tone`
    - `50ms gap`
    - `44ms tone`
    - `52ms gap`
  - `firstMajorUpshift=none`

这说明这次改动至少满足两条硬约束：

- 没把 `capture-play2 opening` 打回去
- 同时也确实修到了 `recording16` 的单-turn 主链

### 5.5.5 `trusted weak chain` 的收益边界已经出现了

在上面的基础上，又做了一轮更窄的结构增强：

- 不恢复旧的“任何 weak chain 都能 backfill”
- 而是只增加一个：
  - `trusted weak chain`

它的核心约束是：

- 仍然必须在同一个 `post-release continuity window`
- 仍然必须是 near-anchor 的同频候选
- 但只有在：
  - recent lock / near-target trust 已经够高
  - representative context 稳定
  - weak onset 连续至少两帧都仍然像真的 tone
- 后续 continuity rescue 才允许借用这个更早的 weak 起点

这一轮的实际结果很重要，因为它说明这个机制的适用边界开始清楚了：

1. 它守住了两条底线

- `recording16`
  - 主链仍然保持 `norm=1.00`
- `capture-play2 opening`
  - `firstMajorUpshift=none`
  - opening 结构仍然没有被打回去

2. 它确实改善了 `capture` 的后续高信任 turn

- `capture-play3`
  - 文本从：
    - `CQ CM T DE T E 9CXN ...`
  - 收敛到：
    - `CQ CQ DE T E E 9CXN ...`
- `CwCaptureWrongCharacterAlignmentProbeTest` 里：
  - Turn 3 的 first mismatch
  - 从：
    - `idx=4 actual=M expected=Q`
  - 推迟到：
    - `idx=6 actual=D expected=C`

这说明：

- multi-frame trusted weak continuity
  - 对“turn 内已经建立起局部信任后，后续弱元素仍会掉”的问题
  - 是有效的

3. 但它没有解决 Turn 1 / Turn 2 的更早期 bootstrap 缺口

当前 probe 仍显示：

- Turn 1
  - first mismatch 仍是：
    - `idx=3 actual=K expected=C`
- Turn 2
  - first mismatch 仍是：
    - `idx=0 actual=N expected=C`

而且这两个窗口的共同点已经更清楚：

- 出错点更早
- 发生时 `aq=false / targetToneLocked=false` 的时间更长
- 说明问题还在：
  - trust 建立之前
  - 或刚建立边缘
  - onset 候选根本进不了 continuity rescue 体系

因此这轮的正式结论是：

- `trusted weak chain`
  - 适合修：
    - high-trust single-turn 内部
    - 后续弱元素丢失
- 它不适合直接修：
  - Turn 1 / Turn 2 这种
  - 更早的 pre-trust / low-lock bootstrap 断点

这也给下一轮缩小了入口：

- 下一轮要盯的不是：
  - `recording16`
  - 也不是 `capture-play3` 的后段 continuity
- 而是：
  - `capture` 里 `Turn 1 / Turn 2` 的 pre-trust bootstrap
  - 为什么候选 onset 在 `aq=false + targetToneLocked=false` 阶段长期进不来

### 6. `recording16` 当前最关键的证据

已确认的关键事实：

- experimental path 下，第一个明确错误字符发生在 `@2400ms`
- 期望字符：`C`
- 实际字符：`6`
- pattern：`-|.|.|.|.`
- 早期 run 里出现了：
  - `R00 768..1312 dur=544ms freq=711Hz ACCEPTED`

这说明：

- 当前 experimental front-end 在 very early startup 就已经把多个真实元素过度合并
- 这不是尾段才出现的问题

### 7. 当前必须坚持的结构原则

- `allow decode != allow learn`
- `tone tracked != tone accepted`
- `SQL` 应是 candidate admission 的一部分，而不是 WPM 副作用旋钮
- 后置 guard 只能做辅助防线，不能替代前端健康分段

### 7.1 `SQL` 的目标语义应回到“人工门限线”

这一条需要单独明确，因为它直接决定 front-end 的设计方向。

更贴近真实使用习惯的理解应该是：

- `SQL` 不是一个抽象的百分比放大器
- 也不是通过若干隐藏 margin 间接影响 attack threshold 的黑箱旋钮
- 它更像一条用户可以理解的“门限线”

近似直觉就是：

- 没有 CW 信号时
  - 背景输入应被门限挡住
- 一旦出现真正的 CW tone
  - 只要它能够越过这条线
  - 即使比较弱
  - 也应有资格进入后续识别链

这更接近很多传统产品的表现：

- 一条连续电平条
- 再配一条人工设置的 SQL 门限线

也就是说，后续设计里 `SQL` 更应被理解为：

- `candidate admission threshold`

而不是：

- WPM learner 的副产品
- release / trust / continuity 逻辑一起混合出来的抽象门限

### 7.2 但“只要越过纯幅度线就放行”仍然不够

这一点是本轮新确认的补充约束。

我们刚刚验证过：

- 如果只按前 `2000ms` 白噪声水平
- 把全局门限压到“刚高于噪声”
- `capture.wav` 的自然解码会明显变差

因此正式结论应是：

- `SQL` 的语义应当回到“门限线”
- 但这条线不能只看宽带幅度

真正合理的 admission 条件应至少同时依赖：

- 是否越过 SQL 门限
- 是否 near-target
- 是否 narrowband qualified
- 是否具有足够的 dominance / isolation / local contrast

换句话说：

- `SQL` 负责回答：
  - “这个候选有没有资格进门”
- tone quality / frequency evidence 负责回答：
  - “进门的这个东西是不是像真的 CW”

这样才能同时满足两点：

- 静噪时不过门
- 弱但真实的 CW tone 可以过门

而不是出现：

- 纯噪声过门
- 或门限太硬把弱真信号一起切掉

### 7.2.1 允许 bootstrap 初值，也允许系统安全下限

这一点也应明确写成正式约束。

`SQL` 既然要回到“门限线”语义，
并不意味着：

- 每次都必须从零开始人工调
- 或者系统完全不能给一个初始估计

相反，更合理的设计应该允许：

1. 一个 bootstrap 初始评估值

- 可来自：
  - 开头短时间噪声观测
  - 默认产品经验值
  - 上一次已知稳定场景的弱参考

它的职责只是：

- 在用户还没有人工细调之前
- 给出一条“先大致能工作”的起始门限线

2. 一个系统容忍下限

- 不允许门限被放得过低
- 以至于纯噪声、宽带毛刺、环境底噪
  大量误入 RX candidate

这条下限应理解为：

- `safety floor`

而不是：

- 现在这种长期主导全局行为的硬编码高门限

也就是说，正式目标不是：

- 永远固定在某个大阈值上

而是：

- `effective SQL line = max(system safety floor, bootstrap/user/local estimate)`

其中：

- `system safety floor`
  - 负责防止门限失控过低
- `bootstrap estimate`
  - 负责冷启动先给出一个可用起点
- `user/local estimate`
  - 负责根据当前噪声环境把门限线放到合适位置

这样既能满足：

- 不同场景噪声水平不同
- SQL 需要跟着环境变化

也能满足：

- 系统不至于因为某次估计失真
- 把门限直接放进纯噪声区

### 7.3 对当前实现的直接约束

因此后续 SQL 相关改造要遵守：

- 优先把 `SQL` 收敛为 front-end candidate admission 的可解释量
- 不再让它主要通过一组隐藏的全局 `margin multiplier` 间接起作用
- 不再把“门限调高/调低”当成主要修复路径
- 真正的重点仍然是：
  - pre-trust bootstrap
  - boundary / onset / release 语义
  - weak-but-real candidate 的早期放行条件

### 8. 当前实现阶段只允许 `RAW RX` 参与判断

这一条现在提升为硬边界，而不是建议。

当前阶段，RX 核心只关心：

- front-end 是否健康
- timing / decode 是否稳定
- RAW 抄收文本是否持续可读

当前阶段，下面这些都属于下游增值能力：

- 呼号识别
- normalized text / semantic recovery
- QSO state / 草稿构建
- ready-for-confirm / manual-review 之类工作流状态

它们可以保留为：

- 后续独立消费层
- 或开发调试时的附加观察层

但它们**不能**参与当前 RX 核心的：

- event acceptance
- timing learning
- WPM 锚定
- epoch 控制
- “当前 RX 是否变好/变坏”的判断

原因很直接：

- 这些上层解释能力本身就依赖 RX 基础是否健康
- 如果在基础层还不稳定时就把它们混回主判断
- 会制造大量伪反馈
- 让我们误把语义层噪声当成 RX 本体问题

因此当前这轮重构的正式范围是：

- `Signal -> Timing -> Decode -> RAW`

而不是：

- `Signal -> Timing -> Decode -> Semantic -> QSO -> Workflow`

### 9. 当前阶段不只“实现”看 `RAW`，连观测与验收也只看 `RAW`

这一条是对上一条的进一步收紧。

当前阶段我们不只是要求：

- RX 核心实现不要被 semantic / QSO / callsign 反向污染

还要求：

- 调试观察面只盯 `RAW`
- 问题分析只盯 `RAW`
- 改动收益评估只盯 `RAW`
- 验收结论也只盯 `RAW`

### 9.1 `decoder` 草稿文本不再等同于最终 `RAW`

这轮已经确认一个此前混淆结论的重要噪声源：

- `decoder.snapshot().decodedText()`
  - 仍会保留 `pre-trust` / opening 草稿态痕迹
  - 例如 `capture.wav` 曾出现前缀 `? CQ CQ ...`
- 但同一轮 `live committed RAW`
  - 也就是 `interpreter.snapshot().rawText()`
  - 以及 final-only admitted decode stream
  - 实际已经是干净的三段 `CQ CQ CQ ...`

因此从现在开始，必须明确区分：

- `decoder scratch text`
  - 只能拿来观察 bootstrap / speculative decode 的内部状态
  - 不能再直接作为用户可见 `RAW`
  - 也不能再作为 turn / repeated-pass 的最终验收依据
- `live committed RAW`
  - 才是当前 RX 对外承诺的最终输出
  - repeated probe / natural-turn probe / 文档结论都应统一读取这一口径

这条修正的直接收益是：

- `capture.wav` 那条“Turn 内已经干净，但 FINAL 仍假脏”的矛盾
  已被证实主要来自观测出口不一致
- 后续再盯 `capture.wav`
  就不会继续把 `decoder` 草稿噪声误判成 `RAW` 提交错误

也就是说，当前阶段下面这些都不应再作为 RX 改进是否有效的依据：

- 呼号候选是否更像样
- normalized text 是否更顺眼
- QSO draft 是否更完整
- ready / review 这类工作流状态是否更漂亮

原因不是这些功能不重要，而是：

- 它们全部建立在 RX 基础之上
- 只要基础层还有偏差
- 这些上层结果就会放大噪声、制造假象、干扰归因

因此当前阶段的正式评估口径统一为：

1. 看 `RAW` 是否更早进入可读区间
2. 看同一段 CW 中 `RAW` 是否更稳定
3. 看尾段是否还会出现累计偏移导致的 `RAW` 崩塌
4. 看 repeated pass 下 `RAW` 是否保持一致，而不是一轮比一轮更差

这也意味着当前的调试界面、trace 分析、bench 口径都应围绕：

- tone event 质量
- timing event 质量
- decoded raw character 序列
- raw text 连续性

而不是围绕：

- semantic interpretation
- QSO state progression
- draft completion quality

## 已证伪 / 已降级的错误做法

下面这些做法已经试过，或者已经被结构性证伪，不应再继续作为主方向推进。

### A. 把问题当成 `display WPM` 问题

已证伪做法：

- `display WPM clamp`
- `trustedWpm hold`
- `seed carry-over`
- UI 层显示压制

为什么错：

- 它们只能压表象
- 不能阻止 timing learner 继续被坏事件喂坏
- 也不能修复前端已经错误生成的 tone/gap 事件

### B. 把问题当成“只要 reset 一下就好”

已验证过两类 reset：

1. `hard reset`

- 直接分段重解整条链
- 会切断 decoder 上下文
- 已证伪

2. `RX-only reset`

- 只重置前端/时基/WPM guard/stabilizer
- 保留 decoder/interpreter 上下文
- 对 `recording16` 结果只比 hard reset 略好
- 仍明显差于 whole-pass

为什么错：

- 固定周期 reset 只是把系统不断打回 bootstrap
- 它没有“保住正确工作点”
- 也没有真正提供状态驱动的负反馈

结论：

- `fixed interval reset` 不是正式方案
- 最多只能作为验证工具

### C. 在旧坏事件之后堆更多后置 gate

已证伪做法：

- `authority gate only`
- `AUTH / AUTH_ONSET / AUTH_MERGE / AUTH_HOLD / FULL`
- 继续在旧 `LiveRxWpmGuard` 后面叠阈值补丁

为什么错：

- 它们处理的是“坏 segmentation 之后的残局”
- 不能修复进入 decoder/timing 之前就已经错误的 event shape
- 很容易先伤 `recording16`，也救不了 `capture.wav`

结论：

- 后置 gate 不再作为主修复路径

### G. 把问题继续押在 `LiveRxToneEventStabilizer` onset 补丁上

已证伪做法：

- 为 `near-target high / lock low` 增加 onset rescue

为什么错：

- probe 已显示它对 `capture.wav` 的核心指标几乎没有净改善
- `raw chain` 与 `live-like chain` 的 tone/gap 统计本来就几乎相同
- 说明 stabilizer 并不是当前主 choke point

结论：

- `LiveRxToneEventStabilizer` 暂不再作为主修复入口
- 主线回到 `CwSignalProcessor` 的 release / bridge / segmentation

### D. 把问题归因到 startup warmup / 半帧补偿 / 微调桥接

已证伪做法：

- `startup warmup`
- `500ms / 1000ms` silence bootstrap
- `half-frame boundary compensation`
- 继续给 bridge 补更多时间戳微调
- `pendingWeakValleyOffTimestampMs` 这种更早 off 起点实验

为什么错：

- 它们没有修复 `recording16` 的早期 run 语义错误
- 有些实验甚至直接让基线退化

结论：

- 主问题不是“差一点边界补偿”
- 主问题是 front-end segmentation 的语义本身不对

### E. 把历史 case 全都当成 truth

已证伪做法：

- 把旧逻辑下的某些“分裂行为”继续当成 strict 正确
- 让历史 case 反过来绑架重构

为什么错：

- 一部分 case 只是记录旧行为
- 并不代表真实目标
- 如果不分级，工程会持续保护错误行为

结论：

- case 必须区分：
  - `truth`
  - `observability`
  - `stress`
  - `invalid assumption`

### F. 认为 `capture.wav` 与 `recording16` 可以靠同一个小补丁一起修好

为什么错：

- `recording16` 当前暴露的是前端 segmentation 主病灶
- `capture.wav` 暴露的是 live-like / repeated-pass / weak-lock 放大效应
- 同一个局部补丁往往会先破坏其中一个

结论：

- 它们必须分 acceptance track 验证

### G. 把低 `det/noise` / 低 `iso` 窗口直接当成 front-end 主根因

已证伪做法：

- 看到 `capture.wav` 中某段 frame window 出现：
  - `det/noise` 很低
  - `lock ratio` 下降
  - `iso` 很低
- 就直接把它判成：
  - “front-end 提前切碎”
  - 或“release / weak-valley 明显出错”

为什么错：

- 新增 `CwCaptureTurnFrontEndFaultProbeTest` 已把 `capture.wav` 每个 turn 的第一处所谓
  `front-end weak window`
  与 `recording16` 同相对偏移窗口并排对齐
- 结果显示：
  - `capture.wav` 中这些窗口的 `tracked / effective / hypothesis tone`
    仍然稳定贴着 `700/710Hz`
  - 而 `recording16` 在同相对偏移位置也会出现非常相似的：
    - tone run 尾部下坠
    - weak-valley 两帧
    - 随后进入真实 `tone-off / gap`
- 说明这类低信号窗口本身，经常只是：
  - 合法的 tone 尾部
  - 或合法的真实 gap

结论：

- 之前基于 `firstFrontEndFault` 一类统计窗口得出的
  `front-end-first`
  结论，不能再单独当作根因证据
- 后续所有 front-end 诊断必须满足：
  - 至少与 `recording16` 对齐比较
  - 或直接与错误字符 / 错误 tone-event 边界对齐
- 不能再让“正常 gap 的低信号窗口”伪装成 segmentation 根因

### H. 在 weak continuity rescue 里粗暴绕过 `TARGET_NOT_LOCKED`

已证伪做法：

- 只因为：
  - `mem=true`
  - anchor 仍在
  - `continuity window` 还活着
- 就直接允许 weak rescue 绕过 `targetToneLocked`

为什么错：

- `CwCaptureWrongCharacterAlignmentProbeTest` 已经直接证明：
  - 这样会把后续弱噪点、短毛刺也连续捞起来
  - tone 事件会碎成多个 0ms / 16ms 级别的假 `DIT`
- `Turn 1` 会从原来的 `K (-.-)` 进一步劣化成 `B (-...)`
- 这不是“更接近正确 `C`”，而是把 continuity window 变成了弱噪声自激器

结论：

- `targetToneLocked` 不能被整块移除
- 如果后续还要放宽这道门，也必须同时补上：
  - 更严格的 turn-local authority
  - continuity window 的单次权限 / 不可级联约束
  - 以及 same-character gap 范围限制

### I. 保留 weak onset chain 跨短 gap，并在 rescue 时无条件向前 backfill

已证伪做法：

- weak onset chain 命中后
  - 允许它跨 1-2 帧短空洞继续保留
- 后续只要 rescue 成立
  - 即使已经有 `frameLocal tone-on`
  - 也仍强制把 `toneOnTimestamp` 往更早的 weak chain 起点回填

为什么错：

- `CwCaptureWrongCharacterAlignmentProbeTest` 已直接证明：
  - 这会把前面的弱前导和后面的强起音糊成同一个长 tone
- `Turn 1` 会从基线的：
  - `K (-.-)`
  - 进一步坏成 `X (-..-)`
- probe 明确可见：
  - `TONE_ON @8704`
  - `TONE_OFF @8816 dur=112`
  - 原本不该存在的长 run 被错误补成了一个 `DAH`

结论：

- weak chain 只能作为“没有更好局部起点时”的保底参考
- 不能覆盖已经存在的 `frameLocal tone-on`
- 更不能靠“跨短 gap 保链”去赌 same-character continuity

### J. 在 continuity-anchor 的 far-tone tone-on 抑制上做泛化放松

已证伪做法：

- 给 `trusted continuity anchor` 增加“通用 strong far-tone 豁免”
- 试图让远频候选只要看起来够强，就直接绕过当前 `tone-on` 抑制

为什么错：

- 针对 `20260427_222505 / 20260427_224524` 的一次泛化实验已经直接验证：
  - 没有把 dirty case 稳定拉好
  - 反而把 `20260427_222505` live 主链从：
    - `recall=0.400`
    - 打穿到 `0.033`
- 这说明当前 continuity-anchor 抑制虽然偏保守
  - 但它同时承担了大量真正的防劫持职责
- 不能在没有更细局部条件的情况下
  - 直接加一个 broad exemption

结论：

- `far-tone release` 之后如果还要继续做
  - 必须走更局部的 probe
  - 例如：
    - 只限特定 band / 特定 lock history / 特定 candidate quality 组合
- 不再接受：
  - “为了救 dirty case，先把 continuity far suppression 放松一层看看”

## 当前主问题的正式拆解

### `recording16`

主病灶：

- experimental front-end startup / weak-valley / release 语义错误
- 早期就会把多个真实元素过度合并

当前第一目标：

- 不再让第一个 `C` 坏成 `6`
- 不再出现 `544ms accepted tone run` 这种早期 over-merge

### `capture.wav`

主病灶：

- live-like 输入下，弱锁定、近场失真、电平变化、重复播放导致的事件质量退化
- 以及“新前端 -> timing/decode”桥接健康度不够
- 但“低 `det/noise` window = front-end 主根因”这一条目前已经不成立

当前第一目标：

- 不再出现 repeated pass 明显越跑越坏
- 让 play1 / play2 / play3 的退化形态与 bench 可解释一致
- 新的分析入口应改成：
  - 错误字符窗口
  - 对齐 `recording16` 的相对时刻比较
  - 而不是继续追逐泛化的 `firstFrontEndFault`

## 新 RX 目标结构

### 1. Tone Tracking Layer

只负责回答：

- 当前怀疑主频点是什么
- 连续性如何
- 置信度如何

它不再直接等价于：

- keying on/off
- timing update authority

### 2. Keying Segmentation Layer

只负责回答：

- 当前是否构成一个健康的 tone run
- 当前 valley 是：
  - same-tone weak valley
  - 还是真正 gap
- 当前 onset 是否足够可信

### 3. CW Validity Gate

只负责准入：

- 哪些 candidate 可以进入 timing/decode
- 哪些 candidate 只能观察、不能采纳

至少要结合：

- SQL 越线
- 持续时间
- 频率连续性
- 包络平滑性
- burst / transient rejection

### 4. Timing / Decode Layer

只在前端事件已经足够健康时工作。

并且必须坚持：

- decode 权限
- 与 learn 权限

显式分离。

## 负反馈正式方案

当前正式方案不是“再调一轮参数”，而是三层负反馈。

### 当前 round 的两条主线

为避免再次陷入“边加探针边局部修补”的节奏，当前这一轮只承认两条主线目标：

1. 用负反馈机制压住单 turn 内后半段漂移
2. 用 turn 切分彻底隔离不同 turn 之间的相互污染

这两条才是这一轮的正式目标。

其它动作如果不能直接服务这两条主线，就不应成为当前重点。

尤其要明确：

- `fast acquire`
- `pre-trust bootstrap`
- `multi-hypothesis`

这些都不是第三条独立战线。

它们的职责只是：

- 帮系统更快、更稳地进入单 turn 的可信状态
- 从而为“turn 内稳定”和“turn 间隔离”服务

换句话说：

- turn 切分解决的是“上下文边界”
- 负反馈解决的是“同一上下文内不自激”
- 快速锁定解决的是“新上下文别慢吞吞、别一上来就选错路”

这三者是主从关系，不是三个并列项目。

### 先补一个统计视角

用户这一轮强调的“小上下文片段”与“稳定速度区间”非常重要。

因此接下来不应再把速度学习理解成：

- 一个全局单值不断被新事件改写

更合理的理解是：

- 在每个局部 `epoch` 内
- 系统维护一组关于 `dot / WPM` 的置信估计
- 早期快速收敛
- 中期低波动跟踪
- 末期在 epoch 结束时主动释放上下文

用更贴近统计的语言说：

- `TX WPM` 是弱先验
- 前若干个健康 tone/gap 是快速收敛证据
- 同一 epoch 内的稳定 decode 应持续收窄 posterior
- 异常事件不应轻易改写 posterior 中心
- epoch 结束后，再为下一段重新建模

也就是说，后续实现应尽量逼近这种行为：

1. `fast acquire`
2. `local stabilize`
3. `context release`

当前实现顺序也应服从同一个逻辑：

1. 先保证 `turn boundary` 真正切开
2. 再保证 `pre-trust` 能快速收敛到可解码区间
3. 最后用负反馈把中后段漂移压住

如果第 2 步只是“更快地选错”，或者第 3 步仍允许跨 turn 污染回流，

那都不算真正完成这一轮目标。

### 第一层：前端分段负反馈

新增两个前端量：

- `releasePressure`
- `onsetDebt`

#### `releasePressure`

在以下情况上升：

- `detection / releaseThreshold` 持续走低
- `recentLockedFrameRatio` 持续下降
- `toneDominanceRatio` 持续下降
- `narrowbandIsolationRatio` 持续下降
- active run 内部连续出现 weak valley
- 弱谷后的 resume 质量很差

在以下情况下降：

- 连续 strong frame
- 频点连续性稳定
- 包络重新健康

作用：

- 低压力：允许短 weak valley bridge
- 中压力：缩短 bridge 窗口，提高 resume 门槛
- 高压力：强制切开，不再继续机械 merge

#### `onsetDebt`

在以下情况上升：

- 刚发生一次高压力 forced split
- 短时间内多次低质量重起

在以下情况下降：

- 连续高质量 onset
- 新 run 建立后持续健康

作用：

- `onsetDebt` 高时，提高新 onset 准入门槛
- 防止“刚切开就被低质量 onset 立刻接回去”

### 第二层：时基学习负反馈

新增一个 `TimingAnchorController`，维护：

- `trustedDotMs`
- `retainedDotMs`
- `anchorConfidence`
- `learningDebt`

原则：

- 只有局部窗口足够自洽，才允许更新 `trustedDotMs`
- 坏窗口可以观测、可以 decode
- 但不自动拥有学习权

#### `pre-trust RAW commit discipline`

这轮之后，`trust` 尚未建立时的正式主线，不再是继续主攻 `multi-hypothesis`。

原因很直接：

- 前面的多路竞争 / 多组参数实验
- 目前没有给出稳定、可重复、明显优于单路径的收益
- 反而容易再次把主线带回“加探针、看 lane、调竞争规则”的旧循环

因此当前正式方案改成：

- 保持单条主路径继续 search / timing / decode
- 但在 `trust` 建立之前
- 不允许 `CHARACTER_DECODED / WORD_BREAK` 直接提交进 `RAW`

也就是说：

- `pre-trust` 阶段允许系统继续观察、试探、形成时基
- 但这些 final decode event 先视为 speculative
- 它们可以帮助建立 trust
- 但不能先污染 RAW 输出

只有满足以下条件后，gate 才能打开：

- 当前 turn 已建立 trusted timing
- 局部时基已经进入可信区间

gate 打开后的约束是：

- 从“第一个 trusted final decode event”开始，后续 RAW 正式提交
- gate 在本 turn 内保持打开
- turn 结束后必须清空并关闭

这条纪律的目标不是“让结果好看”，而是：

- 避免 opening 阶段的 phantom char 先写进 RAW
- 避免错误 `WORD_BREAK` 抢在真正字符之前占位
- 让 trust 的建立和 RAW 的正式提交，成为两个显式分离的步骤

同时明确：

- `multi-path / multi-hypothesis` 不是被永久否定
- 但在单路径 `pre-trust RAW commit control` 证明不够之前
- 它不再是当前 round 的主线投入方向

当前阶段的强约束是：

1. `pre-trust`

- final decode event 只能暂存或丢弃
- 不能进入 RAW

2. `post-trust`

- gate 打开
- 只允许 trusted 之后的 final decode event 进入 RAW

3. `turn-end`

- gate 关闭
- turn 内 speculative 状态全部释放

4. `RAW-only`

- 只有 admitted event
- 才能继续影响 `RAW / interpreter / WPM guard / semantic side effects`

动作：

1. 偏差开始变大

- 增加 `learningDebt`

2. `learningDebt` 过软阈值

- 冻结 learner
- 当前窗口不再改写 timing model

3. `learningDebt` 过硬阈值

- `active dot estimate` 回拉到 `trustedDotMs`
- 其次才回退到 `retainedDotMs`
- 最后才是 `seedWpm`

这意味着：

- 不再允许系统一坏就打回 `15 WPM`
- 优先回到最近真正工作过的时基

#### 这层还必须满足“同段稳定、小段释放”

也就是：

- 同一个 epoch 内
  - update 要偏向低波动
  - 稳定 decode 的速度估计要越来越难被短时异常拖走
- epoch 结束后
  - 这些局部统计量要允许被释放
  - 下一段重新学习

否则：

- 会重新掉回“全局长上下文 learner”这个错误模型

### 第三层：epoch 级 RX-only soft re-bootstrap

这层是保险，不是主解。

只有在以下状态持续成立时才触发：

- `learningDebt` 长时间高位
- `anchorConfidence` 长时间低
- 没有新的可信 decode 窗口
- 前端质量长期达不到最低学习门槛

它只允许重置：

- front-end run state
- timing learner state
- temporary pressure/debt integrator

默认不重置：

- decoder 已输出文本
- interpreter 已形成的上下文

必要时最多只 flush 当前 pending character。

## Bench 与验收基线

### `recording16`

必须持续盯住：

- 第一个 `C` 不能再坏成 `6`
- 早期不能再出现 `544ms accepted tone run`
- `front 40% / middle 30% / rear 30%` 的趋势不能再继续恶化
- 不能只看最终 WPM，必须同时看 run shape

### `capture.wav`

必须持续盯住：

- `play1 / play2 / play3`
- repeated pass 是否越跑越坏
- 真机链路放大的错误是否能在 live-like bench 里被解释

### 验收原则

- 同时看：
  - text similarity
  - event quality
  - timing stability
  - front-end segmentation health
- 不再只用“WPM 有没有被压住”当成功标准
- 还要明确看：
  - 新 epoch 是否能快速逼近可解码区间
  - 同一 epoch 内 WPM 是否保持小波动
  - epoch 结束后旧速度是否不再强污染下一段

#### noisy / weak case 的正式评估约定：关键信息优先

从这轮开始，弱信号、强噪声、QSB、手拍不稳这类 case 的 RX 验收，
正式采用“两层评估”：

- 第一层继续记录：
  - full-text similarity
  - opening fragment fidelity
- 第二层单独评估：
  - key information visibility

其中第二层优先级更高。

这里的 `key information` 主要指：

- `DE`
- callsign
- `RST`
- `QTH`
- `NAME`
- `PSE K / KN / BK / SK`
- 频点
- `WPM`
- 其它直接影响操作者判断和继续通联的内容

而像：

- `CQ`
- `QRZ`
- `CQ DX`

这类 opening fragment：

- 仍然要记录
- 但默认只作为次级指标
- 不能再为了保 opening 而牺牲后续关键字段的可见性

正式约定如下：

- 如果 opening 更漂亮，但后续 `DE / callsign / control tail` 崩掉：
  - 不判优
- 如果 opening 有损，但关键字段仍可见：
  - 判定为更实用
- 因此 noisy / weak case 的 probe 输出，后续应明确区分：
  - `primary practical tokens`
  - `secondary opening tokens`

`recording(7)` 是这条约定的直接样例：

- 比起死保 `QRZ?`
- 更重要的是保住 `DE BI3TUK KN`

也就是说：

- 如果前半段拆不漂亮，但 `DE BI3TUK KN` 仍可见
  - 这是可接受方向
- 如果 opening 看起来更像 `QRZ?`
  - 但后面 callsign 与结尾控制信息整体塌掉
  - 这不是当前主线想要的优化

2026-05-18 在 decoder 侧补上短尾 `DIT` 修补之后，
这条 sample 的当前事实已经和前一版不同，必须更新：

- 当前 `Live RAW` 已推进到：
  - `QR? DE BI3TUK KN.`
- 当前 `Live stable` 已推进到：
  - `QR? DE BI3TUK KN`
- 当前 `Live final / committed` 约为：
  - `?L? DE BI3TUK KN.`
- 当前 `Fixed RAW` 仍然是：
  - `/Z? DE BI3TUK KN.`
- 这轮 decoder 修补已经额外确认：
  - `0ms` 的假 `DIT` 不应再被当成真实 tone 吃进字符
  - 它此前会把尾部 `BI3TUK` 污染成 `6I3TUK`
  - 现在这类假扩展已经被压掉

因此这条 sample 的正式结论要改写为：

- `recording(7)` 当前最重要的净收益
  - 已经不再是“opening 是否更像 `QRZ?`”
  - 而是 `Live` 已重新保住 `DE BI3TUK KN`
- `Fixed`
  - 仍然在 opening 参考上略占优
  - 因为它还能露出 `/Z?`
- 但这已经不足以支撑继续为了追 `Z`
  - 去引入新的全局启发式
  - 或承担打坏 `DE / BI3TUK / KN` 的风险

所以这条 sample 后续应被降级为：

- `guardrail case`
  - 用来防止我们再次把 `DE BI3TUK KN` 弄丢
- 而不是：
  - 当前 RX 主线继续深挖的第一优先级

后续评估这类 noisy / weak case 时，应把这条新版 guardrail 写死：

- `Live` opening 没把 `Z` 找回
  - 不等于这轮优化没有价值
- `Live` opening 更像一点
  - 也不等于值得继续投入
- 只有当某个改动能在不回退 `DE / BI3TUK / KN` 的前提下
  - 再额外恢复 opening 里的 `Z`
  - 才值得重新打开这条分支
- 否则：
  - `recording(7)` 暂停继续深挖
  - 主线重新回到更脏、更影响整体 RX 质量的 case

`recording(12)` 则提供了另一条同样重要、但不同类型的约束：

- 当前它不属于 `FIXED_BETTER`
- 也不属于“某个 `Live` patch 纯正向、可以直接收编”

2026-05-18 在恢复基线后的 `gate sweep` 与 `BASE vs MERGE` 对比已经确认，
此前把 `MERGE` 记成“明确净正向”这一结论已经过时：

- `BASE`
  - `trust=5264ms`
  - `recall=0.897`
  - 文本约为：
    - `?Q CQ CQ DEEE BIC/MS BI9CMS B?SIN70/ PSE K.`
- `AUTH`
  - 与 `BASE` 基本等价
  - `trust=5264ms`
  - `recall=0.897`
- `ONSET`
  - 与 `BASE` 基本等价
  - `trust=5264ms`
  - `recall=0.897`
- `MERGE`
  - `trust=2864ms`
  - `recall=0.862`
  - 文本约为：
    - `?Q CQ CQ XE BIG/MS BI9CMS B?SIN 700 PSE K.`

这说明当前的真实情况是：

- `BASE` 才是目前总体最优解
- `MERGE` 的主要收益只剩下：
  - 更早进入 trusted 状态
  - 尾段 `700 / PSE / K` 的空格形态略更像目标文本
- 但 `MERGE` 已不再带来更高的总体 recall
- `AUTH / ONSET` 目前也没有提供任何可感知的净提升

从事件级对比看，当前能明确确认两件事：

- `DE` 邻域的首个 post-trust mismatch
  - `BASE` 虽然仍有碎裂，至少保住了 `D + E + E + E`
  - `MERGE` 则把 `-.. + . + .` 直接并成 `-..-`
  - 也就是把这里从 `DEE*` 进一步并坏成了 `X`
- 第一个 callsign unknown window
  - `BASE` 与 `MERGE` 都在同一个 anchor
    `@24044`
    产出了相同的超长 unknown 序列
    `...----..-.-..--`
  - 这说明 `MERGE` 并没有真正解决
    `recording(12)` 的 callsign cluster unknown 问题

因此当前对 `recording(12)` 的正式结论应改写成：

- `BASELINE_BEST`
- `MERGE_EARLY_TRUST_ONLY`
- `MERGE` 可以保留为历史实验分支
- 但不能再被视为当前主线候选解
- 更不能再写成：
  - “对 callsign cluster / 后半段已经形成明确净收益”

2026-05-18 同一轮新增的 `character diagnosis + canonical alignment` 还把
`BASE` 路径里的尾段破损进一步钉实了：

- `@20712 emit=B seq=-...`
  - 不是一个孤立的单字符误判
  - 而是后面整串尾段塌陷的起点
- 从 canonical 对齐看，实际更接近：
  - expected:
    - `... BI9CMS --- IN700 PSEK`
  - actual:
    - `... BI9CMS B?S IN70- PSEK`
- 也就是说：
  - `@20712` 的 `B`
  - `@24044` 的超长 unknown
  - 以及后续尾段的额外 `S`
  - 目前应视为同一段连续 corruption
  - 不是三个彼此独立的小毛刺

同一轮对这个 `@20712 / -...` 窗口做的单 tone omission sweep 也已经给出反证：

- 无论删掉 `196ms` 的首个 dah
- 还是删掉后面任意一个 dit
- recall 都会从 `0.897` 直接掉到约 `0.793`
- 尾部 `PSE K` 还会继续恶化

因此当前也可以正式写进约束：

- 不要再把 `@20712 / -...` 当成一个值得直接落生产逻辑的“低成本修补点”
- 这条线如果继续推进，优先级仍应回到：
  - `@24044` 那个超长 unknown cluster 本身
  - 以及它前后 continuity / segmentation 为什么会被拖成一整串连续坏段

2026-05-18 随后又对 `@24044 seq=...----..-.-..--` 本体补做了两轮更直接的反证：

1. unknown cluster 内部 `15` 个 intra-gap 单点 promotion sweep

- 所有单点 promotion 都会把 recall 从 `0.897`
  统一拉低到 `0.793`
- 而且都会把尾部 `PSE K`
  一起拖坏成近似 `?E S?`

2. 只挑三个最长 gap

- `@21112 136ms / 1.33intra`
- `@22444 140ms / 1.37intra`
- `@23371 123ms / 1.21intra`

做组合 promotion sweep 后，结论仍完全一致：

- 任意组合都没有带来净提升
- `recall` 仍统一停在 `0.793`
- 只是把 unknown cluster 改写成
  - `BS?`
  - `B?CM`
  - `BS?CM`
  之类不同形态
- 没有真正恢复 callsign / `IN 700`
  之间的有效边界

因此这里也可以正式收敛成一条约束：

- 不要再把 `@24044` 内部 gap 直接升格成 `LETTER_GAP`
  视为当前主线候选
- 当前它不是一个
  - “挑出一两个最长 gap 就能拆回正确字符流”
  的问题
- 更像是：
  - 上游 continuity / segmentation 已经先整体漂坏
  - 到 `@24044` 时只是以超长 unknown cluster 的形式显形

同一轮补做的 `cadence contrast` 也把这个判断量化了：

- `@24044` 这团 unknown cluster 内部的局部节奏大致是：
  - `dit median ~= 65ms`
  - `dah median ~= 202ms`
  - `gap median ~= 70ms`
- 但同窗 `20650ms .. 24044ms` 的 timing state 却仍然停在：
  - `stabilizedDot median ~= 121ms`
  - `rawDot median ~= 121ms`
  - `trustedDot median ~= 116.7ms`

这说明当前更像是：

- 局部 candidate 内部其实已经露出一段更快 cadence
- 但 timing model / continuity 主链没有及时重新贴回这段更快节奏
- 于是这些 `70ms .. 140ms` 的 gap
  在当时的全局 dot 参考下仍然被当成 intra-gap

但同一轮又补做的 `forced-dot reclass sweep`
也给这条线加上了一个必要限制：

- 即使在 test-only prototype 里
  强行把 `20712 .. 24044` 这段的 local dot
  改成 `70 / 80 / 90 / 100ms`
- 并按更快 dot 重新分类 tone/gap
- 总体 recall 仍统一停在 `0.793`
- 没有把 `@24044` 这一段真正救活

这说明：

- `timing estimate 偏慢`
  确实是问题的一部分
- 但它并不是唯一病灶
- 即使让 timing model 在这段里“跟上更快 cadence”
  当前也不足以单独恢复正确字符流

因此后续如果继续做这条线，
应把判断再收紧一层：

- 不要把它理解成一个纯 timing-model reanchor 问题
- 更像是：
  - timing estimate 偏慢
  - 再叠加更早的 continuity / front-end segmentation 污染
  共同导致了这段长 unknown candidate

因此后续如果继续推进 `recording(12)`，
更值得优先盯的不是：

- decoder 末端再做更多 raw-candidate split 小修补

而是：

- 为什么 `20712 .. 24044` 这段里
  local cadence 已经变快
  timing model 却仍维持在明显偏慢的 dot estimate 上

另外，`2026-05-18` 新做的分段 probe 还确认了一条很关键的限制：

- `8500ms .. 14760ms` 的中段
  - 单独切开后无论 `BASE` 还是 `MERGE`
  - 都不能独立正确解码
- `14760ms .. end` 的尾段
  - 单独切开后也不能独立正确解码
  - 只能零散露出 `700 / PSE / K` 一类尾部控制信息

这说明：

- `recording(12)` 当前不是“找对一两个局部修补参数，就能把第二段或第三段单独救活”的问题
- 它明显依赖前段已经建立起来的 cadence / context
- 因此后续不应再把主要时间投入到：
  - `recording(12)` 局部窗口单独修补
  - 或试图把中段、尾段做成“天然可独立解码”的目标

更现实的目标仍然应是：

- 不再围绕“是否扩大 `MERGE`”来推进
- 优先盯住：
  - `DE` 邻域 post-trust short-gap / short-char fragment
  - callsign cluster 进入低速尾段后
    为什么会收敛成超长 unknown 串
- 而不是追求 `recording(12)` 三段都能孤立正确

下一步如果继续做这条线，目标应是：

- 在 `BASE` 路径上找更窄的局部修正
- 尽量不引入会把 `DE` 继续并坏成 `X`
  的全局 front-end merge 类策略

在做到这一点之前，`recording(12)` 当前应判定为：

- `BASELINE_BEST_BUT_STILL_DIRTY`
- `MERGE advances trust, but does not solve the real unknown cluster`

2026-05-19 又补了一轮更贴近当前主线状态的 `SQL / front-end / local-repair` 复核后，
这段结论需要再收紧一次：

- 当前 `recording(12)` 的最佳前端工作点不是旧的 `sql=55`
  - 而是 `sql=15`
- 在这个工作点上：
  - 全段 `recall=0.931`
  - `trust=3536ms`
  - 最终文本约为：
    - `X Q CQ CQ DE BIGCMS BI9CMS BS??MS IN 700 PSE K.`
- `2032ms` 首段起音与 `8832ms` 中段起音都已经明确越过当前门限
  - 因此此时主病灶已不再是“SQL 太高导致信号没进来”

同一轮对 `8700ms .. 13360ms` 中段做的 window metrics 也已经量化确认：

- `sql=0 .. 55`
  - `active` 仅在 `61.5% .. 63.2%`
  - `locked` 固定在 `79.0%`
  - `aq` 固定在 `79.4% .. 80.1%`
  - `toneOn/toneOff` 也都基本不变
- 这意味着：
  - 在当前版本里，继续把 `recording(12)` 的主问题归因到 `SQL`
    已经不成立

更重要的是，基于 `sql=15` 的几种局部修补 probe 现在都给出了一致反证：

- `@20712 / -...` 的单 tone omission sweep
  - 四种删法全部把 `recall`
    从 `0.931`
    拉低到 `0.828`
- `@24044` 附近 unknown cluster 的 forced-dot reclass sweep
  - `70 / 80 / 90 / 100ms`
    全部统一掉到 `0.828`
- 第一段 callsign unknown 的 local timing repair
  - `drop-only = 0.828`
  - `promote-only = 0.897`
  - `drop+promote = 0.897`
  - 仍然都不如当前基线 `0.931`

因此这条线现在应正式新增一条约束：

- `recording(12)` 当前可以接受：
  - `sql=15` 作为 front-end 基线
- 但不应再继续把时间花在：
  - `@20712`
    的删 tone
  - `@24044`
    的 forced-dot / gap-promotion
  - 或其他局部 test-only patch
- 如果后续还要继续推进它：
  - 应优先回到更上游的 continuity / segmentation 机制
  - 而不是继续在 decoder 末端做局部缝补

## 实施顺序

### P0. 先不再扩散杂音

- 不再往旧 `WPM` 补丁线上继续投入
- 不再新增更多后置 gate 组合试验
- 不再把新实验结论和旧历史噪音混写在主文档里

### 2026-05-19 对 `P1 / P2 / P3` 的批判性复核

经过这几轮以后，原来写下这三条时隐含的假定，
已经不能不加区分地继续照单全收。

当前应先把三件事拆开看：

- “front-end segmentation 仍是主病灶”
  - 这条大判断目前仍然成立
- “因此下一步就应该先做 `FrontEndPressureController`”
  - 这条具体实施假定已经被明显削弱
- “`TimingAnchorController` 还会继续是主收益来源”
  - 这条也需要收紧，只保留为必要的负反馈基础层

也就是说：

- `P2`
  - 被进一步坐实
- `P1`
  - 需要降级成待验证假说
- `P3`
  - 保留，但不再默认它能单独推进主线质量

下面分别改写。

### P1. `FrontEndPressureController`

先落：

- `releasePressure`
- `onsetDebt`

因为 `recording16` 当前第一病灶仍是前端过度合并。

#### 2026-05-19 复核后需要改写的地方

这条当时成立，靠的是一个隐含推断：

- 既然 `recording16` 暴露的是 opening over-merge
  - 那么 `releasePressure / onsetDebt`
    很可能就是下一步最直接的杠杆

但目前新增的代表性 sweep 已经给出反证：

- test-side `releasePressureHold` 原型结果：
  - `recording16`
    - `1.000 -> 1.000`
  - `capture.wav`
    - `1.000 -> 1.000`
  - `recording(15)`
    - `0.950 -> 0.950`
  - `recording(7)`
    - `0.063 -> 0.063`
  - `recording(12)`
    - `0.800 -> 0.686`

这说明：

- 当前这版 `releasePressure` 风格原型
  - 没有证明自己能带来主收益
- 反而已经确认
  - 会剐到 `recording(12)`

因此现在更准确的判断应改写成：

- `P1` 背后的大方向
  - “front-end 需要负反馈”
    - 仍然成立
- 但 `FrontEndPressureController`
  - 不应再被默认视为当前第一落点
- 这版 `releasePressure / onsetDebt`
  - 暂时只保留为实验性思路
  - 不应直接升格进正式主线

### P2. 新 experimental segmentation 替换旧 run 语义

目标：

- 先让 experimental front-end 真正生成更健康的 tone/gap 事件
- 再谈接回主链

#### 2026-05-19 复核后反而更被坐实

相比 `P1`，这条不是被削弱，而是被这几轮结果进一步加强了。

新增证据主要有三类：

- `recording(12)`
  - `sql=15` 已经把“信号没进来”基本排除
  - 多个 local repair sweep 也都统一证明：
    - decoder 末端缝补不是主解
  - 更上游的 continuity / segmentation 语义才是主病灶
- `recording(10)`
  - `HYBRID @ 700 = 0.9718`
  - `STATIC_FIXED @ 700 = 0.9718`
  - `STATIC_AUTO @ 700 = 0.6620`
  - `FIXED_UNTIL_TRUST_THEN_AUTO @ 700 = 0.6831`
  - 这说明：
    - 后层 `auto-track / trust-then-auto`
      不是当前收益来源
    - 更早形成的 tone/gap 结构
      才决定了最终上限
- `P1` 原型自身没有证明收益
  - 反而从反面支持：
    - 现在缺的不是“再补一个 holding gate”
    - 而是更根本的 run / gap 语义重写

因此当前主线应明确更新为：

- `P2`
  - 才是下一步真正的一号位
- 目标不是继续堆 gate
  - 而是把 `CwSignalProcessor` 里旧 run 语义、
    weak continuity、bridge、post-release rescue
    之间的职责重新拆开
- 只有 front-end 真正能生成更健康的 tone/gap 事件
  - 后面的 `TimingAnchorController / RawCommitGate`
    才有稳定输入可接

#### 2026-05-19 当前已确认的第一道安全切口

继续往下做 `P2` 时，不能再直接从阈值下手。

当前代码复盘已经确认：

- `tone on` 入口里
  - 同时混着：
    - fresh attack qualification
    - post-release continuity rescue
    - trusted continuity re-open
- `tone off` 入口里
  - 同时混着：
    - same-tone weak valley hold
    - release-tail hold
    - post-release continuity debt 的后续影响

因此第一步最安全的结构切口不是：

- 直接改 `release / attack` 数值
- 也不是继续补更多 gate

而是：

- 先把 `tone on admission` 这团共享语义单独收口
- 让 live path 与 forced replay path 共用同一套 onset admission context
- 后续再分别拆：
  - true fresh onset
  - continuity rescue
  - trusted continuity reopen

这一步已经开始落地到 `CwSignalProcessor` 内部结构上。

它的意义不是“已经修好了某个脏 case”，而是：

- 先把 `P2` 的第一刀从“参数试错”
  - 改成
  - “职责切分”
- 避免后续每次想动 continuity / rescue
  - 都要在 live 与 forced path 各改一遍
  - 并再次把三种语义一起搅动

因此紧接着的下一刀应明确为：

- 继续保守拆分 `toneActive` 分支
- 把：
  - weak-valley same-tone bridge
  - release-tail hold
  - post-release rescue continuation debt
  - 的 owner 再分开
- 在做到这一步之前
  - 不再优先回到全局阈值调参
  - 也不把局部 sample 的偶然改善误判成 `P2` 已完成

#### 2026-05-19 当前已继续落下的第二道结构切口

在第一刀把 `tone on admission` 收口之后，当前又继续把
`toneActive` 侧的 release 评估拆成共享结构：

- release candidate 资格化
- same-tone weak-valley bridge
- near-target release-tail hold
- 真正 `tone off` 提交

这一步的目的同样不是：

- 直接改变 release 行为

而是：

- 先把 live path 与 forced replay path 里高度重复的
  `toneActive` 分支收成同一套 release context / helper
- 让后续继续拆：
  - bridge
  - tail hold
  - post-release continuity debt
  - 时，不需要同时修改两套几乎相同的长分支

当前应把这条结构性进展理解成：

- `P2` 现在已经不只是“方向判断”
- 而是已经开始在 `CwSignalProcessor` 内部
  - 逐步把旧 run 语义的大杂糅
  - 改造成可以单独下刀的职责边界

#### 2026-05-19 当前已继续落下的第三道结构切口

在 `tone on admission` 与 `toneActive release` 之后，
这一轮继续往中间那层推进：

- 把 `post-release continuity debt`
  - 的派生判断
  - 与生命周期写入点
  - 开始分家

当前已经明确分开的两类职责是：

1. 派生上下文

- 当前 frame 是否还在 continuity window 内
- 是否形成 weak onset chain candidate
- 是否已经进入 weak continuity rescue candidate
- rescue budget 是否耗尽
- 是否只是 weak continuity cooldown pressure

2. 生命周期写入

- tone-on 被 rescue 接受后，如何累加 / 清空 debt
- rescued tone 在运行中“毕业”后，如何整体退出 debt
- rescued short tone 结束后，如何把 continuity window 传给下一段
- reset / beginNewTurn 时，如何统一清空 debt 与 weak onset chain

这一步的意义是：

- 以后如果 `recording(12)`、`recording(15)` 还要继续追 continuity / reacquire
  - 我们可以明确地区分：
    - 是 debt 派生条件不对
    - 还是 debt 生命周期 owner 不对
- 而不是继续在一堆 scattered field write 里追布尔值

#### 2026-05-19 当前已继续落下的第四道结构切口

这一步继续沿着 `release-tail hold` 往下拆：

- 把 `isNearTargetReleaseTailCandidate(...)`
  - 里直接消费 continuity / rescue 状态的那层判断
  - 收成单独的 eligibility context 与 threshold selector

当前已经被显式分开的职责是：

1. eligibility 派生

- recent locked history 是否足够支持 tail hold
- current run 是否已经达到 stable bootstrap
- current run 是否只够 weak bootstrap
- rescued tone 是否还处在需要更强 tail evidence 的窗口
- rescue bootstrap window 是否仍在生效

2. consumer-side debug / threshold 选择

- 哪些 eligibility 应写回 debug trace
- release-tail hold 这一帧到底该吃哪一档 detection threshold
- 哪些 case 属于正常 trust
- 哪些 case 属于 current-run bootstrap / weak bootstrap / rescue bootstrap

这一步的价值不在于：

- 立刻把 `recording(12)` 或 `recording(15)` 再往前推一截

而在于：

- 现在 release-tail 这层终于不再把
  - trust 判断
  - rescue 窗口消费
  - threshold 选择
  - debug 写回
  全部糊在一个长分支里
- 后面如果还要继续收拾
  - `currentToneStartedByPostReleaseRescue`
  - `shouldGraduateCurrentPostReleaseRescuedTone(...)`
  - `isCurrentToneRunWeakBootstrapForReleaseTailHold(...)`
  这些剩余 consumer
  就有了更清楚的下刀边界

#### 2026-05-19 当前已继续落下的第五道结构切口

这一步继续追前一刀留下的 `currentToneStartedByPostReleaseRescue`：

- 把“当前 run bootstrap 状态”
  - 和“rescued tone progress / graduation 状态”
  - 进一步显式化

当前已经被单独抽出的两层语义是：

1. `CurrentToneRunBootstrapContext`

- 当前 run 是否已经有效开始
- 当前 run 已经持续了多久
- 当前 run 是否满足 stable bootstrap
- 当前 run 是否满足 weak bootstrap
- rescued tone 与普通 tone
  在 weak bootstrap 上是否要吃不同的 frame qualification

2. `PostReleaseRescuedToneProgressContext`

- rescued tone 当前是否还处在 rescue 窗口
- 当前 tone 是否仍然贴着 continuity anchor
- recent locked history 是否已经足够把 rescued tone 视为“毕业”
- graduation 到底依赖 stable bootstrap 还是 weak bootstrap

这一步的意义是：

- `shouldGraduateCurrentPostReleaseRescuedTone(...)`
  不再自己重新拼：
  - 是否 rescued
  - 是否 still-on-anchor
  - 是否 recent trust 足够
  - 是否已经 bootstrap 到可毕业
- `isCurrentToneRunWeakBootstrapForReleaseTailHold(...)`
  也不再独自解释
  rescued / non-rescued 当前 run
  的 bootstrap 资格

换句话说：

- 现在 `currentToneStartedByPostReleaseRescue`
  已经不再只是一个到处被 consumer 各自读取的裸布尔值
- 而是开始被收敛成
  - 当前 run bootstrap 语义
  - rescued tone graduation 语义
  两块更清楚的 owner 语境

这会让下一步继续处理：

- `shouldSuppressNearTargetWeakValleyWithoutHardLock(...)`
- release-tail / weak-valley 对 rescued tone 的剩余差异

时，更容易分辨：

- 哪些是 bootstrap 资格问题
- 哪些是 rescued tone 仍然过脆、不能过早参与其它 continuity rescue

#### 2026-05-19 当前已继续落下的第六道结构切口

这一步继续把 weak-valley 路径上残留的 run-fragility 语义收口：

- 把 unlocked weak-valley rescue
  对
  - `currentToneStartedWithoutTrackedMemory`
  - `currentToneStartedByPostReleaseRescue`
  - 当前 run 是否已经成熟到可以跨短 valley
  的消费
  收成单独的 continuity guard context

当前已经被显式收起来的语义是：

1. opening run fragility

- 一个没有 tracked-memory continuity 起家的 opening run
  还没资格立刻拿 unlocked weak-valley rescue
- 它需要先证明本地 cadence
  再允许“短 valley 继续粘住”

2. rescued tone fragility

- 一个刚被 post-release rescue 重开的 run
  仍然属于 fragile continuity candidate
- 在它自己完成 graduation 之前
  不能马上又去吃 unlocked weak-valley glue

3. current-run maturity

- 即便不是 opening run
- 也即便不是 rescued tone
- 当前 run 本身仍然要先长到足够的最小持续时间
  才允许 weak-valley path 把短 valley 当成 continuity

这一步的价值是：

- `shouldSuppressNearTargetWeakValleyWithoutHardLock(...)`
  现在终于不再自己零散解释
  “当前 run 为什么还太脆”
- 后面如果还要继续追：
  - rescued tone 与 weak-valley 的交界
  - opening run 与 local cadence proof 的边界
  - continuity hold 与 bootstrap hold 的职责分离
  就不用再围着两个 run-local flag 到处读条件了

#### 2026-05-19 当前已继续落下的第七道结构切口

这一步回到 write-side owner：

- 把 tone-on accepted 之后的 run activation
- tone-off 之后的 post-release debt 传递
- hard reset / frame-gap reset 之后的 run-local teardown

开始从长分支里拆出来

当前已经显式收起来的 owner 职责是：

1. accepted tone run activation

- tone-on 被接受后
  - 是否属于 post-release rescue
  - 是否没有 tracked-memory continuity 起家
  - 是否允许 strong rescue debt reset
  - run-local continuity support state 如何初始化
  这些写入现在不再散在 live path / forced replay path 两处长分支里

2. tone-off debt carry

- rescued short tone 结束后
  是否要把 continuity window 传给下一段
- 如果不该传
  就在哪里清掉 continuity window / rescue count

这层现在也不再直接把
`currentToneStartedByPostReleaseRescue`
和 tone duration
散着读在 tone-off 分支里

3. hard reset / frame-gap reset teardown

- run-local continuity support state
  包括：
  - weak-bootstrap hold count
  - weak-valley bridge state
  - release-tail hold state
- 与 post-release rescue debt / chain state
  终于开始有了更清晰的 teardown helper

这一步的价值是：

- 到这里 `P2` 已经不只是“把 consumer 条件拆开”
- 而是开始把
  - run activation owner
  - run teardown owner
  - debt carry owner
  也从原始长分支里剥出来

所以后面如果继续追：

- live path 与 forced replay path
  run start 的真正差异
- rescued tone 结束后
  continuity debt 该怎么延续
- frame-gap reset
  是否不该误留某些 continuity carry

就可以更明确地区分：

- 是 lifecycle owner 不对
- 还是具体 eligibility / threshold consumer 不对

#### 2026-05-19 当前已继续落下的第八道结构切口

这一步继续往 `post-release continuity debt` 内部收：

- 把 weak onset chain 本身的 state
- trusted weak chain 的 state
- weak continuity rescue budget

从“几组 field 在不同 helper 里交叉读写”
继续拆成更清楚的 owner 语义

当前已经开始显式分开的两块是：

1. `WeakPostReleaseOnsetChainState`

- 普通 weak chain start
- trusted weak chain start
- trusted weak chain frame count

现在这组 state 不再只是 scattered field：

- `updateWeakPostReleaseOnsetChain(...)`
  已经开始通过单独 lifecycle context 派生
- `hasTrustedWeakPostReleaseOnsetChain()`
- `weakPostReleaseOnsetChainToneOnTimestampOrFallback(...)`
- trusted chain clear

也都开始围着这块 state 转

2. `PostReleaseWeakContinuityBudgetContext`

- 当前 continuity debt 最多允许多少次 reopen
- 当前 continuity window 下 budget 是否已经耗尽

这层语义现在不再在：

- `buildPostReleaseContinuityDebtContext(...)`
- `buildPostReleaseToneOnRescueContext(...)`

各自重新算一遍

这一步的意义是：

- 到这里 `post-release continuity debt`
  已经不再只是一个抽象名词
- 它内部至少开始分成：
  - chain state owner
  - rescue budget owner

因此后面继续追 dirty case 时
我们可以更清楚地区分：

- 是 weak chain 自己记错了 onset 起点
- 还是 trusted chain 没有正确毕业 / 清空
- 还是 rescue budget 该给 extra reopen
  却没有给

换句话说：

- 现在如果 `recording(12)` / `recording(15)` 继续卡 continuity
  我们终于可以开始问：
  - chain 错了？
  - budget 错了？
  - 还是 consumer 错了？
而不是再把三件事混成一个“continuity debt 怪怪的”

#### 2026-05-19 当前已继续落下的第九道结构切口

这一步继续把 `continuity debt` 的 clear / carry owner 具体化：

- 引入了更完整的 `PostReleaseContinuityDebtState`
- 让
  - continuity window
  - weak continuity rescue count
  - weak/trusted onset chain state
  开始有同一个 snapshot / remember 入口

当前已经开始统一走 state transform 的边界是：

1. accepted tone-on 之后

- 非 rescue tone-on
  清 window + rescue budget
- rescue tone-on
  增加 rescue count
  或按 strong rescue 条件清 window + budget

这些不再直接 scattered write 到 field 上

2. tone-off 之后

- rescued short tone
  把 continuity window 传给下一段
- 否则
  清掉 window + rescue budget

这层现在也开始围着 debt state 变换
而不是在 tone-off 分支里直接写 field

3. partial clear / full clear

- `clearPostReleaseContinuityWindowAndRescueCount()`
- `clearWeakPostReleaseOnsetChainState()`
- `clearCurrentPostReleaseRescueState()`

这三种边界现在终于更像：

- clear budget only
- clear chain only
- clear full debt

而不是三组互相覆盖的 field write

这一步的价值是：

- 后面如果我们继续追
  graduation / reset / carry
  边界问题
- 就可以开始明确讨论：
  - 这一步该做 partial clear
  - 还是 full clear
  - 还是只更新 window / count

也就是说，到这里 `continuity debt`
终于开始从“若干 field + 若干 helper”
变成：

- 一块可以讨论 owner 与 transform 的状态体

#### 2026-05-19 当前已继续落下的第十道结构切口

这一步继续把同一批 debt clear
从“按结果形状清”
往“按业务原因转移”再推进一层：

- 去掉了 `PostReleaseContinuityDebtClearMode`
- 不再让 caller 自己知道
  这次该做的是：
  - `FULL`
  - `WINDOW_AND_RESCUE_BUDGET`
  - 还是 `WEAK_CHAIN_ONLY`

现在开始显式按下面这些原因落入口：

1. accepted tone-on

- `clearWeakPostReleaseOnsetChainAfterAcceptedToneOn()`
- `transitionedPostReleaseContinuityDebtStateAfterAcceptedNonRescueToneOn(...)`
- `transitionedPostReleaseContinuityDebtStateAfterStrongRescueDebtReset(...)`

也就是说：

- caller 现在表达的是
  - 这是普通 accepted tone-on
  - 还是 rescue 成功后又触发了 strong reset
- 而不是“我想清掉某两块 field”

2. tone-off without carry

- `transitionedPostReleaseContinuityDebtStateAfterToneOffWithoutCarry(...)`

这让 tone-off 分支可以直接说清楚：

- 这是一个没有继续 carry continuity window 的结束

而不是继续复用一个通用的 budget clear 形状

3. current rescued run 的收尾

- `resetCurrentPostReleaseRescueStateAfterRunReset()`
- `graduateCurrentPostReleaseRescueState()`

这两个现在虽然结果都还是 full clear，
但语义已经分开成：

- stream / frame-gap / lifecycle reset
- rescued run 已经成熟后的 graduation

这一步的价值主要不是“代码更短”，
而是：

- 后面如果我们继续追
  `recording(12)` / `recording(15)` 这类 continuity 边界
- 就终于可以明确问：
  - 是 accepted non-rescue tone-on 清错了
  - 是 strong rescue reset 清错了
  - 是 tone-off no-carry 清错了
  - 还是 run reset / graduation 混了

换句话说：

- 第九刀把 debt state 聚拢起来
- 第十刀开始把“为什么变”从 helper 名字上说清楚

#### 2026-05-19 当前已继续落下的第十一道结构切口

这一步开始处理 `continuity debt` 的 consumer 侧重复计算：

- 去掉了单独的 `PostReleaseWeakContinuityBudgetContext`
- 引入了统一的 `PostReleaseContinuityObservation`

现在这块 observation 统一承接的是：

- continuation window 当前是否 active
- weak continuity rescue budget 上限
- 当前是否已经 hit limit
- 当前是否处在 weak continuity cooldown

也就是说，
原来 scattered 在不同 caller 里的这些判断：

- window active 吗
- trusted weak chain 该不该给 extra rescue
- budget limit 到了没有
- cooldown 还在不在

现在开始先被观察成一个 consumer-facing state，
再由：

- `buildPostReleaseContinuityDebtContext(...)`
- `buildPostReleaseToneOnRescueContext(...)`
- `shouldAllowNearTargetPostReleaseToneOn(...)`

这些入口消费。

这一步尤其重要的一点是：

- tone-on admission 侧
  不再自己一处算 budget
- rescue gate 侧
  也不再另一处再算一遍 budget

而是改成：

- `observePostReleaseContinuity(...)`
  先统一产出当前 continuity observation
- 后续 admission / rescue consumer
  只消费这份 observation

这一刀的价值在于：

- 后面如果继续追
  weak rescue limit / cooldown / trusted boost
  相关 dirty case
- 我们可以更明确地区分：
  - observation 算错了
  - 还是 consumer 用错了

换句话说，
第十刀把“为什么清”拆出来之后，
第十一刀开始把“当前 continuity 状态怎么看”
也从各处局部推导
收成一份统一观察结果

#### 2026-05-19 当前已继续落下的第十二道结构切口

这一步继续往 tone-on admission 本身切：

- 新增了 `ToneOnAdmissionObservation`
- 把原来 `buildToneOnAdmissionContext(...)`
  里面混在一起的三类工作拆开：
  - observe
  - decide
  - context assembly

现在这条链路已经开始分成：

1. `observeToneOnAdmission(...)`

负责收集当前这帧和 continuity 侧的事实：

- attack qualified 了吗
- trusted continuity tone-on candidate 了吗
- weak onset chain candidate 了吗
- frame-local onset timestamp 有没有
- steady late-gap / low-growth strong steady 这些 rescue 事实有没有
- 当前 continuity observation / debt context 是什么

同时把 weak onset chain 的记忆更新留在 observation 段完成，
不再混在最后的准入结论里

2. `decideNearTargetPostReleaseToneOnRescue(...)`

负责回答一个更窄的问题：

- 基于 observation，
  这一帧最终要不要按 near-target post-release rescue 接受

3. `rememberToneOnAdmissionDecision(...)`
   和 `toneOnAdmissionContextFromObservation(...)`

负责把：

- debug / trace 需要的 decision 侧结果
- 最终 live / forced replay 共用的 `ToneOnAdmissionContext`

再收口起来

这一步的价值是：

- 后面如果我们再追 tone-on 前沿问题
  就可以先问：
  - 是 observation 看错了
  - 还是 decision 放错了
  - 还是最终 context 装配错了

而不是继续把三种问题都塞进
`buildToneOnAdmissionContext(...)`
这一个大入口里

#### 2026-05-19 当前已继续落下的第十三道结构切口

这一步开始处理 tone-on consumer
在 `shouldAttemptToneOn(...)` 之后那一长串共用 gate：

- 新增了 `ToneOnAttemptResolution`
- 把 live / forced replay
  在 `far-attack` / `edge-free far-carrier`
  之后真正共用的那段决策收拢起来

当前被明确收进这层 resolution 的是：

- post-release steady suppression
- weak-chain fallback attack
- micro-gap tone-on block
- tone-on threshold / tone-on timestamp 的最终求解

也就是说，
原来散在两个分支里、顺着写下去的一长串：

- bypass suppression 说明
- 两档 steady suppression 判定
- weak-chain fallback 判定
- tone-on threshold 计算
- tone-on timestamp 回填 / backfill
- micro-gap block

现在开始围绕：

- `buildToneOnAttemptResolution(...)`

这一个共享入口收口。

这里特意保留的边界是：

- live 路径自己仍然保有
  - continuity exhausted 的早退
  - far-attack confirm
  - edge-free far-carrier block
  - observability side effects
- forced replay 仍然保有
  - 自己那条较窄的前置 gate
  - 不同的 blocked-frame side effects

换句话说，
这一步不是把 live / forced 硬并掉，
而是只把它们真正已经同构的
`post-carrier-gates tone-on resolution`
抽出来。

这一步的价值在于：

- 后面如果我们继续追
  tone-on 被 suppression / fallback / micro-gap 挡掉
  的 dirty case
- 就可以先问：
  - resolution 算错了吗
  - 还是 live / forced 各自前面的 gate 顺序不同

而不是继续在两条长分支里
同时改三四个点

#### 2026-05-19 当前已继续落下的第十四道结构切口

这一步继续把 tone-on consumer
在真正进入 `post-carrier-gates tone-on resolution`
之前的那层前置 gate 收拢起来：

- 新增了 `ToneOnAttemptFrontGateResolution`

当前被明确放进这层前置 resolution 的是：

- continuity chain exhausted
- far-attack confirm
- trusted far-carrier block
- edge-free far-carrier block

也就是说，
原来 live / forced replay
各自 tone-on 分支前面散着的：

- `exhaustedPostReleaseContinuityAttackCandidate`
- `shouldDelayFarAttackToneOn(...)`
- `shouldBlockEdgeFreeFarCarrierToneOn(...)`

现在开始通过：

- `buildToneOnAttemptFrontGateResolution(...)`

统一给出前置 gate 结论。

这里仍然刻意保留的边界是：

- live 路径
  依然决定自己的 blocked-frame observability side effects
- forced replay
  依然保有自己更轻的 blocked-frame 处理
- edge-free far-carrier block
  仍然只在 live 这条线上启用

换句话说，
第十三刀收的是：

- 前置 gate 之后的共用 tone-on resolution

第十四刀收的是：

- 真正进入那层 resolution 之前的 front gate resolution

这一步的价值在于：

- 后面如果我们继续追
  far-attack / far-carrier / continuity exhausted
  这些“还没进真正 tone-on 尝试就被挡掉”的 case
- 就可以先问：
  - front gate resolution 算错了吗
  - 还是 live / forced 各自对 blocked 结果的消费不同

而不是继续把：

- front gate
- post-carrier-gates resolution
- blocked-frame side effects

三层东西挤在同一个长分支里

#### 2026-05-19 当前已继续落下的第十五道结构切口

这一步继续把 tone-on blocked path
真正消费 resolution 结果时的 side effects
收成统一入口：

- 新增了 `ToneOnAttemptFrameSideEffectMode`
- 把
  - front gate resolution 被挡住之后
  - post-carrier-gates resolution 被挡住之后
  这两类 blocked-frame 消费
  都统一走 helper

当前开始统一的东西是：

- `consumeBlockedToneOnAttemptFrontGateResolution(...)`
- `consumeBlockedToneOnAttemptResolution(...)`
- `rememberBlockedToneOnAttemptFrame(...)`

也就是说，
现在我们终于开始把：

- 决策本身是什么
- 被挡住之后 `lastToneOnDecision` 该记什么
- 这一帧该不该更新 leader observability / tone activity window

这三件事拆开看：

1. resolution

- front gate resolution
- post-carrier-gates resolution

2. decision labeling

- `toneOnDecisionForFrontGateResolution(...)`
- `toneOnDecisionForAttemptResolution(...)`

3. blocked-frame side effects

- live observable
- replay only

这一步刻意没有把 accepted-frame side effects
一起混进来，
因为那会把“被挡住”和“被接受”两类消费又重新缠回去。

换句话说，
到第十五刀为止，
tone-on 这一段已经开始拆成：

- observation
- front gate resolution
- post-carrier-gates resolution
- blocked-frame consumption

后面如果继续追
某个 case 为什么“明明算对了却还是表现不对”，
我们终于可以先问：

- 是 resolution 错了
- 还是 blocked-frame consumption 错了
- 还是 accepted-frame consumption 错了

而不再是一条几百行分支里
所有事情一起动

#### 2026-05-19 当前已继续落下的第十六道结构切口

这一步继续把 tone-on 顶层链最后两类
还散落在 live / replay 分支里的消费收口：

1. accepted-frame consumption

- 新增了 `ToneOnAcceptedFrameSideEffectMode`
- 把 accepted tone-on 之后这几件事统一收口：
  - accepted decision labeling
  - accepted lifecycle build / activate
  - tone-on event emit
  - accepted-frame side effects

当前开始统一走的是：

- `consumeAcceptedToneOnAttempt(...)`
- `rememberAcceptedToneOnAttempt(...)`
- `toneOnDecisionForAcceptedAttempt(...)`
- `rememberAcceptedToneOnAttemptFrame(...)`

2. pre-attempt label 与 no-event frame 收尾

- `rememberPreAttemptToneOnDecision(...)`
- `toneOnDecisionBeforeAttempt(...)`
- `rememberNoEventToneOnFrame(...)`

也就是说，
现在 tone-on 顶层链不再只把

- blocked-frame consumption

收起来，
而是连：

- accepted-frame consumption
- no-event frame consumption
- pre-attempt status labeling

也一起开始从主分支里剥离出去。

到这里为止，
tone-on 这一条主线已经基本被分成：

- observation
- front gate resolution
- post-carrier-gates resolution
- blocked-frame consumption
- accepted-frame consumption
- no-event frame consumption
- pre-attempt labeling

这一步的价值是：

- 后面如果 tone-on 再出问题，
  我们已经可以非常具体地区分：
  - 是 observation / resolution 错
  - 还是 blocked / accepted / no-event 消费错
  - 还是只是 pre-attempt label 误导了 trace

换句话说，
就 tone-on 这条主链本身而言，
现在已经不太需要再继续做“大块拆分”了；
后面更像是：

- 把类似思路复制到 tone-active release / tone-off 收尾
- 或者回头做更细的 consumer 边界净化

#### 2026-05-19 当前已继续落下的第十七道结构切口

这一步开始把同样的分层方式
复制到 `tone-active release -> tone-off` 这条链：

- 新增了 `ToneActiveReleaseResolution`
- 新增了 `ToneOffAcceptedEventContext`
- 新增了 `ToneActiveReleaseFrameSideEffectMode`

当前已经开始明确分开的层次是：

1. tone-active release resolution

- `buildToneActiveReleaseResolution(...)`

现在它明确区分：

- reset release attempt
- continue weak-valley bridge
- continue release-tail hold
- wait for tone-off hang
- emit tone-off

2. tone-active release consumption

- `consumeToneActiveReleaseResolution(...)`

也就是说，
前一层只负责回答：

- 这一帧 release 语义上属于哪一类

后一层才负责：

- continuation frame 的 side effect
- held-tail extension
- accepted tone-off emit
- 或者 simply fall through 给 no-event frame

3. accepted tone-off event construction / consumption

- `buildToneOffAcceptedEventContext(...)`
- `consumeAcceptedToneOffEvent(...)`

这让 tone-off event 本身开始不再和
hang 判断、weak-valley bridge、release-tail hold
继续挤在同一个大分支里。

这一步的价值是：

- 后面如果我们追
  “为什么这帧只是继续 hold”
  和
  “为什么这帧已经 emit tone-off”
  的 dirty case
- 就可以先看：
  - release resolution 算错了吗
  - 还是 accepted tone-off consumption 错了

而不是继续在 `handleToneActiveReleaseFrame(...)`
里同时看：

- release qualification
- continuity hold
- tone-off hang
- tone-off emit
- frame side effects

到这里为止，
跟 tone-on 对应的同类主链，
已经开始复制出相同的结构模板。

当前还值得继续按同类方式收尾的，
主要剩下两块：

- `tone-active continuation / no-event frame bookkeeping`
  这一族 helper 还能再统一一下命名与消费边界
- `tone-off` 后的
  debt carry / run reset / floor update
  还可以再切成更清楚的 accepted-event consumption seam

#### 2026-05-19 当前已继续落下的第十八道结构切口

这一步继续把第十七刀里剩下的两条尾巴收到底：

1. frame bookkeeping family

- 新增了 `FrameBookkeepingMode`
- 把下面这些原来各自带一点点 side effect 差异的 helper
  开始统一到同一套 frame bookkeeping 语义上：
  - blocked tone-on frame
  - accepted tone-on frame
  - tone-active continuation frame
  - no-event tone-on frame

当前开始统一通过：

- `rememberObservedFrame(...)`
- `frameBookkeepingModeForBlockedToneOnAttempt(...)`
- `frameBookkeepingModeForAcceptedToneOnAttempt(...)`
- `frameBookkeepingModeForToneActiveContinuation(...)`
- `frameBookkeepingModeForNoEventToneOnFrame(...)`

也就是说，
现在这几类 frame bookkeeping
不再各自重复写一遍：

- 要不要写 `rememberFrameLeaderObservability(...)`
- 要不要写 `rememberToneActivityWindow()`
- 最后都要 `rememberFrame(...)`

2. accepted tone-off consumption 尾段

- `rememberAcceptedToneOffEvent(...)`
- `applyAcceptedToneOffRunLifecycle(...)`
- `recoverFloorsAfterAcceptedToneOff(...)`

这让 accepted tone-off
开始显式分成：

- event 记账
- run lifecycle 收尾
- noise / signal floor 恢复

而不再继续把它们揉在
`consumeAcceptedToneOffEvent(...)`
一个入口里。

这一步之后，
`tone-on` 和 `tone-active release -> tone-off`
这两条主链都已经基本达到同样的结构化程度：

- resolution
- event / lifecycle consumption
- frame bookkeeping consumption

换句话说，
关于这两条主链，
已经不太剩下“再来一刀会特别值”的大块同类 seam 了。

现在更像是进入两个后续方向二选一：

- 把同样的 owner / consumer 思路继续复制到别的子链
- 或者停止结构切分，回到行为分析 / dirty case 诊断

### P3. `TimingAnchorController`

把下面三件事做实：

- learner freeze
- trusted update admission
- soft re-anchor

#### 当前已落地状态

截至 `2026-05-13`，这一层已经不再只是设计草图，而是已接入当前主实验路径：

- `OperateActivity`
  - stable decode 是否允许更新 anchor，已同时经过：
    - `LiveRxWpmGuard`
    - `TimingAnchorController`
  - timing event 在进入 decoder 前，已允许按 `trustedDot` 做局部 re-anchor
  - turn start 已调用 `TimingAnchorController.beginNewTurn(...)`

- `LocalAudioDecodeTestSupport`
  - live-like replay 链路已与 Operate 保持同构：
    - stable decode anchor admission
    - timing learning freeze / recovery
    - timing event re-anchor
    - turn-local reset

这意味着当前 bench 看到的：

- `recording16`
- `capture.wav`
- repeated-session probe

已经可以开始真实反映这层负反馈设计，而不再是“真机链路接了、测试重放没接”的错位状态。

#### 2026-05-19 复核后应收紧的假定

`P3` 当前最需要批判地修正的一点，不是“它没价值”，
而是：

- 不能再默认它会继续是主线质量提升的主要来源

现在更准确的结论应是：

- `TimingAnchorController`
  - 作为基础设施层
    - 是成立的
  - `freeze / trusted admission / soft re-anchor`
    - 这些契约仍然应该保留
- 但它能发挥作用的前提是：
  - front-end 已经给出了足够健康的 tone/gap 事件

当前多轮结果已经说明：

- `recording(10)`
  - trust 建立本身不是主问题
  - trust 后切 `AUTO_TRACK`
    甚至会变差
- `recording(12)`
  - 当前也不是再调 trusted re-anchor
    就能把 unknown cluster 修好
  - 更上游 segmentation 已经先整体漂坏

因此 `P3` 现在应改写为：

- 保持现有实现与契约
- 继续作为主链必要的负反馈基础层
- 但不再作为当前 round 的一号主攻点
- 只有在“segmentation 已基本健康、但 trust / re-anchor 仍然失稳”的 case 上
  才值得继续深挖 `P3`

### P4. `RxEpochController`

只作为最后一道保险层接入：

- 状态驱动
- RX-only
- 不清空已解文本

这一层在本轮被进一步明确：

- 这里的 `epoch` 不再按抽象时间窗理解
- 而是明确等于一次连续来波 / 一轮发送 / 一个 `turn`

也就是说，RX 之后维护的不是：

- 一个 global learner

而是：

- 一串按 RAW 边界切开的 local turn learners

它的职责不是“定时 reset”，而是：

- 在 turn 开始时快速 bootstrap
- 在 turn 内维持局部稳定
- 在 turn 结束时释放强学习状态
- 让下一次 turn 重新学习，而不是继续继承已经偏掉的全局记忆

#### turn reset 的实现边界

这一层已经明确为一个契约：

- `RxTurnController` 是跨 turn carry 的唯一 owner
- `CwHybridTimingModel` 在 `beginNewTurn(...)` 时必须清掉内部 `trusted / retained`
- `LiveRxWpmGuard` 在 `beginNewTurn(...)` 时必须清掉内部 `trusted / retained`
- 下一段 turn 的 seed 只能来自：
  - `RxTurnController` 的 retained turn anchor
  - 或非常弱的 TX seed
- 后续 turn 不能再把外部 `currentReferenceWpm` 直接当成 seed

原因是：

- `currentReferenceWpm` 往往来自上一段 timing/WPM 状态
- 如果它能在 turn start 重新注入 learner
- 就等于旧的 global learner 通过旁路回流
- 这会破坏“一个 turn 一个局部上下文”的设计

因此：

- `softReset()` 可以继续服务历史 probe / idle reset 实验
- 正式 live-like / Operate turn start 必须走 `beginNewTurn(...)`

### P5. 再谈主线替换

只有当前三层都站稳后，才允许考虑：

- replay/developer -> operate main path

### P6. 生产链与开发分析链的共享装配进度

截至 `2026-05-19`，这一层已经开始真正落地，而不再只是方向判断：

- 新增了 `RxCoreComponents`
  - 统一承载：
    - `CwSignalProcessor`
    - `CwHybridTimingModel`
    - `LiveRxWpmGuard`
    - `LiveRxToneEventStabilizer`
    - `CwFrontEndLearningGate`
    - `RxTurnController`
    - `TimingAnchorController`
    - `RxRawCommitGate`
    - `RxTurnTailRepairController`
    - `CwDecoder`
    - `RAW_COPY_FOCUS` 的 `CwInterpreter`
- `OperateActivity`
  - 已改为从 `RxCoreComponents` 取核心 RX 组件
  - turn/WPM seed 同步与 session reset 也已走共享生命周期
- `InputDebugActivity`
  - 也已改为从 `RxCoreComponents` 取核心 RX 组件
  - debug replay run 前的 reset / re-seed 不再各自手写一长串组件 reset

在此基础上，这一层又继续往前收了一刀：

- 新增了 `RxTimingDecodeRunner`
  - 统一承载共享的：
    - `timing event batch -> relay/adapt -> decoder.process -> decode event fan-out`
    - `flushPendingCharacter(...)` 的 decode event fan-out
- `OperateActivity`
  - `tone -> timing -> decode` 的底层 dispatch 已改为走 `RxTimingDecodeRunner`
  - 仍保留自己的：
    - bootstrap cadence / boundary note
    - raw commit gate note
    - timing event re-anchor / adapt
- `InputDebugActivity`
  - 也已改为走同一个 `RxTimingDecodeRunner`
  - 原来本地手写的双层 `for timingEvent -> for decodeEvent` 骨架已被替换

继续往上，当前又补了一层给 replay / case-analysis 用的共享 session 骨架：

- 新增了 `RxReplaySessionRunner`
  - 统一承载：
    - frame-sequence replay
    - `RxFrameSignalRunner`
    - `RxToneTimingRunner`
    - `RxTimingDecodeRunner`
    - final pending-gap / pending-character flush
- `LocalAudioDecodeTestSupport`
  - simpler `decodeFramesDetailed(...)`
  - `decodeFramesDetailedConfigured(...)`
    已开始直接走这条 shared replay session runner
- 新增了 `WavReplayFrameLoader`
  - 把主代码里的 PCM16 `WAV` 直接装成 zero-based `AudioFrame` replay frames
  - 这样 developer / latest-trace analysis 不必再卡死在 test support 里的本地 helper
- 新增了 `RxReplayAnalysisRunner`
  - 在主代码里直接基于 shared replay session 做离线 RX 分析
  - 当前先收口到：
    - frame replay
    - signal / timing / decode snapshots
    - decoded text preview
- `DeveloperToolsActivity`
  - latest trace 状态不再只显示 `WAV` 路径
  - 已开始读取 trace artifact 里的 preferred tone / `SQL`
  - 并在后台直接跑 shared replay analysis，给出 latest trace 的离线文本预览
- `InputDebugActivity`
  - 从 developer replay intent 同步 latest trace 的 preferred tone / `SQL`
  - 这样手动回放时不会再天然偏离现场采集条件

这一步的意义是：

- developer / case-analysis 已开始不再只“复用一些小 helper”
- 而是开始复用主代码层的 replay session orchestration

这说明我们现在共享的已经不再只是“组件集合”：

- 而是开始共享真正的 decode 执行骨架

同时，这一层又继续补上了两块更贴近逐帧主循环的共享逻辑：

- 新增了 `RxPendingCharacterFlushDecider`
  - 统一承载：
    - frame end timestamp 解析
    - trailing `TONE_OFF` 后的 silence gap 计算
    - pending character 是否允许 flush 的决策
- `OperateActivity`
  - `maybeFlushPendingCharacterDuringSilence(...)` 已改为走共享 decider
  - 仍保留自己的：
    - turn transition note / tail repair / effective-dot 口径
    - `MEANINGFUL_TURN_ACTIVITY` 语义
- `InputDebugActivity`
  - 同样已改为走共享 decider
  - 保持自己的：
    - `toneActive` 语义
    - simpler flush gap 口径

这说明当前共享边界又往上抬了一层：

- 不只是 decode runner
- 而是已经开始共享 frame-level trailing flush 决策

同时，bench / case-analysis 这条线也不再只停留在“外围同构”：

- `LocalAudioDecodeTestSupport`
  - forced replay helper 的：
    - `drainToneEvents(...)`
    - `drainTimingEvents(...)`
    已改为走共享 `RxToneTimingRunner` / `RxTimingDecodeRunner`
  - live-like 主路径中的：
    - frame prelude
    - admitted `tone -> timing -> decode`
    - `flushPendingDecode(...)`
    也已改为复用同一套 shared runner 骨架
  - 其中 live-like 的 frame prelude 已开始走共享 `RxFrameSignalRunner`
    - `AudioInputHealthTracker.process(frame)`
    - `CwSignalProcessor.process(frame)`
    - frame end timestamp 解析
    不再在 bench 路径里各自手写
  - simpler offline/configured decode path 的 frame prelude 也已接入同一个 `RxFrameSignalRunner`
    - bench 基线链与 live/developer 链在 frame 入口的执行骨架继续收敛
  - `RxTrailingWindowRepair`
    - trailing word fresh re-decode 也已改为走共享
      `RxToneTimingRunner` / `RxTimingDecodeRunner`
    - 尾部 repair helper 不再单独维护另一份 decode 骨架
  - `RxRawCommitGate`
    - trusted replay recovery 的 decode pump 也已改为复用共享
      `RxTimingDecodeRunner`
    - 仍保留自己的：
      replay window 选择
      forced-dot 重分类
      leading word-break 合成 / 抑制
  - bench 仍保留自己的：
    - timing learning admission
    - bootstrap cadence / boundary note
    - raw commit gate note
    - stable decode filtering / interpreter / QSO side效应

这意味着当前已经开始形成真正的三边收口：

- `OperateActivity`
- `InputDebugActivity`
- `LocalAudioDecodeTestSupport`

都在逐步消费同一组 RX 执行骨架，而不再各自手写一份 `for tone -> for timing -> for decode`。

继续往上，当前又新补了一层共享 frame prelude：

- 新增了 `RxFrameSignalRunner`
  - 统一承载：
    - `AudioInputHealthTracker.process(frame)`
    - frame 前的 `CwSignalSnapshot`
    - `CwSignalProcessor.process(frame)`
    - frame 后的 `CwSignalSnapshot`
    - frame end timestamp 解析
- `OperateActivity`
  - `onAudioFrame(...)` 的 signal prelude 已改为走 `RxFrameSignalRunner`
  - 仍保留外层自己的：
    - live trace
    - tone mode sync
    - turn transition / tail repair
    - spectrum publish
- `InputDebugActivity`
  - 也已改为走同一个 `RxFrameSignalRunner`
  - 仍保留自己的：
    - level counters
    - spectrum panel
    - experimental front-end replay
    - UI refresh

这说明当前共享的不再只是局部 helper：

- 已经开始覆盖 `onAudioFrame(...)` 前半段的主循环骨架

在这之后，这条收口线又继续往 decode 末端推进了一步：

- 新增了 `RxStableDecodeClassifier`
  - 统一承载共享的：
    - stable decode 的最小准入判断
    - front-end learning gate / trusted anchor / shape guard
    - `unknown-after-trust` 这类提交前过滤语义
- 新增了 `RxCommittedDecodeController`
  - 统一承载共享的：
    - stable decode 命中后的 timing notify
    - timing anchor / turn anchor 的更新
    - raw commit gate admission
    - committed character 的 `LiveRxWpmGuard` note
- `OperateActivity`
  - decode event 进入 RAW 解释器前
    已开始先经过同一个
    `RxStableDecodeClassifier + RxCommittedDecodeController`
- `RxReplayAnalysisRunner`
  - latest-trace 离线分析
    也已改为走同一套 committed RAW admission discipline
  - 这意味着 developer tools 里看到的 latest-trace preview
    已不再只是“能跑 shared replay”
    而是开始更接近生产链真实会 commit 进 RAW 的那条后段纪律

这一步的意义不是“开发者工具已经完全等于生产链”，而是：

- 两边终于开始共享同一组核心 RX owner
- `seed / reset / gate / interpreter` 这层生命周期不再继续漂移
- 现在连 decode 末端的
  - stable filter
  - committed RAW admission
  也开始共享
- 因此 latest-trace analysis 与 Operate
  - 至少在“哪些字符应真正进入 committed RAW”
  - 这件事上
  - 已经不再各说各话

但同时也要明确：

- 这还不是最终收口
- `InputDebugActivity` 目前仍保有自己的 frame processing / experimental front-end 路径
- `DeveloperToolsActivity` 虽然已经能直接给 latest trace 做 shared replay preview
  - 虽然后段 committed RAW discipline 已更接近生产链
  - 但还不是直接复用 Operate 已跑过的完整 RX session timeline
  - 也还不是 full live-like / turn-aware replay reconstruction

在这之后，这条收口线又继续把 turn lifecycle 本身往共享主代码里推了一步：

- 新增了 `RxTurnSessionCoordinator`
  - 统一承载共享的：
    - `RxTurnController.observe(...)` 后的 turn start/end fan-out
    - `beginNewTurn(...)` 对：
      - `CwHybridTimingModel`
      - `LiveRxWpmGuard`
      - `TimingAnchorController`
      - `RxRawCommitGate`
      - `RxTurnTailRepairController`
      的同步重置
    - turn end 时的：
      - front-end reset 判定
      - gate / tail controller 的 end-turn 收尾
      - 以及 end-turn 前的可选 hook
- `OperateActivity`
  - `maybeHandleOperateTurnTransition(...)`
    已改为走同一个 `RxTurnSessionCoordinator`
  - 仍保留自己的：
    - turn-end 前 tail repair 应用
    - tone mode / preferred tone / `SQL` 的 UI/owner side effect
    - trace marker
- `LocalAudioDecodeTestSupport`
  - 原来本地手写的 turn start/end fan-out
    也已改为走同一个 `RxTurnSessionCoordinator`
  - 因此 bench/live-like 链在：
    - turn observe
    - learner begin/end
    - raw gate turn boundary
    这几件事上又进一步收敛

在这之后，这条收口线又继续把 committed output side effect 往共享主代码里推了一步：

- 新增了 `RxCommittedOutputController`
  - 统一承载共享的：
    - committed decode event 对 `RAW_COPY_FOCUS` interpreter 的驱动
    - `RxUnknownFallbackTracker` 的同步推进
    - 可选 semantic interpreter / `QsoStateMachine` 的 side effect replay
    - rebuild 完成后的 `RxRawCommitGate.replaceCommittedOutputText(...)` 同步
- `OperateActivity`
  - 正常 live committed decode 流
    已开始走同一个 `RxCommittedOutputController`
  - turn-end tail repair 后的
    `sessionDecodeEvents -> output rebuild`
    也已改为走同一个 controller
- `RxReplayAnalysisRunner`
  - latest-trace 离线 replay analysis
    也已开始复用同一套 committed output side effect owner
  - 这意味着 developer trace preview
    不只是共享 replay runners / committed gate
    连 committed RAW output 的最终推进语义
    也开始与 Operate 收敛

在这之后，这条收口线又继续把 turn-aware tail repair finalization
往共享主代码里推了一步：

- 新增了 `RxTurnSessionFinalizer`
  - 统一承载共享的：
    - admitted committed decode event
      对 turn-tail tracker 与 committed output side effect 的双路推进
    - tone event 对 tail repair tracker 的持续喂入
    - `finalizeCurrentTurn(...)`
      对：
      - `RxTurnTailRepairController.maybeRepairCurrentTurn(...)`
      - repair 后的 committed output rebuild
      的统一编排
- `RxTurnSessionCoordinator`
  - 不再只负责 turn start/end fan-out
  - turn end 时也会触发共享的 turn finalization
  - `Observation`
    现在会显式带回：
    - `turnFinalization`
    - `tailRepairApplied`
    供 caller 做 trace / UI side effect
- `OperateActivity`
  - admitted committed decode flow
    已不再手动分别推进：
    - `RxTurnTailRepairController`
    - `RxCommittedOutputController`
  - 而是改为走同一个 `RxTurnSessionFinalizer`
  - turn-end / stop 时的 tail repair apply + rebuild
    也已不再本地手搓
  - 只保留自己的：
    - trace marker
    - preferred tone / tone mode / `SQL` 这些 UI/owner side effect
- `CwOperateTailRepairReplayTest`
  - replay 风格的 operate-like tail repair 验证链
    也已开始复用同一个 `RxTurnSessionFinalizer`
  - 因此至少有一条 replay validation path
    已开始与生产态 Operate 共享 turn-aware finalization owner

在这之后，这条收口线又继续把 replay/developer 侧的
turn-aware session reconstruction 往共享主代码里推了一步：

- 新增了 `RxReplayTurnSessionController`
  - 统一承载共享的：
    - offline replay 里的 turn observe
    - admitted committed decode event
      对 shared finalizer 的推进
    - stop 时的 final tail repair apply
    - replay 期间 turn transition trace 的采集
    - turn window 的共享推导
- `RxReplayAnalysisRunner`
  - 不再只是：
    - replay runners
    - committed gate
    - committed output controller
    的串接
  - 现在也开始走：
    - `RxTurnSessionCoordinator`
    - `RxTurnSessionFinalizer`
    - `RxReplayTurnSessionController`
    这条 shared turn-aware reconstruction path
- `RxReplayAnalysisResult`
  - 现在也开始显式携带：
    - `turnCount`
    - `tailRepairCount`
- `DeveloperToolsActivity`
  - latest-trace summary
    已能直接展示 shared replay 下的：
    - turn 数
    - tail repair 数
  - 因此 developer 首页上的 latest-trace preview
    不再只是“共享 replay 的一段文本预览”
    而是开始带有与 Operate 同源的 turn-aware session 诊断信息

在这之后，这条收口线又继续把 shared replay 结果
推进到更深一层的 developer inspection consumer：

- `WavReplayFrameLoader`
  - 不再只支持 `File`
  - 现在也支持直接从 `InputStream` 载入 PCM16 WAV
  - 这让 `content://` 的本地 WAV 选择
    也能走 shared replay analysis
- `RxReplayAnalysisResult`
  - 不再只回传计数级 summary
  - 现在也开始显式携带：
    - `transitionTraces`
    - `turnWindows`
- `InputDebugActivity`
  - 在 `LOCAL_FILE_REPLAY`
    尤其是从 latest-trace 跳转过来的现场 trace 场景下
    已开始异步运行 shared replay analysis
  - 本地文件状态面板
    现在会直接展示：
    - shared replay turn-aware summary
    - turn window timeline
    - preview text
  - 这意味着 developer deeper-inspection screen
    已不再只依赖自己的本地 live/debug owner 视角
    而是开始直接消费 shared replay runtime 的结构化结果

这一步之后，当前真正还没收口的 turn/session gap 又进一步变得更具体了：

- latest-trace / replay analysis
  - 已经开始消费与 Operate 同构的
    turn-aware session reconstruction
  - 结果已经进入：
    - developer 首页 summary
    - `InputDebugActivity` 本地文件 inspection
    两层 consumer
  - 但 `InputDebugActivity`
    自己那条 live/debug 主处理链
    还没有开始复用 shared committed/turn-aware owner
- tail repair 本身
  - repair application / rebuild owner
    已经进入共享 main-code runtime
  - replay/developer 侧的
    turn window derivation
    也已进入共享 main-code runtime
  - 但更深层的 live inspection owner
    仍未普遍改用这套 shared turn/session owner
- `InputDebugActivity`
  - 已开始消费 shared replay inspection result
  - 但仍保留其历史实验期的并行 live/debug owner 语义

因此这一步之后，下一号位应明确收缩为：

- 继续压缩 `InputDebugActivity`
  内部历史并行 live/debug owner
  - 也就是：
    - 哪些 decode / interpreter / qso side effect
      已可以改走 shared committed output owner
    - turn-aware / committed-raw discipline
      是否可以逐步进入 debug local-file replay 主路径
    - 哪些 test helper 仍在手写 turn window derivation
      需要继续压缩到 shared main-code types
- 让 developer replay / latest-trace analysis
  尽量消费与 Operate 同构的 turn-aware session path
- 再逐步压缩 `InputDebugActivity` 与 `OperateActivity`
  内部仅为历史实验保留的并行链路

在这之后，`InputDebugActivity` 的 live/debug 主路径
也终于开始从“完全自管 side effects”
往 shared RX owner 收口了一步：

- `InputDebugActivity`
  - 现在在 live/debug 主处理链里新增接入了：
    - `RxCommittedDecodeController`
    - `RxCommittedOutputController`
    - `RxTurnSessionFinalizer`
    - `RxTurnSessionCoordinator`
  - 但这次接法是刻意“分层保留”的：
    - 原始 `cwInterpreter`
      继续保留给 debug raw diagnostics 面板
    - shared committed path
      开始负责：
      - admitted decode side effects
      - QSO draft / ADIF 视角
      - callsign candidate action source
      - stop-time tail repair finalization
  - 这意味着 developer debug 页第一次开始同时具备：
    - raw dirty stream 可见性
    - 与 Operate 更同构的 committed/QSO 语义
- turn lifecycle
  - live frame pass
    现在会在 tone routing 之前调用 shared
    `RxTurnSessionCoordinator.observe(...)`
  - explicit stop / source terminal state
    现在也会统一走 shared
    `finalizeCurrentTurn(...)`
    再结束当前 turn
  - 这让 developer live/debug 页
    不再只是“停掉后手工冲一下 pending char”
    而是开始带有与 Operate 同构的
    turn-end finalization discipline
- fixture / batch evaluation
  - 现在也改为优先基于 shared committed output snapshot
    来做 end-to-end 评价
  - 不再只盯着 raw interpreter 当时看到了什么

这一步之后，`InputDebugActivity` 与生产 RX 的距离
又缩短了一截，但还没有完全收口：

- 已收口的部分
  - shared committed decode admission
  - shared committed output / QSO side effects
  - shared turn finalization owner
  - shared turn start/end observation
- 还没收口的部分
  - debug 页自己的 experimental front-end / raw diagnostics
    仍然保留为并行观察面
  - `OperateActivity`
    里的 boundary/cadence bootstrap observation
    还没有全量进入这条 debug live path

因此下一号位也更明确了：

- 评估 `InputDebugActivity`
  live path 是否值得继续并入：
  - bootstrap-boundary observation
  - bootstrap-cadence observation
- 或者转去压缩 test-side
  还在手写的 turn-window / session reconstruction helper

在这之后，这个缺口又进一步缩了一步：

- `InputDebugActivity`
  - live/debug 主路径现在也开始正式吃进：
    - `TimingAnchorController`
    - `RxRawCommitGate`
    - `LiveRxWpmGuard`
    - `CwFrontEndLearningGate`
  - tone event -> timing event -> decode
    这条链现在开始：
    - 按 shared learning gate 判定 timing learning
    - 在 decode 前把 timing event 喂给 shared `RxRawCommitGate`
    - 再经过 shared `LiveRxWpmGuard` / `TimingAnchorController`
      的 timing adaptation
  - stable decode 判定
    也不再只是简单的 unknown 过滤
    而是改走 shared `RxStableDecodeClassifier`
  - debug 面板
    现在也开始直接暴露：
    - turn summary
    - raw gate summary
    - WPM guard summary
    - timing anchor summary

这意味着 developer debug live screen
与 Operate live path 之间的差异
进一步从“处理纪律不同”
收缩到“bootstrap observation 还没完全对齐”。

这之后，这个 bootstrap observation 的缺口也继续往共享主代码里收了一步：

- 新增了 `RxBootstrapTimingObserver`
  - 统一承载共享的：
    - bootstrap cadence observation 判定
    - bootstrap boundary observation 判定
    - 对 `CwHybridTimingModel / TimingAnchorController / RxTurnController`
      的 bootstrap note fan-out
    - `inferBootstrapBoundaryDotEstimateMs(...)`
    - `isBootstrapCadenceGap(...)`
- `OperateActivity`
  - 已不再自己维护另一份 bootstrap cadence/boundary 判定与提交逻辑
  - 当前通过轻量 wrapper 直接转调 shared observer
- `InputDebugActivity`
  - live/debug 主路径现在也会在
    `RxRawCommitGate` / `LiveRxWpmGuard` / `TimingAnchorController`
    之前，先走同一个 shared bootstrap observer
  - `flushPendingDecodeAt(...)`
    也补齐到更接近 Operate 的 snapshot/update 顺序
  - `LocalAudioDecodeTestSupport`
  - 当前仍保留 test-side 的额外 `front-end-authority`
    与本地 bootstrap observation 口径
  - 新一轮回归已确认：
    这里不能直接硬并到 shared observer
    否则会先打坏 `recording2` 与 folder baseline
  - 但当前已经先安全收口了一层：
    - bootstrap cadence / boundary 的纯 side-effect fan-out
      已改为复用 shared helper
    - `isBootstrapCadenceGap(...)`
      与 `inferBootstrapBoundaryDotEstimateMs(...)`
      也已不再各自手写第二份
    - `front-end-authority` 兼容判定
      也已被单独抽成 test-only adapter owner
      不再继续混在 `LocalAudioDecodeTestSupport` 主体里
    - bootstrap decision trace
      现在也开始显式区分：
      - compatibility decision
      - verified shared-base decision
      避免 probe 输出把两层语义混在一起
  - 因此目前真正剩下的 test-side gap
    已进一步收缩到：
    - `front-end-authority` 的判定次序
    - 以及本地 bootstrap decision reason 的兼容口径
  - 因此它现在被明确标记为：
    - 下一号位收口 gap
    - 而不是已经完成的共享落点

这之后，`stable decode` 这一层也继续按同样原则做了语义拆分：

- 新增了 test-only `LiveLikeStableDecisionCompatibilityAdapter`
  - shared verified base 直接复用
    `RxStableDecodeClassifier`
  - test harness 历史口径里
    `front-end-authority`
    先于 shared verified decision 的那层覆盖
    被单独隔离出来
- `LocalAudioDecodeTestSupport`
  - 不再在主体里内嵌另一份 stable-decode 判定流程
  - stable decision trace 也开始显式区分：
    - compatibility decision
    - verified shared-base decision
- 这使得后续 probe / regression 分析时
  可以明确知道：
  - 哪些结论来自已经验证过的 shared RX 逻辑
  - 哪些结论只是为了维持历史 live-like baseline
    而保留的 test-only compatibility overlay

当前这批全量 regression 的红线，也可以先按性质分两类看：

- 架构收口仍在做的部分
- `InputDebugActivity` 的 live/debug 主处理链
    仍有少量历史实验期的骨架差异
    ，但 turn end / tail repair 语义现在也已经显式可见
  - test harness 的兼容层仍需保留
    以便和 shared verified decision 并行观察
- 样本质量主导的部分
  - `CwLocalAudioLiveLikeRegressionTest`
    当前主要卡在 `录音 (2)` 的 turn2 opening
  - `CwLocalAudioFolderRegressionTest`
    当前红线主要集中在 `录音`、`录音 (2)`、`录音 (8)`、
    `20260427_222505`

这意味着：

- 继续抠 `Turn2 Opening`
  现在仍不是主赛道
- 但把 live/debug 主路径继续向 shared owner 收口
  仍然是值得优先完成的事

这一步的意义是：

- developer live/debug
- operate live
- replay/developer bootstrap analysis

两条主代码链已经在“什么时候允许把一个 gap 当作 bootstrap 证据”
以及“通过后要怎样同步推进 timing/anchor/turn”
这件事上继续收口；
而 live-like bench 的剩余分叉点
也因此被更明确地缩小到了
`LocalAudioDecodeTestSupport` 这层 test-only authority/observation adapter。

这两轮累计变更已通过：

- `./gradlew app:compileDebugJavaWithJavac`
- `./gradlew app:testDebugUnitTest --tests "org.bi9clt.cwcn.core.rx.RxTurnSessionFinalizerTest" --tests "org.bi9clt.cwcn.core.rx.RxTurnSessionCoordinatorTest" --tests "org.bi9clt.cwcn.core.audio.RxReplayAnalysisRunnerTest" --tests "org.bi9clt.cwcn.core.rx.RxCommittedOutputControllerTest" --tests "org.bi9clt.cwcn.core.rx.RxCommittedDecodeControllerTest"`
- `./gradlew app:testDebugUnitTest --tests "org.bi9clt.cwcn.core.rx.RxRawCommitGateTest" --tests "org.bi9clt.cwcn.core.rx.RxCommittedDecodeControllerTest" --tests "org.bi9clt.cwcn.core.rx.RxTurnSessionCoordinatorTest" --tests "org.bi9clt.cwcn.core.rx.RxTurnSessionFinalizerTest" --tests "org.bi9clt.cwcn.core.audio.RxReplayAnalysisRunnerTest"`

### 最新补充：`20260427_222505 / 20260427_224524` 的 bootstrap window + hold 组合已被单独验证

为了避免后面再把“全局缩窄 bootstrap window + 拉长 fixed hold”
误当成主线候选，
这里把这一轮 dirty-case probe 的结论单独固定下来。

- 新增 probe：
  - `CwDirtyCaseBootstrapWindowHoldProbeTest`
- 已验证组合：
  - `window = 30 / 50`
  - `fixed hold = 0ms ~ 4000ms`
  - 都是在 `preferred=700` 的 live-like `FIXED_HOLD_THEN_AUTO` 真实策略下验证

对 `20260427_222505`：

- baseline：
  - `HYBRID = 0.200`
  - `STATIC_FIXED = 0.567`
- `window=30`
  - 需要把 hold 拉到大约 `960ms ~ 2000ms`
    才能把 recall 拉到 `0.433 ~ 0.467`
  - 但仍明显低于 `STATIC_FIXED`
- `window=50`
  - 即使继续把 hold 拉长，
    也基本没有形成有效改善
- 结论：
  - 单纯延长 hybrid bootstrap fixed 阶段，
    不能把这个 case 稳定拉回 `STATIC_FIXED` 的有效信息量
  - 这个 case 仍更像：
    - `manual fixed / observability-first`
    - 而不是“再调一轮默认 bootstrap hold”就能收口

对 `20260427_224524`：

- baseline：
  - `HYBRID = 0.364`
  - `STATIC_FIXED = 0.318`
- `window=30`
  - 长 hold 确实能持续改善，
    最好大约到 `0.636`
  - 但仍明显低于：
    - startup preferred learn `740Hz` 约 `0.864`
    - manual preferred `740/760Hz` 约 `0.864 ~ 0.909`
- `window=50`
  - 即使 hold 很长，
    也几乎不产生改善
- 结论：
  - 这个 case 的关键不只是 fixed hold 不够长
  - 更像是：
    - 起始 preferred prior 错了
    - 后续 continuity / fallback 又把错误 prior 延续太久

因此这一轮结论应明确写成：

- 不把“全局缩窄默认 bootstrap window + 拉长 fixed hold”
  升格为 RX 主线修复
- `20260427_224524`
  更像值得保留为：
  - `user-assisted preferred/fixed tone`
  - 或更严格约束下的 startup preferred-tone learn
- `20260427_222505`
  则反过来提醒我们：
  - naive startup auto-learn 很可能学错方向
  - 因此它不适合作为“自动 startup 改频”主线设计的正例

### 最新补充：受限版 startup preferred learn 值得保留为“人工辅助 / 保守自动建议”方向

在上面的结论基础上，
又补做了一轮更克制的 startup learn probe：

- 新增 probe：
  - `CwDirtyCaseConstrainedStartupPreferredLearnProbeTest`
- 它验证的不是“本 turn 内实时自动改频”
  - 而是一个更保守的策略假设：
  - 只接受 startup window 内
    - 明显高于 base preferred 的 upper-side 候选
    - 频簇很窄
    - support 不太少
    - total weight 也足够高

当前 probe 条件：

- `window=2500ms`
- `support >= 3`
- `cluster span <= 30Hz`
- `total weight >= 500`
- 只接受 `preferred +20Hz ~ +80Hz` 的 upper-side 候选

结果非常清楚：

- `20260427_222505`
  - naive learn 仍会学到 `670Hz`
  - constrained policy 会明确拒绝
  - 因此至少不会把它往错误方向自动拉走
- `20260427_224524`
  - naive learn 会学到 `740Hz`
  - constrained policy 会接受
  - 接受后：
    - `CONSTRAINED_HYBRID = 0.864`
    - `CONSTRAINED_STATIC_FIXED = 0.864`

因此这轮实验支持的不是：

- “默认 live-auto 主链现在就该自动改成 startup retune”

而是更具体的两点：

- `20260427_224524`
  这种 case 的确存在一个可解释、可约束、可复现的
  upper-side startup candidate
- `20260427_222505`
  则再次说明：
  - startup learn 必须有方向性约束
  - 否则 very-early far-wide dominance 很容易学错

所以当前更合适的产品/策略落点应是：

- 把它保留成：
  - `manual fixed / preferred tone` 的增强建议
  - 或者“保守自动建议值”
- 而不是直接并入默认 RX 主链，
  让系统在当前版本里自行 mid-turn 改频

这里再补一条非常重要的边界确认：

- 后面又尝试过一次
  把这套 constrained-startup suggestion
  直接挂到 shared `RxReplayAnalysisRunner / RxReplayAnalysisResult`
  上，想让 developer analysis 直接复用
- 但当前 shared replay 口径下，
  `20260427_222505 / 20260427_224524`
  并没有稳定复现 probe 里那组 startup far-wide dominance
  证据；
  甚至 `录音 (14)` 这类样本还出现了与 live-like probe
  不一致的 dominant cluster
- 因此这一层现在必须继续明确区分：
  - `test/probe compatibility finding`
  - `shared replay verified result`

当前结论应写死为：

- constrained startup preferred suggestion
  目前只适合作为：
  - probe / developer research 线索
  - 人工 fixed-tone 判断辅助
- 现在还不适合直接并入 shared replay analysis result
  作为正式稳定字段
- 否则会把：
  - 还未验证收口的 test-side compatibility 发现
  - 与已经验证过的 shared replay owner
  混成一层语义

## 明确不再做的事

- 不再继续主攻 `display WPM clamp / hold / seed carry-over`
- 不再把 `fixed interval reset` 当成正式方案
- 不再靠更多后置 gate 试图挽救坏事件
- 不再优先折腾 startup warmup / half-frame compensation / bridge 微调
- 不再把旧 case 的错误行为继续当成 strict truth
- 在单路径 commit discipline 站稳前，不再把 `multi-path / multi-hypothesis` 当成当前主线

## 一句话结论

当前 RX 的主病灶不是“还差一个参数”，而是：

- `pre-trust` 阶段缺少 RAW 提交纪律
- 前端分段缺少负反馈
- learner 缺少冻结与回拉
- 系统缺少状态驱动的 soft re-bootstrap

因此正式方案不是：

- `WPM clamp`
- `fixed reset`
- `TX seed hard anchor`

而是：

- `pre-trust RAW commit gate`
- `前端边界压力积分`
- `时基学习冻结与可信锚点回拉`
- `状态驱动的 RX-only epoch soft re-bootstrap`
