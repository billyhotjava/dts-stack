# F2: 收藏功能彻底清理

**优先级**: P0
**状态**: READY

## 目标

工作台菜单里早已没有"我的收藏"入口，但前后端残留代码 + `portal_user_favorite` 表仍在。本 feature 彻底清理：前端 UI/服务、后端 Resource/Service/Repository/实体、数据库表，一次清走。

## 约束

- **不可逆操作**：数据库 dropTable 前必须有完整 dump 作为回滚物料。
- **顺序保障**：建议按 T01 → T02 → T03 → T04 → T05 的顺序执行（先留 dump，再拆代码，最后删 DB 层），确保任何时刻都能回滚。
- 执行 T01 Liquibase changeset 前需**人工确认** `portal_user_favorite` 表的数据备份落位。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Liquibase dropTable + dump 物料 | P0 | READY | - |
| T02 | 删除后端 PortalUserFavorite 实体 + Repository | P0 | READY | T01 |
| T03 | 删除 WorkbenchService 收藏方法 + Resource 端点 | P0 | READY | T02 |
| T04 | 删除前端 workbenchService.ts 的收藏类型与方法 | P0 | READY | - |
| T05 | 删除 workbench/index.tsx 收藏块与 Modal | P0 | READY | T04 |

## 完成标准

- [ ] `portal_user_favorite` 表已从 schema 删除，Liquibase master changelog 包含对应 changeset。
- [ ] 代码仓库无 `PortalUserFavorite`、`WorkbenchFavorite`、`FavoriteRequest` 等 symbol 残留。
- [ ] `/api/workbench/favorites` 4 个端点全部返回 404。
- [ ] 前端工作台无收藏块、Modal、"编辑收藏"按钮。
- [ ] `./mvnw test`、`pnpm test`（或 `yarn test`）、Liquibase update 均通过。
- [ ] `worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql` 存在并通过 checksum 校验。
