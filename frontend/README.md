# RentPilot Android 用户端

Vue 3 + TypeScript + Vite + Vue Router + Pinia + Capacitor 8。延续 design-taste-frontend 的 App 方向，保留黑白灰与胶囊导航。主导航为 AI / 找房 / 联系 / 我的，默认进入聊天页。聊天通过 `POST /api/ai/search` 接入 Spring AI 条件提取、数据库检索及高德预计通勤筛选，支持交通方式与工作地点选择，推荐房源仍为模拟数据。新对话按账号在本机独立保存；旧方案、LifeSim 与演示记录保留。[最新通勤寻房验证与边界](../docs/commute-search-2026-10-31/README.md) · [AI 模型配置](../docs/ai-search-2026-10-30/README.md) · [此前租房交付](../docs/rental-core-2026-10-28/result.md)。

## 工作目录与命令

需要 Node.js **22.12+**。从仓库根目录执行 `cd frontend`，之后在此目录运行：

```powershell
npm ci
npm run dev -- --port 5178
npm run build
npm test
npm run test:e2e
npm run android:sync
```

- 开发预览：`http://127.0.0.1:5178/`。使用 hash 路由，Android WebView 无需服务端路由回退。
- `npm run build`：TypeScript 检查与 Vite 生产构建，输出本目录的 `dist/`。
- `npm run android:sync`：先构建，再执行 `cap sync android`。前端修改后、Android Studio 运行前重新执行。
- `npm run android:open`：尝试启动 Android Studio。
- `npm run android:apk`：同步后执行 `assembleDebug`；成功时输出 `android/app/build/outputs/apk/debug/app-debug.apk`。
- 浏览器测试默认使用本机 Chrome，测试端口为 5178；依赖通过 `npm ci` 安装。运行完整浏览器测试前，先启动 backend 的 8080 服务；列表测试真实访问数据库接口。

## 房源列表联调

先在另一个终端按 [后端说明](../backend/README.md) 启动服务，再运行前端。浏览器入口为 `http://127.0.0.1:5178/#/listings`；生产预览 `npm run preview -- --port 4173` 使用相同代理规则。后端不可用时显示连接错误和重试，不会回退到六套旧示例。

默认 Web 请求 `/api`，由 Vite 代理到 `http://127.0.0.1:8080`；终端环境变量 `RENTPILOT_API_PROXY_TARGET` 可以覆盖代理目标。Vite 开发和预览代理仅用于本地验证，云端静态托管需配置同源反向代理或明确的 HTTPS API 地址。

Android 默认通过 CapacitorHttp 访问 `http://10.0.2.2:8080/api`，用于 Android 模拟器访问宿主机。原生 HTTP 不需要开启 WebView 混合内容。`app/src/debug/` 的网络配置只允许 10.0.2.2、127.0.0.1、localhost 的 HTTP；Release 没有该例外。真机和正式部署需要在 `.env.local` 中填写可达的 `VITE_API_BASE_URL=https://.../api`，再重新构建、同步和打包；不要把模型密钥或高德安全密钥放进 VITE 变量。配置示例见 `.env.example`。

列表按 20 条分页，支持区域、出租方式、卧室数、最高租金、地铁步行时间、排序及文本搜索；筛选在数据库执行。条件保留在 URL，变化时重置分页；旧请求和迟到结果不会覆盖新条件。空结果、首次失败、追加失败及异常响应分别处理。点击卡片进入完整详情，返回保留加载条数和滚动位置。未知设施不等于否，无坐标不编造点位；首期费用按源数据押金和付款方式计算，物业费周期不明会说明。

六张照片位于 `public/assets/catalog/`，按房型和编号稳定复用，仅为室内示意。列表和详情明确“示意图 / 非该房源实拍”。[摄影师、原始页面与许可记录](public/assets/catalog/README.md) 已保留，不依赖在线图库。数据库房源接入新的账户收藏、浏览历史和看房计划，旧六套示例不混用。

## 账户与个人资源

`/account` 登录，`/account?mode=register` 注册；用户名以字母开头、3–24 位，密码至少 8 个字符。成功后返回原操作页面。`/favorites` 管理收藏，`/recent` 查看或确认清空浏览记录，`/viewings` 管理待看/已取消/已看，`/profile` 查看实际统计、编辑昵称及退出。

