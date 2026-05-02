{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'flowerbiz_item_detailed']) }}

-- 报花明细分配表 STG（v2: 重写为真实 8 字段）
-- 实质是 t_flower_biz_item ↔ p_project_green_item 的分配/出库 junction
-- 25.5% 孤儿（flower_biz_item_id 在 t_flower_biz_item 中查无）— 历史 hard-delete
-- 没有 del_flag / create_time / update_time 字段

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_biz_item_detailed'::text                                        AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS biz_item_detail_id,
  {{ parse_numeric_safe("o.flower_biz_item_id") }}::bigint                    AS flower_biz_item_id,
  {{ parse_numeric_safe("o.project_green_item_id") }}::bigint                 AS project_green_item_id,
  {{ parse_numeric_safe("o.plan_purchase_info_id") }}::bigint                 AS plan_purchase_info_id,

  {{ nullif_placeholder("o.status") }}                                        AS status_raw,
  {{ nullif_placeholder("o.source") }}                                        AS source_raw,

  {{ parse_numeric_safe("o.price") }}                                         AS price,
  {{ parse_date_safe("o.allocate_time") }}                                    AS allocate_time

FROM {{ source('xycyl_ods', 'flower_biz_item_detailed') }} o
