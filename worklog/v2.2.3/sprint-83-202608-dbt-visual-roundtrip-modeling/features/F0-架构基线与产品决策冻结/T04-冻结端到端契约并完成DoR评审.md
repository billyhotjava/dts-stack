# T04：冻结端到端契约并完成 DoR 评审

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01～T03

## 目标

把 README 中的提案路由、DTO、错误码、事务、UI 四态和验收映射升级为 FROZEN，使实现期不再重议产品边界。

## Contract-first

- **输入**：已确认 decision register、domain profile、baseline、NFR。
- **输出**：representation/draft/validate/commit/physical-preview/import/audit 的精确契约；Feature/Task 状态评审单。
- **错误路径**：存在 TBD 层、UI 无具名控件、错误只返回 500、验收无法映射测试时，该 Task 不通过。
- **兼容**：冻结旧深链行为；禁止恢复已退役 SQL model API。

## Definition of Done

- [ ] F1～F6 每个 Task 均满足 sprint-workflow DoR。
- [ ] 只有真正满足契约、依赖、UI 和验收门槛的 Task 转为 READY。
