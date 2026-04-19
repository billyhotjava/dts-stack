{{ config(materialized='view', tags=['finance', 'stg', 'aux-balance']) }}

WITH cleaned AS (
  SELECT
    md5(
      coalesce(cast(subject_code as text), '')
      || '|'
      || coalesce(cast(subject_name as text), '')
      || '|'
      || coalesce(cast(dept_name as text), '')
      || '|'
      || coalesce(cast(contract_name as text), '')
      || '|'
      || coalesce(cast(balance as text), '')
    ) AS source_row_id,
    'ods_finance_aux_balance'::text AS source_table,
    {{ nullif_placeholder("subject_code") }} AS subject_code,
    {{ nullif_placeholder("subject_name") }} AS subject_name,
    {{ nullif_placeholder("dept_name") }} AS dept_name,
    cast(contract_name as text) AS contract_name_raw,
    {{ parse_numeric_safe("balance") }}::numeric(15,2) AS balance
  FROM {{ source('fin_ods', 'aux_balance') }}
)

SELECT
  c.source_row_id,
  c.source_table,
  c.subject_code,
  c.subject_name,
  c.dept_name,
  c.contract_name_raw,
  nullif(btrim(c.contract_name_raw), '') AS contract_name,
  CASE
    WHEN {{ nullif_placeholder("c.contract_name_raw") }} IS NOT NULL
      AND btrim(c.contract_name_raw) <> '—'
    THEN btrim(c.contract_name_raw)
  END AS contract_name_norm,
  c.balance,
  abs(c.balance) AS abs_balance,
  CASE
    WHEN c.balance > 0 THEN 'positive'
    WHEN c.balance < 0 THEN 'negative'
    ELSE 'zero'
  END AS balance_sign,
  CASE
    WHEN c.subject_code LIKE '5001%' THEN '原材料/设备'
    WHEN c.subject_code LIKE '5101%' THEN '外协/服务'
    WHEN c.subject_code LIKE '5201%' THEN '折旧'
    WHEN c.subject_code LIKE '5301%' THEN '检测试验'
    WHEN c.subject_code LIKE '5401%' THEN '设计咨询'
    WHEN c.subject_code LIKE '5501%' THEN '租赁'
    WHEN c.subject_code LIKE '5601%' THEN '培训'
    ELSE '其他'
  END AS expense_category,
  CASE
    WHEN {{ nullif_placeholder("c.contract_name_raw") }} IS NOT NULL
      AND btrim(c.contract_name_raw) <> '—'
    THEN true
    ELSE false
  END AS has_contract
FROM cleaned c
WHERE c.subject_code IS NOT NULL
