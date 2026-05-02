{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'flowerbiz_main']) }}

-- 报花单主表 STG（v2: 列名对齐生产 + 暴露财务/审批/分类全字段）
-- 仅做类型转换 + 占位符归 NULL + 字段改名 + 软删过滤
-- 禁止 CASE WHEN / 别名翻译 / 派生布尔 / 月份格式化 — 这些下沉 DWD

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_flower_biz_info'::text                                                 AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  -- ─── 主键 / 编码 ───
  o.id::bigint                                                                AS biz_id,
  {{ nullif_placeholder("o.code") }}                                          AS biz_code,
  {{ nullif_placeholder("o.title") }}                                         AS biz_title,
  {{ nullif_placeholder("o.batch_code") }}                                    AS batch_code,

  -- ─── 分类 / 状态（raw 字符串，DWD 翻译） ───
  {{ nullif_placeholder("o.biz_type") }}                                      AS biz_type_raw,
  {{ nullif_placeholder("o.status") }}                                        AS status_raw,
  {{ nullif_placeholder("o.urgent") }}                                        AS urgent_raw,
  {{ nullif_placeholder("o.bad_debt_type") }}                                 AS bad_debt_type_raw,
  {{ nullif_placeholder("o.transfer_type") }}                                 AS transfer_type_raw,
  {{ nullif_placeholder("o.source_type") }}                                   AS source_type_raw,
  {{ nullif_placeholder("o.bear_cost_type") }}                                AS bear_cost_type_raw,
  {{ nullif_placeholder("o.sales_payment_type") }}                            AS sales_payment_type_raw,
  {{ nullif_placeholder("o.changer_type") }}                                  AS changer_type_raw,
  {{ nullif_placeholder("o.change_flower_type") }}                            AS change_flower_type_raw,
  {{ nullif_placeholder("o.rent_update_type") }}                              AS rent_update_type_raw,
  {{ nullif_placeholder("o.accounting_status") }}                             AS accounting_status_raw,
  {{ nullif_placeholder("o.cut_confirm_status") }}                            AS cut_confirm_status_raw,
  {{ nullif_placeholder("o.print_status") }}                                  AS print_status_raw,

  -- ─── 项目 / 客户（反范式，customer_name 直接挂主表） ───
  {{ parse_numeric_safe("o.project_id") }}::bigint                            AS project_id,
  {{ nullif_placeholder("o.project_name") }}                                  AS project_name,
  {{ nullif_placeholder("o.customer_name") }}                                 AS customer_name,
  {{ nullif_placeholder("o.phone_number") }}                                  AS customer_phone,
  {{ nullif_placeholder("o.address") }}                                       AS customer_address,
  {{ parse_numeric_safe("o.tenant_id") }}::bigint                             AS tenant_id,

  -- ─── 用户角色（注意: apply_use 不是 apply_user） ───
  {{ parse_numeric_safe("o.apply_use_id") }}::bigint                          AS apply_user_id,
  {{ nullif_placeholder("o.apply_use_name") }}                                AS apply_user_name,
  {{ parse_numeric_safe("o.examine_user_id") }}::bigint                       AS examine_user_id,
  {{ nullif_placeholder("o.examine_user_name") }}                             AS examine_user_name,
  {{ parse_numeric_safe("o.review_user_id") }}::bigint                        AS review_user_id,
  {{ nullif_placeholder("o.review_user_name") }}                              AS review_user_name,
  {{ parse_numeric_safe("o.sign_user_id") }}::bigint                          AS sign_user_id,
  {{ nullif_placeholder("o.sign_user_name") }}                                AS sign_user_name,
  {{ parse_numeric_safe("o.curing_user_id") }}::bigint                        AS curing_user_id,
  {{ nullif_placeholder("o.curing_user_name") }}                              AS curing_user_name,
  {{ parse_numeric_safe("o.project_manage_id") }}::bigint                     AS project_manage_id,
  {{ nullif_placeholder("o.project_manage_name") }}                           AS project_manage_name,
  {{ parse_numeric_safe("o.biz_manage_id") }}::bigint                         AS biz_manage_id,
  {{ nullif_placeholder("o.biz_manage_name") }}                               AS biz_manage_name,
  {{ parse_numeric_safe("o.reject_user_id") }}::bigint                        AS reject_user_id,
  {{ nullif_placeholder("o.reject_user_name") }}                              AS reject_user_name,

  -- ─── 金额（已自带正负号，下游不再加符号） ───
  {{ parse_numeric_safe("o.biz_total_rent") }}                                AS biz_total_rent,
  {{ parse_numeric_safe("o.biz_total_cost") }}                                AS biz_total_cost,
  {{ parse_numeric_safe("o.total_amount") }}                                  AS total_amount,
  {{ parse_numeric_safe("o.total_extra_cost") }}                              AS total_extra_cost,
  {{ parse_numeric_safe("o.total_extra_price") }}                             AS total_extra_price,
  {{ parse_numeric_safe("o.fare") }}                                          AS fare,
  {{ parse_numeric_safe("o.labor_cost") }}                                    AS labor_cost,
  {{ parse_numeric_safe("o.cleaning_fee") }}                                  AS cleaning_fee,
  {{ parse_numeric_safe("o.tax_rate") }}                                      AS tax_rate,
  {{ parse_numeric_safe("o.rent_discount_ratio") }}                           AS rent_discount_ratio,
  {{ parse_numeric_safe("o.lease_term") }}::int                               AS lease_term,

  -- ─── 时间链 ───
  {{ parse_date_safe("o.apply_time") }}                                       AS apply_time,
  {{ parse_date_safe("o.examine_time") }}                                     AS examine_time,
  {{ parse_date_safe("o.review_time") }}                                      AS review_time,
  {{ parse_date_safe("o.sign_time") }}                                        AS sign_time,
  {{ parse_date_safe("o.reject_time") }}                                      AS reject_time,
  {{ parse_date_safe("o.start_lease_time") }}                                 AS start_lease_time,
  {{ parse_date_safe("o.plan_finish_time") }}                                 AS plan_finish_time,
  {{ parse_date_safe("o.cut_confitm_time") }}                                 AS cut_confirm_time,  -- 生产 typo "confitm"
  {{ parse_date_safe("o.settlement_time") }}                                  AS settlement_time,
  {{ parse_date_safe("o.finish_time") }}                                      AS finish_time,
  {{ parse_date_safe("o.document_finish_time") }}                             AS document_finish_time,
  {{ parse_date_safe("o.print_time") }}                                       AS print_time,

  -- ─── 财务/任务挂钩（sprint-25 接） ───
  {{ parse_numeric_safe("o.expense_id") }}::bigint                            AS expense_id,
  {{ nullif_placeholder("o.expense_code") }}                                  AS expense_code,
  {{ parse_numeric_safe("o.settle_id") }}::bigint                             AS settle_id,
  {{ nullif_placeholder("o.settle_code") }}                                   AS settle_code,
  {{ parse_numeric_safe("o.task_info_id") }}::bigint                          AS task_info_id,
  {{ parse_numeric_safe("o.task_item_id") }}::bigint                          AS task_item_id,
  {{ nullif_placeholder("o.task_code") }}                                     AS task_code,

  -- ─── 备注 / 描述 ───
  {{ nullif_placeholder("o.remark") }}                                        AS remark,
  {{ nullif_placeholder("o.give_describe") }}                                 AS give_describe,
  {{ nullif_placeholder("o.sale_describe") }}                                 AS sale_describe,
  {{ nullif_placeholder("o.reject_reason") }}                                 AS reject_reason,
  {{ nullif_placeholder("o.back_reason") }}                                   AS back_reason,

  -- ─── 审计 ───
  {{ parse_numeric_safe("o.create_by") }}::bigint                             AS created_by,
  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_numeric_safe("o.update_by") }}::bigint                             AS updated_by,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'flower_biz_info') }} o
WHERE COALESCE(o.del_flag, '0') = '0'
