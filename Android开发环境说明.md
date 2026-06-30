# DanChi Android 开发环境说明

日期：2026-06-27

## 推荐技术栈

- 开发语言：Kotlin
- UI：Jetpack Compose + Material 3
- 架构：MVVM / MVI，按 `domain`、`data`、`feature-*` 分层
- 本地数据库：Room
- 异步与状态：Kotlin Coroutines + Flow
- 依赖注入：Hilt
- 导航：Navigation Compose
- 复习算法：FSRS，先做本地简化版，后续按真实学习数据校准
- 发音：Android TextToSpeech 兜底，后续接入授权音频或云 TTS 缓存
- 调试与构建：Android Studio + Gradle Wrapper + ADB

## 已安装环境

- Android Studio：`C:\Program Files\Android\Android Studio`
- JDK：Temurin JDK 17，`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`
- Android SDK：`C:\Users\37768\AppData\Local\Android\Sdk`
- Android SDK Command-line Tools：21.0
- Android SDK Platform：`platforms;android-37.0`
- Android SDK Build-Tools：`37.0.0`
- Android SDK Platform-Tools / ADB：`37.0.0`
- Android Emulator：`36.6.11`
- AVD：`DanChi_API37`

已写入用户环境变量：

- `ANDROID_HOME=C:\Users\37768\AppData\Local\Android\Sdk`
- `ANDROID_SDK_ROOT=C:\Users\37768\AppData\Local\Android\Sdk`
- 用户 `PATH` 已加入：
  - `%ANDROID_HOME%\platform-tools`
  - `%ANDROID_HOME%\emulator`
  - `%ANDROID_HOME%\cmdline-tools\latest\bin`

JDK 安装器已写入 `JAVA_HOME`。

## 当前项目状态

当前目录 `E:\DanChi` 还不是 Android 源码工程，只有：

- `背单词App开发文档.md`
- `doc\` 下的参考截图

因此当前还没有：

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradlew.bat`
- `app\`
- `local.properties`

后续创建 Android 工程后，应使用工程自带的 `gradlew.bat`，不依赖全局 Gradle。

## 调试方式

### 1. Android Studio 调试

1. 打开 Android Studio。
2. 打开后续创建的 Android 工程目录。
3. 等待 Gradle Sync 完成。
4. 选择设备：
   - 真机：开启 USB 调试并连接电脑。
   - 模拟器：选择 `DanChi_API37`。
5. 点击 Run 或 Debug。

### 2. 命令行构建

在 Android 工程根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

安装到已连接设备：

```powershell
.\gradlew.bat installDebug
```

查看设备：

```powershell
adb devices
```

查看日志：

```powershell
adb logcat
```

只看 App 日志时，建议在代码里统一使用固定 tag，例如 `DanChi`：

```powershell
adb logcat -s DanChi
```

### 3. 启动模拟器

```powershell
emulator -avd DanChi_API37
```

当前机器 BIOS/UEFI 中的虚拟化未启用，`emulator -accel-check` 显示：

```text
Android Emulator hypervisor driver is not installed on this machine
Virtualization Enabled In Firmware: No
```

这意味着模拟器加速暂不可用。要稳定使用模拟器，需要：

1. 进入 BIOS/UEFI 开启 Intel VT-x 或 AMD-V。
2. 回到 Windows 后，用管理员权限运行：

```powershell
C:\Users\37768\AppData\Local\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat
```

或者在 Windows 功能中启用 Windows Hypervisor Platform。

在这之前，建议优先使用真机调试。

## 真机调试准备

Android 手机侧：

1. 设置 -> 关于手机 -> 连续点击版本号，开启开发者选项。
2. 开发者选项 -> 开启 USB 调试。
3. USB 连接电脑后，手机上允许 RSA 调试授权。

电脑侧验证：

```powershell
adb devices
```

看到类似下面输出即表示连接成功：

```text
List of devices attached
设备序列号    device
```

## 建工程建议

如果从零建立 DanChi Android MVP，建议：

- 包名：`com.danchi.app`
- Minimum SDK：API 26 或 API 28
- Target / Compile SDK：API 37.0
- 模板：Empty Activity + Jetpack Compose
- 第一阶段模块：
  - `app`
  - `core-domain`
  - `core-data`
  - `feature-home`
  - `feature-study`
  - `feature-dictionary`
  - `feature-profile`

首个可跑通目标：

1. 首页今日计划静态数据。
2. 单词学习页静态样例词。
3. Room 存储 50 个测试词。
4. TTS 播放单词。
5. 简化 FSRS 更新复习时间。
6. `assembleDebug` 和真机 `installDebug` 成功。
