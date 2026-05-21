# Logbook Rebuild Plan

## Background

当前 `Logbook / QSO Editor` 的实现更接近“调试型后台页面”，不符合正式产品形态。

现状主要问题：

- `Logbook` 主页面被 `Filter / Sort / Selected Detail` 三段式布局占满，主体不突出。
- `QSO Editor` 混入了过多 `Draft / Review / Export` 工作流信息，不像手动录入日志的正式表单。
- 视觉风格仍延续早期 `bootstrap_panel_background` / 大圆角 / 多层框体，和希望参考的 `FT8CN` 风格相差较大。
- 数据模型还不足以支撑正式日志能力：缺少 `频率 / Grid / 备注 / 手动确认状态` 等字段。
- 导出能力目前只覆盖基础 ADIF 文件输出，缺少时间范围、确认状态筛选、局域网导出与文本分享。

因此这一轮应将 `Logbook` 视为独立产品线重构，而不是在当前页面上继续补丁式演进。

## Product Direction

### 1. Split into two pages

保留两个明确页面：

1. `Logbook List`
2. `QSO Editor`

原则：

- `Logbook List` 负责浏览、搜索、筛选、导出、分享、切换显示模式。
- `QSO Editor` 负责新建、编辑、确认单条 QSO 记录。
- `Operate` 页面进入日志时，只是“带初始值打开同一个 QSO Editor”，不再额外派生第二套编辑 UI。

### 2. Visual direction

`Logbook` 页面视觉参考 `FT8CN`，尽量直接照搬其列表风格，不主动做风格再创造。

明确约束：

- 主体为连续滚动的 `QSO card list`
- 支持 `详细 / 简约` 两种 card 模式
- card 使用较小圆角
- 减少边框感，避免“边框套边框”
- 支持交替背景色
- 顶栏按钮布局尽量接近 `FT8CN`

这条视觉方向后续也可能反向影响 `Operate` 页面，继续推动其去框化和简化。

### 3. Verified FT8CN reference

这一节只记录已经核实过的 `FT8CN` 真实实现，避免后续“想当然”。

#### Top bar real order in FT8CN

`FT8CN` 当前 `LogFragment` 顶栏真实顺序如下：

- 左 1：`export`
- 左 2：`share`
- 左 3：`map`
- 中：呼号输入框
- 右 1：`detail/simple` 切换
- 右 2：`filter`
- 右 3：`count/stat`

说明：

- 这是从 `fragment_log.xml` 的真实布局读取出来的。
- 后续 `CWCN` 不做地图和统计时，也应尽量保留这种“左侧操作按钮 + 中央搜索 + 右侧视图/筛选按钮”的节奏。

#### Detailed card real field arrangement in FT8CN

`FT8CN` 的详细 QSO card 真实排布如下：

- 左列：
  - `remote callsign`
  - `remote grid`
  - `station callsign`
  - `station grid`
  - `confirmation status`
- 中列：
  - `start time`
  - `end time`
  - `rst received`
  - `rst sent`
  - `mode`
- 右列：
  - `band`
  - `frequency`
  - `country/where`
  - `comment`

补充：

- `where` 不是数据库里直接存好的，它会按呼号异步查询国家/地区再回填。
- card 使用交替背景色，而不是统一纯色。

#### Simple/callsign-summary card real field arrangement in FT8CN

`FT8CN` 的简约 card 真实排布如下：

- 左列：
  - `callsign`
  - `confirmed / unconfirmed`
- 中列：
  - `last qso time`
  - `confirmation mode`
  - `mode`
  - `grid`
  - `DXCC / ITU / CQZONE`
- 右列：
  - `band/frequency summary`
  - `distance`
  - `country/where`

说明：

- `distance` 由本机 grid 和对方 grid 实时计算。
- `DXCC / ITU / CQZONE` 来自呼号数据库查询，不是用户手填。

#### Verified FT8CN list interactions

`FT8CN` 当前日志列表真实行为：

