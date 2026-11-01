# Rental Core Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. User authorized direct implementation; preserve the current checkout and do not stop for repeated confirmation.

**Goal:** 可持久化的普通租房闭环，Agent后置。
**Architecture:** Spring Boot JDBC + Flyway用户/会话与个人资源；Vue/Pinia共用HTTP，真列表与旧示例隔离。
**Tech Stack:** 现有Java21/Spring Boot3.5.16/H2、Vue3/TS/Capacitor，新增Spring Security Crypto用于BCrypt，不引入完整Web Security或JWT。
**Spec:** docs/superpowers/specs/2026-10-28-rental-core.md

## Global Constraints

- 原始CSV、设计资源、管理端和Agent不变；保留原本机示例记录。
- 设施未知/空坐标/费用周期未知不编造；个人计划不代表经纪人确认。
- 私有API按Bearer用户隔离，密码与会话只存哈希，不进URL或日志。
- 无Git，使用源码与数据库备份；不伪称提交或部署。

## Review Focus

1. 两个用户相互访问、失效token、切换账号时旧请求：不能泄露或覆盖。
2. 并发收藏/重复计划/历史：无重复且状态幂等。
3. 上海日期、过期状态、未知付款或坐标：明确拒绝或保持未知。
4. 新旧示例ID、返回列表分页与搜索条件：不混源，不丢上下文。
5. 重启持久化、网络失败与表单错误：可重试，未成功不冒充保存。

## Task 1: 后端账户与个人资源

**Files:** backend/src/main/java/com/rentpilot/member/**、db/migration/V2__member_rental_core.sql、MemberApiTest.java。
**Interfaces:** auth响应token/expiresAt/profile；profile含counts；个人资源条目使用ListingRepository批量查询。
- [x] 测试先验证注册/鉴权/收藏/历史/计划，看到缺少功能失败。
- [x] 实现迁移、BCrypt、会话、校验及私有资源，验证隔离/幂等/日期/状态。
- [x] 运行后端全套测试，备份现有数据后重启服务，验证新API。

## Task 2: 普通租房页面

**Files:** frontend/src/services/http.ts、member.ts、stores/member.ts、components/CatalogCard.vue、pages/AccountPage.vue/MemberPage.vue/MemberListingsPage.vue/ViewingsPage.vue/CatalogDetailPage.vue，router/App与CatalogPage接线。
**Interfaces:** 统一apiRequest支持GET/POST/PUT/PATCH/DELETE/204及Bearer，member状态隔离；/listing/SH-MOCK编号进入新详情，旧h编号仍可演示。
- [x] 写账户→详情→收藏→计划→我的的失败浏览器测试。
- [x] 实现正常租房导航、真实详情与账户闭环，旧演示入口保留。
- [x] 验证列表返回、未知值、费用、状态、退出与错误重试，修正旧测试的演示入口。

## Task 3: 交付

- [x] 全套后端/前端测试与构建，Capacitor同步、Gradle编译。
- [x] 一位Terra独立审查，重要问题RED/GREEN修复。
- [x] 手机截图、生产taste检查、模拟器端到端，原数据/本机记录保护检查。
- [x] 中文运行说明、完成清单与真实接入限制。
