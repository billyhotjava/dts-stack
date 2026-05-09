# Metric Visualization Patterns

Load this reference only when concrete metric, model, SQL, BI dataset, or dashboard output is needed.

## Formula JSON Patterns

Use JSON formulas when a metric must be configured visually and later compiled to SQL for PostgreSQL, Doris, DM8, Hive, Spark, or another engine.

### Sum

```json
{
  "type": "aggregation",
  "aggregation": "sum",
  "field": "contract_amount"
}
```

### Count Distinct

```json
{
  "type": "aggregation",
  "aggregation": "count_distinct",
  "field": "project_id"
}
```

### Count If

```json
{
  "type": "conditional_count",
  "field": "project_id",
  "condition": {
    "field": "project_status",
    "operator": "=",
    "value": "在研"
  },
  "distinct": true
}
```

### Ratio

```json
{
  "type": "ratio",
  "numerator": {
    "type": "aggregation",
    "aggregation": "sum",
    "field": "direct_cost_amount"
  },
  "denominator": {
    "type": "aggregation",
    "aggregation": "sum",
    "field": "direct_cost_control_amount"
  },
  "multiply": 100,
  "zero_division": "null"
}
```

### Case When

```json
{
  "type": "case_when",
  "cases": [
    {
      "when": {
        "field": "direct_cost_execution_rate",
        "operator": ">=",
        "value": 90
      },
      "then": "高风险"
    },
    {
      "when": {
        "field": "direct_cost_execution_rate",
        "operator": ">=",
        "value": 80
      },
      "then": "中风险"
    }
  ],
  "else": "正常"
}
```

## Default Metric Libraries

Use these only as starting points. Confirm field names, statuses, grain, and exclusions against the actual data.

### Project Management

| Metric | Formula |
| --- | --- |
| 项目总数 | `count(distinct project_id)` |
| 在研项目数 | `count(distinct case when project_status = '在研' then project_id end)` |
| 已完成项目数 | `count(distinct case when project_status = '已完成' then project_id end)` |
| 延期项目数 | `current_date > plan_end_date and project_status <> '已完成'` |
| 高风险项目数 | `risk_level = '高'` |
| 重大项目数 | `is_major_project = true` |
| 项目完成率 | 已完成项目数 / 项目总数 |
| 延期率 | 延期项目数 / 项目总数 |
| 总经费 | `sum(total_budget_amount)` |
| 已收款 | `sum(received_amount)` |
| 待收经费 | `sum(total_budget_amount - received_amount)` |
| 直接成本执行率 | 直接成本支出金额 / 直接成本控制数 |
| 风险关闭率 | 已关闭风险数 / 风险总数 |

### Procurement

| Metric | Formula |
| --- | --- |
| 采购订单数 | `count(distinct po_id)` |
| 采购金额 | `sum(po_amount)` |
| 到货数量 | `sum(receive_qty)` |
| 到货率 | 到货数量 / 采购数量 |
| 供应商数 | `count(distinct supplier_id)` |
| 逾期交付订单数 | 实际到货日期 > 计划到货日期 |
| 供应商准时交付率 | 准时交付订单数 / 总订单数 |

### Inventory

| Metric | Formula |
| --- | --- |
| 库存金额 | `sum(stock_amount)` |
| 库存数量 | `sum(stock_qty)` |
| 呆滞物料数 | 超过指定天数无出入库 |
| 库存周转率 | 出库金额 / 平均库存金额 |
| 安全库存预警数 | 当前库存 < 安全库存 |
| 超储物料数 | 当前库存 > 最大库存 |

### Quality

| Metric | Formula |
| --- | --- |
| 质量问题数 | `count(issue_id)` |
| 未关闭问题数 | `issue_status <> '已关闭'` |
| 高严重度问题数 | `severity = '高'` |
| 问题关闭率 | 已关闭问题数 / 问题总数 |
| 平均关闭时长 | `avg(close_time - create_time)` |
| 返工次数 | `count(rework_id)` |

## Visualization Component Rules

| Metric need | Recommended component |
| --- | --- |
| Total value | KPI card / big number |
| Ratio or progress | Progress bar / gauge / ring |
| Time trend | Line chart / area chart |
| Ranking | Horizontal bar chart |
| Composition | Donut / pie / stacked bar |
| Risk items | Warning list / table |
| Project schedule | Gantt chart |
| Geographic distribution | Map |
| Organization comparison | Matrix / heatmap |
| Detail trace | Data table with filters |

