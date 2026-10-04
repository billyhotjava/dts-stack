SELECT snapshot_date, budget_no, count(*) AS duplicate_count
FROM {{ ref('biz_dwd_budget_v2') }}
GROUP BY snapshot_date, budget_no
HAVING count(*) > 1
