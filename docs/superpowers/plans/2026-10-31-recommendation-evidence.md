# 第二步：推荐依据、费用和受约束的 AI 解读

用户批准上轮提出的四项设计并要求继续，沿用现有聊天推荐流程、黑白灰和胶囊导航。Vue/TS/Capacitor/Spring AI 均保留，代码代理只用 gpt-6.1-terra。保持第一步的核验续查、明确确认、隔离和限频；不修改 CSV、既有账户数据或数据库结构，不接方案/LifeSim。

## 数据与界面设计

- 后端核算证据和金额，推荐下默认至多三条短理由；展开查看完整依据、费用和排序。
- 满足预算只针对月租，不将周期未知物业费、水电或网络费用按零加入所谓总成本。押金单列一次性项目。
- 付款周期只解析当前目录明确支持的“押一付一”（1个月）、“押一付三”“押二付三”（3个月）；不根据“押几”重算押金。未知付款方式不计算首期合计。首期标签限定“首期租金 + 押金”，不冒充入住总费用。
- 通勤为高德小区坐标预计值，以秒数判定，显示向上取整分钟；未计算时不声称匹配通勤。设施 UNKNOWN 保留未知。
- 通勤推荐按预计秒数、月租、编号排序；普通 AI 筛选按月租、编号排序。标明实际排序规则及部分覆盖，不给虚构评分。
- 用户展开后可点击“AI 解读”；模型只选择已提供证据 ID（最多3个），服务端以选定证据的可信文字组装简短解读，模型不能生成租金、设施或房源事实。失败显示可重试错误，事实和费用仍可查看，不冒充成功。

## 冻结接口

现有 POST /api/ai/search 可选新增字段，旧字段兼容：

```ts
type Evidence = {
  id: string; label: string; text: string
  status: 'MATCHED' | 'UNVERIFIED'
  source: 'LISTING' | 'AMAP'
  fields: string[]
}
type Costs = {
  monthlyRent: number; deposit: number; paymentMethod: string
  propertyFee: number | null; propertyFeePeriod: 'UNSPECIFIED'
  prepaidMonths: number | null
  firstRentPayment: number | null
  knownInitialPayment: number | null
  excludedItems: string[]
}
type Insight = { evidence: Evidence[]; costs: Costs }
// 每个 recommendation 新增 insight?: Insight | null；reasons 保留，改为事实短句。
// 顶层新增 sortRule?: { key:'COMMUTE_RENT_ID'|'RENT_ID'; label:string; partial:boolean } | null
```

新增 `POST /api/ai/explain`：`{searchToken:string, listingId:string}`。
成功响应：`{listingId:string, summary:string, evidenceIds:string[], engine:'SPRING_AI', dataKind:'SIMULATED'}`。
必须从搜索快照里恢复当前展示的推荐及证据，不信任客户端传来的金额/文字。仅允许该票据已展示的最多六个房源。过期409 AI_SEARCH_EXPIRED；未展示的编号400 INVALID_QUERY；模型错误503 AI_EXPLANATION_UNAVAILABLE。返回证据 ID 必须全部属于该房源证据且不重复，至少一个 MATCHED，数量1–3；其他输出/自由文案拒绝。成功缓存只绑定当前票据和房源，不跨条件复用。

不持有全局搜索票据锁执行网络调用；未点击不调用解读模型。使用原 Spring AI 客户端/服务端专用配置与受限超时，不引入 SDK 或模型密钥到前端。

## 任务、测试与验收

Backend：添加事实/费用辅助类和测试，AiSearchService 组装普通筛选及续查结果、票据记录实际展示推荐、sortRule，新增 explain 接口及受约束模型调用。先RED后GREEN测试：预算差额、零押金、三种付款方式/未知付款、未知物业费周期不摊月、未知设施、900/901秒、排序及部分覆盖、模型非法证据/编造字段/失败、令牌过期/非推荐编号、缓存与条件隔离。保持全量已有测试。

Frontend：services/ai.ts 可选字段校验和 explain 方法，新增聚焦组件供 ChatPage 使用。展开/收起、费用独立栏目、AI解读加载/取消/失败重试和当前上下文隔离；保留旧响应显示。默认短理由不重复通勤或价格，展开详情不展示字段路径等实现信息。320/402/768截图、44px点击区域、滚动无溢出/导航遮挡，使用 design-taste-frontend App 规则。

Root：合同对齐、独立审查、完整隔离测试、真实模型和高德查询及解读验证、构建/Capacitor/Gradle、原CSV校验、中文交付记录。正常账户库不用于自动化测试写入。

## 台账

- Ruling：用户已批准简短设计并要求继续，此为既有流程的有界扩展，直接执行，不重复请求开发许可。
- Ruling：模型仅选择证据重点，文字和数字从后端证据生成；这是实际模型参与的受约束解读，不宣传为不受约束的独立事实推理。代价是文案表达范围较小，换取可验证性。
- Backend complete：事实/费用辅助类、排序、普通/通勤/续查/调整推荐、按需 explain、严格 JSON 证据白名单、票据隔离缓存均接入。
- Frontend complete：可选字段兼容、费用按分核对、展开/收起、按需加载/取消/重试/过期、迟到响应和账户/查询隔离；修复展开按钮 `.toggle` 与全局通知开关样式冲突。
- Final fixed：续查原全局锁阻塞无关搜索及解读；缩小为全局查找+单票据执行。blockedContinuationDoesNotBlockOtherTickets 在恢复旧锁时超时失败，恢复修复后通过。
- Verification：后端完整 124/124 与 package、前端单元 47/47、隔离完整浏览器 76/76、TypeScript/Vite/Capacitor/Gradle 通过。真实模型选择预算/通勤等证据，预算从3000改2800后差额从600变400，首期租金7200+源押金2400=9600，普通查询排序验证通过。
- Ruling：taste 自动覆盖规则对内部滚动有已知误报，生产专项延续显式 covered-text 豁免，实际三宽度截图和边界检查另行补验；不宣称无豁免自动全通过。
- 原 CSV SHA-256 不变。没有实体安卓设备连接，不宣称真机验收。完整交付：[推荐依据记录](../../recommendation-evidence-2026-10-31/README.md)。
