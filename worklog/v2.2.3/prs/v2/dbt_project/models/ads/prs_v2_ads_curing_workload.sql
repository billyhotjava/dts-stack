-- 养护工作量分析
select
    cast(f.curing_month_key as text) as curing_month_key,
    cast(f.tenant_id as bigint) as tenant_id,
    cast(f.project_id as bigint) as project_id,
    cast(f.curing_user_id as bigint) as curing_user_id,
    cast(f.curing_user_name as text) as curing_user_name,
    cast(f.business_month as date) as business_month,
    cast(f.curing_record_count as bigint) as curing_record_count,
    cast(f.curing_day_count as bigint) as curing_day_count,
    cast(f.position_visit_quantity as bigint) as position_visit_quantity,
    cast(f.missing_curing_time_count as bigint) as missing_curing_time_count,
    cast(p.project_name as text) as project_name,
    cast(p.customer_id as bigint) as customer_id
from {{ ref('prs_v2_dws_curing_workload_monthly') }} f
left join {{ ref('prs_v2_dwd_project') }} p on f.project_id=p.project_id and f.tenant_id is not distinct from p.tenant_id
