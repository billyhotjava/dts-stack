# DTS v2.2.3 Sprint Queue

新 Sprint 使用 `sprint-{编号}-{YYYYMM}-{简短英文功能说明}` 目录命名；Feature 与 Task 使用中文业务名称。状态仅允许 `READY`、`IN_PROGRESS`、`DONE`、`BLOCKED`。

## Sprint-66: BI 大屏通用下钻重构 (202607)

**目录**: `sprint-66-202607-bi-board-drilldown-refactoring`
**状态**: DONE
**目标**: 在不引入业务领域模型的前提下，将大屏下钻收敛为“点击事件 → 参数映射 → 目标动作 → 状态恢复”的通用交互通道。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-通用交互契约 | 2 | DONE |
| F2-运行时交互内核 | 3 | DONE |
| F3-设计器配置体验 | 3 | DONE |
| F4-兼容回归与交付 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=11, BLOCKED=0
**执行顺序**: F1 → F2 → F3 → F4；F3 可在 F1 契约评审完成后与 F2 后半段并行，但 F4 必须等待 F1-F3 全部通过。