- 搜索框输入后立即触发查询，没有 `300ms debounce`
- 详细/简约切换按钮直接切 adapter 视图模式
- 详细列表支持：
  - 左滑/右滑动作
  - 删除确认
  - 手工确认切换
  - 长按上下文菜单
- 筛选弹窗第一版其实只有一组：
  - `全部`
  - `已确认`
  - `未确认`

这意味着：

- 我们希望做的“当天 / 当月”筛选，是在 `CWCN` 上新增，不是 FT8CN 现成功能。

#### Verified FT8CN grid auto-acquire

`FT8CN` 的本地 grid 自动获取逻辑已经存在，而且实现并不复杂：

- 设置页有一个专门的定位按钮：`configGetGridImageButton`
- 点击后调用 `MaidenheadGrid.getMyMaidenheadGrid(context)`
- 内部通过 `LocationManager` 遍历可用 provider
- 读取各 provider 的 `lastKnownLocation`
- 取精度最好的一个位置
- 然后换算成 Maidenhead grid
- 最终只回填 4 位 grid

它的边界也很明确：

- 依赖系统定位权限
- 依赖系统已经存在可用的最近位置
- 不是持续定位
- 不是高复杂度地图 SDK 方案

因此这块在 `CWCN` 中应优先按这个思路复刻，而不是另起一套更重的方案。

#### Verified FT8CN callsign country lookup

`FT8CN` 当前的呼号归属查询也已经核实：

- 主要数据源是 `assets/cty.dat`
- 国家英文到中文名映射来自 `country_en2cn.dat`
- 查找逻辑本质上是：
  - `=` 开头走精确匹配
  - 其他前缀走最长前缀匹配
- `country / where / DXCC / CQ / ITU` 都是显示期查询结果，不要求用户手填

这意味着：

- `CWCN` 的 `country / region / DXCC` 最稳妥的实现方式，就是沿着这条 `cty.dat + exact/longest-prefix` 路线复刻
- 不需要为了这件事把归属信息硬存进 QSO 表

## List Page Design

### Top Bar layout

顶栏按以下顺序组织：

- 左侧 3 个按钮：`导出`、`分享`、`新建`
- 中间：呼号搜索框
- 右侧 2 个按钮：`详/简切换`、`高级筛选`

说明：

- 不提供地图分布、统计图这类功能入口，至少第一阶段不做。
- 搜索框占据顶栏中心主视觉。
- 搜索采用 `300ms debounce`
- 搜索策略先用：
  - `contains`
  - 如后续需要再评估 `startsWith`

### Card content

列表 card 以 `对方呼号` 为主标题。

推荐显示字段：

- `remote callsign`
- `station callsign`
- `QSO time`
- `band`（由频率实时推导）
- `frequency`
- `RST sent / RST rcvd`
- `remote grid`（可空）
- `station grid`（可空）
- `comment`
- `manual confirmed status`

设计约束：

- 只记录一个 `QSO 时间`
- 不再对正式产品 UI 提供 `start / end time`
- 若来自 `Operate` 的某个 turn，可以自动带入一个推荐时间
- card 上显示 `band`
- 但 `band` 没必要入库存储，应由 `frequency` 实时映射计算
- 这个 `QSO 时间` 在产品语义上解释为：
  - `通联发生时间`
  - 第一版默认映射到 `ADIF` 的 `QSO_DATE + TIME_ON`
  - 不强制建模 `TIME_OFF / QSO_DATE_OFF`

### Item interaction

- `tap card`：进入 `QSO Editor`
- `long press card`：弹出操作菜单

长按菜单第一阶段至少包括：

- `标记已确认 / 取消确认`
- `编辑`
- `删除`

若实现顺手，列表手势按 `FT8CN` 保留：

- `右滑`：标记已确认 / 取消确认
- `左滑`：删除
- 建议只在 `详细 card` 模式启用

备注：

- 这里的“确认/未确认”是产品语义
- 不应再直接暴露旧的 `needManualReview` 语义

### Detail / Compact switch

