# Sprint-104 后端契约源码证据

日期：2026-09-06。本文只记录当前源码与定向测试落点；不是部署、数据库迁移执行、接口联调或浏览器验收证据。

## T02：保存、草稿与阶段

| 结论 | 已核实源码证据 | 定向测试 |
|---|---|---|
| PUT/可编辑视图可缺少稳定业务上下文 | `ModelSpecContract.validateUpdate` 与 `validateEditableView` 过滤稳定上下文必填项；`ModelSpecApplicationService.update` 仍执行格式、归属、租户上下文和引用校验。 | `ModelSpecBusinessContextContractTest.editableDraftDefersMissingStableContextButRejectsSuppliedWrongTypeContext`：FACT 草稿缺 `businessProcessId` 可通过 update/editable 校验。 |
| 交付/阶段仍要求稳定上下文 | `validateDeliverableCreate` 对 FACT 要求 `businessProcessId`，对 APPLICATION 要求 `dataMartId` 与 `subjectDomainId`；`validateView` 走该交付校验。阶段门禁以 `DRAFT_SAVE`、`DESIGNED`、`IMPLEMENTATION_READY`、`RELEASE_READY` 分别评估。 | 同上测试断言交付/视图校验阻断缺失 FACT 业务过程；`ModelSpecStageGateServiceTest` 覆盖阶段门禁。 |
| 已填错类型或无效归属不因草稿豁免 | `validateCreate` 保留跨类型字段拒绝；`ModelSpecApplicationService.validateDataMartContext/validateBusinessContext` 用 tenant/domain/data-mart/confirmed-process 查询校验已提供引用。 | 同上测试断言 FACT 填 `subjectDomainId` 仍报 `MODEL_SPEC_SUBJECT_DOMAIN_NOT_ALLOWED`；`ModelSpecApplicationServiceTest` 覆盖业务过程、数据集市、主题域上下文无效。 |
| 编译前键集合门禁 | `ModelSpecStageGateService` 仅在 IMPLEMENTATION_READY/RELEASE_READY 映射 grain.keys 与 fields.KEY 的重复、无效编码、未知字段和集合不一致为 T04 统一错误码。 | `ModelSpecStageGateServiceTest.implementationStagesRejectGrainKeyContractWithoutBlockingDraftOrDesignedStages`。 |
| 创作草稿保存、提交和 CAS | REST：`PUT /api/modeling/model-specs/{modelSpecId}/authoring-drafts/{draftId}` 保存，`POST .../{draftId}/commit` 提交；服务 `DbtImplementationDraftService.saveAuthoring/commit` 以 ETag、租户、模型、参与者和草稿 ID 定位。 | `DbtImplementationDraftServiceSecurityTest.commitsUnifiedAuthoringSnapshotAndProjectedFieldsThroughOneModelRevisionBoundary` 覆盖统一快照提交与单一模型修订边界。 |
| 存储落点 | `modeling_dbt_implementation_draft` 保存 tenant/plan/model/actor、基线修订与 checksum、ETag、快照、提交回执；`modeling_dbt_implementation_draft_file` 保存草稿文件。唯一约束覆盖 tenant+plan+model+actor+idempotency key 和 ETag。 | Liquibase `20260802_02_modeling_dbt_implementation_draft.xml`，扩展快照字段的 `20260819_02_model_authoring_draft_expand.xml`。 |

## T07：输入与历史执行能力

| 范围 | 当前源码结论 | 定向测试 |
|---|---|---|
| 输入和执行能力 | `ModelImplementationExecutionPlanner.capabilities` 公开 postgres、四类模型输入方式、FULL/INCREMENTAL，以及 FULL→table/view、INCREMENTAL→incremental。 | `ModelImplementationExecutionPlannerTest.publishesTheSameExecutionBoundaryUsedByThePlanner`、`plansFullTableAndViewFromTheRealUiSettings`、`plansIncrementalOnlyWhenCanonicalKeyAndMaterializationAgree`。 |
| SNAPSHOT 执行 | planner 在执行计划生成前返回 `IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED`；没有将其降级为 FULL/INCREMENTAL。 | `failsClosedForMissingTargetSnapshotPartitionAndUnknownSettings`。 |
| TYPE2 | `ModelSpecContract` 允许 TYPE2 元数据；`ModelSpecStageGateService` 只在进入阶段约束时要求生效起止和当前标志字段。当前源码没有 TYPE2 历史维护执行引擎。 | `ModelSpecStageGateServiceTest` 的 DRAFT_SAVE/TYPE2 阶段反例是配置和阶段证据，不能推断历史执行成功。 |

正式验证应在 `/opt/prod/s10/deploy` 的同一提交上执行聚焦 Maven 测试；当前开发目录未运行会触发编译的 Java 测试。
