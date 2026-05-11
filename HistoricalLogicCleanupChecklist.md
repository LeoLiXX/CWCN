# 历史残余逻辑清理清单

Last updated: 2026-05-09

## 说明

- 本清单只关注真正的历史残余逻辑。
- `PowerShell/中文乱码` 问题已经从本清单移除，不再作为代码待办。
- 目标是把 `Production` 主链路、`Debug/Bench` 工具链路、以及少量必须保留的兼容逻辑明确拆开。

## 当前判断

### 最值得立刻处理的主问题

1. `OperateActivity` 内部同时并存了多套旧渲染分支
2. 正式操作链路和 `debug/bench/mock` 语义还没有彻底切开
3. 一些旧的 `legacy/bench` 命名仍然在正式用户可触达路径附近出现

### 明确不是当前主清理目标

1. `LocalLogRepository` 里的 `migrateLegacy*`
   - 这是历史数据兼容，不应轻易删除
2. `AudioTrackTxAudioOutput` 里的 `setLegacyStreamType(...)`
   - 这是 Android 音频兼容实现，不属于业务历史残余
3. `CwSignalSnapshot` 里的 `legacyDefaultsMarker`
   - 先保持观察，不作为第一批动作

## 待办列表

## P0. Operate 主界面历史分叉收敛

### 问题

`OperateActivity` 里同时存在：

- `Safe`
- `Production`
- `ProductionV2`

三层并存的渲染/文案/状态逻辑。

典型热点在：

- [OperateActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/operate/OperateActivity.java)

当前可见症状：

- `renderStatusMainSafe -> ProductionV2`
- `renderStatusDetailSafe -> ProductionV2`
- `renderSourceChipSafe -> ProductionV2`
- `renderTxRouteChipSafe -> ProductionV2`
- `renderReceiveMetaSafe -> ProductionV2`
- 同时旧的 `Production` 整套实现还保留在同一个类里

### 风险

- 后续每次改 `Operate` 行为时，都容易改漏旧分支
- UI 文案和状态机行为不容易建立单一真相
- 很难判断某个现象到底是当前逻辑，还是旧分支兜底

### 动作

- [x] 删除 `OperateActivity` 中已经不再使用的 `Production` 旧分支
- [x] 将 `Safe` 命名改回真实语义命名
- [x] 保留一套正式版本渲染实现，避免 `ProductionV2` 长期挂名
- [x] 把状态栏、RX 摘要、TX 摘要、overlay 文案的渲染入口统一成单一路径

### 预期结果

- `OperateActivity` 只保留一套正式渲染逻辑
- 后续 UI 调整可以直接命中唯一实现

## P0. Operate 正式链路与 Debug 依赖解绑

### 问题

`OperateActivity` 仍直接依赖 `ui.debug` 下的频谱分析类：

- `AudioSpectrumAnalyzer`
- `AudioSpectrumSnapshot`

文件：

- [OperateActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/operate/OperateActivity.java)
- [AudioSpectrumAnalyzer.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/AudioSpectrumAnalyzer.java)
- [AudioSpectrumSnapshot.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/AudioSpectrumSnapshot.java)

### 风险

- 正式操作页继续绑在 debug 包结构上
- 后续想整理 `debug` 工具时，会牵动正式功能
- 包职责不清晰

### 动作

- [x] 将正式频谱/瀑布图所需的分析与快照类型下沉到 `core.spectrum` 或正式 `ui.spectrum`
- [x] `OperateActivity` 不再 import `ui.debug.*`
- [x] `ui.debug` 只保留开发诊断界面专用能力

### 预期结果

- 正式频谱链路独立
- Debug 页面只是在“使用”核心能力，而不是被正式页面反向依赖

## P0. 生产操作路径和 Bench/TX Console 语义切边

### 问题

当前仓库里仍存在较多 bench 语义，且部分仍离正式链路过近：

- `TxActivity` 本身就是 bench/console 风格
- `RigSetupActivity` 中有大量 `bench`、`legacy pulse test`、`Short Pulse Lab` 语义
- `RigProfileCatalog` / `RigRegistry` 中对 bench/mock 的描述较重

