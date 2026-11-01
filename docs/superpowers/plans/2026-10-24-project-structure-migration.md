# RentPilot 项目目录迁移实施计划

**目标：** 按用户指定结构将现有 Android 用户端迁至 `frontend/`，保留 UI、业务逻辑、设计资源和原始数据。用户明确授权直接整理验证，不添加确认步骤。

**方案：** 在原工作区原地迁移（当前没有 Git 仓库），逐项确认源/目标路径后使用 PowerShell 原生文件操作。前端源码、测试、配置、Android 与 node_modules 保持同级关系；根目录 docs/design-reference/deliverables 保留。仅跨模块资源脚本与有效文档需要调整路径。

**验证重点：** 迁移前后 SHA-256 核对源码/测试/素材/CSV/原生源码/前端配置；本地 Vite 旧进程停止后从新目录启动；现有 5 项状态测试、6 项浏览器测试、构建、Capacitor 同步及实际 Gradle 编译尝试；不把网络下载失败描述成 APK 成功。

- [x] 清点依赖及运行进程，记录迁移前文件哈希。
- [x] 逐项安全迁移前端目录和配置，归档原 ZIP 和验证文档；backend/admin-web 仅放 .gitkeep。
- [x] 修正图片导入、截图脚本的跨目录路径；核实 Vite/TS/Capacitor/Gradle 仍正确，不改业务与 UI。
- [x] 更新中文 README、前端说明、根 .gitignore 和有效文档链接。
- [x] 执行哈希核对、状态测试、浏览器测试、构建、Capacitor Android 同步、Gradle 编译尝试。
- [x] 独立复核最终目录与关键路径，记录实际结果及 Android Studio 新入口。
