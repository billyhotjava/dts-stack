# 调用关系与分派图谱（T1）

- 源码基线：`72acb2d4d56667ed50bde899889b0e11ca327082`
- 路径已省略模块前缀：`platform/` = `source/dts-platform/src/main/java/com/yuzhi/dts/platform/`，`ingestion/`、`admin/`、`analytics/` 同理。
- 用途：回答"这个接口由谁实现、注入到哪里、运行时走哪个实现、父类/子类是什么"。
- 口径：只记录源码事实；Spring 代理（`*Repository`）与 `@Primary` 选择结果以注解为准，运行期 Bean 覆盖情况未验证。

## 1 接口 → 实现 → 注入点（T1 相关）

| 接口 | 实现 | 注入点（持有该类型的类） |
|---|---|---|
| `DbtExecutionGateway` | `platform/service/etl/AirflowDbtExecutionGateway.java:14` | `ModelMaterializationDispatchService` |
| `ModelLifecycleCompilerPort` | `platform/service/modeling/CanonicalModelLifecycleCompilerAdapter.java` | `ModelLifecycleService`、`ModelImplementationOwnershipTransitionService`、`DbtImplementationDraftService` |
| `ModelLifecycleTestEvidencePort` | `platform/service/modeling/DbtRunLifecycleTestEvidenceAdapter.java` | `ModelLifecycleService` |
| `ModelSpecPlanWriteAccessPort` | `platform/service/modeling/ModelSpecPlanWriteAccessAdapter.java` | `ModelLifecycleService`、`ModelSpecSourceValidationAdapter`、`CandidateGovernanceQualityRerunService`、`ModelDataRegistrationService` |
| `WarehousePlanOperationsReadPort` | `platform/service/modeling/warehouse/WarehousePlanOperationsReadAdapter.java` | `ModelReleaseCandidateApplicationService`、`OpsService` |
| `ReleaseDutyDirectoryPort` | `platform/service/admin/gateway/directory/AdminReleaseDutyDirectoryAdapter.java` | `ModelPublicationReviewReconciler` |
| `ReleaseCandidateWorkbenchEvidencePort` | `platform/repository/modeling/ReleaseCandidateWorkbenchEvidenceRepository.java` | `ModelReleaseCandidateApplicationService` |
| `GovernanceQualityRerunPort` | `platform/service/governance/GovernanceQualityRerunAdapter.java` | `CandidateGovernanceQualityRerunService` |
| `ModelImplementationDependencyReadPort` | `platform/repository/modeling/ModelImplementationDependencyReadAdapter.java` | `ModelImplementationDependencyService` |
| `ModelMaterializationPlanRelationPort` | `platform/repository/modeling/ModelMaterializationPlanRelationReadAdapter.java` | `ModelMaterializationPlanService` |
| `CatalogSourceReferenceReadPort` | `platform/service/catalog/JpaCatalogSourceReferenceReadAdapter.java:26` | `ModelClassificationPublishGate`、`ModelingSourceScopeGuard`、`SourceReferenceResolverAdapter` |
| `SourceReferenceResolver` | `platform/service/modeling/warehouse/SourceReferenceResolverAdapter.java:25` | `ModelSpecSourceValidationAdapter`、`WarehousePlanApplicationService`、`ModelSpecImportPreviewService` |
| `ModelSpecSourceValidationPort` | `platform/service/modeling/ModelSpecSourceValidationAdapter.java:29` | `ModelSpecApplicationService`、`ModelSpecStageGateService`、`ModelSourceFieldsService`、`CanonicalModelLifecycleCompilerAdapter` |
| `PhysicalPreviewAccessPort` | `platform/service/modeling/serving/DefaultPhysicalPreviewAccessAdapter.java:20` | `ModelPhysicalPreviewService` |
| `PhysicalPreviewPolicyPort` | `platform/service/modeling/serving/DefaultPhysicalPreviewPolicyAdapter.java:33` | `ModelPhysicalPreviewService` |
| `PhysicalPreviewQueryPort` | `platform/service/modeling/serving/PostgresPhysicalPreviewQueryAdapter.java:30` | `ModelPhysicalPreviewService` |
| `ModelGovernancePolicyPort` | `platform/service/modeling/JdbcModelGovernancePolicyAdapter.java:8` | `ModelSpecStageGateService`、`CandidateGovernanceQualityEvidenceService` |
| `GovernedStandardReadPort` | `platform/service/modeling/JpaGovernedStandardReadAdapter.java` | `CatalogColumnSyncService`、`CatalogAssetIdentityResolver`、`GovernanceStandardEvidenceReadService` |
| `CanonicalModelIdentityReadPort` | `platform/service/catalog/JdbcCanonicalModelIdentityReadAdapter.java` | `QueryDatasetService`、`CatalogAssetIdentityResolver`、`ModelQueryDatasetProjectionService` |
| `SourceConnector` | `ingestion/service/etl/api/ApiHttpSourceConnector.java:15` | `SourceConnectorRegistry`（构造注入 `List<SourceConnector>`，`ingestion/service/etl/connector/SourceConnectorRegistry.java:8`） |
| `ConfirmationPolicy` | `ingestion/service/etl/rollback/ModalConfirmationPolicy.java:6` | `DataRollbackService` |
| `GovernedAnalysisDatasetContractProvider` | `analytics/service/analysis/PlatformAnalysisDatasetContractClient.java:25` | `AnalysisApplicationService`、`AnalysisQueryGateway`、`AnalysisPublicationService` |

## 2 多实现接口与实际分派

