# 第一阶段：AI 结果保存为居住方案、持久化与对比

2026-10-31。按 [CodeArts 交接说明](../CODEARTS-交接说明-2026-10-31.md) 第 5 节第一阶段完成。原有 Vue 3 + TypeScript + Capacitor 前端、Spring Boot + Spring AI 后端、黑白灰界面和底部胶囊导航保持不变；未修改原始 CSV、设计资料、已有账户或数据库记录。

## 现在能做什么

1. 在 AI 找房结果（`status=MATCHES`）底部点“保存为方案”。只有当前结果中已展示的房源可以保存；未登录时入口变为登录链接。
2. 保存内容包含：房源 ID、当时条件、工作地、通勤方式与时间、核验覆盖（已核验/部分核验）、路线取数时间、数据集来源和哈希、限制说明。保存后快照固定，不随房源或路线数据变化自动更新。
3. “我的 → 我的方案”按账号列出方案，显示主房源月租、单程通勤（未核验时为“通勤未核验”）、房源数、保存时间。可查看详情、删除、进入对比。
4. 方案详情展示快照中的每套房源、费用（押金/付款方式/物业费/首期）、推荐依据、通勤核验范围、数据来源与限制，以及历史版本；旧版本可恢复为当前版本。
5. 方案对比选择两个已保存方案，比较月租、已确认首期、单程通勤、费用信息与核验范围；任一侧缺少数据时显示“无法比较/未知”，不补零、不推断。

## 数据模型

`V4__living_plans.sql`：

- `living_plan`：账号下的方案组，含标题和当前版本指针。
- `living_plan_version`：每次保存的快照，含 `snapshot_json`、主房源摘要列、覆盖范围和保存时间；`UNIQUE(plan_id, version_no)`。
- 删除方案级联删除版本；所有读写都带 `member_id`，跨账号返回 404。

快照 JSON 由后端从 AI 搜索票据生成（`AiSearchService.snapshot`），不是前端拼装：

- 只接受有效票据；过期或未知票据返回 `409 PLAN_SOURCE_EXPIRED`。
- 只接受票据 `presented` 中实际展示过的房源；其他 ID 返回 `400 PLAN_HOME_NOT_FOUND`。
- 保存时写入 `capturedAt`、`snapshotKind=HISTORICAL`、`realtime=false`、`dataKind=SIMULATED`。
- 非通勤筛选的方案额外补一条“本方案未核验工作地通勤时间”。

## 接口

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/me/plans` | 当前账号的方案列表（每组当前版本） |
| `POST /api/me/plans` | 从 `searchToken` 保存；带 `planId` 时追加新版本 |
| `GET /api/me/plans/{id}` | 方案详情，含当前快照与版本列表 |
| `GET /api/me/plans/{id}/versions` | 版本列表 |
| `PUT /api/me/plans/{id}/active` | 恢复指定版本 |
| `DELETE /api/me/plans/{id}` | 删除方案 |
| `GET /api/me/plans/compare?left=&right=` | 服务端计算事实差异与提示，不接受客户端数值 |

全部接口要求登录；对比同一方案返回 400，跨账号方案返回 404。

## 前端接入

- `src/services/plans.ts`：严格校验方案列表、快照、版本与对比结果；拒绝 `realtime=true`、来源不合法、费用与房源不一致的快照。
- `src/stores/plans.ts`：账号切换即清空；401 交给会员 store 处理；保存/恢复/删除/对比错误显式提示。
- `src/pages/PlansPage.vue`、`PlanDetailPage.vue`、`ComparePage.vue`：改为真实接口，移除旧 Mock 方案展示。未登录显示登录引导，无方案显示空态。
- `src/pages/ChatPage.vue`：新增“保存为方案”入口，保存最近一次已核验结果。
- 旧的 `/lifesim/:id` 仍为 Mock，本阶段未从方案详情提供入口，避免把模拟生活指标冒充方案事实；留待第二阶段接入。

## 本轮验证

| 检查 | 结果 |
| --- | --- |
| 后端全量测试 | **129 项通过**，0 失败、0 错误 |
| 新增方案 API 测试 | 5 项：未登录、票据过期、账号隔离、非法房源、版本追加/恢复/删除、对比与缺失值 |
| 前端单元测试 | **60 项通过**（新增方案服务 5 项） |
| TypeScript 与 Vite 生产构建 | 通过 |
| Capacitor Android 同步 | 通过 |
| Gradle `assembleDebug` | BUILD SUCCESSFUL，Debug APK 生成 |
| 独立 8081 内存库冒烟 | 健康 UP；未登录 `401`；空列表 `[]`；伪造票据 `409 PLAN_SOURCE_EXPIRED`；未知方案 `404`；同方案对比 `400` |
| 原始 CSV | SHA-256 仍为 `07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8` |
| 实体安卓设备 | 本轮未连接，未声称真机验收通过 |

冒烟使用独立内存库 `manual-plan`，结束后已停止 8081 进程，未触碰 `backend/data/` 和用户账户。

## 运行方式

```powershell
# 后端（本机已有 8080 服务时不要重复启动）
cd D:\CODE\RentPilot\backend
$env:JAVA_HOME='C:\Users\chenxiaohao\.jdks\jbr-21.0.11'
.\mvnw.cmd -s .mvn/settings.xml -B -ntp -o test
.\scripts\start.ps1

# 前端
cd D:\CODE\RentPilot\frontend
npm run dev -- --port 5178 --strictPort
```

打开 `http://127.0.0.1:5178/#/chat`，登录后完成一次 AI 找房，点“保存为方案”；再到“我的 → 我的方案”查看、对比或删除。

## 剩余问题与下一阶段

1. **真实 AI 保存链路尚未在本轮实测**：保存依赖真实搜索票据，自动化测试用 Mock 票据覆盖；需要在有模型与高德额度的时段走一次“真实搜索 → 保存 → 刷新 → 对比”，并记录实际结果。
2. **前端暂无“追加为新版本”入口**：后端已支持 `POST /api/me/plans` 带 `planId` 追加版本，详情页可查看和恢复；创建新版本的交互留待第二阶段与 LifeSim/条件调整联动。
3. **LifeSim 仍是 Mock**：未接入方案快照，不得在材料中写成真实生活指标。
4. **实体安卓设备、软键盘、返回键、断网**尚未验收；本轮只到 APK 编译。
5. **云端部署、华为云资源、CodeArts 使用记录**未在本阶段处理。