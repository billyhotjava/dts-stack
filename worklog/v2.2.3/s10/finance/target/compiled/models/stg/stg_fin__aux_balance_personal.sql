

WITH cleaned AS (
  SELECT
    md5(
      coalesce(cast(subject_code as text), '')
      || '|'
      || coalesce(cast(subject_name as text), '')
      || '|'
      || coalesce(cast(employee_dept as text), '')
      || '|'
      || coalesce(cast(employee_name as text), '')
      || '|'
      || coalesce(cast(balance as text), '')
    ) AS source_row_id,
    'ods_finance_aux_balance_personal'::text AS source_table,
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
    when employee_dept is null then null
    when upper(btrim(cast(employee_dept as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(employee_dept as text)), '')
  end
) AS employee_dept,
    (
  case
    when employee_name is null then null
    when upper(btrim(cast(employee_name as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(employee_name as text)), '')
  end
) AS employee_name,
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
  FROM "biadmin"."public"."ods_finance_aux_balance_personal"
)

SELECT
  c.source_row_id,
  c.source_table,
  c.subject_code,
  c.subject_name,
  c.employee_dept,
  c.employee_name,
  c.balance,
  abs(c.balance) AS abs_balance,
  CASE
    WHEN c.balance > 0 THEN 'debit'
    WHEN c.balance < 0 THEN 'credit'
    ELSE 'zero'
  END AS balance_direction,
  CASE
    WHEN c.subject_code LIKE '1122%' THEN '其他应收-借款'
    WHEN c.subject_code LIKE '2211%' THEN '应付职工薪酬'
    ELSE '其他'
  END AS subject_category
FROM cleaned c
WHERE c.subject_code IS NOT NULL