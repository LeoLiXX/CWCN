# RX Architecture Reset Plan

Last updated: 2026-05-10

## 背景

最近这一轮真机反馈已经足够明确，当前 RX 问题不能再被当成单一的 `WPM` 调参问题处理。

现场现象包括：

- 第一遍播放前半段尚可，后半段开始明显走坏
- 第二遍、第三遍即使等待显示值回落，结果仍显著恶化
- 播放音量变大时，`WPM` 更容易被推高到 `30+ / 40`
- `tone` 仍大致在目标频点附近，但 decode 已经明显崩坏
- 手机 `Mic` 场景比离线回放更容易暴露问题

这说明：

- `WPM runaway` 更像下游症状
- 真正的主要矛盾更可能在前端分段、时基污染、以及 live bench 与 offline bench 的错位

## 当前 RX 链路的结构性问题

### 1. 现有 live 路径与 offline UT 路径并不等价

当前真实运行路径大致是：

- `MicrophoneRxAudioSource / UsbExternalRxAudioSource`
- `AudioInputHealthTracker`
- `CwSignalProcessor`
- `LiveRxToneEventStabilizer`
- `CwHybridTimingModel`
- `LiveRxWpmGuard`
- `CwDecoder`
- `CwInterpreter`

但大量离线回放 / 本地音频测试走的是：

- `CwSignalProcessor`
- `CwHybridTimingModel`
- `CwDecoder`

这意味着：

- 离线测试没有复现 live path 的 `AudioInputHealthTracker`
- 没有复现 `LiveRxToneEventStabilizer`
- 没有复现 `LiveRxWpmGuard`
- 没有复现 `OperateActivity` 的 idle reset 行为

后果：

- 测试通过，并不等于真机行为可信
- 离线结果与真机结果之间可能存在系统性错觉

## 2. 前端时间分辨率天然偏粗

当前麦克风路径使用：

- `16 kHz`
- `256 sample / frame`

这意味着单帧时间约为：

- `16 ms`

而 `24 WPM` 左右时一个 `dot` 大约只有：

- `50 ms`

也就是说一个 `dot` 只有约 `3` 帧。

这会带来几个天然风险：

- 手机 `Mic` 的包络抖动很容易跨帧切坏音头音尾
- 一次轻微共振或近讲过热，就足以把一个正常 tone 切成多个片段
- 后面再怎么做 `WPM hold`，都已经是在处理碎片化后的结果

## 3. 当前前端把“音量记忆”耦合进了 keying 判决

当前 `CwSignalProcessor` 的 attack/release threshold 依赖：

- `noiseFloorEstimate`
- `signalFloorEstimate`

而 `signalFloorEstimate` 会被前一段较强 tone 抬高。

如果它回落不够快，就会出现：

- 前半段声音较大
- 后半段同样节奏但略弱的 tone 只有中间最强的一截能过门限
- tone 边缘被削短
- decoder 看到的是“更短的点、更短的间隔”
- 最后表现为 `WPM` 一路走高

一句话总结：

- 现在这条链路把“幅度变化”错误地转译成了“速度变化”

## 4. 当前 timing/decoder 是单假设、低置信度盲走

当前 timing 与 decoder 主要假设：

- 前端送来的 `tone on/off` 已基本可信
- 每个 `tone/gap` 可以立即被分类成：
  - `DIT / DAH`
  - `INTRA / LETTER / WORD`
- decoder 只维护一条主路径

这在理想录音下可以工作，但在真实 `Mic` 场景下有两个问题：

- 一旦前端分裂，时基估计会被污染，并反过来继续污染后续分类
- decoder 没有把“这一段其实不太确定”显式表达出来，而是直接产出硬分类结果

这使得错误会自增强，而不是被局部隔离。

## 5. 一部分测试断言已经偏离真实目标

目前至少有一类问题已经非常明确：

- 某些 `weak valley` 分裂测试，把“分裂成两段”当成当前正确行为

如果这些 case 继续作为主线红线：

- 工程会被迫保护错误行为
- 调整前端时会不断被旧断言拉回去

因此必须把现有 case 分成不同层级：

- `truth`：确实代表真实目标
- `observability`：只是观察当前行为
- `stress`：用于探边界，不代表应通过
- `invalid assumption`：需要降级或重写

## 6. 手机 Mic 问题不是“附属问题”，而是主设计约束

过去更容易只盯着：

- 本地 WAV
- 单次回放
- 相对理想的输入

但这次真机反馈说明：

- 手机麦克风输入并不是边缘情况
- 它暴露出的噪声、共振、AGC 痕迹、音量依赖，恰好是当前 RX 设计最脆弱的地方

