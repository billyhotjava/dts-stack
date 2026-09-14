-- 养护人员项目月度工作量
select
    cast(md5(coalesce(cast(tenant_id as text), '<NULL>') || '|' || coalesce(cast(project_id as text), '<NULL>') || '|' || coalesce(cast(curing_user_id as text), '<NULL>') || '|' || coalesce(cast(date_trunc('month',curing_time) as text), '<NULL>')) as text) as curing_month_key,
    cast(tenant_id as bigint) as tenant_id,
    cast(project_id as bigint) as project_id,
    cast(curing_user_id as bigint) as curing_user_id,
    cast(max(curing_user_name) as text) as curing_user_name,
    cast(date_trunc('month',curing_time) as date) as business_month,
    cast(count(*) as bigint) as curing_record_count,
    cast(count(distinct cast(curing_time as date)) as bigint) as curing_day_count,
    cast(sum(position_quantity) as bigint) as position_visit_quantity,
    cast(count(*) filter (where curing_time is null) as bigint) as missing_curing_time_count
from {{ ref('prs_v2_dwd_curing') }}
group by tenant_id,project_id,curing_user_id,date_trunc('month',curing_time)
