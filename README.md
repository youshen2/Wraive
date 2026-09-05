<p align="center">
  <img src="design/wraive-icon.png" width="112" height="112" alt="Wraive 图标" />
</p>

# Wraive

为 Wear OS 打造的 AI 助手，在手腕上开启文字与语音对话。

Wraive 使用 Kotlin 与 Jetpack Compose 构建，围绕手表的圆形屏幕、旋转表冠与底部边缘按钮设计交互。连接你选择的模型服务，即可在手表上提问、翻译、使用工具并管理会话。

[项目仓库](https://github.com/youshen2/Wraive) · [问题反馈](https://github.com/youshen2/Wraive/issues)

## 功能

- **模型与助手**：支持 OpenAI Chat Completions、OpenAI Responses、Anthropic 和 Google 接口；按供应商管理模型，自定义助手与生成参数。
- **文字与语音**：流式回复、语音输入、回复朗读、Markdown 显示，以及图片和文档附件。
- **搜索与工具**：联网搜索、MCP 工具、长期记忆、世界书、提示词转换和翻译工作台。
- **会话管理**：置顶、归档、临时对话、全局搜索，以及 Markdown / HTML 导出。
- **备份与恢复**：本地备份、S3 兼容云端加密备份，以及供应商配置的二维码分享与导入。
- **手表体验**：圆屏适配、表冠滚动、原生 Wear Material 3 组件、动态配色、自定义字体和中英文界面。

## 开始使用

1. 从首页底部菜单进入 **设置 → 模型与助手 → 模型供应商**，添加供应商，填写接口类型、服务地址和 API Key。
2. 打开对应的供应商详情页，拉取模型列表或手动添加模型。
3. 在 **设置 → 模型与助手 → 助手** 中创建或编辑助手，为其选择模型。
4. 回到首页，通过底部菜单创建新对话或临时对话。

搜索、MCP、语音和云备份服务可在设置中按需配置。

## 构建与安装

### 环境

- JDK 21。
- Android SDK Platform 37，以及 Android SDK Build-Tools、Platform-Tools。
- Android Studio 或命令行环境；使用仓库内的 Gradle Wrapper。
- Wear OS 手表或模拟器，应用最低 Android API 为 26。

### 构建调试版本

```bash
git clone https://github.com/youshen2/Wraive.git
cd Wraive
./gradlew :app:assembleDebug
```

使用 Android Studio 打开项目时可由 IDE 配置 SDK 路径。命令行构建可通过 `ANDROID_HOME` 指定 SDK，或在本地 `local.properties` 中填写 `sdk.dir`。

Windows 下使用 `gradlew.bat :app:assembleDebug`。调试 APK 输出至：

```text
app/build/outputs/apk/debug/app-debug.apk
```

开启手表的开发者选项和调试功能，完成 ADB 连接后安装：

```bash
adb devices
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

### 验证

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

界面验证用例位于 `app/src/androidTest`，可在 Android Studio 中选择目标 Wear OS 模拟器运行。

## 项目结构

```text
app/src/main/java/moye/wear/wraive/
├── ui/          界面、导航、组件与主题
├── chat/        会话生成、提示词与工具调度
├── network/     模型服务、搜索、MCP 与网络连接
├── data/        数据存储、导入导出与备份
├── audio/       语音输入与朗读
├── model/       数据模型与配置
└── service/     后台生成服务

app/src/main/res/    Android 资源与自适应图标
```

## 作者与致谢

设计与开发：**爅峫**。

本项目参考了 [Kelivo](https://github.com/Chevey339/kelivo)，感谢其带来的启发。

## 许可证

Copyright (C) 2026 爅峫。

本项目采用 [GNU Affero General Public License v3.0](LICENSE) 发布，SPDX 标识为 `AGPL-3.0-only`。完整条款见 [LICENSE](LICENSE)。
