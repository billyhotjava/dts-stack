# F0：基线与退役门禁

**优先级**：P0
**状态**：IN_PROGRESS

## 目标

在任何源码或 schema 删除前，冻结模块/调用图、逐环境客户存量画像和可恢复备份，使每个退役批次都有可执行的 Go/No-Go。

## 契约定义

| 类型 | 契约 | 关键字段/规则 |
|---|---|---|
| 调用图 | `RetirementCallerManifest` | symbol/route/caller/owner/runtimeUsage；GitNexus impact + 精确 source/runtime 清单 |
| 数据画像 | `EnvironmentDataProfile` | environmentId/databaseId/schemaRevision/tenant/table/count/status/orphan/lastWriteAt |
| 迁移 | `MigrationManifest` | source/target/revision/checksum/decision/conflict/backup；dry-run 零写入 |
| 备份 | `BackupEvidence` | URI/size/SHA-256/databaseId/createdAt/restoreVerifiedAt/RTO |
| 停机 | `PreDropDecision` | caller=0、conflict=0、orphan=0、manifest drift=0、审批齐全 |

## UI/UX 规格

本 Feature 不新增业务 UI。客户环境操作通过只读命令、受控维护窗口和脱敏报告完成；Sprint-80 页面在 F6 最终验收前继续保持未接后台动作失败关闭。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结架构、调用图与退役清单 | P0 | IN_PROGRESS | - |
| T02 | 客户存量画像与停机判定 | P0 | PLANNED | T01、客户只读授权 |
| T03 | 备份恢复与迁移演练 | P0 | PLANNED | T02、隔离恢复环境 |

## Definition of Ready

- [x] 目标架构、唯一状态链和删除原则已冻结。
- [x] 当前环境数据基线已记录，且明确不可外推客户。
- [ ] 每个待改 symbol 的 GitNexus impact 与 runtime caller 清单完成。
- [ ] 客户环境只读授权、维护窗口和备份位置确认。

## 完成标准

- [ ] 每个环境都有可复跑的数据画像、调用方 manifest 和数据库标识。
- [ ] 每个待删表都有可恢复备份及对象级 checksum。
- [ ] `PreDropDecision` 能以非零退出码阻断未知存量、漂移或冲突。
- [ ] 证据脱敏，不保存凭据或客户原始数据。
