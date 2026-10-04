# PMS-002

## 标题

落地映射 seeds、维表与项目语义层，把源节点稳定挂到重大项目和子项目语义上。

## 范围

- `services/dts-dbt/seeds/`
- `services/dts-dbt/models/dwd/model/`
- `services/dts-dbt/tests/`

## 目标

- 让现有项目节点数据具备重大项目、子项目语义
- 为总览趋势、树状进度看板和甘特分析提供统一数据底座

## 交付

- `pm_dim_major_project.csv`
- `pm_dim_subproject.csv`
- `pm_map_node_subject.csv`
- `pm_dim_delay_reason.csv`
- `biz_dwd_project_node_enriched.sql`

## 验收

- `dbt seed` 成功
- `biz_dwd_project_node_enriched` 能成功构建
- hierarchy join 不出现大面积空值

## 当前进度

- 状态：DONE

## 风险

- 若 `project_no + subsystem + node_task` 的映射策略不够稳定，后续需要补更多规范化字段
