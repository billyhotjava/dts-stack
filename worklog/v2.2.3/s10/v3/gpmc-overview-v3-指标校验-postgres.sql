-- gpmc-overview-v3 最简 PostgreSQL 校验 SQL
--
-- 用法：
-- 1. 先把下面所有 SQL 里的时间范围改成你要校验的月份范围。
-- 2. 直接在 PostgreSQL 客户端里逐段执行。
-- 3. 如果要限定项目或科室，就把注释掉的过滤条件打开。
--
-- 约定：
-- 1. DWS / DWD 中的月份字段都是 text，格式是 YYYY-MM。
-- 2. 这份文件只写最容易理解的 SQL，不追求和页面 JSON 一模一样。

-- =========================================================
-- 一、顶部 / 底部数字卡
-- =========================================================

-- 01 项目总数
SELECT COUNT(DISTINCT project_no) AS "项目总数"
FROM biz_dws_progress_monthly_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12';

-- 02 进行中项目
SELECT COUNT(DISTINCT project_no) AS "进行中项目"
FROM biz_dws_progress_monthly_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12'
  AND incomplete_cnt > 0;

-- 03 项目完成率
SELECT
  CASE
    WHEN SUM(due_cnt + outside_completed_cnt) = 0 THEN 0
    ELSE ROUND(
      SUM(completed_total_cnt)::numeric
      / SUM(due_cnt + outside_completed_cnt)::numeric
      * 100,
      1
    )
  END AS "项目完成率"
FROM biz_dws_progress_monthly_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12';

-- 04 现存质量问题
SELECT COALESCE(SUM(open_issue_cnt), 0) AS "现存质量问题"
FROM biz_dws_quality_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12';

-- 05 未整改技术项
SELECT COALESCE(SUM(reform_pending_i_ii), 0) AS "未整改技术项"
FROM biz_dws_tech_state_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12';

-- 06 高风险总数
SELECT COALESCE(SUM(high_cnt), 0) AS "高风险总数"
FROM biz_dws_risk_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12';

-- =========================================================
-- 二、图表
-- =========================================================

-- 07 进度-按项目
SELECT
  project_no AS "项目编号",
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS "已完成",
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) AS "未完成",
  SUM(CASE WHEN is_pending_normal THEN 1 ELSE 0 END) AS "正常待完成",
  ROUND(
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric
    / COUNT(*)::numeric
    * 100,
    1
  ) AS "完成率"
FROM biz_dwd_project_node_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12'
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY project_no
ORDER BY SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) DESC;

-- 08 进度-按科室
SELECT
  dept AS "责任科室",
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS "已完成",
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) AS "未完成",
  ROUND(
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric
    / COUNT(*)::numeric
    * 100,
    1
  ) AS "完成率"
FROM biz_dwd_project_node_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12'
  AND dept IS NOT NULL
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY dept
ORDER BY SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) DESC;

-- 09 质量-按项目
SELECT
  project_no AS "项目编号",
  SUM(open_issue_cnt) AS "现存问题"
FROM biz_dws_quality_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY project_no
HAVING SUM(open_issue_cnt) > 0
ORDER BY SUM(open_issue_cnt) DESC;

-- 10 质量-按科室
SELECT
  dept AS "责任科室",
  SUM(open_issue_cnt) AS "现存问题",
  SUM(zero_completed_cnt) AS "已归零"
FROM biz_dws_quality_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  AND dept IS NOT NULL
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY dept
ORDER BY SUM(open_issue_cnt) DESC;

-- 11 技术状态-按项目
SELECT
  project_no AS "项目编号",
  SUM(reform_done_i_ii) AS "已整改",
  SUM(reform_pending_i_ii) AS "未整改",
  SUM(order_unsigned_cat_i + order_unsigned_cat_ii + order_unsigned_cat_iii) AS "未签署"
FROM biz_dws_tech_state_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY project_no
ORDER BY SUM(reform_pending_i_ii) DESC;

-- 12 技术状态-按科室
SELECT
  dept AS "责任科室",
  SUM(reform_pending_i_ii) AS "未整改"
FROM biz_dws_tech_state_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  AND dept IS NOT NULL
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY dept
HAVING SUM(reform_pending_i_ii) > 0
ORDER BY SUM(reform_pending_i_ii) DESC;

