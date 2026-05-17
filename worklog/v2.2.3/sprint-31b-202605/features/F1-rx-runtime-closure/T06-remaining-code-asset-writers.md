# T06: 剩余 code asset writer 接入

**优先级**: P0
**状态**: READY
**依赖**: Sprint-31A RX/T03

## 目标

把 Sprint-31A RX/T03 未接入的 `DataStandard`、`ModelingGlossaryTerm`、`SvcApi*` 三类高频 code asset 也接入 `CodeAssetGrantWriter`，使所有代码化资产都拥有 `asset_ownership` + 自动 `MANAGE` grant。

## 背景

RX/T03 当前只接入 `GovIndicatorDefinition` 和 `ModelingSqlModel`：

> `GovIndicatorTemplate`、`ModelingPlan`、`DataStandard`、`ModelingGlossaryTerm`、`SvcApi*` 的完整 writer 接入转入后续增量。

但 IdentityResolver 已经能解析 `DataStandard / Glossary / SvcApi`（含 F1/T03），没有 grant writer 意味着这些资产被解析出来后仍然没有 `asset_grant` 记录，权限校验只能 deny —— 形成"事实源缺权限"。

`GovIndicatorTemplate` 与 `ModelingPlan` 是低频实体，本 Sprint 不在本任务范围。

## 技术设计

1. **DataStandard**：在 `DataStandardService.save/update/publish/archive` 调用 `codeAssetGrantWriter.upsertCodeAsset(...)`，identity 类型 `DATA_STANDARD`，ownerDept 取自 standard.ownerDept，lifecycle 按 status 映射（同 IndicatorService.lifecycleForStatus）。
2. **ModelingGlossaryTerm**：在 `ModelingGlossaryTermService.save/update/publish` 写 identity 类型 `GLOSSARY_TERM`。
3. **SvcApi**：在 `SvcApiDefinitionService.save/publish/deprecate` 写 identity 类型 `API_SERVICE`。
4. 所有 writer 调用复用 `lifecycleForStatus` 公共方法（提取到 `CodeAssetLifecycleMapper` helper，避免三个 service 各写一份）。
5. **不重复造轮子**：注入方式必须用构造器注入（不要复用 RX/T03 的 setter 注入反模式，参见 F3/T01）。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/DataStandardService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingGlossaryTermService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/services/SvcApiDefinitionService.java`
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CodeAssetLifecycleMapper.java`

## 验证

- [ ] `DataStandardServiceTest.save_syncsCodeAssetGrant`
- [ ] `ModelingGlossaryTermServiceTest.publish_marksLifecycleActive`
- [ ] `SvcApiDefinitionServiceTest.deprecate_marksLifecycleDeprecated`
- [ ] 集成查询：保存 DataStandard 后 `asset_ownership` 表能查到对应 `assetType=DATA_STANDARD` 记录

## 完成标准

- [ ] 三类高频 code asset 写入 `asset_ownership` + `MANAGE` grant。
- [ ] `CodeAssetLifecycleMapper` 复用，避免重复实现。
- [ ] 所有调用方走构造器注入（配合 F3/T01）。
