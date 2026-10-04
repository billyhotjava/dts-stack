# `gpmc-overview-v3` 指标血缘说明

## 1. 总览

`gpmc-overview-v3.json` 这张屏当前不走 ADS，实际数据链路是：

```text
ODS -> DWD -> DWS -> 页面
```

按域拆开是：

- 进度域
  - `ods_project_subject_domain_v2`
  - `biz_dwd_project_node_v2`
  - `biz_dws_progress_monthly_v2`
  - `gpmc-overview-v3.json`
- 质量域
  - `ods_quality_issue_v2`
  - `biz_dwd_quality_issue_v2`
  - `biz_dws_quality_monthly_v2`
  - `gpmc-overview-v3.json`
- 技术状态域
  - `ods_tech_state_v2`
  - `biz_dwd_tech_state_v2`
  - `biz_dws_tech_state_monthly_v2`
  - `gpmc-overview-v3.json`
- 风险域
  - `ods_risk_info_v2`
  - `biz_dwd_risk_info_v2`
  - `biz_dws_risk_monthly_v2`
  - `gpmc-overview-v3.json`

## 2. 页面区域和来源表

| 页面区域 | 页面内容 | 当前查询层 |
|---|---|---|
| 顶部数字卡 | 项目总数、项目完成率、未闭环质量问题、未整改技术更改单、高风险数量 | DWS |
| 中间图表 | 进度、质量、技术状态、风险的按项目/按科室图 | 进度图走 DWD，其他图走 DWS |
| 底部总结卡 | 现存质量问题、未整改技术项、高风险总数、项目完成率 | DWS |
| 项目进度甘特 | 活跃项目 + 节点时间线 | DWD |
| 科室筛选 | 科室下拉 | DWD |

## 3. 进度域血缘

### 3.1 ODS

源表：`ods_project_subject_domain_v2`

总览屏实际会用到的原始字段主要有：

- `project_no`
- `subsystem`
- `node_task`
- `node_type`
- `completion_status`
- `plan_start_date`
- `plan_date`
- `actual_start_date`
- `actual_date`
- `dept`
- `owner`
- `project_manager`

### 3.2 DWD

模型：`biz_dwd_project_node_v2`

这一层做了两类关键处理：

1. 原始字段标准化
   - 日期解析
   - 周字段转数字
   - 空字符串转 `NULL`
2. 派生校验常用布尔字段
   - `is_completed`
   - `is_incomplete`
   - `is_pending_normal`
   - `is_overdue_completed`
   - `is_due`
   - `delay_days`
   - `plan_month`
   - `actual_month`

### 3.3 DWS

模型：`biz_dws_progress_monthly_v2`

粒度：

- `project_no + plan_month`

总览屏会用到的字段：

- `total_cnt`
- `pending_normal_cnt`
- `due_cnt`
- `outside_completed_cnt`
- `incomplete_cnt`
- `completed_total_cnt`
- `high_risk_cnt`

### 3.4 页面怎么用

#### 顶部/底部数字卡

- `项目总数`
  - `COUNT(DISTINCT project_no)`
- `进行中项目`
  - `COUNT(DISTINCT project_no)` where `incomplete_cnt > 0`
- `项目完成率`
  - `SUM(completed_total_cnt) / SUM(due_cnt + outside_completed_cnt)`

#### 进度图

- `进度-按项目`
  - 直接查 `biz_dwd_project_node_v2`
  - 按 `project_no` 聚合：
    - `is_completed`
    - `is_incomplete`
    - `is_pending_normal`
- `进度-按科室`
  - 直接查 `biz_dwd_project_node_v2`
  - 按 `dept` 聚合：
    - `is_completed`
    - `is_incomplete`

#### 甘特图

直接查 `biz_dwd_project_node_v2`。

页面展示字段和 DWD 字段对应关系：

| 页面字段 | DWD 字段 |
|---|---|
| 重大项目 | `project_no` |
| 子项目 | `subsystem` |
| 任务 | `node_task` |
| 类型 | `node_type` |
| 计划开始日期 | `plan_start_date` |
| 计划完成日期 | `plan_date` |
| 实际开始日期 | `actual_start_date`，为空时回退 `plan_start_date` |
| 实际完成日期 | `actual_date` |
| 是否完成 | `is_completed` |
| 是否超期完成 | `is_overdue_completed` |
| 是否未完成 | `is_incomplete` |
| 延期天数 | `delay_days` |

## 4. 质量域血缘

### 4.1 ODS

源表：`ods_quality_issue_v2`

总览屏相关原始字段：

- `project_no`
- `dept`
- `issue_date`
- `status`
- `issue_category`

### 4.2 DWD

模型：`biz_dwd_quality_issue_v2`

