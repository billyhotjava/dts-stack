# T01: 后端查询契约与测试

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

提供角色成员分配页专用的分页用户查询契约，避免前端拉取全量用户后本地筛选。

## 技术设计

1. 在 `AdminUserResource` 增加角色上下文查询入口，优先考虑 `GET /api/admin/roles/{role}/assignment-users`。
2. 查询参数支持：
   - `page`
   - `size`
   - `username`
   - `fullName`
   - `deptCode`
   - `deptPath`
3. 复用 `AdminUserService` 的用户快照、部门解析和角色聚合能力。
4. 返回分页结构，单行包含：
   - `id`
   - `keycloakId`
   - `username`
   - `fullName`
   - `email`
   - `deptCode`
   - `deptName`
   - `groupPaths`
   - `enabled`
   - `mdmEnabled`
   - `inRole`
5. `inRole` 只根据当前角色的 `admin_role_member` 关系判断，保持与角色审批执行链路一致。

## 影响范围

- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/vm/AdminUserVM.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/user/AdminUserService.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminKeycloakUserRepository.java`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminRoleMemberRepository.java`
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/user/AdminUserServiceListSnapshotsTest.java`

## 验证

- [ ] 新增或扩展后端测试，先证明 `fullName/deptCode/username/inRole` 需求在当前实现下失败。
- [ ] 实现后运行 focused Maven test。
- [ ] 确认原 `/api/admin/users` 现有用户管理页行为不回归。

## 完成标准

- [ ] `inRole=true` 的用户在查询结果中稳定返回。
- [ ] 姓名和用户名可以独立过滤。
- [ ] 部门过滤不依赖前端全量列表。
- [ ] 页面大小仍受后端上限保护，避免一次返回无限用户。

