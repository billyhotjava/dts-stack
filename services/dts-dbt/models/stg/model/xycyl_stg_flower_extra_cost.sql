{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'flower_extra_cost']) }}

-- 报花额外费用 STG（v2: 唯一一个有 del_flag 的"次要"表）
-- cost_type 实测 1=运费(380) / 2=人工(27) / 3=税费(3) / 4=垃圾清理(162) / 5=其他(2)
-- 注意 4/5 顺序：4 是"垃圾清理"，5 是"其他"

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_extra_cost'::text                                               AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS extra_cost_id,
  {{ parse_numeric_safe("o.biz_id") }}::bigint                                AS biz_id,

  {{ nullif_placeholder("o.biz_type") }}                                      AS biz_type_raw,
  {{ nullif_placeholder("o.cost_type") }}                                     AS cost_type_raw,

  {{ nullif_placeholder("o.title") }}                                         AS title,

  {{ parse_numeric_safe("o.free_amount") }}                                   AS free_amount,
  {{ parse_numeric_safe("o.price_amount") }}                                  AS price_amount,
  {{ parse_numeric_safe("o.tax_rate") }}                                      AS tax_rate,

  {{ parse_numeric_safe("o.pay_user_id") }}::bigint                           AS pay_user_id,
  {{ nullif_placeholder("o.pay_user_name") }}                                 AS pay_user_name,
  {{ parse_date_safe("o.pay_time") }}                                         AS pay_time,

  {{ parse_numeric_safe("o.expense_id") }}::bigint                            AS expense_id,
  {{ nullif_placeholder("o.expense_code") }}                                  AS expense_code,

  {{ nullif_placeholder("o.remark") }}                                        AS remark,

  {{ parse_numeric_safe("o.create_by") }}::bigint                             AS created_by,
  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_numeric_safe("o.update_by") }}::bigint                             AS updated_by,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'flower_extra_cost') }} o
WHERE COALESCE(o.del_flag, '0') = '0'
