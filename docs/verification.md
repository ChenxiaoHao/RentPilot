# RentPilot 验证记录

> 这是用户端初次实现的历史验证记录。项目现已迁至 `frontend/`，迁移后的检查见 [迁移验证记录](migration-verification.md)。当前 Android Studio 入口为 `D:\CODE\RentPilot\frontend\android`。

验证日期：2026-10-24（Asia/Shanghai）。

| 检查 | 实际结果 |
| --- | --- |
| `npm test` | 5 项状态行为测试通过 |
| `npm run test:e2e` | 6 项真实 Chrome 浏览器流程通过 |
| TypeScript + Vite 生产构建 | 通过，生成 `dist/` |
| `npm run android:sync` | 通过，最新 Web 资源与 2 个原生插件已同步 |
| Android Gradle `assembleDebug` | 失败：Wrapper 分发包下载读取超时，未进入 Android 编译 |
| `adb devices` | 无连接设备 |
| 模拟器 | 无 AVD / system image |

浏览器测试覆盖：多行消息、空白禁发、需求补充、推荐、规划完成、偏好草稿隔离、确认保存、刷新恢复、新版本历史快照、收藏、地图无匹配、筛选建议及返回保留条件、方案横滑、LifeSim 场景数据变化、双方案选择。测试模拟了视口缩小和 34px 顶部安全区，确认导航隐藏与输入按钮可见。

全路由检查使用 320 / 360 / 402 / 480px 宽度；360px 用 640px 高，其余用 874px 高，共 60 个路由/尺寸组合（含无效方案和房源回退）。未发现 document 横向溢出或未捕获 JavaScript 异常。

`node scripts/capture-ui.mjs` 已生成 12 个页面的 402px 截图及 360px 首页截图，结果 `issues: []`（未捕获页面异常、未发现未加载图片）。截图位于 `docs/screenshots/`。人工复核了首页、方案、偏好、地图、房源、个人中心和对比页；保留了 Stitch 黑白暖灰卡片与胶囊导航，移除了导出伪 iOS 系统栏。生产资源中的 15 张原图均已本地化，无失效图片索引。

独立代码审查发现并修正了：协商确认指标与预览不符、损坏缓存导致金额渲染异常、地图筛选丢失、系统栏字色与背景对比不足、安全区多算高度、历史详情丢失版本快照。历史详情进入当前 LifeSim/对比时已有明确说明，不把当前指标当历史快照。

Android Studio 当前打开 `D:\CODE\RentPilot\frontend\android`；需要 JDK 21 和 SDK Platform 36。本历史验证时机器只安装 Platform 37，未连接设备。原生下载失败及环境详情见 [历史构建记录](android-build-result.md)。此次不声称 APK 构建成功，也不声称完成真机软键盘、系统手势或不同 Android WebView 的运行验证。
