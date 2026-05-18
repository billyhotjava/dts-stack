# GPMC 大屏 metricNote 注入审计报告

- 注入日期: **2026-05-18**
- 权威字典: `metric-registry.json` (含 60 个指标)
- 涉及大屏: 10 个 (gpmc-*-v4.json)

## 注入总览

- 组件级 metricNote(KPI/图表): **51** 个
- 列级 columnNotes(表格列头): **40** 个
- 合计可悬浮 ℹ️ 入口: **91** 处


## 组件级注入

| 大屏 | KPI/图表数 | 含阈值数 |
|---|---:|---:|
| gpmc-drill-execution-v4.json | 4 | 4 |
| gpmc-drill-quality-v4.json | 4 | 4 |
| gpmc-drill-risk-v4.json | 4 | 0 |
| gpmc-drill-tech-state-v4.json | 4 | 4 |
| gpmc-execution-board-v4.json | 7 | 4 |
| gpmc-overview-v4.json | 5 | 1 |
| gpmc-quality-board-v4.json | 4 | 1 |
| gpmc-risk-board-v4.json | 5 | 1 |
| gpmc-strategic-overview-v4.json | 9 | 6 |
| gpmc-tech-state-board-v4.json | 5 | 2 |

## 列级注入(6 张侧面板表)

| 大屏 | 表组件 | 列数 |
|---|---|---:|
| gpmc-drill-quality-v4.json | `drill-quality-side-panel` | 7 |
| gpmc-drill-risk-v4.json | `drill-risk-side-panel` | 6 |
| gpmc-drill-tech-state-v4.json | `drill-tech-side-panel` | 8 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-alert-table` | 6 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-quality-tags` | 8 |
| gpmc-strategic-overview-v4.json | `gpmc-overview-status-table` | 5 |

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

**gpmc-quality-board-v4.json**
- `gpmc-quality-kpi-total` → **新增质量问题数** · `pjm_qual_new_issues` 
- `gpmc-quality-kpi-open` → **现存质量问题数** · `pjm_qual_open_issues` 
- `gpmc-quality-kpi-close-rate` → **质量问题归零率** · `pjm_qual_zero_rate` ⚠️ 含阈值
- `gpmc-quality-kpi-top1` → **TOP1 质量问题分类数量** · `pjm_qual_top1_count` 

**gpmc-risk-board-v4.json**
- `gpmc-risk-kpi-total` → **风险总数** · `pjm_risk_total` 
- `gpmc-risk-kpi-high` → **高风险数** · `pjm_risk_high` 
- `gpmc-risk-kpi-mid` → **中风险数** · `pjm_risk_mid` 
- `gpmc-risk-kpi-open` → **未释放风险数** · `pjm_risk_open` 
- `gpmc-risk-kpi-release-rate` → **风险释放率** · `pjm_risk_release_rate` ⚠️ 含阈值

**gpmc-strategic-overview-v4.json**
- `gpmc-overview-kpi-total` → **项目总数** · `pjm_project_total` 
- `gpmc-overview-kpi-active` → **进行中项目** · `pjm_project_active` 
- `gpmc-overview-kpi-delay` → **延期项目数** · `pjm_project_delay` 
- `gpmc-overview-kpi-progress` → **节点按时完成率** · `pjm_prog_on_time_rate` ⚠️ 含阈值
- `gpmc-overview-kpi-risk` → **项目风险预警指数** · `pjm_risk_warning_index` ⚠️ 含阈值
- `gpmc-overview-health-gauge` → **项目综合健康度** · `pjm_composite_health` ⚠️ 含阈值
- `gpmc-overview-gauge-progress` → **进度健康度评分** · `pjm_prog_health_score` ⚠️ 含阈值
- `gpmc-overview-gauge-quality` → **质量健康度评分** · `pjm_qual_health_score` ⚠️ 含阈值
- `gpmc-overview-gauge-risk` → **技术状态健康度评分** · `pjm_tech_health_score` ⚠️ 含阈值

**gpmc-tech-state-board-v4.json**
- `gpmc-tech-kpi-total` → **技术状态变更单总数** · `pjm_tech_total_changes` 
- `gpmc-tech-kpi-unsigned` → **技术状态变更单未完成签署(I, II, III类)** · `pjm_tech_unsigned_total` 
- `gpmc-tech-kpi-sign-rate` → **技术状态变更单签署完成率** · `pjm_tech_signature_rate` ⚠️ 含阈值
- `gpmc-tech-kpi-reform-pending` → **未落实整改(I, II类)** · `pjm_tech_reform_pending_i_ii` 
- `gpmc-tech-kpi-reform-rate` → **整改落实完成率(I, II类)** · `pjm_tech_reform_rate` ⚠️ 含阈值

### 列级


**gpmc-drill-quality-v4.json :: `drill-quality-side-panel`** (7 列)
- `新增质量问题` → `pjm_qual_new_issues`
- `现存质量问题` → `pjm_qual_open_issues`
- `已归零数` → `pjm_qual_zero_completed`
- `未提交归零计划` → `pjm_qual_no_plan`
- `技术归零` → `pjm_qual_tech_zero`
- `管理归零` → `pjm_qual_mgmt_zero`
- `双归零` → `pjm_qual_both_zero`

**gpmc-drill-risk-v4.json :: `drill-risk-side-panel`** (6 列)
- `风险总数` → `pjm_risk_total`
- `高风险` → `pjm_risk_high`
- `中风险` → `pjm_risk_mid`
- `低风险` → `pjm_risk_low`
- `已释放` → `pjm_risk_released`
- `未释放` → `pjm_risk_open`

**gpmc-drill-tech-state-v4.json :: `drill-tech-side-panel`** (8 列)
- `变更总数` → `pjm_tech_total_changes`
- `I类` → `pjm_tech_new_changes_i`
- `II类` → `pjm_tech_new_changes_ii`
- `III类` → `pjm_tech_new_changes_iii`
- `已签署` → `pjm_tech_signed_total`
- `未签署` → `pjm_tech_unsigned_total`
- `已整改` → `pjm_tech_reform_done_i_ii`
- `待整改` → `pjm_tech_reform_pending_i_ii`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-alert-table`** (6 列)
- `风险总数` → `pjm_risk_total`
- `高风险` → `pjm_risk_high`
- `中风险` → `pjm_risk_mid`
- `低风险` → `pjm_risk_low`
- `已释放` → `pjm_risk_released`
- `未释放` → `pjm_risk_open`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-quality-tags`** (8 列)
- `设计` → `pjm_qual_cat_design`
- `工艺` → `pjm_qual_cat_process`
- `管理` → `pjm_qual_cat_management`
- `元器件` → `pjm_qual_cat_component`
- `操作` → `pjm_qual_cat_operation`
- `外协外购` → `pjm_qual_cat_outsource`
- `软件` → `pjm_qual_cat_software`
- `其他` → `pjm_qual_cat_other`

**gpmc-strategic-overview-v4.json :: `gpmc-overview-status-table`** (5 列)
- `按时完成` → `pjm_prog_on_time_cnt`
- `超期完成` → `pjm_prog_overdue_done_cnt`
- `未完成` → `pjm_prog_incomplete_cnt`
- `正常待完成` → `pjm_prog_pending_normal_cnt`
- `异常` → `pjm_prog_abnormal_cnt`