# 查询卡片说明

## 查询卡片 → 大屏组件对照表

### 大屏 S1：综合态势总览（gpmc-strategic-overview）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 项目总数 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 进行中项目 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 延期项目 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 平均进度达成率 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 项目状态表 | table | card-major-project-overview | biz_ads_major_project_overview |
| 健康度仪表盘 | gauge | card-major-project-overview | biz_ads_major_project_overview |
| 延期TOP5 | bar-chart | card-delay-reason-trend | biz_ads_delay_reason_trend |
| 风险分布 | bar-chart | card-risk-kpi | biz_ads_risk_kpi |
| 质量问题分类 | pie-chart | card-quality-kpi | biz_ads_quality_kpi |
| 技术状态变更 | bar-chart | card-tech-state-kpi | biz_ads_tech_state_kpi |

### 大屏 S2：项目执行监控（gpmc-execution-board）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 整体完成率 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 里程碑达成率 | number-card | card-milestone-kpi | biz_ads_project_milestone_kpi |
| 延期任务数 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 最大延期天数 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 甘特图 | gantt-chart | card-project-tree-snapshot | biz_ads_major_project_tree_snapshot |
| 延期TOP10 | table | card-delay-reason-trend | biz_ads_delay_reason_trend |
| 阶段分布 | bar-chart | card-non-general-kpi | biz_ads_project_non_general_kpi |

### 大屏 S3：质量信息与跟进（gpmc-quality-board）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 新增质量问题 | number-card | card-quality-kpi | biz_ads_quality_kpi |
| 现存质量问题 | number-card | card-quality-kpi | biz_ads_quality_kpi |
| 归零完成率 | number-card | card-quality-kpi | biz_ads_quality_kpi |
| 问题分类饼图 | pie-chart | card-quality-kpi | biz_ads_quality_kpi |
| 项目排名 | bar-chart | card-quality-period-summary | biz_dws_quality_period_summary |
| 问题清单 | table | card-quality-issue-list | biz_dwd_quality_issue |
| 措施汇总 | table | card-quality-measure-list | biz_dwd_quality_measure |

### 大屏 S4：技术状态与跟进（gpmc-tech-state-board）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 变更数 | number-card | card-tech-state-kpi | biz_ads_tech_state_kpi |
| 签署完成率 | number-card | card-tech-state-kpi | biz_ads_tech_state_kpi |
| 分类饼图 | pie-chart | card-tech-state-kpi | biz_ads_tech_state_kpi |
| 变更清单 | table | card-tech-state-list | biz_dwd_tech_state |
| 措施表 | table | card-tech-state-measure-list | biz_dwd_tech_state_measure |

### 大屏 S5：风险与预警（gpmc-risk-board）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 风险总数 | number-card | card-risk-kpi | biz_ads_risk_kpi |
| 高/中/低风险 | number-card | card-risk-kpi | biz_ads_risk_kpi |
| 风险矩阵 | bar-chart | card-risk-period-summary | biz_dws_risk_period_summary |
| 风险清单 | table | card-risk-info-list | biz_dwd_risk_info |
| 措施表 | table | card-risk-measure-list | biz_dwd_risk_measure |

### 下钻-执行详情（gpmc-drill-execution）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| 整体完成率 | number-card | card-execution-kpi-overview | biz_ads_project_kpi_overview |
| 里程碑达成率 | number-card | card-milestone-kpi | biz_ads_project_milestone_kpi |
| 关键任务明细 | table | card-project-tree-snapshot | biz_ads_major_project_tree_snapshot |
| 资源负载摘要 | table | card-weekly-subproject-summary | biz_dws_week_subproject_summary |
| 延期项目排行 | table | card-delay-reason-trend | biz_ads_delay_reason_trend |

### 下钻-质量（gpmc-drill-quality）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| KPI 卡片 (4个) | number-card | card-quality-kpi | biz_ads_quality_kpi |
| 质量问题清单 | table | card-quality-issue-list | biz_dwd_quality_issue |
| 质量闭环摘要 | table | card-quality-kpi | biz_ads_quality_kpi |
| 跟进措施摘要 | table | card-quality-measure-list | biz_dwd_quality_measure |

