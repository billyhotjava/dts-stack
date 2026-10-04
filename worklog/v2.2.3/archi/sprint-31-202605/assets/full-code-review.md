# DTS 企业级数据平台全量代码评审

**评审日期**: 2026-05-16
**评审范围**: `source/dts-platform`、`source/dts-ingestion`、`source/dts-analytics`、`source/dts-platform-webapp`、`worklog/v2.2.3`。
**评审方法**: GitNexus 全量索引 + 模块结构扫描 + 关键链路源码深挖。GitNexus 当前索引为 `s10-stack`，约 7,194 文件、115,878 symbols、300 execution flows；本次重点抽查了企业数据平台主链路，不等价于逐行审计所有 UI 细节。

## 总体判断

DTS 当前已经不是空壳，也不只是 demo。数据接入、ODS 生成、Addax/Airflow 执行、dbt source、OpenMetadata、本地资产目录、资产授权、语义指标、大屏权限都已有实现。但它仍处在“模块能力拼起来”的阶段，离企业级大数据平台差在三件事：

1. **缺少强契约主链路**：Source -> ODS -> DWD/DWS/ADS -> Catalog -> Semantic -> BI/Dashboard 没有一个统一的状态机、验收脚本和发布门禁。
2. **治理和运行信号不够硬**：质量门禁多为 warning，血缘失败多为 best effort，自动创建资产缺治理属性，性能预检可能压垮大表。
3. **权限事实源仍需收口**：platform `asset_grant` 已经在大屏权限里成为主路径，但 analytics 本地 fallback、creator bypass、缓存和本地语义权限仍需明确迁移边界。

## P0 发现

| 编号 | 问题 | 证据 | 影响 | 修复方向 |
|---|---|---|---|---|
| P0-1 | 黄金链路没有统一契约 | `sprint-18/20/21` 分别覆盖接入、血缘、Connector；`SemanticModelingService` 又单独生成 DWS/ADS | 每个模块看似可用，但客户无法按一条标准链路验收 | 定义 `golden-path` API/脚本/状态机，作为 Sprint-31 F1 |
| P0-2 | Connector Center 仍明显 JDBC-first | `InfraDataSourceResource` 的 `/schema-discover`、`/ods-preview`、`/ods-apply`、`/ods-precheck`、`/sync-task-draft` 均拒绝非 JDBC | API/文件接入能测连或建任务，但不能与 ODS/dbt/source 主链路等价 | 为文件/API 定义单独契约和页面边界；JDBC 主链路先做强验收 |
| P0-3 | dbt 发布门禁 warning-only | `DbtQualityGateService` 明确“质量门不阻塞上线”；`DbtReleaseGateService` 也把构建证据检查降级为 warning | 生产模型可在测试失败、缺 schema、构建过期时上线 | 增加环境级开关：prod 严格阻断，dev/demo warning |
| P0-4 | 运行血缘不够可信 | `AirflowDagService` 依赖 tableMapping 生成 OpenLineage；`OpenLineageReceiverResource` 自动创建 dataset 但缺 source/owner/classification | 血缘图可能完整度不稳定，且会生成未治理资产 | OpenLineage 接收端统一资产解析，不可解析时进入 PENDING_GOVERNANCE |
| P0-5 | 增量水位有跳数风险 | `IncrementalSyncService` 成功后查询源端 `MAX(watermark)` 更新 checkpoint | 作业运行期间源端新增数据可能被水位跳过 | 运行前记录 source upper bound，或以目标端实际落地最大值更新 |
| P0-6 | 语义指标中心缺严肃指标契约 | `SemanticModelingService` 已有公式 JSON 和 SQL 生成，但 schema.yml 只写 meta，公式 DSL 缺版本/口径/质量规则 | 可演示 DWS/ADS，但难以让客户放心复用指标口径 | 补指标 DSL、口径版本、单位格式、预警、质量测试和血缘绑定 |

## P1 发现

