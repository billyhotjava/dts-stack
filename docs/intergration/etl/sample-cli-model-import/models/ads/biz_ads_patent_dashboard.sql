{{ config(materialized='view', tags=['patent','biz','ads','dashboard']) }}

select
  dept_code,
  dept_name,
  patent_cnt,
  valid_patent_cnt,
  invention_patent_cnt,
  latest_grant_date,
  case when patent_cnt = 0 then 0 else round(valid_patent_cnt * 1.0 / patent_cnt, 4) end as valid_rate
from {{ ref('biz_dws_patent_dept_metrics') }}
