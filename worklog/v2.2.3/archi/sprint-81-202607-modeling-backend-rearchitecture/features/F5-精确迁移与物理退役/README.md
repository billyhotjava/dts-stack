# F5：精确迁移与物理退役

**优先级**：P0
**状态**：PLANNED

## 目标

在调用方、数据、备份和停机门禁逐项通过后，按批次物理删除旧 routes/services/entities/repositories/tables，最终删除 legacy migration/usage runtime ledger。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 调用方 | F0 `RetirementCallerManifest` | runtime/source/external consumer=0 |
| 迁移 | `MigrationManifest` | source/target/revision/checksum/conflict/orphan；dry-run/apply/verify/rollback |
| 备份 | `BackupEvidence` | SHA-256 + restore rehearsal |
| 删除 | R0～R6 | 同批重接、停机复核、forward changeset、普通 404 |
| 保留 | audit/changelog retention | dts-admin 中央历史与历史 Liquibase checksum 不变 |

## UI/UX 规格

旧 URL 删除后使用产品统一 404，不新增 410、重定向、恢复页或 tombstone。新 UI 只使用 canonical API；迁移冲突通过运维报告处理，不在业务页面伪自动修复。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 重接并删除立即可删 HTTP 面 | P0 | PLANNED | F0、F1～F3 |
| T02 | 迁移 SQL 模型、业务对象与 vNext 契约 | P0 | PLANNED | F0/T03、F2～F4 |
| T03 | 物理删除旧服务、实体、仓储与业务表 | P0 | PLANNED | T01～T02、停机 GO |
| T04 | 最终删除 legacy migration/usage runtime ledger | P0 | PLANNED | T03、归档/审计签字 |

## Definition of Ready

- [x] 批次和全局删除门禁已定义。
- [ ] 每个环境 F0/T02/T03 为 GO。
- [ ] 每个目标 symbol impact、caller/FK/OpenAPI/runtime usage 为 0。
- [ ] 新 forward Liquibase 与 rollback/restore 方案获 review。

## 完成标准

- [ ] 旧运行面源码和 schema 物理不存在，旧 URL 为普通 404。
- [ ] 不保留长期 410、tombstone、双写、feature flag、shim interface。
- [ ] 中央审计历史和历史 changelog 完整，备份可恢复。
