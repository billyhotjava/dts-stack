# F1：模块化控制面边界

**优先级**：P0
**状态**：PLANNED

## 目标

在 `dts-platform` 内建立可执行的模块依赖边界与公开端口，让 integration、catalog、quality、modeling、execution、audit 各自只有一个 owner。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 模块 | architecture-overview §2 | 依赖单向、无环；禁止跨域 entity/repository |
| 资产端口 | Catalog identity port | 只暴露 `CatalogAssetType/Key` 与注册/解析 DTO |
| 建模端口 | plan/spec/gate/lifecycle/release/materialization application ports | HTTP/resource 只委托端口，不直接写 repository |
| 审计端口 | `AuditService.auditAction(actionCode,stage,resourceId,payload)` | 保持公共入口；耐久化由 adapter/outbox 承担 |
| 守卫 | ArchUnit + source contract | forbidden imports=0，legacy owner 新引用=0 |

## UI/UX 规格

无新增 UI。Sprint-80 页面 URL 和信息架构不变；后台动作仍失败关闭，直到对应 vertical slice 完成并在 F6 验收。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立模块依赖守卫 | P0 | PLANNED | F0/T01 |
| T02 | 收敛 Catalog 资产身份端口 | P0 | PLANNED | T01 |
| T03 | 冻结应用端口与公共审计边界 | P0 | PLANNED | T01 |

## Definition of Ready

- [x] 目标模块职责、允许依赖和禁止依赖已写入架构附件。
- [ ] 目标 package/symbol 的 GitNexus impact 已完成。
- [ ] 现有 circular dependency 和临时例外清单已冻结。

## 完成标准

- [ ] ArchUnit 对模块边界和 legacy owner 新引用 fail-fast。
- [ ] CatalogAssetKey/Type、质量和 AuditService 均复用既有 owner。
- [ ] Spring context 中无平行 application service/repository owner。