| 多实现接口 | 实现 | 分派方式与证据 |
|---|---|---|
| `QueryGateway` | `HiveQueryGateway`、`JdbcQueryGateway`、`NoopQueryGateway` | `JdbcQueryGateway` 标注 `@Primary`（`platform/service/query/JdbcQueryGateway.java:33`）；`NoopQueryGateway` 为 `@ConditionalOnMissingBean` 兜底（`:12`）；`HiveQueryGateway` 仅出现在注释中的 legacy 行为参考（`JdbcQueryGateway.java:25,66`），未发现显式注入选择。注入点 `AssetResource`、`ExploreResource` 等 |
| `CatalogManagedCopyDestructionAdapter` | `ExternalJdbc…`、`ManagedDbt…`、`ManagedFile…`、`ManagedPostgres…` | 集合注入 `List<CatalogManagedCopyDestructionAdapter>`（`platform/service/catalog/CatalogLifecycleControlService.java:50`），按资产类型在服务内选择 |
| `CatalogClassificationProjection` | `CatalogConsumerDependencyProjection`、`CatalogDatasetClassificationProjection`、`CatalogGrantClassificationProjection` | 集合注入 `List<CatalogClassificationProjection>`（`platform/service/catalog/CatalogClassificationService.java:40`），全量遍历聚合 |
| `OncePerRequestFilter`（Spring） | 9 个子类（鉴权、审计、请求体限制等） | Servlet 过滤器链，全部注册为 Bean |
| `*Repository`（Spring Data） | 无手写实现 | 运行期 JDK/CGLIB 代理，不能按类继承关系阅读 |

## 3 抽象类与继承

| 父类/基类 | 子类/范围 | 证据 |
|---|---|---|
| `AbstractAuditingEntity<ID>` | 每个服务各有一份基类，JPA 实体全部继承（platform 侧约 130 个实体，含 `ChangeRequest`、`QueryDatasetVersion` 等） | `platform/domain/AbstractAuditingEntity.java`、`ingestion/domain/AbstractAuditingEntity.java`、`admin/domain/AbstractAuditingEntity.java` |
| `RuntimeException` | 平台内 24 个业务异常，如 `ModelSpecException`、`ModelReleaseCandidateException`、`ModelAuthoringException`、`WarehouseLayerException` | `platform/service/modeling/ModelSpecException.java` 等 |
| `IllegalArgumentException` / `IllegalStateException` | `IndicatorRequestException`、`StandardPackageContractException`、`CatalogTagMigrationConflictException`、`IndicatorConflictException` | inventory 对应条目 |
| `ErrorResponseException` | `BadRequestAlertException` | `platform/web/rest/errors/BadRequestAlertException.java` |
| `ResponseStatusException` | `CatalogAssetTagPermissionException` | `platform/service/catalog/CatalogAssetTagPermissionException.java` |
| `ResponseEntityExceptionHandler` | `ExceptionTranslator`（统一错误响应） | `platform/web/rest/errors/ExceptionTranslator.java` |
| `CompositeConverter` | `CRLFLogConverter` | `platform/config/CRLFLogConverter.java` |
| `SpringBootServletInitializer` | `ApplicationWebXml` | `platform/ApplicationWebXml.java` |
| `ApplicationRunner` | 3 个启动任务 | inventory |

> 说明：本项目"父类/子类"的主体是 **接口 → 单实现 + 构造函数注入**；真正的继承树集中在 JPA 实体基类与异常基类，业务分派靠 DI 而非继承。

## 4 分派形态汇总

| 形态 | 代表 | 定位 |
|---|---|---|
| 单实现端口注入 | `DbtExecutionGateway` 等 22 个端口（见 §1） | 各自注入点 |
| `@Primary` 多实现 | `JdbcQueryGateway` | `platform/service/query/JdbcQueryGateway.java:33` |
| `List<T>` 集合分派 | 销毁适配器、分类投影 | `CatalogLifecycleControlService.java:50`、`CatalogClassificationService.java:40` |
| 私有 if/else 分派 | 管理审批按 `resourceType` 分派 | `admin/web/rest/AdminApiResource.java:3381-3394` |
| 定时器驱动 | 物化分发（2s/30s）、语义同步（30s）、接入执行轮询（15s）、重试队列（30s） | `ModelMaterializationDispatchService.java:155,164`、`CatalogModelSemanticSyncWorker.java:29`、`AirflowExecutionSyncService.java:192`、`ingestion/service/etl/IngestionRetryService.java:56` |
| 事件/Outbox | serving 投影事件、审计 outbox、管理通知 | `CatalogModelServingService.java:177`、审计/通知客户端 |

## 5 T1 主链调用索引

| 主链 | 文档 | 关键分派点 |
|---|---|---|
| 建模主链 | [01-modeling-mainline.md](../01-modeling-mainline.md) | 发布单动作 → `ModelReleaseCandidateApplicationService`；构建 → `DbtExecutionGateway`（Airflow 实现）；发布 → `CandidatePublicationCommitService` → `CatalogModelServingService` → 定时语义同步 → `ModelQueryDatasetProjectionService` |
| 接入执行链 | [02-ingestion-execution.md](../02-ingestion-execution.md) | 任务 → `IngestionTaskService` → `AddaxJobService`/`AirflowAdapter`；API 分支 → `SourceConnectorRegistry` → `ApiHttpSourceConnector` |
| 管理审批链 | [03-admin-approval.md](../03-admin-approval.md) | `AdminApiResource` 私有 `applyChangeRequest` if/else；事务模板 `changeApplyTx` |
| 分析消费链 | [04-analytics-consumption.md](../04-analytics-consumption.md) | `GovernedAnalysisDatasetContractProvider` → `PlatformAnalysisDatasetContractClient`；查询 → `AnalysisQueryGateway` → `QueryExecutionFacade` |
