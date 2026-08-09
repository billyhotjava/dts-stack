# F5：兼容迁移与实施准入

**优先级**：P0
**状态**：DRAFT

## 目标

把 F1～F4 的架构结论转换为可回滚、可分批、可验证的实施路线，并创建下一实施 Sprint，而不是在本 Sprint 直接改代码。

## 契约定义

| 类型 | 输出 |
|---|---|
| 迁移 | 字段/表/路由的 Expand、双读/回填、切换、观测、Contract 顺序 |
| 影响 | 每个拟改 symbol/API 的 GitNexus impact、consumer 与测试矩阵 |
| 交付 | 竖切片 Feature/Task、DoR、NFR、发布/回滚和 E2E 计划 |

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 形成兼容迁移与实施拆分 | DRAFT | F1/T01、F1/T02、F2～F4 |

## Definition of Ready

- [ ] F1～F4 ADR 均已冻结
- [ ] 客户/生产数据画像已补充
- [ ] GitNexus 索引已刷新并重新 impact
- [ ] 迁移 dry-run、回滚与旧路由观测规则已定义
- [ ] 每个实施切片可追溯到关系契约的一条边、一个 owner 和一个 IT 用例
