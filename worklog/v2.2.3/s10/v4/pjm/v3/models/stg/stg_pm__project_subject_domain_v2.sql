{{ config(materialized='view', tags=['project-management-v3', 'stg', 'progress']) }}

WITH cleaned AS (
  SELECT
    o.id AS source_row_id,
    'ods_project_subject_domain_v2'::text AS source_table,
    COALESCE({{ nullif_placeholder("o.source_system") }}, 'excel') AS source_system,
    {{ nullif_placeholder("o.source_file") }} AS source_file,
    {{ nullif_placeholder("o.sheet_name") }} AS source_sheet_name,
    {{ nullif_placeholder("o.batch_id") }} AS source_batch_id,
    {{ parse_numeric_safe("o.row_num") }}::int AS source_row_num,
    o.import_time AS imported_at,

    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.subsystem") }} AS subsystem,
    {{ nullif_placeholder("o.node_task") }} AS node_task,
    {{ nullif_placeholder("o.owner") }} AS owner,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
    {{ nullif_placeholder("o.collab_dept") }} AS collab_dept,
    {{ nullif_placeholder("o.supervisor_dept") }} AS supervisor_dept,
    {{ nullif_placeholder("o.incomplete_reason") }} AS incomplete_reason,
    {{ nullif_placeholder("o.risk_content") }} AS risk_content,
    {{ nullif_placeholder("o.delay_impact") }} AS delay_impact,
    {{ nullif_placeholder("o.institute_leader") }} AS institute_leader,
    {{ nullif_placeholder("o.project_manager") }} AS project_manager,
    {{ nullif_placeholder("o.filled_by") }} AS filled_by,
    {{ nullif_placeholder("o.highlight") }} AS highlight,
    {{ nullif_placeholder("o.deliverable") }} AS deliverable,
    {{ nullif_placeholder("o.completion_status") }} AS completion_status_raw,
    {{ nullif_placeholder("o.node_type") }} AS node_type_raw,
    {{ nullif_placeholder("o.risk_level") }} AS risk_level_raw,
    {{ nullif_placeholder("o.source") }} AS data_source,
    {{ nullif_placeholder("o.delay_applied") }} AS delay_applied_raw,
    {{ nullif_placeholder("o.plan_date") }} AS plan_date_raw,

    {{ parse_date_safe("o.plan_start_date") }} AS plan_start_date,
    {{ parse_date_safe("o.plan_date") }} AS plan_date,
    {{ parse_date_safe("o.actual_start_date") }} AS actual_start_date,
    {{ parse_date_safe("o.actual_date") }} AS actual_date,
    {{ parse_date_safe("o.delay_expected_date") }} AS delay_expected_date,
    {{ parse_date_safe("o.original_plan_date") }} AS original_plan_date,
    {{ parse_date_safe("o.last_update_time") }} AS last_update_time,

    {{ parse_numeric_safe("o.plan_week") }}::int AS plan_week,
    {{ parse_numeric_safe("o.actual_week") }}::int AS actual_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
  FROM {{ source('pm_ods_v2', 'project_subject_domain_v2') }} o
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
  c.node_task,
  c.owner,
  c.dept,
  c.dept_leader,
  c.collab_dept,
  c.supervisor_dept,
  c.incomplete_reason,
  c.risk_content,
  c.delay_impact,
  c.institute_leader,
  c.project_manager,
  c.filled_by,
  c.highlight,
  c.deliverable,

  c.completion_status_raw,
  c.completion_status_raw AS completion_status,

  c.node_type_raw,
  CASE
    WHEN c.node_type_raw IN ('一般', '一般节点') THEN '一般节点'
    WHEN c.node_type_raw IN ('重要', '重要节点') THEN '重要节点'
    WHEN c.node_type_raw IN ('重大', '重大节点') THEN '重大节点'
    WHEN c.node_type_raw IN ('里程碑', '里程碑节点') THEN '里程碑节点'
    ELSE c.node_type_raw
  END AS node_type,

  c.risk_level_raw,
  CASE
    WHEN c.risk_level_raw IN ('高', '高风险') THEN '高'
    WHEN c.risk_level_raw IN ('中', '中风险') THEN '中'
    WHEN c.risk_level_raw IN ('低', '低风险') THEN '低'
    ELSE c.risk_level_raw
  END AS risk_level,

  c.data_source,
  c.delay_applied_raw,
  CASE
    WHEN upper(COALESCE(c.delay_applied_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已提交') THEN '是'
    WHEN upper(COALESCE(c.delay_applied_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未提交') THEN '否'
    ELSE c.delay_applied_raw
  END AS delay_applied,

  c.plan_date_raw,
  c.plan_start_date,
  c.plan_date,
  c.actual_start_date,
  c.actual_date,
  c.delay_expected_date,
  c.original_plan_date,
  c.last_update_time,

  c.plan_week,
  c.actual_week,
  c.last_update_week,

  to_char(c.plan_date, 'YYYY-MM') AS plan_month,
  to_char(c.actual_date, 'YYYY-MM') AS actual_month,
  to_char(c.last_update_time, 'YYYY-MM') AS last_update_month
FROM cleaned c
