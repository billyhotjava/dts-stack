# T02：建立 append-only 核验证据与新鲜度约束

**优先级**：P0
**状态**：DONE
**依赖**：T01

## 目标

把每次 relation probe 结果持久化为不可覆盖的强绑定证据，并确保发布只能消费当前构建产生的新鲜 observation。

## 技术设计（Contract-first）

- **表**：`modeling_physical_relation_observation`。
- **必填列**：id、tenant_id、release_candidate_id、pipeline_run_id、model_spec_id、model_revision/checksum、implementation_revision/checksum、dbt_invocation_id、adapter、database_name、schema_name、identifier、expected_type、actual_type、exists、columns_checksum、metadata_checksum、observed_at、created_date。
- **约束**：
  - FK candidate/run/model；
  - unique `(pipeline_run_id,model_spec_id,implementation_revision,observation_attempt)`；
  - identifier 非空；
  - exists=true 时 actual_type/metadata_checksum/observed_at 非空。
- **新鲜度**：observedAt ≥ pipeline startedAt；invocationId、bundle/model/implementation checksum 全匹配。
- **写入**：append-only repository，无 update/delete API；同一 revision 的 retry
  原子分配递增 `observation_attempt`，`findCurrent` 只投影最大 attempt。
- **索引**：tenant+candidate、tenant+model+implementation、pipeline run。
- **rollback**：仅 schema rollback；生产数据回滚遵守 release plan，不静默删除证据。

## 影响范围

- Liquibase `20260727_13_physical_relation_observation.xml` + master include
- observation repository/service
- clean migration/PostgreSQL IT

## 验证（RED→GREEN）

- [x] migration 在真实 PostgreSQL 加载，FK/unique/check constraints 生效。
- [x] repository 在写入前锁定 pipeline，并校验 time/invocation/revision/checksum。
- [x] retry 保留两条历史 observation，current 投影返回 attempt 2。
- [x] current/candidate/model 查询均有与过滤前缀对齐的索引。

## Definition of Done

- [x] observation 可从 candidate→run→model→relation 完整追踪。
- [x] 不允许 overwrite success 伪造 current。
- [x] expand migration 与“存在证据时阻断 rollback”保护已落库。

生产升级/回滚演练仍由 F6/IT-15 关闭；本 Task 的 DONE 不代表 Sprint 可发布。
证据：`../../it/evidence/f3-real-physical-relation/README.md`。