## Dashboard Layout Pattern

Use this layout for enterprise management dashboards:

```text
Top: title, domain selector, time range, org filter
Row 1: core KPI cards
Row 2: trend chart + ranking chart
Row 3: risk/warning list + composition chart
Row 4: detail table with drilldown/export
```

Expected components:

- `MetricCard`
- `MetricTrendChart`
- `MetricRankChart`
- `RiskWarningList`
- `MetricDataTable`
- `FilterBar`
- `DrilldownDrawer`

## DWS dbt Skeleton

```sql
{{ config(
    materialized='table',
    tags=['project', 'dws', 'metric']
) }}

with base as (

    select
        project_id,
        dept_id,
        dept_name,
        project_type,
        project_status,
        plan_start_date,
        plan_end_date,
        direct_cost_amount,
        direct_cost_control_amount
    from {{ ref('dwd_project_detail') }}
    where project_status <> '已取消'

),

agg as (

    select
        date_format(plan_start_date, '%Y-%m') as stat_month,
        dept_id,
        dept_name,
        project_type,
        count(distinct project_id) as project_cnt,
        count(distinct case when project_status = '在研' then project_id end) as active_project_cnt,
        count(distinct case
            when current_date > plan_end_date and project_status <> '已完成'
            then project_id
        end) as overdue_project_cnt,
        sum(coalesce(direct_cost_amount, 0)) as direct_cost_amount,
        sum(coalesce(direct_cost_control_amount, 0)) as direct_cost_control_amount
    from base
    group by
        date_format(plan_start_date, '%Y-%m'),
        dept_id,
        dept_name,
        project_type

)

select
    *,
    case
        when direct_cost_control_amount = 0 then null
        else direct_cost_amount / direct_cost_control_amount
    end as direct_cost_execution_rate
from agg
```

## schema.yml Skeleton

```yaml
version: 2

models:
  - name: dws_project_month_summary
    description: 项目管理月度公共汇总模型，用于沉淀项目数量、延期、经费执行等核心指标。
    columns:
      - name: stat_month
        description: 统计月份，格式 yyyy-MM
        tests:
          - not_null
      - name: dept_id
        description: 科室ID
      - name: dept_name
        description: 科室名称
      - name: project_cnt
        description: 项目总数
        tests:
          - not_null
      - name: direct_cost_execution_rate
        description: 直接成本执行率，直接成本支出金额 / 直接成本控制数
```

## BI Dataset Registration Pattern

```yaml
dataset_name: 项目管理驾驶舱数据集
physical_table: ads_project_dashboard_overview
description: 用于项目管理综合驾驶舱的 ADS 应用数据集
default_time_column: stat_month
dimensions:
  - stat_month
  - dept_name
  - project_type
  - project_status
metrics:
  - project_cnt
  - active_project_cnt
  - overdue_project_cnt
  - high_risk_project_cnt
  - direct_cost_execution_rate
charts:
  - name: 项目总览KPI
    type: big_number
    metrics:
      - project_cnt
      - active_project_cnt
      - overdue_project_cnt
  - name: 项目趋势分析
    type: line
    x_axis: stat_month
    y_axis:
      - project_cnt
      - overdue_project_cnt
  - name: 科室项目排名
    type: bar
    x_axis: dept_name
    y_axis: project_cnt
```

## Data Quality Checks

Always consider:

- Non-null checks for grain fields such as `stat_month`
- Uniqueness checks for the declared grain
- Range checks for rates and amounts
- Logic checks such as completed count <= total count
- Enum checks for status and risk fields
- Negative amount checks when negative values are not valid
- Time checks such as actual completion date not before start date

## Risk Checklist

Call out these risks in generated designs:

- Ambiguous metric口径
- Duplicate counting from one-to-many joins
- Missing or wrong time field
- Grain not unique
- Amount fields include/exclude tax inconsistently
- Canceled or voided records not filtered
- Zero denominator
- Null amount/count fields
- Cross-system master data inconsistency
- ADS table becoming a dumping ground instead of a page-oriented dataset
