# GPMC 大屏 metricNote 注入审计报告

- 注入日期: **2026-05-18**
- 权威字典: `metric-registry.json` (含 64 个指标)
- 涉及大屏: 10 个 (gpmc-*-v4.json)

## 注入总览

- 组件级 metricNote(KPI/图表): **75** 个
- 图表 seriesNotes(系列口径): **56** 个系列条目
- 列级 metricNote(表格指标列): **51** 个
- 列级 fieldNote(表格枚举列): **40** 个
- 合计可悬浮 ℹ️ 入口: **166** 处


## 组件级注入

| 大屏 | KPI/图表数 | 含阈值数 |
|---|---:|---:|
| gpmc-drill-execution-v4.json | 4 | 4 |
| gpmc-drill-quality-v4.json | 4 | 4 |
| gpmc-drill-risk-v4.json | 4 | 0 |
| gpmc-drill-tech-state-v4.json | 4 | 4 |
| gpmc-execution-board-v4.json | 9 | 4 |
| gpmc-overview-v4.json | 11 | 1 |
| gpmc-quality-board-v4.json | 8 | 1 |
| gpmc-risk-board-v4.json | 8 | 1 |
| gpmc-strategic-overview-v4.json | 14 | 6 |
| gpmc-tech-state-board-v4.json | 9 | 2 |

## 列级注入(6 张侧面板表)

| 大屏 | 表组件 | 列数 |
|---|---|---:|
| gpmc-drill-execution-v4.json | `drill-exec-detail-table` | 1 指标 + 3 字段 |
| gpmc-drill-execution-v4.json | `drill-exec-side-panel` | 3 指标 |
| gpmc-drill-execution-v4.json | `drill-exec-support-table` | 1 指标 + 3 字段 |
| gpmc-drill-quality-v4.json | `drill-quality-detail-table` | 1 指标 + 3 字段 |
| gpmc-drill-quality-v4.json | `drill-quality-side-panel` | 7 指标 |
| gpmc-drill-quality-v4.json | `drill-quality-support-table` | 1 指标 + 3 字段 |
| gpmc-drill-risk-v4.json | `drill-risk-detail-table` | 1 指标 + 3 字段 |
| gpmc-drill-risk-v4.json | `drill-risk-side-panel` | 6 指标 |
| gpmc-drill-risk-v4.json | `drill-risk-support-table` | 1 指标 + 3 字段 |
| gpmc-drill-tech-state-v4.json | `drill-tech-detail-table` | 4 字段 |
| gpmc-drill-tech-state-v4.json | `drill-tech-side-panel` | 8 指标 |
| gpmc-drill-tech-state-v4.json | `drill-tech-support-table` | 4 字段 |
| gpmc-execution-board-v4.json | `gpmc-execution-task-list` | 2 字段 |
| gpmc-execution-board-v4.json | `gpmc-execution-top-overdue` | 2 字段 |
| gpmc-quality-board-v4.json | `gpmc-quality-detail` | 2 字段 |
| gpmc-risk-board-v4.json | `gpmc-risk-detail` | 1 指标 + 3 字段 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-alert-table` | 6 指标 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-delay-top` | 1 指标 + 3 字段 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-quality-tags` | 8 指标 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-status-table` | 5 指标 |
| gpmc-tech-state-board-v4.json | `gpmc-tech-change-list` | 2 字段 |

## 详细注入清单

### 组件级


**gpmc-drill-execution-v4.json**
- `drill-exec-kpi-1` → **节点完成百分比** · `pjm_prog_completion_rate` ⚠️ 含阈值
- `drill-exec-kpi-2` → **里程碑节点完成总百分比** · `pjm_prog_milestone_completion_rate` ⚠️ 含阈值
- `drill-exec-kpi-3` → **节点不正常率** · `pjm_prog_abnormal_rate` ⚠️ 含阈值
- `drill-exec-kpi-4` → **项目风险预警指数** · `pjm_risk_warning_index` ⚠️ 含阈值

