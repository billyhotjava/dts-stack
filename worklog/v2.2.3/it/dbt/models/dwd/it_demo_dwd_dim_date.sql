{{ config(materialized='table', tags=['it-demo', 'dwd', 'dimension']) }}

WITH calendar AS (
    SELECT generated_day::date AS full_date
    FROM generate_series(
        date '2026-01-01',
        date '2027-12-31',
        interval '1 day'
    ) AS generated_day
)

SELECT
    to_char(full_date, 'YYYYMMDD')::integer AS date_key,
    full_date,
    extract(year FROM full_date)::integer AS year_no,
    extract(month FROM full_date)::integer AS month_no,
    to_char(full_date, 'IW')::integer AS iso_week_no,
    extract(isodow FROM full_date)::integer BETWEEN 1 AND 5 AS is_workday
FROM calendar
