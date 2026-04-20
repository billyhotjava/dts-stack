

WITH cleaned AS (
  SELECT
    md5(
      coalesce(cast(project_id as text), '')
      || '|'
      || coalesce(cast(cycle as text), '')
    ) AS source_row_id,
    'ods_finance_project_fund'::text AS source_table,
    (
  case
    when project_id is null then null
    when upper(btrim(cast(project_id as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(project_id as text)), '')
  end
) AS project_id,
    cast(cycle as text) AS cycle_raw,
    (
  case
    when cycle is null then null
    when upper(btrim(cast(cycle as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(cycle as text)), '')
  end
) AS cycle,
    (
  case
    when (
  case
    when total_fund is null then null
    when upper(btrim(cast(total_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total_fund as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when total_fund is null then null
    when upper(btrim(cast(total_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total_fund as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when total_fund is null then null
    when upper(btrim(cast(total_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total_fund as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when total_fund is null then null
    when upper(btrim(cast(total_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total_fund as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when total_fund is null then null
    when upper(btrim(cast(total_fund as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(total_fund as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS total_fund,
    (
  case
    when (
  case
    when direct_ctrl is null then null
    when upper(btrim(cast(direct_ctrl as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_ctrl as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when direct_ctrl is null then null
    when upper(btrim(cast(direct_ctrl as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_ctrl as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when direct_ctrl is null then null
    when upper(btrim(cast(direct_ctrl as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_ctrl as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when direct_ctrl is null then null
    when upper(btrim(cast(direct_ctrl as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_ctrl as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when direct_ctrl is null then null
    when upper(btrim(cast(direct_ctrl as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_ctrl as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS direct_ctrl,
    (
  case
    when (
  case
    when reserve_indirect is null then null
    when upper(btrim(cast(reserve_indirect as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(reserve_indirect as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when reserve_indirect is null then null
    when upper(btrim(cast(reserve_indirect as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(reserve_indirect as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when reserve_indirect is null then null
    when upper(btrim(cast(reserve_indirect as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(reserve_indirect as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when reserve_indirect is null then null
    when upper(btrim(cast(reserve_indirect as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(reserve_indirect as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when reserve_indirect is null then null
    when upper(btrim(cast(reserve_indirect as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(reserve_indirect as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS reserve_indirect,
    (
  case
    when (
  case
    when direct_rate is null then null
    when upper(btrim(cast(direct_rate as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_rate as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when direct_rate is null then null
    when upper(btrim(cast(direct_rate as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_rate as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when direct_rate is null then null
    when upper(btrim(cast(direct_rate as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_rate as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when direct_rate is null then null
    when upper(btrim(cast(direct_rate as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_rate as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when direct_rate is null then null
    when upper(btrim(cast(direct_rate as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(direct_rate as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(8,2) AS direct_rate,
    (
  case
    when (
  case
    when indirect_spent is null then null
    when upper(btrim(cast(indirect_spent as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(indirect_spent as text)), '')
  end
) is null then null
    when regexp_replace((
  case
    when indirect_spent is null then null
    when upper(btrim(cast(indirect_spent as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(indirect_spent as text)), '')
  end
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when indirect_spent is null then null
    when upper(btrim(cast(indirect_spent as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(indirect_spent as text)), '')
  end
), ',', '', 'g')::numeric
    when regexp_replace((
  case
    when indirect_spent is null then null
    when upper(btrim(cast(indirect_spent as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(indirect_spent as text)), '')
  end
), '[,%]', '', 'g') ~ '^-?\d+(\.\d+)?$'
      then regexp_replace((
  case
    when indirect_spent is null then null
    when upper(btrim(cast(indirect_spent as text))) in ('', 'NULL', 'N/A', 'NA', '-', '--', '/', '—') then null
    else nullif(btrim(cast(indirect_spent as text)), '')
  end
), '[,%]', '', 'g')::numeric
    else null
  end
)::numeric(15,2) AS indirect_spent
  FROM "biadmin"."public"."ods_finance_project_fund"
),
normalized AS (
  SELECT
    c.*,
    CASE
      WHEN c.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(c.cycle, '-', 1), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_start_date,
    CASE
      WHEN c.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(c.cycle, '-', 2), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_end_date
  FROM cleaned c
)

SELECT
  n.source_row_id,
  n.source_table,
  n.project_id,
  n.cycle_raw,
  n.cycle,
  n.total_fund,
  n.direct_ctrl,
  n.reserve_indirect,
  n.direct_rate,
  n.indirect_spent,
  n.cycle_start_date,
  n.cycle_end_date,
  to_char(n.cycle_start_date, 'YYYY-MM') AS cycle_start_month,
  to_char(n.cycle_end_date, 'YYYY-MM') AS cycle_end_month,
  CASE
    WHEN n.cycle_start_date IS NOT NULL AND n.cycle_end_date IS NOT NULL THEN
      (
        (extract(year from n.cycle_end_date)::int - extract(year from n.cycle_start_date)::int) * 12
        + (extract(month from n.cycle_end_date)::int - extract(month from n.cycle_start_date)::int)
        + 1
      )
  END AS cycle_month_span
FROM normalized n
WHERE n.project_id IS NOT NULL