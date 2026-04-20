

WITH base AS (
  SELECT *
  FROM "biadmin"."public"."biz_dwd_project_fund"
)

SELECT
  'all_projects'::text AS summary_scope,
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
  coalesce(sum(remaining_fund), 0)::numeric(15,2) AS sum_remaining_fund,
  sum(CASE WHEN is_direct_rate_over_100 THEN 1 ELSE 0 END) AS direct_rate_over_100_count,
  sum(CASE WHEN is_total_rate_over_100 THEN 1 ELSE 0 END) AS total_rate_over_100_count,
  now() AS etl_time
FROM base