-- 项目月度租赁经营总览
select
    cast(f.project_month_key as text) as project_month_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.business_month as date) as business_month,
    cast(f.effective_order_count as bigint) as effective_order_count,
    cast(f.finished_order_count as bigint) as finished_order_count,
    cast(f.pending_order_count as bigint) as pending_order_count,
    cast(f.change_order_count as bigint) as change_order_count,
    cast(f.add_order_count as bigint) as add_order_count,
    cast(f.cut_order_count as bigint) as cut_order_count,
    cast(f.transfer_order_count as bigint) as transfer_order_count,
    cast(f.sale_order_count as bigint) as sale_order_count,
    cast(f.finished_rent_change_amount as numeric) as finished_rent_change_amount,
    cast(f.finished_order_cost as numeric) as finished_order_cost,
    cast(f.finished_sale_amount as numeric) as finished_sale_amount,
    cast(f.missing_business_time_count as bigint) as missing_business_time_count,
    cast(f.missing_rent_amount_count as bigint) as missing_rent_amount_count,
    cast(p.project_name as text) as project_name,
    cast(p.customer_id as bigint) as customer_id
from {{ ref('prs_v2_dws_project_operations_monthly') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
