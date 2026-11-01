# 房源详情位置地图实施报告

## 改动

- `frontend/src/components/ListingLocationMap.vue`：260px 高德地图、真实坐标 Marker 和小区标签、拖动与触屏缩放、放大/缩小/回中、加载/失败/重试及待核验状态；卸载和坐标变化时清理实例并丢弃过期异步结果。
- `frontend/src/services/amap-sdk.ts`：提取共享 SDK 加载器，并发复用、失败后可重试；沿用原生平台 HTTPS API 地址及 `/_AMapService` 代理，不引入安全密钥。
- `frontend/src/pages/CatalogMapPage.vue`：仅替换 SDK 加载调用及类型。
- `frontend/src/pages/CatalogDetailPage.vue`：嵌入地图；高德外链仅在坐标有效且位置已核验时显示。
- `frontend/tests/e2e/listing-location-map.spec.ts`：fake AMap 聚焦测试。

## 验证

- RED：首次执行 `npx playwright test tests/e2e/listing-location-map.spec.ts`，因找不到“房源位置地图”失败。
- GREEN：`npx vue-tsc --noEmit` 通过；`npx playwright test tests/e2e/listing-location-map.spec.ts tests/e2e/map-catalog.spec.ts` 4 项通过。测试检查房源坐标与标记、缩放和回中、320px 不横溢、缺失坐标、SDK 失败重试、卸载后延迟响应、既有地图找房交互。
- fake AMap 无法证明真实瓦片、手势、版权显示和实际网络代理；真实地图、三宽度截图、全量测试及生产/Android 检查由主任务负责。

## 自查及边界

- 坐标按 GCJ-02 原样使用，未引入地理编码、用户定位、后台接口或新依赖；标记作为地图覆盖物随地图移动。
- 非有限、越界、缺失和 UNVERIFIED 均不创建地图或虚构标记；部分非有限数据会被已有目录服务校验更早拒绝。
- 状态区域高度固定；控件为 44px，滚轮缩放关闭。错误不影响其他详情和联系。
- SDK 共用加载成功后保留全局 SDK，地图实例销毁；失败会移除脚本并允许重新加载。
- 本次测试未模拟真实拖动、双指、多次路由切换的所有组合或全部指定屏宽；这些仍需主任务的真实浏览器核验。

## 样式小修

根据源码样式检查，移除了新组件控件与标记的两处阴影及控件分隔线，保留白底控件和标记白描边。主任务将重新构建并检查。
