{{ config(materialized='view', tags=['project-management-v3', 'stg', 'quality']) }}

WITH cleaned AS (
  SELECT
    o.id AS source_row_id,
    'ods_quality_issue_v2'::text AS source_table,
    COALESCE({{ nullif_placeholder("o.source_system") }}, 'excel') AS source_system,
    {{ nullif_placeholder("o.source_file") }} AS source_file,
    {{ nullif_placeholder("o.sheet_name") }} AS source_sheet_name,
    {{ nullif_placeholder("o.batch_id") }} AS source_batch_id,
    {{ parse_numeric_safe("o.row_num") }}::int AS source_row_num,
    o.import_time AS imported_at,

    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.subsystem") }} AS subsystem,
    {{ nullif_placeholder("o.issue_name") }} AS issue_name,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.team_leader") }} AS team_leader,
    {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
    {{ nullif_placeholder("o.issue_summary") }} AS issue_summary,
    {{ nullif_placeholder("o.issue_category") }} AS issue_category_raw,
    {{ nullif_placeholder("o.zero_plan") }} AS zero_plan,
    {{ nullif_placeholder("o.zero_plan_synced") }} AS zero_plan_synced_raw,
    {{ nullif_placeholder("o.status") }} AS status_raw,
    {{ nullif_placeholder("o.current_progress") }} AS current_progress,
    {{ nullif_placeholder("o.project_manager") }} AS project_manager,
    {{ nullif_placeholder("o.filled_by") }} AS filled_by,
    {{ nullif_placeholder("o.issue_date") }} AS issue_date_raw,

    {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
    {{ parse_date_safe("o.issue_date") }} AS issue_date,
    {{ parse_date_safe("o.zero_complete_date") }} AS zero_complete_date,
    {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
    {{ parse_numeric_safe("o.issue_week") }}::int AS issue_week,
    {{ parse_numeric_safe("o.zero_complete_week") }}::int AS zero_complete_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
  FROM {{ source('pm_ods_v2', 'quality_issue_v2') }} o
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
  c.subsystem,
  c.issue_name,
  c.dept,
  c.team_leader,
  c.dept_leader,
  c.issue_summary,

  c.issue_category_raw,
  CASE
    WHEN c.issue_category_raw IN ('外协外购', '外协') THEN '外协'
    WHEN c.issue_category_raw IN ('设计', '工艺', '管理', '元器件', '操作', '软件', '环境', '其他') THEN c.issue_category_raw
    ELSE c.issue_category_raw
  END AS issue_category,

  c.zero_plan,
  c.zero_plan_synced_raw,
  CASE
    WHEN upper(COALESCE(c.zero_plan_synced_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已同步') THEN '是'
    WHEN upper(COALESCE(c.zero_plan_synced_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未同步') THEN '否'
    ELSE c.zero_plan_synced_raw
  END AS zero_plan_synced,

  c.status_raw,
  CASE
    WHEN c.status_raw IN ('处理中', '进行中', '未完成', '未闭环', '待归零', '未完成归零') THEN '未完成归零'
    WHEN c.status_raw = '已完成技术归零' THEN '已完成技术归零'
    WHEN c.status_raw = '已完成管理归零' THEN '已完成管理归零'
    WHEN c.status_raw IN ('已归零', '已闭环', '已完成', '已完成技术和管理归零') THEN '已完成技术和管理归零'
    ELSE c.status_raw
  END AS status,

  c.current_progress,
  c.project_manager,
  c.filled_by,
  c.new_plan_count,

  c.issue_date_raw,
  c.issue_date,
  c.zero_complete_date,
  c.last_update_time,
  c.issue_week,
  c.zero_complete_week,
  c.last_update_week,

  CASE
    WHEN btrim(COALESCE(c.zero_plan, '')) IN ('', '无') THEN false
    ELSE true
  END AS has_zero_plan,

  to_char(c.issue_date, 'YYYY-MM') AS issue_month,
  to_char(c.zero_complete_date, 'YYYY-MM') AS zero_complete_month,
  to_char(c.last_update_time, 'YYYY-MM') AS last_update_month
FROM cleaned c
