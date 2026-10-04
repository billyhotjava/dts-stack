# F4：发布治理与物理资产交接

**优先级**：P0
**状态**：IN_PROGRESS（T01/T02 后端核心、T03 本地 locator/actual-column/DBT_MANAGED lineage/并发收敛/physicalAssetRef/发布前隔离/rollback-ARCHIVE、PARTIAL 可见性、100-entry 全链原子性与双门禁本地契约 GREEN；external sync health、页面及 PROD 真实账号证据未关闭）
**依赖**：F2、F3、Sprint-69/72 既有门禁；生产启用外部依赖 Sprint-36/F3 DONE

## 目标

让 Publish Intent 只把 current Candidate 推进到人工审核边界，并在独立审核/发布后以 Candidate 为唯一 owner 全有或全无地提交本地发布事实、唯一 CatalogDataset、字段和血缘，修正 physicalAssetRef 的输出方向。

## 契约定义

| 类型 | 契约 | 关键内容 |
|---|---|---|
| 发布门禁 | current candidate evidence | build/quality/review/classification 全部通过 |
| 发布请求 | `POST .../publish-intents` | SINGLE candidate；记录 intent；RUN_QUALITY→SUBMIT_REVIEW；停止于人工边界 |
| 候选动作 | candidate quality/review/approve/publish commands | Candidate ETag + Idempotency-Key + server actor role |
| 权限双门禁 | domain duty resolver + Sprint-36/F3 `canPerform` | maintainer/reviewer/operator 职责隔离 + 资产动作 deny-by-default |
| 本地提交 | `CandidatePublicationCommitService` | Candidate/ModelSpec/Catalog/field/lineage/physicalAssetRef/MANUAL_ONLY binding scope 原子可见 |
| Catalog 注册 | `CandidatePublicationCommitService` + transaction-participating repository | current observation → CatalogDataset；旧 `REQUIRES_NEW` adapter 不进入 mandatory commit |
| 资产身份 | `CatalogAssetKey.dataset(dataset)` | 统一 type/key |
| artifact 引用 | `physicalAssetRef` | 输出 CatalogDataset UUID |
| 血缘 | 输入资产/上游模型 → 输出 dataset | current revision/invocation |

## UI/UX 规格

- BUILT 未发布：模型详情显示“提交上线”；交付工作台显示已核验关系，模型“发布结果”仍明确为未发布；
- QUALITY_RUNNING/REVIEW_PENDING/APPROVED：分别显示质量检查、等待审核、等待发布操作，不冒充上线成功；
- PUBLISHING/PARTIAL：逐项展示 mandatory local registration；PARTIAL 下所有 entry 不可消费；
- PUBLISHED：模型发布结果显示物理资产与台账深链；外部同步失败显示 DEGRADED；
- PUBLISHED + binding DEPLOYING/FAILED：显示“已发布，运行计划部署中/异常”；ACTIVE + relation healthy 才显示“上线完成”；
- rollback：显示历史资产和当前有效发布状态，不自动 DROP。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立 Publish Intent 与角色感知候选命令 | P0 | IN_PROGRESS | F2/T03、F3/T04；PROD: Sprint-36/F3 |
| T02 | 以 Candidate 为唯一 owner 完成审核发布与本地原子提交 | P0 | IN_PROGRESS | T01、F3/T03；PROD: Sprint-36/F3 |
| T03 | 登记输出资产字段血缘并修正 physicalAssetRef | P0 | IN_PROGRESS | T02 |

## Definition of Ready

- [x] 发布前/后资产可见性已固定。
- [x] Catalog identity、引用和血缘方向已固定。
- [x] Publish Intent 与 reviewer/operator 人工边界已固定。
- [x] mandatory local failure/partial/retry 与 external sync degraded 边界已固定。
- [x] F3 evidence contract GREEN。
- [x] Sprint-36/F3 实际 domain/migration/API/IT 已存在并 DONE；不能把其 READY 文档、actor separation 或页面隐藏冒充资产动作授权。
- [x] F2/T03 已提供可重放的 `CANCEL_CANDIDATE` 命令、终态 `CANCELLED`、active claim 释放与运行证据保留。

F3/T04 typed-column，以及 F2/T03 cancel、五类 drift、replacement claim 原子交接、
START_BUILD/retry 双线程竞争均已解除。F2/T02 已以真实 Airflow 的
accept-after-timeout、duplicate 409、唯一 DagRun 和 Platform 重启后唯一性关闭
exactly-once effect / at-least-once dispatch 门槛，证据已归档；F4 现可从 T01
按顺序实施。

## 完成标准

- [x] 未发布关系不进入可消费资产台账。
- [ ] 发布只消费 current observation。
- [ ] Publish Intent 自动推进最多到 REVIEW_PENDING。
- [ ] Candidate 是唯一发布 owner，旧 lifecycle route 只作兼容委托。
- [x] mandatory local publication 全有或全无。
- [x] Candidate duty role 与 M05 asset action 双门禁 fail-closed，审计员只读（本地契约）。
- [x] Catalog/field/lineage 本地 registration 重试幂等。
- [x] Candidate publication/rollback 的 physicalAssetRef 永远指向输出。
