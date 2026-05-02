{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'recovery']) }}

-- 回收单 STG（v2: 真实 schema 没有 del_flag、code 字段，移除）
-- 真实 schema 有 project_id/project_name/distribution_user_name/distribution_time

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_recovery_info'::text                                                   AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS recovery_id,
  {{ parse_numeric_safe("o.biz_info_id") }}::bigint                           AS biz_id,

  {{ parse_numeric_safe("o.project_id") }}::bigint                            AS project_id,
  {{ nullif_placeholder("o.project_name") }}                                  AS project_name,

  {{ parse_numeric_safe("o.distribution_user_id") }}::bigint                  AS distribution_user_id,
  {{ nullif_placeholder("o.distribution_user_name") }}                        AS distribution_user_name,
  {{ parse_date_safe("o.distribution_time") }}                                AS distribution_time,

  {{ parse_numeric_safe("o.recovery_user_id") }}::bigint                      AS recovery_user_id,
  {{ nullif_placeholder("o.recovery_user_name") }}                            AS recovery_user_name,

  {{ parse_numeric_safe("o.store_house_id") }}::bigint                        AS store_house_id,
  {{ nullif_placeholder("o.store_house_name") }}                              AS store_house_name,

  {{ nullif_placeholder("o.status") }}                                        AS status_raw,

  {{ parse_date_safe("o.plan_recovery_time") }}                               AS plan_recovery_time,
  {{ parse_date_safe("o.recovery_time") }}                                    AS recovery_time,

  {{ nullif_placeholder("o.remark") }}                                        AS remark,
  {{ parse_numeric_safe("o.tenant_id") }}::bigint                             AS tenant_id,

  {{ parse_numeric_safe("o.create_by") }}::bigint                             AS created_by,
  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_numeric_safe("o.update_by") }}::bigint                             AS updated_by,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'recovery_info') }} o
