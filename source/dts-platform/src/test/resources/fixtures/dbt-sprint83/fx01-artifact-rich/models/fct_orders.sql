select order_id, order_date, amount
from {{ ref('stg_orders') }}
