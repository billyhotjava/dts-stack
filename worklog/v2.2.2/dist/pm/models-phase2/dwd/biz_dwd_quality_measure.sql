{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'quality']) }}

SELECT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.measure_content), '') || '|' ||
    COALESCE(btrim(o.last_update_time), '')
  ) AS measure_id,

  -- 关联键（与 biz_dwd_quality_issue 关联）
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    ''  -- issue_date 在措施表中不一定有，用空串占位
  ) AS issue_id_approx,

  {{ nullif_placeholder("o.project_no") }}          AS project_no,
  {{ nullif_placeholder("o.issue_name") }}          AS issue_name,
  {{ nullif_placeholder("o.measure_content") }}     AS measure_content,
  {{ nullif_placeholder("o.measure_status") }}      AS measure_status,
  {{ nullif_placeholder("o.responsible_person") }}  AS responsible_person,
  {{ nullif_placeholder("o.filled_by") }}           AS filled_by,

  {{ parse_date_safe("o.deadline") }}               AS deadline,
  {{ parse_date_safe("o.actual_complete_date") }}   AS actual_complete_date,
  {{ parse_date_safe("o.last_update_time") }}       AS last_update_time,

  'ods_quality_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'quality_measure') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