### 下钻-技术状态（gpmc-drill-tech-state）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| KPI 卡片 (4个) | number-card | card-tech-state-kpi | biz_ads_tech_state_kpi |
| 变更清单 | table | card-tech-state-list | biz_dwd_tech_state |
| 状态分布摘要 | table | card-tech-state-kpi | biz_ads_tech_state_kpi |
| 措施摘要 | table | card-tech-state-measure-list | biz_dwd_tech_state_measure |

### 下钻-风险（gpmc-drill-risk）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| KPI 卡片 (4个) | number-card | card-risk-kpi | biz_ads_risk_kpi |
| 风险清单 | table | card-risk-info-list | biz_dwd_risk_info |
| 风险分类摘要 | table | card-risk-period-summary | biz_dws_risk_period_summary |
| 措施摘要 | table | card-risk-measure-list | biz_dwd_risk_measure |

### 下钻-物料（gpmc-drill-material）

| 组件名称 | 组件类型 | 查询卡片 | 数据表 |
|---------|---------|---------|--------|
| KPI 卡片 (4个) | number-card | card-material-kpi | biz_ads_material_kpi |
| 物料清单 | table | card-material-detail-list | biz_dwd_material_info |
| 供应链摘要 | table | card-material-kpi | biz_ads_material_kpi |
| 交期跟踪 | table | card-material-period-summary | biz_dws_material_period_summary |

## 查询卡片完整清单（25个）

| 序号 | 卡片文件 | 数据表 | 所属域 |
|:---:|---------|--------|--------|
| 1 | card-execution-kpi-overview | biz_ads_project_kpi_overview | 执行域 |
| 2 | card-milestone-kpi | biz_ads_project_milestone_kpi | 执行域 |
| 3 | card-incomplete-risk | biz_ads_project_incomplete_risk | 执行域 |
| 4 | card-non-general-kpi | biz_ads_project_non_general_kpi | 执行域 |
| 5 | card-major-project-overview | biz_ads_major_project_overview | 执行域 |
| 6 | card-project-tree-snapshot | biz_ads_major_project_tree_snapshot | 执行域 |
| 7 | card-delay-reason-trend | biz_ads_delay_reason_trend | 执行域 |
| 8 | card-weekly-subproject-summary | biz_dws_week_subproject_summary | 执行域 |
| 9 | card-quality-kpi | biz_ads_quality_kpi | 质量域 |
| 10 | card-quality-issue-list | biz_dwd_quality_issue | 质量域 |
| 11 | card-quality-measure-list | biz_dwd_quality_measure | 质量域 |
| 12 | card-quality-period-summary | biz_dws_quality_period_summary | 质量域 |
| 13 | card-tech-state-kpi | biz_ads_tech_state_kpi | 技术状态域 |
| 14 | card-tech-state-list | biz_dwd_tech_state | 技术状态域 |
| 15 | card-tech-state-measure-list | biz_dwd_tech_state_measure | 技术状态域 |
| 16 | card-tech-state-period-summary | biz_dws_tech_state_period_summary | 技术状态域 |
| 17 | card-risk-kpi | biz_ads_risk_kpi | 风险域 |
| 18 | card-risk-info-list | biz_dwd_risk_info | 风险域 |
| 19 | card-risk-measure-list | biz_dwd_risk_measure | 风险域 |
| 20 | card-risk-period-summary | biz_dws_risk_period_summary | 风险域 |
| 21 | card-material-kpi | biz_ads_material_kpi | 物料域 |
| 22 | card-material-detail-list | biz_dwd_material_info | 物料域 |
| 23 | card-material-period-summary | biz_dws_material_period_summary | 物料域 |
| 24 | card-progress-measure-summary | biz_dws_progress_measure_summary | 进度跟进域 |
| 25 | card-progress-measure-list | biz_dwd_progress_measure | 进度跟进域 |
