{{ config(materialized='table', tags=['dts-metrics', 'sprint-35-candidate']) }}

-- Candidate artifact generated from a governed graph draft. Publish only through platform/dbt gate.
select
    "stat_date",
    sum("order_amount") as "order_amount",
    sum("order_count") as "order_count",
    (sum("order_amount") / nullif(sum("order_count"), 0)) as "avg_order_amount"
from {{ ref('dws_order_day') }}
group by
    "stat_date"
