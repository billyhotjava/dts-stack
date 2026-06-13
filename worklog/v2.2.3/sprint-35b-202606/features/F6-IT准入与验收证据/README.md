# F6: IT 准入与验收证据

**优先级**: P0
**状态**: READY
**对应缺陷**: 全部（验收闭合）

## 目标

为 Sprint-35b 提供真实集成测试证据，闭合 Sprint-35 因"内存态不持久"而无法成立的验收，证据落 `it/evidence/`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 重启持久性 IT 证据 | P0 | READY | F1 |
| T02 | 安全对等 IT 证据（两链路 permission/RLS/audit 一致） | P0 | READY | F2 |
| T03 | 发布闭环/回滚 IT 证据 | P0 | READY | F3 |

## 完成标准
- [ ] `it/evidence/persistence/`：重启不丢、并发版本锁。
- [ ] `it/evidence/security-parity/`：pack 与 lifecycle 链路安全行为对等。
- [ ] `it/evidence/publish-closure/`：发布幂等、补偿、回滚链。
- [ ] 阻断条件（见 it/README）无一触发。
