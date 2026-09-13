# Sprint-21: DTS Connector Center 工业级数据接入中心

**时间**: 2026-04
**状态**: IN_PROGRESS
**类型**: Implementation（接入中心 Phase 2，产品化接入内核 + 运行治理）
**目标**: 在 Sprint-18 已完成数据库/文件入 ODS 主链路、Sprint-20 正在打通血缘可视化的基础上，把 DTS 数据接入能力升级为工业级 Connector Center：连接器可治理、数据源可管理、Schema 可探测、任务可向导生成、运行可观测、质量可预检、权限和审计可交付。

## 背景

当前 DTS 的 ELT 底座已经明确：

```text
Addax + dbt + Airflow
```

Sprint-18 已经把接入主链路收敛为：

```text
数据库 / Excel / CSV -> ODS -> dbt stg
```

Sprint-20 正在把血缘可视化打通为：

```text
源系统 -> 接入任务 -> ODS -> dbt model -> 指标/BI
```

但从产品形态看，DTS 还缺一个真正的接入中心内核。Addax 是执行器，不是接入管理平台；Airflow 是调度器，不是业务运行中心；dbt 是建模工具，不是源系统接入入口。

本 Sprint 的目标不是引入 Airbyte / SeaTunnel，也不是上 dlt。当前阶段优先把 DTS 自己必须掌握的接入产品能力补齐：

```text
Connector Registry
Data Source Center
Schema Discover
ODS Generator
Sync Task Wizard
Run Center
Quality & Incremental Governance
Security & Audit
```

## 产品原则

1. **DTS 掌握接入元数据**：连接器、数据源、Schema、任务、调度、运行、日志、血缘、质量和权限都必须有 DTS 自己的统一模型。
2. **执行引擎插件化**：Addax/JDBC 是标准版主引擎；dlt、Airbyte、SeaTunnel 以后只是引擎适配器，不反向决定产品模型。
3. **用户不直接写 Addax JSON**：页面配置必须能生成 Addax Job、Airflow DAG、ODS DDL 和 dbt source。
4. **Schema Discover 是核心入口**：建任务前必须能读库、读表、读字段、识别主键/索引/增量字段候选。
5. **增量语义必须可解释**：watermark 推进、失败不推进、补数、重跑、删除策略和幂等写入必须清晰。
6. **运行中心面向业务用户**：Airflow UI 是底层运维入口，DTS Run Center 才是客户日常使用入口。
7. **接入即治理**：连接测试、权限校验、质量预检、行数对账、schema drift 和审计记录要进入接入流程。
8. **血缘继续复用 Sprint-20 成果**：接入任务生成的 ODS、任务、运行状态必须天然进入 lineage graph。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|--------:|--------|------|
| F1 | Connector Registry 连接器目录 | 4 | P0 | DONE |
| F2 | 数据源中心与凭据治理 | 4 | P0 | DONE |
| F3 | Schema Discover 探测服务 | 5 | P0 | DONE |
| F4 | ODS 与 dbt source 自动生成 | 4 | P0 | DONE |
| F5 | 同步任务向导与批量建任务 | 5 | P0 | DONE |
| F6 | 接入任务运行中心与可观测 | 5 | P1 | DONE |
| F7 | 质量预检与增量治理 | 5 | P1 | DONE |
| F8 | 安全审计、验收与发布材料 | 6 | P0 | IN_PROGRESS |

**合计 38 个 task。**

## 范围边界

### 本 Sprint 做

- 连接器注册表和能力声明。
- 数据源统一管理、连接测试、凭据脱敏/加密边界。
- JDBC/数据库类 Schema Discover。
- 表、字段、主键、索引、更新时间字段候选识别。
- ODS DDL、Addax Job、Airflow DAG、dbt `source.yml` 生成链路。
- 多表同步任务向导。
- 接入运行中心、日志入口、失败分类、重跑入口。
- 接入前质量预检、行数对账、schema drift 事件。
- 与 Sprint-20 lineage graph 对接。

### 本 Sprint 不做

