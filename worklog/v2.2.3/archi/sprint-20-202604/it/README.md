# Sprint-20 IT — 集成测试与验收材料

本目录承载 Sprint-20 端到端集成验证的所有产物。

## 文件清单（执行后填充）

| 文件 | 说明 | 责任 Task |
|------|------|----------|
| `end-to-end-smoke.md` | 端到端冒烟实测，含 4 步 + 截图 | F7-T01 |
| `performance-report.md` | 性能压测报告 | F7-T02 |
| `runbook.md` | 上线 / 回滚 / 故障排查手册 | F7-T03 |
| `lineage-backfill-runbook.md` | 历史数据回填操作手册 | F1-T04 |
| `screenshots/` | 验收截图集合 | F7-T01 |

## 验收闸门

Sprint-20 标记 DONE 前必须满足：

1. `end-to-end-smoke.md` 4 个 Step 全部 ✅
2. `performance-report.md` 80% 场景达标
3. `runbook.md` 已被独立工程师演练
4. 至少 2 个真实用户角色（数据工程师 / 业务分析师）走查通过
5. 所有 P0 任务（F1、F2、F5、F7）`DONE`
6. 所有 Liquibase 迁移在 staging 跑过且回滚验证通过

## 已知不在本 Sprint 的工作

- Inceptor 原生方言（LATERAL VIEW / MERGE INTO）的列级解析 → v2.4.0
- 跨租户 lineage 共享 → 待 IAM 重构后
- Spark / Flink 任务 OpenLineage 集成 → v2.4.0
