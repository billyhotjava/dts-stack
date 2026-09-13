# T03：实现 PUBLISHED fork 与幂等续编

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T02

## 目标

让 PUBLISHED 只能通过显式 `FORK_PUBLISHED` 派生新 DRAFT，并让 DRAFT 的重复 create 请求幂等返回同一活动草稿。

## 技术设计（Contract-first）

- **输入契约**：create body `intent,baseModelRevision/checksum,baseImplementationRevision/checksum?,idempotencyKey`。
- **输出契约**：`AuthoringDraftView` 含 `forkedFromRevision/checksum?`、新 DRAFT model snapshot、source bundle、draft ETag。
- **数据流**：锁定 canonical head → 校验 pins/status → clone published snapshot/implementation bundle → 建 DRAFT head + authoring draft → receipt/audit。
- **错误路径**：PUBLISHED+EDIT_DRAFT → 409 `MODEL_AUTHORING_FORK_REQUIRED`；DRAFT+FORK → 409；base pins stale → 412；同 key 异 payload → 409。
- **复用点**：`synchronizeDbtManagedFields` 的 PUBLISHED→DRAFT 不可变先例、ModelSpec CAS/revision repository、draft idempotency。
- **审计**：`MODELING_AUTHORING_DRAFT_FORK` 记录 before/after pins，不记录 bundle 正文。

## UI 交互规格

PUBLISHED 页面唯一主动作“创建新草稿版本”；loading 防重；成功原地切换到 DRAFT；失败保留 PUBLISHED 视图。

## 影响范围

`ModelSpecApplicationService` 的显式 fork seam、authoring service/resource、模型工作台按钮及相应 service/MockMvc/Vitest；不修改已发布 revision 和 release candidate 表。

## 验证（RED→GREEN）

- [ ] PostgreSQL IT：旧 published revision/候选 pins 不变，新 head 为 DRAFT。
- [ ] 并发两个 fork：一个成功，另一个幂等 replay 或明确冲突，无双 head。
- [ ] 403/409/412 与审计分类测试。

## Definition of Done

- [ ] PUBLISHED 无任何原地写路径。
- [ ] 同 key 重放得到同一 draft/revision；异 payload 被拒绝。
- [ ] source bundle/provenance/AssetKey 身份在 fork 前后可追溯。
