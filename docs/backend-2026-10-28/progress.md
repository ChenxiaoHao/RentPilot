# SDD ledger — plan: docs/superpowers/plans/2026-10-28-listing-backend.md

- 用户已授权直接实施本阶段；复用参赛规格，按当前接口规格收窄范围。
- Ruling: 无 Git 元数据，原地实现，不初始化 Git 或工作树；用受保护文件哈希和测试报告留证，不伪称提交。
- Ruling: Docker Engine 未启动，本地 H2 文件库用于开发；预留 PostgreSQL，后续需真实实例验证，不为本轮引入安装或云资源开通。
- Pre-flight: importer 写入 listing/dataset_import，repository 读取同一迁移；测试数据库独立内存库，运行数据库 backend/data/。
- Task 1: complete. Health RED 404; reader RED 5 failed assertions. GREEN 6 tests passed. CSV preserved null and unknown values; schema and batch import work against real H2.
- Task 2: complete. HTTP RED 28 failures (missing routes/CORS); GREEN 34 tests passed. Independent CSV checks: 浦东整租一居 ≤3000 = 68；潍坊一村 = 9；电梯未知 = 171；1号线 = 114。
- Task 3: in progress. Added importer idempotency/transaction rollback/source change/count drift coverage; preparing packaged runtime verification.
- Final review (GPT-6.1 Terra): Important — ApplicationRunner 在服务端口启动后运行，可能在来源校验前响应旧数据。RED StartupReadinessTest 实测初始化后只有 0 行；改为依赖数据库迁移完成的 InitializingBean，在服务器生命周期启动前验证与导入。
- Final: Ruling: PostgreSQL/云端认证、前端接线和工作地路线为后续独立阶段，不对本轮缺失能力作已完成声明。
- Final: Ruling: 同行数的人工数据库内容篡改不在当前只读库校验范围；SHA 表示导入文件来源，不宣称是实时数据库内容哈希。后续允许更新房源时需加入版本/内容完整性机制。
- Review fix GREEN: Wrapper verify 共 41 测试通过；构建成功。独立运行首次因本机 JBR 21 AF_UNIX 临时路径错误失败，堆栈定位 PipeImpl/UnixDomainSockets；在启动脚本添加进程级短目录参数，不修改全局环境或应用业务代码。
- 交付验证更正：早期 Wrapper 构建与定向测试交叠，独立运行发现旧 JAR 仍为 Runner。停止交叠，改为顺序 clean verify 再验证 JAR；不把早期运行记录当最终证据。
- 测试隔离更正：SpringApplicationBuilder.properties 的低优先级会被 local 配置覆盖；readiness 测试改为命令行高优先级的随机内存库并检查 JDBC URL。对旧 Runner 回退实测 RED=0行（red-readiness-isolated.log），恢复 InitializingBean 后验证全套。测试曾生成 backend/data 原始同版本库，无用户会话数据且未删除文件；现在所有测试库独立内存。
- Task 3: complete. 最终顺序 clean verify 41/41 PASS；最终 JAR 独立运行、26 HTTP 检查、15页1500唯一编号、重启幂等通过；日志确认 importer 在 Tomcat started 之前。
- 最终服务通过 scripts/start.ps1 启动在 127.0.0.1:8080（进程 8376）；正常目录数据库持久化，HTTP 再验26项通过。前端/原数据等92受保护文件0变化。参见 result.md / verification.json。
- Final review resolved: 1 Important fixed (RED/GREEN + full suite + runtime order evidence); no deferred minors. 无 Git 仓库，不执行合并/推送/清理源码，保留所有验证与交付文件。
