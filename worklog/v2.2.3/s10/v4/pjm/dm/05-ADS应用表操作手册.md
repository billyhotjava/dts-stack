# ADS 应用表操作手册

## 1. ADS 规则

ADS 是看板、接口和指标消费的输出契约。当前 PJM 有 10 张 ADS：5 张基础 KPI、4 张域内派生指标、1 张综合派生指标。

创建 ADS 时选择“应用表”，目标分层为 ADS，不选择业务过程。每张表必须绑定固定修订的 DWS/ADS 上游，并声明输出粒度和字段契约。

## 2. 通用创建步骤

1. 确认上游 DWS 或 ADS 的当前修订已经通过设计检查。
2. 在模型工作台选择“新建 → 应用表”。
3. 选择该输出的主责数据域；跨域综合表以研究项目域作为消费主责域，并在业务定义中说明跨域依赖。
4. 选择 ADS 分层，填写模型名称、物理表名、业务定义和粒度。
5. 录入 KEY/TIME/MEASURE/ATTRIBUTE 字段及中文显示名。
6. 通过 dbt 逆向导入建立 revision-pinned `dependsOn`。
7. 保存后检查 DESIGNED；再完成构建、测试、质量、权限、密级和发布门禁。

当前 10 张 ADS 均为 `table` + 全量加载 + 无分区，按现状登记。

## 3. ADS 模型总表

| 物理表名 | 数据域 | 粒度/KEY | 固定上游 | 输出内容 |
|---|---|---|---|---|
| `biz_ads_progress_kpi_v2` | 研究项目域 | `plan_year + plan_month` | `biz_dws_progress_monthly_v2` | 进度基础计数与比率 |
| `biz_ads_progress_derived_v2` | 研究项目域 | `plan_year + plan_month` | progress KPI | 6 个进度派生指标和预警 |
| `biz_ads_quality_kpi_v2` | 质量管理域 | `period_year + period_month` | `biz_dws_quality_monthly_v2` | 质量基础计数 |
| `biz_ads_quality_derived_v2` | 质量管理域 | `period_year + period_month` | quality KPI | 5 个质量派生指标和预警 |
| `biz_ads_tech_state_kpi_v2` | 产品技术域 | `period_year + period_month` | `biz_dws_tech_state_monthly_v2` | 技术状态基础计数 |
| `biz_ads_tech_state_derived_v2` | 产品技术域 | `period_year + period_month` | tech-state KPI | 5 个技术状态派生指标和预警 |
| `biz_ads_risk_kpi_v2` | 研究项目域 | `period_year + period_month` | `biz_dws_risk_monthly_v2` | 风险总量、等级、释放率 |
| `biz_ads_budget_kpi_v2` | 财务管理域 | `snapshot_scope + snapshot_date` | `biz_dws_budget_v2` | 按业务快照日的预算执行总量 |
| `biz_ads_budget_derived_v2` | 财务管理域 | `snapshot_scope + snapshot_date` | budget KPI | 按业务快照日的 5 个预算派生指标和预警 |
| `biz_ads_composite_derived_v2` | 研究项目域 | `period_year + period_month` | progress KPI/derived、quality derived、tech derived | 综合健康与风险预警 |

## 4. 项目进度 ADS

### 4.1 `biz_ads_progress_kpi_v2`

- 模型名称：项目进度基础指标。
- 业务定义：跨项目汇总每月节点数量、完成、超期、风险和里程碑指标，保留分子/分母。
- KEY/TIME：`plan_year`、`plan_month`。
- MEASURE：DWS 的全部计数字段，以及 `completion_rate`、`on_time_rate`、`overdue_completion_rate`、`abnormal_rate_numerator`、`overdue_rate_numerator`、`abnormal_rate`、`overdue_rate`、`milestone_completion_rate`、`milestone_on_time_rate`、`milestone_overdue_rate`。
- 比率字段当前为 0～1 小数；中文显示名或指标元数据必须明确单位为“比例”，不能与 derived 表的 0～100 百分比混用。

### 4.2 `biz_ads_progress_derived_v2`

- 模型名称：项目进度派生指标。
- KEY/TIME：`plan_year`、`plan_month`。
- MEASURE：`pjm_prog_on_time_rate`、`pjm_prog_overdue_rate`、`pjm_prog_abnormal_rate`、`pjm_prog_milestone_on_time_rate`、`pjm_prog_high_risk_incomplete_ratio`、`pjm_prog_health_score`。
- ATTRIBUTE：`warn_on_time_rate`、`warn_overdue_rate`、`warn_milestone_rate`。
- 派生率为 0～100 百分比；阈值分别来自 SQL 注释，发布前由业务负责人确认，不因示例直接冻结为企业标准。

## 5. 质量 ADS

### 5.1 `biz_ads_quality_kpi_v2`

- 模型名称：质量问题基础指标。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`new_issue_cnt`、`open_issue_cnt`、`tech_zero_cnt`、`mgmt_zero_cnt`、`both_zero_cnt`、`zero_completed_cnt`、`no_zero_plan_cnt`、`open_cat_design`、`open_cat_process`、`open_cat_management`、`open_cat_component`、`open_cat_operation`、`open_cat_outsource`、`open_cat_software`、`open_cat_other`。

