# T03：登记输出资产、字段、血缘并修正 physicalAssetRef

**优先级**：P0
**状态**：DRAFT
**依赖**：T02

## 目标

在 PUBLISH registration 中从 current observation 创建或更新唯一输出 CatalogDataset，登记字段和血缘，并让 artifact 指向该输出资产。

## 技术设计（Contract-first）

- **输入契约**：PUBLISHING candidate entry、current relation observation、current SQL/schema artifact、输入 bindings/upstream models。
- **输出契约**：
  - CatalogDataset locator=database/schema/identifier；
  - tags 含 modelSpec/implementation/candidate/run/invocation checksums；
  - `CatalogAssetKey.dataset(dataset)` 唯一；
  - artifact `physicalAssetRef=dataset.id`；
  - 输入 asset keys → 输出 dataset key 的 lineage。
- **幂等**：同 locator + lifecycle identity 重试返回同一 dataset；不重复字段/血缘。
- **模式一致性**：DESIGNER_GENERATED/DBT_MANAGED 走同一 registration；generic dbt sync 保持兼容。
- **发布前隔离**：lifecycle-bound DbtAssetSync 只写 artifact/observation，不提前创建 enabled CatalogDataset。
- **mandatory local 错误路径**：locator 冲突、字段 checksum mismatch、Catalog/Lineage/physicalAssetRef 任一步失败 → candidate PARTIAL；预备记录可保留供幂等重试，但所有 entry 继续 disabled/不可检索，不允许部分发布。
- **外部同步**：OpenMetadata/BI 等在 candidate PUBLISHED 后由 outbox 同步；失败只更新 `syncHealth=DEGRADED`，不得把 candidate 迁回 PARTIAL。
- **回滚**：恢复当前有效发布/资产状态，不删除 observation，不自动 DROP relation。

## 影响范围

- `CanonicalModelReleaseRegistrationAdapter`
- `DbtAssetSyncService`
- lifecycle artifact repository
- CatalogDataset/field/lineage services

## 验证（RED→GREEN）

- [ ] 普通 compiler 不再用 input sourceBindingId 作为 physicalAssetRef。
- [ ] DBT_MANAGED publish 后 physicalAssetRef 非空。
- [ ] 同发布重试资产/字段/血缘计数不增加。
- [ ] pre-publish catalog 检索为空，publish 后可查。
- [ ] mandatory PARTIAL 时所有 entry 均不可检索，重试完成后一次性可见。
- [ ] external sync 失败保持 PUBLISHED 并可幂等重试。
- [ ] rollback/partial retry IT。

## Definition of Done

- [ ] 输出资产身份唯一且可反查全部证据。
- [ ] 两种 implementation mode 资产语义一致。
- [ ] 物理结果页无需推断输入资产。
- [ ] 本地发布可见性全有或全无，外部同步健康不污染 Candidate 状态。
