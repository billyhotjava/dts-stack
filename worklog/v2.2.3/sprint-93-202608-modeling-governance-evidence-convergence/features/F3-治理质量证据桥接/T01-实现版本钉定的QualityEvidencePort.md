# T01：实现版本钉定的 QualityEvidence Port

**优先级**：P0
**状态**：IMPLEMENTATION_COMPLETE / MODULE_VERIFIED / IT-05_PENDING
**依赖**：F0/T01 提供真实质量样本用于最终 IT

## 目标

以只读 Port 从现有 GovRule/Version/Binding/QualityRun 形成不可变、可校验、可批量查询的治理质量证据。

## 技术设计（Contract-first）

- **输入契约**：`QualityEvidenceRequest {CatalogAssetType assetType,String assetKey,List<UUID> ruleVersionIds,Instant asOf,long maxAgeSeconds}`。
- **输出契约**：`QualityEvidence {UUID ruleId,UUID ruleVersionId,UUID bindingId,UUID runId,String status,Instant finishedAt,String evidenceChecksum,List<String> violations}`。
- **有效性**：assetKey 唯一解析 datasetId；binding 属于指定 ruleVersion 和 dataset；run 属于相同 binding/version/dataset；run 为终态；finishedAt≤asOf 且未过期；checksum 覆盖全部 ID/status/finishedAt。
- **失败语义**：`MISSING/RUNNING/FAILED/ERROR/EXPIRED/ASSET_MISMATCH/VERSION_MISMATCH/BINDING_MISMATCH` 均结构化返回并 fail-closed。
- **查询预算**：候选批量加载，SQL ≤8；禁止每条规则 N+1。
- **复用点**：账本 L13～L15、Sprint-81 T02 既有设计、CatalogAssetKey resolver、现有质量 repository/entity。
- **依赖方向**：Modeling 仅依赖 Port DTO/接口，不 import governance entity/repository。

## UI 交互规格

本 Task 仅提供 API/service DTO；T02 消费。错误码和 violations 必须是用户可翻译的稳定代码。

## 影响范围

dts-platform quality query adapter、modeling port、批量 repository 与 Testcontainers 测试；无新表。

## 验证（RED→GREEN）

- [x] 每种 violation 参数化测试。
- [x] latest version 变化不改变 pinned evidence。
- [x] asset/version/binding/run 串线全部拒绝。
- [ ] 查询次数 ≤8，目标索引经 EXPLAIN 验证。
- [x] 相同输入 checksum 稳定。

## Definition of Done

- [x] Port 不复制质量事实、不依赖模板数量。
- [x] Modeling 不直接引用 governance repository/entity。
- [ ] IT-05 有真实 pass/fail/expired 证据。
