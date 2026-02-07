{{ config(materialized='view', tags=['ads']) }}

select
  d.customer_id,
  c.customer_name,
  c.region_code,
  d.order_cnt_30d,
  d.order_amt_30d,
  d.last_order_time
from {{ ref('dws_customer_30d_metrics') }} as d
left join {{ ref('ods_customer') }} as c
  on d.customer_id = c.customer_id

