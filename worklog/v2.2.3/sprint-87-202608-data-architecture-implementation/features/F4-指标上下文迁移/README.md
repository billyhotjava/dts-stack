# F4：指标上下文迁移

**优先级**：P0

**状态**：CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E

**证据**：`../../assets/implementation-evidence-20260810.md`

## 目标

把指标的业务分类、数据域和过程上下文从自由文本收口为稳定 ID，在不破坏现有消费者的前提下完成兼容迁移。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 实现指标上下文兼容迁移 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E | F0/T01（生产消费者观测仍阻塞） |

## Feature DoD

- [ ] ADR-86-06/07 的业务分类、数据域和跨域约束落到统一服务校验。
- [ ] 所有已登记消费者逐项通过双读、双写、回填、切换和回滚验证。
- [ ] HIGH 风险影响面经刷新后的 GitNexus 和当前代码审计共同确认。