-- 13 风险-按项目
SELECT
  project_no AS "项目编号",
  SUM(high_cnt) AS "高风险",
  SUM(mid_cnt) AS "中风险",
  SUM(low_cnt) AS "低风险"
FROM biz_dws_risk_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY project_no
ORDER BY SUM(high_cnt) DESC;

-- 14 风险-按科室
SELECT
  dept AS "责任科室",
  SUM(high_cnt) AS "高风险",
  SUM(mid_cnt) AS "中风险",
  SUM(low_cnt) AS "低风险"
FROM biz_dws_risk_monthly_v2
WHERE period_month >= '2025-01'
  AND period_month <= '2025-12'
  AND dept IS NOT NULL
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY dept
ORDER BY SUM(high_cnt) DESC;

-- =========================================================
-- 三、甘特图
-- =========================================================

-- 15 活跃项目列表
-- 含义：统计周期内至少有 1 个未完成节点的项目
SELECT
  project_no AS "重大项目",
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) AS "未完成节点数"
FROM biz_dwd_project_node_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12'
  -- AND project_no ILIKE '%PRJ-2025-001%'
  -- AND dept = '某责任科室'
GROUP BY project_no
HAVING SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) > 0
ORDER BY project_no;

-- 16 某个项目的甘特明细
-- 用法：把下面的 PRJ-2025-001 改成你要核对的项目编号
SELECT
  project_no AS "重大项目",
  COALESCE(subsystem, '(无子项目)') AS "子项目",
  node_task AS "任务",
  node_type AS "类型",
  to_char(plan_start_date, 'YYYY-MM-DD') AS "计划开始日期",
  to_char(plan_date, 'YYYY-MM-DD') AS "计划完成日期",
  to_char(COALESCE(actual_start_date, plan_start_date), 'YYYY-MM-DD') AS "实际开始日期",
  to_char(actual_date, 'YYYY-MM-DD') AS "实际完成日期",
  is_completed AS "是否完成",
  is_overdue_completed AS "是否超期完成",
  is_incomplete AS "是否未完成",
  COALESCE(delay_days, 0) AS "延期天数",
  risk_level AS "风险等级",
  completion_status AS "完成情况",
  dept AS "责任科室",
  owner AS "责任人",
  project_manager AS "项目经理"
FROM biz_dwd_project_node_v2
WHERE plan_month >= '2025-01'
  AND plan_month <= '2025-12'
  AND project_no = 'PRJ-2025-001'
ORDER BY subsystem NULLS LAST, plan_date;

-- =========================================================
-- 四、筛选器
-- =========================================================

-- 17 科室筛选
-- 注意：页面当前这个下拉不带时间范围
SELECT DISTINCT
  dept AS label,
  dept AS value
FROM biz_dwd_project_node_v2
WHERE dept IS NOT NULL
ORDER BY dept;

-- =========================================================
-- 五、需要继续回查时的明细 SQL
-- =========================================================

-- 18 质量明细回查
SELECT
  project_no,
  issue_name,
  dept,
  issue_date,
  status,
  is_zero_completed
FROM biz_dwd_quality_issue_v2
WHERE to_char(issue_date, 'YYYY-MM') >= '2025-01'
  AND to_char(issue_date, 'YYYY-MM') <= '2025-12'
  -- AND project_no = 'PRJ-2025-001'
ORDER BY issue_date DESC;

-- 19 技术状态明细回查
SELECT
  project_no,
  tech_state_name,
  change_item,
  dept,
  change_submit_time,
  change_category,
  is_signature_completed,
  is_reform_pending,
  is_reform_done
FROM biz_dwd_tech_state_v2
WHERE to_char(change_submit_time, 'YYYY-MM') >= '2025-01'
  AND to_char(change_submit_time, 'YYYY-MM') <= '2025-12'
  -- AND project_no = 'PRJ-2025-001'
ORDER BY change_submit_time DESC;

-- 20 风险明细回查
SELECT
  project_no,
  risk_name,
  dept,
  risk_submit_date,
  risk_level,
  risk_status,
  is_high_risk,
  is_mid_risk,
  is_low_risk,
  is_released
FROM biz_dwd_risk_info_v2
WHERE to_char(risk_submit_date, 'YYYY-MM') >= '2025-01'
  AND to_char(risk_submit_date, 'YYYY-MM') <= '2025-12'
  -- AND project_no = 'PRJ-2025-001'
ORDER BY risk_submit_date DESC;
