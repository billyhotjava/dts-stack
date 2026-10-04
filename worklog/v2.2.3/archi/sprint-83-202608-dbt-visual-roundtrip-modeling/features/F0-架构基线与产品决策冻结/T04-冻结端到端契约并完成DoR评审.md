# T04：冻结当前切片契约并完成 DoR 评审

**优先级**：P0  
**状态**：DONE（83a）
**依赖**：T01～T03

## 目标

把 README 中的提案路由、DTO、错误码、事务、UI 四态和验收映射升级为 FROZEN，并只对当前准备拉取的 P0 竖切片执行 DoR，使实现期不再重议产品边界，同时允许未排期的 P1/P2 Task 保持 DRAFT。

## Contract-first

- **输入**：已确认 decision register、domain profile、baseline、NFR 与当前待拉取切片；S3 materialization 额外消费 T05。
- **输出**：当前 P0 切片涉及的 representation/import/audit 精确契约；切片与 Feature/Task 状态评审单。draft/validate/commit/physical-preview 等 P1 契约可先保持 FROZEN_POLICY/DRAFT_IMPLEMENTATION，不阻断 P0。
- **错误路径**：存在 TBD 层、UI 无具名控件、错误只返回 500、验收无法映射测试时，该 Task 不通过。
- **兼容**：冻结旧深链行为；禁止恢复已退役 SQL model API。

## Definition of Done

- [x] 83a P0 切片满足 sprint-workflow DoR；未要求 F1～F6 全部 Task 同时 READY。
- [x] 只有 83a P0 Task 可转 READY；P1/P2 保持 DRAFT，S3 继续受 H83-01/F0-T05 阻断。
