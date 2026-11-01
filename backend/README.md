# RentPilot 租房后端

Java 21 + Spring Boot **3.5.16** + Spring JDBC + Flyway。实现本地持久化数据库、1500 条模拟房源校验导入/查询，以及账户、收藏、浏览记录、个人看房计划和资料管理。普通租房页面已接通；本轮不开发大模型 Agent、工作地路线计算或管理后台。

## Windows 启动

在 `D:\CODE\RentPilot\backend` 执行，JDK 21 为必需项。下方 JAVA_HOME 是本机已验证的安装路径，其他机器替换为自己的 JDK：

```powershell
cd D:\CODE\RentPilot\backend
$env:JAVA_HOME = 'C:\Users\chenxiaohao\.jdks\jbr-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -s .mvn/settings.xml -B -ntp verify
.\scripts\start.ps1
```

`mvnw` / `mvnw.cmd` 为 Apache Maven 官方 Wrapper（固定 Maven 3.9.11），首次需要联网下载工具和依赖。也可用已安装 Maven 3.6.3+。`.mvn/settings.xml` 使用 HTTPS Maven Central，不改动用户全局设置。

默认地址 **http://127.0.0.1:8080/api/health**。按 Ctrl+C 停止。修改源码后使用 `./scripts/start.ps1 -Build`，先测试和重新打包再启动；`-Port 8081` 可换端口。

启动脚本为本机 Windows JBR 21 的长临时路径问题设置进程级 `jdk.net.unixdomain.tmpdir`，目录为 `backend/data/jvm-sockets/`；不修改系统全局变量。直接运行 JAR 若出现 `Unable to establish loopback connection / Invalid argument: connect`，请使用该脚本，或为该 Java 进程指定一个已存在的短路径临时目录。

默认 `local` 配置使用 `backend/data/rentpilot.mv.db`，重启保留数据，H2 控制台关闭。请从 backend 目录启动，数据库相对当前工作目录。打包后的 `target/rentpilot-backend-0.1.0.jar` 内含原 CSV，可单独复制运行，不依赖外部前端和 CSV 路径。

## 导入规则

构建时从 `../deliverables/sh_1500/上海1500条模拟房源.csv` 按原字节复制入 JAR。启动前校验 UTF-8 BOM、38 列、1500 条、编号唯一、数值范围、设施三态及成对坐标；完整校验后在数据库事务内导入。Flyway 负责结构迁移。

- 源 SHA-256：`07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8`。
- 批次 ID：`shanghai-simulation-v1`，记录哈希、数量和首次导入时间。
- 相同来源再次启动只验证，不重复导入；文件哈希或数据库数量不一致会明确失败，**不会自动删除或覆盖已有数据**。
- CSV 改版需要显式的数据迁移方案；不能编辑校验值来掩盖损坏。
- 可通过 `RENTPILOT_LISTINGS_CSV=file:D:/path/source.csv` 指定外部同版本文件，仍需通过相同校验。留空变量不会自动回退，请删除空变量。
- 14 条无坐标、423 条无地铁步行距离，保持 null。12 项设施为 `YES / NO / UNKNOWN`，未知不等于否。
- `COMMUNITY_REFERENCE` 仅表示原数据声明位置参考小区，未在本阶段重新核验。坐标为 GCJ-02、小区级参考；`UNVERIFIED` 不显示假点位。所有价格、户型、设施均为模拟，不能当作真实在租房源。

## 接口

| 方法与路径 | 返回 |
| --- | --- |
| `GET /api/health` | status 和实际导入批次摘要 |
| `GET /api/listings` | items、page、size、total、totalPages、dataKind、source |
| `GET /api/listings/map` | 地图可视范围内的 marker、total、returned、limit、truncated、bounds、dataKind、source |
| `GET /api/listings/{id}` | 完整模拟房源详情，保留原编号 |
| `GET /api/listing-filters` | 16 区、218 街镇及数量、出租方式、卧室数、地铁线路、来源 |

列表参数全部可省略，组合条件取交集：

| 参数 | 约定 |
| --- | --- |
| page / size | 页码从 0 起；默认 0 / 20；size 1–16 |
| q | 名称、行政区、街镇、小区、地址的普通文本匹配，最多 120 字符；百分号/下划线按字面匹配 |
| district / town | 中文完整名称，精确匹配；可从筛选项接口获取 |
| rentalType | ENTIRE（整租）/ SHARED（合租） |
| bedrooms | 原整套户型中的卧室数，1–10；合租的 3/4 室不表示独占多个卧室 |
| minRent / maxRent | CNY/月，包含上下限，非负、最多 2 位小数 |
| minArea / maxArea | 平方米，包含上下限，非负、最多 2 位小数 |
| maxMetroWalkMinutes | 到最近地铁站的步行时间上限，1–180 分钟；排除缺失值 |
| metroLine | 准确线路名称，如 `1号线`，不会混入 `11号线`；正确拆分 `1/12/13号线` |
| hasCoordinates | true / false |
| elevator / petsAllowed / privateBathroom | YES / NO / UNKNOWN，精确匹配 |
| sort | id（默认）、rentAsc、rentDesc、areaDesc、metroWalkAsc；相同值按编号排序；空步行时间置后 |