| 编号 | 问题 | 证据 | 影响 | 修复方向 |
|---|---|---|---|---|
| P1-1 | 数据源变更只写影响记录，不阻断任务 | `InfraManagementService.notifyIngestionTasks` 生成 PENDING 变更日志 | 数据源改密码/URL 后任务可能继续用旧假设运行 | 数据源变更触发任务 `NEEDS_REVIEW`，严重变更暂停自动调度 |
| P1-2 | ODS 生成仍是简化版 | `OdsGenerationService` Addax draft 只做 reader/writer 基本映射，增量列按所有表共用字段解析 | 多表异构、技术字段、schema evolution 都不足 | 拆分单表契约，支持 per-table incremental 和字段映射差异 |
| P1-3 | 大表预检会执行 `count(*)` | `OdsPrecheckProbeService` 对源表执行 count 探测 | 大表/生产库预检可能慢或影响源库 | 改为采样、估算、可配置超时和慢查询保护 |
| P1-4 | 资产门户 OpenMetadata 列表未按 `canRead` 过滤 | `CatalogAssetPortalService.listAssets` 对 OM cache 直接分页转 summary；详情页才检查 `canRead` | 列表可能暴露资产存在性和元数据摘要 | 列表层也应用 extension/legacy 权限过滤，或只展示治理可见资产 |
| P1-5 | analytics 权限 fallback 仍会合并本地授权 | `ScreenPermissionService.listAccessibleScreenIds` 在 platform 结果后合并 local ids 并告警 | “platform 唯一事实源”还不是硬边界 | 迁移完成后默认关闭 local fallback，仅保留 break-glass |
| P1-6 | 语义模型发布后的运行状态缺自动回写闭环 | `triggerModelRun` 写 `RUNNING`，结果依赖显式 `updateModelRun` | Airflow 成功/失败不一定回写 semantic_model_run | 接入 Airflow run polling / callback，自动更新 run 和 model 状态 |

## P2 发现

| 编号 | 问题 | 证据 | 影响 | 修复方向 |
|---|---|---|---|---|
| P2-1 | 前端仍有“后续 API 缺口”提示 | `SemanticPublishPage` 标注 Superset 远端 Dataset 同步缺口 | 客户容易认为语义发布未完成 | Sprint-31 先接 QueryDataset，下一步再接远端 Superset |
| P2-2 | 本地 Catalog / OpenMetadata 双目录仍并存 | `CatalogAssetPortalService` 合并 OM cache + legacy catalog | 运维和治理口径可能混乱 | 明确 OpenMetadata 优先、本地 catalog fallback 的治理状态 |
| P2-3 | analytics 自有语义模型仍存在 | `SemanticQueryService` 使用 `AnalyticsSemanticModel/Metric/Field` | platform 语义中心和 analytics 语义查询可能形成双语义层 | 本版本只收敛权限；下一版本规划 IAM/语义消费边界剥离 |

## 模块结论

### 数据接入 / Connector Center

现状可交付 JDBC 主链路，但 API/文件和 JDBC 不是同一等级。`InfraDataSourceResource` 对非 JDBC 的 ODS 生成、预检、同步任务草案明确拒绝；`IngestionTaskResource` 对 API 有特殊 task 生成路径，但还不是统一的 ODS/dbt source 产品契约。Sprint-31 应先把能力边界写清楚，再补 JDBC 黄金链路和文件/API 的最小正式契约。

### dbt / 数据开发

dbt 仍是当前最稳的主建模引擎。平台已有 dbt source 刷新、模型文件写入、发布触发和 build evidence，但质量门禁没有阻断性。企业级版本必须让 `compile/test/build`、schema contract、类型元信息、owner/classification、lineage diff 进入阻断门禁。

### 血缘 / 资产治理

已存在 Addax declared lineage、OpenLineage receiver、dbt manifest import、OpenMetadata cache 和本地 Catalog fallback，但链路分散。关键风险是“血缘写入失败不影响主流程”和“自动创建资产治理信息不足”。企业级方向应把血缘变成发布和运行的硬证据，而不是旁路日志。

### 语义指标中心

后端已具备基础形态，不是纯 demo：可维护主题域、对象、维度、指标、DWS/ADS、生成 dbt、审核、发布、注册 BI Dataset、注册血缘。短板在于公式 DSL、指标口径版本、质量测试、真实 BI 注册、运行回写和业务友好的模型审核流。

### BI / 大屏 / analytics

analytics 仍是消费层和设计器承载方，platform-webapp 已合并入口。当前大屏权限方向正确：读写走 platform asset_grant，local table 作为 fallback。但要成为企业级权限边界，需要迁移存量 grants、关闭默认 local fallback、缓存失效和 fallback 告警报表。

### 权限 / 安全

platform 已具备 `asset_grant`、数据密级、数据集访问审批、服务间鉴权拆分。下一步不是马上大改 IAM，而是先让平台成为权限事实源，所有消费侧只读 platform 授权结果。

## Sprint-31 推荐执行顺序

1. 先做 F1 黄金链路契约，避免继续按模块零散修补。
2. 再补 F2/F3/F4，把接入、血缘、dbt 发布变成强可验收链路。
3. 然后做 F5/F6，让语义指标和大屏消费承接治理后的资产。
4. 最后做 F7，把性能、审计、告警、IT 证据补齐。

## 下一代预研方向

生产路线继续保持 `Addax + dbt + Airflow + OpenMetadata + Superset/自研大屏`。下一代预研可放在 v2.3.x：

- `dlt` 补 API/JSON 接入。
- `SQLMesh` 预研模型版本和环境隔离。
- `Dagster/Kestra` 预研资产编排和可视化工作流。
- `OpenLineage` 做运行血缘事实源。
- IAM 剥离在权限事实源收口后再做。
