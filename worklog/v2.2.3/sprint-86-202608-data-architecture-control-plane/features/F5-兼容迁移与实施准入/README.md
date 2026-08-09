# F5：兼容迁移与实施准入

**优先级**：P0
**状态**：DONE（Architecture）

## 目标

把 F1～F4 的架构结论转换为可回滚、可分批、可验证的实施路线，并创建下一实施 Sprint，而不是在本 Sprint 直接改代码。

## 契约定义

| 类型 | 输出 |
|---|---|
| 迁移 | 字段/表/路由的 Expand、双读/回填、切换、观测、Contract 顺序 |
| 影响 | 每个拟改 symbol/API 的 GitNexus impact、consumer 与测试矩阵 |
| 交付 | 竖切片 Feature/Task、DoR、NFR、发布/回滚和 E2E 计划 |

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 形成兼容迁移与实施拆分 | P0 | DONE | F1～F4 |

## Definition of Ready

- [x] F1～F4 ADR 均已冻结
- [x] 客户/生产数据画像缺口已登记为 Sprint-87 F0 `BLOCKED_INPUT`，未用本地画像冒充客户事实
- [x] GitNexus 索引失效状态已登记为 Sprint-87 G0 blocker；本 Sprint 的 scoped 前端引用清单可用
- [x] 迁移样本、备份/回滚锚点和旧路由观测输入已转入 Sprint-87 F0，不作为 Sprint-86 架构关闭的伪前置
- [x] `assets/nfr-budget.md` 各预算均有已批准设计值、fitness function 规格与具名运行验证阻塞
- [x] 每个实施切片可追溯到关系契约的一条边、一个 owner 和一个 IT 用例
- [x] IT-06 已登记真实参与者和验收模板；Expand/Contract、回滚和观测窗口已形成评审稿

## Feature Definition of Done

- [x] T01 达到 DONE，ADR-86-10 达到 `ACCEPTED`
- [x] 每个关系/字段/路由变更都有 Expand、双读/回填、切换、观测、Contract 和回滚顺序
- [x] 下一实施 Sprint 具有已批准架构契约、NFR、测试、发布、回滚和 E2E Task，并以 G0 门禁约束实施期精确 schema/DTO
- [x] IT-06 有真实评审记录，IT-01～IT-07 的行动项均已关闭或转入具名后续 Sprint
- [x] Sprint-86 只标记 Architecture DONE，不冒充功能、部署或真实浏览器交付
