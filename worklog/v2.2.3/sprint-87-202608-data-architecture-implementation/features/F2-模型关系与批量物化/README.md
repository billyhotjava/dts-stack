# F2：模型关系与批量物化

**优先级**：P0

**状态**：CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E

**证据**：`../../assets/implementation-evidence-20260810.md`

## 目标

实现稳定业务上下文、依赖 DAG、候选原子创建、逐项物化状态和不覆盖历史的二次物化。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 实现模型关系、批量候选与 attempt 证据 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E | F0/T01（生产迁移与 E2E 仍阻塞） |

## Feature DoD

- [ ] ADR-86-13/14/15 契约、NFR 和 E2E-A 的真实切片通过。
- [ ] 模型 revision、candidate、attempt、physical observation 和资产状态可区分。
