{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'risk']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.risk_submit_time), '')
  ) AS risk_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}              AS project_no,
  {{ nullif_placeholder("o.risk_name") }}               AS risk_name,
  {{ nullif_placeholder("o.subsystem") }}               AS subsystem,
  {{ nullif_placeholder("o.belonging_unit") }}          AS belonging_unit,
  {{ nullif_placeholder("o.risk_description") }}        AS risk_description,
  {{ nullif_placeholder("o.risk_phase") }}              AS risk_phase,
  {{ nullif_placeholder("o.risk_category") }}           AS risk_category,
  {{ nullif_placeholder("o.risk_level") }}              AS risk_level,
  {{ nullif_placeholder("o.impact_scope") }}            AS impact_scope,
  {{ nullif_placeholder("o.response_measure") }}        AS response_measure,
  {{ nullif_placeholder("o.monthly_control_plan") }}    AS monthly_control_plan,
  {{ nullif_placeholder("o.weekly_release_plan") }}     AS weekly_release_plan,
  {{ nullif_placeholder("o.release_plan_synced") }}     AS release_plan_synced,
  {{ nullif_placeholder("o.progress_situation") }}      AS progress_situation,
  {{ nullif_placeholder("o.response_owner") }}          AS response_owner,
  {{ nullif_placeholder("o.control_owner") }}           AS control_owner,
  {{ nullif_placeholder("o.dept") }}                    AS dept,
  {{ nullif_placeholder("o.risk_status") }}             AS risk_status,
  {{ nullif_placeholder("o.remark") }}                  AS remark,
  {{ nullif_placeholder("o.filled_by") }}               AS filled_by,

  -- === 数值字段 ===
  {{ parse_numeric_safe("o.new_plan_count") }}::int     AS new_plan_count,

  -- === 周数字段 ===
  {{ parse_numeric_safe("o.risk_submit_week") }}::int   AS risk_submit_week,
  {{ parse_numeric_safe("o.final_release_week") }}::int AS final_release_week,
  {{ parse_numeric_safe("o.progress_stat_week") }}::int AS progress_stat_week,
  {{ parse_numeric_safe("o.risk_release_week") }}::int  AS risk_release_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int   AS last_update_week,

  -- === 风险等级（字典派生） ===
  COALESCE(rl.severity_rank, 0)                         AS risk_rank,
  COALESCE(rl.is_high, false)                           AS is_high_risk,
  COALESCE(rl.is_mid,  false)                           AS is_mid_risk,
  COALESCE(rl.is_low,  false)                           AS is_low_risk,

  -- === 释放标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.risk_status") }}, '')) = '已释放' THEN true
    ELSE false
  END AS is_released,

  -- === 日期解析 ===
  {{ parse_date_safe("o.risk_submit_time") }}           AS risk_submit_date,
  {{ parse_date_safe("o.final_release_time") }}         AS final_release_date,
  {{ parse_date_safe("o.progress_stat_time") }}         AS progress_stat_date,
  {{ parse_date_safe("o.risk_release_date") }}          AS risk_release_date,
  {{ parse_date_safe("o.last_update_time") }}           AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.risk_submit_time") }})::int     AS submit_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.risk_submit_time") }})::int  AS submit_quarter,
  to_char({{ parse_date_safe("o.risk_submit_time") }}, 'YYYY-MM')         AS submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN {{ parse_date_safe("o.risk_submit_time") }} IS NOT NULL
     AND btrim(COALESCE({{ nullif_placeholder("o.risk_status") }}, '')) != '已释放'
    THEN (current_date - {{ parse_date_safe("o.risk_submit_time") }})::int
    ELSE 0
  END AS pending_days,

  'ods_risk_info_v2'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods_v2', 'risk_info_v2') }} o
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = {{ nullif_placeholder("o.risk_level") }}
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != ''
