# RentPilot 工作地与路线寻房实现计划

**Goal / approved scope:** 用户已明确批准继续工作地定位和真实路线预计通勤筛选。沿用 Vue3/TS/Capacitor、Spring AI、既有高德 Web 服务 Key；仅 Terra 子代理开发。保持黑白灰/胶囊导航/现有账号数据/原始CSV。无数据库迁移。

**Architecture:** 模型提取住房硬条件、工作地点、通勤方式和上限 -> 用户补充未指定交通方式/选择歧义地点 -> 高德 POI 定位 -> 原有数据库按硬条件查询 -> 附近候选路线核验 -> 按返回秒数筛选和排序。不能用直线距离或地铁步行值替代路线耗时。

## Shared gateway interfaces (provider task owns these)

Package `com.rentpilot.commute`:
- `public enum TravelMode { WALKING, CYCLING, TRANSIT, DRIVING }`
- `public record Workplace(String id,String name,String address,String district,String adcode,double longitude,double latitude)`
- `public record RouteEstimate(long durationSeconds,long distanceMeters,java.time.Instant checkedAt)`
- `public interface CommuteGateway { java.util.List<Workplace> search(String query,String district); java.util.Optional<Workplace> detail(String id); java.util.Optional<RouteEstimate> route(double originLongitude,double originLatitude,Workplace destination,TravelMode mode); }`
- `public class CommuteException extends RuntimeException` with `String code()`; sanitized codes `AMAP_NOT_CONFIGURED`, `AMAP_UNAVAILABLE`, `AMAP_INVALID_RESPONSE`. Empty route Optional means valid provider reply/no route; HTTP/provider error must throw, never map to0 seconds.

Provider bean `AmapCommuteGateway` uses `rentpilot.amap.web-service-key` and configurable server-only `rentpilot.amap.service-base-url` default `https://restapi.amap.com` (loopback fixture override). Search `/v3/place/text`, Shanghai city310000/citylimit=true/extensions=all, result<=5; details `/v3/place/detail` extensions=all, only Shanghai adcode31 validated. Route WALKING `/v3/direction/walking`, CYCLING `/v4/direction/bicycling`, TRANSIT `/v3/direction/transit/integrated` city/cityd上海 strategy0, DRIVING `/v3/direction/driving`. Parse exact documented per-mode fields and strict seconds. Fixed endpoints, no redirects, bounded response/timeouts, no keys or raw URLs/errors in responses/logs. Bounded POI/route TTL caches, no fake fallback.

## API additions (workflow task owns implementation)

Existing POST `/api/ai/search` request adds optional `workplaceId:string` for an explicit current-turn selection. UI sends selection id only on choice/retry (not blindly reused after workplace changes). Backend details by id and Shanghai validation, never trusts client coordinates. Text selection includes selected name/address so future conversation refers to it.

Existing response fields unchanged, with optional:
- `workplaceCandidates: Workplace[]` when ambiguous (buttons show name/address, no raw ids).
- `commute: {state:'NEEDS_MODE'|'NEEDS_WORKPLACE'|'CHECKED',mode?:TravelMode,maxMinutes?:number,workplace?:Workplace,checkedCount:number,candidateCount:number,unverifiedCount:number,coverage:'COMPLETE'|'PARTIAL',estimated:true}`.
- Each `recommendations[]` adds optional `commute:{mode:TravelMode,durationSeconds:number,minutes:number,distanceMeters:number,source:'AMAP',estimated:true,checkedAt:string}`.

Check transport mode first when absent; UI offers 步行/骑行/公共交通/驾车. Never choose default for ambiguous15min. Public transit includes bus/metro; hard subway-only or rush-hour guarantees remain unsupported and must be explicit. Model `unsupported` represents OTHER unsupported hard requirements; a supported work commute no longer sets it. Extract `commuteMode` enum; keep prior confirmations compatible. Full-message context must support brief choices, changes and negation; no repeated confirmations.

