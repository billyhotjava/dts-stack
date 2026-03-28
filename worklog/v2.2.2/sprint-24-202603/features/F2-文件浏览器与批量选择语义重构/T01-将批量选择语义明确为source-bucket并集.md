# T01: 将批量选择语义明确为 source-bucket 并集

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

明确 `tree / governance / list` 三类选择来源的语义边界，避免看似统一、实则互相覆盖。

## 技术设计

- `bulkSelection` 保留并集模型
- 但不再在入口打开时无条件重置全部选择
- 清空动作支持：
  - 清空全部
  - 清空单一来源
- 页面上补充来源感知反馈

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/sqlModelBulkSelection.helpers.ts`

## 验证

- [ ] helper 单测
- [ ] 页面交互回归

## 完成标准

- [ ] 多来源选择的语义可解释、可预期
