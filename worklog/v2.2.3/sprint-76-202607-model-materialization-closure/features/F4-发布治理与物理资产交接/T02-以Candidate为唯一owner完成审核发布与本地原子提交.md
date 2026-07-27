# T02：以 Candidate 为唯一 owner 完成审核、发布与本地原子提交

**优先级**：P0
**状态**：DRAFT
**依赖**：T01、F3/T03；生产启用外部依赖 Sprint-36/F3 DONE

## 目标

让候选只能在全部 entry 具备 current BUILT relation observation 后进入质量、审核、批准和发布，并以 Candidate 为唯一 owner 全有或全无地提交本地发布事实；构建成功不得自动审批。

## 技术设计（Contract-first）

- **输入契约**：candidate immutable snapshot/status、current runs/observations、quality evidence、review/operator actor、classification gate。
- **输出契约**：allowedActions 严格按 Sprint-69 状态机；BUILT 后仅允许 RUN_QUALITY，QUALITY_PASSED 后才可 SUBMIT_REVIEW，APPROVED 后仅 operator 可 PUBLISH。
- **发布前置**：PUBLISH 必须 APPROVED，且所有 evidence 的 model/implementation/bundle checksum 仍 current。
- **职责分离**：服务端 domain duty resolver 计算 maintainer/reviewer/operator actions；同一 actor 不可提交并批准/发布，安全审计员只读。不接受客户端 role，allowedActions 与实际 mutation 共享同一 authorize path。
- **资产动作双门禁**：发布准备 CatalogDataset/field/lineage/physicalAssetRef 时，除 operator duty role 外还必须调用已交付的 Sprint-36/F3 `AccessChecker.canPerform(...)` deny-by-default policy；Sprint-76 不复制资产权限表。policy unavailable、缺失、过期或本地接入未完成时 PROD PUBLISH/ROLLBACK/REGISTRATION_RETRY fail-closed。
- **唯一 owner**：Candidate command 是唯一 mutation；旧 `/lifecycle/reviews*`、publish、rollback、registration retry 路由在兼容期只校验 current candidate 并委托，不再直接迁移 ModelSpec。
- **本地提交**：PUBLISHING 先准备全部 entry 的 ModelSpec release、CatalogDataset/field/lineage/physicalAssetRef，并从 plan 下全部 current PUBLISHED 模型重建 `MANUAL_ONLY` binding scope；mandatory 步骤成功后同一数据库事务切换 Candidate/ModelSpec/资产可见性和 binding DEPLOYING。
- **PARTIAL**：mandatory local 步骤不完整时所有 entry 继续不可消费；retry 幂等复用预备记录。禁止一个 candidate 内部分模型 PUBLISHED。
- **外部边界**：OpenMetadata/BI 等在本地 PUBLISHED 后走 outbox；失败只标记 sync health DEGRADED。
- **审计**：START_BUILD、RETRY_BUILD、CREATE_REPLACEMENT_CANDIDATE、CANCEL_CANDIDATE、relation verified、PUBLISH、registration retry 注册动作字典，禁止“未分类”。
- **错误路径**：relation missing/stale、quality stale、classification unresolved、同人审批、越权均 fail-closed。
- **不自动行为**：dbt success 不自动 review/approve/publish；PUBLISH 不重复 dbt build，只消费 current observation。

## 影响范围

- ReleaseCandidate evidence/allowedActions/state transition
- legacy lifecycle publication compatibility adapter
- CandidatePublicationCommitService/publication outbox
- classification publish gate
- dts-admin audit resource dictionary migration
- contract/security tests

## 验证（RED→GREEN）

- [ ] build success 无 observation 仍不可质量/发布。
- [ ] BUILT 页面只出现下一步动作。
- [ ] stale relation/quality/classification 阻断。
- [ ] old lifecycle publish/review route 无 current candidate 时 fail-closed，有 candidate 时只产生同一 command/event。
- [ ] 100-entry mandatory local publication 故障注入后 0 个 entry 对外可见；重试后一次性全部可见。
- [ ] external sync 失败保持 Candidate/ModelSpec PUBLISHED，sync health=DEGRADED。
- [ ] replacement/cancel 动作分类、actor/reason/idempotency 和 claim 变化可审计。
- [ ] 同人审批/发布、客户端 role 注入、越权、跨租户 tests。
- [ ] 缺 M05 policy、policy DENY、policy expired、resolver 无角色和 auditor mutation 均返回稳定 403/blocker，且没有部分资产可见。

## Definition of Done

- [ ] 发布控制面没有隐藏快捷路径。
- [ ] Candidate/ModelSpec/Catalog 不存在可观察的分裂发布真值。
- [ ] 所有治理门禁引用 current revision。
- [ ] 审计动作分类完整。
- [ ] Sprint-36/F3 实际 domain/migration/API/IT 存在且发布链已接入，不以其 READY 文档状态替代完成证据。
