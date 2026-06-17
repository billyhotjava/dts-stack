{{ config(materialized='view', tags=['project-management-v3', 'stg', 'budget']) }}

-- 预算 STG 结构化层：仅做类型转换与占位符收敛。
-- 已执行/剩余/超支等派生口径下沉 DWD。金额按源值解析，不做单位换算。

SELECT
  o.id AS source_row_id,
  'ods_budget_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.budget_no") }} AS budget_no,
  {{ nullif_placeholder("o.subtopic") }} AS subtopic,
  {{ nullif_placeholder("o.research_lab") }} AS research_lab,

  {{ parse_numeric_safe("o.budget_amount_adjusted") }}::numeric AS budget_amount,
  {{ parse_numeric_safe("o.prepaid_amount") }}::numeric        AS prepaid_amount,
  {{ parse_numeric_safe("o.book_cost_amount") }}::numeric      AS book_cost_amount,
  {{ parse_numeric_safe("o.payable_amount") }}::numeric        AS payable_amount
FROM {{ source('pm_ods_v2', 'budget_v2') }} o