共用 `src/services/http.ts` 处理原生与浏览器 HTTP、Bearer、204、错误和取消。`src/stores/member.ts` 在 `rentpilot-account-v1` 保存会话及资料，不保存密码；实际私有资源保存在后端并按账户隔离。401 清除失效会话，跨标签切换账户关闭旧确认框，迟到请求不覆盖新账户。同账户其他标签没有实时数据推送，重新进入或刷新会读取最新数据。退出不会删除后端记录。

看房使用上海日期，可选今天起 60 天，当天已过时段拒绝保存；支持修改时间、取消、标记已看。它是个人安排，不联系经纪人、不冒充预约成功。原 `rentpilot-demo-v1` 不清空，旧收藏/方案/计划不自动迁入账户。

## 高德地图找房

列表右上角“地图找房”进入 `/find-map`。高德 JS API 2.0 提供底图，地图移动、缩放和尺寸变化后查询 `GET /api/listings/map`，按屏幕空间聚合相邻点位；点击点位、左右滑动房源卡片、进入详情。地图和列表沿用筛选条件，隐藏的户型等条件会明确展示并可清除。API 每次最多 500 套，截断时页面提示放大或筛选；不生成缺失坐标的假点位。全部为模拟房源，小区坐标不代表真实在租。

在 `frontend/.env.local` 配置 `VITE_AMAP_WEB_KEY` 为高德 Web 端 Key，启动 Vite 前或 `android:sync` 前配置。它会随 JS/APK 发布，是公开客户端标识，不能当作服务端秘密；限制其授权域名和用途。安全密钥放在被忽略的 `backend/config/application-local.properties`，以 `rentpilot.amap.security-code` 配置，另以 `rentpilot.amap.web-key` 绑定同一 Web Key；均不要提交仓库。前端不包含安全密钥。后端必须在 `backend/` 目录启动才会读取该相对配置。

本机浏览器的 `/_AMapService` 由 Vite 代理到后端。Android Debug 内 `https://localhost/_AMapService/v3/log/init` 由原生 WebView 客户端仅对该路径转发到模拟器宿主机，未放宽全局混合内容，其他资源仍由 Capacitor 处理。Release 不含此调试转发；真机/发布使用可达 HTTPS 后端，在 `VITE_API_BASE_URL` 配置其 `/api` 地址并重新构建，安全代理也需由该 HTTPS 域名提供。公网部署前还需要网关限流和授权配置，见 [地图交付记录](../docs/map-integration-2026-10-28/result.md)。

## Android Studio

使用 **Open** 打开 **`D:\CODE\RentPilot\frontend\android`** 整个目录。Gradle JDK 选择 **JDK 21**，安装 **Android SDK Platform 36**，并确保 Gradle 8.14.3 与 Maven 依赖下载网络可用。之后执行 Sync Project with Gradle Files，连接设备或启动模拟器，再运行 `app`。

包名为 `com.rentpilot.app`，最低 API 24，目标/编译 API 36。SDK 本机路径保存在被忽略的 `android/local.properties`；不要提交该路径。可用的本机环境和此前构建限制见 [原生构建记录](../docs/android-build-result.md)。

本机 JBR 21 若出现 `Unable to establish loopback connection` 等长临时路径问题，在 `frontend/android` 创建 `.gradle/jvm-sockets`，并在当前终端设置 `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\CODE\RentPilot\frontend\android\.gradle\jvm-sockets'` 后执行 Gradle；不必修改系统全局变量。

## 页面与状态

