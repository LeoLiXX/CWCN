# CWCN Android Icon Pack

这是一套可直接接入 Android 工程的自适应图标资源，基于 `assets/branding/cwcn-icon.svg` 的视觉方向做了适合 `VectorDrawable` 的简化版本。

## 包含内容

- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `res/drawable/ic_cwcn_launcher_background.xml`
- `res/drawable/ic_cwcn_launcher_foreground.xml`
- `res/drawable/ic_cwcn_launcher_monochrome.xml`
- `res/values/cwcn_icon_colors.xml`

## 接入方式

将本目录下的 `res` 内容复制到你未来 Android 工程的 `app/src/main/res` 中，然后在 `AndroidManifest.xml` 里使用：

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ... />
```

如果目标工程需要 Android 13 的单色图标，当前 `ic_launcher.xml` 已经包含：

```xml
android:monochrome="@drawable/ic_cwcn_launcher_monochrome"
```

## 说明

- 这是工程接入版，不是像素级最终商店出图版。
- 当前仓库里还没有 CWCN 自己的 Android App 模块，所以资源先独立存放在 `assets/branding/android/`。
- 如果后续需要，我可以继续补一套 `PNG/WebP` 导出版与 Play 商店展示图。