提供与 `FT8CN` 类似的 `详细 / 简约` card 切换。

建议：

- 这是纯 UI 展示状态
- 不影响数据结构
- 状态持久化到本地偏好设置

### Advanced filter

高级筛选用弹窗，不常驻占据主页面空间。

第一阶段只支持两组条件：

1. 确认状态
   - `全部`
   - `未确认`
   - `已确认`
2. 时间范围
   - `全部`
   - `当天`
   - `当月`

使用两组 `RadioGroup` 即可。

## QSO Editor Design

### Role

`QSO Editor` 是独立正式录入页。

它承担：

- 手动新建 QSO
- 编辑已有 QSO
- 接收来自 `Operate` 的预填初始值

不再承担：

- 调试式 draft workflow 展示
- 确认队列解释面板
- 已确认日志总览面板

### Editor input model

建议第一阶段正式表单字段：

- `QSO local time`
- `remote callsign`
- `station callsign`
- `frequency`
- `RST sent`
- `RST rcvd`
- `remote grid`
- `station grid`
- `name`
- `QTH`
- `comment`
- `manual confirmed`

### Time handling

时间处理原则明确如下：

- 用户在 UI 上输入和查看的是 `Local 时间`
- 应用内部保存为 `UTC`
- 写入 `ADI` 时使用 `UTC`
- 时间转换必须由程序完成，不应让用户手工换算

第一版 `ADI` 导出结论：

- 写出 `QSO_DATE`
- 写出 `TIME_ON`
- 不强制写 `QSO_DATE_OFF`
- 不强制写 `TIME_OFF`

也就是说：

- 第一版的单个 `QSO local time`，就是“本条通联记录的发生时间”
- 在导出时把它转换成 `UTC` 后落到 `QSO_DATE + TIME_ON`

这条规则优先于现有其他页面里任何“直接展示 UTC 文本”的旧做法。

如果后续与其他页面时间设计冲突，应以“用户不用自己换算”为准重新收敛。

### Operate -> Editor prefill

从 `Operate` 页面点击 callsign 进入时，允许预填：

- `remote callsign`
- `QSO time`
- `frequency`（如可得）
- 可能的 `comment seed`

如果当时无法获得：

- `frequency`
- `station grid`
- `remote grid`

则保持为空，允许用户手填或后续自动补。

## Data Model Direction

### Confirmed log product fields

当前 `ConfirmedQsoLog` 需要补齐正式产品字段。

建议至少包含：

- `id`
- `remoteCallsign`
- `stationCallsign`
- `qsoTimeUtcEpochMs`
- `frequencyHz` 或 `frequencyKHz`
- `rstSent`
- `rstRcvd`
- `remoteGrid`
- `stationGrid`
- `name`
- `qth`
- `comment`
- `manualConfirmed`
- `mode`
- `confirmedAtEpochMs`

说明：

- `mode` 固定写 `CW`
- `band` 不入库，通过 `frequency` 实时推导

### Review flag semantics

当前库中的 `needManualReview` 更像旧工作流语义。

新产品语义中建议：

- 正式使用 `manualConfirmed`
- `needManualReview` 从主 UI 语义中退场

迁移时可考虑：

- 旧记录先映射成：`manualConfirmed = !needManualReview`

但当前阶段没有正式历史用户数据，这一轮不把“兼容旧正式数据迁移”当作阻塞约束。

## Frequency / Band

### Frequency

- 没有 CAT/USB 时，频率允许手填
- 有 CAT/USB 时，编辑页应支持读取当前电台频率

建议第一阶段行为：

- 打开编辑页时如能获取当前频率，则自动预填
- 用户仍可手动修改

### Band

- 波段由频率实时推导
- 不建议单独存储在数据库中
- card 显示时再计算
- 导出时如 ADIF 需要，也可在导出阶段实时推导

## Grid / Distance / Region

### Station grid

本地 `station grid` 当前设置页虽然已有存储字段，但“自动获取”能力仍未完成。

这是一个已知缺口，需要单独补做。

