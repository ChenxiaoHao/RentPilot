# 房源详情内嵌位置地图：实施要求

这是现有详情页位置区域的局部功能，用户已授权直接实施，不另做页面改版。保留 Vue3 + TypeScript + Capacitor、黑白灰和现有详情/导航/联系行为。

## 唯一任务

在 `/listing/:id` 的真实目录详情 `frontend/src/pages/CatalogDetailPage.vue` 的「位置与日常出行」加入真实高德 JSAPI 2.0 地图，默认中心是该条房源的坐标，明显标记该小区。允许鼠标/手指拖动、双指缩放，提供44px以上的放大、缩小、回到房源按钮；桌面滚轮不要导致详情滚动陷阱。地图约240–280px高，适应320/402/768宽度，既有地址、地铁和高德外链保持可用。

数据只有 GCJ-02 小区级参考坐标，不是精确楼栋。界面保留一句「模拟房源 · 小区位置参考」，不可写成真实房源/楼栋准确定位。经纬度缺失、非有限/越界或 locationStatus=UNVERIFIED 不显示虚构点，显示位置待核验。不要自动地理编码、浏览器定位、读取用户当前位置或新增后台接口。

## 结构与边界

- 新建聚焦的 `ListingLocationMap.vue`，可增加小型 `services/amap-sdk.ts`，检查并复用当前 CatalogMapPage 的密钥和 `/_AMapService` 安全代理策略，安全密钥只在后端。不得读取/打印任何密钥。
- 如提取共用SDK加载器，CatalogMapPage只替换加载调用及类型，保持现有六点地图查询、浮卡、路由不变。并发去重，SDK失败后可重试，避免组件销毁后创建地图和重复资源泄漏。
- 显式加载/失败/重试状态，地图失败不阻断详情/联系，保留高德外链；路由切换清除旧地图和标记，异步迟到不恢复旧地图；卸载destroy。
- 可以使用AMap.Marker或投影标记，标记和可视标签必须留在真实坐标位置，拖动地图时标记不能固定在画面中心冒充坐标。
- 本次只改必要前端文件和测试。禁止后台/CSV/用户数据/本地配置改动，禁止新技术栈/产品依赖。不要启动新服务、不要执行全量账号测试（root另开隔离后端），不要提交git（这里没有git仓库）。

## 设计与测试

使用 `C:/Users/chenxiaohao/.codex/skills/design-taste-frontend/SKILL.md` 及 app-screens规则；这是单一区域，匹配周边设计，不重抽方向。地图保持高德原本彩色，控件黑白灰，复制一套App风格；状态文字简洁，加载/错误时地图区域高度稳定；保留高德版权logo。

按TDD先写可失败的行为测试并观察RED，再实现。有效覆盖：当前房源坐标/标记正确，拖动和缩放回中，缺坐标不伪造，SDK失败重试，路由切换/卸载异步清理，320无横溢出、控件可见。使用现有Playwright fakeAMap模式，不把fake当成真实地图验证。可运行限定spec于8080读取，不能调用账号写接口。root会独立跑全部测试、真实地图和构建同步。

参考文档已查：高德官方 https://lbs.amap.com/api/javascript-api-v2/guide/map/map-bind （dragEnable/touchZoom/scrollWheel/keyboardEnable）；https://lbs.amap.com/api/javascript-api-v2/guide/map/state （setZoom/getZoom/setZoomAndCenter）。不依赖第三方包装库。

不要再派子代理。完成报告写入同目录 `2026-10-31-listing-location-map-report.md`，包含改动清单、RED/GREEN证据、命令结果、接口限制、自查。最终短回复Status/测试/文件路径/风险。
