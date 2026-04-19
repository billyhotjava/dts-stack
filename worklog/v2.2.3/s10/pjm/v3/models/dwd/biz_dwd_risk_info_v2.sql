{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'risk']) }}

WITH typed AS (
  SELECT
    s.*,
    COALESCE(s.last_update_time, current_date) AS state_as_of_date,
    EXTRACT(YEAR FROM s.risk_submit_date)::int AS submit_year,
    EXTRACT(QUARTER FROM s.risk_submit_date)::int AS submit_quarter
  FROM {{ ref('stg_pm__risk_info_v2') }} s
  WHERE s.project_no IS NOT NULL
    AND s.risk_submit_date IS NOT NULL
)

SELECT
  concat('risk_info:', t.source_row_id) AS risk_id,

  t.source_row_id,
  t.source_table,
  t.source_system,
  t.source_file,
  t.source_sheet_name,
  t.source_batch_id,
  t.source_row_num,
  t.imported_at AS source_imported_at,

  t.project_no,
  t.risk_name,
  t.subsystem,
  t.belonging_unit,
  t.risk_description,
  t.risk_phase,

  t.risk_category_raw,
  t.risk_category,
  rc.risk_category_id,
  COALESCE(rc.cat_technical, false) AS is_risk_technical,
  COALESCE(rc.cat_schedule, false) AS is_risk_schedule,
  COALESCE(rc.cat_cost, false) AS is_risk_cost,
  COALESCE(rc.cat_design, false) AS is_risk_design,
  COALESCE(rc.cat_quality, false) AS is_risk_quality,
  CASE
    WHEN rc.code IS NULL OR COALESCE(rc.cat_other, false) THEN true
    ELSE false
  END AS is_risk_other,

  t.risk_level_raw,
  t.risk_level,
  rl.risk_level_id,
  t.impact_scope,
  t.response_measure,
  t.monthly_control_plan,
  t.weekly_release_plan,
  t.release_plan_synced_raw,
  t.release_plan_synced,
  t.progress_situation,
  t.response_owner,
  t.control_owner,
  t.dept,
  t.risk_status_raw,
  t.risk_status,
  t.remark,
  t.filled_by,

  t.new_plan_count,

  t.risk_submit_week,
  t.final_release_week,
  t.progress_stat_week,
  t.risk_release_week,
  t.last_update_week,

  COALESCE(rl.severity_rank, 0) AS risk_rank,
  COALESCE(rl.is_high, false) AS is_high_risk,
  COALESCE(rl.is_mid, false) AS is_mid_risk,
  COALESCE(rl.is_low, false) AS is_low_risk,

  CASE
    WHEN t.risk_status = '已释放' THEN true
    ELSE false
  END AS is_released,

  t.risk_submit_time_raw,
  t.risk_submit_date,
  t.final_release_date,
  t.progress_stat_date,
  t.risk_release_date,
  t.last_update_time,

  t.submit_year,
  t.submit_quarter,
  t.submit_month,
  t.release_month,
  t.last_update_month,
  t.state_as_of_date,
  to_char(t.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  CASE
    WHEN t.risk_status <> '已释放'
    THEN (t.state_as_of_date - t.risk_submit_date)::int
    ELSE 0
  END AS pending_days,

  now() AS etl_time
FROM typed t
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = t.risk_level
LEFT JOIN {{ ref('dim_risk_category_v2') }} rc
  ON rc.code = t.risk_category
