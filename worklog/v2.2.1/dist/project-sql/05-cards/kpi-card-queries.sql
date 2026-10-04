-- ============================================================
-- 项目管理大屏卡片 KPI 查询
-- 适用：dts-analytics-webapp 大屏卡片配置
-- 所有查询基于 biadmin 数据库 public schema
-- 参数占位符：{{v_plan_month}} 需替换为实际月份，如 '2026-03'
-- ============================================================

-- ============================================================
-- 一、项目（含一般节点）KPI — 14 个指标
-- 数据来源：biz_ads_project_kpi_overview
-- ============================================================

-- KPI #1-14 总览（一次查询返回全部）
SELECT
  plan_month,
  total_cnt                  AS "项目本周期节点总数",           -- KPI #1
  pending_normal_cnt         AS "正常待完成",                   -- KPI #2
  due_cnt                    AS "已到时间节点总数",             -- KPI #3
  outside_completed_cnt      AS "本周期以外完成节点总数",       -- KPI #4
  incomplete_cnt             AS "未完成总数",                   -- KPI #5
  on_time_cnt                AS "按时完成数",                   -- KPI #6
  overdue_completed_cnt      AS "超期完成数",                   -- KPI #8
  completed_total_cnt        AS "完成总数",                     -- KPI #9
  ROUND(completion_rate * 100, 2)         AS "完成百分比",      -- KPI #10
  ROUND(on_time_rate * 100, 2)           AS "按时完成百分比",   -- KPI #11
  ROUND(overdue_completion_rate * 100, 2) AS "超期完成百分比",  -- KPI #12
  abnormal_pending_cnt                   AS "不正常待变更节点数",    -- KPI #13
  overdue_incomplete_unchanged_cnt       AS "超期未完成未变更节点数" -- KPI #14
FROM biz_ads_project_kpi_overview
WHERE plan_month = '{{v_plan_month}}';

-- KPI #1: 项目本周期节点总数（number-card）
SELECT total_cnt AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #3: 已到时间节点总数（number-card）
SELECT due_cnt AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #5: 未完成总数（number-card）
SELECT incomplete_cnt AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #9: 完成总数（number-card）
SELECT completed_total_cnt AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #10: 完成百分比（number-card）
SELECT ROUND(completion_rate * 100, 2) AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #11: 按时完成百分比（number-card）
SELECT ROUND(on_time_rate * 100, 2) AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #12: 超期完成百分比（number-card）
SELECT ROUND(overdue_completion_rate * 100, 2) AS value FROM biz_ads_project_kpi_overview WHERE plan_month = '{{v_plan_month}}';

-- KPI #10 趋势：完成百分比近6期（combo-chart / line-chart）
SELECT plan_month AS month, ROUND(completion_rate * 100, 1) AS value
FROM biz_ads_project_kpi_overview
WHERE plan_month <= '{{v_plan_month}}'
ORDER BY plan_month DESC LIMIT 6;

-- ============================================================
-- 二、项目（除一般节点）KPI — 6 个指标
-- 数据来源：biz_ads_project_non_general_kpi
-- ============================================================

-- KPI #13-18 总览
SELECT
  plan_month,
  abnormal_pending_cnt               AS "不正常待变更节点数",           -- KPI #13 (非一般)
  overdue_incomplete_unchanged_cnt   AS "超期未完成未变更节点数",       -- KPI #14 (非一般)
  overdue_incomplete_changed_cnt     AS "超期未完成已变更节点数",       -- KPI #15
  overdue_completed_unchanged_cnt    AS "超期已完成未变更节点数",       -- KPI #16
  ROUND(abnormal_rate * 100, 2)      AS "不正常待变更百分比",          -- KPI #17
  ROUND(overdue_rate * 100, 2)       AS "超期百分比"                   -- KPI #18
FROM biz_ads_project_non_general_kpi
WHERE plan_month = '{{v_plan_month}}';

-- KPI #17: 不正常待变更百分比（number-card）
SELECT ROUND(abnormal_rate * 100, 2) AS value FROM biz_ads_project_non_general_kpi WHERE plan_month = '{{v_plan_month}}';

-- KPI #18: 超期百分比（number-card）
SELECT ROUND(overdue_rate * 100, 2) AS value FROM biz_ads_project_non_general_kpi WHERE plan_month = '{{v_plan_month}}';

-- ============================================================
-- 三、截止目前未完成节点 — 5 个指标
-- 数据来源：biz_ads_project_incomplete_risk
-- ============================================================

-- KPI #19-23 总览
SELECT
  plan_month,
  incomplete_high_risk_cnt   AS "未完成高风险节点数",     -- KPI #19
  incomplete_mid_risk_cnt    AS "未完成中风险节点数",     -- KPI #20
  incomplete_milestone_cnt   AS "未完成里程碑节点数",     -- KPI #21
  incomplete_major_cnt       AS "未完成重大节点数",       -- KPI #22
  incomplete_important_cnt   AS "未完成重要节点数"        -- KPI #23
FROM biz_ads_project_incomplete_risk
WHERE plan_month = '{{v_plan_month}}';

-- KPI #19: 未完成高风险节点数（number-card）
SELECT incomplete_high_risk_cnt AS value FROM biz_ads_project_incomplete_risk WHERE plan_month = '{{v_plan_month}}';

