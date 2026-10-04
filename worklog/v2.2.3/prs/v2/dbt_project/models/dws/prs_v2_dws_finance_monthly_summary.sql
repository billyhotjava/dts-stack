-- 项目月度应收与现金收入并列汇总
with settlement as (
select tenant_id,project_id,finance_month,count(*) as settlement_count,
 sum(receivable_before_discount) as receivable_before_discount, sum(discounted_receivable) as discounted_receivable, sum(ledger_received_amount) as ledger_received_amount,
 count(*) filter (where discounted_receivable is null) as missing_receivable_count
from {{ ref('prs_v2_dwd_finance_month_settlement') }}
where is_confirmed
group by tenant_id,project_id,finance_month
), cash as (
select tenant_id,project_id,collection_month as finance_month,count(*) as collection_count, sum(collection_amount) as confirmed_collection_amount, count(*) filter (where collection_amount is null) as missing_collection_amount_count
from {{ ref('prs_v2_dwd_finance_collection') }}
where is_confirmed and income_type=1
group by tenant_id,project_id,collection_month
), period_keys as (
select tenant_id,project_id,finance_month from settlement
union
select tenant_id,project_id,finance_month from cash
)
select
    cast(md5(coalesce(cast(k.tenant_id as text), '<NULL>') || '|' || coalesce(cast(k.project_id as text), '<NULL>') || '|' || coalesce(cast(k.finance_month as text), '<NULL>')) as text) as project_month_key,
    cast(k.tenant_id as bigint) as tenant_id,
    cast(k.project_id as bigint) as project_id,
    cast(k.finance_month as date) as finance_month,
    cast(coalesce(s.settlement_count,0) as bigint) as settlement_count,
    cast(coalesce(c.collection_count,0) as bigint) as collection_count,
    cast(case when s.settlement_count is null then 0 else s.receivable_before_discount end as numeric) as receivable_before_discount,
    cast(case when s.settlement_count is null then 0 else s.discounted_receivable end as numeric) as discounted_receivable,
    cast(case when s.settlement_count is null then 0 else s.ledger_received_amount end as numeric) as ledger_received_amount,
    cast(case when c.collection_count is null then 0 else c.confirmed_collection_amount end as numeric) as confirmed_collection_amount,
    cast(coalesce(s.missing_receivable_count,0) as bigint) as missing_receivable_count,
    cast(coalesce(c.missing_collection_amount_count,0) as bigint) as missing_collection_amount_count
from period_keys k
left join settlement s on k.tenant_id is not distinct from s.tenant_id and k.project_id is not distinct from s.project_id and k.finance_month is not distinct from s.finance_month
left join cash c on k.tenant_id is not distinct from c.tenant_id and k.project_id is not distinct from c.project_id and k.finance_month is not distinct from c.finance_month
