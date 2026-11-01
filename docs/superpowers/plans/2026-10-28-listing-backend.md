# Listing Backend Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. User authorized direct completion; do not stop for repeated confirmation.

**Goal:** 本地可运行的真实数据库查询后端，导入 1500 条模拟数据。
**Architecture:** Spring Boot + JDBC + Flyway；本地 H2，预留 PostgreSQL。CSV 先全量校验，再事务导入，HTTP 只读访问。
**Tech Stack:** Java 21, Spring Boot 3.5.16, Maven, Commons CSV 1.14.1.
**Spec:** docs/superpowers/specs/2026-10-28-listing-backend.md

## Global Constraints

- 不修改 frontend/、原始 CSV、设计资源、admin-web/；不接入真实 AI 或地图。
- 设施三态、缺失坐标/地铁值、数据来源及模拟性质必须保留。
- 所有查询参数化，sort 白名单；分页默认 20、上限 100。
- 当前目录无 Git；原地实现，记录受保护文件哈希，不能伪称提交或工作树。

## Review Focus

1. CSV BOM、引号、中文、重复编号或损坏行：失败不留半批数据。
2. 低预算、组合条件、空字段：不得悄悄放宽或把 null 当 0/false。
3. SQL 片段、百分号、下划线、线路 1/11：文本转义和精确线路过滤。
4. 分页、排序相同值、非法/未知参数：稳定返回或 400，详情未找到为 404。
5. 重启、文件变更、数据数量漂移：幂等且发现不一致，不覆盖已有数据。

## Task 1: 可启动服务与导入

**Files:** backend/pom.xml, src/main/java/com/rentpilot/**, src/main/resources/**, src/test/java/com/rentpilot/**.
**Interfaces:** 启动完成前 DatasetImporter.importDataset()；数据集 ID shanghai-simulation-v1；ListingRepository 的查询使用相同列名。
- [x] 写健康接口和 1500 条导入测试；运行并确认缺少功能时失败。
- [x] 实现启动、迁移、严格 CSV 校验、事务导入和来源记录。
- [x] 验证空值、设施、哈希、重复导入、错误 CSV 和事务回滚。

## Task 2: 列表、详情、筛选项

**Files:** backend/src/main/java/com/rentpilot/listing/**, api/**, tests.
**Interfaces:** GET /api/listings 返回 items/page/size/total/totalPages/dataKind/source；GET /api/listings/{id} 返回完整 Listing；筛选/健康共用 DatasetInfo。
- [x] 先写 HTTP 集成测试，验证 404/缺少筛选功能的失败。
- [x] 实现参数验证、JDBC 条件、稳定排序、详情、筛选项和统一错误。
- [x] 跑全套后端测试。

## Task 3: 交付验证

**Files:** backend/README.md, backend/scripts/**, README.md, .gitignore, docs/backend-2026-10-28/**.
- [x] 提供 Maven Wrapper 和 Windows 启动说明，PostgreSQL 外部配置，忽略本地数据库。
- [x] Maven verify 打包；独立 JAR 启动并 HTTP 验证；重启验证幂等与持久化。
- [x] 一位 Terra 新上下文审查；必要修复按 RED/GREEN；保存最终证据。
