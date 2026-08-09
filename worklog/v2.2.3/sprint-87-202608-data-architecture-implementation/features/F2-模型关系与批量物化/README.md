# F2：模型关系与批量物化

**优先级**：P0

**状态**：BLOCKED

## 目标

实现稳定业务上下文、依赖 DAG、候选原子创建、逐项物化状态和不覆盖历史的二次物化。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 实现模型关系、批量候选与 attempt 证据 | BLOCKED | F0/T01、F1/T01 |

## Feature DoD

- [ ] ADR-86-13/14/15 契约、NFR 和 E2E-A 的真实切片通过。
- [ ] 模型 revision、candidate、attempt、physical observation 和资产状态可区分。
