# T03: CodeAssetGrantWriter 接入

**优先级**: P0
**状态**: DONE
**依赖**: T01, T02

## 目标

把已有 `GovIndicator*`、`Modeling*`、`DataStandard`、`Glossary`、`SvcApi*` 等代码化资产写入 platform asset 事实源，避免这些实体绕过 `asset_id / asset_key / asset_grant`。

## 待完成范围

- [x] 接入 `GovIndicatorDefinition` 保存、更新、发布、归档链路。
- [x] 接入 `ModelingSqlModel` 保存、更新链路。
- [x] 当前版本写入 `asset_type`、`asset_key`、`asset_id`、ownerDept、classification、lifecycleStatus 到 `asset_ownership` / `asset_grant` 可消费路径。
- [ ] `GovIndicatorTemplate`、`ModelingPlan`、`DataStandard`、`ModelingGlossaryTerm`、`SvcApi*` 的完整 writer 接入转入后续增量。

## 验收建议

- [x] 至少 `GovIndicatorDefinition` 与 `ModelingSqlModel` 两个高频实体有实际 writer 调用方。
- [x] `CodeAssetGrantWriterTest` 覆盖 ownerDept ownership 与 MANAGE grant 写入。
- [ ] 软删除/退役状态和剩余代码化资产 writer 补全作为后续任务继续推进。
