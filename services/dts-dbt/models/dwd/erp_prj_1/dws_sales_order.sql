{{ config(materialized='table', tags=['erpdemo_linksrc', 'dwd']) }}

select
  *
from {{ source('ods', 'your_table') }}
