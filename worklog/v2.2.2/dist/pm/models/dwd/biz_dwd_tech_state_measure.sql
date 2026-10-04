{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'tech-state']) }}

SELECT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    COALESCE(btrim(o.measure_content), '') || '|' ||
    COALESCE(btrim(o.last_update_time), '')
  ) AS measure_id,

  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    ''
  ) AS tech_state_id_approx,

  {{ nullif_placeholder("o.project_no") }}          AS project_no,
  {{ nullif_placeholder("o.tech_state_name") }}     AS tech_state_name,
  {{ nullif_placeholder("o.measure_content") }}     AS measure_content,
  {{ nullif_placeholder("o.measure_status") }}      AS measure_status,
  {{ nullif_placeholder("o.responsible_person") }}  AS responsible_person,
  {{ nullif_placeholder("o.filled_by") }}           AS filled_by,

  {{ parse_date_safe("o.deadline") }}               AS deadline,
  {{ parse_date_safe("o.actual_complete_date") }}   AS actual_complete_date,
  {{ parse_date_safe("o.last_update_time") }}       AS last_update_time,

  'ods_tech_state_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'tech_state_measure') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
