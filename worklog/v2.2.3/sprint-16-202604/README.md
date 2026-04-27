# Sprint-16: API 数据接入产品化

**时间**: 2026-04
**状态**: IN_PROGRESS
**类型**: Product Feature Design + Implementation Plan（正式产品能力，不按短期 MVP 处理）
**目标**: 将 API 数据接入建设为 DTS 平台一等数据源类型，支持后续客户系统通过 REST/HTTP API 原样接入 ODS，并复用现有调度、dbt、stg 建模、目录、血缘、质量与运维能力。

## 背景

当前 ELT 入湖主链路已覆盖离线文件和数据库源。客户后续数据接入会出现第三类源：由其他业务系统提供 API，DTS 需要按计划调用 API 获取数据并落到 ODS。

本特性不能简单复用数据库接入页面或把 API 当作 Addax reader 参数裸透传。正式产品能力必须覆盖鉴权扩展、请求预检、分页/限流、schema snapshot、stg 映射、增量游标、密钥保护、运行观测和发布门禁。

## 产品原则

1. API 是一等数据源类型，前端、后端、能力契约、运行时、审计和目录都要显式识别 `api/http`。
2. 鉴权机制必须插件化，当前不提前固化具体方案，先定义 Auth Provider 扩展点。
3. API 数据默认先原样落 ODS，再通过 stg 进入 dbt、目录、血缘、质量和 BI，不支持 BI 运行时直接穿透外部 API。
4. API 配置必须拆分非敏感配置与密钥引用，任何 token、secret、private key 不允许进入任务日志、Addax job 明文或前端回显。
5. schema 发现采用 preview/inference 流程，不复用 JDBC table discovery。
6. 运行时要抽象 `SourceConnector`，允许 API 选择 Addax HTTP reader 或 DTS 自研 API runner；Airbyte 不作为 Sprint-16/Sprint-18 的实施依赖。
7. 增量语义使用 cursor/checkpoint，不强行套数据库 SQL where。

## Sprint-18 约束回灌

Sprint-18 已明确：**ODS 是源端只读镜像层，stg 才是标准化层**。因此 Sprint-16 的 API 接入必须按以下边界调整：

- API ODS 表不承载业务字段改名、类型标准化、敏感字段标签或质量规则。
- API ODS 仅保存 record path 提取到的原始 record（如 `_dts_raw_record`）和 `_dts_*` 技术字段。
- preview/inference 产物先落 schema snapshot，用于 drift 检测、dbt source.yml、目录注册和 stg 自动生成。
- 原先文档中的“ODS mapping / ODS 字段编辑”统一改为“schema snapshot / stg mapping”。
- 任何用户可编辑的字段名、类型、nullable、primary key、sensitive flag 都属于 stg 规则，不得写回 ODS 建表契约。

## 非目标

- 不为某一个客户 API 写硬编码专用接入。
- 不在本阶段承诺覆盖所有 SaaS 连接器市场。
- 不把 API 接入做成“高级参数 JSON 手填”功能。
- 不改变现有 Excel、CSV、数据库接入的用户主流程。
- 不绕过 ODS 直接进入 DWS/ADS/BI。

## 用户主流程

1. 管理员创建 API 数据源，选择鉴权方式，填写基础 URL、默认 headers、超时、限流和密钥。
2. 平台执行安全的连接测试和鉴权 dry-run，不持久化响应样本中的敏感字段。
3. 数据开发人员创建 API 接入任务，选择数据源和 endpoint/resource。
4. 平台执行 preview，按 record path 提取记录，保存 schema snapshot；用户只确认记录路径和样本，不编辑 ODS 字段。
5. 用户配置同步模式、分页策略、增量 cursor、调度计划和失败策略。
6. 运行时按任务生成 execution plan，执行 API 拉取、原样落 ODS、checkpoint 更新、schema snapshot / stg / 目录 / 血缘 / 质量联动。
7. 运维页面展示运行日志、HTTP 状态、限流、重试、解析错误、schema drift 和 checkpoint。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|---------|--------|------|
| F1 | API 接入契约与鉴权扩展点 | 5 | P0 | IN_PROGRESS |
| F2 | API 数据源管理与预检 | 5 | P0 | IN_PROGRESS |
| F3 | API schema snapshot 与 stg 映射 | 5 | P0 | DRAFT |
| F4 | API 运行时与增量同步 | 5 | P0 | DRAFT |
| F5 | API 接入向导与任务编排 | 5 | P1 | DRAFT |
| F6 | 安全治理、观测与发布门禁 | 5 | P0 | DRAFT |

**合计 30 个 task。**

## 依赖图

```text
F1 契约与扩展点
  -> F2 数据源管理与预检
  -> F3 schema snapshot 与 stg 映射
  -> F4 运行时与增量同步
  -> F5 前端向导与任务编排
  -> F6 安全治理、观测与发布门禁
```

