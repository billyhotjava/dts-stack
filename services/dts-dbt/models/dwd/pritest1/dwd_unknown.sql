{{ config(materialized='table', tags=['patent', 'dwd']) }}

select
  *
from {{ source('ods', 'ods_patent_info') }}
