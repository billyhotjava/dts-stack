

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
    (
  case
    when subject_code is null then null
    when upper(btrim(cast(subject_code as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(subject_code as text)), '')
  end
) AS subject_code,
    (
  case
    when subject_name is null then null
    when upper(btrim(cast(subject_name as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(subject_name as text)), '')
  end
) AS subject_name,
    (
  case
    when dept_name is null then null
    when upper(btrim(cast(dept_name as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(dept_name as text)), '')
  end
) AS dept_name,
    cast(contract_name as text) AS contract_name_raw,
    (
  case
    when (
  case
    when balance is null then null
    when upper(btrim(cast(balance as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(balance as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when balance is null then null
    when upper(btrim(cast(balance as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(balance as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when balance is null then null
    when upper(btrim(cast(balance as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(balance as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when balance is null then null
    when upper(btrim(cast(balance as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(balance as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when balance is null then null
    when upper(btrim(cast(balance as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(balance as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS balance
  FROM "biadmin"."public"."ods_finance_aux_balance"
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
    WHEN (
  case
    when c.contract_name_raw is null then null
    when upper(btrim(cast(c.contract_name_raw as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(c.contract_name_raw as text)), '')
  end
) IS NOT NULL
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
    WHEN (
  case
    when c.contract_name_raw is null then null
    when upper(btrim(cast(c.contract_name_raw as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(c.contract_name_raw as text)), '')
  end
) IS NOT NULL
      AND btrim(c.contract_name_raw) <> '—'
    THEN true
    ELSE false
  END AS has_contract
FROM cleaned c
WHERE c.subject_code IS NOT NULL