设施输出还包括 privateKitchen、gas、balcony、airConditioning、refrigerator、washingMachine、waterHeater、furnished、cookingAllowed。

### 地图找房

`GET /api/listings/map` 按当前地图可视矩形返回带坐标的模拟房源，坐标仍为 GCJ-02、小区级参考。`minLongitude`、`minLatitude`、`maxLongitude`、`maxLatitude` 必填，范围包含边界；经度限制 -180 至 180、纬度限制 -90 至 90，最多 6 位小数。其余筛选参数（q、district、town、rentalType、bedrooms、minRent、maxRent、minArea、maxArea、maxMetroWalkMinutes、metroLine、elevator、petsAllowed、privateBathroom、sort）与列表接口规则一致。

单次最多返回 500 个 marker，默认 200；可用 `limit` 调整。`total` 是范围内匹配总数，`truncated=true` 表示仅返回了前 `limit` 条。14 条无坐标房源不会出现在结果中，也不会生成占位点位；范围内没有房源时返回 200 和空数组。未知参数、重复参数、缺少范围、范围顺序错误或不合法坐标返回 400。返回的每个 marker 含编号、经纬度及卡片所需名称、区域、街镇、小区、地址、出租方式、户型、卧室数、租金、面积、最近地铁、线路和步行时间，并带 `dataKind=SIMULATED`、`coordinateSystem=GCJ-02`、`coordinateScope=COMMUNITY`。

详情中 `rent` 为月租，`deposit` 为押金，`paymentMethod` 保留“押一付三”等原文；`propertyFee` 保留原值，其周期原资料未明确，因此 `propertyFeePeriod=UNSPECIFIED`。`availability` 保留原资料日期或描述，并非实时在租状态。接口不返回虚构照片、客观评分或工作地通勤时间。每条与每页都含模拟数据标记和来源信息。

不存在的编号返回 404；无匹配结果返回 200 和空数组；非法/重复/空/未知参数返回 400，不会静默忽略。列表接口不接受 `commute=30`，工作地通勤筛选通过下方 `POST /api/ai/search` 完成。统一错误示例：

```json
{"code":"INVALID_QUERY","message":"查询条件无效","path":"/api/listings","fieldErrors":{"size":"必须为 1 至 100 的整数"}}
```

SQL 值全部参数化，排序固定白名单。数据库异常返回 503，不向客户端暴露 SQL 或堆栈。房源查询公开、无需登录；个人资源需要会话。服务默认绑定回环地址，**尚未做受控云端部署与公网安全加固**。

## 账户及个人资源 API

