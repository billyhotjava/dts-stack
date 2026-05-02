{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'flowerbiz_log']) }}

-- 报花单操作日志 STG（v2: biz_type 实测 98% NULL，重命名为 log_biz_type 避免误用为父表 bizType）
-- t_flower_biz_log 真实 schema：无 del_flag/create_time/update_time，仅 10 个字段

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_biz_log'::text                                                  AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS log_id,
  {{ parse_numeric_safe("o.sorts") }}::int                                    AS sorts,
  {{ parse_numeric_safe("o.biz_id") }}::bigint                                AS biz_id,

  -- 注意: 此处 biz_type 不是父表 bizType (98% NULL)，是日志特定语义；prefer JOIN main 取真实 biz_type
  {{ nullif_placeholder("o.biz_type") }}                                      AS log_biz_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS status_raw,

  {{ nullif_placeholder("o.operation_title") }}                               AS operation_title,
  {{ parse_numeric_safe("o.operation_user_id") }}::bigint                     AS operation_user_id,
  {{ nullif_placeholder("o.operation_user_name") }}                           AS operation_user_name,
  {{ parse_date_safe("o.operation_time") }}                                   AS operation_time,
  {{ nullif_placeholder("o.operation_content") }}                             AS operation_content

FROM {{ source('xycyl_ods', 'flower_biz_log') }} o