参考 `FT8CN` 已核实实现，建议后续补充：

- 在设置页提供一个显式的“定位获取 grid”按钮
- 基于 `LocationManager + lastKnownLocation` 获取位置
- 自动换算并回填 4 位 Maidenhead grid
- 允许用户手动覆盖

### Remote grid

- 对方 `grid` 为可选字段
- 若为空，不阻塞保存、导出、分享

### Distance

若双方 `grid` 都存在：

- 自动计算 `Distance`
- 可写入 `comment`

建议格式：

- `Distance: xxxx km, QSO by CWCN`

若任一方 `grid` 缺失：

- 不计算 distance
- `comment` 至少保留 `QSO by CWCN`

### Country / region

如果后续希望像 `FT8CN` 一样显示国家/地区名：

- 不能仅靠 grid 自动得出
- 需要 prefix / DXCC 数据支持

因此优先级建议：

1. `distance`
2. `country/region`

## Export

### ADIF export

导出功能应从 `QSO Editor` 移出，回到 `Logbook List` 顶栏。

第一阶段导出条件：

- 时间范围：
  - `全部`
  - `当天`
  - `当月`
- 确认状态：
  - `全部`
  - `未确认`
  - `已确认`

导出格式：

- `.adi`
- `MODE=CW`
- 时间写入 `UTC`
- 备注包含 `QSO by CWCN`
- 若可计算 `distance`，备注可包含 distance

### LAN export

支持 host 一个局域网 server 并从网页下载导出文件，是这一轮必须包含的正式能力。

要求：

- 网页导出 `.adi`
- 支持：
  - `全部 / 当天 / 当月`
  - `全部 / 未确认 / 已确认`
- 与本地导出保持同一套记录筛选口径
- 由应用负责生成和维护下载内容，不要求用户手工整理文件

## Share

支持 `txt` 分享。

行为：

- 与导出一样支持范围/确认状态筛选
- 内容结构参考导出
- `MODE` 固定为 `CW`
- 备注逻辑：
  - 有 distance 时：`Distance: xxxx km, QSO by CWCN`
  - 无 distance 时：`QSO by CWCN`

## Nice to Have

以下能力先记为增强项，不阻塞第一阶段：

- 将 RX 侧观察到的 `tone / wpm` 写入备注
- 将本机 TX 使用的 `tone / wpm` 写入备注
- CAT 频率读取增强
- 国家/地区识别
- 本地 grid 自动获取

## Rebuild Scope

建议直接视为重做的部分：

- `QsoLogbookActivity`
- `activity_qso_logbook.xml`
- `QsoEditorActivity`
- `activity_qso_editor.xml`

建议复用的底层部分：

- `LocalLogRepository` 的 SQLite 仓库骨架
- `CwAdifExporter` 的 ADIF 输出基础
- 站点 callsign / grid 的设置存储
- `Operate -> QSO Editor` 的入口链路

## Proposed Delivery Order

### Phase 1: Data model and persistence

- 扩展 confirmed log 字段
- 设计数据库迁移
- 明确 `Local time input -> UTC storage -> UTC export`
- 将 `manualConfirmed` 变成正式产品字段

### Phase 2: Rebuild QSO Editor

- 重做成正式表单页
- 支持手动新建
- 支持编辑已有记录
- 支持 `Operate` 带初始值进入

### Phase 3: Rebuild Logbook List

- FT8CN 风格顶栏
- card 列表
- 搜索
- 详/简切换
- 高级筛选
- 长按菜单

### Phase 4: Export and share

- ADIF 范围导出
- TXT 分享
- 局域网 host server 导出 `.adi`

## Open Questions

当前还需要后续确认的点：

1. `manualConfirmed` 与旧 `needManualReview` 的迁移策略最终怎么定
2. `frequency` 存储单位使用 `Hz` 还是 `kHz`
3. `station grid auto-detect` 的具体来源和权限策略
4. `country/region` 第一版是否完全不显示，还是留空位待后续补 DXCC
