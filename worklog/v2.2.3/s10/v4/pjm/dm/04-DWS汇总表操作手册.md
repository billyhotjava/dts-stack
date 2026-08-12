# DWS 汇总表操作手册

## 1. DWS 规则

DWS 是公共层的主题汇总，不是 ODS“汇总表”的简单搬运。每张 DWS 必须：

- 只依赖固定修订的 DWD 模型；
- 明确输出粒度；
- 至少包含一个 MEASURE 字段或指标引用；
- 保存分子、分母和计数，避免只保存不可重算的百分比；
- 不选择业务过程。业务过程已经由上游 FACT 固定，SUMMARY 页面也不展示该字段。

当前 5 张 DWS 均为 `table` + 全量加载 + 无物理分区，操作手册按现有 dbt 实现登记，不擅自改为增量或分区表。

## 2. 通用创建步骤

1. 确认对应 DWD 已保存、设计检查通过，且要引用的修订仍是 CURRENT。
2. 进入“数据建模 → 模型工作台”。
3. 点击新建，选择“汇总表”。
4. 选择数据域和系统 DWS 分层。
5. 填写模型名称、物理表名、业务定义和粒度。
6. 填写 KEY、TIME、ATTRIBUTE 和 MEASURE 字段；每个字段补中文显示名。
7. 关联上游 DWD 的固定修订。当前基础页不能编辑依赖，现有模型应随 dbt 逆向导入建立依赖。
8. 保存后点击“提交”，检查 DESIGNED 门禁没有“汇总表需声明汇总字段或指标引用”。

## 3. 项目进度月度汇总

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 研究项目域 |
| 模型名称 | 项目进度月度汇总 |
| 物理表名 | `biz_dws_progress_monthly_v2` |
| 业务定义 | 按项目和计划月份汇总项目节点完成、超期、风险及节点类型计数，为跨项目月度指标保留可重算分子/分母。 |
| 模型粒度 | 一个项目×计划月份一行。 |
| KEY | `project_no`、`plan_month` |
| TIME | `plan_year`、`plan_quarter`、`plan_month` |
| 上游 | `biz_dwd_project_node_v2` 固定修订 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |

MEASURE 字段：

`total_cnt`、`pending_normal_cnt`、`due_cnt`、`outside_completed_cnt`、`incomplete_cnt`、`on_time_cnt`、`overdue_completed_cnt`、`overdue_completed_unchanged_cnt`、`completed_total_cnt`、`abnormal_pending_non_general_cnt`、`overdue_incomplete_unchanged_non_general_cnt`、`overdue_incomplete_changed_non_general_cnt`、`overdue_completed_unchanged_non_general_cnt`、`incomplete_high_risk_cnt`、`incomplete_mid_risk_cnt`、`incomplete_milestone_cnt`、`incomplete_major_cnt`、`incomplete_important_cnt`、`milestone_on_time_cnt`、`milestone_overdue_completed_cnt`、`milestone_pending_cnt`、`high_risk_cnt`、`mid_risk_cnt`、`milestone_total_cnt`、`major_total_cnt`、`important_total_cnt`、`on_time_cnt_v2`、`pending_normal_cnt_v2`、`overdue_completed_effective_cnt`、`overdue_incomplete_effective_cnt`。

验收重点：同一 `project_no + plan_month` 唯一；指标总数与四大状态分类能解释；周期外完成数没有重复计入。

## 4. 质量问题月度汇总

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 质量管理域 |
| 模型名称 | 质量问题月度汇总 |
| 物理表名 | `biz_dws_quality_monthly_v2` |
| 业务定义 | 按项目、责任单位和问题发生月份汇总新增、现存、归零和原因分类数量。 |
| 模型粒度 | 一个项目×责任单位×问题发生月份一行。 |
| KEY | `project_no`、`dept`、`period_month` |
| TIME | `period_year`、`period_month` |
| 上游 | `biz_dwd_quality_issue_v2` 固定修订 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |

MEASURE 字段：`new_issue_cnt`、`open_issue_cnt`、`tech_zero_cnt`、`mgmt_zero_cnt`、`both_zero_cnt`、`zero_completed_cnt`、`no_zero_plan_cnt`；未归零分类 `open_cat_design/process/management/component/operation/outsource/software/environment/other`；全量分类 `total_cat_design/process/management/component/operation/outsource/software/environment/other`。