| 页面/状态 | 路由或操作 |
| --- | --- |
| 默认首页 | `/` → `/listings`，普通租房列表 |
| AI 聊天首页 | `/chat` 未发送消息时，快捷填写、多行输入、本地图片预览 |
| AI 多轮对话、需求补充、房源推荐 | `/chat`，发送消息、快捷回复 |
| 规划生成中 | `/generating`，完成后点击查看方案 |
| AI 主动协商 | `/negotiate`，A/B 预览、确认生成版本 |
| 数据库房源列表 | `/listings`，搜索/筛选/分页/收藏/完整详情 |
| 高德地图找房 | `/find-map`，真实底图、模拟房源点位聚合、筛选、卡片与详情联动 |
| 账户 | `/account`，用户名密码登录/注册与返回原操作 |
| 收藏及浏览记录 | `/favorites`、`/recent`，后端分页/移除/清空 |
| 个人看房计划 | `/viewings`，待看/已取消/已看/改时间 |
| 地图预览 | `/map`，仍为六套示例的点位示意与旧筛选，不是 1500 条真实地图点位 |
| 居住方案列表 | `/plans`，标签、前后按钮或横向滑动 |
| 方案详情 | `/plans/commute`、`/plans/economy`、`/plans/quality` |
| LifeSim 总览与场景切换 | `/lifesim/commute`，场景、方案、工作日数量、可展开时间线 |
| 双方案对比 | `/compare`，方案选择、交换、取舍偏好与采用 |
| 个人中心 | `/profile`，账户资料、后端实际统计和资源入口；原示例中心移至 `/demo/profile` |
| 数据库房源详情 | `/listing/SH-MOCK-000001` 等，真实查询模拟数据、费用/设施/外部位置/收藏/个人计划 |
| 原样例房源详情 | `/listing/h1` 至 `/listing/h6`，照片放大、假设费用试算、本地看房计划 |
| 居住偏好权重 | `/preferences`，草稿、实时预览、确认应用 |
| 无匹配房源 | `/empty`，或地图选二居/低预算进入 |
| 历史方案与版本对比 | `/history`，查看历史快照、差异及确认恢复 |

UI 保留白色背景、黑色主按钮、浅灰卡片和底部悬浮胶囊导航。402×874 是设计参考；布局使用动态视口、安全区域、正常页面/聊天滚动。键盘出现时隐藏底部导航；原生 Activity 使用 `adjustResize`；Capacitor SystemBars 注入旧 WebView 所需的安全区变量。

## 数据和集成边界

- 房源列表实际查询后端数据库中的 1500 条**模拟房源**，不代表真实挂牌；照片为室内示意。AI 聊天使用真实模型提取需求，工作地通勤调用高德预计路线，明确显示部分候选的核验范围；旧方案、风险和 LifeSim 指标仍为本地 Mock 演示。
- 原有地图、聊天推荐、旧详情及方案使用 `src/data/mock.ts` 的 6 套示例房源和 3 套基础方案；前端没有复制或改写原始 CSV，1500 条数据由后端负责导入与查询。
- 新账户的收藏、浏览记录、昵称和个人看房计划保存在后端，重启保留。旧方案、收藏、权重、历史、归档对话、昵称、计划和场景仍在独立本机示例存储；损坏缓存回退默认值。没有真实推送或短信服务。
- `/find-map` 使用高德地图 JS SDK 与后端范围接口；坐标为 GCJ-02 小区参考，空坐标不生成点位。`/map` 仍是本地矢量示意与旧模拟点位，`src/services/map.ts` 保留旧示例适配入口。详情的外部高德 URI 打开小区参考位置，尚未接入实时定位；预计路线计算用于 AI 工作地找房，非独立导航功能。
- AI 输入图片仅本地预览，不上传、不参与推理。原样例详情的咨询/计划仍为本机演示；新数据库详情的计划保存至后端。费用说明不冒充合同报价；分享使用系统 Web Share/剪贴板能力。

## Stitch 资源

原 ZIP 保留在仓库根目录 `design-reference/`，解压参考位于 [`../design-reference/stitch_rentpilot_ai_home_ui/`](../design-reference/stitch_rentpilot_ai_home_ui/)。导出含 17 HTML，其中一个额外探索页未加入当前范围。`rentpilot_1`、`rentpilot_ai_1`、`rentpilot_ai_2` 的 `screen.png` 实为 28 字节下载失败信息；还原使用相应 HTML 和提供的总览截图。