热点文件：

- [TxActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/tx/TxActivity.java)
- [RigSetupActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java)
- [RigProfileCatalog.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigProfileCatalog.java)

### 风险

- 用户难以分辨“正式可用路径”与“实验/测试路径”
- 文案会持续把产品引回 bench 心智
- 正式功能评估时，容易被调试工具行为污染

### 动作

- [x] 明确 `Operate` 才是正式 TX/RX 工作流入口
- [x] `TxActivity` 重新定位为开发工具，不再承担正式发送主路径叙事
- [x] `RigSetupActivity` 中将正式配置说明与 bench/lab 操作区分开
- [x] 用户可见文案里减少 `bench`、`lab`、`legacy pulse` 等表述

### 预期结果

- 正式用户路径更清晰
- Debug/Bench 不再和主产品流程缠在一起

## P1. Mock USB 路径与正式 USB 路径分层

### 问题

当前 `Mock USB Serial Keyer` 仍在正式 rig 目录与适配器体系中占有较重位置。

热点文件：

- [MockUsbSerialKeyerPortFactory.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/MockUsbSerialKeyerPortFactory.java)
- [MockUsbSerialBenchFactory.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/MockUsbSerialBenchFactory.java)
- [RigRegistry.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigRegistry.java)
- [RigProfileCatalog.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigProfileCatalog.java)
- [UsbSerialKeyerRigControlAdapter.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/UsbSerialKeyerRigControlAdapter.java)

### 风险

- 真实 USB 路径和 Mock 路径在概念上混得太近
- 后续 USB 直连生产链路收敛时，容易把 mock 行为当成真实能力的一部分

### 动作

- [x] 把 Mock USB 能力明确标记为 developer-only
- [x] 正式 rig 列表默认不强调 mock 项
- [x] 适配器对 mock 场景的支持入口尽量只在 developer mode 下出现

### 预期结果

- 真实 USB 生产链路更加干净
- Mock 继续保留，但不会污染正式能力边界

## P1. Rig Setup 中旧实验命名清理

### 问题

`RigSetupActivity` 中还残留一些明显的旧实验命名，例如：

- `legacy native serial CAT TX/PTT pulse test`
- `Pulse Legacy CAT TX/PTT`

文件：

- [RigSetupActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java)

### 风险

- 用户难以判断哪些是当前推荐路径
- 老实验入口继续制造误导

### 动作

- [ ] 将旧实验命名统一标成 developer / legacy
- [ ] 对当前推荐正式路径单独做“推荐”标识
- [ ] 清理已经没有实际判断价值的旧实验提示语

## P2. Developer 模式文案与入口再收口

### 问题

虽然 `DeveloperToolsActivity`、`InputDebugActivity`、`Settings` 里已经有 developer mode 概念，但文案和边界仍可继续收紧。

热点文件：

- [DeveloperToolsActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/developer/DeveloperToolsActivity.java)
- [InputDebugActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- [SettingsActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/settings/SettingsActivity.java)

### 动作

- [ ] 明确哪些功能永远属于 developer tools
- [ ] 避免正式页面再反向依赖 developer 页面思维模型
- [ ] Settings 中 developer mode 说明进一步压缩成“高级调试入口”

## 建议开工顺序

1. `P0. Operate 主界面历史分叉收敛`
2. `P0. Operate 正式链路与 Debug 依赖解绑`
3. `P0. 生产操作路径和 Bench/TX Console 语义切边`
4. `P1. Mock USB 路径与正式 USB 路径分层`
5. `P1. Rig Setup 中旧实验命名清理`

## 建议第一刀

第一刀建议只做 `OperateActivity`：

1. 删掉旧 `Production` 分支
2. 把 `Safe/ProductionV2` 收敛成一套正式命名
3. 不碰 UI 样式，只收敛逻辑入口

这样风险最低，而且收益最大：

- 能立刻降低后续所有 `Operate` 修改的复杂度
- 不会误伤 USB、RX、TX 的核心链路
- 做完后我们再拆 debug 依赖会顺很多
