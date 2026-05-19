# T02: 成员分配表格 UI 与查询条件

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

用表格替代“部门 + 候选成员下拉 + 加入待审批列表”的逐个添加模式。

## 技术设计

1. 表格查询区包含：
   - 部门 TreeSelect；
   - 姓名 Input；
   - 用户名 Input；
   - 查询 / 重置按钮。
2. 表格列包含：
   - 用户名；
   - 姓名；
   - 部门；
   - 账号状态；
   - 角色状态。
3. 使用 Ant Design Table `rowSelection` 表达勾选态。
4. 分页切换触发服务端查询。

## 影响范围

- `source/dts-admin-webapp/src/admin/views/role-detail.tsx`
- `source/dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts`

## 验证

- [ ] source-level test 覆盖查询控件和 `rowSelection`。
- [ ] `pnpm build` 通过。

## 完成标准

- [ ] 管理员无需逐个下拉选择即可批量维护角色成员。

