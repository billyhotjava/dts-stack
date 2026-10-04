# PMS-004

## 标题

构建趋势分析和树状快照 ADS，支撑统一入口项目看板的专题视图。

## 范围

- `services/dts-dbt/models/dws/model/`
- `services/dts-dbt/models/ads/model/`
- `services/dts-dbt/deploy/project-cockpit/`

## 目标

- 输出项目总览趋势、延期归因趋势、重大项目树快照的专用结果集

## 交付

- `biz_dws_week_subproject_summary.sql`
- `biz_ads_major_project_overview.sql`
- `biz_ads_major_project_tree_snapshot.sql`
- `biz_ads_delay_reason_trend.sql`
- 校验 SQL

## 验收

- 总览趋势、风险趋势和树快照均能独立查询
- 聚合结果与基础节点表口径一致
- 校验 SQL 能发现明显错配

## 当前进度

- 状态：DONE

## 风险

- 若趋势粒度与树状快照粒度混用，容易导致前端解释不一致