- 不引入 Airbyte。
- 不引入 SeaTunnel。
- 不把 dlt 作为默认执行引擎。
- 不做 CDC 全量能力；只预留连接器能力声明和策略字段。
- 不重构 dbt 建模中心。
- 不替换 OpenMetadata；OpenMetadata 仍是外部元数据镜像/采集系统。

## 目标架构

```text
                    DTS Connector Center
 ┌──────────────────────────────────────────────────────┐
 │ Connector Registry / Data Source / Schema Discover   │
 │ Task Wizard / ODS Generator / Run Center / Audit      │
 └───────────────┬─────────────────────┬────────────────┘
                 │                     │
        ┌────────▼────────┐   ┌────────▼────────┐
        │ Addax/JDBC       │   │ Future engines  │
        │ 标准数据库/文件   │   │ dlt/SeaTunnel   │
        └────────┬────────┘   └────────┬────────┘
                 │                     │
                 └──────────┬──────────┘
                            ▼
                         ODS Layer
                            │
                            ▼
                        dbt source
                            │
                            ▼
                     DWD / DWS / ADS
                            │
                            ▼
                    指标 / BI / API / Agent
```

## 依赖图

```text
Sprint-18 接入主链路
        │
        ├──> F1 Connector Registry ──┐
        ├──> F2 数据源中心 ───────────┼──> F3 Schema Discover ──> F4 ODS/dbt source
        │                            │                              │
Sprint-20 血缘模型 ──────────────────┘                              ▼
                                                             F5 同步任务向导
                                                                    │
                                           ┌────────────────────────┼───────────────────────┐
                                           ▼                        ▼                       ▼
                                  F6 运行中心              F7 质量/增量治理          F8 验收发布
```

## 完成标准

- [x] 用户可以在 Connector Registry 看到每类连接器的能力、配置 schema 和执行引擎。
- [x] 用户可以新增数据库数据源，完成连接测试、凭据脱敏展示和健康检查。
- [x] 用户可以通过 Schema Discover 读取库、表、字段、主键、索引和增量字段候选。
- [x] 用户可以选择源表，一键预览并生成 ODS 表、Addax Job、Airflow DAG 和 dbt `source.yml`。
- [x] 用户可以批量选择多张表创建同步任务，不需要手写 JSON。
- [x] 任务执行后，Run Center 展示抽取行数、写入行数、失败行数、耗时、错误摘要、日志和重跑入口。
- [x] 接入任务写入或更新 Sprint-20 的 Addax/job/source/ODS 血缘。
- [x] 接入前预检覆盖连接、权限、字段类型、主键唯一性、增量字段可用性、行数波动和目标表写入权限。
- [x] 增量任务有 watermark 状态、推进规则、失败不推进、补数和重跑说明。
- [ ] 安全审计覆盖数据源创建、凭据修改、任务创建、任务执行、任务删除、导出和重跑。
- [ ] 用户侧 API、日志、审计导出和 smoke 输出中不出现明文数据源凭据。
- [ ] 至少一条数据库源和一条文件源完成端到端冒烟并归档证据。

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| Connector Registry 过度设计 | 延迟主链路交付 | 先做内置注册表，不做 Marketplace UI |
| 各数据库元数据查询差异大 | Schema Discover 不稳定 | 先聚焦 PostgreSQL/MySQL/Oracle/DM8/SQL Server，保留方言适配层 |
| ODS DDL 自动生成误判类型 | 入湖失败或字段截断 | 生成前必须有预览和人工确认；保留类型映射 override |
| 增量策略配置错误 | 数据重复或漏数 | watermark 状态可视化、dry-run、失败不推进、重跑策略 |
| 凭据安全不足 | 交付风险高 | 凭据字段加密、响应脱敏、审计记录、权限控制 |
| 与 Sprint-20 血缘结构耦合过深 | 接入中心迭代被血缘阻塞 | 通过稳定的 lineage writer / impact API 对接 |

