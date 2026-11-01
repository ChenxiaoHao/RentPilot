# 房源列表接线完成

2026-10-28。本轮只实现前端列表查询和示意照片。沿用 taste-skill 的既有黑白灰方向、中文字号与胶囊导航，没有继续开发 AI 或高德地图。

## 成品入口

- 浏览器：[房源列表](http://127.0.0.1:4173/#/listings)。开发版为 http://127.0.0.1:5178/#/listings。
- App 底部点击“找房”；聊天首页的“浏览全部房源”和对话推荐区的“全部”也进入新列表。
- 点击卡片在“房源信息”弹层查看数据库中的房源，而不是跳转到旧 h1–h6 示例详情。
- [列表截图](list-402.png)、[信息弹层](detail-402.png)、[Android 实机屏幕](android-list.png)、[软键盘](android-keyboard.png)。

## 已完成行为

真实请求 `/api/listings`、`/api/listing-filters`、`/api/listings/{id}`。区域、出租方式、卧室数、预算、文本搜索、地铁步行时间和排序均由后端处理。默认不限制为一居；共 1500 条模拟房源，按20条加载下一页。组合条件“浦东新区、整租、1室、月租≤3000”返回68条。

条件写入URL，刷新和关闭弹层不丢失；修改条件回到第一页。旧请求、迟到结果及分页错误不会覆盖新条件。首次错误可以重试，下一页失败保留已加载内容，无结果不擅自放宽条件。客户端校验分页、列表、详情和筛选项结构；缺少字段时显示可恢复错误。

详情保留原编号、价格、面积、整套户型、出租方式、楼层、朝向、押金、付款方式、物业费（周期未注明）、原入住描述及设施三态。未知地铁信息没有填0或换成工作地通勤时间。待核验地址照实展示。

新增6张 Pexels 照片，共约690KB，已保存本地。按房型和编号稳定重复使用，显示“示意图 / 非该房源实拍”，不代表对应小区或房号。[摄影师、原页面和许可清单](../../frontend/public/assets/catalog/README.md) 与 [照片预览](photos.png) 已保存。原始CSV和说明文件哈希未变，原有设计资源和图片未覆盖。

## 验证结果

| 项目 | 实测 |
| --- | --- |
| 单元测试 | 16通过，包括编码参数、异常响应、设施空值和原生取消等待 |
| 浏览器测试 | 42通过，包含原有流程与8项新增列表/入口测试 |
| 移动适配 | 320/360/402/480宽度无横向溢出、无坏图；加载更多滚动留白正常 |
| 前端构建 | TypeScript + Vite通过 |
| Capacitor Android同步 | 通过 |
| Gradle assembleDebug | BUILD SUCCESSFUL |
| taste-skill生产版检查 | 0 FAIL / 0 WARN；已人工查看截图 |
| Pixel 8 API35 | 原生HTTP查询1500总数、68筛选结果、20条首屏批次、信息弹层、原生返回通过 |
| 软键盘 | 导航隐藏；搜索框底部222.55 < 可视高度521.14逻辑像素 |
| 原有本地记录 | 验证前后 localStorage 字节一致，未清除数据 |

证据：[单元](unit.log)、[浏览器](e2e-all.log)、[同步构建](android-sync.log)、[Gradle](gradle.log)、[Android数据](android-verification.json)、[生产检查](taste-production/report.json)。RED日志保留功能缺失及回归检测证据，最终结果以上述最终日志为准。

最新 APK：`D:\CODE\RentPilot\frontend\android\app\build\outputs\apk\debug\app-debug.apk`，6,375,054字节。

SHA-256：`AB3FD2A7F89592CD2C52EE3BA4F4777BD8BE41074D942BB3F84AC45870952C70`。

Android Studio继续打开 `D:\CODE\RentPilot\frontend\android`。启动后端后，模拟器可以直接运行本轮Debug包。当前服务是本机联调版；默认原生地址10.0.2.2仅供模拟器使用。真机和Release需配置可访问的HTTPS API，再构建和同步；未声称真机或云端已经验证。

## 审查与范围

一位 GPT-6.1 Terra 独立审查，发现响应校验不完整和两个“全部房源”旧入口，已补校验和跳转，并通过RED/GREEN与全套测试。原生地址的设备/发布限制按开发配置保留并写入运行说明，没有放宽Release网络策略。

原 `/map` 仍为示意点位，地图按钮明确标为“查看地图示意”；AI、LifeSim、方案与六套旧样例详情仍为原演示。新数据库房源未接入旧收藏、预约或方案生成，避免把不完整流程冒充已联通。后端源码与原数据未改。

复现步骤见 [frontend/README.md](../../frontend/README.md)。图片来自明确标记免费的 [Pexels源页](https://www.pexels.com/photo/interior-of-modern-minimalist-studio-apartment-with-living-room-and-bedroom-7060814/)，使用条件参见 [Pexels License](https://www.pexels.com/license/)；Android Debug网络规则与 [官方说明](https://developer.android.com/privacy-and-security/security-config) 一致，前端代理依据 [Vite预览代理文档](https://vite.dev/config/preview-options.html#preview-proxy)。
