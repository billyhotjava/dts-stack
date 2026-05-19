# Sprint-33: 角色管理成员分配重构（202605）

**时间**: 2026-05
**状态**: IN_PROGRESS
**类型**: UX / Refactor / Contract（dts-admin + dts-admin-webapp）
**目标**: 把角色编辑页从“按部门下拉逐个添加成员”重构为“角色基础信息模块 + 可查询分页用户表”，支持按部门、姓名、用户名筛选，已在角色内的用户默认勾选，并通过现有审批流提交成员增删差异。

## 背景

当前 `dts-admin-webapp` 角色编辑页已经把角色基础信息和成员变更放在同一个页面，但成员添加仍是小规模交互：管理员必须先选部门，再在候选下拉框中逐个选择用户，已在角色内的用户也不会出现在候选列表里。用户规模增大后，这个流程无法快速盘点角色成员，也无法通过一张表完成批量勾选、取消和查询。

本 Sprint 保留现有角色变更审批链路，不直接绕过审批写入成员关系。重构重点是把“谁在角色中、谁将被加入/移除”的状态前移到用户表格里，并把查询能力下沉到后端分页契约，避免前端全量拉用户后本地筛选。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 角色成员用户查询契约 | P0 | 3 | IN_PROGRESS | 现有 `/admin/users`、`/admin/roles/{role}/members` |
| F2 | 角色编辑页模块化与成员表格 | P0 | 4 | READY | F1 |
| F3 | 回归验证、IT 证据与状态收口 | P0 | 3 | READY | F1, F2 |

**统计**: READY=7, IN_PROGRESS=1, DONE=0, BLOCKED=0

## 目标体验

1. 角色编辑页上半部分只负责角色名称、所属域、描述和审批备注。
2. 页面下半部分显示用户表格，列包含用户名、姓名、部门、账号状态、角色内状态。
3. 表格支持按部门、姓名、用户名查询；查询和分页不依赖前端全量拉取。
4. 用户已经属于该角色时，勾选框默认选中；取消勾选表示待移除。
5. 未在角色内的用户被勾选后表示待新增。
6. 提交时仍生成现有 `memberAdds` / `memberRemoves` payload，并通过 `createChangeRequest + submitChangeRequest` 审批流。
7. 存在待审批角色变更时，页面只读或禁止提交新的成员变更。

## 非目标

- 不重写角色审批中心。
- 不改变角色成员最终落库路径和审批通过后的执行逻辑。
- 不把角色成员直接同步到 Keycloak realm role；继续沿用当前 `admin_role_member` 关系作为本模块事实源。
- 不在本 Sprint 改造菜单绑定逻辑，菜单仍由“菜单管理”模块维护。

## 完成标准

- [ ] 后端提供可分页的角色成员候选用户查询契约，支持部门、姓名、用户名筛选，并返回 `inRole` 状态。
- [ ] 前端不再依赖 `getAllAdminUsers()` 完成角色成员编辑。
- [ ] 角色编辑页拆分为角色基础信息模块和成员分配表格模块。
- [ ] 表格默认勾选已在角色内的用户，跨页勾选/取消能正确计算新增和移除差异。
- [ ] 提交 payload 继续兼容现有角色审批、审批详情和审批通过执行链路。
- [ ] 覆盖后端查询契约测试、前端 source-level 行为测试和 `dts-admin-webapp` 构建。
- [ ] IT 证据写入 `worklog/v2.2.3/sprint-33-202605/it/`。

## 验证策略

- 后端 focused unit/contract test：角色成员候选查询按用户名、姓名、部门过滤，并标记 `inRole`。
- 前端 source-level contract test：角色编辑页不再调用全量用户接口，存在成员表格、查询条件和 rowSelection 差异状态。
- 前端构建：`pnpm build` from `source/dts-admin-webapp`。
- 后端 focused test 或 compile：从 `source` 执行 `./mvnw -q -pl dts-admin -Dtest=... test`，必要时补 `-DskipTests compile`。

## 相关文件

- `source/dts-admin-webapp/src/admin/views/role-detail.tsx`
- `source/dts-admin-webapp/src/admin/views/role-management.tsx`
- `source/dts-admin-webapp/src/admin/api/adminApi.ts`
- `source/dts-admin-webapp/src/admin/types.ts`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminUserResource.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/user/AdminUserService.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminKeycloakUserRepository.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminRoleMemberRepository.java`

