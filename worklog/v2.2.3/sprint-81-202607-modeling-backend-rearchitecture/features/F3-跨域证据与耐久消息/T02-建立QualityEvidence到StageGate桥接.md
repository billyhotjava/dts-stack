# T02：建立 QualityEvidence 到 StageGate 桥接

**优先级**：P0
**状态**：PLANNED
**依赖**：T01；F2/T01 revision contract

## 目标

让 StageGate 只消费既有 rule/version/binding/run 形成的不可变质量证据，不复制质量表或依赖模板数量。

## 技术设计（Contract-first）

- **输入契约**：`QualityEvidenceRequest {asset,ruleVersionIds,asOf,maxAgeSeconds}`。
- **输出契约**：`QualityEvidence {ruleId,ruleVersionId,bindingId,runId,status,finishedAt,evidenceChecksum}` 列表。
- **查询**：按 ruleVersionIds/binding/asset 批量加载，单次 StageGate SQL ≤8；禁止逐 rule N+1。
- **有效性**：run 终态；binding 匹配相同 CatalogAssetKey；version pinned；finishedAt 未过期；tenant 相同。
- **错误路径**：missing/running/failed/error/expired/asset mismatch/tenant mismatch 都返回 violation，fail-closed。
- **复用点**：`gov_rule`、`gov_rule_version`、`gov_rule_binding`、`gov_quality_run`。

## 影响范围

quality query port、StageGate adapter、批量 repository query。

## 验证

- [ ] 每种无效 evidence 的结构化 violation。
- [ ] latest version 漂移不会改变 pinned gate result。
- [ ] Testcontainers 查询次数和索引 plan 断言。
- [ ] 隔离真实 run 在 F6 前可生成和清理。

## Definition of Done

- [ ] StageGate 无直接 quality repository/entity import。
- [ ] 模板不被当作通过证据。
- [ ] evidence checksum 可在 lifecycle transition 时复验。
