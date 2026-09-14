-- 项目月度租赁业务汇总
select
    cast(md5(coalesce(cast(tenant_id as text), '<NULL>') || '|' || coalesce(cast(project_id as text), '<NULL>') || '|' || coalesce(cast(date_trunc('month',business_time) as text), '<NULL>')) as text) as project_month_key,
    cast(tenant_id as bigint) as tenant_id,
    cast(project_id as bigint) as project_id,
    cast(date_trunc('month',business_time) as date) as business_month,
    cast(count(*) as bigint) as effective_order_count,
    cast(count(*) filter (where is_finished) as bigint) as finished_order_count,
    cast(count(*) filter (where not is_finished) as bigint) as pending_order_count,
    cast(count(*) filter (where order_type=1) as bigint) as change_order_count,
    cast(count(*) filter (where order_type=2) as bigint) as add_order_count,
    cast(count(*) filter (where order_type=3) as bigint) as cut_order_count,
    cast(count(*) filter (where order_type=4) as bigint) as transfer_order_count,
    cast(count(*) filter (where order_type=7) as bigint) as sale_order_count,
    cast(sum(rent_change_amount) filter (where is_finished) as numeric) as finished_rent_change_amount,
    cast(sum(order_cost_amount) filter (where is_finished) as numeric) as finished_order_cost,
    cast(sum(sale_order_amount) filter (where is_finished and order_type=7) as numeric) as finished_sale_amount,
    cast(count(*) filter (where business_time is null) as bigint) as missing_business_time_count,
    cast(count(*) filter (where is_finished and rent_change_amount is null) as bigint) as missing_rent_amount_count
from {{ ref('prs_v2_dwd_flower_order') }}
where is_effective
group by tenant_id,project_id,date_trunc('month',business_time)
