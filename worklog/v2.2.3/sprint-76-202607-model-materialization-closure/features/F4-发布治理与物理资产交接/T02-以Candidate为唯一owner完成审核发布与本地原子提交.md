# T02：以 Candidate 为唯一 owner 完成审核、发布与本地原子提交

**优先级**：P0
**状态**：IN_PROGRESS（Candidate 唯一 owner、本地提交、兼容入口、MANUAL_ONLY binding、T03 资产 locator、发布前隔离、100-entry 原子发布与本地权限矩阵门槛 GREEN；external sync、真实职责账号与页面验收待关闭）
**依赖**：T01、F3/T04；生产启用外部依赖 Sprint-36/F3 DONE

## 目标

让候选只能在全部 entry 具备 current BUILT relation observation 后进入质量、审核、批准和发布，并以 Candidate 为唯一 owner 全有或全无地提交本地发布事实；构建成功不得自动审批。

## 技术设计（Contract-first）

- **输入契约**：candidate immutable snapshot/status、current runs/observations、quality evidence、review/operator actor、classification gate。
- **输出契约**：allowedActions 严格按 Sprint-69 状态机；BUILT 后仅允许 RUN_QUALITY，QUALITY_PASSED 后才可 SUBMIT_REVIEW，APPROVED 后仅 operator 可 PUBLISH。
- **发布前置**：PUBLISH 必须 APPROVED，且所有 evidence 的 model/implementation/bundle checksum 仍 current。
- **职责分离**：服务端 domain duty resolver 计算 maintainer/reviewer/operator actions；同一 actor 不可提交并批准/发布，安全审计员只读。不接受客户端 role，allowedActions 与实际 mutation 共享同一 authorize path。
- **资产动作双门禁**：发布准备 CatalogDataset/field/lineage/physicalAssetRef 时，除 operator duty role 外还必须调用已交付的 Sprint-36/F3 `AccessChecker.canPerform(...)` deny-by-default policy；Sprint-76 不复制资产权限表。policy unavailable、缺失、过期或本地接入未完成时 PROD PUBLISH/ROLLBACK/REGISTRATION_RETRY fail-closed。
- **唯一 owner**：Candidate command 是唯一 mutation；旧 `/lifecycle/reviews*`、publish、rollback、registration retry 路由在兼容期只校验 current candidate 并委托，不再直接迁移 ModelSpec。
- **本地提交**：PUBLISHING 后，`CandidatePublicationCommitService` 在一个数据库事务中
  锁定 Candidate/entry/current observation，写全部 ModelSpec release、
  CatalogDataset/field/lineage/physicalAssetRef、outbox 和默认 binding scope，最后 CAS
  Candidate=PUBLISHED。事务提交前任何对象均不可见。
- **binding 所有权**：为解除 F4↔F7 循环依赖，本 Task 创建最小
  `modeling_plan_execution_binding` 与 entry 表/仓储，字段仅覆盖唯一身份、version、
  `MANUAL_ONLY`、desiredScopeChecksum、`DEPLOYING` 和全部 current PUBLISHED scope；
  F7/T01 在此基础上补齐 CRON/deployment/OPERATIONAL_RUN，不重复建表或重建 owner。
- **binding scope 隔离**：每次按 `plan + environment + executionTargetKey` 精确聚合
  全部 current PUBLISHED 模型；禁止跨环境混入。已发布模型缺少当前
  `environment/executionTargetKey/dbtUniqueId/targetIdentifier/artifact/dependency`
  发布事实时返回 `MODEL_PLAN_BINDING_RELEASE_FACTS_REQUIRED`，不得静默漏模型或推断旧事实。
- **失败收口**：mandatory 事务失败时整体 rollback；协调器在独立短事务把
  PUBLISHING→PARTIAL 并写稳定 failure receipt，因此 PARTIAL 时数据库中新增可消费
  ModelSpec/Catalog/binding 数必须为 0。retry 重新执行同一幂等 commit，禁止保留半成品
  enabled asset。
- **外部边界**：OpenMetadata/BI 等在本地 PUBLISHED 后走 outbox；失败只标记 sync health DEGRADED。
- **审计**：START_BUILD、RETRY_BUILD、CREATE_REPLACEMENT_CANDIDATE、CANCEL_CANDIDATE、relation verified、PUBLISH、registration retry 注册动作字典，禁止“未分类”。
- **错误路径**：relation missing/stale、quality stale、classification unresolved、同人审批、越权均 fail-closed。
- **不自动行为**：dbt success 不自动 review/approve/publish；PUBLISH 不重复 dbt build，只消费 current observation。

## 影响范围

- ReleaseCandidate evidence/allowedActions/state transition
- legacy lifecycle publication compatibility adapter
- CandidatePublicationCommitService/publication outbox
- 最小 PlanExecutionBinding/binding entry migration 与 publication repository
- classification publish gate
- dts-admin audit resource dictionary migration
- contract/security tests

