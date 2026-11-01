# LifeSim 接入真实方案（2026-10-31 晚）

## 本轮完成

LifeSim 从 Mock 生活模拟改为读取真实方案快照的「生活账本」，并接上时间 + 消费问答，随方案版本保存。

### 后端

- 沿用 `V5__living_plan_lifesim.sql`，在 `living_plan_version` 增加 `lifesim_json`；问答随当前版本保存，恢复旧版本时读取旧版本答案。
- 新增 `LifesimModels`、`LifesimService`、`LifesimController`：
  - `GET /api/me/plans/{id}/lifesim`
  - `PUT /api/me/plans/{id}/lifesim`
- 所有计算在服务端完成，前端只提交答案，不接受客户端算好的时间或金额。
- 来源分五类：`FACT`（已核验）、`USER`（你填的）、`DERIVED`（系统推算）、`ASSUMED`（固定假设）、`UNKNOWN`（未知）。
- 未核验通勤时返回 `NEEDS_COMMUTE`，不生成时间轴；跳过项保持 `UNKNOWN`，不填默认金额。
- 费用只汇总已知项：月租、周期明确的物业费、用户填的固定支出、用户选择的日常开销区间；首期单列，不摊薄。

### 前端

- `services/lifesim.ts`：严格校验返回结构，拒绝非法来源标记和缺失字段。
- `stores/lifesim.ts`：按登录态加载/保存，401 交给会员 store。
- `pages/LifeSimPage.vue` 重写：
  - 真实方案选择器；
  - 两屏问答（时间 4 题、消费 3 题），每题可跳过；
  - 时间轴、月度开销区间、来源图例、限制说明；
  - 视觉沿用黑白灰，增加时间轴、深色开销卡与状态标签。
- 方案详情底部新增「看看你的一天」入口；对比页新增两个方案的 LifeSim 入口。
- 移除「我的 → 探索功能演示」入口，切断 `/demo/profile` 的主路径暴露；`/demo/profile`、`/history`、`/preferences` 不再从正常演示路径可达。
- Chat 结果页：保存按钮前置到结果摘要附近；「部分核验」提示改为可读提示条。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 后端全量测试 | **134 项通过**，0 失败 |
| 新增 `LifesimApiTest` | 5 项：未登录、跨账号、空答案、服务端重算、跳过与未核验通勤 |
| 前端全量测试 | **64 项通过**（新增 LifeSim 服务 4 项） |
| TypeScript + Vite 构建 | 通过 |
| Capacitor Android 同步 | 通过 |
| Gradle `assembleDebug` | BUILD SUCCESSFUL，APK 已更新 |
| 原始 CSV | 未修改，SHA-256 仍为 `07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8` |

## 运行方式

```powershell
# 后端（先停止旧的 8080 进程，否则仍在跑旧代码）
cd D:\CODE\RentPilot\backend
$env:JAVA_HOME='C:\Users\chenxiaohao\.jdks\jbr-21.0.11'
.\scripts\start.ps1

# 前端
cd D:\CODE\RentPilot\frontend
npm run dev -- --port 5178 --strictPort
```

路径：登录 → AI 找房 → 保存方案 → 我的方案 → 方案详情 →「看看你的一天」→ 填时间与消费 → 生成。

## 剩余问题

1. **真实模型/高德票据下的完整链路未实测**：本轮测试用 Mock 票据；需要在有额度时走一次「真实搜索 → 保存 → LifeSim → 对比」。
2. 消费只做区间，不估算做饭、水电、外卖金额；这是有意为之，避免编数据。
3. 尚未做多场景切换（工作日/加班/周末），目前只有单日时间轴 + 周加班天数。
4. 实体安卓设备未连接，验证到 APK 编译为止。
5. 初赛表、10 例量化、截图与录屏尚未更新。