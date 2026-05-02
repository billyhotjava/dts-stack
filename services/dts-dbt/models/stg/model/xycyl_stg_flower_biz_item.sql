{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'flowerbiz_item']) }}

-- 报花明细 STG（v2: 列名对齐生产 t_flower_biz_item 全 62 字段）
-- 行级 rent / cost 是真实数据源；plant_number 是数量；frm_loss/buyback/keep 用于回收联动

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_biz_item'::text                                                 AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  -- ─── 主键 / FK ───
  o.id::bigint                                                                AS biz_item_id,
  {{ parse_numeric_safe("o.flower_biz_id") }}::bigint                         AS biz_id,
  {{ parse_numeric_safe("o.parent_id") }}::bigint                             AS parent_item_id,

  -- ─── 摆位 / 商品 ───
  {{ parse_numeric_safe("o.position_id") }}::bigint                           AS position_id,
  {{ nullif_placeholder("o.position_name") }}                                 AS position_name,
  {{ nullif_placeholder("o.position_full_name") }}                            AS position_full_name,
  {{ parse_numeric_safe("o.good_price_id") }}::bigint                         AS good_price_id,
  {{ nullif_placeholder("o.green_name") }}                                    AS green_name,
  {{ nullif_placeholder("o.good_norms") }}                                    AS good_norms,
  {{ nullif_placeholder("o.good_specs") }}                                    AS good_specs,
  {{ nullif_placeholder("o.good_unit") }}                                     AS good_unit,
  {{ nullif_placeholder("o.good_type") }}                                     AS good_type_raw,
  {{ parse_numeric_safe("o.project_green_id") }}::bigint                      AS project_green_id,
  {{ parse_numeric_safe("o.old_green_id") }}::bigint                          AS old_green_id,

  -- ─── 业务分类 ───
  {{ nullif_placeholder("o.biz_type") }}                                      AS biz_type_raw,
  {{ nullif_placeholder("o.plant_type") }}                                    AS plant_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS status_raw,
  {{ nullif_placeholder("o.transfer_type") }}                                 AS transfer_type_raw,
  {{ nullif_placeholder("o.combination_to_scene") }}                          AS combination_to_scene_raw,
  {{ nullif_placeholder("o.force_change_flowers") }}                          AS force_change_flowers_raw,
  {{ nullif_placeholder("o.reduction_flower_way") }}                          AS reduction_flower_way_raw,
  {{ nullif_placeholder("o.expense_status") }}                                AS expense_status_raw,
  {{ nullif_placeholder("o.source") }}                                        AS source_text,

  -- ─── 数量 ───
  {{ parse_numeric_safe("o.plant_number") }}::int                             AS plant_number,
  {{ parse_numeric_safe("o.total_number") }}::int                             AS total_number,
  {{ parse_numeric_safe("o.good_number") }}::int                              AS good_number,
  {{ parse_numeric_safe("o.reject_number") }}::int                            AS reject_number,
  {{ parse_numeric_safe("o.frm_loss_number") }}::int                          AS frm_loss_number,
  {{ parse_numeric_safe("o.buyback_number") }}::int                           AS buyback_number,
  {{ parse_numeric_safe("o.keep_number") }}::int                              AS keep_number,
  {{ parse_numeric_safe("o.net_receipts_number") }}::int                      AS net_receipts_number,
  {{ parse_numeric_safe("o.transfer_number") }}::int                          AS transfer_number,
  {{ parse_numeric_safe("o.distribute_purchase_number") }}::int               AS distribute_purchase_number,
  {{ parse_numeric_safe("o.distribute_base_number") }}::int                   AS distribute_base_number,
  {{ parse_numeric_safe("o.distribute_slow_number") }}::int                   AS distribute_slow_number,
  {{ parse_numeric_safe("o.real_purchase_number") }}::int                     AS real_purchase_number,
  {{ parse_numeric_safe("o.real_out_number") }}::int                          AS real_out_number,
  {{ parse_numeric_safe("o.finish_number") }}::int                            AS finish_number,
  {{ parse_numeric_safe("o.finish_distribute_number") }}::int                 AS finish_distribute_number,

  -- ─── 金额（line-level） ───
  {{ parse_numeric_safe("o.rent") }}                                          AS rent,
  {{ parse_numeric_safe("o.cost") }}                                          AS cost,
  {{ parse_numeric_safe("o.real_purchase_price") }}                           AS real_purchase_price,
  {{ parse_numeric_safe("o.real_out_price") }}                                AS real_out_price,
  {{ parse_numeric_safe("o.real_slow_price") }}                               AS real_slow_price,

  -- ─── 时间 ───
  {{ parse_date_safe("o.put_time") }}                                         AS put_time,
  {{ parse_date_safe("o.confirm_put_time") }}                                 AS confirm_put_time,
  {{ parse_numeric_safe("o.confirm_put_user_id") }}::bigint                   AS confirm_put_user_id,
  {{ parse_date_safe("o.start_time") }}                                       AS start_time,
  {{ parse_date_safe("o.end_time") }}                                         AS end_time,

  -- ─── 库房 / 调拨 ───
  {{ parse_numeric_safe("o.distribute_store_house_id") }}::bigint             AS distribute_store_house_id,
  {{ parse_numeric_safe("o.distribute_slow_house_id") }}::bigint              AS distribute_slow_house_id,
  {{ parse_numeric_safe("o.back_storehouse_id") }}::bigint                    AS back_storehouse_id,
  {{ parse_numeric_safe("o.transfer_cut_green_id") }}::bigint                 AS transfer_cut_green_id,

  -- ─── 备注 / 排序 ───
  {{ nullif_placeholder("o.rent_des") }}                                      AS rent_des,
  {{ nullif_placeholder("o.bad_debt_reason") }}                               AS bad_debt_reason,
  {{ nullif_placeholder("o.remark") }}                                        AS remark,
  {{ parse_numeric_safe("o.sort") }}::bigint                                  AS sort_order,

  -- ─── 审计 ───
  {{ parse_numeric_safe("o.create_by") }}::bigint                             AS created_by,
  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_numeric_safe("o.update_by") }}::bigint                             AS updated_by,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'flower_biz_item') }} o
WHERE COALESCE(o.del_flag, '0') = '0'