这一层会把原始状态拆成布尔字段：

- `is_zero_completed`
- `is_tech_zero`
- `is_mgmt_zero`
- `is_both_zero`
- `has_zero_plan`

同时产出时间标签：

- `issue_year`
- `issue_month`

### 4.3 DWS

模型：`biz_dws_quality_monthly_v2`

粒度：

- `project_no + dept + issue_month`

总览屏会用到的字段：

- `open_issue_cnt`
- `zero_completed_cnt`

### 4.4 页面怎么用

- `未闭环质量问题`
- `现存质量问题`
- `质量-按项目`
- `质量-按科室`

本质上都来自：

- `SUM(open_issue_cnt)`
- `SUM(zero_completed_cnt)`

## 5. 技术状态域血缘

### 5.1 ODS

源表：`ods_tech_state_v2`

总览屏相关原始字段：

- `project_no`
- `dept`
- `change_submit_time`
- `change_category`
- `completion_signature`
- `file_signature_status`
- `reform_status`

### 5.2 DWD

模型：`biz_dwd_tech_state_v2`

这一层会把技术状态拆成布尔字段：

- `is_cat_i`
- `is_cat_ii`
- `is_cat_iii`
- `is_signature_completed`
- `is_file_reviewed`
- `is_file_signed`
- `is_reform_pending`
- `is_reform_done`
- `is_reform_na`

同时产出时间标签：

- `submit_year`
- `submit_month`

### 5.3 DWS

模型：`biz_dws_tech_state_monthly_v2`

粒度：

- `project_no + dept + submit_month`

总览屏会用到的字段：

- `reform_pending_i_ii`
- `reform_done_i_ii`
- `order_unsigned_cat_i`
- `order_unsigned_cat_ii`
- `order_unsigned_cat_iii`

### 5.4 页面怎么用

- `未整改技术更改单`
- `未整改技术项`
  - `SUM(reform_pending_i_ii)`
- `技术状态-按项目`
  - `SUM(reform_done_i_ii)`
  - `SUM(reform_pending_i_ii)`
  - `SUM(order_unsigned_cat_i + order_unsigned_cat_ii + order_unsigned_cat_iii)`
- `技术状态-按科室`
  - `SUM(reform_pending_i_ii)`

## 6. 风险域血缘

### 6.1 ODS

源表：`ods_risk_info_v2`

总览屏相关原始字段：

- `project_no`
- `dept`
- `risk_submit_time`
- `risk_level`
- `risk_status`

### 6.2 DWD

模型：`biz_dwd_risk_info_v2`

这一层会把风险等级和状态拆成布尔字段：

- `is_high_risk`
- `is_mid_risk`
- `is_low_risk`
- `is_released`

同时产出时间标签：

- `submit_year`
- `submit_month`

### 6.3 DWS

模型：`biz_dws_risk_monthly_v2`

粒度：

- `project_no + dept + submit_month`

总览屏会用到的字段：

- `high_cnt`
- `mid_cnt`
- `low_cnt`
- `open_cnt`

### 6.4 页面怎么用

- `高风险数量`
- `高风险总数`
  - `SUM(high_cnt)`
- `风险-按项目`
  - `SUM(high_cnt)`
  - `SUM(mid_cnt)`
  - `SUM(low_cnt)`
- `风险-按科室`
  - `SUM(high_cnt)`
  - `SUM(mid_cnt)`
  - `SUM(low_cnt)`

## 7. 发现问题时，应该先查哪一层

### 7.1 数字卡有问题

先查 DWS。

原因：

- 数字卡本来就是从 DWS 直接聚合出来的
- 先查 DWS 最容易定位是“页面 SQL 问题”还是“上游模型问题”

### 7.2 图表有问题

先看页面是查 DWD 还是 DWS。

- 进度图：先查 DWD
- 质量/技术状态/风险图：先查 DWS

### 7.3 甘特图有问题

先查 DWD。

因为甘特图直接查的是节点明细，不经过 DWS。

### 7.4 如果 DWS 也不对

再往回查 DWD，看布尔字段和日期字段有没有算错。

### 7.5 如果 DWD 也不对

最后回 ODS，查原始字段是否有脏值、空值、日期格式问题、状态枚举问题。

## 8. 一个最重要的提醒

这张屏现在虽然仓库里有 ADS 模型，但**页面实际没有使用 ADS**。

所以这次校验不要把重点放在：

- `biz_ads_progress_kpi_v2`
- `biz_ads_quality_kpi_v2`
- `biz_ads_tech_state_kpi_v2`
- `biz_ads_risk_kpi_v2`

先把 DWD/DWS 和页面 SQL 对上，才是当前最有效的校验路径。
