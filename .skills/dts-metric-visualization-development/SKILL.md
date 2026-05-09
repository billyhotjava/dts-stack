---
name: dts-metric-visualization-development
description: "Use when designing or implementing DTS metric visualization work: metric semantic modeling, DWD to DWS/ADS design, dbt model generation, ADS datasets, BI/Superset dataset registration, dashboard or big-screen pages, metric configuration UIs, lineage, metric口径, chart selection, and visualization development for project, procurement, inventory, quality, finance, HR, equipment, or production domains."
---

# DTS Metric Visualization Development

Use this skill to turn business metric needs into auditable DTS data products and visualization deliverables.

The target chain is:

```text
DWD detail model -> metric definition -> dimensions/grain/filters
-> DWS reusable summary model -> ADS application dataset
-> BI dataset/API -> dashboard/big screen/product page
```

## Workflow

1. Classify the request:
   - Metric system design
   - DWS public summary model design
   - ADS application dataset design
   - dbt SQL/schema generation
   - BI dataset or Superset registration
   - Dashboard/big-screen/front-end page design
   - Metric modeling product UI design
2. Identify the business domain, target users, page/application goal, and core business objects.
3. Inventory source models/tables and fields: primary key, foreign keys, time fields, org fields, status fields, amount fields, and people fields.
4. Define metrics before writing SQL: name, code, business meaning, formula, source fields, grain, dimensions, filters, unit, format, warning rules, owner, and drilldown behavior.
5. Decide the model layer:
   - Use DWS for reusable public summaries that can serve multiple scenes.
   - Use ADS for a specific page, dashboard, API, or business application.
6. Generate the smallest complete deliverable for the user request: model design, SQL/dbt, schema YAML, BI dataset config, API contract, page structure, or implementation task split.
7. Include validation and risk checks for duplicate counting, join expansion, ambiguous time fields, nulls, zero division, status exclusions, and grain uniqueness.

## Business Vocabulary

Prefer business-facing terms unless the user is asking for implementation details:

| Warehouse term | Business-facing term |
| --- | --- |
| DWD | 明细数据 |
| DWS | 公共汇总模型 |
| ADS | 应用数据集 / 看板数据集 |
| Metric | 指标 |
| Dimension | 分析维度 |
| Grain | 统计粒度 |
| Fact table | 业务明细表 |
| Wide table | 应用宽表 |
| Lineage | 指标来源 / 数据来源追溯 |

## Output Contracts

### Metric System Design

Return:

1. Business goal
2. Business objects
3. Metric groups
4. Core metrics with formulas
5. Analysis dimensions
6. Statistical grains
7. DWS model candidates
8. ADS dataset candidates
9. Visualization component suggestions
10. Data quality and implementation risks

### DWS Model Design

Return:

- Model name, purpose, and reuse scenes
- Statistical grain
- Source DWD/DWS models
- Dimensions and metrics
- Field design with Chinese names and data types
- dbt SQL when enough fields are known
- `schema.yml` tests and descriptions
- Quality checks

Use DWS when the model is reusable across multiple dashboards, reports, APIs, or subject areas.

### ADS Dataset Design

Return:

- Dataset name and target page/API
- Source DWS/DWD models
- Wide fields optimized for the page
- Chart-to-field mapping
- Refresh cycle and materialization recommendation
- API contract when the user is building an application page
- BI dataset registration config when the user mentions BI/Superset

Use ADS when the model directly serves a page, big screen, interface, or business application.

### Dashboard or Page Design

Return:

- Page goal and target role
- Information architecture
- KPI, trend, ranking, distribution, risk, and detail components
- Required ADS datasets or APIs
- Interaction rules: filters, drilldown, export, sorting, warning states
- Front-end implementation guidance consistent with DTS product style

For DTS operational pages, keep the UI dense, quiet, scannable, and workflow-oriented. Do not create marketing-style pages.

## Metric Definition Template

Every important metric should be documented with this shape:

```yaml
metric_code: direct_cost_execution_rate
metric_name: 直接成本执行率
business_domain: 项目管理
business_object: 项目
description: 衡量项目直接成本支出相对于直接成本控制数的执行程度
formula_text: 直接成本支出金额 / 直接成本控制数 * 100%
formula_type: ratio
source_models:
  - dwd_project_detail
source_fields:
  - direct_cost_amount
  - direct_cost_control_amount
default_grain:
  - stat_month
  - dept_id
dimensions:
  - stat_month
  - dept_name
  - project_type
filters:
  - project_status != '已取消'
unit: "%"
format: percent
warning_rules:
  - condition: value >= 0.9
    level: high
owner: 财务管理部门
refresh_cycle: daily
drilldown: true
```

## SQL And dbt Rules

- Do not generate aggregate SQL before the grain is explicit.
- Use `coalesce` for nullable amount/count inputs.
- Guard every ratio against zero division.
- Check whether status values such as 已取消, 作废, 删除 should be excluded.
- Prevent join expansion by validating join cardinality before aggregating.
- Put derived ratios in an outer query when the numerator and denominator are aggregates.
- Name fields consistently:
  - Count: `xxx_cnt`
  - Amount: `xxx_amount`
  - Rate: `xxx_rate`
  - Boolean flag: `is_xxx`
  - Level: `xxx_level`
  - Date/time grain: `stat_date`, `stat_month`, `stat_quarter`, `stat_year`
- Generate both `model.sql` and `schema.yml` when producing dbt deliverables.
- Treat generated SQL as reviewable output, not as automatically approved production logic.

## DTS Integration

When implementing in this repository:

1. Use `dts-dbt-modeling-governance` for dbt, DWS/ADS, lineage, and model publication details.
2. Use `dts-frontend-product-consistency` for product page, route, menu, dashboard, or analytics UI changes.
3. Use `dts-quality-gate` after code, SQL, dbt, config, or script changes.
4. Inspect existing platform modeling and analytics pages before adding new UI or API contracts.
5. Keep generated models aligned with DTS naming and publication behavior instead of creating isolated SQL files that the platform cannot discover.

## References

Load `references/metric-patterns.md` when the request needs:

- Formula JSON examples
- Default metric libraries for project/procurement/inventory/quality
- Chart recommendation rules
- BI dataset registration examples
- Data quality and risk checklist
- Example DWS/ADS/dbt output skeletons
