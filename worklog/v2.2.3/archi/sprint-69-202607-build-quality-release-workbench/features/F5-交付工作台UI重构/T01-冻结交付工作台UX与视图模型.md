# T01：冻结交付工作台 UX 与视图模型

**优先级**：P0
**状态**：READY
**依赖**：F1-T01

## 目标

冻结计划级第六步的信息架构、状态文案、响应式布局和纯视图模型，先消除 UI 对 lifecycle 状态的自行推断。

## 技术设计

- 页面固定为概览、候选范围、构建、质量、审核发布、时间线六区。
- 首屏只显示一个首要阻塞和一个主动作，其他动作进入对应区域。
- `deliveryViewModel.ts` 只消费后端 `status/blockers/allowedActions`。
- 所有状态使用图标、文字和颜色三重表达；390px 使用纵向步骤与底部动作区。

## 影响范围

- 新增 `pages/modeling/delivery/deliveryViewModel.ts`
- 新增 view-model tests
- 更新 Sprint-69 UX 矩阵

## 实施步骤

1. 先写候选为空、构建失败、质量失败、待审核、PARTIAL、PUBLISHED、ROLLED_BACK 快照测试。
2. 实现纯视图模型和业务可读文案。
3. 运行 `node --import tsx --test src/pages/modeling/delivery/deliveryViewModel.test.ts`。

## 完成标准

- [ ] 后端同一状态在所有页面产生一致标签、主动作和阻塞文案。
- [ ] UX 评审明确桌面、390px、loading、empty、error 和 stale 状态。
- [ ] **UI 实现验收**：桌面与 390px 原型覆盖六个工作台区域，关键状态同时使用图标、文字和颜色表达，Chrome 95 下无隐藏主动作和横向溢出。
