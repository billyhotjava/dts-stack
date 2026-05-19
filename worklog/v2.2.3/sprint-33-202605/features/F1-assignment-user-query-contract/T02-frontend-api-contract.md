# T02: 前端 API 类型与客户端封装

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在 `dts-admin-webapp` 中定义角色成员分配表格所需的类型和 API 客户端方法。

## 技术设计

1. 在 `AdminUser` 兼容字段基础上新增 `RoleAssignmentUser` 或等价类型。
2. 在 `adminApi` 新增 `getRoleAssignmentUsers(role, query)`。
3. 查询参数与后端保持一致：分页、部门、姓名、用户名。
4. 返回复用 `PagedResult<T>`，避免自定义分页结构漂移。

## 影响范围

- `source/dts-admin-webapp/src/admin/types.ts`
- `source/dts-admin-webapp/src/admin/api/adminApi.ts`
- `source/dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts`

## 验证

- [ ] source-level test 覆盖 API path、query 参数名称和返回类型消费点。
- [ ] TypeScript 编译通过。

## 完成标准

- [ ] 前端可以通过单一 API 获取带 `inRole` 的分页用户。

