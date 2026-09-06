# T11 质量规则上下文与并发契约

`GET /api/modeling/plans/{planId}/release-candidates/{candidateId}/quality-context` 使用既有发布职责和计划/候选授权，只读返回当前候选已经核验的物化输出。它不创建目录资产、不按名称猜测资产，也不接受客户端给出的 datasetId。

每个 `assets[]` 项给出 `modelSpecId`、`modelRevision`、已持久化的 `datasetId`、`assetKey`、`qualifiedName`、`configurable`、阻断码及当前规则。`datasetId` 由候选已核验的 source/schema/table 查询已登记 CatalogDataset，重复或缺失时上下文不可用；不会回退到名称匹配或构造一个不存在的 UUID。

构建的关系核验事务在候选转为 `BUILT` 后登记未发布的质量资产。登记失败会回滚该转变并进入既有 `BUILD_FAILED` 重试路径，不会留下可发布而未登记的候选。登记只允许 `BUILT` 或 `QUALITY_RUNNING` 候选，且再次核验物理证据和模型 revision/checksum。

`primaryAction.code` 是只读 UI 指令：`CONFIGURE_QUALITY_RULES`、`RUN_QUALITY`、`RERUN_GOVERNANCE_QUALITY` 或 `NONE`，不是对 `WorkbenchView.allowedActions` 的扩写。首次执行走既有 `POST .../{candidateId}/quality`，以 `RUN_QUALITY`、当前 ETag 和幂等键将 `BUILT` 转为 `QUALITY_RUNNING` 并触发治理运行；只有已有失败/缺失/过期治理证据时，才可用 `POST .../governance-quality/runs` 重跑。

交付状态聚合只能在 `ModelReleaseCandidateApplicationService.canMaintainForRead(tenantId, actorId, planId)` 为真时展示派生的配置或重跑动作；普通读取权限不授予质量维护。`RUN_QUALITY` 还必须来自既有 `allowedActionsForRead`，不可由上下文合成。

规则更新必须携带 `expectedVersion`。服务端锁定规则、比较当前业务版本并返回 `409`（缺失、不可用或冲突），规则 `datasetId` 不可变；任何重绑请求均返回 `QUALITY_RULE_DATASET_REBIND_FORBIDDEN`。前端从模型入口固定 datasetId，安全保留内部 `returnTo`，失败时不导航。

质量执行器仍只支持默认数据湖来源。候选物化输出不在默认数据湖时上下文返回 `configurable=false` 和 `QUALITY_DATASET_NOT_DEFAULT_LAKE`；本任务不扩大质量执行或数据库服务权限。
