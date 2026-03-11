-- ============================================================
-- Sprint-5 指标验证查询
-- 基于 39 条有效测试数据，验证 35 个指标计算正确性
-- 使用时间区间 2026-01-01 ~ 2026-03-31（2026-Q1）
-- ============================================================

\echo '=============================='
\echo '0. 基础数据校验'
\echo '=============================='

-- DWD 有效行数应为 39（过滤掉 1 条说明行）
SELECT
  'dwd_row_count' AS check_name,
  COUNT(*) AS actual,
  39 AS expected,
  CASE WHEN COUNT(*) = 39 THEN 'PASS' ELSE 'FAIL' END AS result
FROM biz_dwd_project_node;

-- DIM 表行数
SELECT 'dim_completion_status' AS check_name, COUNT(*) AS actual, 7 AS expected,
  CASE WHEN COUNT(*) = 7 THEN 'PASS' ELSE 'FAIL' END AS result
FROM dim_completion_status;

SELECT 'dim_node_type' AS check_name, COUNT(*) AS actual, 4 AS expected,
  CASE WHEN COUNT(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result
FROM dim_node_type;

SELECT 'dim_risk_level' AS check_name, COUNT(*) AS actual, 3 AS expected,
  CASE WHEN COUNT(*) = 3 THEN 'PASS' ELSE 'FAIL' END AS result
FROM dim_risk_level;


\echo '=============================='
\echo '1. 项目（含一般节点）指标 - ads_project_kpi_overview'
\echo '=============================='

-- 2026-Q1 整季汇总
SELECT
  plan_year,
  plan_month,
  total_cnt,
  pending_normal_cnt,
  due_cnt,
  outside_completed_cnt,
  incomplete_cnt,
  on_time_cnt,
  overdue_completed_cnt,
  completed_total_cnt,
  completion_rate,
  on_time_rate,
  overdue_completion_rate,
  abnormal_pending_cnt,
  overdue_incomplete_unchanged_cnt,
  overdue_incomplete_changed_cnt
FROM biz_ads_project_kpi_overview
WHERE plan_year = 2026
ORDER BY plan_month;


\echo '=============================='
\echo '2. 项目（除一般节点）指标 - ads_project_non_general_kpi'
\echo '=============================='

SELECT
  plan_year,
  plan_month,
  abnormal_pending_cnt,
  overdue_incomplete_unchanged_cnt,
  overdue_incomplete_changed_cnt,
  overdue_completed_unchanged_cnt,
  abnormal_rate,
  overdue_rate
FROM biz_ads_project_non_general_kpi
WHERE plan_year = 2026
ORDER BY plan_month;


\echo '=============================='
\echo '3. 未完成节点风险分布 - ads_project_incomplete_risk'
\echo '=============================='

SELECT
  plan_year,
  plan_month,
  incomplete_high_risk_cnt,
  incomplete_mid_risk_cnt,
  incomplete_milestone_cnt,
  incomplete_major_cnt,
  incomplete_important_cnt
FROM biz_ads_project_incomplete_risk
WHERE plan_year = 2026
ORDER BY plan_month;


\echo '=============================='
\echo '4. 里程碑与本周期节点 - ads_project_milestone_kpi'
\echo '=============================='

SELECT
  plan_year,
  plan_month,
  milestone_on_time_cnt,
  milestone_overdue_completed_cnt,
  milestone_pending_cnt,
  milestone_incomplete_cnt,
  milestone_completion_rate,
  high_risk_cnt,
  mid_risk_cnt,
  milestone_total_cnt,
  major_total_cnt,
  important_total_cnt
FROM biz_ads_project_milestone_kpi
WHERE plan_year = 2026
ORDER BY plan_month;


\echo '=============================='
\echo '5. 参数化查询验证 - 自定义区间 2026-01-01 ~ 2026-03-31'
\echo '=============================='

-- 综合 KPI（自定义区间）
WITH nodes AS (
  SELECT * FROM biz_dwd_project_node
  WHERE plan_date BETWEEN '2026-01-01' AND '2026-03-31'
),
outside AS (
  SELECT COUNT(*) AS cnt
  FROM biz_dwd_project_node
  WHERE is_completed = true
    AND actual_date BETWEEN '2026-01-01' AND '2026-03-31'
    AND (plan_date < '2026-01-01' OR plan_date > '2026-03-31')
)
SELECT
  COUNT(*)                                                         AS total_cnt,
  SUM(CASE WHEN completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt,
  SUM(CASE WHEN is_on_time THEN 1 ELSE 0 END)                     AS on_time_cnt,
  SUM(CASE WHEN is_overdue_completed THEN 1 ELSE 0 END)           AS overdue_completed_cnt,
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)                   AS completed_cnt,
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END)                  AS incomplete_cnt,
  (SELECT cnt FROM outside)                                        AS outside_completed_cnt
