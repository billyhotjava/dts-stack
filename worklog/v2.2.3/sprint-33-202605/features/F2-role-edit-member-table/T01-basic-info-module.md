# T01: 角色基础信息模块提取

**优先级**: P0
**状态**: READY
**依赖**: F1/T02

## 目标

把角色名称、所属域、描述、审批备注从 `RoleDetailView` 中收敛为独立模块，降低成员表格重构时的状态耦合。

## 技术设计

1. 保持现有只读/编辑模式语义。
2. 不改变 `displayLabel`、`scope`、`description`、`updateReason` 的保存语义。
3. 提取后的模块通过 props 接收值和变更回调，不直接提交审批。

## 影响范围

- `source/dts-admin-webapp/src/admin/views/role-detail.tsx`

## 验证

- [ ] 构建通过。
- [ ] source-level test 能定位角色基础信息模块入口。

## 完成标准

- [ ] 成员表格变更不需要修改角色基础信息渲染逻辑。