> 上图为最长依赖路径，**不是执行顺序**。实际并行支线：
> - `F2/T05`（mock fixtures）、`F6/T01`（threat model）可与 F1 并行。
> - `F3/T05`（catalog/血缘命名）可与 F4 并行。
> - `F5/T01-T02`（数据源选择 + 鉴权表单）可在 F1/T02 + F2/T01 完成后立即启动，不需要等 F4。
> - `F6/T04`（测试矩阵）应在 F2-F4 持续累积，不是 F5 之后的一次性收尾。

F6 不是收尾文档任务，安全、审计、脱敏、观测和测试门禁必须从 F1 同步设计，最终统一验收。

## 关键设计触点

- `source/dts-ingestion`: 新增 API connector contract、preview、schema snapshot、execution plan、checkpoint、错误分类。
- `source/dts-platform`: 扩展数据源模型、密钥引用、代理接口、ODS 原样落地同步、schema snapshot/stg 映射代理和权限审计。
- `source/dts-platform-webapp`: 新增 API 数据源配置页和 API 接入任务向导。
- `services/dts-dbt`: 原则上不感知 API，只消费落地后的 ODS source。
- `worklog/v2.2.1/platform/access/tasks/P2-01-connector-capability-contract.md`: 复用既有连接器能力契约思路，扩展 `api/http` 能力声明。
- `docs/implementation/ingestion-task-schema.md`: 需要升级到 v2，把 API source、auth、pagination、cursor、schema inference 纳入业务 API 契约。

## 开放问题

| 问题 | 当前决策 | 后续动作 |
|---|---|---|
| 客户最终鉴权机制未定 | 先做 Auth Provider SPI，不绑定具体实现 | F1/T02 输出 provider contract |
| API 运行时选型 | 不预设只用 Addax，且不引入 Airbyte 依赖 | F4/T01 输出 execution plan 抽象，F4/T02 对比 Addax HTTP reader/custom runner |
| Secret 后端 | 不允许继续依赖普通 props 字符串 | F1/T05 和 F6/T01 确定存储与脱敏策略 |
| Schema drift 策略 | 默认 notify + block 可配置 | F3/T04 和 F6/T04 进入测试矩阵 |
| 多 endpoint 任务模型 | 支持一个数据源多个 resource | F3/T03 定义 resource 到 ODS 表映射 |
| Connector type 命名统一 | 决议为 `api`（聚合 alias `http/https/http_api/api_http/rest/rest_api/httpreader`，由 `ApiConnectorTypes` 归一化） | 已落地；F5 前端必须只展示 `api`，alias 仅做后端容错 |
| 运行时阻断开关 | 当前在 `IngestionTaskService:721-723` 用硬抛阻断；Phase 2 上线前必须替换为 feature flag `dts.ingestion.api.runtime.enabled` | 见 F6/T05 发布门禁 checklist |

## 完成标准 ↔ Task 映射

| # | 完成标准 | 主导 Task | 协同 Task |
|---|---|---|---|
| 1 | API 数据源类型在平台管理、任务创建、能力契约和后端校验中均为一等类型 | F1/T03、F2/T01 | F1/T01、F5/T01 |
| 2 | 至少支持 `none/apiKey/bearerToken` 三种 Auth Provider 骨架，并能无破坏扩展 OAuth2、签名、mTLS | F1/T02 | F2/T02、F5/T02 |
| 3 | Preview 能对 JSON object/array/嵌套 record path 字段推断并输出 schema snapshot；字段编辑仅生成 stg mapping | F3/T01、F3/T02 | F3/T03、F5/T03 |
| 4 | 运行时支持 full_refresh 与 incremental cursor，并能持久化 checkpoint | F4/T01、F4/T04 | F4/T02、F4/T03 |
| 5 | 任务失败区分 AUTH/HTTP_4XX/HTTP_5XX/RATE_LIMIT/TIMEOUT/PARSE/PAGINATION/SCHEMA_DRIFT/ODS_WRITE 9 类 | F4/T05 | F6/T03、F6/T04 |
| 6 | 密钥不出现在数据库普通字段、前端响应、Airflow/Addax job、应用日志、审计 payload 明文 | F1/T05、F2/T04、F6/T01 | F4/T05、F6/T04 |
| 7 | API 接入任务原样落 ODS 后触发 schema snapshot、dbt source 刷新、stg 建模入口、目录注册、血缘同步、质量检查 | F3/T05 | F4/T01 |
| 8 | 有单元测试、契约测试、fixture mock API、集成 smoke 和手工验收 runbook | F6/T04 | F2/T05、F6/T05 |

## 覆盖率门槛（写入 F6/T04）

