# 查询卡片 SQL 说明

## 概述

本目录包含 23 个查询卡片 SQL 文件，供 GPMC（项目管控指挥中心）6 个大屏使用。每个 SQL 文件对应一个可复用的查询卡片，在平台的「BI 分析 → 查询卡片」中创建后，可被大屏组件引用。

## 使用方式

### 方式 A：直接使用大屏实例（推荐）

1. 在 `screen-instances/` 目录下的 6 个 JSON 文件已包含 SQL 查询
2. 全局替换 `{{DATABASE_ID}}` 为实际数据源 ID
3. 通过平台界面导入 JSON 文件

### 方式 B：手动创建查询卡片

1. 进入「BI 分析 → 查询卡片 → 新建」
2. 选择数据源（指向包含 dbt 产出表的 PostgreSQL 数据库）
3. 粘贴本目录下对应的 SQL 文件内容
4. 保存后获得 cardId，在大屏组件中绑定

## 数据源配置

查询 SQL 读取的表是 dbt 产出的数仓表（ADS/DWS/DWD 层），需要先完成以下步骤：

1. 在目标数据库中执行 `ods_create_tables.sql` 创建 ODS 表
2. 通过入湖任务导入 Excel 数据到 ODS 表
3. 运行 `dbt run` 构建全部数仓表
4. 在平台「数据接入中心」注册该数据库为数据源

## 查询卡片清单

### 执行域（8 个）— 大屏 1 综合态势 + 大屏 2 执行监控

| 文件名 | 数据表 | 用途 | 对应大屏 |
|--------|--------|------|----------|
| `card-execution-kpi-overview.sql` | biz_ads_project_kpi_overview | 整体完成率/准时率/超期率 | S1, S2 |
| `card-milestone-kpi.sql` | biz_ads_project_milestone_kpi | 里程碑完成/高风险统计 | S2 |
| `card-incomplete-risk.sql` | biz_ads_project_incomplete_risk | 未完成高风险/里程碑数 | S1, S2 |
| `card-non-general-kpi.sql` | biz_ads_project_non_general_kpi | 非一般节点异常率/超期率 | S2 |
| `card-major-project-overview.sql` | biz_ads_major_project_overview | 项目级健康度/进度概览 | S1, S2 |
| `card-project-tree-snapshot.sql` | biz_ads_major_project_tree_snapshot | 项目→子项目→节点树状结构 | S2 |
| `card-delay-reason-trend.sql` | biz_ads_delay_reason_trend | 按周延期原因分类趋势 | S1, S2 |
| `card-weekly-subproject-summary.sql` | biz_dws_week_subproject_summary | 子项目周度进度汇总 | S2 |

### 质量域（4 个）— 大屏 3 质量信息与跟进

| 文件名 | 数据表 | 用途 | 对应大屏 |
|--------|--------|------|----------|
| `card-quality-kpi.sql` | biz_ads_quality_kpi | 月度质量KPI（归零率/闭环率等） | S1, S3 |
| `card-quality-period-summary.sql` | biz_dws_quality_period_summary | 按项目/科室质量汇总 | S3 |
| `card-quality-issue-list.sql` | biz_dwd_quality_issue | 质量问题明细表 | S3 |
| `card-quality-measure-list.sql` | biz_dwd_quality_measure | 质量措施明细表 | S3 |

### 技术状态域（4 个）— 大屏 4 技术状态与跟进

| 文件名 | 数据表 | 用途 | 对应大屏 |
|--------|--------|------|----------|
| `card-tech-state-kpi.sql` | biz_ads_tech_state_kpi | 月度技术状态KPI（签署率等） | S1, S4 |
| `card-tech-state-period-summary.sql` | biz_dws_tech_state_period_summary | 按项目/科室技术状态汇总 | S4 |
| `card-tech-state-list.sql` | biz_dwd_tech_state | 技术状态变更明细表 | S4 |
| `card-tech-state-measure-list.sql` | biz_dwd_tech_state_measure | 技术状态措施明细表 | S4 |

### 成本域（3 个）— 大屏 5 成本与预算控制

