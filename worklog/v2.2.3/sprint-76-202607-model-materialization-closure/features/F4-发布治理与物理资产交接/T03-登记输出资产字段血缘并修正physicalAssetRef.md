# T03：登记输出资产、字段、血缘并修正 physicalAssetRef

**优先级**：P0
**状态**：DRAFT
**依赖**：T02

## 目标

在 PUBLISH registration 中从 current observation 创建或更新唯一输出 CatalogDataset，登记字段和血缘，并让 artifact 指向该输出资产。

## 技术设计（Contract-first）

- **输入契约**：PUBLISHING candidate entry、current relation observation、current SQL/schema artifact、输入 bindings/upstream models。
- **输出契约**：
  - CatalogDataset `sourceId=executionTargetKey` 解析出的当前数据源 UUID，
    `hiveDatabase=schema`、`hiveTable=identifier`，database/adapter 放入 evidence tags；
  - tags 含 modelSpec/implementation/candidate/run/invocation checksums；
  - `CatalogAssetKey.dataset(dataset)` 唯一；
  - artifact `physicalAssetRef=dataset.id`；
  - 输入 asset keys → 输出 dataset key 的 lineage。
- **幂等**：同 locator + lifecycle identity 重试返回同一 dataset；不重复字段/血缘。
- **唯一性兜底**：增加 `(source_id, lower(hive_database), lower(hive_table))` 的数据库
  唯一约束/索引；升级前若存在重复 locator 必须 fail migration 并给出治理清单，不在
  发布事务中静默合并历史资产。仅 deterministic UUID 不能阻止 generic sync 并发重复。
- **observation 读取**：扩展 F3 repository 的 publication projection，按
  tenant/candidate/version/model/current attempt 读取完整 actual columns、locator、
  invocation 和 checksum；不得从 model 名合成 `dts_modeling.model_<uuid>`。
- **目标解析**：通过只读 `ExecutionTargetCatalogResolver` 把
  `executionTargetKey` 映射到 source UUID/adapter/database；不读取或复制凭据。
- **资产动作**：在任何写入前构造 prospective CatalogDataset；locator 不存在要求
  `AssetAction.CREATE`，已存在要求 `AssetAction.UPDATE`，回滚要求 `ARCHIVE`。
  operator duty 与 `AccessChecker.canPerform` 缺一不可；super-admin 也必须先具备显式
  RELEASE_OPERATOR duty。
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
- execution target metadata resolver + locator unique migration

## 当前代码落差（2026-07-28）

- `CanonicalModelReleaseRegistrationAdapter` 对部分普通模型仍可合成
  `dts_modeling.model_<uuid>`，没有消费 F3 current observation。
- 旧 registration 使用 `REQUIRES_NEW` 且逐 step 吞异常，无法满足 Candidate 级本地
  全有或全无。
- F3 `findCurrent` 只返回字段数等摘要，尚不能给 publication 提供 actual column
  name/type/nullability；必须扩展只读 projection，不新增 schema 台账。
- `catalog_dataset` 当前没有 output locator 数据库唯一键；仅应用层查找不能抵御
  Candidate publish 与 generic dbt sync 并发。

## 验证（RED→GREEN）

- [ ] 普通 compiler 不再用 input sourceBindingId 作为 physicalAssetRef。
- [ ] DBT_MANAGED publish 后 physicalAssetRef 非空。
- [ ] 同发布重试资产/字段/血缘计数不增加。
- [ ] pre-publish catalog 检索为空，publish 后可查。
- [ ] mandatory PARTIAL 时所有 entry 均不可检索，重试完成后一次性可见。
- [ ] external sync 失败保持 PUBLISHED 并可幂等重试。
- [ ] rollback/partial retry IT。
- [ ] 同 locator 两个并发 publisher/generic sync 只能得到一个 dataset；历史重复
  locator 使迁移 fail-closed 并输出修复清单。
- [ ] CREATE/UPDATE/ARCHIVE policy 分支、operator duty 缺失及 super-admin 无 duty
  全部在写入前 403，Catalog/field/lineage/physicalAssetRef 计数不变。

## Definition of Done

- [ ] 输出资产身份唯一且可反查全部证据。
- [ ] 两种 implementation mode 资产语义一致。
- [ ] 物理结果页无需推断输入资产。
- [ ] 本地发布可见性全有或全无，外部同步健康不污染 Candidate 状态。
