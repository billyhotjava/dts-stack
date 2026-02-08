{{ config(materialized='view', tags=['patent','biz','dwd']) }}

select
  p.patent_no,
  p.patent_title_cn,
  p.patent_type,
  p.application_date,
  p.grant_date,
  p.first_publication_date,
  p.assignee_name,
  p.inventor_names,
  p.dept_name,
  p.agent_org_name,
  p.state,
  p.dept_code
from {{ source('ods', 'ods_patent_info') }} as p
where p.state <> '作废'
