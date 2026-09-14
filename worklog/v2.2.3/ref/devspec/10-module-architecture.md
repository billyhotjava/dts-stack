# DTS 模块级架构与模块间调用关系

- 日期：2026-09-14
- 源码基线：`da531fb16`（v2.2.3 分支；绘制期间合入的提交只改动 6 个源码文件，不涉及模块结构）
- 路径前缀：`platform/` = `source/dts-platform/src/main/java/com/yuzhi/dts/platform/`，`ingestion/`、`admin/`、`analytics/` 同理。
- 对应图：10 系统模块全景、11 dts-platform 核心业务模块、12 dts-platform 支撑模块、13 dts-ingestion、14 dts-admin、15 dts-analytics。

## 1 口径

- **模块**：服务内 `service/` 下的功能子包（如 `platform/service/modeling`），或职责内聚的一组类（如 dts-ingestion 的 `IngestionTaskService`）。
- **调用关系**：箭头从调用方指向被依赖方。服务内依据 `import` 统计与构造注入字段；跨服务依据 HTTP 客户端的请求路径。
- **只画主关系**：每张图不超过 12 个模块；次要依赖写在图下方卡片中。
- 本文记录源码与配置事实，不代表运行、部署或业务验收结论。

## 2 服务与模块规模

| 服务 | 主要模块（`service/` 子包，括号内为类数量） |
|---|---|
| dts-platform | modeling(289)、catalog(135)、governance(126)、goldenchain(75)、sql(70)、infra(54)、services(30)、security(22)、etl(22)、iam(16)、workbench(15)、audit(14)、admin(11)、permission(8)、development(8)、event(6)、ingestion(6)、integration(6) 等 |
| dts-ingestion | 服务根包（任务、执行提交/命令/查询、质量工作流）、etl(61，含 api/connector/rollback)、dto(19)、audit(5)、infra(5) |
| dts-admin | audit(32)、infra(21)、personnel(8)、keycloak(4)、ops(4)、pki(2)、user(2)、approval/mdm/notify/workflow，以及服务根包的 `ChangeRequestService`、`OrganizationService`、`PortalMenuService` |
| dts-analytics | 服务根包(61，大屏、查询执行、分类与权限客户端)、analysis(21)、publication(6)、projectcockpit(5)、semantic(2)、audit(2) |

## 3 系统模块全景（图 10）

| 调用方模块 | 被调用方模块 | 关系 | 依据 |
|---|---|---|---|
| platform modeling | platform sql | 数据集投影 | `platform/service/modeling/serving/ModelQueryDatasetProjectionService.java` 引用 `QueryDatasetContractSnapshotAssembler` |
| platform modeling | platform catalog | 资产键、分类门禁（88 处 import） | `ModelClassificationPublishGate` 引用 `CatalogClassificationBoundary` |
| platform modeling | platform etl | 构建计划（35 处 import） | `PlanOperationalRunService` 引用 `AirflowClient`、`DbtScopedProjectService` |
| platform modeling | platform governance | 指标、质量复跑（38 处 import） | `ModelSpecMetricReferenceService` 引用 `IndicatorDto` |
| platform modeling | analytics analysis/publication | 语义发布 HTTP | `AnalyticsSemanticPublishClient` → `/api/semantic/publish` |
| analytics analysis | platform sql | 数据集契约读取 HTTP | `PlatformAnalysisDatasetContractClient` → `web/rest/internal/AnalysisDatasetContractResource` → `PublishedQueryDatasetService` |
| platform modeling | admin 组织人员与目录 | 部门、人员目录 | `ModelSpecAccessService` 引用 `AdminDirectoryGateway` → admin `web/rest/platform/PlatformDirectoryResource` |
| admin 变更审批 | admin 组织人员 | 变更分派执行 | `AdminApiResource#applyChangeRequest` |
| platform ingestion（接入准入与代理） | platform catalog | 分类准入 | `IngestionClassificationAdmissionService` 引用 `CatalogClassificationService` |
| platform ingestion | ingestion 任务 | 任务代理 HTTP | `IngestionServiceClient` → `/api/ingestion/tasks` |
| ingestion 任务 | ingestion etl | 两阶段执行 | `IngestionTaskService` 注入 `AddaxJobService`、`AirflowDagService` |
| ingestion etl | Airflow / Addax | 作业与 DAG 触发 | `AirflowClient` → `/api/v1` |
| platform etl | Airflow / dbt | dbt DAG 触发 | `AirflowDbtExecutionGateway`（`DbtExecutionGateway` 实现） |
| ingestion etl | platform catalog | 执行血缘回写 HTTP | `AirflowExecutionSyncService` → `PlatformInfraClient#syncIngestionExecutionLineage` → `/catalog/lineage/ingestion-executions` |

## 4 dts-platform 核心业务模块（图 11）

