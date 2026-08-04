# F6：真实端到端验收与旧入口退役

**优先级**：P0
**状态**：TEST_MATRIX_PASS / E2E_SCRIPT_COMPLETE / E2E_INPUT_PENDING
**依赖**：按交付切片独立验收；F6 不再整体等待 F1～F5，也不参与首次产生 F0 认证证据

## 目标

按竖切片集中证明用户旅程共享同一建模、发布、执行、资产和审计控制面；安全/浏览器验收与旧入口物理退役分开，避免 P2 清理阻断 P0/P1 价值交付。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 固化编码后 dbt/adapter/恶意包回归矩阵 | P1 | PASS_CODE | F0/T02、对应实现切片；S3 回归另依赖 F0/T05 |
| T02 | 验收 artifact-rich dbt ZIP → ModelSpec DRAFT 旅程 | P0 | E2E_INPUT_PENDING | F3/T01～T04、F5/T01～T03 |
| T03 | 验收可视化选择 dbt 与高级实现的发布物化旅程 | P1 | E2E_INPUT_PENDING | F0/T05、F2/T03～T04、F4/T01～T04、F5/T02～T03 |
| T04 | 故障注入、Chrome95 与安全审计验收 | P1 | E2E_INPUT_PENDING | 对应切片、T01～T03 |
| T05 | 验收旧执行面与解析尾巴物理退役 | P2 | RUNTIME_E2E_PENDING | F5/T05、T04 |

## 完成标准

- [ ] 当前承诺切片对应 IT 子项有真实证据，无占位 PASS；P1/P2 未排期项保持 DRAFT，不冒充当前 Sprint DONE。
- [ ] 跨租户、并发、重放、重启、部分失败和 stale callback 均 fail-closed。
- [ ] P2/T05 完成后旧 `/studio/sql-modeling` 深链受控收敛，建模 UI 不再使用 `/api/etl/dbt/preview`、共享 dbt 文件写、旧导入脚本或旧按标签 Bash DAG。
- [ ] GitNexus detect_changes、独立 Java/TS/DB/security review 均无未解决 HIGH/CRITICAL。
