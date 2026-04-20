

WITH cleaned AS (
  SELECT
    md5(coalesce(cast(year_period as text), '')) AS source_row_id,
    'ods_finance_own_fund'::text AS source_table,
    cast(year_period as text) AS year_period_raw,
    btrim(cast(year_period as text)) AS year_period,
    (
  case
    when (
  case
    when career_fund is null then null
    when upper(btrim(cast(career_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_fund as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when career_fund is null then null
    when upper(btrim(cast(career_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_fund as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when career_fund is null then null
    when upper(btrim(cast(career_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_fund as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when career_fund is null then null
    when upper(btrim(cast(career_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_fund as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when career_fund is null then null
    when upper(btrim(cast(career_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_fund as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS career_fund,
    (
  case
    when career_note is null then null
    when upper(btrim(cast(career_note as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(career_note as text)), '')
  end
) AS career_note,
    (
  case
    when (
  case
    when deprec_fund is null then null
    when upper(btrim(cast(deprec_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_fund as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when deprec_fund is null then null
    when upper(btrim(cast(deprec_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_fund as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when deprec_fund is null then null
    when upper(btrim(cast(deprec_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_fund as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when deprec_fund is null then null
    when upper(btrim(cast(deprec_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_fund as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when deprec_fund is null then null
    when upper(btrim(cast(deprec_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_fund as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS deprec_fund,
    (
  case
    when deprec_note is null then null
    when upper(btrim(cast(deprec_note as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(deprec_note as text)), '')
  end
) AS deprec_note,
    (
  case
    when (
  case
    when welfare_fund is null then null
    when upper(btrim(cast(welfare_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(welfare_fund as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when welfare_fund is null then null
    when upper(btrim(cast(welfare_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(welfare_fund as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when welfare_fund is null then null
    when upper(btrim(cast(welfare_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(welfare_fund as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when welfare_fund is null then null
    when upper(btrim(cast(welfare_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(welfare_fund as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when welfare_fund is null then null
    when upper(btrim(cast(welfare_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(welfare_fund as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS welfare_fund,
    (
  case
    when (
  case
    when safety_fund is null then null
    when upper(btrim(cast(safety_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(safety_fund as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when safety_fund is null then null
    when upper(btrim(cast(safety_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(safety_fund as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when safety_fund is null then null
    when upper(btrim(cast(safety_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(safety_fund as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when safety_fund is null then null
    when upper(btrim(cast(safety_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(safety_fund as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when safety_fund is null then null
    when upper(btrim(cast(safety_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(safety_fund as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS safety_fund,
    (
  case
    when (
  case
    when total is null then null
    when upper(btrim(cast(total as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when total is null then null
    when upper(btrim(cast(total as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when total is null then null
    when upper(btrim(cast(total as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when total is null then null
    when upper(btrim(cast(total as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when total is null then null
    when upper(btrim(cast(total as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS total
  FROM "biadmin"."public"."ods_finance_own_fund"
)

SELECT
  c.source_row_id,
  c.source_table,
  c.year_period_raw,
  c.year_period,
  c.career_fund,
  c.career_note,
  c.deprec_fund,
  c.deprec_note,
  c.welfare_fund,
  c.safety_fund,
  c.total,
  substring(c.year_period from '^\d{4}')::int AS period_year,
  CASE
    WHEN c.year_period LIKE '%年初' THEN 'opening'
    WHEN c.year_period LIKE '%预计增加' THEN 'increase'
    WHEN c.year_period LIKE '%预计使用' THEN 'usage'
    WHEN c.year_period LIKE '%余额' THEN 'balance'
  END AS period_type,
  CASE
    WHEN c.year_period LIKE '%年初' THEN 1
    WHEN c.year_period LIKE '%预计增加' THEN 2
    WHEN c.year_period LIKE '%预计使用' THEN 3
    WHEN c.year_period LIKE '%余额' THEN 4
  END AS period_sort,
  CASE
    WHEN c.year_period LIKE '%余额' THEN true
    ELSE false
  END AS is_balance_row
FROM cleaned c
WHERE c.year_period IS NOT NULL