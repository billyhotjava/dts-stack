# F3：canonical 模型应用引擎

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

复用现有 ModelSpec 与 dbt ownership 服务，按拓扑创建或修订普通模型及其实现，不产生半完成候选。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 按拓扑应用 ModelSpec 与 revision | P0 | IN_PROGRESS | F2 |
| T02 | 创建普通与 DBT_BACKED implementation | P0 | IN_PROGRESS | T01 |
| T03 | 绑定 dbt artifact 与技术依赖 | P0 | IN_PROGRESS | T02 |
| T04 | 收口事务幂等并发与重试 | P0 | IN_PROGRESS | T01/T02/T03 |

## 完成标准

- [ ] 候选按依赖顺序创建并固定 revision。
- [ ] DESIGNER_GENERATED 与 DBT_BACKED 不混淆所有权。
- [ ] 每候选原子写入，失败无半成品。
- [ ] 重放、漂移、并发和 node 占用都有稳定结果。
