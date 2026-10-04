# T02: AuditV2Service 接入 DB catalog

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

将 V2 审计写入分类从 JSON registry/override 混合模式迁移为 DB catalog 优先。

## 技术设计

- 按 `sourceSystem + buttonCode/actionCode` 查 DB catalog。
- request override 仅允许用于已注册动作的运行时补充，不作为未知动作的正式分类依据。
- 未命中时使用 `UNCLASSIFIED_PLATFORM_EVENT` 或 `UNCLASSIFIED_ADMIN_EVENT`。

## 影响范围

- `AuditV2Service.java`
- `AuditButtonRegistry.java`
- `AuditActionCatalogService.java`

## 验证

- [x] 已注册 platform actionCode 使用 DB module/action。
- [x] 未注册 actionCode 记录 miss 并使用未分类动作。

## 完成标准

- [x] 运行时分类不再依赖 JSON。
