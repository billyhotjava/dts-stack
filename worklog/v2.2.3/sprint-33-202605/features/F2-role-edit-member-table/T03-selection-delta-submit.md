# T03: 勾选差异计算与审批 payload 接入

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

把表格勾选差异转换为现有角色审批 payload，继续走 `memberAdds` / `memberRemoves`。

## 技术设计

1. 当前页行的有效选中态：
   - `inRole=true` 且不在 `pendingRemovals` 中；
   - 或用户名在 `pendingAdds` 中。
2. 勾选非角色用户：加入 `pendingAdds`，从 `pendingRemovals` 删除。
3. 取消已在角色用户：加入 `pendingRemovals`，从 `pendingAdds` 删除。
4. 取消待新增用户：从 `pendingAdds` 删除。
5. 提交时沿用现有 `createChangeRequest` payload 字段。

## 影响范围

- `source/dts-admin-webapp/src/admin/views/role-detail.tsx`

## 验证

- [ ] source-level test 覆盖 `memberAdds` 和 `memberRemoves` 字段仍存在。
- [ ] 人工走读审批中心 change snapshot 字段不需要改名。

## 完成标准

- [ ] 审批通过后的 `applyRoleMemberMutations` 不需要改造即可处理成员变更。

