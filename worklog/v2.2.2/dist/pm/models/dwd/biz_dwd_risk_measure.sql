{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'risk']) }}

SELECT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.measure_content), '') || '|' ||
    COALESCE(btrim(o.last_update_time), '')
  ) AS measure_id,

  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    ''
  ) AS risk_id_approx,

  {{ nullif_placeholder("o.project_no") }}            AS project_no,
  {{ nullif_placeholder("o.risk_name") }}             AS risk_name,
  {{ nullif_placeholder("o.measure_content") }}       AS measure_content,
  {{ nullif_placeholder("o.measure_status") }}        AS measure_status,
  {{ nullif_placeholder("o.responsible_person") }}    AS responsible_person,
  {{ nullif_placeholder("o.closure_deliverable") }}   AS closure_deliverable,
  {{ nullif_placeholder("o.filled_by") }}             AS filled_by,

  {{ parse_date_safe("o.deadline") }}               AS deadline,
  {{ parse_date_safe("o.actual_complete_date") }}   AS actual_complete_date,
  {{ parse_date_safe("o.last_update_time") }}       AS last_update_time,

  'ods_risk_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'risk_measure') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