**gpmc-drill-quality-v4.json**
- `drill-quality-kpi-1` → **质量问题归零率** · `pjm_qual_zero_rate` ⚠️ 含阈值
- `drill-quality-kpi-2` → **现存质量问题比率** · `pjm_qual_remaining_ratio` ⚠️ 含阈值
- `drill-quality-kpi-3` → **归零计划提交率** · `pjm_qual_plan_submit_rate` ⚠️ 含阈值
- `drill-quality-kpi-4` → **质量健康度评分** · `pjm_qual_health_score` ⚠️ 含阈值

**gpmc-drill-risk-v4.json**
- `drill-risk-kpi-1` → **风险总数** · `pjm_risk_total` 
- `drill-risk-kpi-2` → **高风险数** · `pjm_risk_high` 
- `drill-risk-kpi-3` → **中风险数** · `pjm_risk_mid` 
- `drill-risk-kpi-4` → **低风险数** · `pjm_risk_low` 

**gpmc-drill-tech-state-v4.json**
- `drill-tech-kpi-1` → **技术状态变更单签署完成率** · `pjm_tech_signature_rate` ⚠️ 含阈值
- `drill-tech-kpi-2` → **整改落实完成率(I, II类)** · `pjm_tech_reform_rate` ⚠️ 含阈值
- `drill-tech-kpi-3` → **变更单未签署比率** · `pjm_tech_unsigned_ratio` ⚠️ 含阈值
- `drill-tech-kpi-4` → **技术状态健康度评分** · `pjm_tech_health_score` ⚠️ 含阈值

**gpmc-execution-board-v4.json**
- `gpmc-execution-completion-trend` → **完成时间变化数** · `[图表] 完成时间变化数` 
- `gpmc-execution-stage` → **项目状态分布图** · `[图表] 项目状态分布图` 
- `gpmc-execution-kpi-incomplete` → **项目本周期节点未完成总数** · `pjm_prog_incomplete_cnt` 
- `gpmc-execution-kpi-completion` → **节点完成百分比** · `pjm_prog_completion_rate` ⚠️ 含阈值
- `gpmc-execution-kpi-milestone` → **里程碑节点完成总百分比** · `pjm_prog_milestone_completion_rate` ⚠️ 含阈值
- `gpmc-execution-kpi-overdue-rate` → **节点超期率** · `pjm_prog_overdue_rate` ⚠️ 含阈值
- `gpmc-execution-kpi-overdue-done-rate` → **超期完成率** · `pjm_prog_overdue_completion_rate` ⚠️ 含阈值
- `gpmc-execution-kpi-overdue-cnt` → **超期任务数** · `pjm_prog_overdue_cnt` 
- `gpmc-execution-kpi-high-risk-open` → **截止目前未完成高风险节点数** · `pjm_prog_high_risk_incomplete` 

**gpmc-overview-v4.json**
- `ov4-kpi-project-total` → **项目总数** · `pjm_project_total` 
- `ov4-kpi-completion-rate` → **节点完成百分比** · `pjm_prog_completion_rate` ⚠️ 含阈值
- `ov4-kpi-quality-open` → **现存质量问题数** · `pjm_qual_open_issues` 
- `ov4-kpi-tech-reform` → **未落实整改(I, II类)** · `pjm_tech_reform_pending_i_ii` 
- `ov4-kpi-high-risk` → **高风险数** · `pjm_risk_high` 
- `ov4-progress-by-project` → **项目节点统计** · `[图表] 项目节点统计` 
- `ov4-progress-by-dept` → **科室项目节点统计** · `[图表] 科室项目节点统计` 
- `ov4-risk-by-project` → **项目风险统计** · `[图表] 项目风险统计` 
- `ov4-risk-by-dept` → **风险类型占比** · `[图表] 风险类型占比` 
- `ov4-gantt-chart` → **下月重点计划甘特** · `[图表] 下月重点计划甘特` 
- `ov4-procurement-by-dept` → **物料采购** · `pjm_procurement_by_dept` 

