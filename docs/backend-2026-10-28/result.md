# 房源后端交付记录

日期：2026-10-28。范围：后端初始化、原始 1500 条模拟房源导入、只读查询接口。前端界面、原始 CSV 和设计素材未修改。

## 已交付

- `backend/`：Java 21、Spring Boot 3.5.16、JDBC、Flyway、Maven Wrapper 3.9.11。
- 本地 H2 文件数据库 `backend/data/rentpilot.mv.db`，重启保留数据；PostgreSQL 驱动和环境变量配置预留。
- UTF-8/BOM/38 列校验，唯一编号、数值范围、设施三态和坐标完整性校验；整批事务导入、来源 SHA-256、首次导入时间和幂等验证。
- `GET /api/health`、`GET /api/listings`、`GET /api/listings/{id}`、`GET /api/listing-filters`。
- 分页、文字搜索、区/街镇、出租方式、卧室数、租金/面积、地铁步行/线路、坐标有无及设施三态筛选，稳定排序，400/404/503 错误与 CORS 白名单。
- 中文 [启动与接口说明](../../backend/README.md)、[HTTP 示例](../../backend/requests.http)、[HTTP 验证脚本](../../backend/scripts/verify-http.ps1)。

## 最终验证

| 验证 | 实测结果 |
| --- | --- |
| `mvnw.cmd -s .mvn/settings.xml -B -ntp clean verify` | BUILD SUCCESS；41 测试，0 失败、0 错误、0 跳过 |
| 独立 JAR | 复制到独立工作目录运行，无外部 CSV 路径依赖 |
| HTTP 检查 | 26 项通过；遍历 15 页，1500 个唯一编号，无重复 |
| 重启 | 1500 条保持，首次导入时间不变，记录 no duplicate import |
| 启动时序 | 数据集校验日志先于 Tomcat started；回归测试使用随机隔离内存库 |
| 组合筛选 | 浦东新区 + 整租 + 1 室 + 月租 ≤3000：68 条，与独立 CSV 统计一致 |
| 缺失与来源 | 14 条空坐标、423 条空地铁步行数据；未知设施保持 UNKNOWN |
| 来源保护 | 92 个受保护前端/测试/素材/原始数据/预留管理端文件，变更 0 |
| JAR 内 CSV | SHA-256 与原始文件完全一致 |

最终 JAR：`backend/target/rentpilot-backend-0.1.0.jar`，29,352,327 字节。

SHA-256：`FB1D9FEF3B9366E9490C2A2137985467F0A9A59F20AF2C7E249DA689A7237F59`。

原 CSV SHA-256：`07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8`。

证据：[构建日志](final-verify.log)、[HTTP 本地检查](http-local.json)、[独立检查](http-standalone.json)、[重启结果](runtime-verification.json)、[总记录](verification.json)。早期 RED 日志是有意验证缺失功能与回归测试；最终结论以上述最终日志为准。

## 运行状态与后续

交付时服务在 **http://127.0.0.1:8080/api/health** 运行，使用 backend/data 文件库。重新启动：在 backend 目录设置本机 JDK 21 的 JAVA_HOME 后执行 `./scripts/start.ps1`。源码变化后使用 `-Build`。

本机 JBR 21 的长临时路径会触发 selector pipe 的 AF_UNIX 错误；启动脚本设置进程级短目录解决，不改全局变量。独立运行和最终本地服务均已验证。

当前未连接真实 PostgreSQL/华为云，未接入模型、工作地通勤或前端数据服务。前端仍是之前的本地示例。模拟房源是数据库中的真实查询结果，但房源本身不是实际出租供应。当前服务没有用户认证与公网访问方案，保持回环地址的本地只读开发用途。

审查发现的启动时序问题已修复：使用依赖数据库迁移的 InitializingBean 在服务端口开放前完成验证。独立审查未遗留其他问题。未来如允许修改数据库内容，需增加内容版本校验；当前来源哈希描述导入文件，不表示实时数据库逐字段完整性校验。

下一阶段先让前端列表/地图/详情使用这些接口并处理缺失图片和位置，再实现结构化需求与 Spring AI 工具调用。本轮没有创建虚假的模型调用或 CodeArts 使用记录。

参考官方文档：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[数据库初始化依赖](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)、[Apache Commons CSV](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.html)。
