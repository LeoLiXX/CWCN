# CWCN 图标导出规格

本文件定义 `CWCN` 图标在工程接入、测试包、截图与后续商店物料中的统一导出规格。

## 1. 视觉源文件

- 唯一主母版：`assets/branding/cwcn-icon.svg`
- Android 接入版资源：`assets/branding/android/`

原则：

- 所有位图导出都从 `cwcn-icon.svg` 出发。
- Android `VectorDrawable` 允许做结构简化，但视觉语义必须保持一致。
- 不允许出现第二套无关风格的“临时图标”长期留在仓库中。

## 2. Android 工程接入规格

推荐保留两套资源：

- 自适应图标：使用 `mipmap-anydpi-v26` 中的 `ic_launcher.xml` 与 `ic_launcher_round.xml`
- 兼容位图：在需要时补充传统 `mipmap-*` 位图资源

如果后续需要导出传统位图，建议尺寸如下：

| 目录 | 尺寸 |
|---|---:|
| `mipmap-mdpi` | `48 x 48` |
| `mipmap-hdpi` | `72 x 72` |
| `mipmap-xhdpi` | `96 x 96` |
| `mipmap-xxhdpi` | `144 x 144` |
| `mipmap-xxxhdpi` | `192 x 192` |

说明：

- Android 主线优先使用自适应图标。
- 兼容位图仅作为低版本回退、第三方分发渠道或部分工具链兼容之用。

## 3. 通用位图导出包

建议维护一套通用导出包，供 README、演示文档、测试渠道与后续商店物料使用：

| 文件名建议 | 尺寸 | 用途 |
|---|---:|---|
| `cwcn-icon-1024.png` | `1024 x 1024` | 总母版位图 |
| `cwcn-icon-512.png` | `512 x 512` | 通用展示 |
| `cwcn-icon-256.png` | `256 x 256` | 文档与网页 |
| `cwcn-icon-192.png` | `192 x 192` | Android 渠道常用展示 |
| `cwcn-icon-128.png` | `128 x 128` | 轻量展示 |
| `cwcn-icon-64.png` | `64 x 64` | 小尺寸预览 |

## 4. 小尺寸可读性要求

图标在缩小到 `64 x 64` 及以下时，必须仍能识别出以下层次：

- 深色底板
- 电键主体轮廓
- 基本信号元素

如果后续发现缩小时细节过多，应优先做“小尺寸专用简化版”，而不是继续堆叠高频装饰。

## 5. 后续扩展建议

后续如需补充正式物料，建议继续派生以下文件：

- `cwcn-feature-graphic.*`
- `cwcn-splash-mark.*`
- `cwcn-notification-icon.*`

这些派生物应在视觉上与主图标保持同一品牌语言：

- 深色基底
- 黄铜电键
- 青绿色信号元素
