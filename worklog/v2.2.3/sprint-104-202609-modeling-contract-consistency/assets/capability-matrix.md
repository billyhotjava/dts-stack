# Sprint-104 实现输入与执行能力矩阵

日期：2026-09-06。此表是当前源码和已存在定向测试的能力声明，不是浏览器、部署或真实物化全部通过的证明。

## 输入方式：保存/提交边界

`ModelImplementationInputPolicy.allows` 是四类模型的唯一输入方式 owner；保存还必须通过输入引用校验。`ModelImplementationInputPolicyTest.enforcesFourModelInputMatrixAndRegisteredGenerator` 与 `rejectsUnconfirmedPhysicalBinding` 覆盖下表的正反边界。

| 模型类型 | PHYSICAL_ASSET | UPSTREAM_MODEL | GENERATED |
|---|---|---|---|
| DIMENSION | 可保存：当前租户/计划的来源绑定及版本必须确认 | 拒绝 `MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED` | 仅 `DATE_DIMENSION` 可保存；其他生成器拒绝 |
| FACT | 可保存：当前来源绑定及版本必须确认 | 可保存：必须是已固定、当前有效的上游 FACT 实现；自引用、循环、跨计划未发布或陈旧引用拒绝 | 拒绝 |
| SUMMARY | 拒绝 | 可保存：必须是已固定、当前有效且非 APPLICATION 的上游实现 | 拒绝 |
| APPLICATION | 拒绝 | 可保存：必须是已固定、当前有效上游实现 | 拒绝 |

“可保存”只说明输入契约通过，并不说明目标字段已经有来源值。`fieldMappings` 必须把每个需要从来源取得的目标字段映射到实际存在的来源字段；未映射的 TYPE2 生效起止/当前标志字段不能由 FULL 自动生成。当前运行样例已表明，来源只有 `project_id/month_id/amount/remark` 而目标 SQL 引用 `valid_from` 时，dbt 会失败。该事实是运行诊断证据，不应被误写成矩阵已验证成功。

## 加载与执行边界

`ModelImplementationExecutionPlanner.capabilities()` 与 `plan(...)` 共同定义保存后的可执行范围。`ModelImplementationExecutionPlannerTest.publishesTheSameExecutionBoundaryUsedByThePlanner`、`plansFullTableAndViewFromTheRealUiSettings`、`plansIncrementalOnlyWhenCanonicalKeyAndMaterializationAgree` 和 `failsClosedForMissingTargetSnapshotPartitionAndUnknownSettings` 是定向依据。

| loadStrategy | 保存/计划 | 执行产物 | 必要条件 | 明确不代表 |
|---|---|---|---|---|
| FULL | 支持 | `table` 或 `view` | postgres；合法 `targetPhysicalName`；`partitionFields=[]` | 不创建 TYPE2 历史版本，也不补齐未映射字段 |
| INCREMENTAL | 支持 | `incremental` | postgres；完整 canonical KEY；`partitionFields=[]`；materialization 必须为 `incremental` | 不因有 TYPE2 元数据获得历史维护 |
| SNAPSHOT | 拒绝 `IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED` | 无 | 不会降级为 FULL/INCREMENTAL | 没有 SNAPSHOT 执行能力 |

当前 adapter 仅为 postgres；`partitionFields` 当前不支持，非空时拒绝 `IMPLEMENTATION_PARTITION_UNSUPPORTED`。未知 settings、非法目标名和策略/物化冲突均在计划前拒绝。

## TYPE2 的配置与执行分离

`ModelSpecContract` 允许 TYPE2 元数据保存；`ModelSpecStageGateService` 在适用阶段要求 `effectiveFromField`、`effectiveToField` 和 `currentFlagField`。这允许用户编辑、草稿保存、恢复和提交模型修订。当前执行 planner 仅声明 FULL/INCREMENTAL，未声明 TYPE2 历史维护或 SNAPSHOT 执行引擎。因此：

- TYPE2 元数据可保存/提交，不等于可从任意来源执行历史维护。
- FULL 仅表示全量 table/view 物化，不等于历史维护。
- 执行前仍须有真实来源字段或明确生成/转换逻辑，以满足模型 SQL 的全部目标字段引用。

正式验收仍需在 `/opt/prod/s10/deploy` 的同一提交上分别验证接口、页面和实际 dbt/目标关系；本文件不替代这些证据。
