# Sprint-33 IT: 角色管理成员分配重构

**状态**: DONE（聚焦验证通过；未启动浏览器手工 smoke）
**日期**: 2026-05-19

## 验收目标

- 角色编辑页能够通过分页用户表维护成员。
- 表格支持部门、姓名、用户名查询。
- 已在角色内的用户默认勾选。
- 新增/移除成员继续走角色变更审批流。
- 大用户量场景不依赖前端全量拉取用户。

## 已执行验证

| 验证项 | 命令 / 方式 | 证据 |
|--------|-------------|------|
| 后端查询契约 | `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-admin -am -Dtest=AdminUserServiceListSnapshotsTest -Dsurefire.failIfNoSpecifiedTests=false test` from `source` | `it/evidence/backend-focused-20260519.md` |
| 前端 source contract | `./node_modules/.bin/vitest run src/admin/views/role-detail.assignment-table.source-contract.test.ts` from `source/dts-admin-webapp` | `it/evidence/frontend-focused-20260519.md` |
| 前端生产构建 | `pnpm build` from `source/dts-admin-webapp` | `it/evidence/frontend-focused-20260519.md` |

## 当前结论

后端查询契约、前端 source-level 合约测试和 `dts-admin-webapp` 生产构建均已通过。角色编辑页已改为分页用户表维护成员，保留现有角色变更审批流。

2026-05-19 追加完善：`RoleDetailView` 已进一步拆为 `RoleBasicInfoSection` 与 `RoleMemberAssignmentSection`，并通过 source-level contract、`pnpm build` 和后端 focused test 复验。

2026-05-19 追加完善：按 TDD 补充待审批互斥保护 RED 用例，确认失败后统一禁用成员表格选择、当前成员移除/恢复/撤销按钮和成员切换 handler；复验 source-level contract 5/5、`pnpm build`、后端 focused test 均通过。

## 剩余风险

- 未启动本地浏览器进行人工 smoke；需要在联调环境确认真实组织树、用户快照和角色成员数据的组合展示。
- `pnpm build` 输出包含项目既有 Vite chunk 警告和 browserslist 过期提示，本 Sprint 未处理。
