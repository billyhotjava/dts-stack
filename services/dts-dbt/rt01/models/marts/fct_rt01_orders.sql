{{ config(materialized='table') }}

select
    order_id,
    customer_code,
    order_date,
    amount,
    updated_at
from {{ ref('stg_rt01_orders') }}