因此：

- 不能把 `Mic` 问题当成“以后再适配”
- 它必须反向约束主线 RX 设计

## 7. 当前链路缺少“CW 有效性判决”前置门

最近真机复测给出了一个非常强的信号：

- 轻微咳嗽就能把 `WPM` 推到 `40+`

这说明当前链路里，至少在 live 主线上：

- 非 CW 瞬态噪声
- 近讲共振
- 短促爆发音

依然有机会被解释成合法的 `tone event`
并继续进入：

- `timing`
- `decoder`
- `interpreter`

这不是单纯的参数宽松，而是结构上缺少一道前置问题：

- 这一段输入，是否足够像“可解码 CW”

当前已有的：

- `AudioInputHealthTracker`
- `toneDominanceRatio`
- `narrowbandIsolationRatio`
- `recentLockedFrameRatio`

更多是在做“质量观测”或“后置抑制”，还没有形成一个真正的
`CW-validity gate`

即：

- 只有当一个 tone run 在频率连续性、持续时间、包络形态上都足够像 CW，
  才允许它进入 timing/decoder

这意味着下一阶段必须明确区分两层：

1. `tone detected`

- 只是检测到某个窄带能量峰或短暂 tone-like 事件

2. `cw candidate accepted`

- 满足最小持续时间
- 满足频率连续性
- 满足前后 gap/tone 的基本结构约束
- 排除明显的 burst/noise/transient

如果这层不建立：

- 后面所有 `WPM hold`
- `trusted timing`
- `decoder recovery`

都会继续在噪声已经越权进入后的残局里打补丁

## 8. SQL 在下一版里应升级为主设计部件，而不是附属调参

这次复盘后的判断是：

- `SQL` 目前有用
- 但它现在只是前端 attack/release threshold 的倍率旋钮
- 它还没有成为真正的 `CW-validity gate` 组成部分

## 2026-05-11 结构性修正结论

这次再次回到同一个问题后，可以明确记录两点：

1. 过去几轮把重点放在 `display WPM hold / clamp`

- 这只能压住显示层或解码层表象
- 不能阻止底层 `timing learner` 继续被错误事件喂坏
- 因此真机上仍会表现为：
  - 第一遍后半段开始坏
  - 第二遍第三遍继续坏

2. 当前真正的结构问题是：

- 低置信度事件同时承担了两种职责：
  - 作为 decoder 输入
  - 作为 timing learner 的学习样本

一旦前端把 tone/gap 切碎：

- decoder 先被带偏
- learner 继续把这些碎片当成合法加速证据
- 然后新的 dot estimate 又反过来污染下一轮分类

这形成了一个自增强反馈环。

### 当前已确认的正确方向

接下来 RX 主线必须遵守下面这个结构原则：

- `decode path` 与 `learning path` 显式分离
- 低置信度事件可以被观察、可以尝试解码
- 但不应自动拥有改写 timing model 的权力

一句话总结：

- `allow decode` 不等于 `allow learn`

### 当前已开始落地的机制

本轮已经开始把以下结构接入：

- timing model 增加 `allowLearning` 分支
- live RX path 在进入 trusted / runaway 区间后，可冻结 learner 更新
- 后续所有 `stable / hold / learning` 判据，应优先基于 `raw timing snapshot`
  而不是基于稳定化后的 `snapshot()`

### 下一步不应再做的事

- 不要继续靠更多 `WPM clamp` 参数叠补丁
- 不要继续把“旧 case 里 fast fragment 仍会推高 WPM”当正确行为保护
- 不要再让 APK 真机试错充当主要分析手段

### 下一步真正要做的事

1. 统一 live path 与 live-like test harness 的 `raw timing` 判据
2. 明确 bootstrap 阶段与 trusted 阶段的 learner 权限边界
3. 补一个直接针对 `录音 (16)` 尾段 runaway 的回归用例
4. 继续推进 `CW-validity gate`，把非 CW 瞬态和碎片事件挡在 learner 之前

结合真机反馈，下一版应明确提升 `SQL` 的地位。

推荐原则：

1. `SQL` 负责“是否允许疑似信号进入 CW 判决”

- 背景噪声不过线时，不进入 tone candidate
- 短促瞬态即使过线，也不自动视为 CW

2. `SQL` 不直接决定 `WPM`

- `SQL` 只应影响“进门资格”
- 不能让门限副作用继续变成时基污染来源

3. `SQL` 应与窄带 tone 证据绑定，而不是只看总体音量

- 总体 frame level
- 目标频点 tone level
- tone 对 noise floor 的超额
- tone 连续性

