{{ config(materialized='view') }}

select
    cast(order_id as bigint) as order_id,
    cast(customer_code as varchar(32)) as customer_code,
    cast(order_date as date) as order_date,
    cast(amount as numeric(18, 2)) as amount,
    cast(updated_at as timestamp) as updated_at
from {{ ref('rt01_orders') }}
