{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'recovery_item']) }}

-- 回收明细 STG（v2: 真实 schema 没有 del_flag/good_id/create_time/update_time）
-- 真实字段是 goods_price_id（注意复数 s）+ good_norms/specs/unit
-- recovery_type 实测 1=报损 / 2=回购 / 3=留用

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_recovery_info_item'::text                                              AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS recovery_item_id,
  {{ parse_numeric_safe("o.recovery_info_id") }}::bigint                      AS recovery_id,
  {{ parse_numeric_safe("o.biz_item_id") }}::bigint                           AS biz_item_id,

  {{ parse_numeric_safe("o.goods_price_id") }}::bigint                        AS goods_price_id,
  {{ nullif_placeholder("o.good_name") }}                                     AS good_name,
  {{ nullif_placeholder("o.good_norms") }}                                    AS good_norms,
  {{ nullif_placeholder("o.good_specs") }}                                    AS good_specs,
  {{ nullif_placeholder("o.good_unit") }}                                     AS good_unit,
  {{ nullif_placeholder("o.good_type") }}                                     AS good_type_raw,

  {{ nullif_placeholder("o.recovery_type") }}                                 AS recovery_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS status_raw,

  {{ parse_numeric_safe("o.recovery_number") }}::int                          AS recovery_number,
  {{ parse_numeric_safe("o.real_recovery_number") }}::int                     AS real_recovery_number,
  {{ parse_numeric_safe("o.good_cost") }}                                     AS good_cost,

  {{ parse_date_safe("o.recovery_time") }}                                    AS recovery_time

FROM {{ source('xycyl_ods', 'recovery_info_item') }} o
