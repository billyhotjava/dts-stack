-- 报花变更记录分析
select
    cast(f.change_id as bigint) as change_id,
    cast(f.order_id as bigint) as order_id,
    cast(f.change_code as text) as change_code,
    cast(f.change_type as integer) as change_type,
    cast(f.change_status as integer) as change_status,
    cast(f.change_quantity as integer) as change_quantity,
    cast(f.before_good_price_id as bigint) as before_good_price_id,
    cast(f.after_good_price_id as bigint) as after_good_price_id,
    cast(f.before_total_amount as numeric) as before_total_amount,
    cast(f.after_total_amount as numeric) as after_total_amount,
    cast(f.apply_time as timestamp) as apply_time,
    cast(f.confirmed_time as timestamp) as confirmed_time,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.customer_id as bigint) as customer_id,
    cast(p.project_name as text) as project_name
from {{ ref('prs_v2_dwd_change') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
