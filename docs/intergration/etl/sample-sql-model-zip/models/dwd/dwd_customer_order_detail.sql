{{ config(materialized='table', tags=['dwd']) }}

with base_customer as (
  select * from {{ ref('ods_customer') }}
),
base_order as (
  select
    o.order_id,
    o.customer_id,
    o.order_amount,
    o.order_status,
    o.order_time
  from {{ source('crm', 'order_info') }} as o
  where o.is_deleted = 0
)
select
  o.order_id,
  o.customer_id,
  c.customer_name,
  c.region_code,
  o.order_amount,
  o.order_status,
  o.order_time
from base_order as o
left join base_customer as c
  on o.customer_id = c.customer_id

