# RentPilot Android 用户端设计依据

用户已确定设计并明确授权直接实施；不另设设计确认步骤。仅开发 Vue 3 + TypeScript + Vite + Vue Router + Pinia + Capacitor Android 用户端，保留原始 CSV、ZIP、IDE 文件。

Stitch 原始文件位于 `design-reference/stitch_rentpilot_ai_home_ui/`。17 个 HTML 包含 16 组目标页面与状态，以及一个额外探索页；额外探索页不加入产品。三张 28 字节 PNG 无法作为有效截图，但对应 HTML 和用户总览图仍可用于还原。

视觉取自导出：白色页面、#000 主操作、#f6f3f2 卡片、#5e5e5e 次要文字、Manrope 拉丁字体和系统中文字体、12–24px 圆角、悬浮胶囊导航。参考 402×874 的布局比例，移除导出的固定画板、模拟 iOS 系统栏与禁止缩放设置。页面用动态视口、真实 Android safe area、正常内容滚动；软键盘出现时隐藏导航并保持输入可见。

功能：多行聊天与本地多轮 Mock、需求补充、规划生成、主动协商；地图示意与筛选，预留高德适配接口；三方案横滑、详情、房源详情；LifeSim 场景、对比；偏好草稿预览与确认；个人中心、历史版本、恢复与比较；无匹配筛选放宽。全部 Mock 房源/AI/路线清楚标记，不触发真实预约、真实 AI、真实地图请求。

工程现统一位于 `frontend/`（2026-10-24 按用户要求迁移），Android 工程位于 `frontend/android/`，Web 编译目录 `frontend/dist/`。使用 hash history 支持 Capacitor 离线页面。图片和字体打包本地，不在运行时依赖 Stitch 的 CDN。

验证：状态行为测试、真实浏览器多尺寸/长内容/输入/主要流程检查、TypeScript 与生产构建、Capacitor add/sync、尝试 Gradle APK 构建；如本机无设备或 SDK/依赖受限明确报告，不能把同步成功当作设备验证。
