# T03：登记输出资产、字段、血缘并修正 physicalAssetRef

**优先级**：P0
**状态**：IN_PROGRESS（本地 locator、actual columns、DBT_MANAGED/lineage、并发收敛、physicalAssetRef、发布前隔离、rollback/ARCHIVE、PARTIAL 可见性、100-entry 原子发布与双门禁契约主链 GREEN；external sync、页面与 PROD 账号验收待关闭）
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
- **目标解析**：通过只读 `ModelExecutionTargetCatalogResolver` 把
  `executionTargetKey` 映射到 source UUID/adapter/database；不读取或复制凭据。
- **资产动作**：在任何写入前构造 prospective CatalogDataset；locator 不存在要求
  `AssetAction.CREATE`，已存在要求 `AssetAction.UPDATE`，回滚要求 `ARCHIVE`。
  operator duty 与 `AccessChecker.canPerform` 缺一不可；super-admin 也必须先具备显式
  RELEASE_OPERATOR duty。
- **模式一致性**：DESIGNER_GENERATED/DBT_MANAGED 走同一 registration；generic dbt sync 保持兼容。
- **发布前隔离**：lifecycle-bound DbtAssetSync 只写 artifact/observation，不提前创建 enabled CatalogDataset。
- **mandatory local 错误路径**：locator 冲突、字段 checksum mismatch、Catalog/Lineage/physicalAssetRef 任一步失败 → candidate PARTIAL；预备记录可保留供幂等重试，但所有 entry 继续 disabled/不可检索，不允许部分发布。
- **外部同步**：candidate PUBLISHED 后由 outbox 交接目标消费者；outbox `SENT` 只表示
  Kafka handoff 成功，不得冒充 OpenMetadata/BI 已应用。只有目标消费者按
  `eventId + candidateId + candidateVersion + target` 幂等处理并回写 ACK，才能投影
  target sync health；投递或消费失败标记 `DEGRADED`，但不得把 candidate 迁回 PARTIAL。
- **回滚**：恢复当前有效发布/资产状态，不删除 observation，不自动 DROP relation。

## 已实现证据（2026-07-28）

- `CatalogPhysicalLocator` 以
  `(sourceId, lower(trim(schema)), lower(trim(table)))` 生成稳定 dataset UUID；
  Candidate 与 generic dbt sync 复用同一算法，不再按 ModelSpec revision 生成物理资产 ID。
- `ModelExecutionTargetCatalogResolver` 只读取 server-owned materialization key 与
  `DbtWorkspaceConfig.targetDataSourceId`，不读取 JDBC 密码或 runtime profile lease；
  Candidate target/adapter 漂移时 fail-closed。
- `DbtAssetSyncService` 对物化 model 注入目标 `sourceId`，创建时使用稳定 locator ID；
  对升级前唯一的 `source_id is null` 同表资产执行收敛更新，多个候选则拒绝猜测。
- `CandidatePublicationEvidenceRepository` 从 current verified observation 读取并解析
  `actual_columns`；Catalog 字段使用实际 name/type/nullability，ModelSpec 字段只补充
  role、security level 和 display name。
- `CandidatePublicationRepository` 在同一发布事务内锁定 locator，唯一 legacy 资产可
  被显式认领；Catalog table 也优先复用同 dataset/name 投影，artifact
  `physical_asset_ref` 指向最终被认领的输出 dataset。
- `20260728_06_catalog_dataset_physical_locator.xml` 在建索引前列出最多 20 个重复
  locator 并以 `CATALOG_DATASET_PHYSICAL_LOCATOR_DUPLICATES_EXIST` 中止升级；
  随后建立 PostgreSQL expression/partial unique index。
- 同轮真实执行发现旧发布 SQL 错把 `modeling_dbt_artifact` 当作包含
  `tenant_id/model_revision`；现通过 `modeling_model_spec` 约束 tenant，并使用
  artifact 真实 `revision` 字段，真实 PostgreSQL registration 已覆盖。
- Candidate publisher 与 generic dbt sync 已由 locator 唯一索引和稳定 UUID 收敛；
  双线程真实事务最终只保留一个 dataset，artifact 指向获胜资产。
- DBT_MANAGED 与 DESIGNER_GENERATED 复用同一登记事务；DBT_MANAGED 重试后
  dataset/field/model lineage/catalog lineage 数量均保持稳定，关闭同主键血缘重开冲突。
