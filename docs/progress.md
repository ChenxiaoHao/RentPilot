# RentPilot Android 实施记录

## 目录迁移后的入口

- 所有前端源码、配置、依赖、脚本、测试、Web 构建与 Android 工程现位于 `frontend/`。
- Android Studio 打开 `D:/CODE/RentPilot/frontend/android`；npm 命令在 `frontend/` 执行。
- `backend/`、`admin-web/` 只保留 `.gitkeep`；原 CSV、Stitch 导出、ZIP 保持原内容。
- 本次验证见 `docs/migration-verification.md`。以下为初次实现过程的历史记录。
- 迁移验证：130 项 SHA-256 一致；前端构建、5 项状态测试、6 项浏览器流程、Capacitor 同步通过；Gradle 下载阶段达到180秒时限后有界结束，未进入编译、无APK。

- 授权：用户要求依据已确定设计直接实现、构建、同步 Android；不再增加确认门槛。
- 资源：17 HTML，14 份有效单页PNG + 一张总览PNG，3 份28字节无效PNG。原始ZIP和CSV只读保留。
- 工作区：根目录无Git仓库，按用户指定 D:/CODE/RentPilot 原地新增文件，不创建工作树或提交。
- 接口分工：root 维护共享组件、数据、状态、路由及聊天地图；worker 只写分配的 pages 文件。
- 实现：9 个核心页面与 7 个交互状态均有对应页面/组件和可操作跳转。
- 资源：15 张原始图片下载并本地化，无截图替代；原始 CSV 和 ZIP 未改动。
- 状态测试：5 passed；浏览器：6 passed；多尺寸：60 route/viewport combinations 无横向溢出与未捕获异常。
- 最新 `npm run android:sync` 通过（含 TypeScript、Vite production build、Capacitor copy/update）。
- 原生构建：实际 assembleDebug 在 Gradle Wrapper 下载阶段读取超时，未生成 APK。仍需 SDK Platform 36；当前无设备/AVD。
- 审查修复：Mock 协商确认指标、cache schema、筛选保留、系统栏 LIGHT、safe-top 高度、历史版本详情快照及当前模拟跳转提示。
- 文档：README.md、docs/verification.md、docs/android-build-result.md；当前 Android Studio 打开 D:/CODE/RentPilot/frontend/android。
