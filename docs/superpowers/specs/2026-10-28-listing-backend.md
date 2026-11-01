# 房源查询后端第一阶段

用户授权：初始化后端、导入现有 1500 条模拟房源、实现查询接口。本阶段不改前端，不接入大模型或路线 API，不创建管理后台。

## 实现约定

- Java 21、Spring Boot 3.5.16、Maven、Spring JDBC、Flyway。单体服务；后续 Spring AI 独立接入，当前不安装模型客户端。
- 本地默认 H2 文件数据库（PostgreSQL 模式），不依赖尚未启动的 Docker；提供 PostgreSQL 环境变量配置，但未经真实 PostgreSQL 验证不得声称云数据库已经接通。
- CSV 从原目录打包入 JAR，不改动原文件。启动时核验 SHA-256 `07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8`、38 列、1500 行、唯一编号、必填字段、数值、设施三态和成对坐标。完整验证后在事务内导入；同来源重复启动不重复插入。已有批次与文件或数据库数量不一致时明确失败，不自动清空数据。
- 保留全部 38 个原字段的含义；金额使用十进制定点数。12 项设施保留 YES / NO / UNKNOWN。14 条空坐标和 423 条空地铁步行距离保持 null；地铁步行时间不是到工作地点的通勤时间。
- 房源和每页均明确 `SIMULATED`；位置为真实小区参考或待核验，坐标范围是小区，不表示真实在租房屋。物业费周期在原资料中未明确，不擅自作为每月固定费用。无照片、评分、工作地通勤数据时不编造。

## API

- `GET /api/health`：运行状态及当前已导入数据集摘要。
- `GET /api/listings`：page（从 0 起）、size（默认 20，1–100）、q、district、town、rentalType（ENTIRE/SHARED）、bedrooms（整套户型卧室数，合租不是可独占卧室数）、minRent/maxRent、minArea/maxArea、maxMetroWalkMinutes、metroLine、hasCoordinates、elevator、petsAllowed、privateBathroom。筛选取交集，设施支持三态精确筛选。
- 排序 `id`（默认）、`rentAsc`、`rentDesc`、`areaDesc`、`metroWalkAsc`；固定白名单，价格相同按编号稳定排序，地铁时间排序缺失项置后。
- 查询关键字按普通文本匹配名称、区、街镇、小区、地址；转义 SQL 通配符。所有值参数化。无结果返回 200 和空数组，非法参数返回 400，编号不存在返回 404，未知查询参数返回 400，避免条件被悄悄忽略。
- `GET /api/listings/{id}`：保留原始编号的完整详情。
- `GET /api/listing-filters`：区/街镇及数量、出租方式/户型数、准确拆分的地铁线路，数据来源和缺失统计。
- JSON 错误统一为 code/message/path/fieldErrors，不返回 SQL、异常栈或本地文件路径。仅开放只读 API 和配置的开发来源；默认绑定 127.0.0.1，尚无用户认证及公网部署。

## 验收

真实 1500 条导入、重复启动无重复；原数据哈希不变；分页无重叠、稳定排序；组合过滤结果正确；空结果和非法/未知参数区分；单个房源详情保留未知项；失败导入不留下半批数据；打包 JAR 可独立启动、重启保留数据。保留测试报告、实测 HTTP 结果及中文运行说明。
