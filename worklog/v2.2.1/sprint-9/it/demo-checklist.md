# 项目看板系统演示清单

## 演示入口

- 首页快捷卡片 `项目看板`
- 左侧导航 `项目看板`
- 顶部快捷入口 `项目看板`
- 路由：`/analytics/project-cockpit`

## 推荐演示步骤

1. 先展示统一筛选栏，说明所有主题共用一个上下文，不需要反复返回列表页
2. 在 `总览趋势` 展示重大项目数、节点完成率、高风险节点和重点预警
3. 在 `计划执行` 展示项目甘特板、里程碑、近期节点和责任科室负载
4. 在 `风险归因` 展示延期原因结构、延期趋势和责任科室矩阵
5. 在 `重大项目树` 演示 `重大项目 -> 子项目 -> 节点` 穿透和右侧详情面板
6. 在 `口径支撑` 展示覆盖率、指标口径、测试数据来源和待客户补充清单

## 当前演示依赖

- `worklog/v2.2.1/sprint-9/it/project-cockpit-test-batch-2000.xlsx`
- `worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py`
- `services/dts-dbt/seeds/pm_dim_major_project_seed.csv`
- `services/dts-dbt/seeds/pm_dim_subproject_seed.csv`
- `services/dts-dbt/seeds/pm_dim_delay_reason_seed.csv`
- `services/dts-dbt/seeds/pm_map_node_subject_seed.csv`

## 当前运行时说明

- `project-cockpit/*.tsv` 仅保留为开发/回归夹具
- 正式链路目标是 `Excel/CSV -> ODS -> dbt -> ADS -> 项目看板`
- `口径支撑` 应优先说明当前批次、质量覆盖、主数据接口占位和待补数仓维表，而不是继续强调 TSV 演示文件

## 待客户补充清单

- 正式的重大项目 / 子项目映射维表
- 延期原因标准枚举与归口规则
- 实际进度比例或阶段完成率口径
- 资源投入、工时、人员负载类数据
- 主数据系统建设计划、接口范围与后续切换时点