## 关键代码触点

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/infra/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/`
- `source/dts-platform-webapp/src/pages/infra/`
- `source/dts-platform-webapp/src/pages/ingestion/`
- `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx`
- `services/dts-dbt/models/sources/`

## 当前落地切片

- 2026-04-29：完成 F1 Connector Registry，新增 `infra_connector` 注册表、内置连接器 seed、连接器列表/详情/刷新 API、`/foundation/connectors` 前端目录页和菜单入口，为 F2 数据源中心和 F3 Schema Discover 提供统一 connector 元数据入口。
- 2026-04-29：完成 F2 数据源中心与凭据治理增强，新增 `infra_data_source.connector_key`，数据源创建/更新强制绑定连接器，连接测试结果返回失败类型与处理建议，数据源 UI 接入连接器目录。
- 2026-04-29：完成 F3 Schema Discover，新增数据源级 schema 探测 API、JDBC 元数据预览、表/字段/主键/索引/增量候选/采样结构和数据源页面探测弹窗，并补齐 Discover 结果缓存、手动刷新和 schema drift 标记。
- 2026-04-29：推进 F4 ODS/dbt source 生成，新增 ODS 生成预览和落库 API，支持从探测表生成 ODS 命名、技术字段、类型映射、DDL 预览、dbt source YAML、Addax/Airflow 草稿，并可写入 `infra_ods_table_mapping`、catalog 字段、dbt `ods_sources.yml` 和接入血缘。
- 2026-04-29：推进 F5 同步任务向导，新增 `sync-task-draft` payload 生成接口和数据源页“生成同步任务”操作，可从 Schema Discover/ODS 方案批量创建 ingestion task，并通过现有代理生成 Addax Job 与 Airflow DAG；页面已支持 ODS schema、来源系统、业务编码和同步模式配置，增量模式要求多表存在共同增量候选字段。
- 2026-04-29：推进 F6 Run Center，复用入湖任务中心展示 7 日执行概览、目标表、读写行数、耗时、错误分类/建议，并增加最新执行日志、血缘入口、最新失败执行的列表级失败重试和整批重跑入口。
- 2026-04-29：推进 F7 质量预检与增量治理，任务中心新增列表级预检入口和预检状态列，复用现有 pre-check、watermark state/audit 与 schema drift 能力。
- 2026-04-29：启动 F8 安全审计与验收，补齐平台侧 audit catalog 中 Connector Center 相关动作，登记连接器目录、数据源、Schema Discover、ODS 生成和同步任务草稿审计语义，并沉淀权限矩阵/验收路径。
- 2026-04-29：继续推进 F7/F8，新增 `POST /api/infra/data-sources/{id}/ods-precheck`，在数据源页生成同步任务前执行 ODS/任务草稿预检，覆盖 JDBC 连接类型、连接测试状态、源表字段、目标 ODS 映射冲突、增量 watermark 字段和类型转换警告，并记录 `FOUNDATION_ODS_PRECHECK` 审计事件。
- 2026-04-29：继续推进 F7 深度预检，新增源端只读探测服务，预检会实际验证源表 SELECT 权限，并尝试输出行数、主键空值/重复分组、增量字段空值规则；支持 `precheckQueryTimeoutSeconds` 和 `precheckProbeDisabled` 做现场降级。
- 2026-04-30：继续推进 F7 目标端写入预检，基于默认数据湖目标端执行 JDBC 连接和 ODS schema 临时建表/删表探测，提前暴露默认目标端缺失、连接失败、schema 不存在或 CREATE 权限不足问题；支持 `precheckTargetWriteProbeDisabled` 做现场降级。
- 2026-04-30：完成 F7 类型兼容预检，新增 `TYPE_COMPATIBILITY` 和 `TYPE_COMPATIBILITY_SUMMARY` 规则，覆盖数值精度、时间类型、二进制、JSON、boolean 和字符字段向业务类型转换等风险。
- 2026-04-30：完成 F7 数据量波动预检，新增 `SOURCE_ROW_VOLUME_BASELINE` 规则，源表行数探测后可按全局或按表 `precheckRowCountBaselines` 校验 min/max/expected/maxDeviationRatio；未配置阈值时返回当前行数作为基线候选。
- 2026-04-30：继续推进 F8 审计闭环，ingestion 服务侧补齐任务手动/异步执行、失败重试/整批重跑的成功/失败审计，并同步 `INGESTION_TASK_*`、`INGESTION_EXECUTION_RETRY` 动作到平台与 common 审计 catalog。
- 2026-04-30：补齐 F8 验收与发布材料，新增 PostgreSQL 源表样例、CSV 文件样例、ODS 请求样例、Connector Center smoke 脚本和现场 Runbook，覆盖 discover、预检、ODS/dbt source、建任务、执行和 Run Center 观测。
- 2026-04-30：继续推进 F4/F5 字段级契约，ODS 生成请求支持 `include=false`、`targetName`、`targetDataType`，后端已具备字段选择、重命名和类型覆盖语义，前端独立向导页可直接复用。
- 2026-04-30：继续推进 F6 Run Center 补数闭环，新增 `POST /api/ingestion/tasks/{id}/backfill`，执行记录持久化补数窗口和 `BACKFILL_RANGE`，Addax 运行时注入时间范围 where，且补数成功不推进主增量 watermark。
- 2026-04-30：完成 F5 同步策略后端契约，支持全量覆盖、全量追加、时间戳增量和数值主键增量；主键增量会校验多表共同主键并映射到 ingestion 增量任务。
- 2026-04-30：完成 F6 Run Center 前端补数入口，任务列表对增量任务露出“回填”入口，执行历史页支持窗口回填弹窗、回填列提交、执行记录窗口展示，并明确回填不推进主增量检查点。
- 2026-04-30：完成 F4/F5 前端收口，Schema Discover 弹窗增加字段包含/排除、目标字段重命名、目标 ODS 类型覆盖表格，并补齐全量追加和主键增量策略控件，预览、预检、ODS apply 和 sync-task-draft 共用同一份 ODS request。
- 2026-04-30：继续推进 F8 凭据安全收口，用户侧数据源详情不再返回 JDBC/API 明文 secrets，运行时明文凭据切换到仅内部 `service:*` principal + `X-DTS-Service-Token` 可调用的 `runtime-detail`，ingestion 运行链路同步改用内部运行时详情。
- 2026-04-30：继续推进 F8 工业级验收证据，新增文件源 smoke、凭据脱敏审计脚本、验收记录模板和 PostgreSQL/MySQL/Oracle/SQL Server/DM8 方言验证矩阵。
- 2026-04-30：补齐 F8 凭据边界单测，覆盖用户 principal、伪造服务头和内部服务令牌三类 `runtime-detail` 访问路径；同步修复审计日志代理测试对 BFF cookie/Authorization fallback 签名的适配。
- 2026-04-30：补齐 ingestion 侧 `PlatformInfraClientTest`，验证数据源运行时详情必须访问 `/runtime-detail`，并携带 `X-DTS-Service` 与 `X-DTS-Service-Token`。
- 2026-04-30：验收脚本补齐 `DTS_COOKIE` / `DTS_COOKIE_JAR` 支持，既可用 Bearer token，也可用 HttpOnly cookie 跑 Connector Center、文件源和凭据脱敏 smoke。
- 2026-04-30：补齐 platform 入湖代理 `/api/ingestion/tasks/{id}/backfill`，让 Run Center 补数能力可通过统一平台 API 被验收脚本和前端调用。
- 2026-04-30：按 `dev`/`app`/`legacy` 三模式一致原则调整服务令牌方案，不再要求初始化后修改 `.env` 或 compose；平台内部服务 token 由数据库 hash 管理，ingestion 通过 `infra_service_settings.platform.serviceToken` 保存运行时 token 并脱敏/加密。

## 仍待工业级补齐

- F8 还需要在真实联调环境执行数据库源/文件源端到端冒烟、凭据脱敏审计抽检，并把输出归档到 `it/evidence/<date>-<rc>/`。
- F3 的多数据库方言矩阵已提供模板，但 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的现场证据仍需补齐。

## 相关 Sprint

- `worklog/v2.2.3/sprint-18-202604/README.md` — 企业级数据接入中心 Phase 1。
- `worklog/v2.2.3/sprint-19-202604/README.md` — OpenMetadata 元数据采集闭环。
- `worklog/v2.2.3/sprint-20-202604/README.md` — Data Lineage 端到端可视化。
