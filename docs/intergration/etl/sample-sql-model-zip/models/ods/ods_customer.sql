{{ config(materialized='view', tags=['ods']) }}

select
  c.customer_id,
  c.customer_name,
  c.phone,
  c.region_code,
  c.created_at
from {{ source('crm', 'customer') }} as c
where c.is_deleted = 0

