-- 项目月度结算与现金收入分析
select
    cast(f.project_month_key as text) as project_month_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.finance_month as date) as finance_month,
    cast(f.settlement_count as bigint) as settlement_count,
    cast(f.collection_count as bigint) as collection_count,
    cast(f.receivable_before_discount as numeric) as receivable_before_discount,
    cast(f.discounted_receivable as numeric) as discounted_receivable,
    cast(f.ledger_received_amount as numeric) as ledger_received_amount,
    cast(f.confirmed_collection_amount as numeric) as confirmed_collection_amount,
    cast(f.missing_receivable_count as bigint) as missing_receivable_count,
    cast(f.missing_collection_amount_count as bigint) as missing_collection_amount_count,
    cast(p.project_name as text) as project_name,
    cast(p.customer_id as bigint) as customer_id
from {{ ref('prs_v2_dws_finance_monthly_summary') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
