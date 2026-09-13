# Lineage Provenance Evidence

**Sprint**: 31A -> 31B -> 32 final IT 共用
**Owner**: Lineage platform owner
**触发命令**: Sprint-32 final IT 中执行 lineage sync、lineage failure report 和字段血缘 backfill dry-run
**预期输出**: DWD / DWS / ADS / BI / SCREEN 血缘可追溯，失败记录可被前端处置
**目前状态**: PENDING，待 Sprint-32 final IT 写入实际日志

## Linked Scope

- Sprint-31A F3: `worklog/v2.2.3/sprint-31a-202605/features/F3-lineage-provenance/README.md`
- Sprint-31B F4/T03: `worklog/v2.2.3/sprint-31b-202605/features/F4-sprint-31a-gap-and-status-rectification/T03-column-lineage-backfill-strategy.md`
- Sprint-31B F6/T04: `worklog/v2.2.3/sprint-31b-202605/features/F6-frontend-acceptance-recovery/T04-governance-gap-remediation-ui.md`

## Evidence To Capture

- lineage sync 成功记录和失败记录都能关联资产身份。
- 字段级血缘 backfill dry-run 输出影响范围、时间窗口和 rollback 策略。
- 前端能从 lineage failure report 跳转到资产详情 lineage tab。