-- KPI #20: 未完成中风险节点数（number-card）
SELECT incomplete_mid_risk_cnt AS value FROM biz_ads_project_incomplete_risk WHERE plan_month = '{{v_plan_month}}';

-- ============================================================
-- 四、本周期内节点统计 — 11 个指标
-- 数据来源：biz_ads_project_milestone_kpi
-- ============================================================

-- KPI #24-32 总览
SELECT
  plan_month,
  milestone_on_time_cnt          AS "里程碑按时完成数",         -- KPI #24/#33
  milestone_overdue_completed_cnt AS "里程碑超期完成数",        -- KPI #25/#34
  milestone_pending_cnt          AS "里程碑正常待完成数",       -- KPI #26
  ROUND(milestone_completion_rate * 100, 2) AS "里程碑完成百分比", -- KPI #27
  high_risk_cnt                  AS "高风险节点数",             -- KPI #28
  mid_risk_cnt                   AS "中风险节点数",             -- KPI #29
  milestone_total_cnt            AS "里程碑节点总数",           -- KPI #30
  major_total_cnt                AS "重大节点总数",             -- KPI #31
  important_total_cnt            AS "重要节点总数"              -- KPI #32
FROM biz_ads_project_milestone_kpi
WHERE plan_month = '{{v_plan_month}}';

-- KPI #27: 里程碑完成百分比（number-card）
SELECT ROUND(milestone_completion_rate * 100, 2) AS value FROM biz_ads_project_milestone_kpi WHERE plan_month = '{{v_plan_month}}';

-- KPI #28: 高风险节点数（number-card）
SELECT high_risk_cnt AS value FROM biz_ads_project_milestone_kpi WHERE plan_month = '{{v_plan_month}}';

-- KPI #29: 中风险节点数（number-card）
SELECT mid_risk_cnt AS value FROM biz_ads_project_milestone_kpi WHERE plan_month = '{{v_plan_month}}';

-- ============================================================
-- 五、辅助查询（非 KPI，用于图表）
-- ============================================================

-- 节点类型完成分析（bar-chart）
SELECT
  node_type AS "节点类型",
  SUM(completed_cnt) AS "已完成",
  SUM(incomplete_cnt) AS "未完成"
FROM biz_dws_period_node_type_summary
WHERE plan_month = '{{v_plan_month}}'
  -- AND project_no = '{{v_project_no}}'
GROUP BY node_type
ORDER BY CASE node_type WHEN '一般节点' THEN 1 WHEN '重要节点' THEN 2 WHEN '重大节点' THEN 3 WHEN '里程碑节点' THEN 4 END;

-- 风险分布（pie-chart）
SELECT risk_level AS name, SUM(incomplete_cnt) AS value
FROM biz_dws_period_risk_summary
WHERE plan_month = '{{v_plan_month}}'
  -- AND project_no = '{{v_project_no}}'
GROUP BY risk_level
ORDER BY CASE risk_level WHEN '高' THEN 1 WHEN '中' THEN 2 WHEN '低' THEN 3 END;

-- 责任人任务分布（bar-chart 横向堆叠）
SELECT
  owner AS category,
  SUM(CASE WHEN is_completed AND NOT is_overdue_completed THEN 1 ELSE 0 END) AS "已完成",
  SUM(CASE WHEN NOT is_completed AND NOT is_incomplete THEN 1 ELSE 0 END) AS "进行中",
  SUM(CASE WHEN is_overdue_completed OR is_incomplete THEN 1 ELSE 0 END) AS "超期"
FROM biz_dwd_project_node
WHERE plan_month = '{{v_plan_month}}'
  -- AND project_no = '{{v_project_no}}'
GROUP BY owner
ORDER BY COUNT(*) DESC;

-- 甘特图数据（gantt-chart）
SELECT node_task, node_type, plan_date::text, actual_date::text,
       is_completed, is_overdue_completed, is_incomplete,
       delay_days, risk_level, owner
FROM biz_dwd_project_node
WHERE plan_month = '{{v_plan_month}}'
  -- AND project_no = '{{v_project_no}}'
ORDER BY CASE node_type WHEN '重大节点' THEN 1 WHEN '重要节点' THEN 2 WHEN '一般节点' THEN 3 WHEN '里程碑节点' THEN 4 END, plan_date ASC;

-- 节点明细表（table）
SELECT
  node_task AS "节点任务", node_type AS "节点类型", owner AS "责任人",
  plan_date::text AS "计划日期", actual_date::text AS "实际日期",
  CASE WHEN is_completed AND NOT is_overdue_completed THEN '按时完成'
       WHEN is_completed AND is_overdue_completed THEN '超期完成'
       WHEN is_incomplete THEN '未完成'
       ELSE '进行中' END AS "完成状态",
  delay_days AS "超期天数", risk_level AS "风险等级", risk_content AS "风险内容"
FROM biz_dwd_project_node
WHERE plan_month = '{{v_plan_month}}'
  -- AND project_no = '{{v_project_no}}'
ORDER BY CASE risk_level WHEN '高' THEN 1 WHEN '中' THEN 2 ELSE 3 END, delay_days DESC NULLS LAST;
