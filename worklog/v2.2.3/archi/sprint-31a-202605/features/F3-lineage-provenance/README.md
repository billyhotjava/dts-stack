# F3: 血缘和来源证明收敛

**优先级**: P0
**状态**: DONE

## 目标

把 OpenLineage、dbt manifest、Addax/Airflow 运行血缘和手工声明血缘收敛成可追溯、可治理、可失败告警的资产事实。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | OpenLineage 资产解析增强 | P0 | DONE | F1 |
| T02 | dbt manifest 资产同步证据 | P0 | DONE | F1, F2 |
| T03 | Addax/Airflow 来源证明 | P0 | DONE | F1 |
| T04 | 字段级血缘和时间窗口 | P1 | DONE | T01-T03 |
| T05 | 血缘失败告警和阻断报告 | P0 | DONE | T01-T04 |

## 完成标准

- [x] 血缘写入失败不再只是旁路日志。
- [x] 自动创建资产带来源证明和治理状态。
- [x] 资产详情能说明上游、下游、来源和最近观测时间。
