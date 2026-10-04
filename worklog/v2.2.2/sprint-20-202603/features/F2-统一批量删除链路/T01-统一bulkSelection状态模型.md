# T01: 统一 bulkSelection 状态模型

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

将左树、治理弹窗、平铺列表的批量选择收敛到一套统一状态模型中。

## 技术设计

- 在页面层新增 `bulkSelection`
- 包含：
  - `selectedIds`
  - `selectedSource`
  - `lastChangedAt`
- 治理弹窗不再持有独立的长期选择状态，而是直接读写 `bulkSelection`
- 后续平铺列表接入时也直接复用这套状态

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/components/GovernanceModal.tsx`
- `source/dts-platform-webapp/src/pages/modeling/sqlModeling.types.ts`

## 验证

- [ ] 左树勾选和治理勾选互相可见
- [ ] 清空选择会同步影响各入口
- [ ] 删除完成后能自动清理成功删除项

## 完成标准

- [ ] 页面内只保留一套批量选择语义
- [ ] 后续批量操作都可直接复用
