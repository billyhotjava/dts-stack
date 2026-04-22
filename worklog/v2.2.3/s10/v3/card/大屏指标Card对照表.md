# v2 大屏 × 指标 × Card 对照表

## 大屏层级

```
S1  综合态势总览        (高层)
S2  项目执行监控        (中层)
S3  质量信息与跟进      (中层)
S4  技术状态与跟进      (中层)
S5  风险与预警中心      (中层)
S6  成本与预算控制      (中层·预留)
D1  下钻-执行详情
D2  下钻-质量明细
D3  下钻-技术状态明细
D4  下钻-风险明细
D5  下钻-物料明细       (暂缓)
```

---

## S1: 综合态势总览 (gpmc-strategic-overview)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 项目节点总数 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #1 | Tier1 |
| 节点完成率 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #9 | Tier1 |
| 延期节点数 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #5 | Tier1 |
| 按时完成率 | number-card | card-progress-derived | biz_ads_progress_derived_v2 | 二次#1 | Tier2 |
| 健康度仪表盘 | gauge | card-composite-health | biz_ads_composite_derived_v2 | 二次#17 | Tier2 |
| 风险预警指数 | gauge | card-composite-health | biz_ads_composite_derived_v2 | 二次#18 | Tier2 |
| 质量问题分类 | pie-chart | card-quality-category | biz_ads_quality_kpi_v2 | #41-48 | Tier1 |
| 技术状态变更 | bar-chart | card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #49-51 | Tier1 |
| 风险分布 | bar-chart | card-risk-kpi | biz_ads_risk_kpi_v2 | 风险高/中/低 | Tier1 |
| 三域健康雷达 | radar | card-composite-health | biz_ads_composite_derived_v2 | 二次#6,11,16 | Tier2 |

---

## S2: 项目执行监控 (gpmc-execution-board)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 整体完成率 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #9 | Tier1 |
| 里程碑达成率 | number-card | card-progress-milestone | biz_ads_progress_kpi_v2 | #26 | Tier1 |
| 延期任务数 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #5 | Tier1 |
| 按时完成率 | number-card | card-progress-derived | biz_ads_progress_derived_v2 | 二次#1 | Tier2 |
| 超期率 | number-card | card-progress-derived | biz_ads_progress_derived_v2 | 二次#2 | Tier2 |
| 异常率 | number-card | card-progress-derived | biz_ads_progress_derived_v2 | 二次#3 | Tier2 |
| 进度健康度 | gauge | card-progress-derived | biz_ads_progress_derived_v2 | 二次#6 | Tier2 |
| 阶段分布 | bar-chart | card-progress-non-general | biz_ads_progress_kpi_v2 | #12-15 | Tier1 |
| 风险分类 | bar-chart | card-progress-incomplete-risk | biz_ads_progress_kpi_v2 | #18-22 | Tier1 |
| 里程碑统计 | bar-chart | card-progress-milestone | biz_ads_progress_kpi_v2 | #23-25,29-31 | Tier1 |
| 延期TOP10 | table | card-progress-node-list | biz_dwd_project_node_v2 | DWD明细 | 明细 |

---

## S3: 质量信息与跟进 (gpmc-quality-board)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 新增质量问题 | number-card | card-quality-kpi | biz_ads_quality_kpi_v2 | #34 | Tier1 |
| 现存质量问题 | number-card | card-quality-kpi | biz_ads_quality_kpi_v2 | #35 | Tier1 |
| 归零率 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#7 | Tier2 |
| 计划提交率 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#9 | Tier2 |
| 质量健康度 | gauge | card-quality-derived | biz_ads_quality_derived_v2 | 二次#11 | Tier2 |
| 问题分类饼图 | pie-chart | card-quality-category | biz_ads_quality_kpi_v2 | #41-48 | Tier1 |
| 归零类型分布 | bar-chart | card-quality-kpi | biz_ads_quality_kpi_v2 | #36-39 | Tier1 |
| 问题清单 | table | card-quality-issue-list | biz_dwd_quality_issue_v2 | DWD明细 | 明细 |

---

## S4: 技术状态与跟进 (gpmc-tech-state-board)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 变更总数 | number-card | card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #49+50+51 | Tier1 |
| 签署完成率 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#12 | Tier2 |
| 整改完成率 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#13 | Tier2 |
| I类占比 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#14 | Tier2 |
| 技术状态健康度 | gauge | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#16 | Tier2 |
| 更改类别分布 | pie-chart | card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #49-51 | Tier1 |
| 签署状态分布 | bar-chart | card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #52-59 | Tier1 |
| 变更清单 | table | card-tech-state-list | biz_dwd_tech_state_v2 | DWD明细 | 明细 |

---

## S5: 风险与预警中心 (gpmc-risk-board)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 风险总数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 风险总数 | Tier1 |
| 高风险数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 高风险 | Tier1 |
| 中/低风险数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 中/低风险 | Tier1 |
| 释放率 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 释放率 | Tier1 |
| 未释放数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 未释放 | Tier1 |
| 风险等级分布 | bar-chart | card-risk-kpi | biz_ads_risk_kpi_v2 | 高/中/低 | Tier1 |
| 风险清单 | table | card-risk-info-list | biz_dwd_risk_info_v2 | DWD明细 | 明细 |