DWS 有 `open_cat_environment`，当前 ADS KPI 没有输出该字段。上线前必须由质量负责人确认这是有意合并到“其他”，还是实现遗漏；在确认前登记为口径差异。

### 5.2 `biz_ads_quality_derived_v2`

- 模型名称：质量问题派生指标。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`pjm_qual_zero_rate`、`pjm_qual_remaining_ratio`、`pjm_qual_plan_submit_rate`、`pjm_qual_design_defect_ratio`、`pjm_qual_health_score`。
- ATTRIBUTE：`warn_zero_rate`、`warn_design_ratio`。
- 所有派生率为 0～100 百分比。

## 6. 技术状态 ADS

### 6.1 `biz_ads_tech_state_kpi_v2`

- 模型名称：技术状态基础指标。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`total_change_cnt`、`new_cat_i/ii/iii`、5 个文件签署状态计数、`file_unsigned_i_ii`、`file_unsigned_iii`、`file_signed_total`、`order_unsigned_cat_i/ii/iii`、`order_unsigned_total`、`order_signed_cnt`、`reform_pending_i_ii`、`reform_done_i_ii`、`reform_na_iii`。

### 6.2 `biz_ads_tech_state_derived_v2`

- 模型名称：技术状态派生指标。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`pjm_tech_signature_rate`、`pjm_tech_reform_rate`、`pjm_tech_type1_ratio`、`pjm_tech_unsigned_ratio`、`pjm_tech_health_score`。
- ATTRIBUTE：`warn_signature_rate`、`warn_reform_rate`。

## 7. 风险 ADS

### `biz_ads_risk_kpi_v2`

- 模型名称：项目风险基础指标。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`total_risk_cnt`、`high_cnt`、`mid_cnt`、`low_cnt`、`released_cnt`、`open_cnt`、`release_rate`。
- `release_rate` 当前为 0～1 小数；显示层若乘以 100，必须在指标定义中固定转换位置，不能 SQL 和前端重复换算。

## 8. 预算 ADS

### 8.1 `biz_ads_budget_kpi_v2`

- 模型名称：预算执行基础指标。
- 粒度：每个 `snapshot_scope + snapshot_date` 一行。
- MEASURE：`item_cnt`、`total_budget`、`total_prepaid`、`total_book_cost`、`total_payable`、`total_executed`、`total_remaining`、`overrun_amount`、`overrun_item_cnt`，金额单位万元。

### 8.2 `biz_ads_budget_derived_v2`

- 模型名称：预算执行派生指标。
- 粒度：每个 `snapshot_scope + snapshot_date` 一行。
- MEASURE：`pjm_budg_execution_rate`、`pjm_budg_book_rate`、`pjm_budg_payable_ratio`、`pjm_budg_remaining_rate`、`pjm_budg_health_score`。
- ATTRIBUTE：`warn_overrun`、`warn_overrun_items`。

两张 SQL 当前都没有 KEY，而 DTS 模型草稿要求至少一个 KEY。发布前应在实现中增加稳定常量字段，例如：

```sql
'ALL'::text AS snapshot_scope
```

并将 `snapshot_scope` 标为 KEY、`snapshot_date` 标为 TIME，两者都非空且一同列入粒度键。唯一性作用于两字段组合，不要把金额或布尔预警字段错误标为 KEY。

## 9. 综合健康 ADS

### `biz_ads_composite_derived_v2`

- 模型名称：项目综合健康派生指标。
- 数据域：研究项目域；业务定义中注明跨质量和产品技术域消费。
- KEY/TIME：`period_year`、`period_month`。
- MEASURE：`pjm_prog_health_score`、`pjm_qual_health_score`、`pjm_tech_health_score`、`pjm_composite_health`、`pjm_risk_warning_index`。
- ATTRIBUTE：`health_warning_level`、`risk_warning_level`。
- 固定上游：progress KPI、progress derived、quality derived、tech-state derived 的当前修订。

当前综合健康不包含预算健康和独立风险释放率。不得把它宣传为“全部业务域综合分”；如要纳入，应另行评审权重、缺失值和跨域月份对齐规则。

## 10. 应用主题对应关系

| 主题域 | ADS |
|---|---|
| 项目进度主题 | progress KPI + derived |
| 质量分析主题 | quality KPI + derived |
| 技术状态主题 | tech-state KPI + derived |
| 风险管控主题 | risk KPI |
| 预算执行主题 | budget KPI + derived |
| 项目综合健康主题 | composite derived |

该表是规划建议。当前应用表基础页面不提供主题域绑定控件，必须以服务端实际关联为验收依据。

## 11. ADS 验收清单

- [ ] 10 张 ADS 的上游依赖都固定到正确修订。
- [ ] 月度表的年/月组合键唯一，预算单行表已补合法常量 KEY。
- [ ] 比例（0～1）和百分比（0～100）单位在字段/指标定义中明确。
- [ ] 金额单位统一为万元。
- [ ] 质量环境类字段的 ADS 缺口已有业务结论。
- [ ] 综合健康范围和权重已确认，没有暗示覆盖预算/全部风险指标。
- [ ] ADS 不直接引用 ODS、STG，也不绑定业务过程。
- [ ] 每个字段有中文显示名、标准/密级/权限证据符合当前规划发布策略。
