# F1: 审批契约与 Stub（lite 版）

**优先级**: P0
**状态**: READY
**设计阶段**: 骨架
**依赖**: F0

## 目标

**不做完整审批引擎实现**。只做：① 定义 dts-approval 对外契约（REST + Kafka）；② 实现一个最小 Stub 服务；③ 发布业务侧 `ApprovalPort` SDK。让 F2/F3 可以 day-1 按"将来的审批接口"集成，**等客户内部审批制度讨论完毕后**，F1-full 上线**业务方零改动切换**。

**F1-full（完整审批引擎）放到后续 Sprint**——等客户提供：① 密级×业务类型×审批级数矩阵；② 审批人路由规则（按部门/按角色）；③ 通知/催办/委托等高级特性。

## 设计红线（brainstorm 已对齐）

- **契约稳定第一**：REST + Kafka + Payload 信封字段在本 Sprint 定死，后续不破坏性变更
- **Stub 不理解业务**：只做"提单→等待→approved/rejected/cancelled"状态机；`bizPayload` / `bizContext` 原样回传
- **透传密级不校验**：`dataClassification` / `bizContext.orgUnit` 字段由 Stub 接收并存档，但不做任何访问控制；full 阶段引擎做校验
- **密级用 dts-common**：枚举 & 规则函数直接引用 `com.yuzhi.dts.common.security.SecurityLevelCatalog`
- **业务方只依赖 `ApprovalPort` 接口 + Kafka 事件**，不直连 Stub DB / UI / 内部 API

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [审批 API 契约定义](T01-审批API契约定义.md) | P0 | READY | F0 |
| T02 | [Stub 服务 MVP](T02-Stub服务MVP.md) | P0 | READY | T01 |
| T03 | [ApprovalPort SDK](T03-ApprovalPort-SDK.md) | P0 | READY | T01 |
| T04 | [Stub 最小管理页](T04-Stub最小管理页.md) | P1 | READY | T02 |

## 对外接口（契约快照）

### REST

```
POST /approvals                  提单，返回 { approvalId, status: "PENDING" }
GET  /approvals/{id}             查询，返回 { approvalId, status, history[] }
POST /approvals/{id}/cancel      申请方撤销
```

### Kafka Topics

```
approval.request.submitted       提单成功
approval.request.approved        审批通过（业务方据此落库）
approval.request.rejected        审批驳回
approval.request.cancelled       已撤销
```

### Payload bizContext（F0 信封的 payload 部分）

```json
{
  "approvalId":    "apr-uuid",
  "bizType":       "mdm.project.create",
  "bizPayload":    { /* 完整业务变更 */ },
  "requestedBy":   "user-xxx",
  "bizContext": {
    "orgUnit":      "dept-xxx",
    "severity":     "NORMAL",
    "relatedResources": ["PRJ-001"]
  },
  "currentStatus": "PENDING"
}
```

**注意**：F0 信封顶层已有 `dataClassification`（等于该审批单数据的密级），bizContext 不重复；申请人密级由 full 阶段引擎按 requestedBy 查 Keycloak。

## 完成标准

- [ ] REST API 契约文档与 openapi 定义发布
- [ ] Kafka topics 声明并预创建，事件信封通过 F0 starter 发送
- [ ] Stub 服务可独立启停，默认自动 approve（可在管理页手动 reject / 延迟）
- [ ] `ApprovalPort` SDK 发布（HTTP 实现 + Kafka 消费端基础类）
- [ ] MDM demo：submit → 收到 approved 事件 → UPSERT 落库的全链路可跑通
- [ ] Stub 不校验密级/部门，只透传并存档（full 阶段再做校验）

## 下一 Sprint 承接（F1-full）

后续 Sprint 在此基础上扩展：

- 审批数据模型（审批流定义 / 审批单 / 节点 / 历史 / 附件）
- 审批规则引擎（`dataClassification` / `bizContext.orgUnit` / severity → 审批链）
- 审批人密级校验（`SecurityLevelCatalog.parseMaxDataLevel`）
- 审批中心前端（发起 / 待办 / 已办 / 详情）
- 组织树集成（Keycloak groups / admin 只读 API）
- 通知（站内信 / 邮件 / 催办）
