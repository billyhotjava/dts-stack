-- 收款与分摊情况明细
with allocation as (
select collection_id,count(*) as allocation_count,sum(allocated_amount) as allocated_amount, count(*) filter (where allocated_amount is null) as missing_allocation_amount_count
from {{ ref('prs_v2_dwd_collection_allocation') }}
group by collection_id
)
select
    cast(f.collection_id as bigint) as collection_id,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.collection_code as text) as collection_code,
    cast(f.project_id as bigint) as project_id,
    cast(f.project_name as text) as project_name,
    cast(f.income_type as integer) as income_type,
    cast(f.collection_status as integer) as collection_status,
    cast(f.payment_type as integer) as payment_type,
    cast(f.pay_mode as text) as pay_mode,
    cast(f.pay_time as timestamp) as pay_time,
    cast(f.collection_amount as numeric) as collection_amount,
    cast(f.update_time as timestamp) as update_time,
    cast(f.customer_id as bigint) as customer_id,
    cast(f.collection_month as date) as collection_month,
    cast(f.is_confirmed as boolean) as is_confirmed,
    cast(coalesce(a.allocation_count,0) as bigint) as allocation_count,
    cast(case when a.allocation_count is null then 0 else a.allocated_amount end as numeric) as allocated_amount,
    cast(f.collection_amount - case when a.allocation_count is null then 0 else a.allocated_amount end as numeric) as unallocated_amount,
    cast(coalesce(a.missing_allocation_amount_count,0) as bigint) as missing_allocation_amount_count
from {{ ref('prs_v2_dwd_finance_collection') }} f
left join allocation a on f.collection_id=a.collection_id