- rollback 在进入原子写事务前同时要求 `RELEASE_OPERATOR` duty 和输出资产
  `AssetAction.ARCHIVE`；资产 UUID 只从当前 Candidate 的 PUBLISHED release facts
  读取，随后归档该 CatalogDataset、关闭血缘并清空 artifact 引用，不自动 DROP 关系。
- 100-entry Candidate 在真实 PostgreSQL 单事务中复用正式 publication repository、
  binding rebuild 与 outbox 写入：末端故障回滚后 Catalog、artifact 引用、binding、
  outbox 和 lifecycle physical ref 均为 0；独立收口为 PARTIAL 后仍不可见，从 PARTIAL
  重试一次后 100 个输出资产、100 个 artifact 引用和 100 个 binding entry 同时可见。
- M05 policy 返回 false 或 evaluator 抛出异常时均在资产写入前 fail-closed；后者返回
  稳定 `MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE`/403 并记录不含凭据的服务端告警。
  无 release duty（包括只有 op-admin/auditor）时 publish/retry/rollback 不读取 Candidate。
- `DbtAssetSyncService` 对带 `modelSpecId` 且 pin current 的生命周期模型只导入 dbt
  artifacts，随后跳过 Catalog/table/column/lineage mutation；旧 PUBLISHED 资产只保留，
  无 `modelSpecId` 的普通外部 dbt 同步行为不变。

## 本轮验证

- 聚焦 Java 测试：
  `CatalogPhysicalLocatorTest`、`ModelExecutionTargetCatalogResolverTest`、
  `CatalogPhysicalLocatorLiquibaseTest`、`CandidatePublicationAdmissionServiceTest`、
  `CandidatePublicationCommitServiceTest`、`ModelReleaseCandidateApplicationServiceTest`、
  `DbtAssetSyncServiceTest`，全部 GREEN；其中 ARCHIVE 门禁定向批次 26 tests GREEN。
- 真实 PostgreSQL：
  `CandidatePublicationRepositoryIT` 6/6 GREEN；覆盖原 binding 原子/环境隔离，
  以及 legacy dbt asset 认领、sourceId 回填、actual columns、physicalAssetRef、
  大小写 locator 重复拒绝、Candidate/generic dbt 并发收敛、DBT_MANAGED
  字段/双层血缘重试幂等、按当前 Candidate 资产引用执行 rollback，以及 100-entry
  故障回滚、PARTIAL 不可见与单次重试后一致可见。
- 发布前隔离补充验证：`DbtAssetSyncServiceTest` 13/13 GREEN；100-entry PostgreSQL
  方法定向 1/1 GREEN，并在首次 publication 写入前断言 Catalog、artifact 引用、
  binding、outbox、lifecycle physical ref 全为 0。未重复运行整套 PostgreSQL IT。
- `git diff --check`（本 Task 相关路径）GREEN。

## 尚未关闭

- external sync health 及 PROD 真实职责账号证据。
- 当前 OpenMetadata client 仅支持 GET/拉取缓存，平台 outbox 也没有目标消费者 ACK；
  在 consumer + ACK + retry 契约落地前，禁止把 outbox `SENT` 显示为同步健康。
- 物理结果页只消费输出资产引用的 Chrome95 用户旅程证据。

## 验证（RED→GREEN）

- [ ] 普通 compiler 不再用 input sourceBindingId 作为 physicalAssetRef。
- [x] DBT_MANAGED publish 后 physicalAssetRef 非空。
- [x] 同发布重试资产/字段/血缘计数不增加。
- [x] pre-publish catalog 检索为空，publish 后可查。
- [x] mandatory PARTIAL 时所有 entry 均不可检索，重试完成后一次性可见。
- [ ] external sync 失败保持 PUBLISHED 并可幂等重试。
- [x] rollback IT：按当前 Candidate 发布事实归档资产并解除 artifact 引用。
- [x] PARTIAL retry IT。
- [x] 同 locator 两个并发 publisher/generic sync 只能得到一个 dataset；历史重复
  locator 使迁移 fail-closed 并输出修复清单。
- [x] CREATE/UPDATE/ARCHIVE policy 分支均在写入前执行。
- [x] operator duty 缺失及 super-admin 无 duty
  全部在写入前 403，Catalog/field/lineage/physicalAssetRef 计数不变。

## Definition of Done

- [x] 输出资产身份唯一且可反查全部本地发布证据。
- [x] 两种 implementation mode 资产语义一致。
- [ ] 物理结果页无需推断输入资产。
- [x] 本地发布可见性全有或全无。
- [ ] 外部同步健康不污染 Candidate 状态。
