{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'change']) }}

-- 变更单 STG（v2: 暴露完整 BEFORE/AFTER 货物对 + 申请/确认双角色）
-- 真实 schema 没有 del_flag — 不做软删过滤
-- change_type 实测仅 1/2/3 三个值；status 仅 1/2/-1

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_change_info'::text                                                     AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS change_id,
  {{ nullif_placeholder("o.code") }}                                          AS change_code,
  {{ nullif_placeholder("o.title") }}                                         AS change_title,
  {{ parse_numeric_safe("o.biz_id") }}::bigint                                AS biz_id,

  {{ nullif_placeholder("o.change_type") }}                                   AS change_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS status_raw,
  {{ parse_numeric_safe("o.change_number") }}::int                            AS change_number,

  -- ─── 申请/确认双角色 ───
  {{ parse_numeric_safe("o.apply_user_id") }}::bigint                         AS apply_user_id,
  {{ nullif_placeholder("o.apply_user_name") }}                               AS apply_user_name,
  {{ parse_date_safe("o.apply_time") }}                                       AS apply_time,
  {{ parse_numeric_safe("o.confirmed_user_id") }}::bigint                     AS confirmed_user_id,
  {{ nullif_placeholder("o.confirmed_user_name") }}                           AS confirmed_user_name,
  {{ parse_date_safe("o.confirmed_time") }}                                   AS confirmed_time,

  -- ─── 金额变更 ───
  {{ parse_numeric_safe("o.before_total_amount") }}                           AS before_total_amount,
  {{ parse_numeric_safe("o.after_total_amount") }}                            AS after_total_amount,

  -- ─── 起租期变更 ───
  {{ parse_date_safe("o.before_settlement_time") }}                           AS before_settlement_time,
  {{ parse_date_safe("o.after_settlement_time") }}                            AS after_settlement_time,

  -- ─── 货物变更（BEFORE） ───
  {{ parse_numeric_safe("o.before_good_price_id") }}::bigint                  AS before_good_price_id,
  {{ nullif_placeholder("o.before_good_name") }}                              AS before_good_name,
  {{ nullif_placeholder("o.before_good_type") }}                              AS before_good_type_raw,
  {{ nullif_placeholder("o.before_good_norms") }}                             AS before_good_norms,
  {{ nullif_placeholder("o.before_good_specs") }}                             AS before_good_specs,
  {{ nullif_placeholder("o.before_good_unit") }}                              AS before_good_unit,

  -- ─── 货物变更（AFTER） ───
  {{ parse_numeric_safe("o.after_good_price_id") }}::bigint                   AS after_good_price_id,
  {{ nullif_placeholder("o.after_good_name") }}                               AS after_good_name,
  {{ nullif_placeholder("o.after_good_type") }}                               AS after_good_type_raw,
  {{ nullif_placeholder("o.after_good_norms") }}                              AS after_good_norms,
  {{ nullif_placeholder("o.after_good_specs") }}                              AS after_good_specs,
  {{ nullif_placeholder("o.after_good_unit") }}                               AS after_good_unit,

  {{ nullif_placeholder("o.remark") }}                                        AS remark,

  {{ parse_numeric_safe("o.create_by") }}::bigint                             AS created_by,
  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_numeric_safe("o.update_by") }}::bigint                             AS updated_by,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'change_info') }} o
