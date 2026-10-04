{{
    config(
        materialized='incremental',
        unique_key='order_id',
        on_schema_change='fail'
    )
}}

select
    order_id,
    customer_code,
    amount,
    updated_at
from {{ ref('stg_rt01_orders') }}
{% if is_incremental() %}
where updated_at > (
    select coalesce(max(updated_at), cast('1900-01-01' as timestamp))
    from {{ this }}
)
{% endif %}
