# T01：建立 Publish Intent 与角色感知候选命令

**优先级**：P0
**状态**：DRAFT
**依赖**：F2/T03、F3/T03；生产启用外部依赖 Sprint-36/F3 DONE

## 目标

让普通/高级建模页面的一次“提交上线”成为稳定、可重放的发布请求：它只推进质量检查和提交审核，在人工边界停止；reviewer/operator 只能通过同一 Candidate 控制面执行批准和发布。

## 技术设计（Contract-first）

- **Publish Intent API**：`POST /api/modeling/model-specs/{modelSpecId}/publish-intents`；headers=`If-Match: release-candidate ETag`,`Idempotency-Key`；body=`{candidateId:UUID,reason:string}`。
- **请求边界**：不接受 action/targetStatus/role/reviewer/operator/selector/schedule；服务端校验 candidate origin=SINGLE_MODEL_INTENT、scope 仅含 path model、snapshot/evidence current。
- **Intent 输出**：`PublicationIntentResult{candidateId,candidateVersion,candidateStatus,outcome,nextHumanAction,blocker?,onlineReadiness,workbenchUrl,replayed}`；outcome 只从 Candidate/binding/health 派生。
- **推进规则**：BUILT 写 append-only PUBLICATION_REQUESTED 并调用 RUN_QUALITY；质量通过后 reconciler 重验 requester authority/current snapshot，再调用 SUBMIT_REVIEW；QUALITY_FAILED 阻断并要求显式重试。
- **人工边界**：REVIEW_PENDING、APPROVED、PUBLISHING、PUBLISHED 只返回 current projection；Publish Intent 永不 APPROVE/PUBLISH。
- **批量边界**：BATCH candidate 一律 `409 MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT` + canonical workbench deep link，不允许从单模型页面提交整批上线。
- **候选命令**：补齐 `quality`,`reviews`,`reviews/approve`,`reviews/reject`,`publish`,`registration/retry`,`rollback`,`cancel`；所有 mutation 使用 Candidate ETag + Idempotency-Key。
- **领域职责 authority**：沿用现有 Keycloak realm role/管理员分配链登记 `ROLE_MODEL_MAINTAINER/ROLE_MODEL_RELEASE_REVIEWER/ROLE_MODEL_RELEASE_OPERATOR`，分别映射为内部三类 duty role；不把 CATALOG_MAINTAINERS、admin/auth-admin/auditor 静默提升为发布职责。
- **领域职责 resolver**：服务端从真实 authenticated authorities 解析 `MODEL_MAINTAINER/RELEASE_REVIEWER/RELEASE_OPERATOR`；`allowedActions` 与 command authorization 调同一个 resolver，禁止现有固定 MODEL_MAINTAINER、客户端 role 和只隐藏按钮。异步 reconciler 执行动作前重新校验 current authority。
- **职责动作**：maintainer=`START_BUILD/RETRY/RUN_QUALITY/SUBMIT_REVIEW/CANCEL`；reviewer=`APPROVE/REJECT`；operator=`PUBLISH/ROLLBACK/REGISTRATION_RETRY/SCHEDULE_ENABLE/SCHEDULE_DISABLE`。同一 actor 的提交/审核/发布隔离由 immutable command ledger 校验。
- **M05 边界**：Candidate 职责 resolver 不能冒充资产动作矩阵。Sprint-36/F3 已 DONE；发布创建/更新 CatalogDataset 等资产时必须消费其 deny-by-default `AccessChecker.canPerform(...)`，本 Task 不复制 `IamAssetActionPolicy`。端口不可用、策略缺失或本地接入未完成时，PROD publish/schedule actions 一律返回稳定权限 blocker。
- **审计员边界**：安全审计员只读审计证据，不因 auditor authority 获得 reviewer/operator action。
- **兼容**：不新增菜单/发布中心；现有模型详情/高级页与交付工作台读取同一 workspace。

## UI 交互规格

- 模型详情/高级页在 BUILT 显示“提交上线”，点击后显示“质量检查中”或“已提交审核”，不显示“上线成功”。
- REVIEW_PENDING 显示 reviewer 待办和工作台深链；APPROVED 显示“等待发布操作”。
- reviewer 在工作台只看到 APPROVE/REJECT；operator 只在 APPROVED 看到“发布上线”。
- PUBLISHED 但 binding 未 ACTIVE 显示“已发布，运行计划部署中/异常”；READY 才显示“上线完成”。
- 所有页面兼容 Chrome95，更新对应 source-contract tests。

## 影响范围

- ModelReleaseCandidateResource/ApplicationService/Contract
- PublishIntent facade + command ledger/reconciler
- actor/authority resolver
- Keycloak realm role seed/管理员角色分配与审计
- Sprint-36/F3 action-policy integration port（只消费，不复制）
- ModelSpecDetailPage/SqlModelingPage/release workbench
- dts-admin audit resource dictionary

## 验证（RED→GREEN）

- [ ] BUILT Publish Intent 只产生 PUBLICATION_REQUESTED + RUN_QUALITY，不产生 APPROVE/PUBLISH。
- [ ] QUALITY_PASSED 自动且仅一次 SUBMIT_REVIEW；权限被撤销或 snapshot 漂移时 fail-closed。
- [ ] REVIEW_PENDING/APPROVED 重放只返回 current projection。
- [ ] BATCH、stale、cancelled、wrong model/candidate、role injection 均返回稳定错误。
- [ ] reviewer/operator allowedActions 与 actor separation/authority 一致。
- [ ] 三类真实测试账号直接调用 API 时只能执行其职责动作；客户端 role/action 注入无效。
- [ ] admin/auth-admin/auditor/catalog maintainer 未显式拥有专用 authority 时不能执行 release duty；撤权后异步推进停止。
- [ ] Sprint-36/F3 不可用或未配置 policy 时 PROD publish/schedule deny-by-default；DEV/TEST 构建不被误报为细粒度 RBAC 完成。
- [ ] 审计员可读取但不可执行审核、发布和调度 mutation。
- [ ] Chrome95 完成“提交上线→等待审核→等待发布”的状态走查。

## Definition of Done

- [ ] Publish Intent 语义固定，不按当前状态执行任意 allowedAction。
- [ ] Candidate command 是唯一质量/审核/发布入口。
- [ ] 页面没有隐藏自动批准或发布链。
- [ ] PG-03 双门禁通过前 Feature 不得声称 production ready。
- [ ] 契约、权限、幂等和 UI 证据进入 IT-12/13/14。
