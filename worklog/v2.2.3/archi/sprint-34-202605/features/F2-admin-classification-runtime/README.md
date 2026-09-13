# F2: dts-admin 运行时分类与入库链路

**优先级**: P0
**状态**: DONE

## 目标

让 dts-admin 审计入库链路使用 DB catalog 分类，并保留 platform 的 sourceSystem。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | AuditActionRequest sourceSystem 透传 | P0 | DONE | F1 |
| T02 | AuditV2Service 接入 DB catalog | P0 | DONE | T01 |
| T03 | AuditIngestResource 未分类治理 | P0 | DONE | T02 |
| T04 | 字典/分组查询迁移到 DB catalog | P1 | DONE | T02 |

## 完成标准

- [x] platform 事件入库后 UI 显示业务端审计。
- [x] 未知动作显示未分类，不再显示系统管理或数据资产。
- [x] 审计模块/分组下拉来自 DB catalog，不依赖旧 URL 映射表。
