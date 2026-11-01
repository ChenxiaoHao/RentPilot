# 2026-10-27 Android 启动故障排查

## 现象与原因

用户截图中 Android Studio 报 `Pixel 8 is already running as process 32340`。现场检查确认该 qemu 进程存在，但 `adb devices -l` 显示 `emulator-5554 offline`：模拟器窗口存在，部署连接失联。不能把此提示等同于 RentPilot 启动崩溃。

尝试 `adb reconnect offline`、重新连接及重启 ADB 服务后仍离线；随后通过模拟器控制台正常关闭对应实例，以 `-avd Pixel_8 -no-snapshot-load` 冷启动。没有清除 AVD 数据，没有删除应用数据或更改页面。

冷启动后 ADB 状态变为 `device`，系统启动标记 `sys.boot_completed=1`。原有聊天内容仍存在。

## 实际构建与运行结果

- 本机现已安装 SDK Platform 36、Build Tools 35/36、JBR 21 和 Gradle 8.14.3，10 月 24 日的环境缺口已不再是当前结论。
- 本次 Codex 终端中的 Gradle 客户端另遇 `PipeImpl → UnixDomainSockets.connect0: Invalid argument: connect`。进程内临时设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\CODE\RentPilot\frontend\android\.gradle\jvm-sockets` 后解决；未改全局 Java 设置或项目业务配置。
- `frontend/android/gradlew.bat assembleDebug --console=plain`：退出 0，`BUILD SUCCESSFUL in 26s`，153 个任务，45 执行、108 已是最新。
- 新 APK：`frontend/android/app/build/outputs/apk/debug/app-debug.apk`，5,548,427 字节。
- APK SHA-256：`5D2A359C62B7E0087F5308F423A69F1E295F8D8C2CEA2B22C06B329925D1EDEF`。
- `adb -s emulator-5554 install -r …`：`Success`，覆盖安装保留已有数据。
- `adb … shell am start -W -n com.rentpilot.app/.MainActivity`：`Status: ok`、`LaunchState: COLD`、`TotalTime: 2410`。
- `topResumedActivity` 为 `com.rentpilot.app/.MainActivity`，应用进程 PID 2078；后续检查仍存活。
- 系统 WebView：`com.android.webview 124.0.6367.219`。本次应用日志没有发现 `FATAL EXCEPTION`、未捕获 JS 错误或页面加载失败。
- 已实际截图确认 RentPilot 对话页面、输入区域和底部胶囊导航显示；截图见 [模拟器运行画面](screenshots/android-running-2026-10-27.png)。
- 源码、测试、配置、设计素材和原始数据的原有 130 项 SHA-256 再次核对全部一致。此次没有优化 UI 或修改业务逻辑。

## 继续使用

Android Studio 继续打开 `D:\CODE\RentPilot\frontend\android`，选择已在线的 Pixel 8，再运行 `app`。若再次出现同类“进程存在但连接离线”问题，可先在 Device Manager 停止对应模拟器，再执行 Cold Boot；无需删除或清空模拟器数据。

这里只验证了本次 Pixel 8 API 35 的启动与页面显示，不据此声称全部机型或全部交互均已完成原生测试。

官方参考：[ADB 状态与连接](https://developer.android.com/tools/adb)、[模拟器冷启动参数](https://developer.android.com/studio/run/emulator-commandline)。本地构建/运行日志使用同日期 `android-launch-2026-10-27-*.log` 文件记录并由 `.gitignore` 排除。
