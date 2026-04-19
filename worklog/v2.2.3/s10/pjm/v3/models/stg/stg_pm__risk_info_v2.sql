{{ config(materialized='view', tags=['project-management-v3', 'stg', 'risk']) }}

WITH cleaned AS (
  SELECT
    o.id AS source_row_id,
    'ods_risk_info_v2'::text AS source_table,
    COALESCE({{ nullif_placeholder("o.source_system") }}, 'excel') AS source_system,
    {{ nullif_placeholder("o.source_file") }} AS source_file,
    {{ nullif_placeholder("o.sheet_name") }} AS source_sheet_name,
    {{ nullif_placeholder("o.batch_id") }} AS source_batch_id,
    {{ parse_numeric_safe("o.row_num") }}::int AS source_row_num,
    o.import_time AS imported_at,

    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.risk_name") }} AS risk_name,
    {{ nullif_placeholder("o.subsystem") }} AS subsystem,
    {{ nullif_placeholder("o.belonging_unit") }} AS belonging_unit,
    {{ nullif_placeholder("o.risk_description") }} AS risk_description,
    {{ nullif_placeholder("o.risk_phase") }} AS risk_phase,
    {{ nullif_placeholder("o.risk_category") }} AS risk_category_raw,
    {{ nullif_placeholder("o.risk_level") }} AS risk_level_raw,
    {{ nullif_placeholder("o.impact_scope") }} AS impact_scope,
    {{ nullif_placeholder("o.response_measure") }} AS response_measure,
    {{ nullif_placeholder("o.monthly_control_plan") }} AS monthly_control_plan,
    {{ nullif_placeholder("o.weekly_release_plan") }} AS weekly_release_plan,
    {{ nullif_placeholder("o.release_plan_synced") }} AS release_plan_synced_raw,
    {{ nullif_placeholder("o.progress_situation") }} AS progress_situation,
    {{ nullif_placeholder("o.response_owner") }} AS response_owner,
    {{ nullif_placeholder("o.control_owner") }} AS control_owner,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.risk_status") }} AS risk_status_raw,
    {{ nullif_placeholder("o.remark") }} AS remark,
    {{ nullif_placeholder("o.filled_by") }} AS filled_by,
    {{ nullif_placeholder("o.risk_submit_time") }} AS risk_submit_time_raw,

    {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
    {{ parse_numeric_safe("o.risk_submit_week") }}::int AS risk_submit_week,
    {{ parse_numeric_safe("o.final_release_week") }}::int AS final_release_week,
    {{ parse_numeric_safe("o.progress_stat_week") }}::int AS progress_stat_week,
    {{ parse_numeric_safe("o.risk_release_week") }}::int AS risk_release_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week,

    {{ parse_date_safe("o.risk_submit_time") }} AS risk_submit_date,
    {{ parse_date_safe("o.final_release_time") }} AS final_release_date,
    {{ parse_date_safe("o.progress_stat_time") }} AS progress_stat_date,
    {{ parse_date_safe("o.risk_release_date") }} AS risk_release_date,
    {{ parse_date_safe("o.last_update_time") }} AS last_update_time
  FROM {{ source('pm_ods_v2', 'risk_info_v2') }} o
)

SELECT
  c.source_row_id,
  c.source_table,
  c.source_system,
  c.source_file,
  c.source_sheet_name,
  c.source_batch_id,
  c.source_row_num,
  c.imported_at,

  c.project_no,
  c.risk_name,
  c.subsystem,
  c.belonging_unit,
  c.risk_description,
  c.risk_phase,

  c.risk_category_raw,
  CASE
    WHEN c.risk_category_raw IN ('技术', '技术风险') THEN '技术'
    WHEN c.risk_category_raw IN ('进度', '进度风险', '供应链', '管理', '资源') THEN '进度'
    WHEN c.risk_category_raw IN ('成本', '成本风险') THEN '成本'
    WHEN c.risk_category_raw IN ('设计', '设计风险') THEN '设计'
    WHEN c.risk_category_raw IN ('质量', '质量风险') THEN '质量'
    WHEN c.risk_category_raw IS NULL THEN NULL
    ELSE '其他'
  END AS risk_category,

  c.risk_level_raw,
  CASE
    WHEN c.risk_level_raw IN ('高', '高风险') THEN '高'
    WHEN c.risk_level_raw IN ('中', '中风险') THEN '中'
    WHEN c.risk_level_raw IN ('低', '低风险') THEN '低'
    ELSE c.risk_level_raw
  END AS risk_level,

  c.impact_scope,
  c.response_measure,
  c.monthly_control_plan,
  c.weekly_release_plan,

  c.release_plan_synced_raw,
  CASE
    WHEN upper(COALESCE(c.release_plan_synced_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已同步') THEN '是'
    WHEN upper(COALESCE(c.release_plan_synced_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未同步') THEN '否'
    ELSE c.release_plan_synced_raw
  END AS release_plan_synced,

  c.progress_situation,
  c.response_owner,
  c.control_owner,
  c.dept,

  c.risk_status_raw,
  CASE
    WHEN c.risk_status_raw = '已释放' THEN '已释放'
    WHEN c.risk_status_raw IS NULL THEN NULL
    ELSE '未释放'
  END AS risk_status,

  c.remark,
  c.filled_by,
  c.risk_submit_time_raw,
  c.new_plan_count,

  c.risk_submit_week,
  c.final_release_week,
  c.progress_stat_week,
  c.risk_release_week,
  c.last_update_week,

  c.risk_submit_date,
  c.final_release_date,
  c.progress_stat_date,
  c.risk_release_date,
  c.last_update_time,

  to_char(c.risk_submit_date, 'YYYY-MM') AS submit_month,
  to_char(c.risk_release_date, 'YYYY-MM') AS release_month,
  to_char(c.last_update_time, 'YYYY-MM') AS last_update_month
FROM cleaned c
