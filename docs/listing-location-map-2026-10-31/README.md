# 房源详情位置地图交付记录

本轮只完善房源详情的「位置与日常出行」，未推进方案保存、对比等后续工作。

## 已完成

- 详情内嵌真实高德 JSAPI 2.0 彩色地图，使用当前房源的 GCJ-02 坐标，显示小区名称标记。
- 支持鼠标/手指拖动、触屏缩放、放大/缩小，以及「回到房源」。滚轮缩放关闭，减少详情页滚动干扰。
- 260px 高度适应不同屏宽，保留地址、地铁信息和「高德查看」外链。控制按钮均为44px。
- SDK加载中、失败重试、位置待核验均有状态；加载失败不阻断其他详情操作。
- 切换房源/离开页面销毁旧地图，迟到的SDK结果不会创建旧房源地图。加载器与现有地图找房共用，保留后端安全代理。
- 使用 taste-skill 的 App 规则，保持既有配色、字体和页面结构；未重做详情其他区域、导航或联系流程。

## 验证

| 检查 | 结果 |
| --- | --- |
| 前端单元测试 | 55项通过 |
| 完整浏览器回归 | 87项通过；独立8081内存数据库、5182测试前端 |
| TypeScript/Vite生产构建 | 通过 |
| Capacitor Android同步 | 通过 |
| Gradle assembleDebug | 最终 BUILD SUCCESSFUL |
| taste源码lint | 0 FAIL / 0 WARN |
| taste生产版App inspector | 320、402、768宽：0 FAIL / 0 WARN |
| 独立代码评审与样式修订复核 | 无P1/P2问题 |
| 实体Android设备 | adb未发现设备，本轮未做实体设备验证 |

真实高德服务验证不是fake SDK测试：在房源 `SH-MOCK-001248`，拖动后标记随地图移动161px，回中恢复原位置；生产版320/402/768宽分别模拟触屏拖动，标记移动128/123/95px，回中成功，无横向溢出，三个控件均可见且不小于44px。保留高德版权信息。

浏览器测试首次82通过、5项注册失败，查明是临时测试后端未允许5182来源，返回403 Invalid CORS request；只调整临时进程环境后完整重跑87项通过，未改正式配置或账号业务代码。Gradle首次遇到本机loopback临时路径问题，使用既有短路径JVM设置后通过。最终安装包包含最后一次样式修订。

## 截图及记录

- [修改前402](before-402.png)
- [最终320](after-320.png)、[最终402](after-402.png)、[最终768](after-768.png)
- [真实鼠标拖动记录](live-results.json)、[生产版触屏与适配记录](production-responsive-results.json)
- taste完整截图和测量位于 `frontend/.taste-skill/listing-map-before/`、`frontend/.taste-skill/listing-map-after/`。最终截图逐张查看；另以正常874px手机视口检查位置区域。

## 视觉复核

本次是单个区域的增量实现，沿用项目方向，无新方向抽取；分数为内部主观自评，不代表赛事或外部评审结果。

ROUND 1
Seen in one second: 地址和地铁卡片，详情内没有可查看的位置地图。
Idea (one sentence): 把位置证据直接放在房源地址下，减少跳转。
Live thing: 高德外链。
Layout words by section: 位置=地址/地铁卡片；设施=网格；费用=列表。
Scores: design 6.5 · usability 6 · creativity 6 · content 6 → overall 6.2
Caps applied: 沿用既有小号辅助字和普通租房布局，非整页改版。
Top three problems (most visible first), each with the screenshot it shows in and the fix:
1. before-402.png：没有地图，无法直接看周边；加入内嵌地图。
2. before-320.png：只能跳转外部；加入拖动、缩放、回中控件。
3. before-768.png：位置数据精度容易被误解；地图内保留小区参考标注，缺失坐标不猜位置。

ROUND 2
Seen in one second: 地址下的彩色街区地图和当前小区标记。
Idea (one sentence): 直接在详情中理解房源周边位置，同时诚实展示坐标精度。
Live thing: 地图拖动/缩放/回中及失败重试。
Layout words by section: 位置=地址/地图/地铁；设施=网格；费用=列表。
Scores: design 6.5 · usability 8.5 · creativity 6.5 · content 8 → overall 7.3
Caps applied: 保留既有详情辅助字体及常规租房模式，不追求整页视觉重设计。
Top three problems (most visible first), each with the screenshot it shows in and the fix:
1. after-320.png：长地址需要换行；现有换行完整可读，无横溢出，保留。
2. after-402.png：说明仅保持一句坐标边界和既有地铁说明，避免再加操作提示。
3. after-768.png：房源坐标仍为模拟小区参考；无法通过UI提升为楼栋精确坐标，需后续合法来源的数据支持。

## 边界和运行

地图是真实高德服务，房源仍为模拟数据；当前位置只到小区参考级别，不能声称是实际在租房屋的精确门牌/楼栋。未新增定位、地理编码、后台接口或新依赖。14条无坐标房源不生成标记。

未修改原始CSV、后端源码或用户账户库。CSV SHA-256保持 `07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8`。

前端开发继续使用 `frontend/` 中的 `npm run dev -- --port 5178`；刷新详情页即可查看。Android Studio打开 `D:\CODE\RentPilot\frontend\android`，最新Debug安装包位于 `frontend/android/app/build/outputs/apk/debug/app-debug.apk`。高德交互依据 [官方交互文档](https://lbs.amap.com/api/javascript-api-v2/guide/map/map-bind) 与 [地图状态文档](https://lbs.amap.com/api/javascript-api-v2/guide/map/state)。