| 文件名 | 数据表 | 用途 | 对应大屏 |
|--------|--------|------|----------|
| `card-cost-kpi.sql` | biz_ads_cost_kpi | 月度/年度成本KPI（执行率/偏差率） | S1, S5 |
| `card-cost-period-summary.sql` | biz_dws_cost_period_summary | 按项目/科室成本汇总 | S5 |
| `card-cost-detail-list.sql` | biz_dwd_cost_accounting | 成本核算明细表 | S5 |

### 风险域（4 个）— 大屏 6 风险与预警中心

| 文件名 | 数据表 | 用途 | 对应大屏 |
|--------|--------|------|----------|
| `card-risk-kpi.sql` | biz_ads_risk_kpi | 月度风险KPI（闭环率/高风险数等） | S1, S6 |
| `card-risk-period-summary.sql` | biz_dws_risk_period_summary | 按项目/科室风险汇总 | S6 |
| `card-risk-info-list.sql` | biz_dwd_risk_info | 风险明细表 | S6 |
| `card-risk-measure-list.sql` | biz_dwd_risk_measure | 风险措施明细表 | S6 |

## 大屏与查询卡片对应关系

```
大屏 1 — 综合态势总览（OverviewScreen）
  ├─ card-execution-kpi-overview    → KPI: 项目总数/进行中/延期/完成率
  ├─ card-cost-kpi                  → KPI: 年度预算/执行率
  ├─ card-risk-kpi                  → KPI: 高风险比例; 图表: 风险分布
  ├─ card-quality-kpi               → 图表: 质量问题分类/闭环趋势
  ├─ card-tech-state-kpi            → 图表: 技术状态变更
  ├─ card-major-project-overview    → 表格: 项目状态; 仪表盘: 健康度
  ├─ card-delay-reason-trend        → 图表: 延期TOP5
  └─ card-incomplete-risk           → KPI: 预警统计

大屏 2 — 项目执行监控（ExecutionBoard）
  ├─ card-execution-kpi-overview    → KPI: 完成率/准时率
  ├─ card-milestone-kpi             → KPI: 里程碑达成率
  ├─ card-project-tree-snapshot     → Gantt: 项目甘特图
  ├─ card-non-general-kpi           → 图表: 阶段分布/工作量
  ├─ card-delay-reason-trend        → 表格: 延期TOP10
  └─ card-incomplete-risk           → KPI: 阻塞链路

大屏 3 — 质量信息与跟进（QualityBoard）
  ├─ card-quality-kpi               → KPI: 新增/现存/归零率/闭环率
  ├─ card-quality-period-summary    → 图表: 项目排名/分类饼图
  ├─ card-quality-issue-list        → 表格: 质量问题明细
  └─ card-quality-measure-list      → 表格: 质量措施汇总

大屏 4 — 技术状态与跟进（TechStateBoard）
  ├─ card-tech-state-kpi            → KPI: 变更数/签署率/I类更改
  ├─ card-tech-state-period-summary → 图表: 分类饼图/签署状态
  ├─ card-tech-state-list           → 表格: 技术状态明细
  └─ card-tech-state-measure-list   → 表格: 措施行动

大屏 5 — 成本与预算控制（CostBoard）
  ├─ card-cost-kpi                  → KPI: 年度预算/执行率/偏差率
  ├─ card-cost-period-summary       → 图表: 月度趋势/部门排名
  └─ card-cost-detail-list          → 表格: 成本核算明细

大屏 6 — 风险与预警中心（RiskBoard）
  ├─ card-risk-kpi                  → KPI: 风险总数/高风险/闭环率
  ├─ card-risk-period-summary       → 图表: 风险矩阵/分类饼图
  ├─ card-risk-info-list            → 表格: 风险明细
  └─ card-risk-measure-list         → 表格: 措施汇总
```

## 数据层次说明

```
ADS (应用层)  ← KPI 数字卡、趋势图表优先使用
  ↑
DWS (汇总层)  ← 按项目/科室/周期的汇总图表使用
  ↑
DWD (明细层)  ← 明细表格使用
```