**gpmc-quality-board-v4.json**
- `gpmc-quality-kpi-total` → **新增质量问题数** · `pjm_qual_new_issues` 
- `gpmc-quality-kpi-open` → **现存质量问题数** · `pjm_qual_open_issues` 
- `gpmc-quality-kpi-close-rate` → **质量问题归零率** · `pjm_qual_zero_rate` ⚠️ 含阈值
- `gpmc-quality-kpi-top1` → **TOP1 质量问题分类数量** · `pjm_qual_top1_count` 
- `gpmc-quality-project` → **项目质量问题** · `[图表] 项目质量问题` 
- `gpmc-quality-stage` → **时段质量问题** · `[图表] 时段质量问题` 
- `gpmc-quality-category` → **质量问题类别** · `[图表] 质量问题类别` 
- `gpmc-quality-trend` → **月度质量问题趋势** · `[图表] 月度质量问题趋势` 

**gpmc-risk-board-v4.json**
- `gpmc-risk-kpi-total` → **风险总数** · `pjm_risk_total` 
- `gpmc-risk-kpi-high` → **高风险数** · `pjm_risk_high` 
- `gpmc-risk-kpi-mid` → **中风险数** · `pjm_risk_mid` 
- `gpmc-risk-kpi-open` → **未释放风险数** · `pjm_risk_open` 
- `gpmc-risk-kpi-release-rate` → **风险释放率** · `pjm_risk_release_rate` ⚠️ 含阈值
- `gpmc-risk-project` → **项目风险统计** · `[图表] 项目风险统计` 
- `gpmc-risk-category` → **风险分类分布** · `[图表] 风险分类分布` 
- `gpmc-risk-dept` → **科室风险统计** · `[图表] 科室风险统计` 

**gpmc-strategic-overview-v4.json**
- `gpmc-overview-kpi-total` → **项目总数** · `pjm_project_total` 
- `gpmc-overview-kpi-active` → **进行中项目** · `pjm_project_active` 
- `gpmc-overview-kpi-delay` → **延期项目数** · `pjm_project_delay` 
- `gpmc-overview-kpi-progress` → **节点按时完成率** · `pjm_prog_on_time_rate` ⚠️ 含阈值
- `gpmc-overview-kpi-risk` → **项目风险预警指数** · `pjm_risk_warning_index` ⚠️ 含阈值
- `gpmc-overview-weekly-chart` → **月度完成趋势** · `[图表] 月度完成趋势` 
- `gpmc-overview-stage-chart` → **项目阶段分布** · `[图表] 项目阶段分布` 
- `gpmc-overview-health-gauge` → **项目综合健康度** · `pjm_composite_health` ⚠️ 含阈值
- `gpmc-overview-gauge-progress` → **进度健康度评分** · `pjm_prog_health_score` ⚠️ 含阈值
- `gpmc-overview-gauge-quality` → **质量健康度评分** · `pjm_qual_health_score` ⚠️ 含阈值
- `gpmc-overview-gauge-risk` → **技术状态健康度评分** · `pjm_tech_health_score` ⚠️ 含阈值
- `gpmc-overview-quality-trend` → **质量闭环趋势** · `[图表] 质量闭环趋势` 
- `gpmc-overview-risk-rank` → **风险分类排名** · `[图表] 风险分类排名` 
- `gpmc-overview-tech-change` → **技术状态变更** · `[图表] 技术状态变更` 

