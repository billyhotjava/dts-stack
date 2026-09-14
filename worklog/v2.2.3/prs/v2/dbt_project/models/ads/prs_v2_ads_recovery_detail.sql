-- 回收明细与执行情况分析
select
    cast(f.recovery_item_id as bigint) as recovery_item_id,
    cast(f.recovery_id as bigint) as recovery_id,
    cast(f.order_item_id as bigint) as order_item_id,
    cast(f.goods_price_id as bigint) as goods_price_id,
    cast(f.good_name as text) as good_name,
    cast(f.recovery_type as integer) as recovery_type,
    cast(f.planned_quantity as integer) as planned_quantity,
    cast(f.actual_quantity as integer) as actual_quantity,
    cast(f.good_cost as numeric) as good_cost,
    cast(f.item_status as integer) as item_status,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.order_id as bigint) as order_id,
    cast(f.recovery_status as integer) as recovery_status,
    cast(f.recovery_user_id as bigint) as recovery_user_id,
    cast(f.recovery_user_name as text) as recovery_user_name,
    cast(f.plan_recovery_time as timestamp) as plan_recovery_time,
    cast(f.recovery_time as timestamp) as recovery_time,
    cast(p.project_name as text) as project_name,
    cast(p.customer_id as bigint) as customer_id
from {{ ref('prs_v2_dwd_recovery') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
