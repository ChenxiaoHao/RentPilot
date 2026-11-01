# 地图找房联调记录

2026-10-28，接收用户提供的 CodeArts 后端成果后，完成接口复核、真实高德地图、前端交互与 Android Debug 联调。用户提供的是 Web 端 Key 与服务端安全密钥；没有在此记录密钥内容。

## 已完成

- CodeArts 实现 `GET /api/listings/map`：按 GCJ-02 矩形范围查询，允许列表条件，返回 `total / returned / limit / truncated`。25 项接口测试覆盖边界、排序、截断、空坐标和非法参数。独立重新运行其提交后的完整 74 项后端测试，并完成之前因运行中 JAR 被占用而失败的 `mvn verify` 打包。
- 普通找房列表右上角进入 `/find-map`。高德 JS API 2.0 真实底图使用浅灰风格；按当前视野加载后端模拟房源，点位按屏幕距离聚合，点击查看卡片，左右滑动并进入现有详情。边缘点位以圆点表示，缩放/平移后继续聚合；保留可访问名称。
- 支持区域、最高租金和出租方式筛选。列表已有关键词、户型、地铁等条件会继承并显示，可清除；切换筛选时先清空旧结果并显示加载状态，防止旧卡片被误认成新条件的结果。地图尺寸改变时重新计算可视范围。
- 每次最多返回 500 套；超出时说明有截断并建议放大或筛选。14 条原始数据缺少坐标，不在地图伪造点位，但仍可在普通列表查看。模拟数据的 GCJ-02 坐标是小区级参考，不代表真实在租或精确门牌。
- 后端实现高德 JS 安全代理：安全密钥只在后端配置；限定固定高德上游、当前使用的 `/v3/log/init` 路径与指定 Web Key，拒绝客户端传入安全密钥，限制查询长度、并发、响应体大小、连接与读取空闲时间。新增 4 项代理测试。
- Android Debug 以同源 `https://localhost/_AMapService/v3/log/init` 转发到固定模拟器宿主机端口；其他请求仍由 Capacitor 处理，未放宽 WebView 全局混合内容。Release 不启用该调试转发。

## 验证

| 检查 | 结果 |
| --- | --- |
| 后端完整 `mvn verify` | 78 项测试通过、JAR 重新打包成功 |
| 前端单元测试 | 26 项通过 |
| 浏览器全量流程 | 50 项通过；最后的地图视觉调整后，地图专项再次通过 |
| 真实高德浏览器运行 | SDK 与代理返回 200，真实底图、房源点位和卡片显示 |
| 预算筛选 | 浏览器实际接口返回 197 条 ≤ 2000 元房源，未混入超预算房源 |
| 手机视口 | 320×640、360×740、402×874、480×960 无横向溢出，卡片与导航可见 |
| taste-skill 生产版 App 检查 | 390×844：0 FAIL、0 WARN；另人工检查桌面浏览器与 Android 真实地图截图 |
| Android | Capacitor 同步、Gradle Debug APK 构建成功；Pixel 8 API 35 实际加载高德底图、点位与服务端代理，3000 元筛选结果符合预算；设备已有本机记录未变化 |
| 原始数据 | CSV SHA-256 仍为 `07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8` |

截图、测试与构建日志位于 `../rental-core-2026-10-28/`：`screens/amap-real-android.png`、`screens/amap-real-320.png`、`amap-layout.json`、`amap-android-verification.json`、`amap-secure-verify.log`、`amap-e2e-final.log`、`amap-final-gradle.log` 和 `amap-taste/`。自动化模拟 SDK 的测试只验证交互，真实 SDK 与原生代理另有实际网络检查。

人工视觉判断：地图使用灰阶底图及黑白标记，点位密集时聚合；价格、筛选、卡片和底部导航有清晰层级。屏幕边缘点位用圆点避免数字截断；数据仍可能因 500 条上限不完整，页面对此有提示。真实户型照片与真实房源仍未接入。

## 运行

后端在 `backend/` 启动 `scripts/start.ps1`。前端可运行 `npm run dev -- --port 5179`，或先构建后 `npm run preview -- --port 4174`。从找房页面点击右上角地图图标；本机当前预览为 `http://127.0.0.1:4174/#/find-map`。

Android Studio 打开 `D:\CODE\RentPilot\frontend\android`，JDK 21、Android SDK Platform 36；每次 Web 代码修改后先执行 `npm run android:sync`。当前 Debug APK 位于 `frontend/android/app/build/outputs/apk/debug/app-debug.apk`，大小 6,332,083 字节，SHA-256 `30B60F9AD3B15003C252219853EC31A7A9A16DC8C6306DB940EF7E80ED10B1C5`。

本机配置位置：`frontend/.env.local` 的 `VITE_AMAP_WEB_KEY` 为客户端可见的 Web Key；`backend/config/application-local.properties` 的 `rentpilot.amap.security-code` 为服务端安全密钥，并配置相同 `rentpilot.amap.web-key` 以约束代理。两个文件被忽略，不提交。前端 Web Key 会包含在 JS 和 APK 中，不能当作服务端秘密；请在高德控制台配置合适的授权与额度。

## 限制

- 当前没有实时定位、路线规划、地理编码或工作地通勤计算；旧 `/map` 仍是独立演示。
- Android 模拟器 Debug 使用 `10.0.2.2` 访问宿主机。实体手机和 Release 需要可达的 HTTPS 后端，以及相同域名下的安全代理，配置 `VITE_API_BASE_URL` 后重新构建；实体设备尚未验证。
- 当前后端仅绑定本地，尚未完成公网部署。代理限制并发但没有面向公网的频率限制；读取超时为单次空闲超时而非整体请求时限。对外部署前需网关限流、授权限制和生产安全复核；高德其他插件可能需要额外的代理路径并应单独评估。
- Agent、AI 模型、真实房东联系、支付和签约未在本轮开发。