**gpmc-tech-state-board-v4.json**
- `gpmc-tech-kpi-total` → **技术状态变更单总数** · `pjm_tech_total_changes` 
- `gpmc-tech-kpi-unsigned` → **技术状态变更单未完成签署(I, II, III类)** · `pjm_tech_unsigned_total` 
- `gpmc-tech-kpi-sign-rate` → **技术状态变更单签署完成率** · `pjm_tech_signature_rate` ⚠️ 含阈值
- `gpmc-tech-kpi-reform-pending` → **未落实整改(I, II类)** · `pjm_tech_reform_pending_i_ii` 
- `gpmc-tech-kpi-reform-rate` → **整改落实完成率(I, II类)** · `pjm_tech_reform_rate` ⚠️ 含阈值
- `gpmc-tech-project-sign` → **项目变更签署率** · `[图表] 项目变更签署率` 
- `gpmc-tech-dept-review` → **科室变更签署审查** · `[图表] 科室变更签署审查` 
- `gpmc-tech-dept-trend` → **科室变更趋势** · `[图表] 科室变更趋势` 
- `gpmc-tech-project-count` → **项目变更次数** · `[图表] 项目变更次数` 

### 列级


**gpmc-drill-execution-v4.json :: `drill-exec-detail-table`** (4 列)
- [指标] `延期天数` → `pjm_prog_delay_days`
- [字段字典] `节点类型` → `节点类型`
- [字段字典] `完成情况` → `完成情况`
- [字段字典] `风险等级` → `风险等级`

**gpmc-drill-execution-v4.json :: `drill-exec-side-panel`** (3 列)
- [指标] `节点总数` → `pjm_prog_total_nodes`
- [指标] `已完成` → `pjm_prog_on_time_cnt`
- [指标] `未完成` → `pjm_prog_incomplete_cnt`

**gpmc-drill-execution-v4.json :: `drill-exec-support-table`** (4 列)
- [指标] `延期天数` → `pjm_prog_delay_days`
- [字段字典] `节点类型` → `节点类型`
- [字段字典] `完成情况` → `完成情况`
- [字段字典] `风险等级` → `风险等级`

**gpmc-drill-quality-v4.json :: `drill-quality-detail-table`** (4 列)
- [指标] `滞留天数` → `pjm_qual_aging_days`
- [字段字典] `问题分类` → `质量问题分类`
- [字段字典] `归零计划` → `是/否(布尔)`
- [字段字典] `状态` → `质量问题状态`

**gpmc-drill-quality-v4.json :: `drill-quality-side-panel`** (7 列)
- [指标] `新增质量问题` → `pjm_qual_new_issues`
- [指标] `现存质量问题` → `pjm_qual_open_issues`
- [指标] `已归零数` → `pjm_qual_zero_completed`
- [指标] `未提交归零计划` → `pjm_qual_no_plan`
- [指标] `技术归零` → `pjm_qual_tech_zero`
- [指标] `管理归零` → `pjm_qual_mgmt_zero`
- [指标] `双归零` → `pjm_qual_both_zero`

**gpmc-drill-quality-v4.json :: `drill-quality-support-table`** (4 列)
- [指标] `滞留天数` → `pjm_qual_aging_days`
- [字段字典] `问题分类` → `质量问题分类`
- [字段字典] `归零计划` → `是/否(布尔)`
- [字段字典] `状态` → `质量问题状态`

**gpmc-drill-risk-v4.json :: `drill-risk-detail-table`** (4 列)
- [指标] `滞留天数` → `pjm_qual_aging_days`
- [字段字典] `风险等级` → `风险等级`
- [字段字典] `风险类别` → `风险类别`
- [字段字典] `风险状态` → `风险状态`

**gpmc-drill-risk-v4.json :: `drill-risk-side-panel`** (6 列)
- [指标] `风险总数` → `pjm_risk_total`
- [指标] `高风险` → `pjm_risk_high`
- [指标] `中风险` → `pjm_risk_mid`
- [指标] `低风险` → `pjm_risk_low`
- [指标] `已释放` → `pjm_risk_released`
- [指标] `未释放` → `pjm_risk_open`

**gpmc-drill-risk-v4.json :: `drill-risk-support-table`** (4 列)
- [指标] `滞留天数` → `pjm_qual_aging_days`
- [字段字典] `风险等级` → `风险等级`
- [字段字典] `风险类别` → `风险类别`
- [字段字典] `风险状态` → `风险状态`

