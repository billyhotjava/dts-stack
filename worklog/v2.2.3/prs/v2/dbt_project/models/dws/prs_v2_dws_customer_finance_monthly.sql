-- 客户月度应收与现金收入汇总
select
    cast(md5(coalesce(cast(f.tenant_id as text), '<NULL>') || '|' || coalesce(cast(p.customer_id as text), '<NULL>') || '|' || coalesce(cast(f.finance_month as text), '<NULL>')) as text) as customer_month_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(p.customer_id as bigint) as customer_id,
    cast(f.finance_month as date) as finance_month,
    cast(count(distinct f.project_id) as bigint) as project_count,
    cast(count(*) filter (where p.customer_id is null) as bigint) as unmapped_project_count,
    cast(sum(f.receivable_before_discount) as numeric) as receivable_before_discount,
    cast(sum(f.discounted_receivable) as numeric) as discounted_receivable,
    cast(sum(f.ledger_received_amount) as numeric) as ledger_received_amount,
    cast(sum(f.confirmed_collection_amount) as numeric) as confirmed_collection_amount,
    cast(sum(f.missing_receivable_count) as bigint) as missing_receivable_count,
    cast(sum(f.missing_collection_amount_count) as bigint) as missing_collection_amount_count
from {{ ref('prs_v2_dws_finance_monthly_summary') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
group by f.tenant_id,p.customer_id,f.finance_month
