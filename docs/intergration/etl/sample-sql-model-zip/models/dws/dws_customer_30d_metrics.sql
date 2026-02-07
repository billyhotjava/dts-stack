{{ config(materialized='table', tags=['dws']) }}

select
  customer_id,
  count(*) as order_cnt_30d,
  sum(order_amount) as order_amt_30d,
  max(order_time) as last_order_time
from {{ ref('dwd_customer_order_detail') }}
where order_time >= dateadd(day, -30, current_timestamp)
group by customer_id