| 模块 | 工具 | 阈值 |
|---|---|---|
| `dts-ingestion` API 包 / connector / capability | jacoco | line ≥ 80% / branch ≥ 70% |
| `dts-platform` infra/api / secret / checkpoint | jacoco | line ≥ 80% / branch ≥ 70% |
| `dts-platform-webapp` API 接入向导 / provider form | vitest c8 | line ≥ 75% |
| 关键 5 条 E2E 流程 | Playwright/Cypress | 100% pass |

## 发布策略

| 阶段 | 范围 | 出口 |
|---|---|---|
| Phase 0 | 契约、Auth SPI、数据源模型、preview mock | 设计评审通过 |
| Phase 1 | API 数据源 CRUD、preview、schema snapshot、前端向导 | 可创建但默认不自动调度 |
| Phase 2 | 运行时、分页、cursor、checkpoint、失败分类 | 可灰度给内部测试租户 |
| Phase 3 | 审计、权限、观测、E2E、发布门禁 | 可作为正式产品能力交付 |

### Phase ↔ Feature/Task 对应

| Phase | 必须完成的 Task | 出口校验 |
|---|---|---|
| Phase 0 | F1/T01-T05、F2/T05、F6/T01（设计稿） | 顶层完成标准 1、2、6（仅契约面）通过；评审记录入库 |
| Phase 1 | F2/T01-T03、F3/T01-T03、F5/T01-T03 | 顶层完成标准 1、3 通过；ODS 仅保存原始 record + `_dts_*`，硬阻断仍生效，flag `runtime.enabled=false` |
| Phase 2 | F4/T01-T05、F3/T04、F5/T04-T05 | 顶层完成标准 4、5、7 通过；F6/T05 中"运行时阻断开关"项替换为 flag；灰度租户白名单生效 |
| Phase 3 | F2/T04、F6/T02-T05、覆盖率门槛 | 顶层完成标准 6、8 通过；jacoco/vitest/Playwright 阈值在 CI 强制 |

## 实现进展

### 2026-04-25

- `dts-ingestion` 新增 API source contract、Auth Provider Registry 和 contract/auth-provider 查询接口。
- `dts-ingestion` connector capability 默认种子新增 `api`，支持 `full_refresh` / `incremental`，声明 auth、pagination、schema preview、cursor checkpoint 和错误分类能力。
- `dts-ingestion` 创建任务时能识别 `api/http/rest/httpreader` 为 `api` connector；正式运行时暂时阻断，只允许保存草稿，避免 runtime 未完成前误执行。
- `dts-platform` 新增 API 数据源校验地基：要求 `baseUrl`、禁止 JDBC URL、禁止 token/secret 明文进入 props、自动补 `connectorType=api` 和 `readerType=httpreader`。
- `dts-platform` 将 `infra_data_source.props` 从 `varchar(2048)` 扩展为 `text`，为复杂 API 配置预留容量。
- `dts-platform` 代理新增 `/api/ingestion/api/contract` 和 `/api/ingestion/api/auth-providers`，为后续前端向导提供入口。
- `dts-ingestion` 新增 `SourceConnector`、`ExecutionPlan`、`SourceConnectorRegistry` 和 `ApiHttpSourceConnector`，为后续 API runner 接入预留执行策略扩展点。

### 2026-04-27

- 完成 Sprint-18 ODS/stg 约束回灌 review：详见 `review-20260427-sprint18-alignment.md`。
- `dts-ingestion` API contract 升级到 `1.1.0`，新增 `odsLanding` 输出，明确 API ODS 固定为 `_dts_raw_record` + `_dts_*` 技术字段。
- `ApiSourceContracts` 移除 ODS 语义的 `ApiFieldMapping`，改为 `ApiLandingPolicy`、`SchemaSnapshotPolicy`、`ApiStagingFieldMapping`。
- `dts-platform-webapp` API 接入分支移除 `apiFieldsJson` / `resource.fields` 提交入口，避免把字段重命名和类型标准化写入 ODS。
- Sprint-16 文档将 F3/F5 从“ODS 字段映射”调整为“schema snapshot + stg 映射”，并移除 Airbyte 作为当前方案候选。

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 鉴权机制后续变化大 | 重做表单和后端校验 | Auth Provider SPI + provider metadata 驱动表单 |
| API 返回结构不稳定 | stg 字段漂移、下游模型失败 | schema drift policy + preview report + snapshot/stg mapping version |
| 外部 API 限流或不稳定 | 任务长时间失败或拖垮对方系统 | rate limit、retry-after、backoff、熔断和并发配额 |
| 密钥泄露 | 安全事故 | secretRef、日志脱敏、审计、测试门禁 |
| Addax HTTP reader 能力不足 | 分页、鉴权、token refresh 难实现 | execution plan 允许 custom API runner |