FROM nodes;

-- 按节点类型（自定义区间）
SELECT
  node_type,
  COUNT(*)                                                         AS total_cnt,
  SUM(CASE WHEN is_on_time THEN 1 ELSE 0 END)                     AS on_time_cnt,
  SUM(CASE WHEN is_overdue_completed THEN 1 ELSE 0 END)           AS overdue_completed_cnt,
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END)                  AS incomplete_cnt,
  SUM(CASE WHEN completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt
FROM biz_dwd_project_node
WHERE plan_date BETWEEN '2026-01-01' AND '2026-03-31'
GROUP BY node_type
ORDER BY node_type;


\echo '=============================='
\echo '6. 交叉验证：DWS 汇总一致性'
\echo '=============================='

-- DWS node_summary 合计 vs DWD 直接统计（2026年）
WITH dws_total AS (
  SELECT SUM(total_cnt) AS dws_cnt
  FROM biz_dws_period_node_summary
  WHERE plan_year = 2026
),
dwd_total AS (
  SELECT COUNT(*) AS dwd_cnt
  FROM biz_dwd_project_node
  WHERE plan_year = 2026
)
SELECT
  'dws_vs_dwd_2026' AS check_name,
  dws.dws_cnt,
  dwd.dwd_cnt,
  CASE WHEN dws.dws_cnt = dwd.dwd_cnt THEN 'PASS' ELSE 'FAIL' END AS result
FROM dws_total dws, dwd_total dwd;

-- DWS node_type_summary 合计 vs DWD
WITH dws_total AS (
  SELECT SUM(total_cnt) AS dws_cnt
  FROM biz_dws_period_node_type_summary
  WHERE plan_year = 2026
),
dwd_total AS (
  SELECT COUNT(*) AS dwd_cnt
  FROM biz_dwd_project_node
  WHERE plan_year = 2026
)
SELECT
  'dws_type_vs_dwd_2026' AS check_name,
  dws.dws_cnt,
  dwd.dwd_cnt,
  CASE WHEN dws.dws_cnt = dwd.dwd_cnt THEN 'PASS' ELSE 'FAIL' END AS result
FROM dws_total dws, dwd_total dwd;


\echo '=============================='
\echo '7. 枚举覆盖验证'
\echo '=============================='

-- 确认所有完成情况枚举都有数据
SELECT
  cs.code,
  COUNT(d.node_id) AS cnt
FROM dim_completion_status cs
LEFT JOIN biz_dwd_project_node d ON d.completion_status = cs.code
GROUP BY cs.code, cs.sort_order
ORDER BY cs.sort_order;

-- 确认所有节点类型都有数据
SELECT
  nt.code,
  COUNT(d.node_id) AS cnt
FROM dim_node_type nt
LEFT JOIN biz_dwd_project_node d ON d.node_type = nt.code
GROUP BY nt.code, nt.severity_rank
ORDER BY nt.severity_rank;

-- 确认所有风险等级都有数据
SELECT
  rl.code,
  COUNT(d.node_id) AS cnt
FROM dim_risk_level rl
LEFT JOIN biz_dwd_project_node d ON d.risk_level = rl.code
GROUP BY rl.code, rl.severity_rank
ORDER BY rl.severity_rank;


\echo '=============================='
\echo 'DONE - 请人工核对上述结果'
\echo '=============================='
