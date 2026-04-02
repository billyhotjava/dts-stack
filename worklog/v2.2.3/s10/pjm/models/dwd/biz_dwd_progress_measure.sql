{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'progress']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '') || '|' ||
    COALESCE(btrim(o.measure_title), '')
  ) AS measure_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}               AS project_no,
  {{ nullif_placeholder("o.subsystem") }}                AS subsystem,
  {{ nullif_placeholder("o.node_task") }}                AS node_task,
  {{ nullif_placeholder("o.completion_status") }}        AS completion_status,
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

  -- === 日期解析 ===
  {{ parse_date_safe("o.plan_date") }}                   AS plan_date,
  {{ parse_date_safe("o.follow_up_date") }}              AS follow_up_date,
  {{ parse_date_safe("o.final_closure_date") }}          AS final_closure_date,
  {{ parse_date_safe("o.last_update_time") }}            AS last_update_time,

  -- === 周数 ===
  {{ parse_numeric_safe("o.plan_week") }}::int           AS plan_week,
  {{ parse_numeric_safe("o.follow_up_week") }}::int      AS follow_up_week,
  {{ parse_numeric_safe("o.final_closure_week") }}::int  AS final_closure_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int    AS last_update_week,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.plan_date") }})::int            AS plan_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.plan_date") }})::int         AS plan_quarter,
  to_char({{ parse_date_safe("o.plan_date") }}, 'YYYY-MM')                AS plan_month,

  EXTRACT(YEAR FROM {{ parse_date_safe("o.follow_up_date") }})::int       AS follow_up_year,
  to_char({{ parse_date_safe("o.follow_up_date") }}, 'YYYY-MM')           AS follow_up_month,

  -- === 衍生字段 ===
  CASE
    WHEN {{ parse_date_safe("o.plan_date") }} IS NOT NULL
     AND {{ parse_date_safe("o.follow_up_date") }} IS NOT NULL
    THEN ({{ parse_date_safe("o.follow_up_date") }} - {{ parse_date_safe("o.plan_date") }})::int
  END AS plan_to_followup_days,

  CASE
    WHEN {{ parse_date_safe("o.follow_up_date") }} IS NOT NULL
     AND {{ parse_date_safe("o.final_closure_date") }} IS NOT NULL
    THEN ({{ parse_date_safe("o.final_closure_date") }} - {{ parse_date_safe("o.follow_up_date") }})::int
  END AS followup_to_closure_days,

  'ods_progress_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'progress_measure') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