Flyway `V2__member_rental_core.sql` 新增账户、会话、收藏、历史和看房计划表，不改 V1 原房源和原 CSV。没有引入完整 Spring Security Web 或 JWT；仅使用 Spring Security Crypto 的 BCrypt。

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/auth/register` | `{username,password,nickname?}`，返回 201 与会话 |
| `POST /api/auth/login` | `{username,password}`，返回会话 |
| `POST /api/auth/logout` | 注销当前会话，204 |
| `GET /api/me` | 当前资料及收藏/浏览/待看计数 |
| `PATCH /api/me` | `{nickname}`，修改昵称 |
| `GET /api/me/favorite-ids` | 当前账户全部收藏编号 |
| `GET /api/me/favorites` | 收藏房源分页 |
| `PUT / DELETE /api/me/favorites/{listingId}` | 添加/移除收藏，204、幂等 |
| `GET /api/me/history` | 最近浏览分页 |
| `PUT /api/me/history/{listingId}` | 更新最近浏览时间，204 |
| `DELETE /api/me/history` | 清空当前账户历史，204 |
| `GET /api/me/viewings` | 看房计划分页，可用 status 筛选 |
| `POST /api/me/viewings` | `{listingId,date,slot}`，新增/更新同房源待看计划 |
| `PUT /api/me/viewings/{id}` | 修改待看计划时间，房源编号不变 |
| `PATCH /api/me/viewings/{id}/status` | `{status:"CANCELLED"}` 或 `COMPLETED` |

注册/登录响应包含 `token`、`expiresAt`、`profile`。私有请求加 `Authorization: Bearer <token>`，服务端由令牌确定用户，不能通过请求中的用户编号访问他人记录；他人计划编号返回 404。分页页码从 0 开始，默认 20 条、最多 50 条，返回 `{items,page,size,total,totalPages}`。

用户名字母开头、3–24 位字母/数字/下划线，不区分大小写；密码至少 8 个字符、最多 72 个 UTF-8 字节，BCrypt strength 12；昵称最多 16 个字符。会话为随机 32 字节不透明令牌，有效期 30 天，数据库只存 SHA-256，密码只存 BCrypt 哈希。不要在 URL、版本控制或联调截图放入密码/令牌。

计划日期使用上海时区，今天至 60 天后；时段为 `MORNING`（10:00）、`AFTERNOON`（14:00）、`EVENING`（18:30），当天已过时段拒绝。状态 `PLANNED / CANCELLED / COMPLETED`；结束后不能编辑，非法转换返回 409。同账户同房源只有一条待看计划；写操作对账户行加锁，避免并发重复。收藏和历史按用户+房源唯一。

个人计划**不代表房东/经纪人预约确认**。没有短信、找回密码、经纪人聊天、支付或合同接口。账号、会话和个人记录与原 CSV 同在本地文件数据库，重启保留；切勿删除数据库来“重置”用户数据。

## 验证和联调

```powershell
.\mvnw.cmd -s .mvn/settings.xml -B -ntp verify
# 在服务已启动时另开一个终端
.\scripts\verify-http.ps1
```

自动化共 78 项，包含真实 CSV + H2 + HTTP、认证与用户隔离、密码/会话哈希、失效会话、状态/日期和并发写入测试，不以 Mock 数据库替代查询；其中 25 项覆盖地图范围边界、无坐标排除、空结果、数量限制和非法参数，4 项覆盖高德代理配置和拒绝非法请求。原 HTTP 脚本遍历全部 15 页，检查 1500 个唯一编号、组合筛选、空值、错误和来源。`requests.http` 可在 IntelliJ HTTP Client 中逐个执行。[普通租房交付记录](../docs/rental-core-2026-10-28/result.md) 包含文件数据库重启持久化与原生 HTTP 证据。

### 高德 JS 安全代理

`/_AMapService/v3/log/init` 为高德 JS SDK 当前基础地图所需的服务端转发入口。仅允许该固定路径、固定高德 HTTPS 上游和已配置的 Web Key；禁止客户端传入 jscode，安全密钥只由服务端添加。并发、查询长度、响应大小、连接和读取空闲时间均有限制。无配置返回 503，错误 Key 返回 403。

将同一高德 Web Key 与其安全密钥分别配置为 `rentpilot.amap.web-key`、`rentpilot.amap.security-code`，例如写入被忽略的 `backend/config/application-local.properties`，不要提交或贴到日志。此代理只满足本机地图基础功能，不代表可直接暴露公网：正式部署还需 HTTPS、网关限流、配额和授权限制，以及实际部署环境验证。读取超时目前限制单次空闲等待，不保证整个响应的总时长。

浏览器可直接打开：

- http://127.0.0.1:8080/api/listings?maxRent=3000&rentalType=ENTIRE&bedrooms=1&sort=rentAsc
- http://127.0.0.1:8080/api/listings/SH-MOCK-000201
- http://127.0.0.1:8080/api/listing-filters

Android Debug 已通过原生 HTTP 使用 `10.0.2.2:8080` 访问宿主机，并实测查询这 1500 条模拟数据；真机与 Release 需配置可达的 HTTPS 地址。Web 开发/预览通过 Vite 同源代理接入。CORS 白名单支持现有本地预览及 Capacitor localhost 来源，可通过 `RENTPILOT_CORS_ORIGINS` 以逗号分隔覆盖。详见 [前端联调说明](../frontend/README.md)。

## PostgreSQL 预留

本阶段 Docker Engine 不可用，尚未使用真实 PostgreSQL 或华为云 RDS。驱动、Flyway PostgreSQL 模块和独立配置已提供。后续在专用数据库执行相同测试，再确认服务产品、版本、SSL 和网络配置。

```powershell
$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:RENTPILOT_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/rentpilot'
$env:RENTPILOT_DB_USERNAME = 'rentpilot'
# RENTPILOT_DB_PASSWORD 在本机安全设置，不提交或写进命令示例。
.\scripts\start.ps1
```

不要在当前本地库运行时随意切换配置。账号密码、数据库文件和构建产物已加入忽略规则。Spring AI 寻房现已接入；模型服务需要单独配置，见下一节。

### AI 寻房配置

在本地未提交的 `config/application-local.properties` 配置专用服务凭据：

```properties
rentpilot.ai.base-url=https://your-provider.example
rentpilot.ai.completions-path=/v1/chat/completions
rentpilot.ai.model=your-model
rentpilot.ai.api-key=your-app-key
```

只在服务端配置密钥，不要提交或传给浏览器。没有配置时房源目录仍可启动，AI 接口返回 `AI_NOT_CONFIGURED`；不会自动读取其他应用的 API 密钥。服务采用 OpenAI 兼容聊天接口，请按供应商要求设置 base URL、路径和模型。每次请求最多 20 条消息，每条最多 2000 字，总计最多 12000 字；仅用户消息参与条件提取。房源是模拟数据，地铁步行时间不能代表工作地通勤时间。

工作地通勤检索需要服务端配置 `rentpilot.amap.web-service-key`。路线以社区坐标至工作地点的高德预计耗时为依据，非门到门或高峰保证。默认每批核验最近的 12 个不同坐标及其全部匹配房源，可用 `rentpilot.commute.max-candidates` 设置每批坐标数（限制为 1–16）；响应会标明累计核验、未核验数量和覆盖范围。未命中缓存的路线请求启动间隔为 500ms，以避免短时高频触发供应商限流；缓存命中无需等待。自动化测试使用模拟网关，不访问真实高德服务。

`POST /api/ai/search` 可在用户明确选择候选工作地时附带 `workplaceId`；服务端重新查询并验证上海地点。后续修改交通方式或预算时，可用 `previousWorkplaceId` 传入当前对话已确认的地点作为提取上下文；它不等同于本轮明确选择，用户更换工作地后仍重新定位。失败重试保留原请求的地点上下文，新对话不沿用旧值。响应的 `workplaceCandidates` 表示需要选择，`commute` 包含核验数量、未核验数量和覆盖情况；推荐中的 `commute` 提供路线秒数及预计分钟数。`total` 仅计本轮已核验且满足通勤上限的房源。相同小区参考坐标共用一次路线请求，核验数量按房源计。未指定交通方式时需要用户补充；公共交通包括公交和地铁。所有路线信息均为估算。最新实测与边界见 [通勤寻房记录](../docs/commute-search-2026-10-31/README.md)。

服务端复用已确认地点时同时核对名称和提取出的区、镇信息；显式区域冲突时重新定位，同名地点不能直接沿用旧坐标。

AI 搜索将进度保存在进程内存中，最多 128 个快照，有效期 10 分钟；重启后需重新搜索。`POST /api/ai/search` 可提交 `action`：`CONTINUE` 继续核验，`APPLY_ADJUSTMENT` 需携带 `adjustmentId` 确认建议，两者均需 `token`。令牌过期返回 HTTP 409 和 `AI_SEARCH_EXPIRED`。路线按不同坐标分批核验，结果累计；`FILTER_ONLY` 预览只代表数据筛选，`VERIFIED_ROUTE` 预览来自已核验路线。

无匹配的普通筛选也支持预算预览与确认；调整从服务端快照恢复，客户端不能提交任意字段补丁。重复操作幂等，部分路线失败保留待继续核验，全部失败返回服务错误。完整接口行为与最新验证见 [AI 找房闭环记录](../docs/search-closure-2026-10-31/README.md)。


### 推荐依据与按需解读

`POST /api/ai/search` 的推荐包含服务端计算的依据和费用。物业费周期未知，不折算月费；首期金额仅包含明确付款周期的租金与押金，不包含水电网络等费用。通勤为高德基于小区坐标的路线预计值。普通筛选按月租排序，通勤结果按预计时长和月租排序，部分核验会标明覆盖情况。

用户请求解读时，`POST /api/ai/explain` 接收 `searchToken` 和 `listingId`，只允许当前搜索实际展示的房源。模型从服务端提供的证据中选择 ID，服务端验证后组装文字；不接受模型生成的自由事实。解读成功按票据和房源缓存，失败返回 `AI_EXPLANATION_UNAVAILABLE`，过期票据返回 `AI_SEARCH_EXPIRED`。使用现有 `rentpilot.ai` 服务端配置，无需向客户端提供密钥。

解读响应严格拒绝额外事实字段、未知/重复证据、重复 JSON 键和尾随内容。外部调用不持有全局搜索票据锁；续查也按单票据隔离，慢路线不阻塞无关普通查询的结果保存。最新完整测试 124 项通过，配置、真实验证及能力边界见 [推荐依据交付记录](../docs/recommendation-evidence-2026-10-31/README.md)。
