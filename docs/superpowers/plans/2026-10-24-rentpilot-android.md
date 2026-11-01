# RentPilot Android Implementation Plan

> **For agentic workers:** Use subagent-driven-development for independent page groups. User explicitly requested implementation in this chat; continue through validation without additional approval gates.

**Goal:** 实现 Stitch 16 组页面状态，并交付 Android Studio 可打开的 Capacitor Android 工程。

**Architecture:** `frontend/` 内 Vite SPA，Vue Router hash 路由；共享主题/布局/卡片和 Pinia 状态。页面按聊天地图、居住方案、偏好历史分组，避免共享文件写入冲突。本计划的 src/tests/package/Android 路径均相对于迁移后的 `frontend/`；本计划保留初次实现的完成记录。

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Capacitor 8.

**Spec:** `docs/superpowers/specs/2026-10-24-rentpilot-android-design.md`

## Global Constraints

- 黑白灰、白色背景、黑色主要按钮、浅灰卡片、悬浮胶囊导航。
- 402×874 仅设计参考，不固定页面宽高；适配 Android 安全区域与键盘。
- 不开发后端或管理端，不修改原始 CSV/ZIP，不把 Mock 当真实接口。
- 保留导出参考资源，运行时本地图片/字体，Android Studio 打开 android。

## Review Focus

- 多行和中文输入法发送：空白不发送，组合输入回车不误发。
- 320px 窄屏与长消息：无横向溢出，控件换行，输入区可滚动。
- 权重草稿：取消不保存，确认后更新预览/历史，刷新可恢复。
- 不存在的方案和房源 ID：可返回列表，不渲染空对象。
- Android 返回键、系统栏与键盘：正确路由返回、安全区域留白、不遮挡输入。

### Task 1: 基础工程和状态契约
- [x] 创建 package/TS/Vite/Capacitor 配置，安装依赖；保留已有文件。
- [x] 在 tests/state.test.ts 验证空白消息、权重确认隔离、版本恢复，再实现 src/stores/pilot.ts。
- [x] 创建 src/data/mock.ts、src/components/{AppIcon,AppHeader,PropertyCard,MetricGrid}.vue、src/styles.css、src/App.vue。
- [x] 提取 Stitch 图片/天际线并本地化，记录失败项。

### Task 2: 聊天与地图（root）
- [x] src/pages/{ChatPage,GeneratingPage,NegotiationPage,MapPage,ListingPage}.vue；实现多轮消息、规划生成、主动协商、筛选与详情；src/services/map.ts 适配接口。
- [x] 浏览器检查聊天发送、规划完成、地图筛选、房源收藏。

### Task 3: 方案决策页面（独立 worker）
- [x] src/pages/{PlansPage,PlanDetailPage,LifeSimPage,ComparePage}.vue；消费 Plan 数据与共享卡片/状态。
- [x] 横滑与标签同步、场景切换、方案选择与详情有效，非法 ID 显示回退。

### Task 4: 偏好与个人历史（独立 worker）
- [x] src/pages/{ProfilePage,PreferencesPage,HistoryPage,EmptyPage}.vue；权重草稿预览确认、历史比较恢复、无匹配放宽。
- [x] 用状态测试与浏览器验证版本/草稿隔离。

### Task 5: Android 与整体检查
- [x] 建立全路由，npm test、npm run build、浏览器主要流程及多尺寸检查。
- [x] npx cap add android、npx cap sync android；尝试 Gradle assembleDebug 和设备列表。
- [x] 最终独立代码审查、修正实际问题、重新运行对应检查。
- [x] README 记录启动构建、Android Studio 路径、Mock/高德边界及实际验证限制。
