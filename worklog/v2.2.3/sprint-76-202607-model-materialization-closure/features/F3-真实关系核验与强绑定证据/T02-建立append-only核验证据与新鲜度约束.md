# T02：建立 append-only 核验证据与新鲜度约束

**优先级**：P0
**状态**：DRAFT
**依赖**：T01

## 目标

把每次 relation probe 结果持久化为不可覆盖的强绑定证据，并确保发布只能消费当前构建产生的新鲜 observation。

## 技术设计（Contract-first）

- **表**：`modeling_physical_relation_observation`。
- **必填列**：id、tenant_id、release_candidate_id、pipeline_run_id、model_spec_id、model_revision/checksum、implementation_revision/checksum、dbt_invocation_id、adapter、database_name、schema_name、identifier、expected_type、actual_type、exists、columns_checksum、metadata_checksum、observed_at、created_date。
- **约束**：
  - FK candidate/run/model；
  - unique `(tenant_id,pipeline_run_id,model_spec_id,implementation_revision)`；
  - identifier 非空；
  - exists=true 时 actual_type/metadata_checksum/observed_at 非空。
- **新鲜度**：observedAt ≥ pipeline startedAt；invocationId、bundle/model/implementation checksum 全匹配。
- **写入**：append-only repository，无 update/delete API；retry 新增 observation。
- **索引**：tenant+candidate、tenant+model+implementation、pipeline run。
- **rollback**：仅 schema rollback；生产数据回滚遵守 release plan，不静默删除证据。

## 影响范围

- Liquibase `20260727_08...` + master include
- observation repository/service
- clean migration/PostgreSQL IT

## 验证（RED→GREEN）

- [ ] FK/unique/check constraints IT。
- [ ] stale time/invocation/checksum 全部拒绝。
- [ ] retry 保留两条历史 observation。
- [ ] `EXPLAIN` current model/candidate 查询走索引。

## Definition of Done

- [ ] observation 可从 candidate→run→model→relation 完整追踪。
- [ ] 不允许 overwrite success 伪造 current。
- [ ] migration expand/rollback 证据齐全。
