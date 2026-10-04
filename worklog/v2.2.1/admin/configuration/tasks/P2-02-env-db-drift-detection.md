# P2-02 `.env` 与 DB 配置漂移检测

`status`: `done`  
`priority`: `P2`

## 目标

发现并治理“`.env` 值”和“Admin 可视化值”长期不一致的问题。

## 范围

- 配置来源优先级策略
- 漂移检测脚本与报表
- 冲突处理策略

## 子任务

1. 定义优先级：`DB override` 或 `.env override`（按分类）。
2. 编写定时检测脚本输出冲突列表。
3. Admin 增加冲突面板与一键对齐动作。
4. 对齐动作写入审计。

## 验收标准

- 可按日输出冲突清单。
- 冲突可在页面完成确认并对齐。

## 风险与回滚

- 风险：自动对齐覆盖了预期值。
- 回滚：对齐前自动快照，支持逐项恢复。

## 实现进展（2026-02-22）

- 新增漂移检测脚本：`worklog/v2.2.1/admin/configuration/scripts/check-config-drift.sh`
- 新增运行手册：`worklog/v2.2.1/admin/configuration/report/p2-02-drift-detection-runbook.md`
- 输出产物：`worklog/v2.2.1/admin/configuration/raw/config-drift-report.tsv`
