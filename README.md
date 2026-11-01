# RentPilot

RentPilot 包含 Vue 3 + TypeScript + Vite + Vue Router + Pinia + Capacitor 8 的 Android 用户端，以及 Java 21 + Spring Boot 后端。1500 条模拟房源的搜索筛选、详情、账户、收藏、浏览记录和联系消息已接入后端。主导航为 **AI / 找房 / 联系 / 我的**，保持黑白灰与底部胶囊导航。Spring AI 寻房通过模型提取条件，再从数据库只读检索候选；模型服务必须单独配置。`admin-web/` 继续预留。

原有方案、LifeSim 和旧地图示例继续保留；它们仍为本地演示，不能视作真实 AI 计算结果。新的 AI 对话使用独立、按账号区分的本机历史，不覆盖旧演示记录。AI 工作地找房已接高德地点选择、预计路线筛选和累计续查，支持步行、骑行、公共交通、驾车及多轮修改；无匹配时先预览建议，明确确认后才修改条件。推荐可展开查看服务端核算的依据、费用和排序，并按需请求 Spring AI 在核验事实中选择解读重点。界面延续 design-taste-frontend 的 App 设计方向。[最新推荐依据与解读验证](docs/recommendation-evidence-2026-10-31/README.md) · [AI 找房闭环](docs/search-closure-2026-10-31/README.md) · [AI 模型配置](docs/ai-search-2026-10-30/README.md)。

## 目录结构

```text
RentPilot/
├── frontend/                  # 当前 Vue 用户端与 Capacitor Android 工程
│   ├── android/               # Android Studio / Gradle 工程
│   ├── src/                   # 页面、组件、状态与 Mock 数据
│   ├── public/                # 本地图片与静态资源
│   ├── scripts/               # 资源导入等开发脚本
│   ├── tests/                 # 单元与浏览器流程测试
│   ├── package.json
│   ├── package-lock.json
│   ├── capacitor.config.ts
│   └── README.md              # 路由、资源和前端开发说明
├── backend/                   # Spring Boot 房源、账户及个人资源 API
│   ├── src/                   # JDBC 查询、事务导入、迁移和测试
│   ├── scripts/               # Windows 启动和 HTTP 验证
│   ├── data/                  # 本地持久化数据库（生成，不提交）
│   ├── pom.xml
│   ├── mvnw.cmd               # Maven Wrapper
│   └── README.md              # 运行方式、接口和数据规则
├── admin-web/
│   └── .gitkeep               # 管理端预留，无实现
├── deliverables/              # 原始 CSV 数据，保留原内容
├── design-reference/          # 原始设计 ZIP 与解压参考
├── docs/                      # 验证记录、截图及资源/构建报告
├── .gitignore
└── README.md
```

依赖目录 `frontend/node_modules/`、Web 构建输出 `frontend/dist/`、Android 构建缓存及测试临时产物不纳入版本控制。Gradle Wrapper、原生源码与资源、依赖锁文件、设计 ZIP 和原始 CSV 应保留。

## 开发与验证

后端在 `backend/` 使用 JDK 21，运行 `./mvnw.cmd -s .mvn/settings.xml verify` 构建和测试，再执行 `./scripts/start.ps1` 启动。默认地址 http://127.0.0.1:8080/api/health；数据保存在本地文件库，重复启动不重复导入。详细接口、参数、模拟数据标记与 PostgreSQL 预留配置见 [后端说明](backend/README.md)。

后端首轮验收：41 项自动化测试、26 项实际 HTTP 检查和独立 JAR 重启验证通过；1500 个唯一房源编号，原 CSV 和素材未修改。[查看后端交付记录](docs/backend-2026-10-28/result.md)。

默认进入 `/chat`，数据库房源列表在 `/listings`；可在 `/account?mode=register` 注册测试账户，用户名以字母开头、3–24 位，密码至少 8 个字符。Web 通过 Vite `/api` 代理访问后端，Android Debug 通过原生 HTTP 访问模拟器宿主机。本地照片均为室内示意，不代表对应房源实拍。后端不可用时显示错误与重试，不回退虚假成功。此前 [房源列表联调记录](docs/listing-integration-2026-10-28/result.md) 保留历史结果。

需要 Node.js **22.12+**。从仓库根目录进入前端后执行所有 npm 命令：

```powershell
cd frontend
npm ci
npm run dev -- --port 5178
npm run build
npm test
npm run test:e2e
npm run android:sync
```

开发预览为 `http://127.0.0.1:5178/`。`build` 包含 TypeScript 检查并输出 `frontend/dist/`；`android:sync` 会先构建，再将 Web 资源和 Capacitor 配置同步到原生工程。每次前端改动后、Android Studio 运行前都应重新同步。浏览器流程测试默认使用本机 Chrome，端口为 5178，完整测试需要后端 8080 服务。真机和正式部署须配置可达的 HTTPS 后端后重新构建。

## Android Studio

使用 **Open** 打开 **`D:\CODE\RentPilot\frontend\android`** 整个目录。该目录包含 `settings.gradle`、根 `build.gradle`、`app` 模块和 Gradle Wrapper。

1. Gradle JDK 选择 **JDK 21**。
2. 在 SDK Manager 安装 **Android SDK Platform 36**；Build Tools 不能替代对应的 SDK Platform。
3. 确保能够下载 Gradle 8.14.3 和 Google Maven / Maven Central 依赖，按本机网络配置 Android Studio 代理。
4. Sync Project with Gradle Files，连接启用 USB 调试的设备或启动模拟器，再运行 `app`。

