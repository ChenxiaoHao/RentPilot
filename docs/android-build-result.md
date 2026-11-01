# Android 原生构建验证

> 本文件保留目录迁移前的执行环境、命令和失败输出；其中旧工作目录属于历史事实。当前工程位于 `D:\CODE\RentPilot\frontend\android`，迁移后本次编译结果见 [迁移验证记录](migration-verification.md) 与 [Gradle 新记录](migration/gradle-result.md)。

验证时间：2026-10-24 16:14（Asia/Shanghai）。

本次已实际执行 `assembleDebug`，退出码为 **1**。首个失败发生在 Gradle Wrapper 下载阶段，尚未进入 Android Gradle Plugin 配置、SDK 检查或 Java 编译。**没有生成 APK**。

## 本机环境与工程配置

| 项目 | 已核实的值 |
| --- | --- |
| Android Studio | `D:\chenxiaohao\Android Studio\bin\studio64.exe`，2026.2，build `262.9437.185.2621.16467767` |
| 本次 JDK | `D:\CODE\XinKeLink\.local\backend-day1\tools\jdk21\jdk-21.0.12.1+1` |
| JDK 验证 | Microsoft OpenJDK `21.0.12.1+1-LTS`；`java -version` 退出 0 |
| SDK 目录 | `D:\chenxiaohao\Android\Sdk` |
| 本机已安装 SDK Platform | 仅 `android-37.0` |
| 本机 Build Tools | `36.0.0` |
| 工程 Wrapper 配置 | `gradle-8.14.3-all.zip`；本次未完成分发包下载，因此没有验证 Gradle 实际启动 |
| Android Gradle Plugin | 工程配置 `8.13.0` |
| 工程 SDK 要求 | `minSdkVersion=24`，`compileSdkVersion=36`，`targetSdkVersion=36` |
| 工程 Java 要求 | Capacitor 与应用生成配置均使用 `JavaVersion.VERSION_21` |

本次复用了本机已有 JDK 21，未下载另一份 JDK，未修改用户全局 Java/Android 环境变量。写入的 `android/local.properties` 内容为：

```properties
sdk.dir=D:/chenxiaohao/Android/Sdk
```

## 实际执行命令

工作目录：`D:\CODE\RentPilot\android`。

```powershell
$env:JAVA_HOME='D:\CODE\XinKeLink\.local\backend-day1\tools\jdk21\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
$env:ANDROID_HOME='D:\chenxiaohao\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

退出码：`1`。关键原始输出：

```text
Downloading https://services.gradle.org/distributions/gradle-8.14.3-all.zip

Exception in thread "main" java.io.IOException: Downloading from https://services.gradle.org/distributions/gradle-8.14.3-all.zip failed: timeout (10000ms)
    at org.gradle.wrapper.Install.forceFetch(SourceFile:4)
    at org.gradle.wrapper.Install$1.call(SourceFile:8)
    at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)
Caused by: java.net.SocketTimeoutException: Read timed out
```

此前另有一个执行 `gradlew --version` 的 Wrapper 下载进程。验证代理误把尚未刷新到文件元数据的 `.part` 大小当作下载停滞，提前停止了该进程；该操作不属于 Gradle 自行失败。随后上面的 `assembleDebug` 命令独立开始下载，并以上述网络读取超时自行退出。为控制验证时长，未继续下载或反复重试。

最终检查：`android/app/build/outputs/apk/debug/app-debug.apk` 不存在。没有把工程目标 SDK 改成已安装的 API 37。

## Android Studio 打开与设置

1. 当前直接打开 `D:\CODE\RentPilot\frontend\android`（项目目录已迁移）。
2. 在 Settings → Build, Execution, Deployment → Build Tools → Gradle 中，将 **Gradle JDK** 指向上面的 JDK 21 目录。该 JDK 目录来自本机另一项目的工具目录；也可在 Android Studio 中安装并选择独立的 JDK 21。
3. 在 SDK Manager 中使用 SDK 目录 `D:\chenxiaohao\Android\Sdk`，补装工程要求的 **Android SDK Platform 36**。本次尚未运行到 SDK 校验，这是一项已核实的环境缺口，不是上述命令实际抛出的首个错误。
4. 网络可访问 Gradle 分发服务和构建依赖仓库后，执行 Sync，再运行 `app` 或重新执行上面的 `assembleDebug` 命令。

本记录没有声称原生构建成功，也没有声称已完成真机或模拟器运行。
