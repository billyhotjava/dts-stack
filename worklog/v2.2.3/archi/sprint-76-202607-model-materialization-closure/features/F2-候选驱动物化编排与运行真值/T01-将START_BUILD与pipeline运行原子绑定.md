# T01：将 START_BUILD 与 pipeline run 原子绑定

**优先级**：P0
**状态**：DRAFT
**依赖**：F1/T03

## 目标

复用 ReleaseCandidate `/lock` 命令，在 candidate 进入 BUILDING 的同一事务内，为每个 current entry 创建可恢复的 QUEUED pipeline run；同时提供一个无独立状态的 Build Intent facade，让单模型页面可一次点击完成候选准备和 canonical START_BUILD。

## 技术设计（Contract-first）

- **输入契约**：candidate command 接受 planId、candidateId、candidate If-Match、Idempotency-Key、reason；Build Intent 只接受 headers=`If-Match,Idempotency-Key` 和 body=`planId,environment`。
- **快捷 facade**：`POST /api/modeling/model-specs/{id}/build-intents` 校验 plan membership 后只创建/精确复用 scope 完全一致的 `SINGLE_MODEL_INTENT`，再委托同一 application command。
- **来源隔离**：candidate.origin=`SINGLE_MODEL_INTENT|BATCH_WORKBENCH`；快捷 facade 绝不创建、扩展或缩小 BATCH candidate。
- **冲突规则**：模型存在于 BATCH DRAFT/active candidate 时返回 `409 MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT` + deep link；相同 SINGLE_MODEL active 返回原 candidate/run；相同 revision 已发布则引导 OPERATIONAL_RUN。
- **服务端派生**：从 current ModelSpec/Implementation、artifact、plan/environment 解析 revision/checksum、selector、targetIdentifier、artifact/dependency checksum、adapter/profile/target；客户端同名字段一律拒绝。
- **执行目标**：一个 candidate 的所有 entry 必须解析到当前唯一安全 `executionTargetKey`；非当前 target 返回 `422 MODEL_EXECUTION_TARGET_UNAVAILABLE`。
- **上游门禁**：每个 ref 上游必须 current PUBLISHED，或显式位于同一 candidate；禁止 selector 隐式带入未审核上游。
- **输出契约**：现有 `CommandResult` 保持兼容；workspace evidence 可读取新 QUEUED runs。
- **数据迁移**：candidate 增加 `origin,execution_target_key,adapter,profile_key`；entry 增加不可变 snapshot 字段和 nullable `active_claim_key`；`modeling_pipeline_run` 增加 `release_candidate_id,implementation_revision,implementation_checksum,environment,run_purpose,artifact_bundle_checksum,scoped_bundle_checksum,dbt_invocation_id`。
- **事务**：candidate DRAFT→BUILDING 与 N 条 QUEUED run 同事务；任一 entry validation/bundle 失败则全部回滚。
- **数据库唯一性**：DRAFT entry 的 `active_claim_key=null`；START_BUILD 事务写 `sha256(tenant|environment|modelSpecId)` 并由唯一索引决定并发唯一获胜者。
- **释放规则**：PUBLISHED/REJECTED/ROLLED_BACK/CANCELLED 清空 claim；BUILD_FAILED/QUALITY_FAILED 保留供 retry；STALE 由 replacement 事务原子换 claim。
- **错误路径**：empty scope、>100 entries、stale checksum、artifact/dependency missing、target unavailable、active claim conflict → 422/409 稳定错误。
- **复用点**：ReleaseCandidate CAS/idempotency command ledger、`modeling_pipeline_run`。

## 影响范围

- ReleaseCandidate application/service/repository
- ModelingVNext/pipeline run repository
- Liquibase `20260727_08...`
- PostgreSQL IT

## 验证（RED→GREEN）

- [ ] current `/lock` 只改状态不写 run 的 RED。
- [ ] candidate/run 原子提交与回滚 IT。
- [ ] Build Intent 首次创建、精确 SINGLE_MODEL 复用、BATCH 冲突、已发布引导和重放 IT。
- [ ] active_claim 唯一约束、终态释放、索引、clean migration/rollback IT。
- [ ] DRAFT 不占坑、并发 START_BUILD 单获胜、CANCELLED 释放 claim IT。
- [ ] 客户端注入 selector/dagId/checksum/target/profile 被拒绝。
- [ ] 非当前 target 与未发布/漂移上游 fail-closed。
- [ ] 并发两次 START_BUILD 只有一个获胜。

## Definition of Done

- [ ] DB 中不存在 BUILDING candidate 且 0 pipeline run 的悬空状态。
- [ ] 快捷入口与工作台返回同一 candidate/run/allowedActions。
- [ ] 单模型点击不会改变任何 BATCH candidate scope。
- [ ] 同 tenant/environment/model 不存在两个 active candidate claims。
- [ ] 旧 pipeline run 行兼容可读。
- [ ] 迁移注册 master 且可回滚。