## 已实现证据（2026-07-28）

- `ModelReleaseCandidateApplicationService` 的 publish/registration retry/rollback 已统一委托
  Candidate admission/coordinator/atomic commit；发布不触发 Airflow/dbt。
- `LegacyModelLifecycleCandidateAdapter` 已接管旧 review/approve/publish/retry/rollback URL；
  批量 Candidate 在旧单模型 URL fail-closed，历史终态 Candidate 不会遮蔽当前活动 Candidate。
- `CandidatePublicationCommitService @Transactional` 已将 current evidence 重校验、
  ModelSpec lifecycle、Catalog/field/lineage、artifact `physicalAssetRef`、binding、outbox
  与 Candidate=PUBLISHED 放入同一事务；失败由独立收口服务转 PARTIAL。
- registration retry 只重试本地登记，使用独立审计动作
  `MODEL_RELEASE_CANDIDATE_REGISTRATION_RETRY`；PUBLISH/ROLLBACK 动作也已进入公共审计字典，
  canonical 与两份 Docker fallback JSON checksum 一致。
- Liquibase 已建立唯一 `(tenant,plan,environment,executionTargetKey)` 的
  `modeling_plan_execution_binding` 与 release-bound entries，首版仅 `MANUAL_ONLY`。
- `CandidatePublicationRepositoryIT` 已在 PostgreSQL 17.4 通过：迁移启动、事务回滚后
  0 binding、成功提交、连续 A+B 聚合、TEST 不混入 PROD、旧发布事实缺失稳定阻断。
- 同一真实 PostgreSQL 套件已覆盖 100-entry 全链故障注入：正式 publication
  repository、binding rebuild、outbox 和 Candidate/entry 状态在一个事务末端失败后
  全量回滚；独立收口 PARTIAL 不暴露 entry，单次 retry 后 100 项一次性可见。
- publication admission 已把 M05 evaluator 异常转换成稳定
  `MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE`/403，并在 Candidate transition、Catalog、
  field、lineage、physicalAssetRef 写入前停止；缺少任何 release duty 时 publish、
  registration retry、rollback 在读取 Candidate 前即 fail-closed。
- lifecycle-bound dbt sync 现在只导入 current artifacts 并保留已发布旧资产，不再在
  build 阶段创建或更新 Catalog/field/lineage；无 `modelSpecId` 的普通外部 dbt sync
  继续按原契约登记资产。100-entry PostgreSQL 用例新增发布事务前零可见断言。
- `ReleaseDutyResolverTest` 证明 admin/auth-admin/auditor/op-admin 不会被静默提升；
  Sprint-36 的 evaluator/repository tests 证明 policy 缺失、过期及显式 DENY 均不授权。
  本轮 admission/application 定向测试 29/29 GREEN。
- Sprint-76 新增单元/资源/IT 已加入 Maven test compile 白名单，避免只执行遗留 class
  造成假绿。

## 仍未关闭

- external sync `DEGRADED` 投影与重试 IT。
- 三类真实职责账号、撤权与跨租户 API 验收。
- `dts-common` Maven 资源测试受既有 root-owned `target/` 阻断；源 JSON 已通过解析且三份
  checksum 一致，不能把环境阻断写成 Maven GREEN。

## 验证（RED→GREEN）

- [x] build success 无 current observation 仍不可发布。
- [ ] BUILT 页面只出现下一步动作。
- [ ] stale relation/quality/classification 阻断。
- [x] old lifecycle publish/review route 无 current candidate 时 fail-closed，有 candidate 时只产生同一 command/event。
- [x] 100-entry mandatory local publication 故障注入后 0 个 entry 对外可见；重试后一次性全部可见。
- [x] 连续发布 A、B 后同一 MANUAL_ONLY binding scope 包含 A+B；不存在只含最近
  Candidate 的覆盖。
- [ ] external sync 失败保持 Candidate/ModelSpec PUBLISHED，sync health=DEGRADED。
- [ ] replacement/cancel 动作分类、actor/reason/idempotency 和 claim 变化可审计。
- [ ] 同人审批/发布、客户端 role 注入、越权、跨租户 tests。
- [x] 缺 M05 policy、policy DENY、policy expired、resolver 无角色和 auditor mutation 均返回稳定 403/blocker，且没有部分资产可见（本地契约）。

## Definition of Done

- [x] 发布控制面没有直接迁移 ModelSpec 的旧 lifecycle 快捷路径。
- [x] Candidate/ModelSpec/Catalog 不存在可观察的本地分裂发布真值。
- [x] 所有发布 admission 与 commit 门禁引用 current revision/evidence。
- [ ] 审计动作分类完整。
- [x] Sprint-36/F3 实际 domain/migration/API/IT 存在且发布链已接入，不以其 READY 文档状态替代完成证据。
- [x] F4 提供唯一 binding persistence owner；F7 只能扩展调度/运行能力，不形成循环
  migration 或第二套 scope。
