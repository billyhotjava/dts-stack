-- 租赁业务明细分析
select
    cast(f.order_item_id as bigint) as order_item_id,
    cast(f.order_id as bigint) as order_id,
    cast(f.position_id as bigint) as position_id,
    cast(f.goods_price_id as bigint) as goods_price_id,
    cast(f.project_green_id as bigint) as project_green_id,
    cast(f.item_type as integer) as item_type,
    cast(f.item_status as integer) as item_status,
    cast(f.parent_id as bigint) as parent_id,
    cast(f.plant_type as integer) as plant_type,
    cast(f.green_name as text) as green_name,
    cast(f.requested_quantity as integer) as requested_quantity,
    cast(f.finished_quantity as integer) as finished_quantity,
    cast(f.source_rent as numeric) as source_rent,
    cast(f.source_cost as numeric) as source_cost,
    cast(f.start_time as timestamp) as start_time,
    cast(f.end_time as timestamp) as end_time,
    cast(f.del_flag as text) as del_flag,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.customer_id as bigint) as customer_id,
    cast(f.business_time as timestamp) as business_time,
    cast(f.is_effective as boolean) as is_effective,
    cast(p.project_name as text) as project_name
from {{ ref('prs_v2_dwd_flower_item') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
