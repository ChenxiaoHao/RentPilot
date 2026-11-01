# RentPilot 界面优化与验证结果

日期：2026-10-27。先按应用流程分析所有页面，再实施用户授权的界面和交互优化；详细结论与计划见 [analysis-and-plan.md](analysis-and-plan.md)。

**后续更新：** 用户反馈视觉变化不明显，随后进行了[二次视觉优化](../ui-visual-revision-2026-10-27/result.md)。本文件中的 APK 校验值和截图记录的是二次优化之前的历史版本；APK 输出路径现在指向新版。

## 实施内容

- 保留 Stitch 的黑白灰方向、现有图片、胶囊导航和 Vue 3 / TypeScript / Capacitor 技术栈。统一中文层级、文字对比、卡片间距、圆角及主要触控区域；长内容仍正常滚动。
- 对话增加快捷填写、多行换行、本地参考图片预览与移除、回到最新消息；图片不上传、不参与模拟 AI 回复。输入框支持安全区域和软键盘调整。
- 地图支持筛选与点位联动、清晰选中反馈；无结果时保留实际筛选条件，未设的预算与通勤保持不限，建议中的自定义预算也能在筛选器正确显示。
- 方案增加前后切换、风险详情展开；偏好支持预设、草稿提示与实时预览；LifeSim 增加场景和时间节点互动；历史版本支持定位比较并在恢复前确认。
- 房源图片可放大，咨询、预约、收藏与个人设置增加可关闭的弹层和操作反馈；预约明确不会产生真实请求。协商选择具有键盘和选中语义，生成流程明确是固定示例。

## 自动与视觉检查

- `npm run build`：TypeScript 检查与生产构建成功。
- `npm test`：5 项状态测试通过。
- `npm run test:e2e`：24 项浏览器测试通过，包含 320 / 360 / 402 / 480 宽度的页面溢出检查。最后一次仅涉及首页标签与导航字体的视觉调整后，相关聊天与原有流程共 10 项再次通过。
- 生成并检查 `before/` 和 `after/` 中的 37 张手机截图及测量；最后一轮无浏览器页面错误、无水平溢出。真实 Android 测试不依赖浏览器截图替代。
- 使用作者仓库 commit `12af471f627f866411b4bd785b19646d3672784c` 的 `design-taste-frontend` App 流程；390×844 竖屏检查为 **0 FAIL、1 WARN**：首页标题 30px 高于工具推荐的手机常用 20–24px，为延续 Stitch 首页标题层级而保留。首次检查为 0 FAIL、3 WARN，原有的小字与对比警告已消除。
- 源码静态扫描还有 32 条 WARN：14 条 `eyebrow` 标记、12 条阴影、4 条数字编号和 2 条背景模糊。它们是通用模式匹配，包含语义标签、输入焦点、开关圆点和用户指定的胶囊导航／对话弹层效果；已结合 App 规则与实际截图检查，记录见 [taste-final-lint.json](taste-final-lint.json)，不将其声称为零警告。
- 自动工具自定义宽度会使用横向比例生成高度，故其结果仅采用官方 390×844 竖屏；其他手机尺寸由 Playwright 指定实际竖屏尺寸检查。

## Android

- `npx cap sync android` 成功；`gradlew.bat assembleDebug --console=plain` 成功，日志见 [gradle-final.log](gradle-final.log)。本次命令使用 JDK 21 和进程级临时 socket 目录，未修改系统 Java 配置。
- APK：[app-debug.apk](../../frontend/android/app/build/outputs/apk/debug/app-debug.apk)，5,567,341 字节，SHA-256 `5F4EBE2CF48D7CA7A6F59EFC6542A3568E895D70EFCC207DCFF54BEE269C05EE`。
- Pixel 8 API 35 模拟器上 `install -r` 安装成功、Activity 启动成功，已有对话数据保留。
- 模拟器 WebView 宽 412 逻辑像素；软键盘出现后可见高度为 521，输入框和 44×44 发送按钮位于可见区，导航隐藏。见 [键盘截图](after/android-keyboard.png) 与 [测量数据](after/android-measurements.json)。
- 房源示意图弹层在模拟器中打开，安卓返回键关闭弹层且留在房源详情，见 [弹层截图](after/android-sheet.png) 和 [检查结果](after/android-sheet-back.json)。
- Android Studio 应打开 `D:\CODE\RentPilot\frontend\android`。尚未在实体 Android 设备验证，因此不同厂商输入法和手势区域仍需后续真机检查。

## 数据边界

原始上海 1500 条 CSV、说明、package.json 和 package-lock.json 的 SHA-256 均与实施前一致。方案、对话、地图、房源与预约仍为本地 Mock，未接入真实 AI、高德地图、经纪人、预约、推送或后端。参考图片本地预览在发送文字后会清除，并不会作为对话附件存储。