**gpmc-drill-tech-state-v4.json :: `drill-tech-detail-table`** (4 列)
- [字段字典] `更改类别` → `更改类别`
- [字段字典] `文件签署状态` → `文件签署状态`
- [字段字典] `整改状态` → `整改状态`
- [字段字典] `签署完成` → `是/否(布尔)`

**gpmc-drill-tech-state-v4.json :: `drill-tech-side-panel`** (8 列)
- [指标] `变更总数` → `pjm_tech_total_changes`
- [指标] `I类` → `pjm_tech_new_changes_i`
- [指标] `II类` → `pjm_tech_new_changes_ii`
- [指标] `III类` → `pjm_tech_new_changes_iii`
- [指标] `已签署` → `pjm_tech_signed_total`
- [指标] `未签署` → `pjm_tech_unsigned_total`
- [指标] `已整改` → `pjm_tech_reform_done_i_ii`
- [指标] `待整改` → `pjm_tech_reform_pending_i_ii`

**gpmc-drill-tech-state-v4.json :: `drill-tech-support-table`** (4 列)
- [字段字典] `更改类别` → `更改类别`
- [字段字典] `文件签署状态` → `文件签署状态`
- [字段字典] `整改状态` → `整改状态`
- [字段字典] `签署完成` → `是/否(布尔)`

**gpmc-execution-board-v4.json :: `gpmc-execution-task-list`** (2 列)
- [字段字典] `节点类型` → `节点类型`
- [字段字典] `完成情况` → `完成情况`

**gpmc-execution-board-v4.json :: `gpmc-execution-top-overdue`** (2 列)
- [字段字典] `节点类型` → `节点类型`
- [字段字典] `完成情况` → `完成情况`

**gpmc-quality-board-v4.json :: `gpmc-quality-detail`** (2 列)
- [字段字典] `问题分类` → `质量问题分类`
- [字段字典] `整改状态` → `整改状态`

**gpmc-risk-board-v4.json :: `gpmc-risk-detail`** (4 列)
- [指标] `滞留天数` → `pjm_qual_aging_days`
- [字段字典] `风险等级` → `风险等级`
- [字段字典] `风险分类` → `风险类别`
- [字段字典] `风险状态` → `风险状态`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-alert-table`** (6 列)
- [指标] `风险总数` → `pjm_risk_total`
- [指标] `高风险` → `pjm_risk_high`
- [指标] `中风险` → `pjm_risk_mid`
- [指标] `低风险` → `pjm_risk_low`
- [指标] `已释放` → `pjm_risk_released`
- [指标] `未释放` → `pjm_risk_open`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-delay-top`** (4 列)
- [指标] `延期天数` → `pjm_prog_delay_days`
- [字段字典] `节点类型` → `节点类型`
- [字段字典] `完成情况` → `完成情况`
- [字段字典] `风险等级` → `风险等级`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-quality-tags`** (8 列)
- [指标] `设计` → `pjm_qual_cat_design`
- [指标] `工艺` → `pjm_qual_cat_process`
- [指标] `管理` → `pjm_qual_cat_management`
- [指标] `元器件` → `pjm_qual_cat_component`
- [指标] `操作` → `pjm_qual_cat_operation`
- [指标] `外协外购` → `pjm_qual_cat_outsource`
- [指标] `软件` → `pjm_qual_cat_software`
- [指标] `其他` → `pjm_qual_cat_other`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-status-table`** (5 列)
- [指标] `按时完成` → `pjm_prog_on_time_cnt`
- [指标] `超期完成` → `pjm_prog_overdue_done_cnt`
- [指标] `未完成` → `pjm_prog_incomplete_cnt`
- [指标] `正常待完成` → `pjm_prog_pending_normal_cnt`
- [指标] `异常` → `pjm_prog_abnormal_cnt`

**gpmc-tech-state-board-v4.json :: `gpmc-tech-change-list`** (2 列)
- [字段字典] `签署状态` → `文件签署状态`
- [字段字典] `整改状态` → `整改状态`