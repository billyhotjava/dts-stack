{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'rent_time_log']) }}

-- 起租期变更日志 STG（v2: 真实 schema 没有 del_flag、change_reason、update_time）
-- rent_time_type 实测两值: 1=起租期变更（5875 行）/ 2=减租期（262 行）

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_rent_time_log'::text                                            AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS rent_time_log_id,
  {{ parse_numeric_safe("o.biz_id") }}::bigint                                AS biz_id,

  {{ nullif_placeholder("o.rent_time_type") }}                                AS rent_time_type_raw,

  {{ parse_date_safe("o.old_rent_time") }}                                    AS old_rent_time,
  {{ parse_date_safe("o.new_rent_time") }}                                    AS new_rent_time,

  {{ parse_numeric_safe("o.change_user_id") }}::bigint                        AS change_user_id,
  {{ nullif_placeholder("o.change_user_name") }}                              AS change_user_name,
  {{ parse_date_safe("o.change_time") }}                                      AS change_time

FROM {{ source('xycyl_ods', 'flower_rent_time_log') }} o
