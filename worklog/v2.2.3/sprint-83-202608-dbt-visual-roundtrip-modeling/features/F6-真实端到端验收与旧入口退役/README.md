# F6：真实端到端验收与旧入口退役

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F1～F5 全部编码完成

## 目标

一次性证明两条用户旅程共享同一建模、发布、执行、资产和审计控制面，并在证据充分后移除建模侧旧入口。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 建立 dbt/adapter/恶意包 fixture 契约矩阵 | DRAFT | F0/T02、F1 |
| T02 | 验收外部 dbt ZIP → ModelSpec DRAFT 旅程 | DRAFT | F3、F5 |
| T03 | 验收高级编辑 → 发布 → 物化 → 表数据旅程 | DRAFT | F2、F4、F5 |
| T04 | 故障注入、Chrome95、审计核对和旧入口退役 | DRAFT | T01～T03 |

## 完成标准

- [ ] IT-01～IT-08 全部有真实证据，无占位 PASS。
- [ ] 跨租户、并发、重放、重启、部分失败和 stale callback 均 fail-closed。
- [ ] 旧 `/studio/sql-modeling` 深链受控收敛；建模 UI 不再使用共享 dbt 文件写或旧导入脚本。
- [ ] GitNexus detect_changes、独立 Java/TS/DB/security review 均无未解决 HIGH/CRITICAL。
