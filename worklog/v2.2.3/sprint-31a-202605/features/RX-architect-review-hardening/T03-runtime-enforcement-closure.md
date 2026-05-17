# T03: CodeAssetGrantWriter 接入

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标

把已有 `GovIndicator*`、`Modeling*`、`DataStandard`、`Glossary`、`SvcApi*` 等代码化资产写入 platform asset 事实源，避免这些实体绕过 `asset_id / asset_key / asset_grant`。

## 待完成范围

- [ ] 接入 `GovIndicatorDefinition` / `GovIndicatorTemplate` 保存和发布链路。
- [ ] 接入 `ModelingSqlModel` / `ModelingPlan` 保存和发布链路。
- [ ] 接入 `DataStandard` / `ModelingGlossaryTerm` 保存和审核链路。
- [ ] 对 `SvcApi*` 暴露的数据服务资产生成 `API_SERVICE` asset identity。
- [ ] 写入结果必须包含 `asset_type`、`asset_key`、`asset_id`、owner、classification、lifecycleStatus。

## 验收建议

- 至少 `GovIndicatorDefinition` 与 `ModelingSqlModel` 两个高频实体有实际 writer 调用方。
- writer 单测覆盖重复写入幂等、软删除/退役状态、缺治理字段进入 `PENDING_GOVERNANCE`。
- `asset_grant` 能对上述资产执行 READ / EDIT / MANAGE 检查。
