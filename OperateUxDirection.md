# Operate UX Direction

Last updated: 2026-05-20

## 背景

当前这一轮 `RX -> RAW` 主线已经基本收口。

接下来 `Operate` 页的讨论应从“继续混入 QSO 状态机/语义推断”切回到：

- 真实收发现场的信息展示
- turn 级对话可读性
- 人工主导的日志入口

## 当前问题

基于当前版本的实际使用反馈，`Operate` 页的主要问题是：

- RX 显示仍更像“最新一条快照”，不是按 turn 追加的对话流
- TX 与 RX 都塞在同一显示区里，但当前展示语义不清晰
- TX 文本有独立区域，RX 文本也有独立区域，视觉上偏碎
- 没有显式的“清空当前显示区”能力
- 主显示区没有明确保证“内容增多后自动滚到最新”
- 时间目前不是绝对时间，不利于现场回看
- QSO 状态机 / draft 区块仍占据主界面，但当前产品方向并不想继续以它为核心

## 目标形态

`Operate` 主显示区应改为：

- 单一对话流水区
- 按时间顺序显示 RX / TX turn
- 始终以“最近内容在底部”为主

理想示意：

```text
[2026-05-20 12:00:00]-RX
CQ CQ CQ DE BI9XXX PSE K.

[2026-05-20 12:00:01]-TX
BI9XXX DE JA1ABC UR 599 5NN

[2026-05-20 12:00:02]-TX
QTH XIAN RIG YAESU FT710 ANT GP5.6M

[2026-05-20 12:00:02]-RX
R R JA1ABC DE BI9XXX UR 5NN QSO 73.
```

## 已确认的产品方向

### 1. 主显示区以 transcript 为核心

后续 `Operate` 不再把主显示区当成：

- “最新 RX 快照 + 最新 TX 快照”

而应明确改成：

- transcript / conversation timeline

具体约定：

- RX 按 turn 追加
- TX 在开始发送时追加一条
- TX 发送中只更新这条的进度，不重复追加同一内容
- 内容过多时自动向上滚动，默认保持最新内容可见
- 需要提供手动 `Clear` 动作，清空当前 transcript

### 2. 时间显示使用绝对时间

主显示区不再优先显示“几秒前 / 几分钟前”。

后续应支持：

- `UTC`
- `Local Time`

如果后面确认有必要，再扩展：

- 可选时区

第一阶段不强求复杂时区编辑，但必须先支持 `UTC / Local` 切换。

### 3. TX 进度应体现在 transcript 中

TX 在 transcript 中应可见：

- 已发送部分
- 当前发送位置
- 未发送部分

可接受的实现方式包括：

- 当前字符高亮
- 已发送字符变色
- 或逐字显现

不要求第一阶段做复杂动画，但要让操作者看得出：

- 当前正在发哪一段

### 4. TX 期间不能让 RX 污染 transcript

这是必须正面处理的问题。

当前结论是：

- TX 期间不能继续让 RX 向 transcript 正常追加内容

第一阶段推荐策略：

- 手机麦克风 RX 路线
  - TX 时暂停 RX capture
- 外部音频 / USB RX 路线
  - 可继续保留底层 capture
  - 但 TX 期间不要向 transcript commit RX 内容

核心原则是：

- transcript 里不要混入明显的自发射污染

### 5. QSO 状态机不再作为 Operate 主界面核心

当前方向下，CW 对话更自由，不应再把：

- QSO state machine
- draft phase
- 自动完成 QSO 的判断

放在 `Operate` 主界面的中心位置。

因此后续 `Operate` 主界面应进一步简化：

- 移除或弱化大块 draft / phase UI
- 不再让主显示区围绕 QSO 状态机构建

这不等于：

- 日志功能不重要

而是说：

- 是否完成 QSO 主要靠人工判断
- 主界面应优先服务“看懂当前收发内容”

### 6. 保留 lightweight callsign hint，但不再绑死状态机

呼号推断仍然有价值，但定位应改成：

- 基于当前 `RX -> RAW` 成果的轻量候选提示

而不是：

- 驱动主界面 phase / draft 流程的中心状态

后续期望行为：

- 在 `Operate` transcript 或其附近高亮显示 callsign candidate
- 允许用户点选其中一个候选
- 长按或明确动作进入 QSO 日志编辑入口
- 自动带入：
  - 当前 turn 时间，使用 `UTC`
  - 目标呼号 candidate
- 允许人工二次修改后保存

第一阶段不强求自动带入：

- `RST`
- `QTH`
- `NAME`

这些信息如果后面容易接，再补。

### 7. 呼号候选必须收紧

为了避免 UI 上出现大量伪候选，后续 callsign hint 需要遵守更严格的上限。

当前约定：

- 呼号长度上限按 `8` 位左右处理
- 超过 `8` 位的 token 不再作为主 callsign candidate

同时建议保留的基础约束包括：

- 只接受 `A-Z0-9?`
- 必须含数字
- 含 `?` 的候选降权
- 重复出现的 clean candidate 优先

原则是：

- 宁可漏报
- 不要在主界面高频误报

## 当前实现与目标之间的差异

当前实现还停留在：

- `RxSessionSnapshot.rawText()` 的单快照展示
- transcript 视图容器已经存在
- 但 entry 生成仍不是 turn-aware conversation model

所以接下来不应该继续在现有 draft/QSO 结构上修补，而应直接转向：

- transcript-first 的展示模型

## 推荐实施顺序

### 第一阶段

- 把主显示区改成真正的 RX/TX transcript
- RX 按 turn 追加
- TX 按一次发送生成一条并显示进度
- 自动滚动到最新
- 增加 `Clear transcript`
- 支持 `UTC / Local` 时间显示

### 第二阶段

- 落实 TX 期间的 RX 抑制策略
- 避免 transcript 被自发射污染

### 第三阶段

- 增加 lightweight callsign hint
- 支持点击/长按进入日志编辑
- 自动带入 `UTC turn time + callsign`

## 非目标

当前这条 `Operate` 线暂不追求：

- 在主界面自动判断 QSO 是否完成
- 在主界面维持复杂 QSO phase 状态机
- 在主界面强推 `RST/QTH/NAME` 等自动结构化填充
- 让 semantic / callsign / QSO 功能反向污染 `RX -> RAW` 主链

## 结论

从当前阶段开始：

- `RX -> RAW` 主线可视为已足够干净，可以切回 `Operate`
- `Operate` 的下一步核心不是继续堆语义状态机
- 而是先把 transcript、turn、TX/RX 污染隔离、以及人工日志入口这几件事做对