15 张原图已全部本地化至 `public/assets/stitch/`，字体也随构建打包，运行时不需要 Tailwind、Google Fonts 或图片 CDN。来源和索引见 [资源报告](../docs/stitch-assets-report.json)。在 `frontend/` 中执行 `node scripts/import-stitch.mjs --offline` 可使用缓存离线重建索引；去掉 `--offline` 可按脚本逻辑重试原图下载。设计参考和报告仍位于仓库根目录的 `design-reference/`、`docs/`。

## 验证与限制

地图接入后的最新验证：后端 78 项、前端单元 26 项、浏览器全量 50 项通过；真实高德地图、Android 模拟器、生产构建、Capacitor 同步与 Debug APK 均已验证。详情见 [地图联调记录](../docs/map-integration-2026-10-28/result.md)。此前普通租房阶段的 49/22/49 项测试与 Android 账户流程证据保留在 [普通租房记录](../docs/rental-core-2026-10-28/result.md)；[房源列表联调](../docs/listing-integration-2026-10-28/result.md) 保留更早结果。

2026-10-28 前端 MVP 验证：9 项单元测试、34 项浏览器交互测试、生产构建、Capacitor 同步与 Gradle Debug APK 构建通过。Pixel 8 API 35 实测 10 个页面无横向溢出或缺图，多行输入、软键盘与原生返回关闭弹层正常，方案按钮完整显示在胶囊上方。最新结果见 [MVP 完成记录](../docs/ui-mvp-2026-10-28/result.md)；[10 月 27 日二次优化](../docs/ui-visual-revision-2026-10-27/result.md) 保留历史记录。

迁移后的结果以 [迁移验证记录](../docs/migration-verification.md) 为准。原有 [验证记录](../docs/verification.md)、[原生构建记录](../docs/android-build-result.md) 和 [`docs/screenshots/`](../docs/screenshots/) 保留历史证据。

10 月 24 日迁移时前端构建、5 项状态测试、6 项浏览器流程和 Capacitor 同步通过；当时原生依赖下载超时，尚未生成 APK。该历史限制已在 10 月 27 日的验证中解决。尚未进行实体 Android 设备测试。

### AI 找房继续核验

最新检索结果可继续核验，或在无匹配时预览建议的单项调整。确认后才发起调整检索；筛选预览不代表通勤路线已核验。检索令牌过期后可手动重新发送，按对话重新查找。续查不消耗模型对话条数；本机保存时压缩较早的续查记录，保留原始需求、确认调整、最近进度和待重试操作。第一步验证为单元 42 项、浏览器完整 71 项及文案精简后的专项 12 项；见 [闭环交付记录](../docs/search-closure-2026-10-31/README.md)，后续结果见下节。

### 推荐依据、费用与 AI 解读

推荐卡片下显示事实短理由，点击“推荐依据与费用”展开核验来源、月租、周期待确认的物业费、一次性押金和已知首期租金加押金。AI 解读仅在明确点击时请求，不把未知费用按零处理；错误和过期不影响查看事实。最新单元 47 项、完整浏览器 76 项及生产构建、Capacitor、Android Debug 通过，详见 [第二步交付记录](../docs/recommendation-evidence-2026-10-31/README.md)。

推荐卡片可展开查看资料依据和费用；物业费周期未明确时单独列示，首期金额仅包含已知首期租金和押金。AI 解读需主动点击，结果过期后需重新查找。

### 找房条件面板

工作区/街镇级联、自由地址、最低/最高租金、租赁方式和四种交通方式均可直接填写；不再固定张江高科、2500元下限或整租一居。“填入对话”先生成草稿，发送后查询；新对话/账号/历史切换会清空旧草稿。区域目录来自现有listing-filters，工作区域不自动用作住房筛选。本轮前端55项、完整浏览器85项及构建/同步/Android Debug通过，详见 [面板交付记录](../docs/search-conditions-2026-10-31/README.md)。

### 详情位置地图

房源详情位置区域内嵌高德地图，支持拖动、触屏缩放、放大/缩小和回中；位置按当前房源小区参考坐标展示，未知坐标不放置占位点。未新增依赖或后台接口，SDK复用既有安全代理。单元55项、完整浏览器87项及构建/同步/Debug APK通过；详见 [详情地图交付记录](../docs/listing-location-map-2026-10-31/README.md)。