都应一起参与

4. `SQL` 需要 hysteresis

- 起跳门限
- 保持门限
- 释放门限

这三者应明确区分，避免一过线就频繁抖动

5. `SQL` 需要更可解释的 UI

- 不只是百分比
- 还应能看到：
  - 当前噪声底
  - 当前 tone level
  - 当前 SQL 线
  - 是否越线

也就是说：

- `SQL` 在下一版中应成为“噪声拒绝与信号准入”的核心参数
- 但它不能替代：
  - tone 连续性判断
  - 瞬态噪声抑制
  - timing 置信门控

## 下一阶段的总原则

### 原则 1

不要再优先修 `WPM display` 或 `WPM hold` 症状。

只有在前端事件源已经足够可信时，`WPM hold` 才是增益项。

### 原则 2

先建立“更接近 live 的 bench truth”，再继续大范围调算法。

### 原则 3

不再让明显错误的历史断言继续充当主线成功标准。

### 原则 4

调试/开发观测能力应保留，但尽量留在 developer/debug 路径，不污染正常 App 交互。

## 建议的重构顺序

### P0. 先把 bench truth 校正

优先做：

1. 建立 live-equivalent replay harness

- 在离线回放里接入：
  - `AudioInputHealthTracker`
  - `LiveRxToneEventStabilizer`
  - `LiveRxWpmGuard`
  - idle reset 行为

2. 增加 repeated-play bench

- 同一段录音：
  - 连续播放 3 次
  - 中间只给短静默
  - 观察首遍尾部、第二遍、第三遍的差异

3. 增加 level-sweep bench

- 同一段录音：
  - 低音量
  - 正常音量
  - 高音量

4. 把现有 case 重新分级

- `strict`
- `soft`
- `observability`
- `stress`
- `invalid assumption`

### P1. 前端分段机制重做

目标不是小修 threshold，而是重新明确：

- 用什么量来判断 tone active
- 用什么量来判断 release
- 什么情况下把短 valley 视为同一 tone 的内部下陷

推荐方向：

1. 继续保留窄带 tone tracker

- 用于跟踪目标频点

2. 但 keying 判决改为更接近“包络检测”

- 以目标频点窄带能量包络为主
- 采用更细粒度的 sub-frame/bin 观测
- 不要只依赖 256-sample frame 级别的单次 attack/release 判决

3. 明确区分三类状态

- `tone on`
- `weak valley but same tone`
- `true tone off`

4. 降低“历史强音量抬高后续门限”的耦合

- 让 signal-floor memory 更快回落
- 或把它从主 attack/release 判决中降权

5. 在 tone event 进入 timing 前增加 `CW-validity gate`

推荐至少考虑：

- 最小 tone-run 持续时间
- 最小频率连续性
- 包络平滑度 / 非瞬态判别
- transient burst rejection
- 对单次短促异常事件的整段丢弃，而不是交给 decoder 产出 `E/T`
- SQL 越线只作为必要条件之一，不作为充分条件

### P2. timing 改成置信驱动，而不是无条件自学习

当前建议继续沿着这句原则走：

- 高置信时更新 WPM
- 低置信时冻结 WPM
- 长静默后再回退 WPM

但要注意：

- 这不应再只是 `display WPM` 层的策略
- 而应成为 timing update 本身的输入门控

需要明确：

- 什么叫高置信前端
- 什么叫高置信 timing window
- 哪些 gap/tone 只能用于 decode，不能用于更新基准

### P3. decoder/解释层承认不确定性

后续可以考虑：

- 让 decoder 对边界模糊的 segment 保留更多中间态
- 区分：
  - 可见 RAW
  - 较保守 normalize
  - 语义推断

避免把不确定的结构过早“硬解释”为确定字符。

## 立即不该做的事

以下方向在当前阶段应降低优先级：

1. 继续只靠 `trustedWpm / hold` 修首遍尾部与重复播放问题
2. 把更多历史错误行为写进 strict case
3. 仅凭单次离线 WAV 回放结果宣布 RX 已改善
4. 把真机 `Mic` 问题视为次要输入路径

## 当前最重要的判断

这次问题不应再被描述成：

- “某个参数还没调到位”

更准确的描述应该是：

- 当前 RX 主线仍偏向“理想单次音频 + 单峰分段 + 事后 WPM 修正”的设计
- 但真实手机 `Mic` 与连续播放场景已经证明，这套假设本身不够稳

因此下一步不是继续补局部症状，而是：

- 先修 bench truth
- 再修 front-end segmentation
- 然后才轮到 timing 与 decoder 的进一步收敛
