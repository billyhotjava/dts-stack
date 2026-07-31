# T04：耐久投递审计到 dts-admin

**优先级**：P0
**状态**：PLANNED
**依赖**：T03

## 目标

把 platform 审计 outbox 可靠、幂等地投递到 dts-admin，继续复用公共 AuditService 和中央动作字典/历史。

## 技术设计（Contract-first）

- **输入契约**：未 delivered 的 `modeling_audit_outbox`；字段含 actionCode/stage/resource/actor/tenant/clientIp/correlationId/payload。
- **输出契约**：dts-admin 按 `auditId` 幂等接受；成功写 `delivered_at`，中央记录分类不可为“未分类”。
- **调用边界**：业务调用方仍使用 `AuditService.auditAction(...)`；durable adapter 捕获审计命令并写 outbox，dispatcher 使用 admin forward contract。
- **超时/重试**：connect 2s/read 5s；仅 network/5xx/429 以 1/2/4/8/16s+jitter 重试，4xx 进入 DLQ/告警。
- **安全**：actor/tenant/IP 服务端解析；IP 复用 `IpAddressUtils.resolveClientIp`；payload secret/redaction；pairwise service auth。
- **失败策略**：普通动作业务可提交并留 backlog；发布、删除等高风险动作若本地 audit outbox 无法持久化则回滚。

## 影响范围

AuditService forward seam、dts-admin client/receiver、action catalog registration、metrics/alerts。

## 验证

- [ ] dts-admin down 30s 后恢复，0 丢失、60s 内 backlog 清空。
- [ ] timeout/429/5xx/4xx/duplicate tests。
- [ ] 中央记录 actionCode/stage/actor/tenant/IP/correlation 完整且分类正确。
- [ ] 本地 outbox 清理不影响中央历史。

## Definition of Done

- [ ] audit backlog、oldest age、retry、DLQ 指标可观测。
- [ ] dts-admin 中央历史为唯一长期审计事实。
- [ ] 任何审计失败不被静默吞掉或降级为普通日志。
