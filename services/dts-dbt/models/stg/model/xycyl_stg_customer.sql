{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'customer']) }}

-- 客户维度 STG（v2: p_customer 没有 customer_type 字段，是普通 type 字段）
-- sprint-22 暴露最小列；完整客户资料在 sprint-24 摆放/客户域

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_customer'::text                                                        AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS customer_id,
  {{ nullif_placeholder("o.code") }}                                          AS customer_code,
  {{ nullif_placeholder("o.name") }}                                          AS customer_name,
  {{ nullif_placeholder("o.abbreviation") }}                                  AS customer_abbreviation,
  {{ nullif_placeholder("o.contacts_name") }}                                 AS contacts_name,
  {{ nullif_placeholder("o.contacts_phone") }}                                AS contacts_phone,
  {{ nullif_placeholder("o.contacts_post") }}                                 AS contacts_post,
  {{ nullif_placeholder("o.type") }}                                          AS customer_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS customer_status_raw,
  {{ nullif_placeholder("o.source") }}                                        AS customer_source_raw,
  {{ nullif_placeholder("o.address") }}                                       AS address,

  {{ parse_numeric_safe("o.tenant_id") }}::bigint                             AS tenant_id,

  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'customer') }} o
WHERE COALESCE(o.del_flag, '0') = '0'
