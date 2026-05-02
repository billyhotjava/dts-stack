{{ config(materialized='view', tags=['xycyl', 'xycyl-flowerbiz', 'stg', 'project']) }}

-- 项目维度 STG（v2: p_project 没有 customer_id/customer_name 字段；客户经 p_contract 中转）
-- sprint-22 用 main.customer_name (反范式) 作为客户维度

SELECT
  o.id::bigint                                                                AS source_row_id,
  'ods_project'::text                                                         AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'rs_cloud_flower')    AS source_system,
  o.imported_at                                                               AS imported_at,

  o.id::bigint                                                                AS project_id,
  {{ nullif_placeholder("o.code") }}                                          AS project_code,
  {{ nullif_placeholder("o.name") }}                                          AS project_name,
  {{ nullif_placeholder("o.abbreviation") }}                                  AS project_abbreviation,

  {{ parse_numeric_safe("o.contract_id") }}::bigint                           AS contract_id,         -- → p_contract.id (sprint-23+ 联客户)
  {{ nullif_placeholder("o.customer_type") }}                                 AS customer_type_raw,

  {{ parse_numeric_safe("o.manager_id") }}::bigint                            AS project_manager_id,  -- 注意: 字段实际是 manager_id
  {{ parse_numeric_safe("o.biz_user_id") }}::bigint                           AS project_biz_user_id,
  {{ parse_numeric_safe("o.supervisor_id") }}::bigint                         AS project_supervisor_id,
  {{ parse_numeric_safe("o.curing_director") }}::bigint                       AS curing_director_id,
  {{ nullif_placeholder("o.curing_director_name") }}                          AS curing_director_name,

  {{ nullif_placeholder("o.status") }}                                        AS status_raw,
  {{ nullif_placeholder("o.type") }}                                          AS type_raw,
  {{ nullif_placeholder("o.address") }}                                       AS address,

  {{ parse_date_safe("o.start_time") }}                                       AS project_start_time,
  {{ parse_date_safe("o.end_time") }}                                         AS project_end_time,
  {{ parse_date_safe("o.settle_start_time") }}                                AS settle_start_time,
  {{ parse_date_safe("o.settle_end_time") }}                                  AS settle_end_time,

  {{ parse_numeric_safe("o.tenant_id") }}::bigint                             AS tenant_id,

  {{ parse_date_safe("o.create_time") }}                                      AS created_at,
  {{ parse_date_safe("o.update_time") }}                                      AS updated_at

FROM {{ source('xycyl_ods', 'project') }} o
WHERE COALESCE(o.del_flag, '0') = '0'
