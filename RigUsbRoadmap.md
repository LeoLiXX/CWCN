# Rig / USB 路线记录

Last updated: 2026-05-06

## 当前结论

当前工程里的 USB 能力不是完全空白，但也还没有达到正式生产可交付状态。

- `USB TX / Keying`：
  已有底层实现。`UsbSerialKeyerRigControlAdapter` 可以通过串口 `RTS / DTR` 做 CW keying。
- `USB 正式设置路径`：
  已经开始接入正式 `Rig Setup`。现在至少可以看到 USB keyer 状态、保存 `key line / preferred device`、发起 Android USB 权限申请，并从检测到的设备列表中选择目标设备。
- `USB RX / 外部音频输入`：
  还没有接上。当前正式 `Operate` 页的 RX 仍然默认走手机麦克风。
- `USB CAT`：
  `Serial CAT` 方向已有更多设置、探测与权限处理，但它和 USB keyer 仍然是相邻而未统一的两条路径。

## 一个重要原则

需要明确区分两类东西：

1. 正式生产链路
   目标是让普通用户把设备接起来，完成真实的 RX / TX 使用。

2. 开发者 bench / 调试链路
   目标是验证底层控制是否工作，比如脉冲、短文本、端口实验、时序扫描。

`Pulse`、`Send VVV`、`Stop` 这种都属于第二类。
它们可以保留，但必须隐藏在开发者路径里，不能继续污染正式设置主线。

## 目前已经具备的正式能力

1. Android Manifest 已声明 `usb host`，并监听 `USB_DEVICE_ATTACHED` 进入 `RigSetupActivity`
2. `RigRegistry` 已注册 `usb-serial-keyer`
3. `OperateActivity` 在 TX 时已经能根据 profile 选择 `UsbSerialKeyerRigControlAdapter`
4. `Rig Setup` 现在已经具备正式用户真正需要的 USB 基础入口：
   - key line 选择
   - preferred device 保存
   - 检测到的 USB 设备 selector
   - 当前 USB keyer 状态显示
   - Android USB 权限申请

## 仍然缺的正式生产能力

### P0

1. `Operate` 页没有清楚显示当前实际 TX 路由

用户需要知道：
- 当前是否走 `Mic / Phone Audio`
- 还是走 `USB keyer / RIG`
- 选中的 USB 设备是谁
- 当前 key line 是 `RTS` 还是 `DTR`
- 是否仍缺 Android USB 权限

2. `USB attach` 之后的正式引导还不够强

现在能跳到 `Rig Setup`，但还不够像正式产品流程。
理想状态应该是：
- 自动聚焦到相关 USB profile
- 明确提示下一步该做什么
- 完成授权后能回到 `Operate` 继续工作

3. 默认输入 / 输出链路策略要真正落地

根据之前已经达成的产品决定：
- 如果没有连电台：
  - RX 默认走 `Mic`
  - TX 默认走手机音频
- 如果已连电台：
  - 走 `RIG` 配置好的链路

这条策略必须在设置、状态显示、实际收发路径三处一致。

### P1

4. `Settings` 里还需要更明确的正式路由配置

比如：
- 默认 TX route
- 默认 RX source
- USB / Serial / CAT 连接优先级
- 断开时回退策略

5. `Operate` 页还没有形成真正的生产对接闭环

正式闭环应该是：
- 进入 `Operate`
- 看见当前路由状态
- 如果缺权限或缺连接，有明确提示
- 直接开始 RX / TX
- 不需要先进入 bench 页面做验证动作

### P2

6. `USB RX audio source` 尚未设计与实现

这是后续大线，不是当前最该先做的事情。
如果目标是尽快形成可用的正式版本，应先完成 `USB keying / CAT` 的正式链路，再决定是否推进 USB 外部音频。

## bench / 调试功能的归属

以下能力属于开发者 bench，不属于正式主链路：

- 单次 key down / key up
- `E / T / VVV` 这种短文本测试
- 强制 stop 测试
- 时序扫描、短脉冲实验、开关端口实验

这些能力可以保留，但应满足两个条件：

- 默认不出现在正式用户主流程里
- 只在开发者模式或实验入口中显示

## 当前推荐的下一步顺序

1. 先把 `USB keyer` 明确归位
   - 正式用户路径只保留连接、授权、设备选择、状态摘要
   - bench 动作隐藏到开发者路径

2. 再补 `Operate` 页的正式路由摘要
   - 当前 RX 来源
   - 当前 TX 路由
   - 当前 RIG / USB 状态
   - 权限缺失 / 设备缺失提示

3. 然后把“未连电台走 Mic / 手机音频，已连电台走 RIG”这条默认策略真正打通

4. 最后再决定是否开启 `USB RX audio` 这条大线

## 当前建议

对正式 Production 路线来说，下一步最值得做的不是继续扩 bench，
而是：

`把 Operate 页变成真正可见、可解释、可工作的正式 RX / TX 路由入口`

原因：

- 这才是用户真正工作的页面
- USB 是否可用，不该靠 bench 动作判断，而该靠正式路由状态和真实操作闭环判断
- 能直接推动“接上设备就能工作”的产品体验