| 模块 | 职责 |
|---|---|
| `web/rest`（约 120 个 `*Resource`） | 前端业务 API 入口 |
| `web/rest/internal` | 服务间内部接口：分析契约、资产权限、报表登记、运行回写、OpenLineage |
| `service/modeling` | 规划、模型定义、加工配置、构建计划、发布单、serving 投影 |
| `service/catalog` | 数据资产目录、分类分级、血缘、生命周期 |
| `service/governance` | 标准、指标、维度、数据质量 |
| `service/sql` | SQL 工作台、查询数据集（QueryDataset）与发布契约 |
| `service/etl` | dbt 项目、运行配置租约、Airflow 调度 |
| `service/security` | 访问检查、组织可见性、SQL 改写、脱敏 |
| `service/permission` | 资产授权、访问登记、看板分享 |
| `service/services` | 数据 API、令牌、限流、数据产品 |
| `service/admin/gateway` | 调用 dts-admin 的目录、审计、认证网关 |

| 调用方 | 被调用方 | 关系 | 依据 |
|---|---|---|---|
| web/rest | modeling | 建模 API（330 处 import） | `ModelReleaseCandidateResource` 等 |
| internal | sql | 契约读取 | `AnalysisDatasetContractResource` → `PublishedQueryDatasetService` |
| internal | modeling | 运行回写 | `PlanExecutionInternalResource` 引用 `FinalizeCommand`、`RunArtifactView` |
| modeling | catalog / governance / etl / sql | 见第 3 节 | 同上 |
| modeling | admin/gateway | 部门、人员目录 | `ModelSpecAccessService`、`WarehousePlanAuthorizationGuard` |
| governance | security | 访问检查、SQL 改写（23 处 import） | `DimensionService` 引用 `AccessChecker`；`IndicatorService` 引用 `SecuritySqlRewriter` |
| catalog | permission | 资产授权（11 处 import） | `CodeAssetGrantWriter` 引用 `AssetPermissionService` |
| catalog | security | 访问检查（10 处 import） | `CatalogLifecycleProjectionService` 引用 `AccessChecker` |
| etl | catalog | dbt 资产同步（21 处 import） | `DbtAssetSyncService` 引用 `CatalogColumnSyncService` |
| services | catalog | 分类派生（13 处 import） | `DataProductService` 引用 `CatalogConsumerClassificationService` |

未画出：`services → security`（`SvcApiQueryService` 引用 `CatalogMaskingService`）、`sql → query`（`SqlExecutionService` 引用 `QueryGateway`）、`internal → permission`（`AssetPermissionInternalResource`）、`internal → catalog`（`OpenLineageReceiverResource`）。

## 5 dts-platform 支撑模块（图 12）

| 调用方 | 被调用方 | 关系 | 依据 |
|---|---|---|---|
| internal | rollback | 回滚完成回执 | `RollbackInvalidationCompletionResource`（5 处 import） |
| internal | visualization | 报表登记 | `ReportRegistrationInternalResource` → `ReportRegistrationService` |
| visualization | catalog | 报表资产 | 4 处 import |
| workbench | catalog | 资产概览 | 2 处 import |
| 核心业务模块 | event | 投影事件 outbox | `CandidateRollbackCommitService` 引用 `PlatformEventOutboxService`；投递 `PlatformEventKafkaDispatcher` |
| 核心业务模块 | catalog | 资产、分类 | 第 4 节 |
| catalog | openmetadata | 元数据同步 | `OpenMetadataAssetSyncService` 引用 `OpenMetadataClient` |
| 核心业务模块 | query | SQL 执行 | `SqlExecutionService`、governance 引用 `QueryGateway`（`JdbcQueryGateway` 为 `@Primary`） |
| 核心业务模块 | audit | 操作审计（modeling 27 处 import） | `AuditService` |
| query | infra | 数据源连接（6 处 import） | — |
| infra | catalog | 数据源资产同步（36 处 import） | `JdbcCatalogSyncService`、`PostgresCatalogSyncService` |
| infra | admin/gateway | 基础设施同步 | 4 处 import |
| audit | admin/gateway | 审计上报 HTTP | `AdminAuditGateway` |

## 6 dts-ingestion（图 13）

