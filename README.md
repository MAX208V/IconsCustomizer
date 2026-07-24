# IconsCustomizer 🎨

![Android Version](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![LSPosed Framework](https://img.shields.io/badge/LSPosed-Required-blue)
![HyperOS](https://img.shields.io/badge/HyperOS-Supported-orange)
![License](https://img.shields.io/badge/License-MIT-green.svg)

一款基于 Xposed/LSPosed 的 HyperOS 桌面图标自定义模块。突破系统限制，将任意第三方图标包直接应用于系统桌面，支持**多图标包优先级级联混搭**。

> **⚠️ 开发者说明：**
> 本模块最初为个人使用而开发。如遇到问题欢迎在 [GitHub Issues](../../issues) 反馈，我会在有空时尽力修复。

## 📸 截图

<p align="center">
  <b>HyperOS 应用自定义图标包</b><br>
  <img src="imgs/screenshots1.jpg" width="600" alt="HyperOS 桌面应用不同图标包的效果" />
</p>

<br>

<p align="center">
  <b>IconsCustomizer 模块界面</b><br>
  <img src="imgs/screenshots2.jpg" width="300" alt="IconsCustomizer 模块设置界面" />
</p>

## ✨ 功能特性

### 🎯 图标包管理
- **多图标包优先级级联**：按顺序添加多个图标包，依次查找匹配图标，前一个包无匹配自动降级到下一个
- **拖拽排序**：长按 ≡ 手柄拖拽调整图标包加载优先级
- **兜底图标来源独立选择**：所有图标包均无匹配时，使用指定图标包的通用兜底图标
- **APK 图标显示**：图标包列表中显示每个包自身的应用图标

### 🖼️ 图标合成引擎
- **ADW 标准合成**：严格按照 CM11 `IconPackHelper.createIconBitmap()` 实现，支持 `<iconback>` / `<iconupon>` / `<iconmask>` / `<scale>` 合成
- **通用兜底图标**：图标包未提供合成定义时自动生成圆角矩形 + 居中缩略图
- **图标大小可调**：150–250px 自由调节

### 🔀 多包混搭选择器
- **所有图标包统一展示**：为应用选择图标时，同时列出所有配置图标包的全部 drawable
- **匹配图标预览区**：Chip 栏上方显示每个图标包匹配该应用的专属图标，点击直接应用
- **Chip 筛选栏**：按图标包筛选显示，可左右滑动，默认只显示第一个包
- **图标名称 + 来源标注**：每个图标下方显示 drawable 名称和来源包名

### 🎨 颜色自定义
- **覆盖图标颜色**：独立设置前景色/背景色
- **Monet 取色支持**：配合 ColorBlendr 等应用实现动态取色

### 🛠️ 其他功能
- **仅主题主屏幕图标**：可限制只替换桌面图标而非全部
- **Dock 美化**：启用 Dock 圆角、背景颜色、透明度调节
- **时钟小组件主题化**：Monet 时钟颜色自定义
- **文件夹背景自定义**：颜色和不透明度调节

### 🌐 国际化
- 完整中文/英文界面支持，自动跟随系统语言

## ⚠️ 前置要求

1. **Root 权限**：已安装 Magisk 或 KernelSU
2. **Xposed 框架**：已安装并启用 LSPosed（Zygisk 或 Riru 版本）
3. **系统版本**：HyperOS（已在 Poco F7 HyperOS 3.0 / 3.1 中国版最新桌面测试）
4. 建议先在 HyperOS 主题应用中**应用默认图标**以获得最佳效果

## 🚀 安装

1. 从 [Releases](../../releases) 页面下载最新 `.apk`
2. 安装 APK 到设备
3. 打开 **LSPosed 管理器**
4. 进入 **模块** 选项卡，启用 **IconsCustomizer**
5. 确保作用域中勾选了 **系统桌面**（HyperOS Launcher）
6. **强制停止桌面应用或重启设备** 以激活 Hook

## 🛠️ 使用说明

### 基础设置
1. 打开 **IconsCustomizer** 应用
2. 开启 **允许自定义图标包**
3. 点击 **图标包优先级** → **添加图标包**，选择一个已安装的图标包
4. 可添加多个图标包并长按拖拽调整顺序
5. 在 **通用兜底图标来源** 中选择当所有包均无匹配时使用哪个包的兜底图标

### 多包级联规则
```
应用图标查找流程：
  ① 检查所有包的手动覆盖（custom_icon_{包}_{组件}）
  ② 按优先级顺序：包A appfilter → 包B appfilter → ……
     每级仅查找精确匹配，不使用兜底图标
  ③ 全部未匹配 → 使用指定兜底图标包的
     通用图标（ADW 合成 → 内置圆角矩形）
```

### 为特定应用自定义图标
1. 开启主题化后点击 **为特定应用自定义图标**
2. 在应用列表中找到目标应用
3. 每个应用显示其当前使用的图标来源包名
4. 点击应用进入图标选择器
5. 图标网格展示所有配置包的图标，Chip 栏按包筛选
6. 顶部预览区显示每个包匹配该应用的专属图标，点击直接应用
7. 从任意包选择任意图标保存为该应用的自定义覆盖

### 自定义颜色
开启 **覆盖图标颜色** 后可分别设置前景色和背景色，支持 Monet 动态取色。

> **🎨 提示：**
> 使用 Monet 取色时，每次更换壁纸或更改 Monet 调色板后需点击 **重启桌面** 按钮以应用新颜色。
> 建议仅在图标包支持动态 Monet 壁纸取色时使用 **覆盖图标颜色** 功能。

## 📜 许可证

本项目基于 [MIT License](LICENSE) 开源。
