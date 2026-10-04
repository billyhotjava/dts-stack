# T01: 角色基础信息模块提取

**优先级**: P0
**状态**: DONE
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

- [x] 构建通过。
- [x] 角色基础信息渲染保持在成员分配表格上方。
- [x] source-level test 覆盖 `RoleBasicInfoSection` 与 `RoleMemberAssignmentSection` 的独立模块和调用点。

## 完成标准

- [x] 成员表格变更不需要修改角色基础信息渲染逻辑。
