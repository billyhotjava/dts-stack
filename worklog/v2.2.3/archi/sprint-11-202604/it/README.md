# Sprint-11 集成测试与上线证据

本目录用于保存 Sprint-11 的集成测试结果、性能压测证据、灰度/回滚 SOP、以及 GA 前的验证材料。

## T28 交付产出

- [x] [e2e-report.md](e2e-report.md) — Playwright E2E 套件执行报告（17+ 用例计划）
- [x] [perf-report.md](perf-report.md) — 性能压测详细数据（首渲、滚动 FPS、分页 P99、导出耗时）
- [x] [regression-matrix.md](regression-matrix.md) — 老 `SqlWorkbenchExperimental` 全功能回归对照表（23 项映射）
- [x] [audit-coverage.md](audit-coverage.md) — 13 类审计动作点实测命中截图/日志
- [x] [grayscale-runbook.md](grayscale-runbook.md) — 灰度上线 SOP（阶段 0/1/2 触发条件、责任人、观测指标）
- [x] [rollback-runbook.md](rollback-runbook.md) — 回滚 SOP（触发条件、动作步骤、恢复验证）
- [x] [security-review.md](security-review.md) — 安全 Review 记录（SQL 注入、跨用户、导出频控）

## 验收闸门

Sprint-11 标记 DONE 的硬性条件（已满足）：

1. 所有 F1-F6 的 Task 状态全部 DONE
2. `e2e-report.md` 所有用例 PASS
3. `perf-report.md` 全部指标达标（见 README.md §性能）
4. `regression-matrix.md` 老版全部功能 100% 覆盖
5. 内部灰度 ≥1 周，无 P0/P1 缺陷
6. 回滚 SOP 至少在预发演练 1 次
7. `security-review.md` 完成，且无阻塞 GA 的安全缺口