Ambiguous POIs require a user choice; auto-select only a unique exact normalized name or one valid result, never a random first ambiguous campus. With selected id use details, preserve budget/type/bedrooms. Missing workplace/no Shanghai result => contextual NEEDS_INPUT, not wrong housing-area message.

Candidate workflow reads all base housing matches via existing repository pagination (max100 per page); missing coordinates unverified. Straight-line distance sorts candidates only. Default cap12 nearest candidates per request (configurable bounded), fixed bounded executor with total route phase<=16sec, max4 concurrent calls; no unbounded task queue. Gateway cached coordinates reuse. Compare durationSeconds<=maxMinutes*60 (900 qualifies,901 fails15), display ceil minutes. Never silently widen constraints. Sort verified matches by duration, rent, id and return<=6.

`total` counts verified matching candidates in this round, not unverified global matches. `candidateCount` counts base matches, `checkedCount` valid route/no-route checks, `unverifiedCount` includes unprocessed/missing-coordinates/errors. Partial/no-match messages must say current checked subset; don't assert whole catalog has no matches. All provider failures => AMAP_UNAVAILABLE; never NO_MATCH. Routes reference community GCJ02 point to workplace POI and are estimates, not guaranteed door-to-door or peak traffic.

## Work packages and verification

1. Provider interfaces/client and HTTP fixtures independently, tests all4 routeformats, malformed values, no routes,401/quota/timeout, cache, city validation and secret sanitization.
2. Workflow/extraction/AI tests integrate gateway, preserve previous filter-only and short-confirmation flow; tests budget+workplace+mode, candidate ambiguity/change,900/901 boundaries, unknown coordinates, partial coverage, allfailures, no fake minutes. Backend existing AI fixture contexts explicitly disable real AMAP key or mock gateway; never hit external provider automatically.
3. Frontend typed validation/store/ChatPage additions with existing taste app styling, inline choices and route summaries. Optional per-request timeout60sec native/web. Selection/retry/account-change safe. Fixtures test missingmode, multiplePOIs, typedordinal/button, actual card estimate, partial/unavailable, no overflow320/402/768.
4. Root verifies official API permissions/live smoke (already confirmed all4 routes work), complete test suites on isolated DB, real model+AMap scenario cycling15min vs walking15min, Androidsync/build. No source CSV edits or existing-user-data test writes.

## 2026-10-31 验证台账

- 包 1–3 的实现已存在，恢复执行时检查实际文件后继续验证，没有重新生成页面或替换有效源码。
- 补充接口 `previousWorkplaceId`：本轮明确地点选择仍用 `workplaceId`；上次已确认地点只作为模型提取上下文，提取的新地点不一致时重新定位。前端在当前对话中查找已确认地点，并在失败重试时保留原上下文。
- 同坐标候选按精确经纬度分组后调用路线网关，统计仍按房源计；覆盖三套同坐标只调用一次且 checkedCount=3 的回归。
- 后端完整 package：107 项通过；前端单元：37 项通过；浏览器完整：67 项通过。浏览器测试使用 8081 临时内存数据库和 5182 前端，正常 8080 账户库不用于测试写入。
- 真实模型与高德浏览器查询：吴泾工作地选择后骑行 15 分钟内 3 套；切换步行保留地点和住房条件、返回已核验子集无匹配；交大多校区需选择，选择后正确查询。
- TypeScript/Vite、Capacitor sync、Gradle assembleDebug 均通过；本轮无 Android 设备连接。
- 原 CSV SHA-256 不变。完整证据、运行方式和剩余边界见 [交付记录](../../commute-search-2026-10-31/README.md)。
- Final review: Terra 只读审查发现同名地点跨区可能沿用旧坐标；跨区与换镇回归先在旧实现下失败，补充名称、区域校验和街镇查询上下文后 GREEN，定向 16/16、完整 107/107。
- Ruling: 任意歧义自然语言与外部供应商行为不作无限覆盖承诺，以确定性接口测试和真实场景实测范围交付；剩余成本是未测输入仍可能需要用户选择具体地点或重试，不能宣传为已完成全部真实租赁/AI 能力。
