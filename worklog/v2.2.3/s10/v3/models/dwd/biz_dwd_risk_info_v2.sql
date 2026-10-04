{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'risk']) }}

WITH cleaned AS (
  SELECT
    {{ nullif_placeholder("o.project_no") }}                 AS project_no,
    {{ nullif_placeholder("o.risk_name") }}                  AS risk_name,
    {{ nullif_placeholder("o.subsystem") }}                  AS subsystem,
    {{ nullif_placeholder("o.belonging_unit") }}             AS belonging_unit,
    {{ nullif_placeholder("o.risk_description") }}           AS risk_description,
    {{ nullif_placeholder("o.risk_phase") }}                 AS risk_phase,
    {{ nullif_placeholder("o.risk_category") }}              AS risk_category,
    {{ nullif_placeholder("o.risk_level") }}                 AS risk_level,
    {{ nullif_placeholder("o.impact_scope") }}               AS impact_scope,
    {{ nullif_placeholder("o.response_measure") }}           AS response_measure,
    {{ nullif_placeholder("o.monthly_control_plan") }}       AS monthly_control_plan,
    {{ nullif_placeholder("o.weekly_release_plan") }}        AS weekly_release_plan,
    {{ nullif_placeholder("o.release_plan_synced") }}        AS release_plan_synced,
    {{ nullif_placeholder("o.progress_situation") }}         AS progress_situation,
    {{ nullif_placeholder("o.response_owner") }}             AS response_owner,
    {{ nullif_placeholder("o.control_owner") }}              AS control_owner,
    {{ nullif_placeholder("o.dept") }}                       AS dept,
    {{ nullif_placeholder("o.risk_status") }}                AS risk_status,
    {{ nullif_placeholder("o.remark") }}                     AS remark,
    {{ nullif_placeholder("o.filled_by") }}                  AS filled_by,
    {{ nullif_placeholder("o.risk_submit_time") }}           AS risk_submit_time_raw,
    {{ parse_numeric_safe("o.new_plan_count") }}::int        AS new_plan_count,
    {{ parse_numeric_safe("o.risk_submit_week") }}::int      AS risk_submit_week,
    {{ parse_numeric_safe("o.final_release_week") }}::int    AS final_release_week,
    {{ parse_numeric_safe("o.progress_stat_week") }}::int    AS progress_stat_week,
    {{ parse_numeric_safe("o.risk_release_week") }}::int     AS risk_release_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int      AS last_update_week,
    {{ parse_date_safe("o.risk_submit_time") }}              AS risk_submit_date,
    {{ parse_date_safe("o.final_release_time") }}            AS final_release_date,
    {{ parse_date_safe("o.progress_stat_time") }}            AS progress_stat_date,
    {{ parse_date_safe("o.risk_release_date") }}             AS risk_release_date,
    {{ parse_date_safe("o.last_update_time") }}              AS last_update_time
  FROM {{ source('pm_ods_v2', 'risk_info_v2') }} o
),
typed AS (
  SELECT
    c.*,
    CASE
      WHEN btrim(COALESCE(c.risk_status, '')) = '已释放' THEN true
      ELSE false
    END                                                     AS is_released,
    EXTRACT(YEAR FROM c.risk_submit_date)::int              AS submit_year,
    EXTRACT(QUARTER FROM c.risk_submit_date)::int           AS submit_quarter,
    to_char(c.risk_submit_date, 'YYYY-MM')                  AS submit_month
  FROM cleaned c
  WHERE c.project_no IS NOT NULL
    AND c.risk_submit_date IS NOT NULL
)

SELECT
  -- === 主键 ===
  md5(
    COALESCE(t.project_no, '') || '|' ||
    COALESCE(t.risk_name, '') || '|' ||
    COALESCE(t.risk_submit_time_raw, '')
  ) AS risk_id,

  -- === 原始业务字段 ===
  t.project_no,
  t.risk_name,
  t.subsystem,
  t.belonging_unit,
  t.risk_description,
  t.risk_phase,
  t.risk_category,
  COALESCE(rc.cat_technical, false)                         AS is_risk_technical,
  COALESCE(rc.cat_schedule, false)                          AS is_risk_schedule,
  COALESCE(rc.cat_cost, false)                              AS is_risk_cost,
  COALESCE(rc.cat_design, false)                            AS is_risk_design,
  COALESCE(rc.cat_quality, false)                           AS is_risk_quality,
  CASE
    WHEN rc.code IS NULL OR COALESCE(rc.cat_other, false)
    THEN true ELSE false
  END                                                       AS is_risk_other,
  t.risk_level,
  t.impact_scope,
  t.response_measure,
  t.monthly_control_plan,
  t.weekly_release_plan,
  t.release_plan_synced,
  t.progress_situation,
  t.response_owner,
  t.control_owner,
  t.dept,
  t.risk_status,
  t.remark,
  t.filled_by,

  -- === 数值字段 ===
  t.new_plan_count,

  -- === 周数字段 ===
  t.risk_submit_week,
  t.final_release_week,
  t.progress_stat_week,
  t.risk_release_week,
  t.last_update_week,

  -- === 风险等级（字典派生） ===
  COALESCE(rl.severity_rank, 0)                             AS risk_rank,
  COALESCE(rl.is_high, false)                               AS is_high_risk,
  COALESCE(rl.is_mid, false)                                AS is_mid_risk,
  COALESCE(rl.is_low, false)                                AS is_low_risk,

  -- === 释放标志 ===
  t.is_released,

  -- === 日期解析 ===
  t.risk_submit_date,
  t.final_release_date,
  t.progress_stat_date,
  t.risk_release_date,
  t.last_update_time,

  -- === 时间维度标签 ===
  t.submit_year,
  t.submit_quarter,
  t.submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN NOT t.is_released
    THEN (current_date - t.risk_submit_date)::int
    ELSE 0
  END AS pending_days,

  'ods_risk_info_v2'::text AS source_table,
  now() AS etl_time

FROM typed t
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = t.risk_level
LEFT JOIN {{ ref('dim_risk_category_v2') }} rc
  ON rc.code = t.risk_category
