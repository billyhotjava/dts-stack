# T01: Sprint-31A 状态口径修正

**优先级**: P0
**状态**: DONE
**依赖**: F1-F3 进入开发

## 目标

把 Sprint-31A 的状态口径从「README 标 DONE + RX 标 CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS」拆成可被外部读懂的统一口径：契约层 DONE，运行时层 PARTIAL（由 Sprint-31B 收口）。

## 背景

当前三处状态：
- `sprint-31a-202605/README.md` Feature 表里 RX 状态为 `CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS`
- `sprint-31a-202605/features/RX-architect-review-hardening/README.md` 自己也是同样口径
- `sprint-queue.md` 跟着写「CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS」

但 RX/T03 / T04 / T05 实际状态分散在 DONE / IN_PROGRESS，外部读者无法快速看到「整体到底卡在哪、还差什么」。

## 技术设计

1. 在 Sprint-31A README 增加 "运行时收口" 章节，明确指向 Sprint-31B 各 Feature。
2. 把 Feature 表的 RX 行拆成两列状态：
   ```
   | RX | 架构评审追补项 | P0 | 5 | CONTRACT: DONE / RUNTIME: PARTIAL (Sprint-31B) | F1-F6, Sprint-32 |
   ```
3. RX README 的「完成标准」checkbox 用两类符号：
   - `[x] (contract)` 已契约落地
   - `[ ] (runtime)` 待 Sprint-31B 闭环
4. `sprint-queue.md` 同步：把 Sprint-31A 状态拆为 `CONTRACT_DONE` + 「运行时收口见 Sprint-31B」并加 Sprint-31B 队列条目。
5. 新建 `sprint-31a-202605/assets/sprint31a-status-rectification-20260518.md` 记录这次口径修正的 rationale。

## 影响范围

- `worklog/v2.2.3/sprint-31a-202605/README.md`
- `worklog/v2.2.3/sprint-31a-202605/features/RX-architect-review-hardening/README.md`
- `worklog/v2.2.3/sprint-queue.md`
- 新增 `worklog/v2.2.3/sprint-31a-202605/assets/sprint31a-status-rectification-20260518.md`

## 验证

- [x] 三处状态口径一致（Sprint-31A README / RX README / sprint-queue 均使用 CONTRACT_DONE / RUNTIME_PARTIAL）。
- [x] 外部 reader 能从 Sprint-31A README 直接跳到 Sprint-31B。

## 完成标准

- [x] 状态口径修正完成。
- [x] rationale 文档归档。

## 实现记录

- Sprint-31A README 顶部状态改为 `CONTRACT_DONE / RUNTIME_PARTIAL（运行时收口见 Sprint-31B）`。
- RX README 增加状态口径说明，明确哪些属于当前版本契约完成、哪些由 Sprint-31B 收口。
- `sprint-queue.md` 同步 Sprint-31A 状态和 RX 行状态。
- Rationale: `worklog/v2.2.3/sprint-31a-202605/assets/sprint31a-status-rectification-20260518.md`。
