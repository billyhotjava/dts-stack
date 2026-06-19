# F2: 连接器与驱动

**优先级**: P1
**状态**: READY

## 目标

收纳现网连接器注册（`ConnectorRegistryPage`）与 JDBC 驱动管理（`JdbcDriversPage`）两个 foundation 页面进阶段①，作为数据源背后的"可用连接类型 + 驱动包"管理面。service 对齐 `connectorsService`、`jdbcDriversService`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-连接器注册.md) | 连接器注册 | P1 | READY | S1 |
| [T02](./T02-JDBC驱动管理.md) | JDBC 驱动管理 | P1 | READY | S1 |

## 完成标准

- [ ] `ConnectorRegistryPage` 用 CompactTable 列出已注册连接器（类型/图标/版本/状态），接 `connectorsService`。
- [ ] `JdbcDriversPage` 列出 JDBC 驱动（驱动名/类名/版本/上传状态），接 `jdbcDriversService`。
- [ ] 两页均在阶段① slot 内可路由可访问，`VITE_USE_MOCK` 下有样例数据。