| 调用方 | 被调用方 | 关系 | 依据 |
|---|---|---|---|
| `IngestionTaskResource` | `IngestionExecutionSubmissionService` | 提交执行 | 构造注入 |
| `IngestionExecutionSubmissionService` | `IngestionTaskService` | 两阶段执行 | 构造注入 |
| `RollbackResource` | `DataRollbackService` | 回滚请求 | 构造注入 |
| `IngestionTaskService` | `SourceConnectorRegistry` / `ApiIngestionExecutor` | API 源执行 | 构造注入 |
| `IngestionTaskService` | `AddaxJobService` | 生成作业 | 构造注入 |
| `IngestionTaskService` | `AirflowDagService` / `AirflowAdapter` | DAG 触发 | 构造注入 |
| `IngestionTaskService`、`AddaxJobService`、`AirflowDagService` | `ModelTargetGuard` | 目标表校验 | `ModelTargetGuard` 被 5 个类引用 |
| `IngestionTaskService` | `PostIngestionQualityWorkflowService` | 入湖后质量 | 构造注入 |
| `ModelTargetGuard` | `PlatformInfraClient` | 目标校验 HTTP | `/internal/modeling/ingestion-targets/validate` |
| `PostIngestionQualityWorkflowService` | `PlatformInfraClient` | 质量运行 HTTP | `/governance/quality/runs` |
| `AirflowExecutionSyncService` | `PlatformInfraClient` | 执行血缘 HTTP | `/catalog/lineage/ingestion-executions` |

未画出：`AirflowExecutionSyncService → PostIngestionQualityWorkflowService`、`IngestionRetryService → IngestionTaskService`、`DataRollbackService → AddaxJobService / AirflowDagService`。`AuditService` 当前只写日志。

## 7 dts-admin（图 14）

| 调用方 | 被调用方 | 关系 | 依据 |
|---|---|---|---|
| `AdminApiResource` | `ChangeRequestService` | 提交、审批 | 构造注入 `crRepo`、`changeRequestService` |
| `AdminApiResource` | `AdminUserService` / `AuditV2Service` / `OrganizationService` | 用户操作、审计写入、组织变更 | 构造注入 |
| `KeycloakApiResource` | `PkiChallengeService` / `PkiVerificationService` | 挑战与验签 | 引用 |
| `AdminUserService` | `KeycloakAdminClient` | 用户、角色 | 构造注入 |
| `web/rest/platform/*` | `KeycloakAdminClient` | 目录查询 | `PlatformDirectoryResource`、`ReleaseDutyInternalResource` |
| `OrganizationService` | `KeycloakAdminClient` | 组织同步 | 构造注入 |
| `KeycloakUserProvisioningService` | `KeycloakAdminClient` | 账号开通 | 构造注入 |
| `MdmGatewayService` | `OrganizationService` / `PersonnelImportService` | 组织拉取、人员导入 | 构造注入 |
| `AuditIngestResource` | audit | 外部审计入库 | `AuditIngestIdempotencyService` |

未画出：`AdminUserService → ChangeRequestService`、`AdminApiResource → DtsCommonNotifyClient`、`InfraAdminService → PlatformInfraClient / IngestionInfraClient`。

## 8 dts-analytics（图 15）

| 调用方 | 被调用方 | 关系 | 依据 |
|---|---|---|---|
| `CardResource` / `DashboardResource` / `AnalysisResource` | `AnalysisQueryGateway` | 受治理查询 | 构造注入 |
| 同上 | `DashboardPublicationService` / `AnalysisPublicationService` | 发布 | 构造注入 |
| 同上 | `AnalyticsConsumerClassificationService` | 分类校验 | 构造注入 |
| `ScreenResource` | `ScreenPermissionService` / 分类服务 | 大屏权限 | 构造注入 |
| `AnalysisQueryGateway` | `GovernedAnalysisDatasetContractProvider` | 契约读取 | 实现为 `PlatformAnalysisDatasetContractClient` |
| `AnalysisQueryGateway` | `QueryExecutionFacade` | SQL 执行 | 构造注入 |
| `AnalysisPublicationService` | `AnalysisApplicationService` / 契约提供方 | 定义快照、契约校验 | 构造注入 |
| `SemanticQueryService` | `AnalysisQueryGateway` | 指标计划查询 | 构造注入；另调 `PlatformIndicatorPlanClient` |
| `QueryExecutionFacade` → `DatasetQueryService` | `ExternalDatabaseDataSourceRegistry` | JDBC 查询 | 构造注入 |
| `SemanticPublishResource` | `PlatformAnalyticsDatabaseRegistrationService` | 登记分析库 | 构造注入 |
| `PlatformAnalyticsDatabaseRegistrationService` → `MetadataSyncService` | `ExternalDatabaseDataSourceRegistry` | 元数据同步 | 构造注入 |

跨服务出口：`PlatformReportRegistrationClient` → `/internal/reports/registrations`；`AnalyticsClassificationClient` → `/api/catalog/classifications/consumers/*`；`PlatformPermissionClient` → `/api/internal/asset-permission/check`。

## 9 待确认

- 服务内关系按 `import` 与构造注入统计，未区分运行期开关（如 Kafka、OpenMetadata 同步是否启用）。
- `service/goldenchain`（75 类）、`iam`、`development` 等模块未进入主图，需要时可单独出图。
