# F4: OpenMetadata 采集作业运维化

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

让 OpenMetadata ingestion 容器和脚本从一次性“可能跳过”变成可重复执行、可观测、可定位失败的运维能力。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | dbt/OpenMetadata Ingestion 脚本修复 | P0 | READY |
| T02 | Airflow Managed APIs 与调度检查 | P1 | READY |
| T03 | 采集日志、重试与退出码规范 | P0 | READY |
| T04 | 现场冒烟 Runbook | P1 | READY |

## 完成标准

- [ ] token 为空时不会在不知情的情况下跳过采集。
- [ ] no-auth 模式必须显式开启并被日志记录。
- [ ] ingestion 脚本成功、跳过、失败有不同退出码或状态说明。
- [ ] 运维人员能按 runbook 复现采集、查看日志并判断结果。