包名为 `com.rentpilot.app`，最低 API 24，目标/编译 API 36。SDK 本机路径写入被忽略的 `frontend/android/local.properties`。在 `frontend/` 中可执行 `npm run android:open` 打开 Android Studio，或在 JDK、SDK 和网络就绪后执行 `npm run android:apk`；成功时 APK 位于 `frontend/android/app/build/outputs/apk/debug/app-debug.apk`。

## 数据与功能边界

1500 条数据已真实导入数据库并参与查询，但全部为**模拟房源**。未知设施、空坐标及费用周期不明保持原意。新账户的收藏、历史、看房计划保存在后端并按账户隔离，重启保留；密码使用 BCrypt，会话令牌在数据库仅存哈希，设备不保存密码。当前为本机 MVP，尚未做云端部署、真实 PostgreSQL 和实体 Android 设备验收。

找房列表可进入高德底图 `/find-map`，展示中心附近最多六套模拟房源，支持筛选和进入详情；坐标为小区参考位置，尚无实时定位。AI 聊天已接 Spring AI 提取条件、后端检索和高德预计路线筛选，需要单独配置模型及高德 Web 服务；默认每批核验 12 个不同坐标及其全部候选房源，可保持条件继续核验，明确标注部分覆盖，不能把子集无匹配说成整个目录无匹配。搜索快照为本机进程内短期数据，重启或过期后需重新查询。原 `/map`、旧方案中的通勤和风险、LifeSim 仍为本地演示。两个演示账户可协商并保存联系消息，认领模拟房源不代表房东身份认证；看房计划仍是个人安排。页面、接口及集成边界详见 [前端说明](frontend/README.md)。

## 验证记录

**2026-10-31 详情位置地图：** 房源详情内嵌可拖动、缩放、回中的高德地图，保留小区参考标记、未知位置和失败重试。单元55项、完整浏览器87项、生产构建、Android同步及Debug APK编译通过；生产版320/402/768宽真实地图触屏拖动通过，taste检查无FAIL/WARN。详见 [详情地图交付记录](docs/listing-location-map-2026-10-31/README.md)。

**2026-10-31 条件面板改造：** 支持低预算/租金上下限、工作区镇与具体地址、四种通勤方式和可选租赁方式，取消固定张江、2500下限与整租一居。前端55项、完整浏览器85项通过；构建、Capacitor同步及Android Debug编译通过。真实查询900元以内返回3套模拟合租，500元如实无匹配。详见 [条件面板交付记录](docs/search-conditions-2026-10-31/README.md)。

**2026-10-31 推荐依据与按需解读：** 后端 124 项、前端单元 47 项、完整浏览器 76 项通过；前端构建、Capacitor 同步及 Android Debug 编译成功。真实模型/高德验证推荐依据、首期费用 9600 元及预算改变后解读使用新差额；物业费周期未知保持待确认。详见 [第二步交付记录](docs/recommendation-evidence-2026-10-31/README.md)。

**2026-10-31 AI 找房闭环：** 后端 118 项、前端单元 42 项、浏览器完整 71 项通过，文案精简后相关浏览器专项 12 项通过；前端构建、Capacitor 同步和 Android Debug 编译通过。真实服务已验证累计核验 46 → 70 套、取消预览不改条件、确认后仅调整单项、多轮修改与普通筛选预算建议。高德限频已通过未缓存路线请求间隔处理。详见 [闭环交付记录](docs/search-closure-2026-10-31/README.md)。

**2026-10-31 AI 通勤寻房：** 后端 107 项、前端单元 37 项、浏览器全量 67 项通过；前端生产构建、Capacitor 同步和 Android Debug APK 构建通过。真实模型与高德已验证吴泾工作地选择、骑行结果、切换步行及交大校区选择。本轮未连接实体 Android 设备，详见 [通勤寻房验证记录](docs/commute-search-2026-10-31/README.md)。下方保留历史阶段的验收结果。

**2026-10-28 最新地图联调：** 后端 78 项、前端单元 26 项、浏览器全量 50 项通过；地图专项、生产构建、Capacitor 同步和 Android Debug APK 构建通过。Pixel 8 API 35 已实测真实高德底图、模拟房源聚合和预算筛选，原本机记录未变。详见 [地图联调记录](docs/map-integration-2026-10-28/result.md)。[普通租房记录](docs/rental-core-2026-10-28/result.md)、[此前前端 MVP](docs/ui-mvp-2026-10-28/result.md)、[二次视觉优化](docs/ui-visual-revision-2026-10-27/result.md) 和 [启动排查](docs/android-launch-2026-10-27.md) 保留历史证据。以下迁移记录保留的是 10 月 24 日当时的结果。

迁移后的验证命令、结果与限制以 [迁移验证记录](docs/migration-verification.md) 为准。[原有验证记录](docs/verification.md)、[原生构建记录](docs/android-build-result.md) 和 `docs/screenshots/` 保留历史证据，不能据此认定迁移后的检查已通过。

10 月 24 日迁移时，构建、5 项状态测试、6 项浏览器流程和 Capacitor 同步通过，130 个迁移保护文件的 SHA-256 一致；当时原生依赖下载超时，尚未生成 APK。该历史限制已在上述 10 月 27 日验证中解决。

## 参赛准备

项目按第十一届华为 ICT 大赛中国创新赛赛题 1 规划。已完成第1天的赛题/模板梳理、范围与验收标准、初赛材料提纲和本机环境检查；云账号、比赛资源、模型与高德权限仍待账号内核验。15天安排及工具交接从 [参赛工作区](docs/competition/README.md) 进入。第1天仅新增资料与文档，没有改动产品代码、UI或原始数据。
