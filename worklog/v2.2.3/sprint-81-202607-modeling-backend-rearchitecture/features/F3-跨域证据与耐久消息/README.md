# F3：跨域证据与耐久消息

**优先级**：P0
**状态**：PLANNED

## 目标

把 integration、Catalog、quality、modeling 串为单向证据链，并以分离的 durable event/audit outbox 保证领域集成与中央审计不丢失。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 身份 | Integration → CatalogAssetRef | 外部 identity 必须解析为 CatalogAssetKey |
| 质量 | QualityEvidenceRequest/Response | rule/version/binding/run 固定、终态、未过期、tenant/asset 一致 |
| 事件 | `modeling_domain_event_outbox` | aggregate 顺序、eventId 幂等、独立 dispatcher |
| 审计 | `modeling_audit_outbox` | action/stage/resource/actor/tenant/IP/correlation；独立 dispatcher |
| 中央投递 | audit outbox → public AuditService adapter → dts-admin | 临时失败重试，分类正确，中央历史唯一 |

## UI/UX 规格

无新增 UI。StageGate 的质量错误由现有建模页面以结构化 violation 展示；审计/outbox 故障不显示虚假成功。高风险发布/删除若审计持久化失败必须 fail-closed。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立 Integration 到 Catalog 身份桥接 | P0 | PLANNED | F1/T02 |
| T02 | 建立 QualityEvidence 到 StageGate 桥接 | P0 | PLANNED | T01、F2/T01 |
| T03 | 分离领域事件与审计 Outbox | P0 | PLANNED | F1/T03、F2 事务 |
| T04 | 耐久投递审计到 dts-admin | P0 | PLANNED | T03 |

## Definition of Ready

- [x] Catalog 和质量 canonical 表/owner 已识别。
- [x] 两类 outbox schema、事务和失败语义已定义。
- [ ] AuditService/forwarder、quality service、identity resolver symbol impact 完成。

## 完成标准

- [ ] integration 不直接写模型；StageGate 不直接查跨域 repository。
- [ ] event/audit 两张 outbox 独立重试、指标和 DLQ。
- [ ] dts-admin 故障期间审计 0 丢失，恢复后幂等投递且分类正确。
