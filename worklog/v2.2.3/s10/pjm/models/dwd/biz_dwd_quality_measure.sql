{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'quality']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.measure_title), '') || '|' ||
    COALESCE(btrim(o.follow_up_date), '')
  ) AS measure_id,

  -- === 原始业务字段（质量问题部分） ===
  {{ nullif_placeholder("o.project_no") }}           AS project_no,
  {{ nullif_placeholder("o.subsystem") }}            AS subsystem,
  {{ nullif_placeholder("o.issue_name") }}           AS issue_name,
  {{ nullif_placeholder("o.dept") }}                 AS dept,
  {{ nullif_placeholder("o.team_leader") }}          AS team_leader,
  {{ nullif_placeholder("o.dept_leader") }}          AS dept_leader,
  {{ nullif_placeholder("o.issue_summary") }}        AS issue_summary,
  {{ nullif_placeholder("o.issue_category") }}       AS issue_category,
  {{ nullif_placeholder("o.zero_plan") }}            AS zero_plan,
  {{ nullif_placeholder("o.zero_plan_synced") }}     AS zero_plan_synced,
  {{ nullif_placeholder("o.status") }}               AS status,
  {{ nullif_placeholder("o.current_progress") }}     AS current_progress,
  {{ nullif_placeholder("o.project_manager") }}      AS project_manager,

  -- === 跟进措施字段 ===
  {{ nullif_placeholder("o.measure_category") }}         AS measure_category,
  {{ nullif_placeholder("o.measure_title") }}            AS measure_title,
  {{ nullif_placeholder("o.follow_up_person") }}         AS follow_up_person,
  {{ nullif_placeholder("o.main_recipient") }}           AS main_recipient,
  {{ nullif_placeholder("o.cc_recipient") }}             AS cc_recipient,
  {{ nullif_placeholder("o.closure_status") }}           AS closure_status,
  {{ nullif_placeholder("o.closure_deliverable_type") }} AS closure_deliverable_type,
  {{ nullif_placeholder("o.closure_deliverable") }}      AS closure_deliverable,
  {{ nullif_placeholder("o.risk_content") }}             AS risk_content,
  {{ nullif_placeholder("o.remark") }}                   AS remark,
  {{ nullif_placeholder("o.filled_by") }}                AS filled_by,

  -- === 数值字段 ===
  {{ parse_numeric_safe("o.new_plan_count") }}::int      AS new_plan_count,

  -- === 日期解析 ===
  {{ parse_date_safe("o.issue_date") }}              AS issue_date,
  {{ parse_date_safe("o.follow_up_date") }}          AS follow_up_date,
  {{ parse_date_safe("o.final_closure_date") }}      AS final_closure_date,
  {{ parse_date_safe("o.last_update_time") }}        AS last_update_time,

  -- === 周数字段 ===
  {{ parse_numeric_safe("o.issue_week") }}::int              AS issue_week,
  {{ parse_numeric_safe("o.follow_up_week") }}::int          AS follow_up_week,
  {{ parse_numeric_safe("o.final_closure_week") }}::int      AS final_closure_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int        AS last_update_week,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  'ods_quality_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'quality_measure') }} o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != ''
