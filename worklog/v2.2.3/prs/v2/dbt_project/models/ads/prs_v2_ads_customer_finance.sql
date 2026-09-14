-- 客户月度经营财务分析
select
    cast(f.customer_month_key as text) as customer_month_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.customer_id as bigint) as customer_id,
    cast(f.finance_month as date) as finance_month,
    cast(f.project_count as bigint) as project_count,
    cast(f.unmapped_project_count as bigint) as unmapped_project_count,
    cast(f.receivable_before_discount as numeric) as receivable_before_discount,
    cast(f.discounted_receivable as numeric) as discounted_receivable,
    cast(f.ledger_received_amount as numeric) as ledger_received_amount,
    cast(f.confirmed_collection_amount as numeric) as confirmed_collection_amount,
    cast(f.missing_receivable_count as bigint) as missing_receivable_count,
    cast(f.missing_collection_amount_count as bigint) as missing_collection_amount_count,
    cast(c.customer_name as text) as customer_name
from {{ ref('prs_v2_dws_customer_finance_monthly') }} f
left join {{ ref('prs_v2_dwd_customer') }} c on f.customer_id=c.customer_id and f.tenant_id is not distinct from c.tenant_id
