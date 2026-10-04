{{ config(materialized='table', tags=['finance', 'biz', 'dws', 'project-fund']) }}

-- 项目经费主题汇总表
-- 产出多 scope: 全部 / 重大项目 / 非重大项目 / 按项目状态

WITH base AS (
  SELECT * FROM {{ ref('biz_dwd_project_fund') }}
),
projects_labeled AS (
  SELECT
    b.*,
    CASE
      WHEN b.is_major_project IS true THEN 'major'
      WHEN b.is_major_project IS false THEN 'non_major'
      ELSE 'unknown'
    END AS major_key,
    CASE
      WHEN b.project_status_code = 'IN_PROGRESS'                  THEN 'in_progress'
      WHEN b.project_status_code = 'PENDING_EXPENSE'              THEN 'pending_expense'
      WHEN b.project_status_code = 'COMPLETED_PENDING_COLLECTION' THEN 'pending_collection'
      WHEN b.project_status_code = 'COMPLETED_AUDIT'              THEN 'audited'
      ELSE 'unknown'
    END AS status_key
  FROM base b
),
agg_all AS (
  SELECT
    'all_projects'::text AS summary_scope,
    'all'::text AS scope_key,
    '全部项目'::text AS scope_label,
    count(DISTINCT project_id) AS project_count,
    count(*) AS record_count,
    min(cycle_start_date) AS earliest_cycle_start_date,
    max(cycle_end_date) AS latest_cycle_end_date,
    round(avg(cycle_month_span)::numeric, 1) AS avg_cycle_month_span,
    coalesce(sum(total_fund), 0)::numeric(15,2) AS sum_total_fund,
    coalesce(sum(direct_ctrl), 0)::numeric(15,2) AS sum_direct_ctrl,
    coalesce(sum(reserve_indirect), 0)::numeric(15,2) AS sum_reserve_indirect,
    coalesce(sum(direct_spent), 0)::numeric(15,2) AS sum_direct_spent,
    coalesce(sum(indirect_spent), 0)::numeric(15,2) AS sum_indirect_spent,
    coalesce(sum(total_spent), 0)::numeric(15,2) AS sum_total_spent,
    coalesce(sum(CASE WHEN remaining_fund > 0 THEN remaining_fund ELSE 0 END), 0)::numeric(15,2) AS sum_remaining_fund,
    coalesce(sum(CASE WHEN remaining_fund < 0 THEN abs(remaining_fund) ELSE 0 END), 0)::numeric(15,2) AS sum_overspend_amount,
    coalesce(sum(received_fund), 0)::numeric(15,2) AS sum_received_fund,
    coalesce(sum(receivable_fund), 0)::numeric(15,2) AS sum_receivable_fund,
    count(DISTINCT CASE WHEN is_direct_rate_over_100 THEN project_id END) AS direct_rate_over_100_count,
    count(DISTINCT CASE WHEN is_total_rate_over_100 THEN project_id END) AS total_rate_over_100_count,
    count(DISTINCT CASE WHEN is_major_project THEN project_id END) AS major_project_count,
    count(DISTINCT CASE WHEN project_is_completed THEN project_id END) AS completed_project_count
  FROM projects_labeled
),
agg_by_major AS (
  SELECT
    'by_major'::text AS summary_scope,
    major_key AS scope_key,
    CASE
      WHEN major_key = 'major' THEN '重大项目'
      WHEN major_key = 'non_major' THEN '非重大项目'
      ELSE '未知是否重大项目'
    END AS scope_label,
    count(DISTINCT project_id) AS project_count,
    count(*) AS record_count,
    min(cycle_start_date) AS earliest_cycle_start_date,
    max(cycle_end_date) AS latest_cycle_end_date,
    round(avg(cycle_month_span)::numeric, 1) AS avg_cycle_month_span,
    coalesce(sum(total_fund), 0)::numeric(15,2) AS sum_total_fund,
    coalesce(sum(direct_ctrl), 0)::numeric(15,2) AS sum_direct_ctrl,
    coalesce(sum(reserve_indirect), 0)::numeric(15,2) AS sum_reserve_indirect,
    coalesce(sum(direct_spent), 0)::numeric(15,2) AS sum_direct_spent,
    coalesce(sum(indirect_spent), 0)::numeric(15,2) AS sum_indirect_spent,
    coalesce(sum(total_spent), 0)::numeric(15,2) AS sum_total_spent,
    coalesce(sum(CASE WHEN remaining_fund > 0 THEN remaining_fund ELSE 0 END), 0)::numeric(15,2) AS sum_remaining_fund,
    coalesce(sum(CASE WHEN remaining_fund < 0 THEN abs(remaining_fund) ELSE 0 END), 0)::numeric(15,2) AS sum_overspend_amount,
    coalesce(sum(received_fund), 0)::numeric(15,2) AS sum_received_fund,
    coalesce(sum(receivable_fund), 0)::numeric(15,2) AS sum_receivable_fund,
    count(DISTINCT CASE WHEN is_direct_rate_over_100 THEN project_id END) AS direct_rate_over_100_count,
    count(DISTINCT CASE WHEN is_total_rate_over_100 THEN project_id END) AS total_rate_over_100_count,
    count(DISTINCT CASE WHEN is_major_project THEN project_id END) AS major_project_count,
    count(DISTINCT CASE WHEN project_is_completed THEN project_id END) AS completed_project_count
  FROM projects_labeled
  GROUP BY major_key
),
agg_by_status AS (
  SELECT
    'by_status'::text AS summary_scope,
    status_key AS scope_key,
    CASE status_key
      WHEN 'in_progress' THEN '在研'
      WHEN 'pending_expense' THEN '支出待处理'
      WHEN 'pending_collection' THEN '已完成待收款'
      WHEN 'audited' THEN '已完成审计'
      ELSE '未知状态'
    END AS scope_label,
    count(DISTINCT project_id) AS project_count,
    count(*) AS record_count,
    min(cycle_start_date) AS earliest_cycle_start_date,
    max(cycle_end_date) AS latest_cycle_end_date,
    round(avg(cycle_month_span)::numeric, 1) AS avg_cycle_month_span,
    coalesce(sum(total_fund), 0)::numeric(15,2) AS sum_total_fund,
    coalesce(sum(direct_ctrl), 0)::numeric(15,2) AS sum_direct_ctrl,
    coalesce(sum(reserve_indirect), 0)::numeric(15,2) AS sum_reserve_indirect,
    coalesce(sum(direct_spent), 0)::numeric(15,2) AS sum_direct_spent,
    coalesce(sum(indirect_spent), 0)::numeric(15,2) AS sum_indirect_spent,
    coalesce(sum(total_spent), 0)::numeric(15,2) AS sum_total_spent,
    coalesce(sum(CASE WHEN remaining_fund > 0 THEN remaining_fund ELSE 0 END), 0)::numeric(15,2) AS sum_remaining_fund,
    coalesce(sum(CASE WHEN remaining_fund < 0 THEN abs(remaining_fund) ELSE 0 END), 0)::numeric(15,2) AS sum_overspend_amount,
    coalesce(sum(received_fund), 0)::numeric(15,2) AS sum_received_fund,
    coalesce(sum(receivable_fund), 0)::numeric(15,2) AS sum_receivable_fund,
    count(DISTINCT CASE WHEN is_direct_rate_over_100 THEN project_id END) AS direct_rate_over_100_count,
    count(DISTINCT CASE WHEN is_total_rate_over_100 THEN project_id END) AS total_rate_over_100_count,
    count(DISTINCT CASE WHEN is_major_project THEN project_id END) AS major_project_count,
    count(DISTINCT CASE WHEN project_is_completed THEN project_id END) AS completed_project_count
  FROM projects_labeled
  GROUP BY status_key
)

SELECT *, now() AS etl_time FROM agg_all
UNION ALL
SELECT *, now() AS etl_time FROM agg_by_major
UNION ALL
SELECT *, now() AS etl_time FROM agg_by_status