---

## S6: 成本与预算控制 (预留)

> 暂无财务数据，预留位置。

---

## D1: 下钻-执行详情 (gpmc-drill-execution)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 整体完成率 | number-card | card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #9 | Tier1 |
| 里程碑达成率 | number-card | card-progress-milestone | biz_ads_progress_kpi_v2 | #26 | Tier1 |
| 高风险未完成 | number-card | card-progress-incomplete-risk | biz_ads_progress_kpi_v2 | #18 | Tier1 |
| 风险预警指数 | number-card | card-composite-health | biz_ads_composite_derived_v2 | 二次#18 | Tier2 |
| 任务明细表 | table | card-progress-node-list | biz_dwd_project_node_v2 | DWD明细 | 明细 |
| 延期项目排行 | table | card-progress-node-list | biz_dwd_project_node_v2 | DWD明细 | 明细 |

---

## D2: 下钻-质量明细 (gpmc-drill-quality)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 归零率 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#7 | Tier2 |
| 现存率 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#8 | Tier2 |
| 计划提交率 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#9 | Tier2 |
| 质量健康度 | number-card | card-quality-derived | biz_ads_quality_derived_v2 | 二次#11 | Tier2 |
| 质量问题清单 | table | card-quality-issue-list | biz_dwd_quality_issue_v2 | DWD明细 | 明细 |
| 闭环摘要 | table | card-quality-kpi | biz_ads_quality_kpi_v2 | #36-39 | Tier1 |

---

## D3: 下钻-技术状态明细 (gpmc-drill-tech-state)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 签署完成率 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#12 | Tier2 |
| 整改完成率 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#13 | Tier2 |
| 未签署率 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#15 | Tier2 |
| 技术健康度 | number-card | card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#16 | Tier2 |
| 变更清单 | table | card-tech-state-list | biz_dwd_tech_state_v2 | DWD明细 | 明细 |
| 签署状态分布 | table | card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #52-59 | Tier1 |

---

## D4: 下钻-风险明细 (gpmc-drill-risk)

| 组件名称 | 组件类型 | Card 查询 | v2 数据表 | 指标编号 | 指标层级 |
|---------|---------|----------|----------|---------|---------|
| 风险总数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 风险总数 | Tier1 |
| 高风险数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 高风险 | Tier1 |
| 释放率 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 释放率 | Tier1 |
| 未释放数 | number-card | card-risk-kpi | biz_ads_risk_kpi_v2 | 未释放 | Tier1 |
| 风险清单 | table | card-risk-info-list | biz_dwd_risk_info_v2 | DWD明细 | 明细 |
| 风险分类摘要 | table | card-risk-kpi | biz_ads_risk_kpi_v2 | 高/中/低 | Tier1 |

---

## D5: 下钻-物料明细 (暂缓)

> 物料域 v2 模型未建，暂缓。

---

## Card 查询汇总 (v2)

### 原始指标查询 (原始指标查询.sql)

| Card 名称 | 查询数据表 | 覆盖指标 | 使用大屏 |
|-----------|----------|---------|---------|
| card-progress-kpi-overview | biz_ads_progress_kpi_v2 | #1-11 | S1,S2,D1 |
| card-progress-non-general | biz_ads_progress_kpi_v2 | #12-17 | S2 |
| card-progress-incomplete-risk | biz_ads_progress_kpi_v2 | #18-22 | S2,D1 |
| card-progress-milestone | biz_ads_progress_kpi_v2 | #23-33 | S2,D1 |
| card-quality-kpi | biz_ads_quality_kpi_v2 | #34-40 | S1,S3,D2 |
| card-quality-category | biz_ads_quality_kpi_v2 | #41-48 | S1,S3 |
| card-quality-issue-list | biz_dwd_quality_issue_v2 | DWD明细 | S3,D2 |
| card-tech-state-kpi | biz_ads_tech_state_kpi_v2 | #49-67 | S1,S4,D3 |
| card-tech-state-list | biz_dwd_tech_state_v2 | DWD明细 | S4,D3 |
| card-risk-kpi | biz_ads_risk_kpi_v2 | 风险KPI | S1,S5,D4 |
| card-risk-info-list | biz_dwd_risk_info_v2 | DWD明细 | S5,D4 |
| card-progress-node-list | biz_dwd_project_node_v2 | DWD明细 | S2,D1 |

### 二次指标查询 (二次指标查询.sql)

| Card 名称 | 查询数据表 | 覆盖指标 | 使用大屏 |
|-----------|----------|---------|---------|
| card-progress-derived | biz_ads_progress_derived_v2 | 二次#1-6 | S1,S2 |
| card-quality-derived | biz_ads_quality_derived_v2 | 二次#7-11 | S1,S3,D2 |
| card-tech-state-derived | biz_ads_tech_state_derived_v2 | 二次#12-16 | S1,S4,D3 |
| card-composite-health | biz_ads_composite_derived_v2 | 二次#17-18 | S1,D1 |

### 统计

| 类别 | Card 数量 | 覆盖指标数 |
|------|----------|----------|
| 原始指标 Card | 12 | 67 + 风险KPI + 4个DWD明细 |
| 二次指标 Card | 4 | 18 |
| **合计** | **16** | **85 + 风险 + DWD** |
