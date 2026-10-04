-- 月度租金快照明细
select
    cast(s.id as bigint) as rent_snapshot_id,
    cast(s.project_id as bigint) as project_id,
    cast(s.department_id as bigint) as department_id,
    cast(s.rent_type as integer) as rent_type,
    cast(s.receivable_amount as numeric) as receivable_amount,
    cast(s.receivable_after_discount as numeric) as receivable_after_discount,
    cast(s.regular_rent as numeric) as regular_rent,
    cast(s.snapshot_time as timestamp) as snapshot_time,
    cast(p.tenant_id as bigint) as tenant_id,
    cast(p.customer_id as bigint) as customer_id,
    cast(case when s.settlement_year between 1900 and 9999 and s.settlement_month between 1 and 12 then make_date(s.settlement_year, s.settlement_month, 1) when trim(s.year_and_month) ~ '^[12][0-9]{3}-?(0[1-9]|1[0-2])$' then make_date(cast(substring(replace(trim(s.year_and_month), '-', ''), 1, 4) as integer), cast(substring(replace(trim(s.year_and_month), '-', ''), 5, 2) as integer), 1) else null end as date) as finance_month,
    cast(s._dts_source_system as text) as dts_source_system,
    cast(s._dts_source_table as text) as dts_source_table,
    cast(s._dts_import_time as timestamp) as dts_import_time,
    cast(s._dts_batch_id as text) as dts_batch_id,
    cast(s._dts_execution_id as text) as dts_execution_id,
    cast(s._dts_task_id as text) as dts_task_id
from {{ source('public', 'ods_prsa_month_rent_snapshot') }} s
left join {{ ref('prs_v2_dwd_project') }} p on s.project_id = p.project_id
