{{ config(materialized='table', tags=['patent','biz','dws','metrics']) }}

select
  dept_code,
  dept_name,
  count(*) as patent_cnt,
  sum(case when state = '有效' then 1 else 0 end) as valid_patent_cnt,
  sum(case when patent_type = '发明' then 1 else 0 end) as invention_patent_cnt,
  max(grant_date) as latest_grant_date
from {{ ref('biz_dwd_patent_base') }}
group by dept_code, dept_name