验收重点：归零状态只来自 canonical 维度标志；分类未命中进入“其他”，不能丢行。

## 5. 技术状态月度汇总

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 产品技术域 |
| 模型名称 | 技术状态月度汇总 |
| 物理表名 | `biz_dws_tech_state_monthly_v2` |
| 业务定义 | 按项目、责任科室和更改提出月份汇总更改类别、评审、文件签署、变更单签署和整改状态。 |
| 模型粒度 | 一个项目×责任科室×更改提出月份一行。 |
| KEY | `project_no`、`dept`、`period_month` |
| TIME | `period_year`、`period_month` |
| 上游 | `biz_dwd_tech_state_v2` 固定修订 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |

MEASURE 字段：`total_change_cnt`、`new_cat_i`、`new_cat_ii`、`new_cat_iii`、`requirement_submitted_cnt`、`requirement_not_submitted_cnt`、`review_done_cnt`、`review_pending_cnt`、`file_submitted_not_reviewed_i_ii`、`file_reviewed_not_signed_i_ii`、`file_reviewed_signed_i_ii`、`file_submitted_not_signed_iii`、`file_submitted_signed_iii`、`order_unsigned_cat_i`、`order_unsigned_cat_ii`、`order_unsigned_cat_iii`、`order_signed_cnt`、`reform_pending_i_ii`、`reform_done_i_ii`、`reform_na_iii`。

验收重点：I/II 和 III 类文件签署语义不同，必须保留 `signature_context` 映射逻辑。

## 6. 项目风险月度汇总

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 研究项目域 |
| 模型名称 | 项目风险月度汇总 |
| 物理表名 | `biz_dws_risk_monthly_v2` |
| 业务定义 | 按项目、责任科室和风险提出月份汇总风险等级、释放状态和风险分类数量。 |
| 模型粒度 | 一个项目×责任科室×风险提出月份一行。 |
| KEY | `project_no`、`dept`、`period_month` |
| TIME | `period_year`、`period_month` |
| 上游 | `biz_dwd_risk_info_v2` 固定修订 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |

MEASURE 字段：`total_risk_cnt`、`high_cnt`、`mid_cnt`、`low_cnt`、`released_cnt`、`open_cnt`、`cat_technical_cnt`、`cat_schedule_cnt`、`cat_cost_cnt`、`cat_design_cnt`、`cat_quality_cnt`、`cat_other_cnt`。

验收重点：高中低之和与风险总数的差异必须可解释；空等级不能被默认成低风险。

## 7. 预算执行汇总

| 页面字段 | 填写值 |
|---|---|
| 数据域 | 财务管理域 |
| 模型名称 | 预算执行汇总 |
| 物理表名 | `biz_dws_budget_v2` |
| 业务定义 | 按业务快照日、项目和研究室汇总预算、三本账执行、剩余和超支金额，金额单位万元。 |
| 模型粒度 | 一个业务快照日×项目×研究室一行。 |
| KEY | `snapshot_date`、`project_no`、`research_lab` |
| TIME | `snapshot_date` |
| 上游 | `biz_dwd_budget_v2` 固定修订 |
| 物化 / 加载 / 分区 | `table` / 全量 / 留空 |

MEASURE 字段：`item_cnt`、`budget_amount`、`prepaid_amount`、`book_cost_amount`、`payable_amount`、`executed_amount`、`remaining_amount`、`overrun_amount`、`overrun_item_cnt`。

本表使用 DWD 的业务 `snapshot_date` 支持跨快照分析，仍不得直接使用 `_dts_import_time` 作为日期筛选口径。

## 8. DWS 验收清单

- [ ] 5 张表的 KEY 与粒度一致，组合键唯一。
- [ ] 所有上游为同规划或已发布模型的固定修订，状态 CURRENT。
- [ ] 没有直接引用 ODS 或 STG。
- [ ] 计数和金额字段标记 MEASURE，时间字段标记 TIME。
- [ ] 百分比需要的分子/分母可从 DWS 重算。
- [ ] 汇总表没有伪造业务过程绑定。
- [ ] 预算表明确“业务快照日、万元、不使用技术导入时间”三项口径。
