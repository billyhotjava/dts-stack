# T03: 大数据量分页与角色状态一致性复核

**优先级**: P0
**状态**: DONE
**依赖**: T01, T02

## 目标

确认角色成员分配查询在人数较多时不会退回到全量拉取，并且跨页勾选/取消时状态不会丢失。

## 技术设计

1. 后端分页上限沿用用户管理页面保护，不开放无限 size。
2. 前端保存跨页 delta：
   - `pendingAdds`
   - `pendingRemovals`
   - 当前页 `inRole`
3. 当前页选中态由 `inRole + pendingAdds - pendingRemovals` 合成。
4. 提交时只发送 delta，不发送整页或全量用户列表。

## 影响范围

- `source/dts-admin-webapp/src/admin/views/role-detail.tsx`
- `source/dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts`

## 验证

- [x] source-level test 覆盖“已在角色内默认勾选”的源码条件。
- [x] source-level test 覆盖“取消已在角色内用户 -> memberRemoves”的源码条件。
- [x] source-level test 覆盖“勾选非角色用户 -> memberAdds”的源码条件。

## 完成标准

- [x] 翻页后待新增/待移除状态仍可提交。
