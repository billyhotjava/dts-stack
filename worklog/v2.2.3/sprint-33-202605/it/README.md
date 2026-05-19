# Sprint-33 IT: 角色管理成员分配重构

**状态**: IN_PROGRESS
**日期**: 2026-05-19

## 验收目标

- 角色编辑页能够通过分页用户表维护成员。
- 表格支持部门、姓名、用户名查询。
- 已在角色内的用户默认勾选。
- 新增/移除成员继续走角色变更审批流。
- 大用户量场景不依赖前端全量拉取用户。

## 计划验证

| 验证项 | 命令 / 方式 | 证据 |
|--------|-------------|------|
| 后端查询契约 | `./mvnw -q -pl dts-admin -Dtest=AdminUserServiceListSnapshotsTest test` from `source` | `it/evidence/backend-focused-20260519.md` |
| 前端 source contract | `./node_modules/.bin/vitest run src/admin/views/role-detail.assignment-table.source-contract.test.ts` from `source/dts-admin-webapp` | `it/evidence/frontend-focused-20260519.md` |
| 前端生产构建 | `pnpm build` from `source/dts-admin-webapp` | `it/evidence/frontend-focused-20260519.md` |

## 当前结论

Sprint 已创建，代码重构尚在执行中。未取得验证证据前不得标记 DONE。

