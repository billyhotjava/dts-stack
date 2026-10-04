

WITH stg AS (
  SELECT * FROM "biadmin"."public"."stg_pm__risk_info_v2"
  WHERE project_no IS NOT NULL
    AND risk_submit_date IS NOT NULL
),
normalized AS (
  SELECT
    s.*,
    CASE
      WHEN s.risk_category_raw IS NULL THEN NULL
      ELSE COALESCE(rca.canonical_code, '其他')
    END AS risk_category,
    rla.canonical_code AS risk_level,
    ba.canonical_code AS release_plan_synced,
    CASE
      WHEN s.risk_status_raw IS NULL THEN NULL
      ELSE COALESCE(rsa.canonical_code, '未释放')
    END AS risk_status
  FROM stg s
  LEFT JOIN "biadmin"."public"."dim_risk_category_alias" rca
    ON rca.alias_raw = s.risk_category_raw
  LEFT JOIN "biadmin"."public"."dim_risk_level_alias" rla
    ON rla.alias_raw = s.risk_level_raw
  LEFT JOIN "biadmin"."public"."dim_boolean_alias" ba
    ON ba.alias_raw = upper(s.release_plan_synced_raw)
  LEFT JOIN "biadmin"."public"."dim_risk_status_alias" rsa
    ON rsa.alias_raw = s.risk_status_raw
),
derived AS (
  SELECT
    n.*,
    to_char(n.risk_submit_date, 'YYYY-MM') AS submit_month,
    to_char(n.risk_release_date, 'YYYY-MM') AS release_month,
    to_char(n.last_update_time, 'YYYY-MM') AS last_update_month,
    EXTRACT(YEAR FROM n.risk_submit_date)::int AS submit_year,
    EXTRACT(QUARTER FROM n.risk_submit_date)::int AS submit_quarter,
    COALESCE(n.last_update_time, current_date) AS state_as_of_date
  FROM normalized n
)

SELECT
  concat('risk_info:', d.source_row_id) AS risk_id,

  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,

  d.project_no,
  d.risk_name,
  d.subsystem,
  d.belonging_unit,
  d.risk_description,
  d.risk_phase,

  d.risk_category_raw,
  d.risk_category,
  rc.risk_category_id,
  rc.label AS risk_category_label,
  COALESCE(rc.cat_technical, false) AS is_risk_technical,
  COALESCE(rc.cat_schedule, false) AS is_risk_schedule,
  COALESCE(rc.cat_cost, false) AS is_risk_cost,
  COALESCE(rc.cat_design, false) AS is_risk_design,
  COALESCE(rc.cat_quality, false) AS is_risk_quality,
  CASE
    WHEN rc.code IS NULL OR COALESCE(rc.cat_other, false) THEN true
    ELSE false
  END AS is_risk_other,

  d.risk_level_raw,
  d.risk_level,
  rl.risk_level_id,
  rl.label AS risk_level_label,
  COALESCE(rl.severity_rank, 0) AS risk_rank,
  COALESCE(rl.is_high, false) AS is_high_risk,
  COALESCE(rl.is_mid, false) AS is_mid_risk,
  COALESCE(rl.is_low, false) AS is_low_risk,

  d.impact_scope,
  d.response_measure,
  d.monthly_control_plan,
  d.weekly_release_plan,

  d.release_plan_synced_raw,
  COALESCE(d.release_plan_synced, d.release_plan_synced_raw) AS release_plan_synced,

  d.progress_situation,
  d.response_owner,
  d.control_owner,
  d.dept,

  d.risk_status_raw,
  d.risk_status,
  CASE WHEN d.risk_status = '已释放' THEN true ELSE false END AS is_released,

  d.remark,
  d.filled_by,
  d.new_plan_count,

  d.risk_submit_week,
  d.final_release_week,
  d.progress_stat_week,
  d.risk_release_week,
  d.last_update_week,

  d.risk_submit_time_raw,
  d.risk_submit_date,
  d.final_release_date,
  d.progress_stat_date,
  d.risk_release_date,
  d.last_update_time,

  d.submit_year,
  d.submit_quarter,
  d.submit_month,
  d.release_month,
  d.last_update_month,
  d.state_as_of_date,
  to_char(d.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  CASE
    WHEN d.risk_status <> '已释放'
      THEN (d.state_as_of_date - d.risk_submit_date)::int
    ELSE 0
  END AS pending_days,

  now() AS etl_time
FROM derived d
LEFT JOIN "biadmin"."public"."dim_risk_level_v2" rl
  ON rl.code = d.risk_level
LEFT JOIN "biadmin"."public"."dim_risk_category_v2" rc
  ON rc.code = d.risk_category